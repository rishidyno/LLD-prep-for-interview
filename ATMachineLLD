import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.time.LocalDateTime;

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                ATM MACHINE — LOW LEVEL DESIGN (INTERVIEW READY)             ║
// ║                        Target: SDE-1 / SDE-2 Roles                         ║
// ║                        Time to cover: ~45 mins                             ║
// ║                                                                              ║
// ║  DESIGN PATTERNS (Name ALL of these upfront):                               ║
// ║  ─────────────────────────────────────────────                              ║
// ║  1. STATE    → ATMState     (each ATM state is its own class)               ║
// ║  2. FACTORY  → ATMStateFactory (centralizes state object creation)          ║
// ║  3. STRATEGY → DispenseStrategy (swap cash dispensing algorithm)            ║
// ║  4. SINGLETON→ ATM (one machine, one instance)                              ║
// ║                                                                              ║
// ║  WHAT TO SAY FIRST:                                                         ║
// ║  ──────────────────                                                         ║
// ║  "I see four design decisions:                                               ║
// ║                                                                              ║
// ║   1. State management → State Pattern. An ATM has strict state transitions. ║
// ║      Withdraw is only valid when Authenticated. Auth only after CardInserted.║
// ║      If I use if/else everywhere, adding a new state touches every method.  ║
// ║      State Pattern gives each state its own class. ATM just delegates.      ║
// ║                                                                              ║
// ║   2. State creation → Factory Pattern. ATM never calls new IdleState() —   ║
// ║      the Factory centralizes this. States can be cached per ATM instance    ║
// ║      since they hold an ATM reference. Adding a new state = new class +     ║
// ║      one Factory case. Zero changes to ATM.                                 ║
// ║                                                                              ║
// ║   3. Cash dispensing → Strategy Pattern. Different ATMs may have different  ║
// ║      dispensing policies (greedy, specific denominations). The algorithm    ║
// ║      is injected — ATM never knows which strategy it's using.               ║
// ║                                                                              ║
// ║   4. Concurrency → ReentrantLock on Account. Two ATMs can serve two people  ║
// ║      with the same account simultaneously. Balance check + debit must be    ║
// ║      atomic — I use tryLock with timeout to avoid deadlock."                ║
// ╚══════════════════════════════════════════════════════════════════════════════╝


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 1: ATM STATE TYPE ENUM
// ══════════════════════════════════════════════════════════════════════════════
//
// WHY AN ENUM FOR STATE TYPE?
// ────────────────────────────
// The Factory maps this enum to a concrete ATMState class.
// Compiler-enforced — no invalid state strings can be passed.
// Adding a new state = add one enum value + one Factory case.
//
// INTERVIEW SCRIPT:
// "ATMStateType is the key into the Factory. The ATM says
//  'I want to move to AUTHENTICATED state' — Factory hands back
//  the right object. ATM never calls new on any state class directly."
//
enum ATMStateType {
    IDLE,           // no card inserted — only insertCard() allowed
    CARD_INSERTED,  // card in, PIN not yet validated — only authenticate() allowed
    AUTHENTICATED   // PIN validated — withdraw/balance/changePin/eject all allowed
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 2: ATM STATE INTERFACE — STATE PATTERN
// ══════════════════════════════════════════════════════════════════════════════
//
// STATE PATTERN:
// ──────────────
// Each concrete state class knows WHICH operations are valid in that state
// and WHAT HAPPENS when they're called. The ATM class holds a reference to
// the current state and delegates every user action to it.
//
// WHY NOT ENUM + SWITCH IN ATM?
// ──────────────────────────────
// With switch:
//   ATM.withdraw() → switch(state) { case IDLE: reject, case CARD_INSERTED: reject, ... }
//   ATM.checkBalance() → switch(state) { case IDLE: reject, case CARD_INSERTED: reject, ... }
//   Every method has the same boilerplate. Add CardBlocked state → fix every switch.
//
// With State Pattern:
//   ATM.withdraw() → currentState.withdraw(amount)   ← one line, state decides
//   Add CardBlocked state → one new class. ATM never changes.
//
// INTERVIEW SCRIPT:
// "This is the State interface. Every user action is a method here.
//  Each concrete state implements what should happen when that action is
//  taken in that state. Invalid actions throw IllegalStateException
//  with a user-friendly message. The ATM class has zero conditionals
//  for state — it purely delegates."
//
interface ATMState {
    void insertCard(Card card);
    void authenticate(String pin);
    void withdraw(double amount);
    void checkBalance();
    void changePIN(String oldPin, String newPin);
    void ejectCard();
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 3: ATM STATE FACTORY — FACTORY PATTERN
// ══════════════════════════════════════════════════════════════════════════════
//
// FACTORY PATTERN:
// ────────────────
// Creates ATMState instances given an ATMStateType.
// ATM asks the Factory when it needs to transition states.
//
// KEY DESIGN CHOICE — CACHE OR NOT?
// ────────────────────────────────────
// States here are NOT stateless — they hold a reference to ATM context.
// So we CANNOT share one instance across multiple ATMs.
// But for a SINGLE ATM, we CAN cache — the same IdleState instance for
// this ATM is reused every time we transition back to IDLE.
//
// This is called per-context caching — one cache per ATM instance.
// The Factory creates the cache at ATM construction time.
//
// WHY BOTHER CACHING?
// ─────────────────────
// An ATM transitions states thousands of times a day. Without caching,
// we create a new IdleState object for every session end. With caching,
// it's always the same object. Less GC pressure.
//
// INTERVIEW SCRIPT:
// "The Factory caches one instance per state per ATM. States hold
//  ATM context so they can't be shared across ATMs, but within one ATM,
//  the same IdleState instance is reused on every card ejection.
//  Adding a new state = new class + one Factory case. ATM never changes."
//
class ATMStateFactory {

    // Per-ATM state cache — keyed by state type
    private final Map<ATMStateType, ATMState> cache = new HashMap<>();

    ATMStateFactory(ATM atm) {
        // Pre-build all states for this ATM and cache them
        cache.put(ATMStateType.IDLE,          new IdleState(atm));
        cache.put(ATMStateType.CARD_INSERTED,  new CardInsertedState(atm));
        cache.put(ATMStateType.AUTHENTICATED,  new AuthenticatedState(atm));
    }

    // Returns the cached state instance for this ATM
    public ATMState getState(ATMStateType type) {
        ATMState state = cache.get(type);
        if (state == null)
            throw new IllegalArgumentException("Unknown ATM state: " + type);
        return state;
    }
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 4: CONCRETE STATES
// ══════════════════════════════════════════════════════════════════════════════
//
// Each state class:
//   1. Holds a reference to ATM (the context) — to trigger state transitions
//      and access dispenser/bank.
//   2. Implements valid operations for that state.
//   3. For invalid operations, throws IllegalStateException with a clear message.
//
// HELPER: invalidOperation() — avoids repeating the throw in every invalid method.
//
// INTERVIEW SCRIPT:
// "Each state has the ATM reference as context. Valid operations do their work
//  and call atm.setState(factory.getState(NEXT_STATE)) to transition.
//  Invalid operations call a shared helper that throws immediately.
//  Zero conditionals in ATM itself — it just delegates."

// ── IDLE STATE ────────────────────────────────────────────────────────────────
// Only insertCard() is valid. Everything else is rejected.
// Transitions to: CARD_INSERTED
//
class IdleState implements ATMState {

    private final ATM atm;

    IdleState(ATM atm) { this.atm = atm; }

    @Override
    public void insertCard(Card card) {
        // Set current card in ATM context, then transition to CARD_INSERTED
        atm.setCurrentCard(card);
        atm.setCurrentAccount(atm.getBank().getAccount(card.getAccountId()));
        atm.transitionTo(ATMStateType.CARD_INSERTED);
        System.out.println("[ATM] Card inserted. Please enter your PIN.");
    }

    @Override public void authenticate(String pin)               { invalidOp("Please insert your card first."); }
    @Override public void withdraw(double amount)                { invalidOp("Please insert your card first."); }
    @Override public void checkBalance()                         { invalidOp("Please insert your card first."); }
    @Override public void changePIN(String oldPin, String newPin){ invalidOp("Please insert your card first."); }
    @Override public void ejectCard()                            { invalidOp("No card inserted."); }

    private void invalidOp(String msg) {
        throw new IllegalStateException("[ATM] Invalid operation in IDLE state. " + msg);
    }
}


// ── CARD INSERTED STATE ───────────────────────────────────────────────────────
// Card is in the machine. Only authenticate() is valid.
// Tracks failed PIN attempts — locks card after 3 failures.
// Transitions to: AUTHENTICATED (success) or IDLE (3 failures → card retained)
//
class CardInsertedState implements ATMState {

    private final ATM atm;
    private int failedAttempts = 0;
    private static final int MAX_ATTEMPTS = 3;

    CardInsertedState(ATM atm) { this.atm = atm; }

    @Override
    public void authenticate(String pin) {
        Card card = atm.getCurrentCard();

        if (card.validatePIN(pin)) {
            failedAttempts = 0; // reset on success
            atm.transitionTo(ATMStateType.AUTHENTICATED);
            System.out.println("[ATM] PIN verified. Welcome, " + card.getCardHolderName() + "!");
        } else {
            failedAttempts++;
            int remaining = MAX_ATTEMPTS - failedAttempts;
            System.out.println("[ATM] Incorrect PIN. " + remaining + " attempt(s) remaining.");

            if (failedAttempts >= MAX_ATTEMPTS) {
                System.out.println("[ATM] Too many incorrect attempts. Card retained.");
                card.block();           // mark card as blocked in bank
                atm.setCurrentCard(null);
                atm.setCurrentAccount(null);
                failedAttempts = 0;    // reset for next session
                atm.transitionTo(ATMStateType.IDLE);
            }
        }
    }

    @Override public void insertCard(Card card)                  { invalidOp("Card already inserted."); }
    @Override public void withdraw(double amount)                { invalidOp("Please authenticate first."); }
    @Override public void checkBalance()                         { invalidOp("Please authenticate first."); }
    @Override public void changePIN(String o, String n)          { invalidOp("Please authenticate first."); }

    @Override
    public void ejectCard() {
        System.out.println("[ATM] Card ejected.");
        failedAttempts = 0;
        atm.setCurrentCard(null);
        atm.setCurrentAccount(null);
        atm.transitionTo(ATMStateType.IDLE);
    }

    private void invalidOp(String msg) {
        throw new IllegalStateException("[ATM] Invalid operation in CARD_INSERTED state. " + msg);
    }
}


// ── AUTHENTICATED STATE ───────────────────────────────────────────────────────
// Full access: withdraw, checkBalance, changePIN, ejectCard.
// Transitions to: IDLE after ejectCard() or completed transaction.
//
// WITHDRAW FLOW:
// 1. Validate amount (positive, multiple of smallest note, ≤ daily limit)
// 2. Try to acquire Account lock (ReentrantLock with timeout)
// 3. Check ATM cash availability via dispenser
// 4. Check account balance
// 5. Debit account
// 6. Dispense cash
// 7. Record transaction
// 8. Release lock
//
// INTERVIEW SCRIPT:
// "I acquire the account lock before checking balance — check and debit
//  must be one atomic operation. Without this, two ATMs could both
//  read ₹5000, both decide withdrawal is valid, and both debit —
//  resulting in ₹10000 being dispensed from a ₹5000 account."
//
class AuthenticatedState implements ATMState {

    private final ATM atm;
    private static final double DAILY_WITHDRAWAL_LIMIT = 50000.0;
    private static final int    LOCK_TIMEOUT_MS        = 3000;

    AuthenticatedState(ATM atm) { this.atm = atm; }

    @Override
    public void withdraw(double amount) {
        // ── VALIDATION ───────────────────────────────────────────────────────
        if (amount <= 0)
            throw new IllegalArgumentException("[ATM] Amount must be positive.");
        if (amount > DAILY_WITHDRAWAL_LIMIT)
            throw new IllegalArgumentException("[ATM] Amount exceeds daily limit of ₹" + DAILY_WITHDRAWAL_LIMIT);

        Account account = atm.getCurrentAccount();

        // ── ACQUIRE ACCOUNT LOCK (with timeout to avoid deadlock) ────────────
        //
        // WHY tryLock INSTEAD OF lock()?
        // ────────────────────────────────
        // lock() blocks indefinitely. If two ATMs both acquire locks in
        // opposite order (A locks Account1 then tries Account2; B locks
        // Account2 then tries Account1), they deadlock forever.
        //
        // tryLock(timeout) returns false instead of blocking — we can
        // release what we hold and retry. Prevents deadlock completely.
        //
        // For a single-account operation (ATM only touches one account),
        // deadlock isn't possible here, but tryLock is still best practice
        // in any financial system — covers future multi-account operations
        // like fund transfers.
        //
        boolean lockAcquired = false;
        try {
            lockAcquired = account.getLock().tryLock(
                LOCK_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("[ATM] Request interrupted. Please try again.");
            return;
        }

        if (!lockAcquired) {
            System.out.println("[ATM] System busy. Please try again.");
            return;
        }

        try {
            // ── CHECK ATM HAS ENOUGH CASH ─────────────────────────────────
            if (!atm.getDispenser().canDispense(amount)) {
                System.out.println("[ATM] Insufficient cash in ATM. Please try another amount.");
                return;
            }

            // ── CHECK ACCOUNT BALANCE ─────────────────────────────────────
            if (account.getBalance() < amount) {
                System.out.println("[ATM] Insufficient balance. Available: ₹" + account.getBalance());
                atm.recordTransaction(new ATMTransaction(
                    TransactionType.WITHDRAWAL, amount, account.getAccountId(),
                    TransactionStatus.FAILED, "Insufficient balance"));
                return;
            }

            // ── DEBIT ACCOUNT ────────────────────────────────────────────
            account.debit(amount);

            // ── DISPENSE CASH ────────────────────────────────────────────
            Map<Integer, Integer> dispensed = atm.getDispenser().dispense(amount);
            System.out.println("[ATM] Please collect your cash:");
            dispensed.forEach((note, count) ->
                System.out.println("      ₹" + note + " × " + count));

            // ── RECORD TRANSACTION ───────────────────────────────────────
            atm.recordTransaction(new ATMTransaction(
                TransactionType.WITHDRAWAL, amount, account.getAccountId(),
                TransactionStatus.SUCCESS, null));

            System.out.printf("[ATM] ₹%.0f dispensed successfully. Remaining balance: ₹%.2f%n",
                amount, account.getBalance());

        } finally {
            // ALWAYS release the lock — even if exception is thrown
            account.getLock().unlock();
        }
    }

    @Override
    public void checkBalance() {
        Account account = atm.getCurrentAccount();
        // Balance check is a read — no lock needed if balance is declared volatile
        // For simplicity, we read directly (in production: volatile or read lock)
        System.out.printf("[ATM] Available balance: ₹%.2f%n", account.getBalance());
        atm.recordTransaction(new ATMTransaction(
            TransactionType.BALANCE_INQUIRY, 0, account.getAccountId(),
            TransactionStatus.SUCCESS, null));
    }

    @Override
    public void changePIN(String oldPin, String newPin) {
        Card card = atm.getCurrentCard();

        if (!card.validatePIN(oldPin)) {
            System.out.println("[ATM] Incorrect current PIN.");
            atm.recordTransaction(new ATMTransaction(
                TransactionType.PIN_CHANGE, 0, atm.getCurrentAccount().getAccountId(),
                TransactionStatus.FAILED, "Incorrect current PIN"));
            return;
        }

        if (newPin == null || newPin.length() != 4 || !newPin.matches("\\d{4}")) {
            System.out.println("[ATM] PIN must be exactly 4 digits.");
            return;
        }

        card.updatePIN(newPin);
        System.out.println("[ATM] PIN changed successfully.");
        atm.recordTransaction(new ATMTransaction(
            TransactionType.PIN_CHANGE, 0, atm.getCurrentAccount().getAccountId(),
            TransactionStatus.SUCCESS, null));
    }

    @Override
    public void ejectCard() {
        System.out.println("[ATM] Thank you. Card ejected. Please collect your card.");
        atm.setCurrentCard(null);
        atm.setCurrentAccount(null);
        atm.transitionTo(ATMStateType.IDLE);
    }

    @Override public void insertCard(Card card) { invalidOp("Card already inserted."); }

    // authenticate() during authenticated state could handle re-auth,
    // but for simplicity we reject it.
    @Override public void authenticate(String pin) { invalidOp("Already authenticated."); }

    private void invalidOp(String msg) {
        throw new IllegalStateException("[ATM] Invalid operation in AUTHENTICATED state. " + msg);
    }
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 5: CARD
// ══════════════════════════════════════════════════════════════════════════════
//
// Represents a debit card. Stores hashed PIN — never the raw PIN.
//
// PIN HASHING:
// ─────────────
// In production: BCrypt or PBKDF2 with salt.
// For interview demo: we use a simple hash (or store plain with a note).
// ALWAYS mention: "In production I would use BCrypt — never store raw PINs."
//
class Card {

    private final String cardNumber;
    private final String cardHolderName;
    private final String accountId;
    private String hashedPIN;     // never store raw PIN
    private boolean isBlocked;

    public Card(String cardNumber, String cardHolderName, String accountId, String rawPIN) {
        this.cardNumber     = cardNumber;
        this.cardHolderName = cardHolderName;
        this.accountId      = accountId;
        this.hashedPIN      = hashPIN(rawPIN); // hash immediately on construction
        this.isBlocked      = false;
    }

    // Validate: hash the input and compare with stored hash
    public boolean validatePIN(String rawPIN) {
        if (isBlocked) throw new IllegalStateException("Card is blocked. Visit your bank branch.");
        return hashedPIN.equals(hashPIN(rawPIN));
    }

    // Update PIN: store new hashed PIN
    public void updatePIN(String newRawPIN) {
        this.hashedPIN = hashPIN(newRawPIN);
    }

    public void block() { this.isBlocked = true; }

    // INTERVIEW NOTE:
    // "This is a demo hash. In production I'd use BCrypt:
    //  BCrypt.hashpw(rawPIN, BCrypt.gensalt(12))
    //  The salt is stored within the hash string itself — no separate salt column needed."
    private String hashPIN(String rawPIN) {
        // Simple hash for demo — represents the concept
        return String.valueOf(rawPIN.hashCode());
    }

    public String getCardNumber()     { return cardNumber; }
    public String getCardHolderName() { return cardHolderName; }
    public String getAccountId()      { return accountId; }
    public boolean isBlocked()        { return isBlocked; }
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 6: ACCOUNT
// ══════════════════════════════════════════════════════════════════════════════
//
// Represents a bank account.
//
// CONCURRENCY DESIGN — ReentrantLock:
// ─────────────────────────────────────
// WHY ReentrantLock OVER synchronized?
//   1. tryLock(timeout) — avoids indefinite blocking, prevents deadlock
//   2. lockInterruptibly() — thread can be interrupted while waiting
//   3. More explicit — lock/unlock in try/finally makes intent clear
//   4. Reentrant — same thread can acquire it multiple times (future-proofing
//      for complex operations like transfer that touch account twice)
//
// THE CRITICAL RACE CONDITION WE PREVENT:
// ─────────────────────────────────────────
// Thread 1 (ATM-A): reads balance = ₹5000, decides withdrawal of ₹5000 is valid
// Thread 2 (ATM-B): reads balance = ₹5000, decides withdrawal of ₹5000 is valid
// Thread 1: deducts ₹5000 → balance = ₹0
// Thread 2: deducts ₹5000 → balance = -₹5000  ← MONEY CREATED FROM AIR
//
// With lock: Thread 2 can't read balance until Thread 1's entire
// check-and-debit sequence completes. Race condition impossible.
//
class Account {

    private final String      accountId;
    private volatile double   balance;    // volatile for safe single reads without lock
    private final ReentrantLock lock = new ReentrantLock();

    public Account(String accountId, double initialBalance) {
        this.accountId = accountId;
        this.balance   = initialBalance;
    }

    // debit: MUST be called while holding the lock
    public void debit(double amount) {
        if (amount > balance)
            throw new IllegalStateException("Insufficient balance");
        balance -= amount;
    }

    // credit: MUST be called while holding the lock
    public void credit(double amount) {
        balance += amount;
    }

    // getBalance: volatile read — safe without lock for display purposes.
    // For transactional reads (check before debit), always use lock.
    public double getBalance()     { return balance; }
    public String getAccountId()   { return accountId; }
    public ReentrantLock getLock() { return lock; }
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 7: TRANSACTION + ENUMS (immutable audit record)
// ══════════════════════════════════════════════════════════════════════════════
//
// Every ATM operation produces a Transaction — success or failure.
// Immutable: all fields are final. Thread-safe by design.
// This is the audit trail — queryable for dispute resolution.
//
enum TransactionType   { WITHDRAWAL, BALANCE_INQUIRY, PIN_CHANGE }
enum TransactionStatus { SUCCESS, FAILED }

class ATMTransaction {
    private final TransactionType   type;
    private final double            amount;
    private final String            accountId;
    private final TransactionStatus status;
    private final String            failureReason; // null on success
    private final LocalDateTime     timestamp;

    public ATMTransaction(TransactionType type, double amount, String accountId,
                          TransactionStatus status, String failureReason) {
        this.type          = type;
        this.amount        = amount;
        this.accountId     = accountId;
        this.status        = status;
        this.failureReason = failureReason;
        this.timestamp     = LocalDateTime.now();
    }

    @Override
    public String toString() {
        String base = String.format("[TXN] %s | %s | ₹%.0f | Account: %s | %s",
            timestamp, type, amount, accountId, status);
        return (failureReason != null) ? base + " | Reason: " + failureReason : base;
    }
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 8: DISPENSE STRATEGY INTERFACE — STRATEGY PATTERN
// ══════════════════════════════════════════════════════════════════════════════
//
// Separates HOW cash is dispensed from the ATM and CashDispenser mechanics.
// Swap strategy without touching ATM or CashDispenser.
//
interface DispenseStrategy {
    // Returns a map: denomination → count to dispense
    // Returns empty map if amount cannot be dispensed with available notes
    Map<Integer, Integer> dispense(double amount, TreeMap<Integer, Integer> available);
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 9: GREEDY DISPENSE STRATEGY — THE OPTIMAL ALGORITHM
// ══════════════════════════════════════════════════════════════════════════════
//
// ALGORITHM: Greedy — largest denomination first.
//
// WHY GREEDY IS OPTIMAL HERE:
// ────────────────────────────
// Greedy is NOT always optimal for arbitrary coin/note systems.
// For example, denominations [1, 3, 4] and amount 6:
//   Greedy: 4+1+1 = 3 notes.   Optimal: 3+3 = 2 notes.  Greedy fails.
//
// BUT for ATM denominations (e.g., ₹10, ₹20, ₹50, ₹100, ₹200, ₹500, ₹2000),
// greedy IS provably optimal. This is because ATM denomination sets form a
// "canonical coin system" — each larger denomination is a multiple of or
// significantly larger than smaller ones, with no "gaps" that smaller denominations
// can fill more efficiently.
//
// PROOF SKETCH for canonical systems:
// Using a smaller note when a larger one fits can never reduce the count.
// (Formal proof: Pearson 1994 — a denomination set D is canonical iff for every
//  amount A, greedy(A) = optimal(A). ATM denomination sets always satisfy this.)
//
// WHEN WOULD YOU USE DP INSTEAD?
// ─────────────────────────────────
// If the ATM had non-canonical denominations (e.g., ₹1, ₹3, ₹4),
// you'd need DP: dp[amount] = min notes, O(amount × D).
// For ATMs in India or US, greedy is always correct and O(D log D).
//
// TIME:  O(D) where D = number of denomination types (typically 6-7 for ATMs)
// SPACE: O(D) for the result map
//
// INTERVIEW SCRIPT:
// "I use greedy — largest denomination first. Greedy is NOT always optimal
//  for arbitrary coin systems, but it IS provably optimal for canonical
//  denomination sets, which all real ATM denomination sets are.
//  The key property: each denomination divides evenly into larger ones
//  with no gaps that smaller ones can fill more efficiently.
//  If I were designing for arbitrary denominations, I'd switch to DP
//  in O(amount × D) time — that's what the Strategy Pattern enables:
//  swap strategy without touching ATM."
//
class GreedyDispenseStrategy implements DispenseStrategy {

    @Override
    public Map<Integer, Integer> dispense(double amount, TreeMap<Integer, Integer> available) {
        Map<Integer, Integer> result  = new LinkedHashMap<>();
        int remaining = (int) amount; // ATM deals in whole rupees

        // TreeMap.descendingKeySet() gives notes in descending order (₹2000 first)
        // O(D) where D = number of denomination types
        for (int note : available.descendingKeySet()) {
            if (remaining <= 0) break;

            int notesAvailable = available.get(note);
            int notesNeeded    = remaining / note;         // how many of this note fit
            int notesToUse     = Math.min(notesAvailable, notesNeeded); // bounded by stock

            if (notesToUse > 0) {
                result.put(note, notesToUse);
                remaining -= note * notesToUse;
            }
        }

        // If remaining > 0, exact amount couldn't be dispensed with available notes
        if (remaining > 0) return new HashMap<>(); // signal: cannot dispense

        return result;
    }
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 10: CASH DISPENSER
// ══════════════════════════════════════════════════════════════════════════════
//
// Tracks the ATM's physical cash.
// Uses a DispenseStrategy (injected) to compute which notes to give.
// The dispenser is the ONLY place that modifies note counts.
//
// DATA STRUCTURE: TreeMap<Integer, Integer>
// ──────────────────────────────────────────
// Keys = denomination values (e.g., 100, 200, 500, 2000)
// Values = count of notes available
// WHY TREEMAP? Natural ascending ordering — descendingKeySet() for greedy
// is O(D). Also O(log D) put/get — fast for small D.
//
class CashDispenser {

    // TreeMap: sorted by denomination — supports descendingKeySet() for greedy
    private final TreeMap<Integer, Integer> notes;
    private final DispenseStrategy strategy;

    public CashDispenser(DispenseStrategy strategy) {
        this.strategy = strategy;
        this.notes    = new TreeMap<>();
    }

    // Load cash into the ATM (called by ATM technician)
    public void loadCash(int denomination, int count) {
        notes.merge(denomination, count, Integer::sum);
        System.out.println("[DISPENSER] Loaded " + count + " × ₹" + denomination);
    }

    // Check if the amount CAN be dispensed (without actually dispensing)
    // Runs the strategy as a dry-run check
    public boolean canDispense(double amount) {
        Map<Integer, Integer> plan = strategy.dispense(amount, new TreeMap<>(notes));
        return !plan.isEmpty();
    }

    // Actually dispense: compute plan, validate, deduct from stock
    public Map<Integer, Integer> dispense(double amount) {
        Map<Integer, Integer> plan = strategy.dispense(amount, new TreeMap<>(notes));

        if (plan.isEmpty())
            throw new IllegalStateException("Cannot dispense ₹" + amount + " with available notes");

        // Deduct dispensed notes from stock
        plan.forEach((note, count) -> {
            notes.merge(note, -count, Integer::sum);
            if (notes.get(note) == 0) notes.remove(note); // remove exhausted denominations
        });

        return plan;
    }

    // Total cash remaining in ATM
    public double totalCash() {
        return notes.entrySet().stream()
                    .mapToDouble(e -> e.getKey() * e.getValue())
                    .sum();
    }

    public void printStock() {
        System.out.println("[DISPENSER] Current stock:");
        notes.descendingMap().forEach((note, count) ->
            System.out.println("  ₹" + note + " × " + count + " = ₹" + (note * count)));
        System.out.println("  Total: ₹" + totalCash());
    }
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 11: BANK (MOCK BACKEND)
// ══════════════════════════════════════════════════════════════════════════════
//
// In a real system, ATM talks to a bank server via secure API (ISO 8583 protocol).
// For LLD scope, Bank is an in-memory mock that holds accounts.
// The ATM never directly instantiates Account — it asks the Bank.
//
// INTERVIEW SCRIPT:
// "The Bank is a stub here. It represents the real bank backend — the ATM
//  makes calls to it for account lookup. In production this would be a
//  network call to the bank's authorization server."
//
class Bank {

    private final Map<String, Account> accounts = new HashMap<>();
    private final Map<String, Card>    cards     = new HashMap<>();

    public void addAccount(Account account)   { accounts.put(account.getAccountId(), account); }
    public void registerCard(Card card)       { cards.put(card.getCardNumber(), card); }

    public Account getAccount(String accountId) {
        Account a = accounts.get(accountId);
        if (a == null) throw new IllegalArgumentException("Account not found: " + accountId);
        return a;
    }

    public Card getCard(String cardNumber) {
        Card c = cards.get(cardNumber);
        if (c == null) throw new IllegalArgumentException("Card not found: " + cardNumber);
        return c;
    }
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 12: ATM — CONTEXT CLASS (Singleton)
// ══════════════════════════════════════════════════════════════════════════════
//
// The ATM is the CONTEXT in the State Pattern and the FACADE for the user.
// All user interactions go through ATM — it delegates to the current state.
//
// SINGLETON:
// ───────────
// One ATM machine = one instance. The ATM's physical ID, cash, and transaction
// history are unique to that machine. Multiple instances would create isolated
// machines with no shared state — correct for a real deployment where each
// physical ATM has its own JVM process.
//
// For interview: we use Singleton to represent "this ATM machine."
//
// STATE TRANSITIONS:
// ───────────────────
// ATM exposes transitionTo(type) — called by states when they decide to move.
// ATM uses ATMStateFactory to get the next state object.
// This keeps transition logic INSIDE the state (it knows when to transition)
// while the Factory keeps construction centralized.
//
class ATM {

    private static volatile ATM instance;

    private ATMState    currentState;
    private Card        currentCard;
    private Account     currentAccount;

    private final CashDispenser       dispenser;
    private final Bank                bank;
    private final ATMStateFactory     stateFactory;
    private final List<ATMTransaction> transactionLog = new ArrayList<>();
    private final String              atmId;

    // Private constructor — Singleton
    private ATM(String atmId, Bank bank, CashDispenser dispenser) {
        this.atmId      = atmId;
        this.bank       = bank;
        this.dispenser  = dispenser;
        // Factory is created with 'this' reference — builds state cache for THIS ATM
        this.stateFactory = new ATMStateFactory(this);
        // Start in IDLE state
        this.currentState = stateFactory.getState(ATMStateType.IDLE);
    }

    // Double-Checked Locking Singleton
    public static ATM getInstance(String atmId, Bank bank, CashDispenser dispenser) {
        if (instance == null) {
            synchronized (ATM.class) {
                if (instance == null) instance = new ATM(atmId, bank, dispenser);
            }
        }
        return instance;
    }

    // ── STATE MACHINE OPERATIONS (delegated to currentState) ─────────────────
    // ATM has zero conditionals — every method is a pure delegation.
    // The state decides what happens and whether it's valid.

    public void insertCard(Card card)                     { currentState.insertCard(card); }
    public void authenticate(String pin)                  { currentState.authenticate(pin); }
    public void withdraw(double amount)                   { currentState.withdraw(amount); }
    public void checkBalance()                            { currentState.checkBalance(); }
    public void changePIN(String oldPin, String newPin)   { currentState.changePIN(oldPin, newPin); }
    public void ejectCard()                               { currentState.ejectCard(); }

    // ── STATE TRANSITION ─────────────────────────────────────────────────────
    // Called by concrete states when they decide to transition.
    // Factory gives the cached state object for the target type.
    public void transitionTo(ATMStateType type) {
        System.out.println("[ATM] State: " + currentState.getClass().getSimpleName()
                           + " → " + type);
        currentState = stateFactory.getState(type);
    }

    // ── TRANSACTION LOG ──────────────────────────────────────────────────────
    public void recordTransaction(ATMTransaction txn) {
        transactionLog.add(txn);
        System.out.println(txn);
    }

    public void printTransactionLog() {
        System.out.println("\n── Transaction Log for ATM [" + atmId + "] ──");
        transactionLog.forEach(System.out::println);
    }

    // ── GETTERS / SETTERS (used by states) ───────────────────────────────────
    public Card           getCurrentCard()                  { return currentCard; }
    public void           setCurrentCard(Card card)         { this.currentCard = card; }
    public Account        getCurrentAccount()               { return currentAccount; }
    public void           setCurrentAccount(Account acct)   { this.currentAccount = acct; }
    public CashDispenser  getDispenser()                    { return dispenser; }
    public Bank           getBank()                         { return bank; }
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 13: SIMULATION
// ══════════════════════════════════════════════════════════════════════════════
//
// Scenario: Two customers at the same ATM. Covers all flows.
//   Phase 1 — Setup: bank accounts, cards, ATM cash
//   Phase 2 — Happy path: insert card → authenticate → withdraw → eject
//   Phase 3 — Balance inquiry
//   Phase 4 — PIN change
//   Phase 5 — Wrong PIN → lockout after 3 attempts
//   Phase 6 — Insufficient balance
//   Phase 7 — Invalid state access (withdraw without inserting card)
//
public class ATMachineLLD {

    public static void main(String[] args) {

        // ── SETUP: BANK ──────────────────────────────────────────────────────
        System.out.println("══════════════════════════════════════════");
        System.out.println("           PHASE 1: SETUP");
        System.out.println("══════════════════════════════════════════");

        Bank bank = new Bank();

        Account aliceAccount = new Account("ACC001", 15000.0);
        Account bobAccount   = new Account("ACC002", 3000.0);
        bank.addAccount(aliceAccount);
        bank.addAccount(bobAccount);

        Card aliceCard = new Card("CARD001", "Alice", "ACC001", "1234");
        Card bobCard   = new Card("CARD002", "Bob",   "ACC002", "5678");
        bank.registerCard(aliceCard);
        bank.registerCard(bobCard);

        // ── SETUP: ATM ───────────────────────────────────────────────────────
        CashDispenser dispenser = new CashDispenser(new GreedyDispenseStrategy());
        dispenser.loadCash(2000, 10);  // 10 × ₹2000 = ₹20,000
        dispenser.loadCash(500,  20);  // 20 × ₹500  = ₹10,000
        dispenser.loadCash(200,  15);  // 15 × ₹200  = ₹3,000
        dispenser.loadCash(100,  30);  // 30 × ₹100  = ₹3,000
        dispenser.printStock();

        ATM atm = ATM.getInstance("ATM-BLR-001", bank, dispenser);

        // ── PHASE 2: HAPPY PATH — ALICE WITHDRAWS ₹4700 ─────────────────────
        // ₹4700 = 2 × ₹2000 + 1 × ₹500 + 1 × ₹200
        // Greedy: 2000+2000 = 4000, remaining 700 → 500+200 = 700 ✓
        System.out.println("\n══════════════════════════════════════════");
        System.out.println("  PHASE 2: HAPPY PATH — Alice withdraws ₹4700");
        System.out.println("══════════════════════════════════════════");
        atm.insertCard(aliceCard);
        atm.authenticate("1234");
        atm.withdraw(4700);
        atm.ejectCard();

        // ── PHASE 3: BALANCE INQUIRY — BOB ──────────────────────────────────
        System.out.println("\n══════════════════════════════════════════");
        System.out.println("  PHASE 3: BALANCE INQUIRY — Bob");
        System.out.println("══════════════════════════════════════════");
        atm.insertCard(bobCard);
        atm.authenticate("5678");
        atm.checkBalance();
        atm.ejectCard();

        // ── PHASE 4: PIN CHANGE — ALICE ──────────────────────────────────────
        System.out.println("\n══════════════════════════════════════════");
        System.out.println("  PHASE 4: PIN CHANGE — Alice changes to 4321");
        System.out.println("══════════════════════════════════════════");
        atm.insertCard(aliceCard);
        atm.authenticate("1234");
        atm.changePIN("1234", "4321");
        // Verify new PIN works
        atm.ejectCard();
        atm.insertCard(aliceCard);
        atm.authenticate("4321"); // should succeed with new PIN
        System.out.println("[TEST] New PIN works ✓");
        atm.ejectCard();

        // ── PHASE 5: WRONG PIN → LOCKOUT AFTER 3 ATTEMPTS ───────────────────
        System.out.println("\n══════════════════════════════════════════");
        System.out.println("  PHASE 5: WRONG PIN LOCKOUT — Bob");
        System.out.println("══════════════════════════════════════════");
        atm.insertCard(bobCard);
        atm.authenticate("0000"); // wrong
        atm.authenticate("1111"); // wrong
        atm.authenticate("2222"); // wrong → card retained, back to IDLE

        // ── PHASE 6: INSUFFICIENT BALANCE ────────────────────────────────────
        System.out.println("\n══════════════════════════════════════════");
        System.out.println("  PHASE 6: INSUFFICIENT BALANCE — Alice (balance ~₹10,300)");
        System.out.println("══════════════════════════════════════════");
        atm.insertCard(aliceCard);
        atm.authenticate("4321"); // new PIN
        atm.withdraw(50000);      // more than balance → rejected
        atm.ejectCard();

        // ── PHASE 7: INVALID STATE — withdraw without inserting card ─────────
        System.out.println("\n══════════════════════════════════════════");
        System.out.println("  PHASE 7: INVALID STATE — withdraw from IDLE");
        System.out.println("══════════════════════════════════════════");
        try {
            atm.withdraw(500); // ATM is in IDLE — should throw
        } catch (IllegalStateException e) {
            System.out.println("[CAUGHT] " + e.getMessage());
        }

        // ── TRANSACTION LOG ──────────────────────────────────────────────────
        atm.printTransactionLog();
    }
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 14: INTERVIEW FOLLOW-UP Q&A
// ══════════════════════════════════════════════════════════════════════════════
//
// Q1: "Why State Pattern over a switch/enum approach?"
//     A: With switch, every ATM method has the same state validation boilerplate.
//        Add a new state (CardBlocked) → update every switch in every method.
//        With State Pattern: one new class, ATM never changes. Open/Closed.
//        Also: each state class is independently testable.
//
// Q2: "Why Factory for states here? They're not stateless like SplitStrategies."
//     A: States hold ATM reference — not sharable across ATMs.
//        But within one ATM, they CAN be cached and reused.
//        Factory provides per-ATM caching — reuse the same IdleState object
//        every time the ATM returns to IDLE. Less GC. Centralized creation.
//        Add new state = one class + one Factory case. Zero changes to ATM.
//
// Q3: "Why ReentrantLock instead of synchronized?"
//     A: tryLock(timeout) prevents indefinite blocking and potential deadlock.
//        lockInterruptibly() lets threads be cancelled.
//        More explicit — lock/unlock in try/finally makes intent clear.
//        Reentrant — same thread can re-acquire (useful for transfers).
//
// Q4: "How would you handle fund transfer between accounts?"
//     A: Two-account lock ordering to prevent deadlock:
//        Always lock the account with the lower accountId first.
//        Both ATMs follow the same ordering → deadlock impossible.
//        try { lock1.lock(); lock2.lock(); /* transfer */ }
//        finally { lock2.unlock(); lock1.unlock(); }
//
// Q5: "What if the ATM crashes after deducting account balance but before dispensing?"
//     A: This is the distributed systems / 2-Phase Commit problem.
//        Solution: write a PENDING transaction record BEFORE debiting.
//        After successful dispense, mark it COMPLETED.
//        On startup, any PENDING transactions trigger reconciliation:
//        re-check if cash was actually dispensed, refund if not.
//
// Q6: "How would you scale this to 1000 ATMs across the country?"
//     A: Account locking moves to the bank server (optimistic locking with
//        version numbers). Each ATM is stateless except for its cash.
//        Distributed lock: Redis SETNX on accountId before debit.
//        ATM → Bank API is async with idempotency keys to handle retries.
//
// ══════════════════════════════════════════════════════════════════════════════
