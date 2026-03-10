import java.util.*;

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║               SPLITWISE — LOW LEVEL DESIGN (INTERVIEW READY)               ║
// ║                       Target: SDE-1 / SDE-2 Roles                          ║
// ║                       Time to cover: ~45 mins                              ║
// ║                                                                              ║
// ║  DESIGN PATTERNS (Name ALL of these upfront in the interview):              ║
// ║  ─────────────────────────────────────────────────────────────              ║
// ║  1. STRATEGY  → SplitStrategy  (swap split logic without changing Expense) ║
// ║  2. FACTORY   → SplitStrategyFactory (centralize strategy creation)        ║
// ║  3. SINGLETON → SplitwiseService (one shared service across the app)       ║
// ║                                                                              ║
// ║  WHAT TO SAY FIRST:                                                         ║
// ║  ──────────────────                                                         ║
// ║  "There are three key design decisions here:                                ║
// ║                                                                              ║
// ║   1. Split logic → Strategy Pattern. Equal, Exact, and Percentage splits   ║
// ║      all produce the same output (who owes what) but compute it            ║
// ║      differently. Expense delegates to a SplitStrategy — zero if/else.    ║
// ║                                                                              ║
// ║   2. Strategy creation → Factory Pattern. Callers never do                 ║
// ║      'new EqualSplitStrategy()' — one Factory centralizes this.            ║
// ║      Adding a new split type only touches the Factory, not the callers.    ║
// ║                                                                              ║
// ║   3. Balance storage → nested Map. balances[A][B] = X means A owes B ₹X.  ║
// ║      O(1) lookup, O(1) update. We net out opposite debts to keep it clean. ║
// ║                                                                              ║
// ║   4. Simplify debts → Bitmask DP (truly optimal). Greedy fails on inputs   ║
// ║      where independent zero-sum subgroups exist. DP finds the true minimum ║
// ║      by trying every zero-sum partition of the group via dp[mask]."        ║
// ╚══════════════════════════════════════════════════════════════════════════════╝


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 1: USER
// ══════════════════════════════════════════════════════════════════════════════
class User {
    private final String userId;
    private final String name;

    public User(String userId, String name) {
        this.userId = userId;
        this.name   = name;
    }

    public String getUserId() { return userId; }
    public String getName()   { return name; }

    @Override public String toString() { return name + "(" + userId + ")"; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        return userId.equals(((User) o).userId);
    }
    @Override public int hashCode() { return userId.hashCode(); }
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 2: SPLIT TYPE ENUM
// ══════════════════════════════════════════════════════════════════════════════
//
// WHY AN ENUM?
// ─────────────
// Type-safe input to the Factory. Compiler rejects invalid split types.
// All valid types are in one place — single source of truth.
// Adding a type = add one enum value + one Factory case. Nothing else.
//
// INTERVIEW SCRIPT:
// "SplitType enum is the input to the Factory. Clients say SplitType.EQUAL
//  and the Factory returns the right strategy. No strings, no magic values."
//
enum SplitType {
    EQUAL,       // divide total equally among all participants
    EXACT,       // each person owes a pre-specified fixed amount
    PERCENTAGE   // each person owes a percentage of the total
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 3: SPLIT — Value object for one person's share
// ══════════════════════════════════════════════════════════════════════════════
//
// Holds WHO owes money (user) and HOW MUCH (amount).
// The amount field is dual-purpose for PercentageSplit:
//   Before calculate() → holds the percentage (e.g., 40.0 = 40%)
//   After calculate()  → holds the actual rupee amount (e.g., ₹160)
//
class Split {
    private final User user;
    private double amount;

    public Split(User user)                { this.user = user; this.amount = 0.0; }
    public Split(User user, double amount) { this.user = user; this.amount = amount; }

    public User   getUser()                { return user; }
    public double getAmount()              { return amount; }
    public void   setAmount(double amount) { this.amount = amount; }
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 4: SPLIT STRATEGY INTERFACE — STRATEGY PATTERN
// ══════════════════════════════════════════════════════════════════════════════
//
// STRATEGY PATTERN:
// ─────────────────
// Defines a family of algorithms (split calculations), encapsulates each,
// and makes them interchangeable. Expense uses this interface without knowing
// which concrete implementation it holds.
//
// WHY INTERFACE (NOT ABSTRACT CLASS)?
// ─────────────────────────────────────
// Strategies have NO shared state. Interface is sufficient and leaner.
// If we needed shared utility methods (e.g., roundToPaise()), abstract class.
//
// INTERVIEW SCRIPT:
// "This is the Strategy interface. Each concrete class encapsulates one split
//  algorithm. Expense calls calculate() without knowing which strategy it has.
//  Open/Closed Principle: add new split types without modifying Expense at all."
//
interface SplitStrategy {
    // Fills in the 'amount' field on each Split.
    // Throws IllegalArgumentException if inputs are invalid.
    void calculate(double totalAmount, List<Split> splits);
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 5: CONCRETE STRATEGIES
// ══════════════════════════════════════════════════════════════════════════════

// ── EQUAL SPLIT ──────────────────────────────────────────────────────────────
// ₹1200 among 4 → ₹300 each.
// ROUNDING: For production, last person absorbs rounding residue. Use BigDecimal.
//
class EqualSplitStrategy implements SplitStrategy {
    @Override
    public void calculate(double totalAmount, List<Split> splits) {
        if (splits == null || splits.isEmpty())
            throw new IllegalArgumentException("Splits list cannot be empty");
        double share = totalAmount / splits.size();
        for (Split split : splits) split.setAmount(share);
    }
}


// ── EXACT SPLIT ──────────────────────────────────────────────────────────────
// Amounts are pre-set on each Split. This strategy ONLY validates they sum to total.
// INTERVIEW SCRIPT: "ExactSplitStrategy is a validator. Fail fast if sum ≠ total."
//
class ExactSplitStrategy implements SplitStrategy {
    private static final double EPSILON = 0.01; // 1 paisa tolerance

    @Override
    public void calculate(double totalAmount, List<Split> splits) {
        double sum = 0;
        for (Split split : splits) sum += split.getAmount();
        if (Math.abs(sum - totalAmount) > EPSILON)
            throw new IllegalArgumentException(
                "Exact amounts " + sum + " do not sum to total " + totalAmount);
        // Amounts already set — nothing more to compute
    }
}


// ── PERCENTAGE SPLIT ─────────────────────────────────────────────────────────
// split.amount = percentage BEFORE calculate(). Overwritten with rupees AFTER.
// ₹400: Alice=40% → ₹160, Bob=30% → ₹120, Charlie=20% → ₹80, Diana=10% → ₹40
//
class PercentageSplitStrategy implements SplitStrategy {
    private static final double EPSILON = 0.01;

    @Override
    public void calculate(double totalAmount, List<Split> splits) {
        double totalPct = 0;
        for (Split split : splits) totalPct += split.getAmount(); // read as %
        if (Math.abs(totalPct - 100.0) > EPSILON)
            throw new IllegalArgumentException(
                "Percentages must sum to 100. Got: " + totalPct);
        for (Split split : splits) {
            double pct   = split.getAmount();
            split.setAmount((pct / 100.0) * totalAmount); // overwrite with ₹
        }
    }
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 6: SPLIT STRATEGY FACTORY — FACTORY PATTERN
// ══════════════════════════════════════════════════════════════════════════════
//
// FACTORY PATTERN:
// ────────────────
// Centralizes creation of SplitStrategy objects.
// Callers NEVER do 'new EqualSplitStrategy()' — they ask the Factory.
//
// WITHOUT FACTORY (problem):
//   PaymentController: new EqualSplitStrategy()  ← scattered creation
//   GroupService:      new EqualSplitStrategy()  ← duplicate code
//   TestHelper:        new EqualSplitStrategy()  ← if constructor changes → fix all 3
//
// WITH FACTORY (solution):
//   SplitStrategyFactory.getStrategy(SplitType.EQUAL) ← one place to change
//
// CACHING (FLYWEIGHT INSIDE FACTORY):
// ─────────────────────────────────────
// All strategies are STATELESS — calculate() only uses its parameters,
// no instance fields. This means:
//   - Same instance is safe to share across all threads
//   - We create each strategy ONCE at class load time
//   - Zero GC pressure — no new objects created per expense
//
// INTERVIEW SCRIPT:
// "I use a Factory to centralize strategy creation. The Factory caches one
//  instance of each strategy because they're stateless — calculate() is
//  a pure function. Adding a new split type = one new class + one new case
//  in the Factory. All callers that use SplitType.NEW_TYPE get it for free."
//
class SplitStrategyFactory {

    // Cached instances — created once, reused forever (stateless = thread-safe)
    private static final SplitStrategy EQUAL_STRATEGY      = new EqualSplitStrategy();
    private static final SplitStrategy EXACT_STRATEGY      = new ExactSplitStrategy();
    private static final SplitStrategy PERCENTAGE_STRATEGY = new PercentageSplitStrategy();

    // Static method — no need to instantiate the Factory itself
    public static SplitStrategy getStrategy(SplitType type) {
        switch (type) {
            case EQUAL:      return EQUAL_STRATEGY;
            case EXACT:      return EXACT_STRATEGY;
            case PERCENTAGE: return PERCENTAGE_STRATEGY;
            default:
                // Defensive — unreachable if all enum cases handled
                throw new IllegalArgumentException("Unknown split type: " + type);
        }
    }

    // Private constructor — prevent instantiation of this utility class
    private SplitStrategyFactory() {}
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 7: EXPENSE
// ══════════════════════════════════════════════════════════════════════════════
//
// Represents one shared bill.
// Gets strategy from Factory — never instantiates strategy directly.
// Calls strategy.calculate() in constructor — FAIL FAST before any balance update.
//
// INTERVIEW SCRIPT:
// "Expense is the Strategy context. It asks the Factory for a strategy
//  by SplitType, then calls calculate() immediately in the constructor.
//  If splits are invalid (percentages don't sum to 100), we throw before
//  any state is mutated. This is the fail-fast principle."
//
class Expense {
    private final String      expenseId;
    private final String      description;
    private final double      totalAmount;
    private final User        paidBy;
    private final List<Split> splits;
    private final SplitType   splitType;

    public Expense(String expenseId, String description,
                   double totalAmount, User paidBy,
                   List<Split> splits, SplitType splitType) {
        this.expenseId   = expenseId;
        this.description = description;
        this.totalAmount = totalAmount;
        this.paidBy      = paidBy;
        this.splits      = splits;
        this.splitType   = splitType;

        // Factory gives us the right strategy — Expense doesn't know which
        SplitStrategy strategy = SplitStrategyFactory.getStrategy(splitType);
        strategy.calculate(totalAmount, splits); // fail fast if invalid
    }

    public String      getExpenseId()   { return expenseId; }
    public String      getDescription() { return description; }
    public double      getTotalAmount() { return totalAmount; }
    public User        getPaidBy()      { return paidBy; }
    public List<Split> getSplits()      { return splits; }
    public SplitType   getSplitType()   { return splitType; }
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 8: TRANSACTION — Result object from simplifyDebts()
// ══════════════════════════════════════════════════════════════════════════════
//
// Simple value object: "from pays amount to to"
// Returned as a list from simplifyDebts() — the minimum settlement plan.
//
class Transaction {
    private final User   from;
    private final User   to;
    private final double amount;

    public Transaction(User from, User to, double amount) {
        this.from   = from;
        this.to     = to;
        this.amount = amount;
    }

    @Override
    public String toString() {
        return String.format("  %s  →  %s  :  ₹%.2f",
            from.getName(), to.getName(), amount);
    }
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 9: GROUP
// ══════════════════════════════════════════════════════════════════════════════
//
// THE BALANCE MAP — Most important data structure. Explain this first.
// ──────────────────────────────────────────────────────────────────
//   balances[borrowerID][lenderID] = X  →  borrower owes lender ₹X
//
// Rules:
//   - Only ONE direction stored per pair at any time (net-out on every update)
//   - Zero / missing = no debt
//   - O(1) lookup: does Alice owe Bob? → balances["alice"]["bob"]
//   - O(1) update: settle or add expense
//
// NET-OUT LOGIC (in addExpense):
// ───────────────────────────────
//   Before adding new debt, check if OPPOSITE debt exists:
//
//   Case A — reverseDebt >= newAmount:
//     Lender already owes borrower more. Reduce lender's reverse debt.
//     Net: lender still owes borrower, just less.
//
//   Case B — reverseDebt > 0 but < newAmount:
//     Reverse debt partially offsets new debt. Clear reverse, forward the remainder.
//     Flip direction.
//
//   Case C — no reverse debt:
//     Straight addition to borrower's debt.
//
// SIMPLIFY DEBTS — BITMASK DP (TRULY OPTIMAL):
// ──────────────────────────────────────────────
//
//   WHY NOT GREEDY?
//   Greedy (largest debtor pays largest creditor) is NOT always optimal.
//   Counterexample: net = [A=-6, B=-4, C=+3, D=+3, E=+4]
//     Greedy pairs A with E first → breaks the perfect B↔E pair → 4 transactions.
//     Optimal splits into {B,E} and {A,C,D} (two zero-sum groups) → 3 transactions.
//   Greedy never looks for independent zero-sum subgroups — it misses them.
//
//   KEY INSIGHT:
//   A group of K people with net balances summing to 0 always needs K-1 transactions.
//   So: maximize number of zero-sum subgroups → minimize total transactions.
//
//   BITMASK DP:
//   dp[mask] = min transactions to settle all people whose bit is set in mask.
//
//   Recurrence:
//     If sum(mask) != 0 → skip (can't settle this mask independently)
//     If sum(mask) == 0 → dp[mask] = min over all zero-sum submasks S of mask:
//                           dp[S] + dp[mask ^ S]
//     Default: dp[mask] = popcount(mask) - 1  (settle as one big group)
//
//   WHY popcount - 1?
//   A zero-sum group of K people always needs exactly K-1 transactions.
//   Each transaction eliminates exactly one person. K people → K-1 steps.
//
//   TIME:  O(3^N) — submask enumeration across all masks
//   SPACE: O(2^N) — dp array
//   N is group size, never total users. N≤15 → 3^15 = 14M ops. Fast.
//
class Group {
    private final String            groupId;
    private final String            groupName;
    private final List<User>        members;
    private final List<Expense>     expenses;
    private final Map<String, User> userRegistry; // userId → User (for display + algorithm)

    // THE BALANCE MAP
    // balances.get(borrowerId).get(lenderId) = amount borrower owes lender
    private final Map<String, Map<String, Double>> balances;

    public Group(String groupId, String groupName) {
        this.groupId      = groupId;
        this.groupName    = groupName;
        this.members      = new ArrayList<>();
        this.expenses     = new ArrayList<>();
        this.balances     = new HashMap<>();
        this.userRegistry = new HashMap<>();
    }

    public void addMember(User user) {
        members.add(user);
        userRegistry.put(user.getUserId(), user);
        balances.putIfAbsent(user.getUserId(), new HashMap<>());
    }

    // ── METHOD: addExpense ──────────────────────────────────────────────────
    //
    // For each split: borrower = split.user, lender = paidBy.
    // Update balances with NET-OUT logic.
    //
    public void addExpense(Expense expense) {
        expenses.add(expense);
        String lender = expense.getPaidBy().getUserId();

        for (Split split : expense.getSplits()) {
            String borrower = split.getUser().getUserId();
            double amount   = split.getAmount();

            if (borrower.equals(lender)) continue; // payer skipped

            balances.putIfAbsent(borrower, new HashMap<>());
            balances.putIfAbsent(lender,   new HashMap<>());

            // Check for opposite debt (lender already owes borrower?)
            double reverseDebt = balances.get(lender).getOrDefault(borrower, 0.0);

            if (reverseDebt > 0) {
                if (reverseDebt >= amount) {
                    // Case A: reverse absorbs new debt fully
                    balances.get(lender).put(borrower, reverseDebt - amount);
                } else {
                    // Case B: new debt exceeds reverse — flip direction
                    balances.get(lender).put(borrower, 0.0);
                    double existing = balances.get(borrower).getOrDefault(lender, 0.0);
                    balances.get(borrower).put(lender, existing + (amount - reverseDebt));
                }
            } else {
                // Case C: no reverse debt — straight addition
                double existing = balances.get(borrower).getOrDefault(lender, 0.0);
                balances.get(borrower).put(lender, existing + amount);
            }
        }
    }

    // ── METHOD: settle ──────────────────────────────────────────────────────
    public void settle(User fromUser, User toUser, double amount) {
        String from = fromUser.getUserId();
        String to   = toUser.getUserId();
        double debt = balances.getOrDefault(from, new HashMap<>()).getOrDefault(to, 0.0);

        if (debt <= 0)
            throw new IllegalArgumentException(fromUser.getName() + " does not owe " + toUser.getName());
        if (amount > debt + 0.01)
            throw new IllegalArgumentException("Payment ₹" + amount + " exceeds debt ₹" + debt);

        balances.get(from).put(to, Math.max(0, debt - amount));
    }

    // ── METHOD: simplifyDebts ───────────────────────────────────────────────
    //
    // Returns the TRULY MINIMUM list of transactions using Bitmask DP.
    // Greedy is replaced here because it fails on certain inputs — it breaks
    // independent zero-sum subgroups by pairing across them.
    //
    public List<Transaction> simplifyDebts() {

        // ── STEP 1: Compute net balance per person ───────────────────────────
        // net > 0 → creditor (is owed money)
        // net < 0 → debtor   (owes money)
        // net = 0 → already settled, skip them (reduces N, speeds up DP)
        Map<String, Double> net = new HashMap<>();
        for (User member : members) net.put(member.getUserId(), 0.0);

        for (Map.Entry<String, Map<String, Double>> outer : balances.entrySet()) {
            String borrowerId = outer.getKey();
            for (Map.Entry<String, Double> inner : outer.getValue().entrySet()) {
                double amount = inner.getValue();
                if (amount <= 0.01) continue;
                net.merge(borrowerId,     -amount, Double::sum); // borrower net ↓
                net.merge(inner.getKey(), +amount, Double::sum); // lender net ↑
            }
        }

        // ── STEP 2: Collect only non-zero balances into an indexed list ──────
        // We need integer balances for the DP (multiply by 100 to convert paise).
        // Removing zeros shrinks N — critical since complexity is O(3^N).
        List<User>  people = new ArrayList<>();
        List<Integer> bal  = new ArrayList<>();

        for (User member : members) {
            double netVal = net.getOrDefault(member.getUserId(), 0.0);
            if (Math.abs(netVal) > 0.01) {
                people.add(member);
                bal.add((int) Math.round(netVal * 100)); // convert to paise (integer)
            }
        }

        int N = people.size();
        if (N == 0) return new ArrayList<>(); // everyone already settled

        // ── STEP 3: Precompute subset sums ───────────────────────────────────
        // sum[mask] = sum of balances for all people in mask.
        // Built iteratively: sum[mask] = sum[mask without lowest bit] + bal[lowestBit].
        // This is O(2^N) total — avoids O(N * 2^N) re-summing inside the DP.
        //
        // INTERVIEW SCRIPT:
        // "I precompute subset sums so the DP inner loop is O(1) per submask check.
        //  Without this, checking sum(submask)==0 would be O(N) each time."
        int[] subsetSum = new int[1 << N];
        for (int mask = 1; mask < (1 << N); mask++) {
            int lowestBit = mask & (-mask);                          // isolate lowest set bit
            int idx       = Integer.numberOfTrailingZeros(lowestBit); // its index in bal[]
            subsetSum[mask] = subsetSum[mask ^ lowestBit] + bal.get(idx);
        }

        // ── STEP 4: Bitmask DP ───────────────────────────────────────────────
        // dp[mask] = min transactions to settle everyone in mask.
        //
        // For each mask with subsetSum[mask] == 0 (can be settled as a group):
        //   Default: dp[mask] = popcount(mask) - 1  (one big group needs K-1 txns)
        //   Improve: try all zero-sum submasks S of mask:
        //     dp[mask] = min(dp[mask], dp[S] + dp[mask ^ S])
        //     Breaking mask into two smaller zero-sum groups saves transactions
        //     when those groups can settle independently.
        //
        // Submask enumeration:  for (sub = (mask-1)&mask; sub > 0; sub = (sub-1)&mask)
        // This visits every non-empty proper subset of mask's set bits.
        // Total iterations across all masks = 3^N  (each element has 3 states).
        int[] dp = new int[1 << N];
        Arrays.fill(dp, Integer.MAX_VALUE / 2); // infinity
        dp[0] = 0;                               // base case: 0 people → 0 transactions

        int fullMask = (1 << N) - 1;

        for (int mask = 1; mask <= fullMask; mask++) {
            if (subsetSum[mask] != 0) continue; // can't settle independently → skip

            // Default cost: treat everyone in mask as one zero-sum group
            dp[mask] = Integer.bitCount(mask) - 1;

            // Try every non-empty proper submask — maybe splitting is cheaper
            for (int sub = (mask - 1) & mask; sub > 0; sub = (sub - 1) & mask) {
                if (subsetSum[sub] == 0) { // sub is also a zero-sum group
                    int rest = mask ^ sub;  // complement within mask
                    // Both sub and rest are zero-sum (since sum[mask]=0 and sum[sub]=0)
                    dp[mask] = Math.min(dp[mask], dp[sub] + dp[rest]);
                }
            }
        }

        // ── STEP 5: Reconstruct actual transactions from dp ──────────────────
        // The DP gives us the COUNT. To get the actual WHO-PAYS-WHOM, we
        // reconstruct by replaying the optimal grouping found by the DP.
        //
        // APPROACH: find the optimal partition of fullMask into zero-sum submasks,
        // then within each submask, one creditor collects from all debtors (K-1 txns).
        List<Transaction> transactions = new ArrayList<>();
        reconstructTransactions(fullMask, people, bal, subsetSum, dp, transactions);

        return transactions;
    }

    // ── HELPER: reconstructTransactions ────────────────────────────────────
    //
    // Recursively finds the optimal partition (matching dp[mask]) and
    // generates actual Transaction objects for each zero-sum subgroup.
    //
    // For each zero-sum subgroup of size K:
    //   The largest creditor collects from each debtor one by one (K-1 transactions).
    //   This is optimal within the subgroup — each transaction eliminates one person.
    //
    private void reconstructTransactions(int mask, List<User> people, List<Integer> bal,
                                         int[] subsetSum, int[] dp,
                                         List<Transaction> result) {
        if (mask == 0) return;

        // Find the optimal submask split that achieved dp[mask]
        int bestSub = -1;
        for (int sub = (mask - 1) & mask; sub > 0; sub = (sub - 1) & mask) {
            if (subsetSum[sub] == 0 && dp[sub] + dp[mask ^ sub] == dp[mask]) {
                bestSub = sub;
                break; // first one found is sufficient
            }
        }

        if (bestSub == -1) {
            // mask itself is the atomic zero-sum group — generate transactions
            settleGroup(mask, people, bal, result);
        } else {
            // Split into two subgroups and recurse
            reconstructTransactions(bestSub,       people, bal, subsetSum, dp, result);
            reconstructTransactions(mask ^ bestSub, people, bal, subsetSum, dp, result);
        }
    }

    // ── HELPER: settleGroup ─────────────────────────────────────────────────
    //
    // Given a zero-sum group (mask), generates K-1 transactions.
    // Strategy: pick the largest creditor, have each debtor pay them directly.
    // If creditor is fully paid, move to next creditor.
    //
    private void settleGroup(int mask, List<User> people, List<Integer> bal,
                              List<Transaction> result) {
        // Extract mutable balance copies for people in this group
        List<int[]> group = new ArrayList<>(); // [originalIndex, balance]
        for (int i = 0; i < people.size(); i++) {
            if ((mask & (1 << i)) != 0) {
                group.add(new int[]{i, bal.get(i)});
            }
        }

        // Two pointers: one debtor (negative), one creditor (positive)
        // Since sum=0, every debtor will be matched to a creditor exactly.
        int left = 0, right = group.size() - 1;
        // Sort: debtors (negative) on left, creditors (positive) on right
        group.sort((a, b) -> a[1] - b[1]);

        while (left < right) {
            int debtorBal   = group.get(left)[1];   // negative
            int creditorBal = group.get(right)[1];  // positive

            int settle = Math.min(-debtorBal, creditorBal); // paise

            User from = people.get(group.get(left)[0]);
            User to   = people.get(group.get(right)[0]);
            result.add(new Transaction(from, to, settle / 100.0)); // back to rupees

            group.get(left)[1]  += settle; // debtor's debt reduced
            group.get(right)[1] -= settle; // creditor's credit reduced

            if (group.get(left)[1]  == 0) left++;  // debtor fully settled
            if (group.get(right)[1] == 0) right--; // creditor fully paid
        }
    }

    // ── METHOD: printBalances ────────────────────────────────────────────────
    public void printBalances() {
        System.out.println("── Balances in [" + groupName + "] ──");
        boolean anyDebt = false;
        for (Map.Entry<String, Map<String, Double>> outer : balances.entrySet()) {
            for (Map.Entry<String, Double> inner : outer.getValue().entrySet()) {
                if (inner.getValue() > 0.01) {
                    String borrower = userRegistry.getOrDefault(outer.getKey(),
                                          new User(outer.getKey(), outer.getKey())).getName();
                    String lender   = userRegistry.getOrDefault(inner.getKey(),
                                          new User(inner.getKey(), inner.getKey())).getName();
                    System.out.printf("  %s owes %s: ₹%.2f%n", borrower, lender, inner.getValue());
                    anyDebt = true;
                }
            }
        }
        if (!anyDebt) System.out.println("  All settled up!");
    }

    public String getGroupId()   { return groupId; }
    public String getGroupName() { return groupName; }
    public List<User> getMembers() { return members; }
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 10: SPLITWISE SERVICE — SINGLETON
// ══════════════════════════════════════════════════════════════════════════════
//
// Facade + Singleton. Single entry point for all client interactions.
// Holds global registries for users and groups.
// Validates existence before every operation — prevents silent failures.
//
class SplitwiseService {

    private static volatile SplitwiseService instance;
    private final Map<String, User>  users;
    private final Map<String, Group> groups;

    private SplitwiseService() {
        this.users  = new HashMap<>();
        this.groups = new HashMap<>();
    }

    public static SplitwiseService getInstance() {
        if (instance == null) {
            synchronized (SplitwiseService.class) {
                if (instance == null) instance = new SplitwiseService();
            }
        }
        return instance;
    }

    public User addUser(String userId, String name) {
        User user = new User(userId, name);
        users.put(userId, user);
        return user;
    }

    public Group createGroup(String groupId, String groupName) {
        Group group = new Group(groupId, groupName);
        groups.put(groupId, group);
        return group;
    }

    public void addMemberToGroup(String groupId, String userId) {
        getGroup(groupId).addMember(getUser(userId));
    }

    public void addExpense(String groupId, Expense expense) {
        getGroup(groupId).addExpense(expense);
    }

    public void settle(String groupId, String fromId, String toId, double amount) {
        getGroup(groupId).settle(getUser(fromId), getUser(toId), amount);
    }

    public void showBalances(String groupId) {
        getGroup(groupId).printBalances();
    }

    public List<Transaction> simplifyDebts(String groupId) {
        return getGroup(groupId).simplifyDebts();
    }

    private Group getGroup(String id) {
        Group g = groups.get(id);
        if (g == null) throw new IllegalArgumentException("Group not found: " + id);
        return g;
    }

    private User getUser(String id) {
        User u = users.get(id);
        if (u == null) throw new IllegalArgumentException("User not found: " + id);
        return u;
    }
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 11: SIMULATION
// ══════════════════════════════════════════════════════════════════════════════
//
// Scenario: 4 friends on a Goa trip.
// Covers: all 3 split types → Factory → balances → simplify debts → settle.
//
public class SplitwiseLLD {

    public static void main(String[] args) {

        SplitwiseService service = SplitwiseService.getInstance();

        // ── PHASE 1: SETUP ───────────────────────────────────────────────────
        System.out.println("=== PHASE 1: SETUP ===");
        User alice   = service.addUser("u1", "Alice");
        User bob     = service.addUser("u2", "Bob");
        User charlie = service.addUser("u3", "Charlie");
        User diana   = service.addUser("u4", "Diana");

        service.createGroup("g1", "Goa Trip");
        service.addMemberToGroup("g1", "u1");
        service.addMemberToGroup("g1", "u2");
        service.addMemberToGroup("g1", "u3");
        service.addMemberToGroup("g1", "u4");
        System.out.println("Group 'Goa Trip': Alice, Bob, Charlie, Diana\n");


        // ── PHASE 2: EQUAL SPLIT — Hotel ₹1200 paid by Alice ────────────────
        // Factory → EqualSplitStrategy → 1200/4 = ₹300 each
        // Bob, Charlie, Diana each owe Alice ₹300. Alice skipped (payer).
        System.out.println("=== PHASE 2: EQUAL SPLIT (Hotel ₹1200 by Alice) ===");
        service.addExpense("g1", new Expense("e1", "Hotel", 1200.0, alice,
            Arrays.asList(new Split(alice), new Split(bob),
                          new Split(charlie), new Split(diana)),
            SplitType.EQUAL
        ));
        service.showBalances("g1");


        // ── PHASE 3: EXACT SPLIT — Dinner ₹900 paid by Bob ──────────────────
        // Factory → ExactSplitStrategy → validates 300+250+200+150=900 ✓
        // Alice owes Bob ₹300 — BUT Alice is already owed ₹300 by Bob from hotel.
        // NET-OUT: Alice←→Bob cancel out. Only Charlie and Diana owe Bob.
        System.out.println("\n=== PHASE 3: EXACT SPLIT (Dinner ₹900 by Bob) ===");
        service.addExpense("g1", new Expense("e2", "Dinner", 900.0, bob,
            Arrays.asList(new Split(alice, 300.0), new Split(bob, 250.0),
                          new Split(charlie, 200.0), new Split(diana, 150.0)),
            SplitType.EXACT
        ));
        System.out.println("[Note] Alice owes Bob ₹300 (dinner) but Bob owes Alice ₹300 (hotel)");
        System.out.println("[Note] Net-out: Alice ↔ Bob cancel completely");
        service.showBalances("g1");


        // ── PHASE 4: PERCENTAGE SPLIT — Cab ₹400 paid by Charlie ────────────
        // Factory → PercentageSplitStrategy → validates 40+30+20+10=100% ✓
        // Converts: Alice=₹160, Bob=₹120, Charlie=₹80(payer,skipped), Diana=₹40
        System.out.println("\n=== PHASE 4: PERCENTAGE SPLIT (Cab ₹400 by Charlie) ===");
        service.addExpense("g1", new Expense("e3", "Cab", 400.0, charlie,
            Arrays.asList(new Split(alice, 40.0),   // 40% = ₹160
                          new Split(bob, 30.0),      // 30% = ₹120
                          new Split(charlie, 20.0),  // 20% = ₹80 (payer — skipped)
                          new Split(diana, 10.0)),   // 10% = ₹40
            SplitType.PERCENTAGE
        ));
        service.showBalances("g1");


        // ── PHASE 5: SIMPLIFY DEBTS ──────────────────────────────────────────
        //
        // Current raw balances may involve multiple back-and-forth debts.
        // simplifyDebts() computes each person's NET position and finds
        // the MINIMUM transactions to settle everything.
        //
        // Example of what it solves:
        //   Alice owes Charlie ₹160, Bob owes Charlie ₹120
        //   Charlie owes Bob ₹300 (from hotel)
        //   → Bob and Charlie can net out: Charlie pays Bob the difference
        //   → Final: just a few direct payments instead of many
        //
        System.out.println("\n=== PHASE 5: SIMPLIFY DEBTS ===");
        System.out.println("Raw balances (many transactions needed):");
        service.showBalances("g1");
        System.out.println("\nSimplified — MINIMUM transactions to settle all:");
        List<Transaction> plan = service.simplifyDebts("g1");
        if (plan.isEmpty()) System.out.println("  Nothing to settle!");
        else plan.forEach(System.out::println);


        // ── PHASE 6: MANUAL SETTLE ───────────────────────────────────────────
        System.out.println("\n=== PHASE 6: MANUAL SETTLE ===");
        System.out.println("Diana settles ₹300 with Alice...");
        service.settle("g1", "u4", "u1", 300.0);
        service.showBalances("g1");
    }
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 12: INTERVIEW FOLLOW-UP Q&A
// ══════════════════════════════════════════════════════════════════════════════
//
// Q1: "Why use a Factory here, not just new EqualSplitStrategy() directly?"
//     A: Three reasons:
//        1. Single place to change — if a strategy needs constructor args tomorrow,
//           update the Factory only. All callers are unaffected.
//        2. Caching — strategies are stateless, so Factory returns the same cached
//           instance every time. Zero object creation overhead per expense.
//        3. Open/Closed — add ShareSplitStrategy → add one enum + one Factory case.
//           No existing caller code changes.
//
// Q2: "Is simplifyDebts always optimal now?"
//     A: YES — Bitmask DP is provably optimal. Greedy was NOT.
//        Greedy counterexample: net=[A=-6,B=-4,C=+3,D=+3,E=+4].
//          Greedy=4 transactions (misses the {B,E} perfect pair).
//          DP=3 transactions (finds {B,E} and {A,C,D} as independent zero-sum groups).
//        DP complexity: O(3^N) time, O(2^N) space.
//        N is group size (never total users). N≤15 → 14M ops — runs in milliseconds.
//
// Q3: "How to handle floating point precision in money?"
//     A: Use BigDecimal everywhere. double is imprecise:
//        0.1 + 0.2 = 0.30000000000000004 in IEEE 754.
//        BigDecimal provides exact decimal arithmetic. Use HALF_UP rounding.
//
// Q4: "How to make addExpense thread-safe?"
//     A: Add synchronized to Group.addExpense() and Group.settle().
//        For higher concurrency, ConcurrentHashMap + compute() for atomic updates.
//        simplifyDebts() reads only — safe if called when no concurrent writes.
//
// Q5: "How would you add notifications?"
//     A: Observer Pattern. Users register as observers.
//        addExpense() notifies affected users after updating balances.
//        Notification channel (SMS, push, email) is its own Strategy interface.
//
// Q6: "How to support multiple currencies?"
//     A: Add currency to Expense. Inject a CurrencyConverter that converts to
//        a base currency (INR) at expense creation. Store all balances in base.
//        Convert back to user's preferred currency on display.
//
// ══════════════════════════════════════════════════════════════════════════════
