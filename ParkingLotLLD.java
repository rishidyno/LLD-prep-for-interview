import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * ============================================================================
 * ULTIMATE PARKING LOT LOW-LEVEL DESIGN (PRODUCTION READY)
 * ============================================================================
 * INTERVIEW SCRIPT / INTRODUCTION:
 * "To design a scalable Parking Lot, I am separating the physical constraints 
 * (the building) from the business logic (the payments). I will use 3 main patterns:
 * 1. SINGLETON PATTERN: To ensure our system only ever has one 'Building' in memory.
 * 2. STRATEGY PATTERN: To allow the business to change pricing rules dynamically.
 * 3. FACTORY PATTERN: To map a customer's entry time to a specific payment algorithm.
 * ============================================================================
 */

// ==========================================
// 1. ENUMS (Strict Physical & Business Rules)
// ==========================================

/*
 * INTERVIEW EXPLANATION:
 * "I use Enums here to lock down the physical constraints of the garage. 
 * If we used Strings like 'Large', a typo like 'Lrg' could cause a Truck to 
 * be assigned to a Motorcycle spot and crash the system. Enums guarantee safety."
 */
enum VehicleType { MOTORCYCLE, CAR, TRUCK }
enum SpotType { MOTORCYCLE, COMPACT, LARGE }

/*
 * INTERVIEW EXPLANATION:
 * "This Enum solves a massive real-world business problem: Surge Pricing. 
 * We stamp the ticket with this profile at entry. This legally protects the 
 * customer so they get the price they agreed to, even if the manager changes 
 * the global rate while they are parked."
 */
enum PricingProfile { STANDARD_HOURLY, EARLY_BIRD, WEEKEND_FLAT_RATE }


// ==========================================
// 2. THE VEHICLE HIERARCHY (Abstraction)
// ==========================================

/*
 * INTERVIEW EXPLANATION:
 * "Vehicle is an abstract base class. This follows the Open/Closed Principle. 
 * If the business decides to support 'Electric Vehicles' tomorrow, I don't touch 
 * the Parking Lot code. I just add a new ElectricCar class that extends this one."
 */
abstract class Vehicle {
    private String licensePlate;
    private VehicleType type;

    // "Every vehicle must register its license plate and what type it is."
    public Vehicle(String licensePlate, VehicleType type) {
        this.licensePlate = licensePlate;
        this.type = type;
    }

    public String getLicensePlate() { return licensePlate; }
    public VehicleType getType() { return type; }
    
    /*
     * INTERVIEW BONUS POINT:
     * "This is a crucial abstract method. It delegates the sizing logic to the 
     * vehicle itself. The Parking Lot never has to guess what size a Car is; 
     * it just asks the Car: 'What spot do you physically need?'"
     */
    public abstract SpotType getRequiredSpotType();
}

// --- CONCRETE VEHICLES ---

class Motorcycle extends Vehicle {
    public Motorcycle(String licensePlate) { super(licensePlate, VehicleType.MOTORCYCLE); }
    // "Motorcycles explicitly tell the system they need a Motorcycle spot."
    @Override public SpotType getRequiredSpotType() { return SpotType.MOTORCYCLE; }
}

class Car extends Vehicle {
    public Car(String licensePlate) { super(licensePlate, VehicleType.CAR); }
    // "Cars explicitly tell the system they need a Compact spot."
    @Override public SpotType getRequiredSpotType() { return SpotType.COMPACT; }
}

class Truck extends Vehicle {
    public Truck(String licensePlate) { super(licensePlate, VehicleType.TRUCK); }
    // "Trucks explicitly tell the system they need a Large spot."
    @Override public SpotType getRequiredSpotType() { return SpotType.LARGE; }
}


// ==========================================
// 3. THE PARKING SPOT (Single Responsibility)
// ==========================================

/*
 * INTERVIEW EXPLANATION:
 * "The ParkingSpot class follows the Single Responsibility Principle. It doesn't 
 * calculate money, and it doesn't know what floor it is on. It only tracks its 
 * own ID, its physical size, and the specific Vehicle currently sitting in it."
 */
class ParkingSpot {
    private String spotId;
    private SpotType spotType;
    private Vehicle parkedVehicle;

    public ParkingSpot(String spotId, SpotType spotType) {
        this.spotId = spotId;
        this.spotType = spotType;
        this.parkedVehicle = null; // "Spots always start empty."
    }

    public String getSpotId() { return spotId; }
    public SpotType getSpotType() { return spotType; }
    public Vehicle getParkedVehicle() { return parkedVehicle; }
    
    // "O(1) instant check to see if a car is sitting here."
    public boolean isAvailable() { return parkedVehicle == null; }

    /*
     * INTERVIEW EXPLANATION:
     * "The spot acts as the final security guard. Before a vehicle can physically 
     * park, this method guarantees the spot is empty AND the vehicle size matches 
     * the spot size. If a Truck tries to park in a Compact spot, it fails safely."
     */
    public boolean parkVehicle(Vehicle vehicle) {
        if (isAvailable() && vehicle.getRequiredSpotType() == this.spotType) {
            this.parkedVehicle = vehicle;
            return true;
        }
        return false;
    }

    // "Simply wipes the memory of the vehicle so the spot can be reused."
    public void removeVehicle() { this.parkedVehicle = null; }
}


// ==========================================
// 4. THE PARKING LEVEL (The Search Algorithm)
// ==========================================



class ParkingLevel {
    private int floorNumber;
    
    /*
     * INTERVIEW BONUS POINT (DATA STRUCTURE TRICK):
     * "Instead of putting all 500 spots in one massive List, I categorize them into 
     * a HashMap. If a massive Truck enters, we instantly jump to the 'LARGE' list 
     * in O(1) time and skip searching hundreds of Compact spots. This makes spot 
     * assignment incredibly fast at scale."
     */
    private Map<SpotType, List<ParkingSpot>> spotMap;

    public ParkingLevel(int floorNumber) {
        this.floorNumber = floorNumber;
        this.spotMap = new HashMap<>();
        
        // "Initialize the empty lists for each spot category."
        spotMap.put(SpotType.MOTORCYCLE, new ArrayList<>());
        spotMap.put(SpotType.COMPACT, new ArrayList<>());
        spotMap.put(SpotType.LARGE, new ArrayList<>());
    }

    public int getFloorNumber() { return floorNumber; }

    // "Helper to physically build the floor when the garage is constructed."
    public void addSpot(ParkingSpot spot) {
        spotMap.get(spot.getSpotType()).add(spot);
    }

    /*
     * INTERVIEW EXPLANATION:
     * "Because we categorized our spots in a Map, this algorithm is extremely fast. 
     * We just grab the specific list for the vehicle's required size and find the 
     * first available one."
     */
    public ParkingSpot findAvailableSpot(SpotType requiredType) {
        for (ParkingSpot spot : spotMap.get(requiredType)) {
            if (spot.isAvailable()) return spot;
        }
        return null; // "This floor has no empty spots of that size."
    }

    // "The Level finds a spot, and if it exists, it tells the spot to accept the car."
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
// 5. THE PARKING LOT (The Singleton Orchestrator)
// ==========================================

class ParkingLot {
    /*
     * INTERVIEW EXPLANATION:
     * "I am making the ParkingLot a Singleton. We use the 'volatile' keyword 
     * to ensure that if multiple Entry Gate threads update the lot at the exact 
     * same time, they all see the same synchronized memory."
     */
    private static volatile ParkingLot instance;
    private List<ParkingLevel> levels;

    // "Private constructor guarantees no one can accidentally type 'new ParkingLot()'."
    private ParkingLot() { this.levels = new ArrayList<>(); }

    /*
     * INTERVIEW BONUS POINT:
     * "This is Double-Checked Locking. If 4 cars pull up to 4 different gates at 
     * the exact same millisecond, this ensures they share the exact same building 
     * without locking down the entire method and slowing down the whole system."
     */
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

    /*
     * INTERVIEW EXPLANATION:
     * "This checks the building floor by floor, from bottom to top. As soon as a 
     * floor successfully parks the car, we instantly return the physical spot."
     */
    public ParkingSpot assignSpot(Vehicle vehicle) {
        for (ParkingLevel level : levels) {
            ParkingSpot parkedSpot = level.parkVehicle(vehicle);
            if (parkedSpot != null) return parkedSpot;
        }
        return null; // "The entire garage is full for this size!"
    }

    // "Tells the physical spot to wipe its memory of the car."
    public void freeSpot(ParkingSpot spot) { spot.removeVehicle(); }
}


// ==========================================
// 6. TICKETS & PRICING STRATEGY (Business Logic)
// ==========================================

/*
 * INTERVIEW EXPLANATION:
 * "The Ticket is a simple Data Transfer Object (DTO). We lock in their PricingProfile 
 * the exact second they enter. This protects the customer from surge pricing, but 
 * it is just an Enum, making it lightweight enough to easily save to a SQL database."
 */
class Ticket {
    private String ticketId;
    private Vehicle parkedVehicle;
    private ParkingSpot allocatedSpot;
    
    // "We use Java's modern LocalDateTime for thread-safe time tracking."
    private LocalDateTime entryTime;
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


// --- STRATEGY PATTERN ---

/*
 * WHAT THIS IS: The Strategy Design Pattern.
 * WHY IT MATTERS: The Exit Gate doesn't need to know HOW the math works. It just 
 * passes the ticket to this interface and gets back a dollar amount. We can add 
 * 100 new pricing rules without ever touching the Exit Gate code.
 */
interface PricingStrategy {
    double calculatePrice(Ticket ticket, LocalDateTime exitTime);
}

class HourlyPricingStrategy implements PricingStrategy {
    private double hourlyRate;
    public HourlyPricingStrategy(double hourlyRate) { this.hourlyRate = hourlyRate; }

    @Override
    public double calculatePrice(Ticket ticket, LocalDateTime exitTime) {
        long minutes = Duration.between(ticket.getEntryTime(), exitTime).toMinutes();
        
        // "Math.ceil rounds up. 65 minutes becomes 2 hours. Minimum charge is 1 hour."
        long hours = (long) Math.ceil(minutes / 60.0);
        if (hours == 0) hours = 1; 
        
        return hours * hourlyRate;
    }
}

class EarlyBirdPricingStrategy implements PricingStrategy {
    @Override
    public double calculatePrice(Ticket ticket, LocalDateTime exitTime) {
        // "Early birds pay a flat $10, no matter how long they stay!"
        return 10.0; 
    }
}


// ==========================================
// 7. THE GATES (Entry & Exit Terminals)
// ==========================================

/*
 * INTERVIEW EXPLANATION:
 * "The EntryGate asks the Singleton Lot for a spot. If it gets one, it checks the 
 * time, applies the correct business PricingProfile (like Early Bird), and creates 
 * the Ticket."
 */
class EntryGate {
    public Ticket processEntry(Vehicle vehicle) {
        ParkingSpot spot = ParkingLot.getInstance().assignSpot(vehicle);
        
        if (spot == null) {
            System.out.println("ENTRY DENIED: Lot is full for " + vehicle.getType());
            return null;
        }

        // "Business Logic: If it is before 8 AM, stamp it as EARLY_BIRD."
        PricingProfile profile = (LocalDateTime.now().getHour() < 8) 
            ? PricingProfile.EARLY_BIRD 
            : PricingProfile.STANDARD_HOURLY;

        Ticket ticket = new Ticket(vehicle, spot, profile);
        System.out.println("ENTRY GRANTED: " + vehicle.getLicensePlate() + 
                           " parked at " + spot.getSpotId() + " with profile " + profile);
        return ticket;
    }
}

/*
 * INTERVIEW EXPLANATION:
 * "The ExitGate acts as a Factory. It reads the Ticket's locked-in stamp, dynamically 
 * spawns the exact Math Strategy the customer agreed to at entry, calculates the bill, 
 * and then frees up the physical spot."
 */
class ExitGate {
    public double processExit(Ticket ticket) {
        PricingStrategy strategy;

        // "FACTORY LOGIC: Map the stamp to the math algorithm."
        switch (ticket.getAssignedProfile()) {
            case EARLY_BIRD:
                strategy = new EarlyBirdPricingStrategy();
                break;
            case WEEKEND_FLAT_RATE:
                strategy = new HourlyPricingStrategy(20.0); // Flat $20
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

        // 1. Build the physical building (Singleton)
        ParkingLot lot = ParkingLot.getInstance();
        
        // 2. Build Floor 1
        ParkingLevel floor1 = new ParkingLevel(1);
        
        // Add 1 Compact Spot and 1 Large Spot to Floor 1
        floor1.addSpot(new ParkingSpot("1A", SpotType.COMPACT));
        floor1.addSpot(new ParkingSpot("1B", SpotType.LARGE));
        lot.addLevel(floor1);

        // 3. Setup Terminals
        EntryGate entryGate = new EntryGate();
        ExitGate exitGate = new ExitGate();

        // --- SIMULATING THE REAL WORLD ---

        System.out.println("\n[SIMULATION] Car 1 (Compact) Enters...");
        Vehicle car1 = new Car("ABC-123");
        Ticket ticket1 = entryGate.processEntry(car1); // Successfully parks at 1A

        System.out.println("\n[SIMULATION] Truck 1 (Large) Enters...");
        Vehicle truck1 = new Truck("BIG-BOY");
        Ticket ticket2 = entryGate.processEntry(truck1); // Successfully parks at 1B

        System.out.println("\n[SIMULATION] Car 2 (Compact) Enters...");
        Vehicle car2 = new Car("XYZ-999");
        // This will FAIL because our only Compact spot (1A) is taken by Car 1!
        Ticket ticket3 = entryGate.processEntry(car2); 

        System.out.println("\n[SIMULATION] Car 1 (Compact) Leaves...");
        if (ticket1 != null) {
            // Car 1 pays and leaves, freeing up spot 1A
            exitGate.processExit(ticket1); 
        }
        
        System.out.println("\n=== SIMULATION COMPLETE ===");
    }
}
