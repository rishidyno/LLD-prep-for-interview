# 🚗 Ultimate Parking Lot Low-Level Design (LLD)

This repository contains a production-ready Low-Level Design for a Multilevel Parking Lot. It demonstrates advanced Object-Oriented Programming (OOP) concepts, object composition, and scalable business logic for real-world payment systems.

Throughout this guide, you will find **"Interview Scripts"**. These are plain-English, first-person explanations you can use during a system design interview to explain the *Why* behind your architectural choices.

---

## 🗣️ 1. The Interview Kickoff (Clarifying Questions)
*Never start coding without scoping the physical constraints and business rules!*

**Interview Script:** > "Before I start defining classes, I want to make sure I am modeling the physical world and business rules correctly:
> 1. **Physical Layout:** Are we designing a multi-level garage? 
> 2. **Vehicle Types:** Do we need to support different vehicle sizes (Motorcycles, Cars, Trucks) and map them to specific spot sizes?
> 3. **Assignment:** Should the system automatically find and assign the nearest available spot, or do users find their own?
> 4. **Payment Logic:** How do we handle pricing? Is it a flat rate, or hourly? Also, to protect customers from sudden surge pricing, should we lock in their pricing strategy at the exact moment they enter the gate?"

*(Assume the interviewer asks for a multi-level lot, automatic spot assignment based on vehicle size, and dynamic pricing locked in at entry).*

---

## 🏗️ 2. Core Architecture & Design Patterns



To build a robust and highly scalable parking garage, I utilized 3 core design patterns:

### A. The Singleton Pattern (The Building)
* **What:** Ensuring only one instance of a class exists.
* **Where:** The `ParkingLot` class.
* **Why:** A physical building is a single entity.
* **Interview Script:** *"I am making the ParkingLot a Singleton using Double-Checked Locking. If a junior developer accidentally types `new ParkingLot()`, they would create a phantom building in memory, and our entry gates would start assigning cars to spots that are already taken in the real world!"*

### B. The Strategy Pattern (The Math)

* **What:** Abstracting algorithms into interchangeable classes.
* **Where:** The `PricingStrategy` interface and its implementations (`HourlyPricingStrategy`, `EarlyBirdPricingStrategy`).
* **Why:** Business pricing changes constantly. We don't want to rewrite the core parking logic just because the CEO wants a weekend discount.
* **Interview Script:** *"I am using the Strategy Pattern for payments. The Exit Gate doesn't need to know the complex math of how a price is calculated; it just asks the Strategy for the final number. This follows the Open/Closed Principle perfectly."*

### C. The Factory Pattern (The Exit Gate Router)
* **What:** A system that creates or retrieves the correct object based on a condition.
* **Where:** Inside the `ExitGate`.
* **Why:** We stamp the Ticket with a simple Enum at entry, and the Exit Gate translates that Enum into complex math.
* **Interview Script:** *"To protect the customer from illegal surge pricing, the Entry Gate stamps their ticket with a `PricingProfile` (like EARLY_BIRD). When they leave, the Exit Gate acts as a Factory, reading that stamp and fetching the exact `PricingStrategy` they agreed to when they drove in."*

---

## 💻 3. The Complete Java Code

```java
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

// ==========================================
// 1. ENUMS (Strict Physical & Business Rules)
// ==========================================
/*
 * INTERVIEW EXPLANATION:
 * "I use Enums here to lock down the physical constraints of the garage. 
 * If we used Strings, a typo could cause a Truck to be assigned to a Motorcycle spot."
 */
enum VehicleType { MOTORCYCLE, CAR, TRUCK }
enum SpotType { MOTORCYCLE, COMPACT, LARGE }
enum PricingProfile { STANDARD_HOURLY, EARLY_BIRD, WEEKEND_FLAT_RATE }

// ==========================================
// 2. THE VEHICLE HIERARCHY (Abstraction)
// ==========================================
/*
 * INTERVIEW EXPLANATION:
 * "Vehicle is an abstract base class. This delegates the sizing logic to the 
 * vehicle itself. The Parking Lot never has to guess what size a Car is; 
 * it just asks the Car: 'What spot do you need?'"
 */
abstract class Vehicle {
    private String licensePlate;
    private VehicleType type;

    public Vehicle(String licensePlate, VehicleType type) {
        this.licensePlate = licensePlate;
        this.type = type;
    }

    public String getLicensePlate() { return licensePlate; }
    public VehicleType getType() { return type; }
    
    // "Every child class MUST define its physical size requirement."
    public abstract SpotType getRequiredSpotType();
}

class Motorcycle extends Vehicle {
    public Motorcycle(String licensePlate) { super(licensePlate, VehicleType.MOTORCYCLE); }
    @Override public SpotType getRequiredSpotType() { return SpotType.MOTORCYCLE; }
}

class Car extends Vehicle {
    public Car(String licensePlate) { super(licensePlate, VehicleType.CAR); }
    @Override public SpotType getRequiredSpotType() { return SpotType.COMPACT; }
}

class Truck extends Vehicle {
    public Truck(String licensePlate) { super(licensePlate, VehicleType.TRUCK); }
    @Override public SpotType getRequiredSpotType() { return SpotType.LARGE; }
}

// ==========================================
// 3. THE PARKING SPOT (Single Responsibility)
// ==========================================
class ParkingSpot {
    private String spotId;
    private SpotType spotType;
    private Vehicle parkedVehicle;

    public ParkingSpot(String spotId, SpotType spotType) {
        this.spotId = spotId;
        this.spotType = spotType;
        this.parkedVehicle = null;
    }

    public String getSpotId() { return spotId; }
    public SpotType getSpotType() { return spotType; }
    public Vehicle getParkedVehicle() { return parkedVehicle; }
    public boolean isAvailable() { return parkedVehicle == null; }

    // INTERVIEW SCRIPT: "The spot does a final safety check before allowing a car to park."
    public boolean parkVehicle(Vehicle vehicle) {
        if (isAvailable() && vehicle.getRequiredSpotType() == this.spotType) {
            this.parkedVehicle = vehicle;
            return true;
        }
        return false;
    }

    public void removeVehicle() { this.parkedVehicle = null; }
}

// ==========================================
// 4. THE PARKING LEVEL (The Search Algorithm)
// ==========================================
class ParkingLevel {
    private int floorNumber;
    
    /*
     * INTERVIEW BONUS POINT:
     * "Instead of putting all spots in one massive list, I categorize them into a Map. 
     * If a Truck enters, we instantly skip all Compact spots in O(1) time and only 
     * search the Large spots. This makes spot assignment incredibly fast."
     */
    private Map<SpotType, List<ParkingSpot>> spotMap;

    public ParkingLevel(int floorNumber) {
        this.floorNumber = floorNumber;
        this.spotMap = new HashMap<>();
        spotMap.put(SpotType.MOTORCYCLE, new ArrayList<>());
        spotMap.put(SpotType.COMPACT, new ArrayList<>());
        spotMap.put(SpotType.LARGE, new ArrayList<>());
    }

    public int getFloorNumber() { return floorNumber; }

    public void addSpot(ParkingSpot spot) {
        spotMap.get(spot.getSpotType()).add(spot);
    }

    public ParkingSpot findAvailableSpot(SpotType requiredType) {
        for (ParkingSpot spot : spotMap.get(requiredType)) {
            if (spot.isAvailable()) return spot;
        }
        return null;
    }

    public ParkingSpot parkVehicle(Vehicle vehicle) {
        ParkingSpot availableSpot = findAvailableSpot(vehicle.getRequiredSpotType());
        if (availableSpot != null) {
            availableSpot.parkVehicle(vehicle);
            return availableSpot;
        }
        return null; 
    }
}

// ==========================================
// 5. THE PARKING LOT (The Singleton)
// ==========================================
class ParkingLot {
    // "Volatile ensures thread safety across multiple entry gates."
    private static volatile ParkingLot instance;
    private List<ParkingLevel> levels;

    private ParkingLot() { this.levels = new ArrayList<>(); }

    // "Double-Checked Locking for high-performance Singleton instantiation."
    public static ParkingLot getInstance() {
        if (instance == null) {
            synchronized (ParkingLot.class) {
                if (instance == null) {
                    instance = new ParkingLot();
                }
            }
        }
        return instance;
    }

    public void addLevel(ParkingLevel level) { levels.add(level); }

    // "Iterates through floors from bottom to top until a spot is found."
    public ParkingSpot assignSpot(Vehicle vehicle) {
        for (ParkingLevel level : levels) {
            ParkingSpot parkedSpot = level.parkVehicle(vehicle);
            if (parkedSpot != null) return parkedSpot;
        }
        return null; // Garage is completely full for this size!
    }

    public void freeSpot(ParkingSpot spot) { spot.removeVehicle(); }
}

// ==========================================
// 6. TICKETS & PRICING STRATEGY (Business Logic)
// ==========================================
class Ticket {
    private String ticketId;
    private Vehicle parkedVehicle;
    private ParkingSpot allocatedSpot;
    private LocalDateTime entryTime;
    
    /*
     * INTERVIEW EXPLANATION:
     * "We lock in the pricing profile the second they enter! This protects them 
     * from surge pricing later in the day, but it remains lightweight enough 
     * to easily save to a SQL database."
     */
    private PricingProfile assignedProfile; 

    public Ticket(Vehicle parkedVehicle, ParkingSpot allocatedSpot, PricingProfile profile) {
        this.ticketId = UUID.randomUUID().toString();
        this.parkedVehicle = parkedVehicle;
        this.allocatedSpot = allocatedSpot;
        this.entryTime = LocalDateTime.now();
        this.assignedProfile = profile; 
    }

    public String getTicketId() { return ticketId; }
    public Vehicle getParkedVehicle() { return parkedVehicle; }
    public ParkingSpot getAllocatedSpot() { return allocatedSpot; }
    public LocalDateTime getEntryTime() { return entryTime; }
    public PricingProfile getAssignedProfile() { return assignedProfile; }
}

// STRATEGY PATTERN INTERFACE
interface PricingStrategy {
    double calculatePrice(Ticket ticket, LocalDateTime exitTime);
}

class HourlyPricingStrategy implements PricingStrategy {
    private double hourlyRate;
    public HourlyPricingStrategy(double hourlyRate) { this.hourlyRate = hourlyRate; }

    @Override
    public double calculatePrice(Ticket ticket, LocalDateTime exitTime) {
        long minutes = Duration.between(ticket.getEntryTime(), exitTime).toMinutes();
        long hours = (long) Math.ceil(minutes / 60.0);
        if (hours == 0) hours = 1; // Minimum 1 hour charge
        return hours * hourlyRate;
    }
}

class EarlyBirdPricingStrategy implements PricingStrategy {
    @Override
    public double calculatePrice(Ticket ticket, LocalDateTime exitTime) {
        return 10.0; // Flat $10 fee for early birds, regardless of time spent!
    }
}

// ==========================================
// 7. THE GATES (Entry & Exit Terminals)
// ==========================================
class EntryGate {
    public Ticket processEntry(Vehicle vehicle) {
        ParkingSpot spot = ParkingLot.getInstance().assignSpot(vehicle);
        if (spot == null) {
            System.out.println("ENTRY DENIED: Lot is full for " + vehicle.getType());
            return null;
        }

        // "Business Logic: If it's before 8 AM, stamp it as EARLY_BIRD."
        PricingProfile profile = (LocalDateTime.now().getHour() < 8) 
            ? PricingProfile.EARLY_BIRD 
            : PricingProfile.STANDARD_HOURLY;

        Ticket ticket = new Ticket(vehicle, spot, profile);
        System.out.println("ENTRY GRANTED: " + vehicle.getLicensePlate() + 
                           " parked at " + spot.getSpotId() + " with profile " + profile);
        return ticket;
    }
}

class ExitGate {
    public double processExit(Ticket ticket) {
        PricingStrategy strategy;

        // "FACTORY LOGIC: We map their locked-in stamp to the actual math algorithm."
        switch (ticket.getAssignedProfile()) {
            case EARLY_BIRD:
                strategy = new EarlyBirdPricingStrategy();
                break;
            case WEEKEND_FLAT_RATE:
                strategy = new HourlyPricingStrategy(20.0); // Assuming $20 flat weekend
                break;
            case STANDARD_HOURLY:
            default:
                strategy = new HourlyPricingStrategy(5.0); // Standard $5/hour
                break;
        }

        // "Calculate fee based on their locked-in rate!"
        double finalPrice = strategy.calculatePrice(ticket, LocalDateTime.now());
        
        // "Free up the physical spot."
        ParkingLot.getInstance().freeSpot(ticket.getAllocatedSpot());
        
        System.out.println("EXIT PROCESSED: " + ticket.getParkedVehicle().getLicensePlate() + 
                           " paid $" + finalPrice + ". Spot " + 
                           ticket.getAllocatedSpot().getSpotId() + " is now free.");
        return finalPrice;
    }
}

// ==========================================
// 8. MAIN EXECUTION (Simulation)
// ==========================================
public class ParkingLotLLD {
    public static void main(String[] args) {
        System.out.println("=== STARTING PARKING LOT SIMULATION ===");

        // 1. Build the physical lot
        ParkingLot lot = ParkingLot.getInstance();
        ParkingLevel floor1 = new ParkingLevel(1);
        
        // Add 1 Compact Spot and 1 Large Spot to Floor 1
        floor1.addSpot(new ParkingSpot("1A", SpotType.COMPACT));
        floor1.addSpot(new ParkingSpot("1B", SpotType.LARGE));
        lot.addLevel(floor1);

        // 2. Setup Terminals
        EntryGate entryGate = new EntryGate();
        ExitGate exitGate = new ExitGate();

        // 3. Simulate Cars Entering
        System.out.println("\n[SIMULATION] Car 1 (Compact) Enters...");
        Vehicle car1 = new Car("ABC-123");
        Ticket ticket1 = entryGate.processEntry(car1);

        System.out.println("\n[SIMULATION] Truck 1 (Large) Enters...");
        Vehicle truck1 = new Truck("BIG-BOY");
        Ticket ticket2 = entryGate.processEntry(truck1);

        System.out.println("\n[SIMULATION] Car 2 (Compact) Enters...");
        Vehicle car2 = new Car("XYZ-999");
        // This will fail because our only Compact spot is taken by Car 1!
        Ticket ticket3 = entryGate.processEntry(car2); 

        // 4. Simulate Car Leaving
        System.out.println("\n[SIMULATION] Car 1 (Compact) Leaves...");
        if (ticket1 != null) {
            exitGate.processExit(ticket1);
        }
        
        System.out.println("\n=== SIMULATION COMPLETE ===");
    }
}
