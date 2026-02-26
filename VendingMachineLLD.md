# 🍫 Comprehensive LLD Study Guide: Vending Machine

## 1. Problem Definition & Clarifying Questions

Before writing any code, we must define the physical and digital boundaries of the machine.

* **Payment Methods:** Are we handling physical cash, cashless (cards/NFC), or both? *(We chose: Both, prioritizing the physical coin change logic first).*
* **Change Calculation:** What happens if the machine doesn't have enough physical coins to make exact change? *(We chose: The machine must abort the transaction and refund the user before dropping the item).*
* **Inventory Scale:** How is inventory tracked? *(We chose: A grid system, e.g., slot "A1", storing queues of items).*
* **Concurrency:** Can a user buy an item while an admin is restocking? *(We chose: Yes, internal data structures must be thread-safe).*

## 2. Core Entities (Domain Modeling)

* **`VendingMachine`**: The central Context orchestrator. It holds the current state, the hardware inventory, and the change calculation chain.
* **`Item`**: The product being sold (Name, Price).
* **`HardwareInventory`**: Unlike a basic inventory, this tracks *two* things: the queues of physical `Item`s in the slots, AND the exact count of physical `Coin`s in the cash tubes.
* **`CoinHandler`**: A node in the Chain of Responsibility used to calculate change.
* **`VendingMachineState`**: An interface defining what actions the machine can currently accept.

## 3. The "Shine" Factors: Advanced Design Patterns

To pass an SDE-2/SDE-3 bar, you cannot use massive `if-else` blocks. We use three specific patterns to model the physical hardware:

### A. The State Pattern (Hardware Lifecycle)

* **The Problem:** We don't want users pressing "Dispense" before they insert money. We also don't want users pressing buttons if a technician has the physical door open. Using `boolean isDoorOpen` or `boolean hasMoney` leads to spaghetti code.
* **The Solution:** The State Pattern (`IdleState` $\rightarrow$ `HasMoneyState` $\rightarrow$ `DispensingState`).
* **The Analogy:** Think of a manual car transmission. You cannot shift directly into 5th gear from Park. The software completely changes its behavior based on its current state, making illegal operations impossible.

### B. Chain of Responsibility (Exact Change Logic)

* **The Problem:** Modulo math for making change gets extremely messy, especially if a specific coin tube (like Dimes) is completely empty.
* **The Solution:** We pass the required change down a chain: `QuarterHandler` $\rightarrow$ `DimeHandler` $\rightarrow$ `NickelHandler`.
* **The Analogy:** Think of a water filtration system. The big rocks (Quarters) catch the largest chunks of the amount. Whatever spills over goes to the pebbles (Dimes), and the rest goes to the sand (Nickels).

### C. The "Dry Run" Atomic Check (Fixing the Infinite Money Bug)

* **The Problem:** Standard tutorials deduct the item, then try to dispense change. If the machine is out of nickels, the user gets their snack but gets scammed out of their change.
* **The Solution:** Inside `HasMoneyState`, before we transition to dispensing, we do a "Dry Run" down the Chain of Responsibility. We check the `HardwareInventory` to see if we *actually have* the physical coins needed. If we don't, the chain throws an exception, the transaction aborts cleanly, and the user gets their original money back.

---

## 4. Real-World Bottlenecks (SDE-3 Discussion Points)

During the interview, the interviewer will ask *why* you added an `AdminMaintenanceState` when the user physically can't touch the buttons while the door is open. Here is exactly how you defend it:

1. **The IoT / Remote Purchase Bottleneck (Hardware Safety):** Modern machines connect to mobile apps. If the door is open and the software is still in `IdleState`, a user walking by could trigger a remote Apple Pay vend while the technician's hands are inside the spinning coils. The `MaintenanceState` instantly drops all network connections.
2. **The Cash Audit Trail (Financial Bottleneck):** When an admin empties the coin tubes, the software needs to know *why* the cash dropped to $0. By triggering `MaintenanceState` when the door opens, the software securely unlocks APIs like `clearCashBox()` and logs an audit trail: *"Admin collected $100 at 2:00 PM."*
3. **Diagnostic Testing (The "Free Vend" Bottleneck):** A technician needs to test a replaced coil. If the machine is in `IdleState`, it demands money. The `MaintenanceState` bypasses the payment gateway, allowing the technician to press "A1", spinning the coil, and logging it as a `Diagnostic_Vend` rather than a lost sale.

---

## 5. The Master Code Implementation

```java
import java.util.*;
import java.util.concurrent.*;

// ==========================================
// 1. HARDWARE INVENTORY & ENTITIES
// ==========================================

enum Coin {
    QUARTER(25), DIME(10), NICKEL(5);
    private int value;
    Coin(int value) { this.value = value; }
    public int getValue() { return value; }
}

class Item {
    private String name;
    private int priceInCents;
    public Item(String name, int price) { this.name = name; this.priceInCents = price; }
    public String getName() { return name; }
    public int getPrice() { return priceInCents; }
}

/* INTERVIEW EXPLANATION: "The HardwareInventory tracks both products AND physical cash. 
We use ConcurrentHashMap to ensure thread safety, so an admin's diagnostic thread 
doesn't crash a live user transaction." */
class HardwareInventory {
    private Map<String, Queue<Item>> products = new ConcurrentHashMap<>();
    private Map<Coin, Integer> cashTubes = new ConcurrentHashMap<>();

    public HardwareInventory() {
        for (Coin c : Coin.values()) cashTubes.put(c, 0); 
    }

    public void addProduct(String slot, Item item) {
        products.putIfAbsent(slot, new ConcurrentLinkedQueue<>());
        products.get(slot).offer(item);
    }
    public Item peekProduct(String slot) { return products.containsKey(slot) ? products.get(slot).peek() : null; }
    public Item dispenseProduct(String slot) { return products.get(slot).poll(); }
    public boolean isProductSoldOut(String slot) { return !products.containsKey(slot) || products.get(slot).isEmpty(); }

    public void loadCoins(Coin c, int count) { cashTubes.put(c, cashTubes.get(c) + count); }
    public void deductCoins(Coin c, int count) { cashTubes.put(c, cashTubes.get(c) - count); }
    public int getCoinCount(Coin c) { return cashTubes.get(c); }
}

// ==========================================
// 2. EXACT CHANGE LOGIC (Chain of Responsibility)
// ==========================================

/* INTERVIEW EXPLANATION: "This chain performs an atomic 'Dry Run'. It calculates a Map of coins 
needed for change by checking the HardwareInventory. If it reaches the end of the chain and 
cannot make exact change (e.g., out of nickels), it throws an Exception, cleanly aborting the sale." */
abstract class CoinHandler {
    protected CoinHandler nextHandler;
    protected Coin coinType;

    public CoinHandler(Coin coinType) { this.coinType = coinType; }
    public void setNextHandler(CoinHandler next) { this.nextHandler = next; }

    public void calculateChangePlan(int amountNeeded, HardwareInventory inventory, Map<Coin, Integer> changePlan) throws Exception {
        int coinsAvailable = inventory.getCoinCount(coinType);
        int coinsNeeded = amountNeeded / coinType.getValue();
        int coinsToDispense = Math.min(coinsAvailable, coinsNeeded);

        if (coinsToDispense > 0) {
            changePlan.put(coinType, coinsToDispense);
            amountNeeded -= (coinsToDispense * coinType.getValue());
        }

        if (amountNeeded > 0) {
            if (nextHandler != null) {
                nextHandler.calculateChangePlan(amountNeeded, inventory, changePlan);
            } else {
                throw new Exception("EXACT CHANGE ONLY. Machine lacks sufficient coins.");
            }
        }
    }
}

class QuarterHandler extends CoinHandler { public QuarterHandler() { super(Coin.QUARTER); } }
class DimeHandler extends CoinHandler { public DimeHandler() { super(Coin.DIME); } }
class NickelHandler extends CoinHandler { public NickelHandler() { super(Coin.NICKEL); } }

// ==========================================
// 3. HARDWARE STATES (State Pattern)
// ==========================================

interface VendingMachineState {
    void insertMoney(int amount) throws Exception;
    void selectItem(String slotCode) throws Exception;
    void dispense() throws Exception;
}

class IdleState implements VendingMachineState {
    private VendingMachine machine;
    public IdleState(VendingMachine machine) { this.machine = machine; }

    @Override
    public void insertMoney(int amount) {
        machine.addTemporaryBalance(amount);
        System.out.println("Inserted: " + amount + " cents.");
        machine.setState(machine.getHasMoneyState());
    }
    @Override public void selectItem(String slot) throws Exception { throw new Exception("Insert money first."); }
    @Override public void dispense() throws Exception { throw new Exception("No item selected."); }
}

class HasMoneyState implements VendingMachineState {
    private VendingMachine machine;
    public HasMoneyState(VendingMachine machine) { this.machine = machine; }

    @Override
    public void insertMoney(int amount) {
        machine.addTemporaryBalance(amount);
        System.out.println("Inserted: " + amount + " cents. Total: " + machine.getCurrentBalance());
    }

    @Override
    public void selectItem(String slotCode) throws Exception {
        HardwareInventory inv = machine.getInventory();
        if (inv.isProductSoldOut(slotCode)) throw new Exception("Item Sold Out.");
        
        Item item = inv.peekProduct(slotCode);
        if (machine.getCurrentBalance() < item.getPrice()) {
            throw new Exception("Insufficient funds. Price is " + item.getPrice());
        }

        // The Atomic "Dry Run" Check
        int changeNeeded = machine.getCurrentBalance() - item.getPrice();
        Map<Coin, Integer> proposedChangePlan = new HashMap<>();
        
        if (changeNeeded > 0) {
            machine.getChangeChain().calculateChangePlan(changeNeeded, inv, proposedChangePlan);
        }

        machine.setSelectedSlot(slotCode);
        machine.setApprovedChangePlan(proposedChangePlan);
        machine.setState(machine.getDispensingState()); 
    }
    @Override public void dispense() throws Exception { throw new Exception("Select item first."); }
}

class DispensingState implements VendingMachineState {
    private VendingMachine machine;
    public DispensingState(VendingMachine machine) { this.machine = machine; }

    @Override public void insertMoney(int amount) throws Exception { throw new Exception("Wait, dispensing..."); }
    @Override public void selectItem(String slot) throws Exception { throw new Exception("Wait, dispensing..."); }

    @Override
    public void dispense() {
        Item item = machine.getInventory().dispenseProduct(machine.getSelectedSlot());
        System.out.println(">>> DROPPING ITEM: " + item.getName());

        Map<Coin, Integer> plan = machine.getApprovedChangePlan();
        for (Map.Entry<Coin, Integer> entry : plan.entrySet()) {
            machine.getInventory().deductCoins(entry.getKey(), entry.getValue());
            System.out.println(">>> DROPPING COIN: " + entry.getValue() + " " + entry.getKey() + "(s)");
        }

        machine.clearTransaction();
        machine.setState(machine.getIdleState());
    }
}

/* INTERVIEW EXPLANATION: "The MaintenanceState prevents IoT mobile app purchases 
while the physical door is open, and allows technicians to perform 'free vends' 
to test hardware coils without needing to insert money." */
class MaintenanceState implements VendingMachineState {
    private VendingMachine machine;
    public MaintenanceState(VendingMachine machine) { this.machine = machine; }

    @Override public void insertMoney(int amount) throws Exception { throw new Exception("MACHINE OFFLINE FOR MAINTENANCE."); }
    @Override public void dispense() throws Exception { throw new Exception("MACHINE OFFLINE FOR MAINTENANCE."); }

    @Override
    public void selectItem(String slotCode) {
        System.out.println("DIAGNOSTIC MODE: Force-spinning coil for slot " + slotCode);
        Item item = machine.getInventory().dispenseProduct(slotCode);
        if (item != null) System.out.println("Diagnostic Drop: " + item.getName());
    }
}

// ==========================================
// 4. THE CONTEXT ORCHESTRATOR
// ==========================================

class VendingMachine {
    private HardwareInventory inventory = new HardwareInventory();
    private int currentBalance = 0;
    private String selectedSlot = null;
    private Map<Coin, Integer> approvedChangePlan = new HashMap<>();

    private VendingMachineState idleState = new IdleState(this);
    private VendingMachineState hasMoneyState = new HasMoneyState(this);
    private VendingMachineState dispensingState = new DispensingState(this);
    private VendingMachineState maintenanceState = new MaintenanceState(this);
    private VendingMachineState currentState = idleState;

    private CoinHandler changeChain;

    public VendingMachine() {
        CoinHandler quarters = new QuarterHandler();
        CoinHandler dimes = new DimeHandler();
        CoinHandler nickels = new NickelHandler();
        quarters.setNextHandler(dimes);
        dimes.setNextHandler(nickels);
        this.changeChain = quarters;
    }

    public void insertCoin(Coin c) {
        try { currentState.insertMoney(c.getValue()); } 
        catch (Exception e) { System.out.println("UI ERROR: " + e.getMessage()); }
    }

    public void pressButton(String slot) {
        try { 
            currentState.selectItem(slot); 
            currentState.dispense(); 
        } 
        catch (Exception e) { 
            System.out.println("UI ERROR: " + e.getMessage()); 
            refundUser();
        }
    }

    private void refundUser() {
        if (currentBalance > 0) {
            System.out.println("Refunding inserted balance: " + currentBalance + " cents.");
            clearTransaction();
            setState(idleState);
        }
    }

    public void triggerDoorOpen() {
        System.out.println("\n[HARDWARE ALERT] Door Opened. Entering Maintenance Mode.");
        setState(maintenanceState);
    }
    public void triggerDoorClose() {
        System.out.println("\n[HARDWARE ALERT] Door Closed. Machine Online.");
        setState(idleState);
    }

    public void setState(VendingMachineState state) { this.currentState = state; }
    public VendingMachineState getIdleState() { return idleState; }
    public VendingMachineState getHasMoneyState() { return hasMoneyState; }
    public VendingMachineState getDispensingState() { return dispensingState; }
    public HardwareInventory getInventory() { return inventory; }
    public CoinHandler getChangeChain() { return changeChain; }
    public void addTemporaryBalance(int amt) { this.currentBalance += amt; }
    public int getCurrentBalance() { return currentBalance; }
    public void setSelectedSlot(String slot) { this.selectedSlot = slot; }
    public String getSelectedSlot() { return selectedSlot; }
    public void setApprovedChangePlan(Map<Coin, Integer> plan) { this.approvedChangePlan = plan; }
    public Map<Coin, Integer> getApprovedChangePlan() { return approvedChangePlan; }
    
    public void clearTransaction() {
        this.currentBalance = 0;
        this.selectedSlot = null;
        this.approvedChangePlan.clear();
    }
}

// ==========================================
// 5. MAIN SIMULATION (Executing the System)
// ==========================================

public class VendingMachineDemo {
    public static void main(String[] args) {
        VendingMachine machine = new VendingMachine();
        HardwareInventory inv = machine.getInventory();

        // 1. Admin loads the machine
        inv.addProduct("A1", new Item("Coke", 125));
        inv.loadCoins(Coin.QUARTER, 2); // Machine only has 2 Quarters ($0.50)
        inv.loadCoins(Coin.DIME, 0);    // Oh no, out of dimes!
        inv.loadCoins(Coin.NICKEL, 5);  // Machine has 5 Nickels ($0.25)

        System.out.println("--- TRANSACTION 1: The 'Infinite Money' Bug Test ---");
        // User inserts $2.00 (8 quarters) for a $1.25 Coke. 
        // Change needed: 75 cents. 
        // Machine only has $0.75 exactly (2 quarters, 5 nickels). Let's see if the Chain calculates it!
        for(int i=0; i<8; i++) machine.insertCoin(Coin.QUARTER);
        machine.pressButton("A1"); 

        System.out.println("\n--- TRANSACTION 2: Maintenance Hardware Test ---");
        machine.triggerDoorOpen();
        
        // User tries to buy while door is open (Simulating IoT/app request)
        machine.insertCoin(Coin.QUARTER);
        
        // Admin does a free diagnostic test to spin the coil
        machine.pressButton("A1");
        
        machine.triggerDoorClose();
    }
}

```

---

## 6. How the Interviewer Will Grill You (And How to Defend)

**Grill 1: "What if the hardware coil gets stuck and the item doesn't drop?"**

* **Your Defense:** Apply the **Command Pattern**. If the physical motor API returns an `ERROR_JAMMED` callback, the software executes an `.undo()` command. It reverses the inventory deduction, skips dropping the coins, and automatically refunds the user's `currentBalance`.

**Grill 2: "How would you add Credit Card/Apple Pay support?"**

* **Your Defense:** Introduce the **Strategy Pattern**. I would add a `PaymentStrategy` interface (`CashStrategy`, `CreditCardStrategy`). I would also add a new State: `ProcessingPaymentState`. The machine transitions here while making the async network call to the bank API. If the network times out after 10 seconds, it transitions back to `IdleState` and cancels the transaction.
