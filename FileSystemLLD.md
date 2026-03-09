# **Thread-Safe File System (LLD)**

## **1. Architectural Philosophy**

A File System is a hierarchical data store that bridges the gap between a user’s **logical view** (Names and Folders) and the computer's **physical view** (Bytes and Metadata). This design focuses on three pillars:

* **Uniformity:** Treating Files and Directories similarly using the Composite Pattern as `FileSystemNode`.
* **Integrity:** Ensuring thread safety through Lock Striping.
* **Scalability:** Decoupling names (Directory Entries) from attributes (Inodes).

---

## **2. Design Patterns & Industry Analogies**

| Pattern | Purpose in this System | Real-World Analogy |
| --- | --- | --- |
| **Composite** | To treat `File` (Leaf) and `Directory` (Composite) as `FileSystemNode`. | **The Cardboard Box:** A box can hold a book (File) or another box (Directory). |
| **Singleton** | For the `FileSystem` Orchestrator. | **The Central Registry:** One single source of truth for the entire drive. |
| **Strategy** | For `PathResolution`. Supports different path styles (Unix vs. Windows). | **Language Translation:** The core meaning (target file) is the same, but the syntax (path) varies. |
| **State/Metadata** | The `Inode` concept. Separates file identity from its data. | **A Library Card:** The card contains the location and rules (Metadata), the book is the content (Data). |

---

## **3. Advanced Concepts (The "SDE-2" Knowledge Base)**

### **A. Lock Striping (Concurrency)**

Instead of one "Global Lock" for the whole system, we place a `ReentrantReadWriteLock` inside **each Directory**.

* **Benefit:** Two users can create files in `/home/user1` and `/home/user2` simultaneously without blocking each other.
* **Multiple Readers:** Many users can `ls` or `getSize()` at the same time.

### **B. Path Resolution & Caching**

We translate a string `"/a/b/c"` into an object by "walking" the tree.

* **Optimization:** In a production system, we would use a **Dentry Cache** (a Map in memory) to store `Path -> Node` lookups to avoid $O(D)$ traversal for frequent accesses.

### **C. Blocks & Inodes**

In a real disk-based system:

* **Inodes:** Store permissions, owner, and block pointers.
* **Journaling:** A log of "intents" written before changes are made to prevent data loss during power cuts.

---

## **3.2 The "Deep-Dive" Knowledge Base (SDE-2 Master Class)**

---

### **A. Metadata vs. Data (The Inode Concept)**

In a professional-grade File System (like Ext4 or APFS), we never store data directly "inside" a filename. We use an **Inode (Index Node)**.

* **The Logic:** The Directory is just a table of `Name -> Inode ID`. The Inode is the actual "Identity Card" of the file.
* **What's inside an Inode?** Permissions, Owner ID, Size, Timestamps, and **Block Pointers** (addresses of where the data lives on the disk).
* **Hard Links:** You can have two different names in two different folders pointing to the **same Inode ID**. This is why a file isn't deleted until the "Reference Count" in the Inode reaches zero.
* **Soft Links (Symlinks):** This is a special file that contains a "String Path" (like a shortcut). If you delete the original file, the Inode is gone, but the Symlink remains, pointing to a path that no longer exists (a "broken link").

---

### **B. Reliability: Journaling**

This is how modern file systems survive a sudden power loss or a system crash.

* **The Problem:** If the system is halfway through updating a directory and the power cuts, the "pointers" might get corrupted, causing a "File System Error."
* **The Solution (Journaling):** Before any change is made to the main tree, the system writes the "Intent" (e.g., *"I am moving file 'X' from folder 'A' to 'B'"*) into a small, dedicated space called the **Journal**.
* **The Recovery:** On reboot, the system checks the Journal. If it finds an incomplete entry, it either finishes the task or rolls it back perfectly. This is the **ACID** principle applied to file systems.

---

### **C. Performance: The Dentry Cache**

"Walking" a directory path like `/users/rishi/projects/java/main.java` is slow because it requires multiple lookups ($O(Depth)$).

* **The Fix:** We use a **Dentry Cache (Directory Entry Cache)**. This is an in-memory `ConcurrentHashMap<String, FileSystemNode>` that maps the **full path string** directly to the node object.
* **SDE-2 Trade-off:** We trade **Memory (RAM)** for **Speed**. While it makes lookups $O(1)$, we must implement a cache-invalidation logic so that if a directory is renamed, the cache is updated.

---

### **D. Storage Optimization: Blocks vs. Extents**

* **Blocks:** A file is chopped into tiny 4KB chunks. This is easy to manage but if a file is 1GB, the Inode has to store a massive list of pointers, which is inefficient.
* **Extents:** Instead of listing every 4KB block, the Inode says: *"This file starts at Block 500 and uses the next 2000 contiguous blocks."* This makes reading large files much faster and keeps metadata small.

---

### **E. Memory Management: Page Cache**

The OS almost never reads directly from the disk for every request.

* **Page Cache:** When you request a file, the OS copies that "page" of data into RAM.
* **Write-Back Policy:** When you write to a file, the OS updates the RAM version and marks it as **"Dirty."** A background thread (called a flusher) periodically writes these "Dirty" pages back to the physical disk. This makes the system feel incredibly fast to the user.

---

## **SDE-2 Interview "Cheat Sheet" for Part 3**

| Feature | If they ask: "How to scale?" | If they ask: "How to make it safe?" |
| --- | --- | --- |
| **Lookup Speed** | Mention **Dentry Caching** and **B-Trees**. | Mention **Path Resolution** validation. |
| **Data Safety** | Mention **Reference Counting** for Inodes. | Mention **Journaling** and **Checksums**. |
| **Storage Efficiency** | Mention **Extents** and **Compression**. | Mention **De-duplication** (multiple files, one Inode). |

---

## **4. The Complete Production-Ready Implementation**

```java
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * COMPONENT: Common interface for Files and Directories.
 */
abstract class FileSystemNode {
    protected String name;
    protected Directory parent;
    protected long creationTime;

    public FileSystemNode(String name, Directory parent) {
        this.name = name;
        this.parent = parent;
        this.creationTime = System.currentTimeMillis();
    }

    public abstract int getSize();
    public abstract boolean isDirectory();
    public String getName() { return name; }
}

/**
 * LEAF: Represents file data.
 */
class File extends FileSystemNode {
    private StringBuilder content;
    private final ReentrantReadWriteLock fileLock = new ReentrantReadWriteLock();

    public File(String name, Directory parent) {
        super(name, parent);
        this.content = new StringBuilder();
    }

    /* INTERVIEW NOTE: File-level locking ensures content isn't corrupted 
       during simultaneous writes from different threads. */
    public void write(String data) {
        fileLock.writeLock().lock();
        try { this.content.append(data); } 
        finally { fileLock.writeLock().unlock(); }
    }

    public String read() {
        fileLock.readLock().lock();
        try { return content.toString(); } 
        finally { fileLock.readLock().unlock(); }
    }

    @Override
    public int getSize() { 
        fileLock.readLock().lock();
        try { return content.length(); } 
        finally { fileLock.readLock().unlock(); }
    }

    @Override
    public boolean isDirectory() { return false; }
}

/**
 * COMPOSITE: Manages children and implements recursive size calculation.
 */
class Directory extends FileSystemNode {
    private final Map<String, FileSystemNode> children = new HashMap<>();
    private final ReentrantReadWriteLock dirLock = new ReentrantReadWriteLock();

    public Directory(String name, Directory parent) {
        super(name, parent);
    }

    public void addNode(FileSystemNode node) {
        dirLock.writeLock().lock();
        try { children.put(node.getName(), node); } 
        finally { dirLock.writeLock().unlock(); }
    }

    public FileSystemNode getChild(String name) {
        dirLock.readLock().lock();
        try { return children.get(name); } 
        finally { dirLock.readLock().unlock(); }
    }

    @Override
    public int getSize() {
        dirLock.readLock().lock();
        try {
            int totalSize = 0;
            for (FileSystemNode child : children.values()) {
                totalSize += child.getSize(); // Recursive call
            }
            return totalSize;
        } finally { dirLock.readLock().unlock(); }
    }

    public List<String> ls() {
        dirLock.readLock().lock();
        try { return new ArrayList<>(children.keySet()); } 
        finally { dirLock.readLock().unlock(); }
    }

    @Override
    public boolean isDirectory() { return true; }
}

/**
 * SINGLETON: Entry point and Path Resolver.
 */
class FileSystem {
    private static FileSystem instance;
    private final Directory root;

    private FileSystem() { this.root = new Directory("/", null); }

    public static synchronized FileSystem getInstance() {
        if (instance == null) instance = new FileSystem();
        return instance;
    }

    public Directory resolvePath(String path) {
        if (path == null || path.equals("/") || path.isEmpty()) return root;
        String[] parts = path.split("/");
        Directory current = root;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            FileSystemNode next = current.getChild(part);
            if (next != null && next.isDirectory()) current = (Directory) next;
            else return null;
        }
        return current;
    }

    public void mkdir(String path, String name) {
        Directory parent = resolvePath(path);
        if (parent != null) parent.addNode(new Directory(name, parent));
    }

    public void createFile(String path, String name, String content) {
        Directory parent = resolvePath(path);
        if (parent != null) {
            File f = new File(name, parent);
            f.write(content);
            parent.addNode(f);
        }
    }
}

/**
 * SIMULATION: Touches all functionalities including Concurrency and Recursion.
 */
public class Main {
    public static void main(String[] args) throws InterruptedException {
        FileSystem fs = FileSystem.getInstance();

        // 1. Structure Creation
        fs.mkdir("/", "users");
        fs.mkdir("/users", "rishi");
        fs.mkdir("/users/rishi", "docs");

        // 2. Concurrent Testing
        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(() -> fs.createFile("/users/rishi/docs", "file1.txt", "Hello World")); // 11 bytes
        executor.submit(() -> fs.createFile("/users/rishi", "root_note.txt", "Secret Data")); // 11 bytes

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // 3. Size Validation (Recursive)
        Directory rishi = fs.resolvePath("/users/rishi");
        System.out.println("Total Size of /users/rishi: " + rishi.getSize() + " bytes");
        System.out.println("Listing /users/rishi: " + rishi.ls());
    }
}

```
---

## **5. The "Grill" Defense (Common Interview Cross-Questions)**

**Q: "Why use `HashMap` in the Directory? Isn't it slow?"**

* **Defense:** "Actually, a `HashMap` provides $O(1)$ average time complexity for lookups, which is far superior to an $O(N)$ list search. For massive directories on disk, I would switch to a **B+ Tree** index."

**Q: "How do you handle 'Dirty' writes (power failure)?"**

* **Defense:** "In a real system, I would use **Journaling**. Every write intent is logged before the actual data is moved. If the system crashes, we replay the log on reboot."

**Q: "How would you implement Hard Links?"**

* **Defense:** "By using an **Inode Table**. Instead of the File object holding the data directly, it holds an `InodeID`. Multiple File objects (with different names/paths) can point to the same `InodeID`. The data is only deleted when the **Reference Count** of the Inode reaches zero."

---

## **The "Grill" Defense: SDE-2 Cross-Question Bank**

### **1. The Scalability Grill: "How do you handle 100 Million Files?"**

* **The Problem:** Our current `HashMap` in the `Directory` class would exceed RAM.
* **The SDE-2 Defense:** "For a system of that scale, I would move from an in-memory `HashMap` to a **B-Tree or B+ Tree** index stored on disk. B-Trees are optimized for systems where data doesn't fit in RAM because they minimize disk I/O. I would also implement **Metadata Partitioning**, where different parts of the directory tree are managed by different metadata servers."

### **2. The Concurrency Grill: "How do you handle the 'Thundering Herd' on the Root Directory?"**

* **The Problem:** If every path resolution starts at the Root (`/`), the Root's `readLock` becomes a bottleneck.
* **The SDE-2 Defense:** "To prevent the Root from becoming a bottleneck, I would use **Lock Striping** and a **Dentry Cache (Directory Entry Cache)**. By caching frequently accessed paths (like `/home/user/docs`) in a `ConcurrentHashMap`, we bypass the need to lock and traverse the Root for every single operation. I’d also use **Optimistic Locking** for reads to reduce lock contention."

### **3. The Reliability Grill: "What happens if the system crashes during a `move` operation?"**

* **The Problem:** A `move` is a `delete` from Folder A and an `add` to Folder B. If it crashes in between, the file is lost.
* **The SDE-2 Defense:** "I would implement **Journaling (Write-Ahead Logging)**. Before the move begins, a 'Transaction Log' is written to a persistent circular buffer. If the system crashes, the **Recovery Manager** reads the journal on reboot and either completes the move or rolls it back. This ensures **Atomicity**—the move either happens completely or not at all."

### **4. The Optimization Grill: "Calculating folder size recursively is slow. How do you fix it?"**

* **The Problem:** Our `getSize()` is $O(N)$ where $N$ is the number of nested files.
* **The SDE-2 Defense:** "I would implement **Size Propagation (Event-based updates)**. Each directory would maintain a `cachedSize` variable. Whenever a file is written to or deleted, it sends an 'Update' signal up the chain to its parent, which updates its own cache and passes the signal up. This turns `getSize()` into an $O(1)$ operation, moving the cost to the `write` operation where it's more manageable."

### **5. The "Trick" Grill: "What is the difference between a Hard Link and a Soft Link?"**

* **The Problem:** This tests if you understand the Inode/Identity layer.
* **The SDE-2 Defense:** * "A **Hard Link** is a direct pointer to the same **Inode**. Both names are equal. The data only disappears when the 'Reference Count' in the Inode hits zero."
* "A **Soft Link (Symlink)** is a special file that contains a string (the path). If the original file is moved, the Symlink points to nothing (a 'dangling pointer')."



### **6. The Memory Grill: "How do you handle 'Dirty' data in the Page Cache?"**

* **The Problem:** Writing to disk for every single byte is too slow.
* **The SDE-2 Defense:** "I would use a **Write-Back Cache policy**. When a user writes to a file, we update the data in the **Page Cache (RAM)** and mark that page as 'Dirty'. A background 'Flusher' thread (like `pdflush` in Linux) periodically gathers all dirty pages and writes them to the disk in a single, efficient sequential operation."

---

## **Why this wins the interview**

By answering with these specific terms (**B-Trees, Atomicity, Lock Striping, Reference Counting**), you are signaling to the interviewer that you understand how **Linux (Ext4)** or **Windows (NTFS)** actually work. You aren't just "coding a tree"; you are "designing a system."

---
