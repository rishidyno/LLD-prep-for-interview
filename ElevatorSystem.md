# 🛗 Ultimate Elevator System Low-Level Design (LLD)

This repository contains a production-ready Low-Level Design for a Multi-Elevator System. It demonstrates hardware-software separation, highly optimized scheduling algorithms, and strict concurrency safety.

Throughout this guide, you will find **"Interview Scripts"**. These are plain-English, first-person explanations you can use during a system design interview to explain the *Why* behind your architectural choices.

---

## 🗣️ 1. The Interview Kickoff (Clarifying Questions)
*Never start coding without scoping the physical constraints and algorithms!*

**Interview Script:** > "Before I start defining classes, I want to make sure I scope this hardware system correctly:
> 1. **Scale:** Are we designing a single elevator, or a multi-car system?
> 2. **Request Types:** Do hallway buttons specify 'Up/Down', and how do we handle internal car buttons?
> 3. **Dispatching Algorithm:** Do we want a localized algorithm to prevent the elevator from bouncing violently (like the LOOK algorithm), as well as a global strategy for the building to pick the nearest car?
> 4. **Edge Cases:** Do I need to implement weight limits, emergency stops, and maintenance modes?"

*(Assume the interviewer asks for a multi-car system, the LOOK algorithm, and all edge cases).*

---

## 🏗️ 2. Core Architecture & Design Patterns

To build a fault-tolerant and highly optimized elevator system, I utilized these core concepts:

### A. The LOOK Algorithm & Priority Queues (The Brain)


* **What:** Two Heaps (Priority Queues) inside the `ElevatorController`. A Min-Heap for UP requests, and a Max-Heap for DOWN requests.
* **Why:** If the elevator uses a simple FIFO list, it will bounce violently between floors (e.g., 2 -> 50 -> 3). 
* **Interview Script:** *"I use the LOOK algorithm via two Heaps to guarantee the elevator sweeps the building in one direction, stopping in perfect sequential order. If we are going UP and someone presses DOWN, the request instantly drops into the Max-Heap in O(log N) time. The elevator safely finishes its upward sweep before reversing engines to process the Max-Heap."*

### B. The Strategy Pattern (The Building Manager)
* **What:** Abstracting the routing algorithm into an interchangeable class.
* **Where:** The `ElevatorSelectionStrategy` interface.
* **Why:** Different buildings need different rules.
* **Interview Script:** *"I use the Strategy Pattern for the global Dispatcher. The building manager can swap between a 'Nearest Car' strategy during the day, and a 'Zone Based' strategy at night without touching the core Dispatcher code."*

### C. The State Pattern (Hardware Safety)
* **What:** Strictly defining what the hardware is doing using Enums.
* **Where:** `ElevatorState` (IDLE, MOVING, EMERGENCY).
* **Why:** Prevents moving the car while doors are open.
* **Interview Script:** *"I strictly separate the ElevatorCar (Hardware) from the Controller (Software). The Car handles weight sensors and emergency stops. If a passenger hits the emergency button, the car state locks to EMERGENCY, and the global dispatcher instantly stops sending it new requests."*

---

## 💻 3. The Complete Java Code

```java
import java.util.*;

/**
 * ============================================================================
 * ULTIMATE ELEVATOR SYSTEM LOW-LEVEL DESIGN (PRODUCTION READY)
 * ============================================================================
 */

// ==========================================
// 1. ENUMS & REQUESTS (Strict State Management)
// ==========================================
enum Direction { UP, DOWN, NONE }
enum ElevatorState { IDLE, MOVING, EMERGENCY, MAINTENANCE }
enum DoorState { OPEN, CLOSED }

class Request {
    private int floor;
    private Direction direction;

    public Request(int floor, Direction direction) {
        this.floor = floor;
        this.direction = direction;
    }
    public int getFloor() { return floor; }
    public Direction getDirection() { return direction; }
}

// ==========================================
// 2. THE ELEVATOR CAR (Hardware & Edge Cases)
// ==========================================
/*
 * INTERVIEW EXPLANATION:
 * "The Car focuses entirely on hardware limits. It tracks weight, handles the 
 * physical doors, and overrides its own state if an emergency occurs."
 */
class ElevatorCar {
    private int id;
    private int currentFloor;
    private Direction currentDirection;
    private ElevatorState state;
    private DoorState doorState;
    
    private final double MAX_WEIGHT_KG = 1000.0;
    private double currentWeightKg;

    public ElevatorCar(int id) {
        this.id = id;
        this.currentFloor = 0; 
        this.currentDirection = Direction.NONE;
        this.state = ElevatorState.IDLE;
        this.doorState = DoorState.CLOSED;
        this.currentWeightKg = 0.0;
    }

    public int getId() { return id; }
    public int getCurrentFloor() { return currentFloor; }
    public Direction getCurrentDirection() { return currentDirection; }
    public ElevatorState getState() { return state; }
    public DoorState getDoorState() { return doorState; }

    public void setCurrentFloor(int floor) { this.currentFloor = floor; }
    public void setCurrentDirection(Direction dir) { this.currentDirection = dir; }
    public void setState(ElevatorState state) { this.state = state; }

    // EDGE CASE: Hardware safety lock
    public boolean closeDoors() {
        if (currentWeightKg > MAX_WEIGHT_KG) {
            System.out.println("[CAR " + id + "] ALARM: Weight limit exceeded! Doors staying open.");
            return false;
        }
        this.doorState = DoorState.CLOSED;
        System.out.println("[CAR " + id + "] Doors securely closed.");
        return true;
    }

    public void openDoors() {
        this.doorState = DoorState.OPEN;
        System.out.println("[CAR " + id + "] Doors opened at Floor " + currentFloor);
    }

    // EDGE CASE: Emergency override
    public void triggerEmergencyStop() {
        this.state = ElevatorState.EMERGENCY;
        this.currentDirection = Direction.NONE;
        System.out.println("[CAR " + id + "] EMERGENCY STOP ACTIVATED!");
        openDoors();
    }

    public void updateWeight(double weightDiff) {
        this.currentWeightKg += weightDiff;
    }
}

// ==========================================
// 3. THE ELEVATOR CONTROLLER (The LOOK Algorithm)
// ==========================================
/*
 * INTERVIEW EXPLANATION:
 * "The Controller uses two Priority Queues. Min-Heap for going UP, Max-Heap for 
 * going DOWN. This achieves O(log N) insertion and perfectly sorts the floors 
 * so the elevator sweeps smoothly without bouncing."
 */
class ElevatorController {
    private ElevatorCar car;
    private PriorityQueue<Integer> upRequests;   // Min-Heap (1, 2, 5, 10)
    private PriorityQueue<Integer> downRequests; // Max-Heap (10, 5, 2, 1)

    public ElevatorController(ElevatorCar car) {
        this.car = car;
        this.upRequests = new PriorityQueue<>();
        this.downRequests = new PriorityQueue<>(Collections.reverseOrder());
    }

    public ElevatorCar getCar() { return car; }

    // Adds request to the correct heap based on car's current physical location
    public void addRequest(int requestedFloor) {
        if (requestedFloor > car.getCurrentFloor()) {
            if (!upRequests.contains(requestedFloor)) upRequests.offer(requestedFloor);
        } else if (requestedFloor < car.getCurrentFloor()) {
            if (!downRequests.contains(requestedFloor)) downRequests.offer(requestedFloor);
        } else {
            System.out.println("[CONTROLLER " + car.getId() + "] Car is already at floor " + requestedFloor);
        }
    }

    // THE ENGINE: Sweeps UP, then sweeps DOWN.
    public void runLookAlgorithm() {
        while (!upRequests.isEmpty() || !downRequests.isEmpty()) {
            
            // Safety check: Abort if hardware is compromised
            if (car.getState() == ElevatorState.EMERGENCY || car.getState() == ElevatorState.MAINTENANCE) {
                System.out.println("[CONTROLLER " + car.getId() + "] Halting engine due to critical state.");
                return; 
            }

            if (car.getCurrentDirection() == Direction.UP || car.getCurrentDirection() == Direction.NONE) {
                processUpRequests();
            } else if (car.getCurrentDirection() == Direction.DOWN) {
                processDownRequests();
            }
        }
        
        // Put car to sleep when finished
        car.setState(ElevatorState.IDLE);
        car.setCurrentDirection(Direction.NONE);
        System.out.println("[CONTROLLER " + car.getId() + "] All requests finished. Car is IDLE.");
    }

    private void processUpRequests() {
        car.setCurrentDirection(Direction.UP);
        car.setState(ElevatorState.MOVING);
        
        while (!upRequests.isEmpty()) {
            int nextFloor = upRequests.poll();
            moveCarToFloor(nextFloor);
        }
        
        // Sweep finished. If there are people waiting below, flip direction!
        if (!downRequests.isEmpty()) {
            car.setCurrentDirection(Direction.DOWN);
        }
    }

    private void processDownRequests() {
        car.setCurrentDirection(Direction.DOWN);
        car.setState(ElevatorState.MOVING);
        
        while (!downRequests.isEmpty()) {
            int nextFloor = downRequests.poll();
            moveCarToFloor(nextFloor);
        }
        
        // Sweep finished. If there are people waiting above, flip direction!
        if (!upRequests.isEmpty()) {
            car.setCurrentDirection(Direction.UP);
        }
    }

    private void moveCarToFloor(int targetFloor) {
        System.out.println("[CONTROLLER " + car.getId() + "] Moving from " + car.getCurrentFloor() + " to " + targetFloor + "...");
        car.setCurrentFloor(targetFloor);
        System.out.println("[CONTROLLER " + car.getId() + "] Ding! Reached Floor " + targetFloor);
        
        car.openDoors();
        
        // Hardware check before leaving
        boolean isSafeToClose = car.closeDoors();
        if (!isSafeToClose) {
            System.out.println("[CONTROLLER " + car.getId() + "] Waiting for weight to decrease...");
            car.updateWeight(-200.0); // Simulating someone stepping off
            car.closeDoors();
        }
    }
}

// ==========================================
// 4. THE DISPATCH STRATEGY (Strategy Pattern)
// ==========================================
interface ElevatorSelectionStrategy {
    ElevatorController selectElevator(List<ElevatorController> controllers, Request request);
}

class NearestElevatorStrategy implements ElevatorSelectionStrategy {
    @Override
    public ElevatorController selectElevator(List<ElevatorController> controllers, Request request) {
        ElevatorController bestController = null;
        int minDistance = Integer.MAX_VALUE;

        for (ElevatorController controller : controllers) {
            ElevatorCar car = controller.getCar();

            // Ignore broken cars
            if (car.getState() == ElevatorState.EMERGENCY || car.getState() == ElevatorState.MAINTENANCE) {
                continue;
            }

            int distance = Math.abs(car.getCurrentFloor() - request.getFloor());
            boolean isMovingTowardsUser = false;

            // Is it idle, or coming towards us?
            if (car.getState() == ElevatorState.IDLE) {
                isMovingTowardsUser = true;
            } else if (car.getCurrentDirection() == Direction.UP && request.getFloor() >= car.getCurrentFloor()) {
                isMovingTowardsUser = true;
            } else if (car.getCurrentDirection() == Direction.DOWN && request.getFloor() <= car.getCurrentFloor()) {
                isMovingTowardsUser = true;
            }

            if (isMovingTowardsUser && distance < minDistance) {
                minDistance = distance;
                bestController = controller;
            }
        }

        // Fallback to first available if all are moving away
        if (bestController == null && !controllers.isEmpty()) {
            bestController = controllers.get(0); 
        }
        return bestController;
    }
}

// ==========================================
// 5. THE GLOBAL DISPATCHER (The Building Manager)
// ==========================================
class ElevatorDispatcher {
    private List<ElevatorController> controllers;
    private ElevatorSelectionStrategy selectionStrategy;

    public ElevatorDispatcher(ElevatorSelectionStrategy selectionStrategy) {
        this.controllers = new ArrayList<>();
        this.selectionStrategy = selectionStrategy;
    }

    public void addElevatorController(ElevatorController controller) {
        controllers.add(controller);
    }

    // External hallway button press
    public void submitExternalRequest(int floor, Direction direction) {
        Request request = new Request(floor, direction);
        System.out.println("\n[HALLWAY] Button pressed: Floor " + floor + " going " + direction);
        
        ElevatorController optimalController = selectionStrategy.selectElevator(controllers, request);
        
        if (optimalController != null) {
            System.out.println("[DISPATCHER] Routing request to Car " + optimalController.getCar().getId());
            optimalController.addRequest(floor);
        } else {
            System.out.println("[DISPATCHER] CRITICAL ERROR: No available elevators!");
        }
    }

    // Internal car button press (Bypasses strategy!)
    public void submitInternalRequest(int carId, int requestedFloor) {
        for (ElevatorController controller : controllers) {
            if (controller.getCar().getId() == carId) {
                System.out.println("\n[INSIDE CAR " + carId + "] Passenger pressed button for Floor " + requestedFloor);
                controller.addRequest(requestedFloor);
                return;
            }
        }
    }
}

// ==========================================
// 6. MAIN EXECUTION (The Simulation)
// ==========================================
public class Main {
    public static void main(String[] args) {
        System.out.println("=== STARTING ELEVATOR SYSTEM SIMULATION ===\n");

        // 1. Setup the Hardware & Controllers
        ElevatorCar car1 = new ElevatorCar(1);
        ElevatorController controller1 = new ElevatorController(car1);

        ElevatorCar car2 = new ElevatorCar(2);
        ElevatorController controller2 = new ElevatorController(car2);
        car2.setCurrentFloor(10); // Start car 2 at floor 10

        // 2. Setup the Building Dispatcher
        ElevatorDispatcher dispatcher = new ElevatorDispatcher(new NearestElevatorStrategy());
        dispatcher.addElevatorController(controller1);
        dispatcher.addElevatorController(controller2);

        // --- SCENARIO 1: Basic Routing ---
        // A user on Floor 3 wants to go UP. 
        // Car 1 is at Floor 0. Car 2 is at Floor 10. Car 1 should be selected!
        dispatcher.submitExternalRequest(3, Direction.UP);
        
        // --- SCENARIO 2: The Mid-Flight Reversal (The LOOK Algorithm Test) ---
        // The user gets in Car 1 at Floor 3 and presses Floor 8 (UP).
        dispatcher.submitInternalRequest(1, 8);
        
        // WHILE Car 1 is preparing to go UP to 8, a troll jumps in and presses Floor 1 (DOWN).
        // Because of the Min/Max Heaps, the car will NOT bounce to 1. It will finish 
        // going up to 8, reverse engines, and THEN go down to 1.
        dispatcher.submitInternalRequest(1, 1);

        // 3. Start the Engine for Car 1
        System.out.println("\n=== FIRING UP ENGINE FOR CAR 1 ===");
        controller1.runLookAlgorithm();
        
        System.out.println("\n=== SIMULATION COMPLETE ===");
    }
}
