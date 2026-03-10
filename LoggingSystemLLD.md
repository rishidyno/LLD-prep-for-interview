# Logging System — Low Level Design (LLD)
### Interview Preparation Guide | SDE-1 / SDE-2 | ~45 Minutes

---

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [What to Say First (Opening Statement)](#what-to-say-first)
3. [Class Diagram](#class-diagram)
4. [Design Patterns Used](#design-patterns-used)
5. [Class Breakdown](#class-breakdown)
6. [Concurrency Strategy](#concurrency-strategy)
7. [Complexity Analysis](#complexity-analysis)
8. [Running the Code](#running-the-code)
9. [Interview Script — Phase by Phase](#interview-script)
10. [Common Follow-up Questions](#common-follow-up-questions)
11. [What to Mention as Improvements (End of Interview)](#improvements-for-discussion)

---

## Problem Statement

> **Design a logging system that supports:**
> - Multiple log levels (DEBUG, INFO, WARN, ERROR)
> - Writing logs to multiple destinations simultaneously (Console, File)
> - A single shared Logger instance across the entire application
> - Swapping/adding destinations without changing Logger code
> - Thread-safe logging from multiple threads concurrently

---

## What to Say First

> *"Before I write any code, let me identify the key entities.
> We have a **Logger** — the central class the whole app uses.
> A **LogMessage** — the object that holds all info about one log event.
> A **LogLevel** — to represent severity (DEBUG < INFO < WARN < ERROR).
> And something that writes the log somewhere — I'll call it an **Appender**.
>
> The most important design decision is: Logger should NOT know where logs
> go. Whether it's console, file, or a database — that's the Appender's job.
> I'll use the **Strategy Pattern** for Appender so we can swap or add
> destinations without touching Logger at all.
>
> Logger itself should be a **Singleton** — every service in the app must
> share the same logger, same config, same output files.
>
> For the LogMessage, I'll use a **Builder** — it has several fields and
> some are optional, so Builder keeps construction clean and the final
> object immutable."*

This opening alone signals strong design thinking. Say it before writing a single line.

---

## Class Diagram

```
┌─────────────┐         ┌──────────────────────────────┐
│  «enum»     │         │  LogMessage                  │
│  LogLevel   │         │  ─────────────────────────   │
│  ─────────  │         │  - level: LogLevel           │
│  DEBUG = 0  │◄────────│  - message: String           │
│  INFO  = 1  │         │  - className: String         │
│  WARN  = 2  │         │  - threadName: String        │
│  ERROR = 3  │         │  - timestamp: String         │
└─────────────┘         │  ─────────────────────────   │
                        │  + toString(): String        │
                        │  ─────────────────────────   │
                        │  «inner» Builder             │
                        │  + className(): Builder      │
                        │  + build(): LogMessage       │
                        └──────────────────────────────┘
                                      ▲
                                      │ creates
┌────────────────────────┐            │
│  Logger «Singleton»    │────────────┘
│  ──────────────────    │
│  - instance: Logger    │        ┌───────────────────┐
│  - currentLevel        │        │  «interface»      │
│  - appenders: List     │───────▶│  Appender         │
│  ──────────────────    │  uses  │  ──────────────── │
│  + getInstance()       │        │  + append(msg)    │
│  + setLevel(level)     │        └────────┬──────────┘
│  + addAppender(a)      │                 │ implements
│  + log(level, msg, cls)│         ┌───────┴──────────────┐
│  + info/warn/error/dbg │         │                      │
└────────────────────────┘  ┌──────┴───────┐  ┌──────────┴────┐
                            │ ConsoleApp.  │  │ FileAppender  │
                            │ ──────────── │  │ ─────────────  │
                            │ append(msg)  │  │ - filePath    │
                            │ System.out   │  │ append(msg)   │
                            └──────────────┘  │ FileWriter    │
                                              └───────────────┘
```

---

## Design Patterns Used

### 1. Singleton — `Logger`

One Logger per application. All services share the same config and output.

```java
// Double-Checked Locking (production-grade)
private static volatile Logger instance;

public static Logger getInstance() {
    if (instance == null) {                  // fast path — no lock after init
        synchronized (Logger.class) {
            if (instance == null)            // safe — handles race on first init
                instance = new Logger();
        }
    }
    return instance;
}
```

**Why `volatile`?** Without it, the CPU can reorder instructions — another thread might see `instance != null` but the constructor hasn't fully run yet. `volatile` forces a memory fence.

---

### 2. Strategy — `Appender`

The "strategy" is **how/where to write a log**. Logger holds a `List<Appender>` and calls `append()` on each one. It never knows or cares about the implementation.

```
Logger
  │
  ├──► ConsoleAppender.append(msg) → System.out.println(...)
  ├──► FileAppender.append(msg)    → FileWriter.write(...)
  └──► [Future] KafkaAppender      → producer.send(...)   ← zero changes to Logger
```

**Open/Closed Principle:** Logger is closed for modification, open for extension. New destinations = new class only.

---

### 3. Builder — `LogMessage`

LogMessage has 5 fields. Builder pattern gives clean construction + immutability.

```java
LogMessage msg = new LogMessage.Builder(LogLevel.ERROR, "Payment failed")
                       .className("PaymentService")
                       .build();
```

**Why immutability matters:** Once built, no thread can modify a LogMessage. Multiple appenders can read it concurrently with zero synchronization.

---

## Class Breakdown

### `LogLevel` (Enum)

```java
enum LogLevel { DEBUG, INFO, WARN, ERROR }
//               ^=0    ^=1   ^=2   ^=3   (ordinals auto-assigned)
```

**Filtering logic:**
```java
if (level.ordinal() < currentLevel.ordinal()) return; // discard
```

| currentLevel | DEBUG logged? | INFO logged? | WARN logged? | ERROR logged? |
|-------------|--------------|-------------|-------------|--------------|
| DEBUG | ✅ | ✅ | ✅ | ✅ |
| INFO | ❌ | ✅ | ✅ | ✅ |
| WARN | ❌ | ❌ | ✅ | ✅ |
| ERROR | ❌ | ❌ | ❌ | ✅ |

---

### `Logger` (Core class)

```java
public synchronized void log(LogLevel level, String message, String className) {
    // 1. Level filter — O(1) ordinal comparison
    if (level.ordinal() < currentLevel.ordinal()) return;

    // 2. Build immutable LogMessage
    LogMessage logMessage = new LogMessage.Builder(level, message)
            .className(className).build();

    // 3. Dispatch to ALL appenders — Strategy Pattern
    for (Appender appender : appenders) {
        appender.append(logMessage);
    }
}
```

Three responsibilities, clearly separated:
- **Filter** (should this log at all?)
- **Build** (construct the LogMessage)
- **Dispatch** (hand off to appenders)

---

### `ConsoleAppender` vs `FileAppender`

| | ConsoleAppender | FileAppender |
|-|----------------|-------------|
| Destination | `System.out` | Disk file |
| Thread safety | Built-in (System.out is synchronized) | Needs `synchronized` keyword |
| Failure mode | Never fails | `IOException` — caught and swallowed |
| Performance | Fast (in-memory) | Slower (disk I/O) |

---

## Concurrency Strategy

### Why `synchronized` on `log()`?

Two problems without it:

**Problem 1 — ConcurrentModificationException:**
```
Thread A: iterating appenders list in log()
Thread B: calling addAppender() — modifies the list
→ CME thrown in Thread A
```

**Problem 2 — Interleaved output:**
```
Thread A: builds LogMessage → "Payment OK"
Thread B: builds LogMessage → "Order Failed"
Thread A: dispatches → ConsoleAppender
Thread B: dispatches → ConsoleAppender
→ Output mixed up, incomplete lines
```

`synchronized` on `log()` serializes access — one complete log operation at a time.

### The Async Upgrade (mention at end of interview)

```
Current (synchronized):
App Thread → log() → [WAITS] → write to file → return

Async approach:
App Thread → queue.offer(msg) → return immediately   ← no waiting
Background Thread → queue.take() → write to file     ← I/O off the critical path
```

Use `LinkedBlockingQueue` as the buffer. App threads never block on I/O.

---

## Complexity Analysis

| Operation | Time | Notes |
|-----------|------|-------|
| `getInstance()` | O(1) | No lock after initialization (DCL) |
| `setLevel()` | O(1) | Simple field assignment |
| `log()` level check | O(1) | Integer ordinal comparison |
| `log()` dispatch | O(A) | A = number of appenders (usually 2-3) |
| `addAppender()` | O(1) | ArrayList.add() |
| `LogMessage.build()` | O(1) | Field assignments only |

All operations are effectively **O(1)**. The system adds zero meaningful overhead to the application.

---

## Running the Code

```bash
# Compile
javac LoggingSystemLLD.java

# Run
java LoggingSystemLLD
```

**Expected output:**
```
=== PHASE 1: SETUP ===
[Config] Level=DEBUG, Appenders=[Console, File]

=== PHASE 2: BASIC LOGGING ===
[2024-01-15 10:30:00] [DEBUG] [main] [LoggingSystemLLD] Initializing payment service
[2024-01-15 10:30:00] [INFO ] [main] [LoggingSystemLLD] Server started on port 8080
[2024-01-15 10:30:00] [WARN ] [main] [LoggingSystemLLD] Memory usage above 80%
[2024-01-15 10:30:00] [ERROR] [main] [LoggingSystemLLD] Database connection failed

=== PHASE 3: LEVEL FILTER (set to WARN) ===
[2024-01-15 10:30:00] [WARN ] [main] [LoggingSystemLLD] This WILL appear — WARN >= WARN
[2024-01-15 10:30:00] [ERROR] [main] [LoggingSystemLLD] This WILL appear — ERROR > WARN

=== PHASE 4: CONCURRENT LOGGING ===
[...9 lines from 3 threads, each complete and uninterrupted...]

=== PHASE 5: RUNTIME LEVEL CHANGE ===
[2024-01-15 10:30:00] [ERROR] [main] [LoggingSystemLLD] CRITICAL: payment gateway unreachable

[Done] Check app.log for the file output.
```

Also creates `app.log` in the current directory with the same output (phases 2-5).

---

## Interview Script

### How to spend your 45 minutes

| Time | What to do |
|------|-----------|
| 0–5 min | Clarify requirements, give opening statement, draw class diagram |
| 5–10 min | Code `LogLevel` enum + explain ordinal filtering |
| 10–20 min | Code `LogMessage` with Builder, explain immutability |
| 20–25 min | Code `Appender` interface, explain Strategy Pattern |
| 25–35 min | Code `Logger` Singleton with `log()`, explain DCL + synchronized |
| 35–40 min | Code `ConsoleAppender` + `FileAppender`, write the demo main |
| 40–45 min | Run through improvements (async, formatter, filter, named loggers) |

### Key things to say out loud

**On Strategy Pattern:**
> *"I chose an interface for Appender so that Logger has zero dependency on where logs go. If tomorrow we need to send logs to Kafka or Elasticsearch, we create one new class. Logger doesn't change at all."*

**On Singleton + DCL:**
> *"The outer null check skips the monitor lock on every call after initialization — that's the performance win. The inner check handles the race condition when two threads simultaneously see null. The volatile field is critical — without it, CPU reordering could let another thread see a non-null but incompletely constructed Logger."*

**On Builder:**
> *"LogMessage is immutable after construction. No setters. This means once an appender gets a LogMessage, it's guaranteed to never change — multiple appenders can safely read it in parallel with no synchronization needed."*

**On synchronized log():**
> *"I've synchronized log() to prevent two issues: ConcurrentModificationException if addAppender is called during iteration, and interleaved log output from concurrent threads. For higher throughput, I'd make this async using a LinkedBlockingQueue — but synchronized is correct and explainable for this scope."*

---

## Common Follow-up Questions

### Q: How would you make it async?
Use `LinkedBlockingQueue<LogMessage>`. App threads call `queue.offer(msg)` (non-blocking, returns immediately). A single background thread calls `queue.take()` (blocks when empty) and dispatches to appenders. App threads never wait for I/O.

### Q: How would you add different formats per appender (JSON vs plaintext)?
Add a `Formatter` interface: `String format(LogMessage msg)`. Each Appender holds a Formatter. `JSONFormatter` and `PlainTextFormatter` implement it. This is Strategy Pattern applied to formatting. ConsoleAppender uses PlainText, FileAppender uses JSON.

### Q: How would you support named loggers like Log4j?
Replace the Singleton with a registry: `Map<String, Logger>`. `Logger.getLogger("PaymentService")` returns (or lazily creates) a Logger for that name. Each named logger has its own level. Parent-child hierarchy: `com.app.payment` inherits from `com.app` unless overridden.

### Q: How would you add log rotation?
In `FileAppender`, before each write check: if file size > 10MB or date has changed → close current file, rename to `app-2024-01-15.log`, open new `app.log`. This is what Logback's `RollingFileAppender` does.

### Q: How would you filter logs by class name?
Add a `Filter` interface: `boolean accept(LogMessage msg)`. Appenders hold a list of Filters. Before writing, check all filters — if any rejects, skip. `ClassNameFilter` checks `msg.getClassName().startsWith("com.app.payment")`. This is Chain of Responsibility.

---

## Improvements for Discussion

Mention these **at the end** when the interviewer asks *"what would you improve?"*

| Feature | Approach | Pattern |
|---------|----------|---------|
| High throughput | Async queue (LinkedBlockingQueue) | Producer-Consumer |
| Multiple formats | Formatter interface per Appender | Strategy |
| Log rotation | Size/time check in FileAppender | — |
| Named loggers | Logger registry + parent hierarchy | Factory + Registry |
| Class filtering | Filter interface on Appender | Chain of Responsibility |
| Remote logging | KafkaAppender / ElasticsearchAppender | Strategy (new class) |
| MDC (trace IDs) | ThreadLocal<Map> for request context | — |
| Config hot-reload | FileWatcher + Observer pattern | Observer |

---

## SOLID Principles Applied

| Principle | Where |
|-----------|-------|
| **S**ingle Responsibility | Logger logs. Appender writes. LogMessage holds data. Each does one thing. |
| **O**pen/Closed | Add new destination = new Appender class. Logger never changes. |
| **L**iskov Substitution | `ConsoleAppender` and `FileAppender` are interchangeable wherever `Appender` is used. |
| **I**nterface Segregation | `Appender` has exactly one method. No unused methods forced on implementors. |
| **D**ependency Inversion | Logger depends on the `Appender` abstraction, not `FileAppender` directly. |

---

*Focused for SDE-1/2 interviews. Every decision is intentional and explainable in plain English.*enecccgbgddrnbkvnrnhfdugkdlgevncgvnnidbduthn
