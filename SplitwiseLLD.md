# Splitwise — Low Level Design (LLD)
### Interview Preparation Guide | SDE-1 / SDE-2 | ~45 Minutes

---

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [What to Say First](#what-to-say-first)
3. [Class Diagram](#class-diagram)
4. [Design Patterns — All Three](#design-patterns)
5. [The Balance Map](#the-balance-map)
6. [Net-Out Logic](#net-out-logic)
7. [Simplify Debts Algorithm](#simplify-debts-algorithm)
8. [Complexity Analysis](#complexity-analysis)
9. [Interview Time Plan](#interview-time-plan)
10. [Common Follow-up Questions](#common-follow-up-questions)
11. [Improvements to Mention at the End](#improvements)

---

## Problem Statement

> **Design a bill-splitting application (Splitwise) that supports:**
> - Adding users and creating groups
> - Adding expenses with 3 split types: **Equal, Exact, Percentage**
> - Viewing balances: who owes whom, how much
> - **Simplify debts:** compute the minimum transactions to settle everything
> - Settling up: clearing a debt between two users

---

## What to Say First

> *"Let me identify the core entities and three key design decisions.*
>
> *Entities: User, Group, Expense, Split, Transaction.*
>
> *Decision 1 — Split logic: Equal, Exact, and Percentage all produce the same output (who owes what) but compute it differently. I'll use **Strategy Pattern** so Expense never has an if/else for split type.*
>
> *Decision 2 — Strategy creation: Callers shouldn't do `new EqualSplitStrategy()` everywhere. I'll use a **Factory** to centralize this. Since strategies are stateless, the Factory can cache and reuse one instance per type.*
>
> *Decision 3 — Balance storage: A nested Map where `balances[A][B] = X` means A owes B ₹X. O(1) lookup, O(1) update. I net out opposite debts so at most one direction is stored per pair.*
>
> *Decision 4 — Simplify debts: The naive greedy approach (match largest debtor to largest creditor) is NOT always optimal — it can miss independent zero-sum subgroups and produce extra transactions. The truly optimal solution is Bitmask DP: dp[mask] = min transactions to settle everyone in mask. For every zero-sum submask, we try splitting and take the minimum. Time is O(3^N) but N is group size — never more than 15 in practice, so it's fast."*

---

## Class Diagram

```
SplitwiseService «Singleton»
│  users: Map<userId, User>
│  groups: Map<groupId, Group>
│  addUser / createGroup / addExpense / settle / simplifyDebts
│
├──► User (userId, name)
│
└──► Group
      │  members: List<User>
      │  balances: Map<uid, Map<uid, ₹>>   ← THE key data structure
      │  addExpense(expense)
      │  settle(from, to, amount)
      │  simplifyDebts() → List<Transaction>
      │
      └──► Expense
            │  paidBy: User
            │  totalAmount: double
            │  splits: List<Split>
            │  splitType: SplitType ──────────► SplitStrategyFactory
            │                                         │
            │  (constructor calls Factory             │  getStrategy(type)
            │   then strategy.calculate())            │
            │                                   ┌─────┴──────────────────┐
            └──► Split (user, amount)           │  «interface»           │
                                                │  SplitStrategy         │
                                                │  calculate(amt, splits)│
                                                └────────────┬───────────┘
                                                             │ implements
                                               ┌────────────┼──────────────┐
                                         EqualSplit    ExactSplit   PercentageSplit
                                         Strategy      Strategy     Strategy
```

---

## Design Patterns

### 1. Strategy — `SplitStrategy`

The "strategy" is how to divide the bill. Three implementations:

| Strategy | Input (on Split) | What it does |
|---------|-----------------|-------------|
| `EqualSplitStrategy` | Nothing (just users) | `totalAmount / n` for each |
| `ExactSplitStrategy` | Exact rupee amount | Validates sum == total |
| `PercentageSplitStrategy` | Percentage (0–100) | Validates sum == 100%, converts to ₹ |

Expense calls `strategy.calculate(total, splits)` — **zero if/else**.

---

### 2. Factory — `SplitStrategyFactory`

Centralizes strategy creation. Callers use `SplitType.EQUAL`, Factory returns the strategy.

**Without Factory — problem:**
```java
// Scattered everywhere:
new EqualSplitStrategy()   // in PaymentController
new EqualSplitStrategy()   // in GroupService
new EqualSplitStrategy()   // in TestHelper
// Constructor changes → fix all 3 places
```

**With Factory — solution:**
```java
SplitStrategyFactory.getStrategy(SplitType.EQUAL)
// Constructor changes → fix ONE place: the Factory
```

**Bonus — cached instances (Flyweight inside Factory):**
```java
// Strategies are stateless — safe to share
private static final SplitStrategy EQUAL = new EqualSplitStrategy();
// Returns same cached object every time — zero GC pressure
```

**Adding a new split type:**
1. Add `SHARES` to `SplitType` enum
2. Create `SharesSplitStrategy implements SplitStrategy`
3. Add `case SHARES: return SHARES_STRATEGY` to Factory

Zero changes to `Expense`, `Group`, `SplitwiseService`, or any callers.

---

### 3. Singleton — `SplitwiseService`

One service holds all users and groups. Double-Checked Locking:

```java
private static volatile SplitwiseService instance;

public static SplitwiseService getInstance() {
    if (instance == null) {                    // fast path — no lock after init
        synchronized (SplitwiseService.class) {
            if (instance == null)              // safe — handles race on first init
                instance = new SplitwiseService();
        }
    }
    return instance;
}
```

---

## The Balance Map

**Most important thing to explain before writing any Group code.**

```java
Map<String, Map<String, Double>> balances;
// balances.get("alice").get("bob") = 300.0  →  Alice owes Bob ₹300
```

**Rules:**
- Only ONE direction stored per pair at any time
- Zero or missing = no debt
- Net-out on every `addExpense()` update

**Why nested Map and not a list?**

| Operation | Nested Map | List of debt records |
|-----------|-----------|---------------------|
| Does Alice owe Bob? | O(1) | O(n) scan |
| Update after expense | O(1) | O(n) find + update |
| Settle a debt | O(1) | O(n) find + update |

---

## Net-Out Logic

The most interesting part of `addExpense()`. This is what separates good candidates.

**Scenario:**
```
Before: Bob owes Alice ₹300 (hotel)
New:    Alice owes Bob ₹300 (dinner)
→ Without net-out: two entries, two transactions needed
→ With net-out: cancel out completely, zero entries
```

**Three cases:**

```
reverseDebt = balances[lender][borrower]  (does lender already owe borrower?)

Case A: reverseDebt >= newAmount
  → Lender's reverse debt absorbs new debt
  → balances[lender][borrower] -= newAmount

Case B: 0 < reverseDebt < newAmount
  → Partially absorbed, then flip direction
  → balances[lender][borrower] = 0
  → balances[borrower][lender] += (newAmount - reverseDebt)

Case C: reverseDebt == 0
  → Straight addition
  → balances[borrower][lender] += newAmount
```

**What to say:**
> *"Before updating, I check if the opposite debt exists. This ensures at most one direction of debt per pair. Without this, you'd need two transactions where one would suffice."*

---

## Simplify Debts Algorithm

**Why it's needed:**
```
Raw debts (many transactions):       Simplified (minimum transactions):
Alice → Bob    ₹100                  Alice → Diana ₹200
Bob   → Charlie ₹100        →        Bob   → Diana ₹100
Charlie → Diana ₹200
Bob   → Diana  ₹100
```

---

### Why Greedy FAILS

Greedy approach: always match the largest debtor with the largest creditor.

**Counterexample:** net balances = `[A=-6, B=-4, C=+3, D=+3, E=+4]`

```
Optimal eye sees two independent zero-sum groups:
  Group 1: { B=-4, E=+4 } → sum=0 → 1 transaction:  B pays E ₹4
  Group 2: { A=-6, C=+3, D=+3 } → sum=0 → 2 transactions: A pays C ₹3, A pays D ₹3
  TOTAL = 3 ✓

Greedy does:
  Step 1: Largest debtor A(6) vs Largest creditor E(4) → A pays E ₹4
          *** MISTAKE: just destroyed the perfect B↔E pair ***
  Step 2: B(4) vs C(3) → B pays C ₹3 → B=-1, C=0
  Step 3: A(2) vs D(3) → A pays D ₹2 → A=0, D=+1
  Step 4: B(1) vs D(1) → B pays D ₹1 → B=0, D=0
  TOTAL = 4 ✗  (1 worse than optimal)
```

**Root cause:** Greedy only sees amounts. It doesn't check whether two people form an independent zero-sum group that can settle among themselves. By pairing A with E, it "uses up" E and forces B into a longer chain.

**What to say in the interview:**
> *"Greedy is locally optimal — it resolves the most debt per transaction — but it's not globally optimal. It misses cases where people form independent zero-sum subgroups. The truly optimal solution is Bitmask DP."*

---

### Bitmask DP — The Truly Optimal Solution

**Key insight:** A zero-sum group of K people always needs exactly K-1 transactions. So the goal is to partition everyone into the **maximum number of independent zero-sum subgroups**.

**DP definition:**
```
dp[mask] = minimum transactions to settle all people whose bit is set in mask

Base case:    dp[0] = 0
Default cost: dp[mask] = popcount(mask) - 1   (treat as one big group, K-1 txns)

Recurrence (for every mask where sum(mask) == 0):
  For every zero-sum submask S of mask:
    dp[mask] = min(dp[mask],  dp[S] + dp[mask ^ S])
                              └──────  └──────────┘
                              cost for  cost for rest
                              subgroup
```

**Why `popcount - 1`?** Each transaction eliminates exactly one person (their balance hits 0). K people → K-1 steps to clear all.

**Why only process masks where `sum(mask) == 0`?** A set of people can only settle among themselves if their net balances sum to zero. Any other mask is invalid — skip it.

**Submask enumeration trick:**
```java
for (int sub = (mask-1) & mask; sub > 0; sub = (sub-1) & mask)
// Visits every non-empty proper subset of mask's set bits
// (sub-1) clears the lowest set bit, & mask keeps only valid bits
```

**Worked example on counterexample:**
```
People: [0: A=-6,  1: B=-4,  2: C=+3,  3: D=+3,  4: E=+4]
                                  (converted to paise: *100)

Key dp states computed:
  mask=00110  {B=-4, E=+4}          sum=0  dp=1   (1 perfect pair)
  mask=11001  {A=-6, C=+3, D=+3}    sum=0  dp=2   (chain of 2)
  mask=11111  {all 5}               sum=0
    default: popcount(5)-1 = 4
    try split: sub=00110, rest=11001 → dp[00110]+dp[11001] = 1+2 = 3 ✓
    dp[11111] = 3

ANSWER = 3  ✓  (greedy gave 4)
```

**Precomputing subset sums (O(2^N)):**
```java
// sum[mask] = sum[mask without lowest bit] + bal[lowestBitIndex]
// Built iteratively — avoids O(N * 2^N) re-summing in the DP loop
for (int mask = 1; mask < (1 << N); mask++) {
    int lowestBit = mask & (-mask);
    int idx       = Integer.numberOfTrailingZeros(lowestBit);
    subsetSum[mask] = subsetSum[mask ^ lowestBit] + bal[idx];
}
```

**Why O(3^N)?** Each element has 3 states across the submask enumeration: not in mask / in mask but not submask / in both. 3 choices × N elements = 3^N total iterations.

**Practical performance:**

| N (group size) | 3^N ops | Time estimate |
|---------------|---------|--------------|
| 10 | 59,049 | ~microseconds |
| 15 | 14.3M | ~milliseconds |
| 20 | 3.5B | needs pruning |

N is always group size, never total users. Real groups rarely exceed 10–12 people.

---

### Implementation Structure

Three methods work together inside `Group`:

```
simplifyDebts()
  ├── Step 1: Compute net balances from the balance map
  ├── Step 2: Remove zeros (already settled — shrinks N)
  ├── Step 3: Precompute subsetSum[] in O(2^N)
  ├── Step 4: Fill dp[] via submask enumeration in O(3^N)
  └── Step 5: Call reconstructTransactions() to get actual Transaction objects

reconstructTransactions(mask)
  ├── Finds which submask split achieved dp[mask]
  └── Recurses on both halves — builds the optimal partition tree

settleGroup(mask)
  └── Within one atomic zero-sum group: two-pointer on sorted balances
      generates K-1 actual Transaction objects
```

**Balance → Integer conversion:** The balance map stores `double` rupees. Inside `simplifyDebts()` they are multiplied by 100 and rounded to integer paise for exact DP subset sums. Converted back to rupees when creating `Transaction` objects.

---

## Complexity Analysis

| Operation | Time | Notes |
|-----------|------|-------|
| `addUser` / `createGroup` | O(1) | HashMap put |
| `addMemberToGroup` | O(1) | List add |
| `EqualSplitStrategy.calculate` | O(n) | n = participants |
| `ExactSplitStrategy.calculate` | O(n) | validation pass |
| `PercentageSplitStrategy.calculate` | O(n) | two passes |
| `addExpense` | O(n) | n = splits, each O(1) map update |
| `settle` | O(1) | direct map update |
| `getBalance(A,B)` | O(1) | nested map lookup |
| `simplifyDebts` | O(3^N) | N = non-zero members in group. Precompute O(2^N). |
| `reconstructTransactions` | O(2^N) | One pass to find optimal partition |
| `printBalances` | O(M²) | M = members, sparse in practice |
| `SplitStrategyFactory.getStrategy` | O(1) | switch + return cached |

---

## Interview Time Plan

| Time | What to do |
|------|-----------|
| 0–5 min | Clarify requirements. Opening statement. Sketch entities. |
| 5–8 min | Code `SplitType` enum + `Split` value object |
| 8–12 min | Code `SplitStrategy` interface + all 3 concrete strategies |
| 12–16 min | Code `SplitStrategyFactory` — explain caching + Open/Closed |
| 16–22 min | Code `Expense` — show Factory usage, fail-fast in constructor |
| 22–32 min | Code `Group` — balance map + `addExpense` with net-out logic |
| 32–38 min | Code `simplifyDebts` — explain greedy failure first, then DP recurrence |
| 38–42 min | Code `SplitwiseService` Singleton + simulation |
| 42–45 min | Walk through output. List improvements. |

---

## Common Follow-up Questions

### Q: Why Factory here, not just `new EqualSplitStrategy()` directly?
Three reasons: (1) centralized creation — constructor changes affect one place. (2) Caching — stateless strategies reused, zero GC. (3) Open/Closed — new split type = new class + one Factory case, zero caller changes.

### Q: Is `simplifyDebts` always optimal?
**Yes — Bitmask DP is provably optimal. Greedy was not.**
The counterexample: net `[A=-6, B=-4, C=+3, D=+3, E=+4]` — greedy produces 4 transactions, DP produces 3.
Greedy misses the `{B, E}` zero-sum pair because it picks `A` first (larger magnitude).
DP finds the maximum number of independent zero-sum subgroups, which directly minimizes total transactions.
Complexity: O(3^N) time, O(2^N) space — practical since N is group size, never total users.

### Q: How to handle floating point precision for money?
Use `BigDecimal` everywhere — never `double`. IEEE 754: `0.1 + 0.2 = 0.30000000000000004`. `BigDecimal` gives exact decimal arithmetic with configurable rounding (`HALF_UP` for money).

### Q: How to make it thread-safe?
Add `synchronized` to `Group.addExpense()` and `Group.settle()`. For high concurrency: `ConcurrentHashMap` + `compute()` for atomic balance updates. `simplifyDebts()` is read-only — safe without locking if no concurrent writes.

### Q: How to add notifications when someone is added to a debt?
**Observer Pattern** on `addExpense()`. Each User registers as an observer. After balances update, notify affected users. Notification channel (push, SMS, email) is its own Strategy — `NotificationStrategy`.

### Q: What if you want to add a "shares" split type? (e.g., Alice=2 shares, Bob=1 share)
1. Add `SHARES` to `SplitType` enum
2. Create `SharesSplitStrategy` — sum all shares, each person pays `(theirShares / totalShares) * total`
3. Add `case SHARES` to `SplitStrategyFactory`

**Zero changes to Expense, Group, Service, or existing callers.**

---

## Improvements

Mention these at the end when asked *"what would you improve?"*

| Feature | Approach | Pattern |
|---------|----------|---------|
| Shares-based split | New `SharesSplitStrategy` + Factory case | Strategy + Factory |
| Multiple currencies | `CurrencyConverter` + base currency storage | Strategy |
| Floating point precision | Replace `double` with `BigDecimal` | — |
| Notifications | Notify on debt creation/settlement | Observer |
| Thread safety | `synchronized` or `ConcurrentHashMap` | — |
| Persistence | `GroupRepository`, `ExpenseRepository` | Repository |
| Recurring expenses | Scheduler + expense template | — |
| Export balances | PDF/CSV generator | Strategy |
| Expense categories | `ExpenseCategory` enum + filter queries | — |

---

## SOLID Principles Applied

| Principle | Where |
|-----------|-------|
| **S**ingle Responsibility | Strategy computes. Factory creates. Expense holds data. Group manages balances. |
| **O**pen/Closed | New split type = one new class + one Factory case. Zero changes to existing code. |
| **L**iskov Substitution | All three strategies are interchangeable wherever `SplitStrategy` is used. |
| **I**nterface Segregation | `SplitStrategy` has exactly one method — no unused methods forced on implementors. |
| **D**ependency Inversion | `Expense` depends on `SplitStrategy` abstraction, not `EqualSplitStrategy` directly. |

---

*All decisions are intentional and explainable. Focus in interview: Balance Map + Net-Out + why Greedy fails + Bitmask DP recurrence.*
