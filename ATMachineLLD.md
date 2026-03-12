# ATM Machine — Low Level Design (LLD)
### Interview Preparation Guide | SDE-1 / SDE-2 | ~45 Minutes

---

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [What to Say First](#what-to-say-first)
3. [Class Diagram](#class-diagram)
4. [Design Patterns — All Four](#design-patterns)
5. [State Transitions](#state-transitions)
6. [Cash Dispensing Algorithm](#cash-dispensing-algorithm)
7. [Concurrency Design](#concurrency-design)
8. [Complexity Analysis](#complexity-analysis)
9. [Interview Time Plan](#interview-time-plan)
10. [Common Follow-up Questions](#common-follow-up-questions)
11. [Improvements to Mention at the End](#improvements)

---

## Problem Statement

> **Design an ATM Machine that supports:**
> - Card insertion and PIN authentication (debit cards only)
> - Cash withdrawal with denomination tracking
> - Balance inquiry
> - PIN change
> - 3-attempt PIN lockout (card retained on failure)
> - Concurrency: multiple ATMs serving the same account simultaneously

---

## What to Say First

> *"I see four design decisions before writing any code.*
>
> *Decision 1 — State management → State Pattern. An ATM has strict state transitions. You can't withdraw before authenticating. You can't authenticate before inserting a card. If I use switch/if-else in every ATM method, adding a new state (say CardBlocked) means touching every conditional. State Pattern gives each state its own class — ATM just delegates. One new state = one new class, zero changes to ATM.*
>
> *Decision 2 — State creation → Factory Pattern. ATM never calls `new IdleState()` directly. The Factory centralizes state creation and caches one instance per state per ATM, since states hold an ATM reference (not sharable across ATMs, but reusable within one ATM).*
>
> *Decision 3 — Cash dispensing → Strategy Pattern. Different ATMs may have different dispensing policies. The algorithm is injected — ATM never knows which strategy it's using. Swap to DP-based dispensing for non-canonical denomination sets without touching ATM.*
>
> *Decision 4 — Concurrency → ReentrantLock on Account. Two ATMs can hit the same account simultaneously. Balance check and debit must be atomic together. I use tryLock with timeout to avoid deadlock."*

---

## Class Diagram

```
ATM «Singleton»
│  currentState: ATMState       ← delegated to for every user action
│  currentCard: Card
│  currentAccount: Account
│  dispenser: CashDispenser
│  bank: Bank
│  stateFactory: ATMStateFactory
│
│  insertCard() / authenticate() / withdraw()
│  checkBalance() / changePIN() / ejectCard()
│  transitionTo(ATMStateType)   ← called by states to trigger transitions
│
├──► ATMStateFactory
│       cache: Map<ATMStateType, ATMState>  ← per-ATM cache
│       getState(type): ATMState
│
├──► ATMState «interface»
│       insertCard / authenticate / withdraw
│       checkBalance / changePIN / ejectCard
│          │ implements
│    ┌─────┼──────────────────┐
│  IdleState  CardInserted    AuthenticatedState
│             State
│             (tracks failed attempts)
│
├──► CashDispenser
│       notes: TreeMap<Integer, Integer>   ← denomination → count
│       strategy: DispenseStrategy
│       canDispense(amount): boolean
│       dispense(amount): Map<Integer,Integer>
│
├──► DispenseStrategy «interface»
│       dispense(amount, available): Map<Integer,Integer>
│          │ implements
│       GreedyDispenseStrategy  (optimal for canonical denomination sets)
│
├──► Card
│       cardNumber, cardHolderName, accountId
│       hashedPIN   ← never raw PIN
│       validatePIN(raw) / updatePIN(new) / block()
│
├──► Account
│       accountId, balance (volatile)
│       lock: ReentrantLock   ← concurrency guard
│       debit() / credit() / getBalance()
│
└──► ATMTransaction (immutable)
        type, amount, accountId, status, failureReason, timestamp
```

---

## Design Patterns

### 1. State — `ATMState`

**The problem it solves:** Without State Pattern, every ATM method has the same guard:
```java
// WITHOUT State Pattern — fragile:
public void withdraw(double amount) {
    if (state == IDLE)          throw new Exception("Insert card first");
    if (state == CARD_INSERTED) throw new Exception("Authenticate first");
    // actual logic...
}
// Add CardBlocked state → fix every single method. Error-prone.
```

**With State Pattern:**
```java
// ATM has zero conditionals — pure delegation:
public void withdraw(double amount) { currentState.withdraw(amount); }
// State decides what happens. ATM never changes.
```

**Invalid operations:** Each state implements `invalidOp(msg)` helper — throws `IllegalStateException` with a user-friendly message. No duplicated throw logic.

---

### 2. Factory — `ATMStateFactory`

**Key distinction from Splitwise Factory:** States here hold an ATM reference — they are NOT sharable across ATMs. But within one ATM, the same `IdleState` object can be reused every time we return to IDLE.

```
Per-ATM caching:
  ATMStateFactory(atm) constructor pre-builds all 3 states for this ATM.
  getState(IDLE) always returns the same cached IdleState for this ATM.
  Different ATMs have different factories → different cached objects.
```

**Adding a new state (e.g., `CardBlockedState`):**
1. Add `CARD_BLOCKED` to `ATMStateType` enum
2. Create `CardBlockedState implements ATMState`
3. Add `cache.put(ATMStateType.CARD_BLOCKED, new CardBlockedState(atm))` in Factory constructor

Zero changes to ATM, zero changes to other states.

---

### 3. Strategy — `DispenseStrategy`

Separates the cash dispensing algorithm from ATM mechanics. ATM and CashDispenser never know which algorithm is running.

```java
// Swap dispensing algorithm without touching ATM:
CashDispenser dispenser = new CashDispenser(new GreedyDispenseStrategy()); // current
CashDispenser dispenser = new CashDispenser(new DPDispenseStrategy());     // future
```

---

### 4. Singleton — `ATM`

One physical machine = one instance. Uses Double-Checked Locking with `volatile`.

```java
private static volatile ATM instance;

public static ATM getInstance(String atmId, Bank bank, CashDispenser dispenser) {
    if (instance == null) {
        synchronized (ATM.class) {
            if (instance == null) instance = new ATM(atmId, bank, dispenser);
        }
    }
    return instance;
}
```

---

## State Transitions

```
                    insertCard()
    ┌──────────────────────────────────────────────────┐
    ▼                                                  │  ejectCard()
  IDLE  ──insertCard()──►  CARD_INSERTED  ──authenticate()──►  AUTHENTICATED
    ▲                           │                               │
    │                           │ ejectCard()                  │ ejectCard()
    │                           └───────────────────────────────┘
    │
    └────────── 3 wrong PINs → card.block() → back to IDLE
```

**Key transition rules:**

| From | Action | To | Condition |
|------|--------|----|-----------|
| IDLE | insertCard() | CARD_INSERTED | Always |
| CARD_INSERTED | authenticate() | AUTHENTICATED | PIN correct |
| CARD_INSERTED | authenticate() | IDLE | 3rd wrong PIN (card blocked) |
| CARD_INSERTED | ejectCard() | IDLE | Always |
| AUTHENTICATED | ejectCard() | IDLE | Always |
| AUTHENTICATED | withdraw/balance/changePIN | AUTHENTICATED | Stays in state |

---

## Cash Dispensing Algorithm

### Why Greedy is Optimal for ATM Denominations

Greedy (largest denomination first) is **NOT** always optimal for arbitrary coin systems.

**Counterexample for arbitrary denominations:**
```
Denominations: [1, 3, 4],  Amount: 6
Greedy:  4 + 1 + 1 = 3 notes   ✗
Optimal: 3 + 3     = 2 notes   ✓
```

**But for ATM denominations (₹10, ₹20, ₹50, ₹100, ₹200, ₹500, ₹2000), greedy IS provably optimal.**

The property that makes them canonical: each denomination is large enough relative to smaller ones that using a larger note can never be bettered by a combination of smaller notes. This is the "canonical coin system" property (Pearson, 1994).

**Greedy on ATM example:**
```
Withdraw ₹4700. Available: 2000×10, 500×20, 200×15, 100×30
  Step 1: 4700 / 2000 = 2 notes → remaining = 700
  Step 2:  700 /  500 = 1 note  → remaining = 200
  Step 3:  200 /  200 = 1 note  → remaining = 0
  Result: 2×₹2000 + 1×₹500 + 1×₹200 = 4 notes ✓ OPTIMAL
```

**When would you need DP?**
If the ATM used non-canonical denominations. DP: `dp[i] = min notes for amount i`, O(amount × D). The Strategy Pattern enables this swap without touching the ATM.

**Greedy complexity:**
- Time: O(D) where D = denomination types (typically 6–7 for real ATMs)
- Space: O(D) for result map

---

## Concurrency Design

### The Race Condition We Prevent

```
WITHOUT lock:
  ATM-A: reads balance = ₹5000, checks ≥ ₹5000 → OK
  ATM-B: reads balance = ₹5000, checks ≥ ₹5000 → OK  ← interleaved!
  ATM-A: deducts ₹5000 → balance = ₹0
  ATM-B: deducts ₹5000 → balance = -₹5000  ← MONEY CREATED

WITH ReentrantLock:
  ATM-A acquires lock → read balance → deduct → release lock
  ATM-B waits → acquires lock → reads ₹0 → rejects → release
  ✓ Correct
```

### Why ReentrantLock over `synchronized`

| Feature | `synchronized` | `ReentrantLock` |
|---------|---------------|-----------------|
| tryLock with timeout | ❌ | ✅ Prevents deadlock |
| Interruptible wait | ❌ | ✅ Thread can be cancelled |
| Re-entrant | ✅ | ✅ Same thread can re-acquire |
| Explicit intent | ❌ | ✅ lock()/unlock() in try/finally |

### Why `tryLock(timeout)` specifically

`lock()` blocks indefinitely. If a future feature requires locking two accounts (fund transfer), two ATMs locking in opposite order could deadlock forever. `tryLock(timeout)` returns `false` instead — we can back off and retry. Best practice in any financial system.

### `volatile` on balance

`balance` is declared `volatile` — guarantees that single reads (for display in `checkBalance`) see the latest value across threads. For transactional reads (check-then-debit), we still require the lock — volatile alone doesn't make compound operations atomic.

---

## Complexity Analysis

| Operation | Time | Notes |
|-----------|------|-------|
| `insertCard` | O(1) | Map lookup in Bank |
| `authenticate` | O(1) | Hash comparison |
| `withdraw` | O(D) | D = denomination types (≤7) |
| `checkBalance` | O(1) | volatile read |
| `changePIN` | O(1) | Hash + update |
| `ejectCard` | O(1) | State transition |
| `canDispense` | O(D) | Dry-run greedy |
| `dispense` | O(D) | Greedy + deduct stock |
| `transitionTo` | O(1) | Map lookup in Factory cache |
| `loadCash` | O(log D) | TreeMap insert |

---

## Interview Time Plan

| Time | What to do |
|------|-----------|
| 0–5 min | Clarify. Open with all 4 design decisions. Sketch state diagram. |
| 5–10 min | Code `ATMStateType` enum + `ATMState` interface |
| 10–15 min | Code `ATMStateFactory` — explain per-ATM caching |
| 15–25 min | Code all 3 concrete states — explain invalid op helper |
| 25–30 min | Code `Account` with `ReentrantLock` — explain race condition |
| 30–35 min | Code `DispenseStrategy` + `GreedyDispenseStrategy` — explain why greedy is optimal |
| 35–40 min | Code `ATM` Singleton + `CashDispenser` + `Bank` |
| 40–45 min | Run simulation. Walk through output. Mention improvements. |

---

## Common Follow-up Questions

### Q: Why State Pattern over switch/enum in ATM?
With switch: every ATM method has the same state guard boilerplate. Add a new state → fix every method. With State Pattern: one new class + one Factory registration. ATM never changes. Each state is independently testable.

### Q: Why Factory for states — they're not stateless like SplitStrategies?
States hold ATM reference — can't share across ATMs. But within ONE ATM, the same `IdleState` can be reused every session. Factory provides per-ATM caching: one instance per state per ATM, created upfront. Centralized creation — adding a state = one class + one Factory line.

### Q: How to handle fund transfer between accounts?
Lock ordering to prevent deadlock: always acquire the lock for the lower `accountId` first. Both threads follow the same ordering → circular wait impossible → no deadlock.

### Q: What if ATM crashes after debiting but before dispensing?
Two-phase approach: write a `PENDING` transaction BEFORE debiting. Mark `COMPLETED` after dispensing. On restart, check for any `PENDING` transactions and reconcile — refund if cash was not actually dispensed.

### Q: How would you add a `CardBlocked` state?
1. Add `CARD_BLOCKED` to `ATMStateType`
2. Create `CardBlockedState implements ATMState` — most operations throw "Card is blocked. Visit your bank."
3. Add one line in `ATMStateFactory` constructor

Zero other changes.

### Q: Why is balance declared `volatile`?
For single display reads (`checkBalance`) — guarantees latest value is seen across threads without lock overhead. For transactional reads (check before debit) — volatile alone is insufficient; we still need the lock because check-and-debit is a compound operation.

---

## Improvements

| Feature | Approach | Pattern/Note |
|---------|----------|-------------|
| CardBlocked state | New `CardBlockedState` + Factory entry | State + Factory |
| Daily withdrawal limit per card | Track in Card, check in AuthenticatedState | — |
| Receipt printing | `ReceiptPrinter` with print strategy | Strategy |
| Network bank calls | Replace `Bank` with `BankService` (HTTP/ISO8583) | Adapter |
| Non-canonical denominations | `DPDispenseStrategy` as alternative | Strategy |
| Concurrent ATMs on same account | Distributed lock (Redis SETNX) | — |
| Crash recovery | PENDING transaction + reconciliation on restart | — |
| Card types (credit, prepaid) | Subclass or interface for `Card` | Polymorphism |
| Audit logging to DB | Observer on `recordTransaction` | Observer |
| Multiple currencies | Add currency field + FX converter | Strategy |

---

## SOLID Principles Applied

| Principle | Where |
|-----------|-------|
| **S**ingle Responsibility | State handles logic. Factory creates. Dispenser manages cash. Account manages balance. |
| **O**pen/Closed | New state = new class + one Factory line. New dispense algorithm = new Strategy. Nothing else changes. |
| **L**iskov Substitution | All states are interchangeable behind `ATMState` interface. All strategies behind `DispenseStrategy`. |
| **I**nterface Segregation | `ATMState` has exactly the operations a user performs — nothing else forced on implementors. |
| **D**ependency Inversion | ATM depends on `ATMState` interface, not `IdleState` directly. `CashDispenser` depends on `DispenseStrategy` interface. |

---

*Focus in interview: State transitions diagram + why State Pattern beats switch + concurrency race condition + why greedy is optimal for ATM denominations.*
