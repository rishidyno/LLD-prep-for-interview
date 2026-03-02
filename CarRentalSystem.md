# 🚗 Comprehensive LLD Study Guide: Car Rental System

## 1. The Problem Scope & Clarifying Questions

Always start the interview by defining the exact boundaries of the problem.

* **Topology:** Are we a single local shop or a multi-branch enterprise? *(We chose: Multi-branch).*
* **Granularity:** Do we track "10 generic Sedans" or "1 specific Sedan with License Plate XYZ"? *(We chose: Specific physical vehicles).*
* **Concurrency:** Do we need to prevent double-booking if two users click 'Checkout' at the exact same millisecond? *(We chose: Yes, strict thread-safety).*
* **Pricing:** Are there add-ons (GPS, Child Seats) and dynamic pricing (Weekly discounts)? *(We chose: Yes).*

## 2. Core Entities (Domain Modeling)

* **`User`**: Represents the customer. *Why an object and not just a String ID?* Because renting a car involves real-world liability. We must validate their Driving License before letting them book.
* **`Vehicle`**: The physical car. It has a base price and a strict physical state (`AVAILABLE`, `MAINTENANCE`, etc.).
* **`Reservation`**: The contract connecting the User, the Vehicle, and the Dates.
* **`Branch`**: The physical location managing a local fleet of vehicles.
* **`CarRentalSystem`**: The global orchestrator managing everything.

---

## 3. The "Shine" Factors: How to Dominate the Interview

This is where you separate yourself from SDE-1s. Use these simple analogies to explain your architectural choices to the interviewer.

### A. Concurrency (Preventing the Double-Booking Disaster)

* **The Trap:** Most candidates use a simple `HashMap` and just check if the car's status is `AVAILABLE`. But what if User A wants the car for June, and User B wants the same car for July? The car is "AVAILABLE" right now, but their dates might overlap if they aren't checked safely!
* **The SDE-2 Solution:** We use `ConcurrentHashMap` and fine-grained locking (`ReentrantLock`).
* **The Simple Analogy:** Think of booking a movie theater seat. If two people try to click the exact same seat at the exact same millisecond, the system must put a "Lock" on that seat for whoever clicked a millisecond faster. By using a lock keyed to the **License Plate**, User A booking a Toyota does *not* block User B from booking a Honda. We maximize speed while guaranteeing safety.

### B. The State Pattern (Reservation Lifecycle)

* **The Trap:** A user clicks book, and the system instantly returns a "Success" reservation. But what if their credit card is declined 3 seconds later? The car is now stuck as "booked" in the database.
* **The SDE-2 Solution:** A State Machine (`PENDING` $\rightarrow$ `CONFIRMED` or `CANCELED`).
* **The Simple Analogy:** Think of ordering an Uber. First, it's "Finding Driver" (`PENDING`). If a driver accepts, it's "On the Way" (`CONFIRMED`). If no driver is found, it's "Canceled" (`CANCELED`). We lock the car, try to charge the card, and *only* confirm the reservation if the bank says yes.

### C. The Decorator Pattern (Dynamic Add-ons)

* **The Trap:** Creating classes like `SuvWithGps` or `SuvWithGpsAndInsurance`. This leads to hundreds of messy classes (Class Explosion).
* **The SDE-2 Solution:** The Decorator Pattern.
* **The Simple Analogy:** Ordering a custom burger. The base burger is $5. You want cheese? We "wrap" the burger in a Cheese Decorator (+$1). You want bacon? We wrap it again in a Bacon Decorator (+$2). Total is $8. The code dynamically wraps the base rental invoice with whatever add-ons the user selects at checkout.

### D. The Strategy Pattern (Pricing & Payments)

* **The Trap:** Writing massive `if (paymentType == "CREDIT") { ... } else if (paymentType == "PAYPAL") { ... }` blocks inside your checkout flow.
* **The SDE-2 Solution:** The Strategy Pattern.
* **The Simple Analogy:** Think of a Nintendo console. The console (our checkout system) doesn't care what game you play. You just plug in a cartridge (a `Strategy`). We can plug in a `WeeklyDiscountStrategy` cartridge for pricing, and a `PayPalStrategy` cartridge for payment. If we add Apple Pay tomorrow, we just make a new cartridge; we don't rewrite the console.

---

## 4. The Master Code Implementation

Here is the fully integrated, runnable Java code. Practice reading the `/* INTERVIEW EXPLANATION */` blocks out loud.

```java
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * ============================================================================
 * ULTIMATE CAR RENTAL SYSTEM LOW-LEVEL DESIGN (PRODUCTION READY)
 * ============================================================================
 * INTERVIEW SCRIPT / INTRODUCTION:
 * "To design a highly scalable and thread-safe Car Rental System, I am focusing 
 * on concurrency and the Open/Closed Principle. I will use 4 main patterns:
 * 1. STATE PATTERN: To handle the strict lifecycle of a reservation (Pending -> Confirmed).
 * 2. STRATEGY PATTERN: To decouple pricing and payments so business rules can change easily.
 * 3. DECORATOR PATTERN: To handle add-ons (like GPS or Insurance) without Class Explosion.
 * 4. FINE-GRAINED LOCKING: Using ReentrantLocks per vehicle to prevent double-booking 
 * without freezing the entire branch database."
 * ============================================================================
 */

// ==========================================
// 1. STATE MANAGEMENT & ENTITIES
// ==========================================

/*
 * INTERVIEW EXPLANATION:
 * "I use Enums here for Vehicle Type and Status. If we used standard Strings, a typo 
 * like 'available' vs 'AVAILABLE' could break our search algorithms. Enums guarantee 
 * strict type safety at compile time."
 */
enum VehicleType { ECONOMY, SUV, LUXURY }
enum VehicleStatus { AVAILABLE, MAINTENANCE }

/* * INTERVIEW EXPLANATION: 
 * "This represents a strict State Machine for the Reservation. A booking ALWAYS starts 
 * as PENDING. We hold the lock on the vehicle, attempt to charge the user's credit card, 
 * and ONLY if the payment gateway returns 'true' do we transition to CONFIRMED. 
 * If payment fails, it transitions to CANCELED and the vehicle is instantly freed." 
 */
enum ReservationStatus { PENDING, CONFIRMED, CANCELED }

class User {
    private String userId;
    private String fullName;
    
    // "Capturing driving license is a strict domain requirement for a car rental."
    private String drivingLicense; 
    
    public User(String userId, String fullName, String drivingLicense) {
        this.userId = userId;
        this.fullName = fullName;
        this.drivingLicense = drivingLicense;
    }
    public String getUserId() { return userId; }
    public String getDrivingLicense() { return drivingLicense; }
}

class Vehicle {
    private String licensePlate;
    private VehicleType type;
    private VehicleStatus status;
    private double baseDailyRate;

    public Vehicle(String licensePlate, VehicleType type, double baseDailyRate) {
        this.licensePlate = licensePlate;
        this.type = type;
        this.baseDailyRate = baseDailyRate;
        this.status = VehicleStatus.AVAILABLE;
    }
    public String getLicensePlate() { return licensePlate; }
    public VehicleStatus getStatus() { return status; }
    public double getBaseDailyRate() { return baseDailyRate; }
}

class Reservation {
    private String reservationId;
    private User user;
    private String licensePlate;
    private LocalDate startDate;
    private LocalDate endDate;
    private ReservationStatus status;

    public Reservation(User user, String licensePlate, LocalDate startDate, LocalDate endDate) {
        this.reservationId = UUID.randomUUID().toString();
        this.user = user;
        this.licensePlate = licensePlate;
        this.startDate = startDate;
        this.endDate = endDate;
        // "Every reservation must start as PENDING until money physically changes hands."
        this.status = ReservationStatus.PENDING; 
    }

    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }
}

// ==========================================
// 2. STRATEGY PATTERNS (Pricing & Payment)
// ==========================================

/* * INTERVIEW EXPLANATION: 
 * "To adhere to the Open/Closed Principle, I decoupled pricing and payments 
 * using the Strategy Pattern. If the business introduces a 'Holiday Surge' price 
 * or integrates 'Apple Pay' tomorrow, we do not need to touch the core checkout logic. 
 * We just inject a new Strategy class." 
 */

interface PricingStrategy {
    double calculateBasePrice(Vehicle vehicle, long days);
}

class WeeklyDiscountPricingStrategy implements PricingStrategy {
    @Override
    public double calculateBasePrice(Vehicle vehicle, long days) {
        double total = vehicle.getBaseDailyRate() * days;
        // "A simple business rule: 20% discount if you book for a week or more."
        return days >= 7 ? total * 0.8 : total; 
    }
}

interface PaymentStrategy {
    boolean processPayment(double amount);
}

class CreditCardPayment implements PaymentStrategy {
    @Override
    public boolean processPayment(double amount) {
        System.out.println("Charging $" + amount + " to Credit Card...");
        return true; // "Mocking a successful bank charge for the simulation."
    }
}

// ==========================================
// 3. DECORATOR PATTERN (Add-ons)
// ==========================================

/* * INTERVIEW BONUS POINT (AVOIDING CLASS EXPLOSION): 
 * "I use the Decorator pattern for Add-ons like GPS or Insurance. If I used inheritance, 
 * I would have to create `CarWithGPS`, `CarWithInsurance`, and `CarWithGPSAndInsurance` classes. 
 * That is called Class Explosion. The Decorator acts like a wrapper around the base invoice, 
 * allowing us to stack as many add-ons as we want dynamically at runtime." 
 */

interface Invoice { double getTotal(); }

class BaseInvoice implements Invoice {
    private double baseAmount;
    public BaseInvoice(double baseAmount) { this.baseAmount = baseAmount; }
    @Override public double getTotal() { return baseAmount; }
}

abstract class InvoiceDecorator implements Invoice {
    protected Invoice wrappedInvoice;
    public InvoiceDecorator(Invoice invoice) { this.wrappedInvoice = invoice; }
}

class GPSDecorator extends InvoiceDecorator {
    private long days;
    public GPSDecorator(Invoice invoice, long days) { 
        super(invoice); 
        this.days = days; 
    }
    
    @Override 
    public double getTotal() { 
        // "It calculates the GPS cost ($5/day) and adds it to whatever invoice is inside it."
        return wrappedInvoice.getTotal() + (5.0 * days); 
    } 
}

// ==========================================
// 4. CONCURRENT ENGINE (The Double-Booking Fix)
// ==========================================

/* * INTERVIEW EXPLANATION: 
 * "This is the core of our system's thread safety. If two users try to book the exact 
 * same car for the exact same dates at the exact same millisecond, a standard system 
 * will double-book it. I am using ConcurrentHashMaps and ReentrantLocks mapped by 
 * License Plate to guarantee absolute data integrity." 
 */

class Branch {
    
    // "ConcurrentHashMap guarantees O(1) thread-safe lookups across multiple threads."
    private Map<String, Vehicle> fleet = new ConcurrentHashMap<>();
    
    /*
     * INTERVIEW BONUS POINT:
     * "I am using a CopyOnWriteArrayList for the schedules. In a highly concurrent environment 
     * where many users are reading the schedule to see if a car is free, but only a few 
     * are writing to it (booking), this structure prevents ConcurrentModificationExceptions."
     */
    private Map<String, List<Reservation>> vehicleSchedules = new ConcurrentHashMap<>();
    
    // "Stores a unique physical lock for every single car in the fleet."
    private Map<String, ReentrantLock> vehicleLocks = new ConcurrentHashMap<>();

    public void addVehicle(Vehicle v) {
        fleet.put(v.getLicensePlate(), v);
        vehicleSchedules.put(v.getLicensePlate(), new CopyOnWriteArrayList<>());
    }
    
    public Vehicle getVehicle(String licensePlate) { return fleet.get(licensePlate); }

    // "Helper method to verify date overlaps."
    private boolean isVehicleFreeForDates(String licensePlate, LocalDate start, LocalDate end) {
        for (Reservation r : vehicleSchedules.get(licensePlate)) {
            // "We only check against active/pending reservations. Canceled ones are ignored."
            if (r.getStatus() != ReservationStatus.CANCELED && 
                !start.isAfter(r.getEndDate()) && !end.isBefore(r.getStartDate())) {
                return false; // Collision found! The car is busy.
            }
        }
        return true;
    }

    /*
     * INTERVIEW EXPLANATION (FINE-GRAINED LOCKING):
     * "Instead of locking the entire Branch (which would mean only 1 person in the 
     * world could book a car at a time), I lock ONLY the specific vehicle being requested. 
     * This is called fine-grained locking and it allows massive system throughput."
     */
    public Reservation acquireLockAndReserve(String licensePlate, User user, LocalDate start, LocalDate end) throws Exception {
        Vehicle v = fleet.get(licensePlate);
        if (v == null || v.getStatus() != VehicleStatus.AVAILABLE) throw new Exception("Car is in maintenance.");

        // "Dynamically fetch or create a lock for this specific license plate."
        ReentrantLock lock = vehicleLocks.computeIfAbsent(licensePlate, k -> new ReentrantLock());
        
        lock.lock(); // CRITICAL SECTION STARTS HERE
        try {
            // "We MUST check if the car is free INSIDE the lock. If we checked outside, 
            // another thread could have snuck in and booked it right before we locked."
            if (!isVehicleFreeForDates(licensePlate, start, end)) {
                throw new Exception("Date collision: Car was just booked for those dates.");
            }
            
            // "Create the reservation and add it to the schedule safely."
            Reservation res = new Reservation(user, licensePlate, start, end);
            vehicleSchedules.get(licensePlate).add(res);
            return res; // Returns securely in the PENDING state
        } finally {
            // "Crucial: We MUST unlock inside a 'finally' block. If the code above threw 
            // an error and we didn't unlock here, this car would be permanently frozen forever!"
            lock.unlock(); 
        }
    }
}

// ==========================================
// 5. GLOBAL ORCHESTRATOR & MAIN
// ==========================================

/*
 * INTERVIEW EXPLANATION:
 * "The CarRentalSystem is a Singleton. It acts as the Facade for our backend. 
 * The mobile app or website only ever talks to this class. It orchestrates the 
 * validation, locking, pricing, and payment flow."
 */
class CarRentalSystem {
    private static final CarRentalSystem INSTANCE = new CarRentalSystem();
    private CarRentalSystem() {}
    public static CarRentalSystem getInstance() { return INSTANCE; }

    public void checkout(Branch branch, String licensePlate, User user, 
                         LocalDate start, LocalDate end, 
                         PricingStrategy pricing, PaymentStrategy payment, boolean addGps) {
        try {
            // 1. Domain Validation
            if (user.getDrivingLicense() == null) throw new Exception("Invalid Driving License.");

            // 2. Thread-Safe Booking (Creates a PENDING reservation)
            Reservation res = branch.acquireLockAndReserve(licensePlate, user, start, end);
            Vehicle vehicle = branch.getVehicle(licensePlate);
            
            // Calculate rental duration
            long days = ChronoUnit.DAYS.between(start, end);
            if (days == 0) days = 1; // Minimum 1 day charge

            // 3. Strategy Pattern: Calculate Base Price
            double baseCost = pricing.calculateBasePrice(vehicle, days);
            Invoice finalInvoice = new BaseInvoice(baseCost);

            // 4. Decorator Pattern: Wrap the invoice with Add-ons if requested
            if (addGps) {
                finalInvoice = new GPSDecorator(finalInvoice, days);
            }

            // 5. Strategy Pattern: Process Payment
            boolean paymentSuccess = payment.processPayment(finalInvoice.getTotal());
            
            // 6. State Machine Transition
            if (paymentSuccess) {
                res.setStatus(ReservationStatus.CONFIRMED);
                System.out.println("SUCCESS: Booking Confirmed for " + user.getUserId() + "! Total: $" + finalInvoice.getTotal());
            } else {
                res.setStatus(ReservationStatus.CANCELED);
                System.out.println("FAIL: Payment declined. Reservation canceled.");
            }
        } catch (Exception e) {
            System.out.println("ERROR for " + user.getUserId() + ": " + e.getMessage());
        }
    }
}

public class MainSimulation {
    public static void main(String[] args) {
        System.out.println("=== STARTING CAR RENTAL MULTITHREADED SIMULATION ===\n");
        
        CarRentalSystem system = CarRentalSystem.getInstance();
        Branch branch = new Branch();
        
        // Setup fleet
        branch.addVehicle(new Vehicle("NY-123", VehicleType.SUV, 100.0));

        // Setup users
        User alice = new User("U-01", "Alice", "DL-111");
        User bob = new User("U-02", "Bob", "DL-222");

        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 10); 

        // Simulation 1: Alice successfully books the car with GPS
        System.out.println("--> Alice is attempting to book NY-123...");
        system.checkout(branch, "NY-123", alice, start, end, 
                        new WeeklyDiscountPricingStrategy(), new CreditCardPayment(), true);

        // Simulation 2: Bob tries to book the exact same car for the exact same dates
        System.out.println("\n--> Bob is attempting to book NY-123 for the same dates...");
        // The ReentrantLock and date-checking logic ensures this fails safely!
        system.checkout(branch, "NY-123", bob, start, end, 
                        new WeeklyDiscountPricingStrategy(), new CreditCardPayment(), false);
    }
}

```

---

## 5. Next-Level Follow-Ups

If the interviewer asks "How do we deploy this to AWS with 1,000 servers?", drop these concepts:

* **Distributed Locking:** `ReentrantLock` only works on one server. In the real world, we would use **Redis Redlock** to ensure Server A and Server B don't book the same car.
* **Idempotency:** What if the user's phone lags and they click "Pay" twice? We need an **Idempotency Key** (a unique ID generated when they open the checkout screen) so the server knows it's the exact same request and doesn't charge them twice.
