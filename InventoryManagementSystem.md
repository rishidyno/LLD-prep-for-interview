# 📖 Comprehensive LLD Study Guide: Distributed Inventory Management System

## 1. Problem Definition & Clarifying Questions

When given this prompt in an interview, never start coding immediately. Begin by establishing the scope. Here are the exact clarifying questions you should ask:

* **Topology:** Are we designing for a single warehouse or a distributed, multi-warehouse setup? *(We chose: Multi-warehouse).*
* **Granularity:** Do we track aggregate counts (e.g., 500 iPhones) or individual physical items? *(We chose: Individual items with unique barcodes and exact bin locations).*
* **Concurrency:** Do we need to handle flash sales safely where thousands of users try to buy the same item simultaneously? *(We chose: Yes, strict thread safety is required).*
* **Features:** Are we handling inbound restocking and alerts, or just outbound fulfillment? *(We chose: Both inbound and outbound, plus low-stock alerts).*

## 2. System Architecture & Entities

To model the physical world accurately, we need clear boundaries between our data objects.

* **`Product`**: The catalog metadata (ID, Name, Price). It does not care about physical location.
* **`Item`**: A specific physical box in the real world. It has a Barcode, belongs to a Product, and has a physical Bin Location.
* **`Warehouse`**: The local storage facility. It manages its own internal thread-safe inventory and locking mechanisms.
* **`InventoryManager`**: The global orchestrator. It knows about all warehouses, handles routing, and broadcasts system-wide alerts.

## 3. Design Patterns Applied (The "Why")

Interviewers look for SDE-2s to justify their architectural choices using standard patterns.

1. **State Pattern (`ItemState` Enum):** We strictly control an item's lifecycle (`AVAILABLE` $\rightarrow$ `RESERVED` $\rightarrow$ `SHIPPED`).
2. **Singleton Pattern (`InventoryManager`):** Guarantees a single, centralized orchestrator in memory to manage the distributed warehouses.
3. **Strategy Pattern (`WarehouseSelectionStrategy`):** Decouples routing logic from the core manager (Open/Closed Principle).
4. **Observer Pattern (`InventoryObserver`):** Handles low-stock alerts, keeping the inventory domain decoupled from notification domains.

## 4. Concurrency Handling (The Flash Sale Solution)

This is the most critical part of the interview. When multiple threads try to buy the last unit of a product, you must prevent **overselling**.

* **`ConcurrentHashMap<String, Queue<Item>>`**: Gives us thread-safe, $O(1)$ lookups for inventory.
* **`ConcurrentLinkedQueue<Item>`**: A highly optimized, lock-free queue for holding the physical items.
* **Fine-Grained Locking (`ReentrantLock`)**: We maintain a map of locks *per Product ID*. By locking at the Product ID level instead of the whole method, only threads competing for the *exact same item* are forced to wait, maximizing throughput.

---

## 5. The Complete Executable Code with Interview Scripts

```java
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

// ==========================================
// 1. CORE ENTITIES & STATE
// ==========================================

/* INTERVIEW EXPLANATION: "I am using an Enum for ItemState to strictly control the lifecycle of a physical unit. 
If a flash sale occurs, we don't just delete an item from the database when someone clicks 'Checkout'. 
We transition it to RESERVED. If their payment fails, a background job can easily find RESERVED items past 
their TTL and flip them back to AVAILABLE. This prevents phantom reads and lost inventory." */
enum ItemState {
    AVAILABLE, RESERVED, SHIPPED, DAMAGED
}

class Product {
    private String productId;
    private String name;
    private double price;

    public Product(String productId, String name, double price) {
        this.productId = productId;
        this.name = name;
        this.price = price;
    }
    public String getProductId() { return productId; }
    public String getName() { return name; }
}

class Item {
    private String barcode;
    private String productId;
    private ItemState state;
    private String binLocation;

    public Item(String barcode, String productId, String binLocation) {
        this.barcode = barcode;
        this.productId = productId;
        this.binLocation = binLocation;
        this.state = ItemState.AVAILABLE;
    }
    public String getProductId() { return productId; }
    public String getBarcode() { return barcode; }
    public ItemState getState() { return state; }
    public void setState(ItemState state) { this.state = state; }
}

class Location {
    double lat, lon;
    public Location(double lat, double lon) { this.lat = lat; this.lon = lon; }
}

// ==========================================
// 2. WAREHOUSE (Concurrency Engine)
// ==========================================

/* INTERVIEW EXPLANATION: "To handle high concurrency during flash sales, I am designing the Warehouse class 
to be highly thread-safe. I chose a ConcurrentHashMap to store the available inventory, giving us O(1) lock-free lookups. 
However, simply using concurrent collections isn't enough to prevent 'overselling' if 10,000 users try to buy 
the last 5 items. 

To solve this, I introduced 'productLocks'—a map of ReentrantLocks per Product ID. 
Why per product? If I locked the whole warehouse, an order for an iPhone would block an order for a MacBook, 
destroying our throughput. Locking at the product level ensures we have maximum concurrency while guaranteeing 
absolute thread safety for specific high-demand items." */
class Warehouse {
    private String warehouseId;
    private Location location;
    
    private Map<String, Queue<Item>> inventory = new ConcurrentHashMap<>();
    private Map<String, ReentrantLock> productLocks = new ConcurrentHashMap<>();

    public Warehouse(String warehouseId, Location location) {
        this.warehouseId = warehouseId;
        this.location = location;
    }

    public String getWarehouseId() { return warehouseId; }

    public void restockItem(Item item) {
        inventory.putIfAbsent(item.getProductId(), new ConcurrentLinkedQueue<>());
        inventory.get(item.getProductId()).offer(item);
        item.setState(ItemState.AVAILABLE);
    }

    public List<Item> reserveItems(String productId, int quantity) throws Exception {
        ReentrantLock lock = productLocks.computeIfAbsent(productId, k -> new ReentrantLock());
        
        lock.lock(); // Critical Section Begins
        try {
            Queue<Item> availableItems = inventory.getOrDefault(productId, new ConcurrentLinkedQueue<>());
            
            if (availableItems.size() < quantity) {
                throw new Exception("Insufficient stock in Warehouse " + warehouseId);
            }

            List<Item> pickedItems = new ArrayList<>();
            for (int i = 0; i < quantity; i++) {
                Item item = availableItems.poll(); // O(1) thread-safe removal
                if (item != null) {
                    item.setState(ItemState.RESERVED);
                    pickedItems.add(item);
                }
            }
            return pickedItems;
        } finally {
            lock.unlock(); // Critical Section Ends - ALWAYS in a finally block
        }
    }

    public int getAvailableCount(String productId) {
        return inventory.containsKey(productId) ? inventory.get(productId).size() : 0;
    }
}

// ==========================================
// 3. DESIGN PATTERNS (Strategy & Observer)
// ==========================================

/* INTERVIEW BONUS POINT: "By using the Strategy Pattern for warehouse selection, our system is highly extensible. 
Right now, I am implementing a 'NearestWarehouseStrategy' to save on shipping costs. But if the business requirements 
change—say, they want to fulfill orders from warehouses with the most stagnant inventory to clear space—we just 
add a new Strategy class without touching the core InventoryManager logic. This adheres beautifully to the 
Open/Closed Principle." */
interface WarehouseSelectionStrategy {
    Warehouse selectWarehouse(List<Warehouse> warehouses, String productId, int quantity, Location customerLocation);
}

class NearestWarehouseStrategy implements WarehouseSelectionStrategy {
    @Override
    public Warehouse selectWarehouse(List<Warehouse> warehouses, String productId, int quantity, Location customerLocation) {
        for (Warehouse w : warehouses) {
            if (w.getAvailableCount(productId) >= quantity) {
                return w; 
            }
        }
        return null;
    }
}

/* INTERVIEW EXPLANATION: "For the low-stock alerts, I implemented the Observer Pattern. 
The core inventory system shouldn't care *who* needs to know about low stock (e.g., Procurement service, 
Slack bot, Email service). It just broadcasts the event. This decouples the inventory domain from notification domains." */
interface InventoryObserver {
    void onLowStock(String productId, int remainingCount, String warehouseId);
}

class ProcurementService implements InventoryObserver {
    @Override
    public void onLowStock(String productId, int remainingCount, String warehouseId) {
        System.out.println(">>> ALERT [Procurement]: Product " + productId + 
                           " dropping! Only " + remainingCount + " left in " + warehouseId);
    }
}

// ==========================================
// 4. CENTRAL MANAGER (Singleton)
// ==========================================
class InventoryManager {
    private static final InventoryManager INSTANCE = new InventoryManager();
    private List<Warehouse> warehouses = new CopyOnWriteArrayList<>();
    private WarehouseSelectionStrategy routingStrategy;
    private List<InventoryObserver> observers = new CopyOnWriteArrayList<>();
    private static final int LOW_STOCK_THRESHOLD = 5;

    private InventoryManager() {
        this.routingStrategy = new NearestWarehouseStrategy();
    }

    public static InventoryManager getInstance() { return INSTANCE; }
    public void addWarehouse(Warehouse w) { warehouses.add(w); }
    public void addObserver(InventoryObserver o) { observers.add(o); }
    public void setRoutingStrategy(WarehouseSelectionStrategy strategy) { this.routingStrategy = strategy; }

    public List<Item> fulfillOrder(String productId, int quantity, Location customerLocation, String customerId) {
        try {
            Warehouse selectedWarehouse = routingStrategy.selectWarehouse(warehouses, productId, quantity, customerLocation);
            
            if (selectedWarehouse == null) {
                System.out.println("FAIL [" + customerId + "]: Out of stock for " + productId);
                return Collections.emptyList();
            }

            List<Item> pickedItems = selectedWarehouse.reserveItems(productId, quantity);
            System.out.println("SUCCESS [" + customerId + "]: Reserved " + quantity + " units from " + selectedWarehouse.getWarehouseId());
            
            // Trigger Observers if necessary
            int remaining = selectedWarehouse.getAvailableCount(productId);
            if (remaining <= LOW_STOCK_THRESHOLD) {
                notifyObservers(productId, remaining, selectedWarehouse.getWarehouseId());
            }

            return pickedItems;
        } catch (Exception e) {
            System.out.println("ERROR [" + customerId + "]: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private void notifyObservers(String productId, int remaining, String warehouseId) {
        for (InventoryObserver observer : observers) {
            observer.onLowStock(productId, remaining, warehouseId);
        }
    }
}

// ==========================================
// 5. MAIN SIMULATION
// ==========================================
public class InventoryManagementSystemDemo {
    public static void main(String[] args) throws InterruptedException {
        InventoryManager manager = InventoryManager.getInstance();
        manager.addObserver(new ProcurementService());

        Warehouse w1 = new Warehouse("WH-NewYork", new Location(40.71, -74.00));
        manager.addWarehouse(w1);

        Product laptop = new Product("PROD-100", "High-End Laptop", 1999.99);

        System.out.println("--- Restocking Warehouse ---");
        for (int i = 1; i <= 10; i++) {
            w1.restockItem(new Item("BARCODE-" + i, laptop.getProductId(), "Aisle-1"));
        }
        System.out.println("Initial Stock in NY: " + w1.getAvailableCount(laptop.getProductId()) + "\n");

        System.out.println("--- Flash Sale Begins! 15 customers trying to buy ---");
        ExecutorService executor = Executors.newFixedThreadPool(15);
        Location customerLoc = new Location(40.00, -73.00); 

        for (int i = 1; i <= 15; i++) {
            final String customerId = "Customer-" + i;
            executor.submit(() -> manager.fulfillOrder(laptop.getProductId(), 1, customerLoc, customerId));
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("\n--- Flash Sale Ends ---");
        System.out.println("Final Stock in NY: " + w1.getAvailableCount(laptop.getProductId()));
    }
}

```

## 6. Discussion Points & Follow-Up Questions

If you finish early, the interviewer will likely ask you how to scale this further. Be ready to discuss:

* **Order Splitting:** If a user wants 5 items, and WH-A has 3, and WH-B has 2, our current `NearestWarehouseStrategy` fails. You'd explain that you would update the Strategy to return a `Map<Warehouse, Integer>` outlining the split shipment.
* **Cart TTL (Time-To-Live):** Explain that `ItemState.RESERVED` implies the item is in a cart. You would introduce a background cron job (or use a Redis TTL in a real distributed system) that sweeps the database every minute, finding items stuck in `RESERVED` for $> 10$ minutes, and resetting them to `AVAILABLE`.
* **FEFO (First-Expiring, First-Out):** If the prompt changes to a grocery store, you would swap the `ConcurrentLinkedQueue` for a `PriorityQueue`, sorting items by their expiration date.


## 7. Advanced Architectural Enhancements (SDE-3 Talking Points)

While our core engine safely handles high-concurrency flash sales via fine-grained locking, a production-level interview might ask how we handle complex object creation or distributed server environments. Here are the precise enhancements to discuss:

### 7.1. Creational Design Patterns (Handling Complex Products)

Currently, our `Product` class is relatively simple. If we were to expand this system to handle highly complex items (like Electronics with optional warranties, varying power consumption, or specific wireless connectivities), we should integrate the following patterns:

* **The Builder Pattern:** Instead of having constructors with 10+ parameters or relying on heavy setter methods, we would use the Builder Pattern. It is perfect for creating complex product objects with many optional parameters. It guarantees that once the `Product` is built, it is immutable and thread-safe.

* **The Factory Pattern:** To keep the `InventoryManager` clean, we would encapsulate the logic of object instantiation. A `ProductFactory` class would be responsible for returning the correct `Product` instance based on a provided `ProductCategory` enum.



**🎤 Interview Script for Creational Patterns:**

> *"To keep our core engine focused on thread-safe transactions, I would extract the object creation logic out to a `ProductFactory`. Furthermore, since products like Electronics have many optional attributes, I would implement the Builder Pattern. This ensures clean, step-by-step construction of the `Product` objects without polluting the codebase with massive constructors."*

### 7.2. Avoiding Inheritance Anti-Patterns (Composition over Inheritance)

Many standard tutorials implement a deep inheritance tree where `ElectronicsProduct`, `ClothingProduct`, and `GroceryProduct` all heavily extend an abstract `Product` base class.

* **The Trade-off:** Deep inheritance trees make the domain brittle. If a product belongs to two categories (e.g., an electronic wearable shirt), strict inheritance breaks down.
* **The Solution:** We favor **Composition and Tagging**. Instead of extending classes, we give a base `Product` class a list of `Category` tags or an `Attributes` map. This is much more flexible for a real-world e-commerce catalog.

### 7.3. Production-Ready Distributed System Upgrades

To deploy our `Warehouse` logic across thousands of servers, we would abstract our in-memory data structures:

1. **Repository Layer (Database Abstraction):** We would abstract the `ConcurrentHashMap` behind an `InventoryRepository` interface. This allows us to easily swap our in-memory map for a persistent SQL database or a Redis cache without altering the core routing logic.
2. **Distributed Locking:** Our `ReentrantLock` guarantees safety for threads running on a *single* server. In a distributed environment, we would replace this with a distributed locking mechanism like **Redis Redlock** or **Apache Zookeeper** to synchronize locks across multiple JVMs.
3. **Idempotency Keys:** If a user's network drops during checkout and their app automatically retries the request, we risk reserving a *second* item. We would require an `idempotencyKey` (e.g., a checkout session UUID) in the `fulfillOrder` parameters to ensure the operation is strictly processed only once.
4. **The Saga Pattern & Cart TTL:** If we successfully reserve items (transitioning them to `RESERVED`), but the external Payment Service fails a few seconds later, we need a rollback strategy. A background cron job (or Redis TTL) would sweep the database every minute, identify items stuck in `RESERVED` for more than 10 minutes, and revert them to `AVAILABLE` to prevent inventory leakage.

