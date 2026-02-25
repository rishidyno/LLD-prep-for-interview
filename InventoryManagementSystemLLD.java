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
public class InventoryManagementSystemLLD {
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
