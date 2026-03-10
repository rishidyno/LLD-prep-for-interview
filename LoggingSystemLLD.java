import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║               LOGGING SYSTEM — LOW LEVEL DESIGN (INTERVIEW READY)          ║
// ║                          Target: SDE-1 / SDE-2 Roles                       ║
// ║                          Time to cover: ~40-45 mins                        ║
// ║                                                                              ║
// ║  PROBLEM STATEMENT:                                                         ║
// ║  ──────────────────                                                         ║
// ║  Design a logging system that:                                              ║
// ║    1. Supports multiple log levels (DEBUG, INFO, WARN, ERROR)               ║
// ║    2. Can write logs to multiple destinations (Console, File)               ║
// ║    3. Has a single shared Logger instance across the application            ║
// ║    4. Allows switching destinations without changing logger code            ║
// ║    5. Is thread-safe for concurrent logging                                 ║
// ║                                                                              ║
// ║  DESIGN PATTERNS (Name these upfront in interview):                         ║
// ║  ──────────────────────────────────────────────────                         ║
// ║  1. SINGLETON  → Logger (one shared instance across the app)                ║
// ║  2. STRATEGY   → Appender (swap log destinations without changing Logger)   ║
// ║  3. BUILDER    → LogMessage (clean construction of a complex object)        ║
// ║                                                                              ║
// ║  WHAT TO SAY FIRST IN THE INTERVIEW:                                        ║
// ║  ─────────────────────────────────────                                      ║
// ║  "I'll start by identifying the entities: we have a Logger, a LogMessage,  ║
// ║   a LogLevel, and something that writes the log — I'll call it Appender.   ║
// ║   The key design decision is to separate WHAT gets logged (LogMessage)      ║
// ║   from WHERE it gets logged (Appender). This gives us flexibility to add   ║
// ║   new destinations — like a database or Kafka — without touching Logger."  ║
// ╚══════════════════════════════════════════════════════════════════════════════╝


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 1: LOG LEVEL ENUM
// ══════════════════════════════════════════════════════════════════════════════
//
// WHY AN ENUM?
// ────────────
// Log levels are a fixed, ordered set of constants. Enum is perfect because:
//   1. Type-safe — compiler rejects invalid values like logger.log("VERBOSE", ...)
//   2. Ordinal value gives us natural ordering for level filtering
//   3. More readable than int constants (no magic numbers like level = 3)
//
// HOW FILTERING WORKS:
// ─────────────────────
// Every enum value gets an implicit ordinal: DEBUG=0, INFO=1, WARN=2, ERROR=3
// If Logger's configured level is INFO (ordinal=1), then:
//   DEBUG (ordinal=0) → 0 < 1 → FILTERED OUT (not logged)
//   INFO  (ordinal=1) → 1 >= 1 → LOGGED
//   WARN  (ordinal=2) → 2 >= 1 → LOGGED
//   ERROR (ordinal=3) → 3 >= 1 → LOGGED
//
// INTERVIEW SCRIPT:
// "I'm using an enum for log levels because it gives me type safety and
//  built-in ordering via ordinal values. I use ordinal comparison to filter
//  out low-priority logs — if the configured level is WARN, anything below
//  WARN is simply ignored."
//
enum LogLevel {
    DEBUG,  // ordinal = 0 — fine-grained detail, dev environments only
    INFO,   // ordinal = 1 — general operational messages
    WARN,   // ordinal = 2 — something unexpected but recoverable
    ERROR   // ordinal = 3 — failure, needs attention
    // INTERVIEW TIP: In real systems you'd also have TRACE (below DEBUG)
    // and FATAL (above ERROR). Omitted here for interview brevity.
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 2: LOG MESSAGE — BUILDER PATTERN
// ══════════════════════════════════════════════════════════════════════════════
//
// WHY A SEPARATE LogMessage CLASS?
// ─────────────────────────────────
// Instead of passing 4-5 parameters to every log call:
//   logger.log(level, message, className, timestamp, threadName)  ← messy
//
// We encapsulate everything in one object:
//   LogMessage msg = new LogMessage.Builder(...)...build();       ← clean
//
// WHY BUILDER PATTERN?
// ─────────────────────
// LogMessage has multiple fields. Some are optional (e.g., you may not always
// want to log the thread name). Builder pattern lets you:
//   1. Set only what you need (optional fields have defaults)
//   2. Make the object immutable after construction (all fields final)
//   3. Readable — each setter is named, no positional confusion
//
// IMMUTABILITY BENEFIT:
// Once built, a LogMessage never changes. This is thread-safe by design —
// multiple appender threads can read the same LogMessage concurrently
// with zero synchronization needed.
//
// INTERVIEW SCRIPT:
// "LogMessage is immutable and built using the Builder pattern. This is
//  important for thread safety — once a LogMessage is created, no thread
//  can modify it. Multiple appenders can read it simultaneously without
//  any locking."
//
class LogMessage {

    // All fields are final — immutability guarantee
    private final LogLevel level;
    private final String message;
    private final String className;    // which class logged this
    private final String threadName;   // which thread logged this
    private final String timestamp;    // when it was logged

    // Private constructor — only the Builder can create a LogMessage
    private LogMessage(Builder builder) {
        this.level      = builder.level;
        this.message    = builder.message;
        this.className  = builder.className;
        this.threadName = builder.threadName;
        this.timestamp  = builder.timestamp;
    }

    // ── GETTERS ─────────────────────────────────────────────────────────────
    // Read-only access. No setters — enforces immutability.
    public LogLevel getLevel()      { return level; }
    public String getMessage()      { return message; }
    public String getClassName()    { return className; }
    public String getThreadName()   { return threadName; }
    public String getTimestamp()    { return timestamp; }

    // ── FORMAT FOR OUTPUT ───────────────────────────────────────────────────
    // Produces a single-line log string:
    // [2024-01-15 10:30:00] [ERROR] [main] [PaymentService] Payment failed
    //
    // INTERVIEW FOLLOW-UP: "What if different appenders need different formats?"
    // ANSWER: Extract a Formatter interface with a format(LogMessage) method.
    //   JSONFormatter, PlainTextFormatter etc. implement it. Each Appender
    //   holds a reference to its Formatter. This is Strategy Pattern again.
    @Override
    public String toString() {
        return String.format("[%s] [%-5s] [%s] [%s] %s",
            timestamp, level, threadName, className, message);
    }

    // ── STATIC INNER CLASS: Builder ─────────────────────────────────────────
    // Separates the construction logic from the LogMessage itself.
    //
    // Usage:
    //   LogMessage msg = new LogMessage.Builder(LogLevel.ERROR, "Payment failed")
    //                         .className("PaymentService")
    //                         .build();
    //
    static class Builder {
        // Required fields — passed in constructor
        private final LogLevel level;
        private final String message;

        // Optional fields — have sensible defaults
        private String className  = "Unknown";
        private String threadName = Thread.currentThread().getName();
        private String timestamp  = LocalDateTime.now()
                                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // Constructor takes only required fields
        public Builder(LogLevel level, String message) {
            this.level   = level;
            this.message = message;
        }

        // Fluent setters — each returns 'this' so calls can be chained
        public Builder className(String className) {
            this.className = className;
            return this; // enables method chaining: .className("X").threadName("Y")
        }

        public Builder threadName(String threadName) {
            this.threadName = threadName;
            return this;
        }

        // Terminal method — creates and returns the immutable LogMessage
        public LogMessage build() {
            return new LogMessage(this);
        }
    }
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 3: APPENDER INTERFACE — STRATEGY PATTERN
// ══════════════════════════════════════════════════════════════════════════════
//
// WHY AN INTERFACE?
// ──────────────────
// The Logger should not know OR care where logs are written.
// By programming to an interface, we can swap destinations freely:
//   logger.addAppender(new ConsoleAppender())     ← writes to terminal
//   logger.addAppender(new FileAppender("app.log")) ← writes to file
//   logger.addAppender(new DatabaseAppender(...))  ← writes to DB (future)
//   logger.addAppender(new KafkaAppender(...))     ← writes to Kafka (future)
//
// Logger never changes. New destinations = new class, zero existing code change.
// This is the Open/Closed Principle: open for extension, closed for modification.
//
// STRATEGY PATTERN:
// ─────────────────
// The "strategy" here is HOW to write a log message.
// Logger holds a List<Appender> and calls append() on each one.
// The concrete strategy (where to write) is injected, not hardcoded.
//
// INTERVIEW SCRIPT:
// "Appender is the Strategy here. Logger doesn't know if it's writing to
//  console or file — it just calls append() on whatever Appender was given
//  to it. To add a new destination, I create a new class that implements
//  Appender. Logger doesn't change at all. This is Strategy Pattern +
//  Open/Closed Principle working together."
//
interface Appender {

    // ── METHOD: append(message) ─────────────────────────────────────────────
    // Each concrete Appender decides how to handle this log message.
    // ConsoleAppender → System.out.println
    // FileAppender    → write to disk
    // DatabaseAppender → INSERT into logs table
    void append(LogMessage message);
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 4: CONSOLE APPENDER — Concrete Strategy #1
// ══════════════════════════════════════════════════════════════════════════════
//
// Writes log messages to standard output (the terminal).
//
// THREAD SAFETY:
// ──────────────
// System.out.println() is synchronized internally in Java.
// So ConsoleAppender is thread-safe without any extra work.
//
// INTERVIEW FOLLOW-UP: "What if you want color-coded output by level?"
// ANSWER: Add ANSI escape codes inside append():
//   ERROR → "\u001B[31m" (red) + message + "\u001B[0m" (reset)
//   WARN  → "\u001B[33m" (yellow)
//   INFO  → "\u001B[32m" (green)
//
class ConsoleAppender implements Appender {

    @Override
    public void append(LogMessage message) {
        // System.out.println is thread-safe (internally synchronized in Java)
        System.out.println(message.toString());
    }
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 5: FILE APPENDER — Concrete Strategy #2
// ══════════════════════════════════════════════════════════════════════════════
//
// Writes log messages to a file on disk.
//
// THREAD SAFETY CHALLENGE:
// ─────────────────────────
// Unlike ConsoleAppender, file writing is NOT thread-safe by default.
// If Thread A and Thread B both call append() simultaneously:
//   - Both open the FileWriter
//   - Both write their log line
//   - The output gets interleaved or corrupted
//
// SOLUTION: synchronized on the append() method.
// Only one thread can write to the file at a time.
//
// TRADE-OFF:
// synchronized means sequential writes — no parallelism for file logging.
// For high throughput, you'd use an async queue (e.g., LinkedBlockingQueue)
// where app threads enqueue messages and a single writer thread dequeues and writes.
// But for an interview, synchronized is the right answer to demonstrate you
// understand the problem.
//
// INTERVIEW SCRIPT:
// "FileAppender uses synchronized on append() because file writes are not
//  thread-safe. Only one thread writes at a time. If throughput becomes a
//  concern at scale, I'd switch to an async approach with a blocking queue —
//  app threads just enqueue messages and a dedicated writer thread handles disk I/O."
//
class FileAppender implements Appender {

    private final String filePath;

    public FileAppender(String filePath) {
        this.filePath = filePath;
    }

    // ── synchronized: ensures only one thread writes at a time ──────────────
    @Override
    public synchronized void append(LogMessage message) {
        // true = append mode (don't overwrite existing file content)
        try (FileWriter writer = new FileWriter(filePath, true)) {
            writer.write(message.toString() + "\n");
        } catch (IOException e) {
            // INTERVIEW NOTE: Never let logger exceptions crash the application.
            // Log the failure to stderr (last resort) and swallow it.
            System.err.println("FileAppender failed to write: " + e.getMessage());
        }
    }
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 6: LOGGER — SINGLETON PATTERN (The Core Class)
// ══════════════════════════════════════════════════════════════════════════════
//
// WHY SINGLETON?
// ──────────────
// Every part of an application (PaymentService, OrderService, UserService)
// needs to log. They should all write to the SAME logger with the SAME config —
// same log level, same appenders, same output files.
//
// If Logger were not a Singleton:
//   new Logger() in PaymentService → its own config, its own file
//   new Logger() in OrderService   → different config, different file
//   → logs scattered, no unified view of what's happening
//
// SINGLETON ensures one shared instance, one config, one unified log stream.
//
// THREAD SAFETY:
// ──────────────
// The singleton instance creation is synchronized (getInstance).
// The log() method is synchronized to prevent:
//   1. Two threads calling log() simultaneously and interleaving their
//      LogMessage creation mid-way
//   2. addAppender() being called while log() is iterating appenders
//      → ConcurrentModificationException
//
// INTERVIEW FOLLOW-UP: "Can you improve the Singleton?"
// ANSWER: Use Double-Checked Locking with volatile:
//   private static volatile Logger instance;
//   public static Logger getInstance() {
//       if (instance == null) {              // fast path — no lock after init
//           synchronized (Logger.class) {
//               if (instance == null)        // slow path — lock only on first init
//                   instance = new Logger();
//           }
//       }
//       return instance;
//   }
//   The volatile keyword prevents CPU reordering — without it, another thread
//   could see a non-null but partially constructed Logger object.
//
class Logger {

    // ── FIELD: instance ─────────────────────────────────────────────────────
    // null until first call to getInstance() — lazy initialization
    // volatile ensures visibility across threads (for DCL improvement)
    private static volatile Logger instance;

    // ── FIELD: currentLevel ─────────────────────────────────────────────────
    // Only messages at this level or higher get logged.
    // Default: DEBUG — log everything (suitable for development)
    // Production: typically INFO or WARN
    private LogLevel currentLevel;

    // ── FIELD: appenders ────────────────────────────────────────────────────
    // List of destinations. Logger writes to ALL of them on each log call.
    // E.g., [ConsoleAppender, FileAppender] → logs to both console AND file.
    //
    // WHY List AND NOT a single Appender?
    // In real systems you want logs in multiple places simultaneously:
    //   - Console for local development visibility
    //   - File for persistent storage and log rotation
    //   - Kafka/Elasticsearch for centralized aggregation (future)
    private final List<Appender> appenders;

    // ── PRIVATE CONSTRUCTOR ──────────────────────────────────────────────────
    // Private = no one can call new Logger() from outside.
    // This is what enforces the Singleton — one instance, created once.
    private Logger() {
        this.currentLevel = LogLevel.DEBUG; // default level
        this.appenders    = new ArrayList<>();
    }

    // ── METHOD: getInstance() ────────────────────────────────────────────────
    // Double-Checked Locking — thread-safe and performant.
    //
    // First check (no lock):
    //   After initialization, instance is non-null. Most calls skip the
    //   synchronized block entirely → no overhead on the hot path.
    //
    // Second check (inside lock):
    //   Two threads could both see instance == null simultaneously and both
    //   enter the synchronized block. The second check ensures only one creates it.
    //
    // INTERVIEW SCRIPT:
    // "I'm using Double-Checked Locking here. The outer check avoids acquiring
    //  the monitor lock on every call after initialization — that's the performance
    //  optimization. The inner check handles the race condition where two threads
    //  simultaneously see instance as null. volatile on the field prevents CPU
    //  instruction reordering during object construction."
    public static Logger getInstance() {
        if (instance == null) {                    // first check — no lock (fast path)
            synchronized (Logger.class) {
                if (instance == null) {            // second check — inside lock (safe)
                    instance = new Logger();
                }
            }
        }
        return instance;
    }

    // ── METHOD: setLevel(level) ──────────────────────────────────────────────
    // Changes the minimum log level at runtime.
    // E.g., setLevel(WARN) → only WARN and ERROR messages are logged.
    //
    // synchronized: prevents race with log() reading currentLevel simultaneously
    public synchronized void setLevel(LogLevel level) {
        this.currentLevel = level;
    }

    // ── METHOD: addAppender(appender) ────────────────────────────────────────
    // Registers a new destination for log output.
    // Can be called at startup: logger.addAppender(new ConsoleAppender())
    //
    // synchronized: prevents ConcurrentModificationException if log() is
    // iterating appenders at the same moment
    public synchronized void addAppender(Appender appender) {
        this.appenders.add(appender);
    }

    // ── METHOD: log(level, message, className) — THE CORE METHOD ─────────────
    //
    // FLOW:
    //   1. Level check: is this message important enough to log?
    //      Uses enum ordinal comparison — if message level < configured level, skip.
    //   2. Build LogMessage using Builder pattern
    //   3. Pass to every registered Appender
    //
    // WHY synchronized HERE?
    // ───────────────────────
    // Two reasons:
    //   a) Iterating appenders list is not thread-safe if addAppender() is called
    //      concurrently — synchronized prevents ConcurrentModificationException
    //   b) Without synchronization, logs from multiple threads could interleave
    //      in unpredictable order
    //
    // INTERVIEW FOLLOW-UP: "Won't synchronized here cause performance issues?"
    // ANSWER: For low-to-medium traffic, it's fine. For high throughput (e.g.,
    //   microservice handling 10k req/s), switch to async:
    //   - App threads enqueue to a LinkedBlockingQueue (non-blocking)
    //   - A dedicated background thread dequeues and calls appenders
    //   - App threads never block on I/O
    //   This is what Logback's AsyncAppender does in production.
    //   But for this interview, synchronized is correct and explainable.
    public synchronized void log(LogLevel level, String message, String className) {

        // ── STEP 1: LEVEL FILTER ─────────────────────────────────────────────
        // enum.ordinal() gives 0-based position in declaration order.
        // If incoming level's ordinal < configured level's ordinal → skip.
        //
        // Example: currentLevel = WARN (ordinal=2)
        //   log(DEBUG, ...) → DEBUG.ordinal()=0 < 2 → return early, nothing logged
        //   log(ERROR, ...) → ERROR.ordinal()=3 >= 2 → proceed
        if (level.ordinal() < currentLevel.ordinal()) {
            return; // message is below threshold — discard silently
        }

        // ── STEP 2: BUILD LOG MESSAGE ────────────────────────────────────────
        // Builder pattern constructs the immutable LogMessage.
        // threadName is auto-captured from the current thread.
        // timestamp is auto-stamped at construction time.
        LogMessage logMessage = new LogMessage.Builder(level, message)
                .className(className)
                .threadName(Thread.currentThread().getName())
                .build();

        // ── STEP 3: DISPATCH TO ALL APPENDERS ────────────────────────────────
        // Logger doesn't know or care about appender implementations.
        // It just calls append() on each one — Strategy Pattern in action.
        for (Appender appender : appenders) {
            appender.append(logMessage);
        }
    }

    // ── CONVENIENCE METHODS ──────────────────────────────────────────────────
    // These make the API clean at the call site:
    //   logger.info("Server started")  ← instead of  logger.log(LogLevel.INFO, "Server started", ...)
    //
    // They just delegate to log() with the level hardcoded.
    // className is auto-detected from the current thread's stack trace.
    //
    // INTERVIEW NOTE: getCallerClassName() uses the call stack — elegant but has
    // slight overhead (stack trace creation). Fine for most applications.
    // Alternative: caller explicitly passes class name as a parameter.
    public void debug(String message) { log(LogLevel.DEBUG, message, getCallerClassName()); }
    public void info(String message)  { log(LogLevel.INFO,  message, getCallerClassName()); }
    public void warn(String message)  { log(LogLevel.WARN,  message, getCallerClassName()); }
    public void error(String message) { log(LogLevel.ERROR, message, getCallerClassName()); }

    // ── HELPER: getCallerClassName() ─────────────────────────────────────────
    // Walks the call stack to find who called debug/info/warn/error.
    //   Index 0 = getStackTrace()
    //   Index 1 = getCallerClassName()
    //   Index 2 = debug() / info() etc.
    //   Index 3 = actual caller (PaymentService, OrderService, etc.) ← we want this
    private String getCallerClassName() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        if (stack.length >= 4) {
            return stack[3].getClassName(); // the class that called info/debug/etc.
        }
        return "Unknown";
    }
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 7: SIMULATION — LoggingMain
// ══════════════════════════════════════════════════════════════════════════════
//
// Demonstrates all features in a realistic sequence:
//   Phase 1 — Setup: configure logger with level + appenders
//   Phase 2 — Basic logging at different levels
//   Phase 3 — Level filtering in action
//   Phase 4 — Concurrent logging from multiple threads
//   Phase 5 — Runtime level change
//
public class LoggingSystemLLD {

    public static void main(String[] args) throws InterruptedException {

        // ── GET THE SINGLETON LOGGER ─────────────────────────────────────────
        // First call creates the instance (double-checked locking).
        // All subsequent calls in the app get the SAME instance.
        Logger logger = Logger.getInstance();

        // ─────────────────────────────────────────────────────────────────────
        // PHASE 1: SETUP — Add Appenders (Strategy injection)
        // ─────────────────────────────────────────────────────────────────────
        //
        // We inject TWO strategies:
        //   ConsoleAppender → writes to terminal
        //   FileAppender    → writes to app.log
        //
        // Logger doesn't know how each appender works — it just calls append().
        // This is the Strategy Pattern: behavior injected, not hardcoded.
        System.out.println("=== PHASE 1: SETUP ===");
        logger.addAppender(new ConsoleAppender());
        logger.addAppender(new FileAppender("app.log"));
        logger.setLevel(LogLevel.DEBUG); // log everything initially
        System.out.println("[Config] Level=DEBUG, Appenders=[Console, File]\n");


        // ─────────────────────────────────────────────────────────────────────
        // PHASE 2: BASIC LOGGING — All levels
        // ─────────────────────────────────────────────────────────────────────
        //
        // All four messages logged because level=DEBUG (lowest threshold).
        // Each convenience method internally calls log() which:
        //   1. Checks level filter
        //   2. Builds LogMessage via Builder
        //   3. Dispatches to ConsoleAppender AND FileAppender
        System.out.println("=== PHASE 2: BASIC LOGGING ===");
        logger.debug("Initializing payment service");
        logger.info("Server started on port 8080");
        logger.warn("Memory usage above 80%");
        logger.error("Database connection failed");
        System.out.println();


        // ─────────────────────────────────────────────────────────────────────
        // PHASE 3: LEVEL FILTERING
        // ─────────────────────────────────────────────────────────────────────
        //
        // setLevel(WARN) → only WARN and ERROR pass the ordinal check.
        // DEBUG (ordinal=0) and INFO (ordinal=1) are silently discarded.
        //
        // INTERVIEW SCRIPT:
        // "This is the level filter in action. I raise the threshold to WARN.
        //  When debug() and info() are called, their ordinals are below WARN's
        //  ordinal, so log() returns immediately without creating a LogMessage
        //  or touching any appender. Zero overhead for filtered-out logs."
        System.out.println("=== PHASE 3: LEVEL FILTER (set to WARN) ===");
        logger.setLevel(LogLevel.WARN);
        logger.debug("This will NOT appear — DEBUG < WARN");   // filtered out
        logger.info("This will NOT appear — INFO < WARN");     // filtered out
        logger.warn("This WILL appear — WARN >= WARN");        // logged
        logger.error("This WILL appear — ERROR > WARN");       // logged
        System.out.println();


        // ─────────────────────────────────────────────────────────────────────
        // PHASE 4: CONCURRENT LOGGING
        // ─────────────────────────────────────────────────────────────────────
        //
        // Three threads log simultaneously to the same Logger instance.
        // synchronized on log() ensures:
        //   1. No two threads build or dispatch at the same time
        //   2. Each log line is complete before the next starts
        //   3. No ConcurrentModificationException on appenders list
        //
        // INTERVIEW SCRIPT:
        // "Multiple threads share the same Logger singleton. The synchronized
        //  keyword on log() serializes access — threads take turns. Each log
        //  line is complete and uninterrupted. Without synchronized, lines from
        //  different threads could interleave mid-write."
        System.out.println("=== PHASE 4: CONCURRENT LOGGING ===");
        logger.setLevel(LogLevel.DEBUG); // reset to see all messages

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 3; i++) {
                logger.info("PaymentService: processing transaction #" + i);
            }
        }, "PaymentThread");

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 3; i++) {
                logger.warn("OrderService: order queue length = " + (i * 10));
            }
        }, "OrderThread");

        Thread t3 = new Thread(() -> {
            for (int i = 0; i < 3; i++) {
                logger.error("UserService: auth failure for user_" + i);
            }
        }, "UserThread");

        t1.start(); t2.start(); t3.start();
        t1.join();  t2.join();  t3.join();  // wait for all threads to finish
        System.out.println();


        // ─────────────────────────────────────────────────────────────────────
        // PHASE 5: RUNTIME LEVEL CHANGE
        // ─────────────────────────────────────────────────────────────────────
        //
        // This demonstrates that Logger config is live — no restart needed.
        // In production systems, this is triggered via:
        //   - HTTP endpoint: POST /admin/logger/level?value=ERROR
        //   - Config server push (etcd, Consul, AWS Parameter Store)
        //   - JMX (Java Management Extensions)
        //
        // INTERVIEW FOLLOW-UP: "How would you make level changes thread-safe?"
        // ANSWER: setLevel() is already synchronized, so any thread calling
        //   log() sees the new level immediately after setLevel() returns.
        System.out.println("=== PHASE 5: RUNTIME LEVEL CHANGE ===");
        logger.setLevel(LogLevel.ERROR); // only critical messages now
        logger.debug("Ignored — production noise suppressed");
        logger.info("Ignored — production noise suppressed");
        logger.warn("Ignored — production noise suppressed");
        logger.error("CRITICAL: payment gateway unreachable"); // only this logs
        System.out.println("\n[Done] Check app.log for the file output.");
    }
}


// ══════════════════════════════════════════════════════════════════════════════
// SECTION 8: COMMON INTERVIEW FOLLOW-UP Q&A
// ══════════════════════════════════════════════════════════════════════════════
//
// Q1: "How would you make this async for high throughput?"
//     A: Replace synchronized log() with a non-blocking enqueue:
//        private final BlockingQueue<LogMessage> queue = new LinkedBlockingQueue<>(10000);
//        App threads call queue.offer(msg)  → returns immediately (non-blocking)
//        A background thread calls queue.take() → dequeues and calls appenders
//        App threads NEVER wait for I/O. Throughput scales with queue size.
//        Trade-off: logs may be slightly delayed; risk of losing queued logs on crash.
//
// Q2: "What if you want different log formats for Console vs File?"
//     A: Introduce a Formatter interface:
//        interface Formatter { String format(LogMessage msg); }
//        class JSONFormatter implements Formatter { ... }
//        class PlainTextFormatter implements Formatter { ... }
//        Each Appender holds a Formatter reference and calls formatter.format(msg).
//        This is Strategy Pattern applied again — this time to formatting.
//
// Q3: "How would you add log rotation (new file every day / 100MB)?"
//     A: In FileAppender, check file size or date before each write.
//        If size > threshold or date changed → close current file, open new one.
//        File naming: app-2024-01-15.log, app-2024-01-16.log etc.
//        In production, Logback's RollingFileAppender does exactly this.
//
// Q4: "How would you support named loggers (like Log4j)?"
//     A: Change Singleton to a registry:
//        Map<String, Logger> loggerRegistry = new HashMap<>();
//        Logger.getLogger("PaymentService") → returns (or creates) named logger.
//        Each named logger can have its own level and appenders.
//        This is the Factory + Registry pattern.
//
// Q5: "How would you filter logs by class name (e.g., only log from PaymentService)?"
//     A: Add a Filter interface:
//        interface Filter { boolean accept(LogMessage msg); }
//        class ClassNameFilter implements Filter {
//            boolean accept(msg) { return msg.getClassName().contains("Payment"); }
//        }
//        Each Appender holds a list of Filters. If any filter rejects the message,
//        the appender skips it. This is Chain of Responsibility pattern.
//
// Q6: "What's the time complexity of log()?"
//     A: O(A) where A = number of appenders (typically 2-3, so effectively O(1)).
//        The level check is O(1) — just an integer comparison.
//        LogMessage building is O(1).
//        Writing to console is O(M) where M = message length.
//
// Q7: "How does enum ordinal comparison work?"
//     A: Java assigns ordinal values automatically based on declaration order.
//        DEBUG=0, INFO=1, WARN=2, ERROR=3.
//        level.ordinal() < currentLevel.ordinal() means "this message is less
//        important than our threshold" → discard it. Simple integer comparison.
//
// ══════════════════════════════════════════════════════════════════════════════
