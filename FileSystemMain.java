import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * ==========================================
 * 1. THE COMPOSITE BASE: FileSystemNode
 * ==========================================
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
 * ==========================================
 * 2. THE LEAF: File
 * ==========================================
 */
class File extends FileSystemNode {
    private StringBuilder content;
    /* INTERVIEW SCRIPT: "In a real OS, this lock would be inside an Inode. 
       Here, it protects the file content during concurrent writes." */
    private final ReentrantReadWriteLock fileLock = new ReentrantReadWriteLock();

    public File(String name, Directory parent) {
        super(name, parent);
        this.content = new StringBuilder();
    }

    public void write(String data) {
        fileLock.writeLock().lock();
        try {
            this.content.append(data);
        } finally {
            fileLock.writeLock().unlock();
        }
    }

    public String read() {
        fileLock.readLock().lock();
        try {
            return content.toString();
        } finally {
            fileLock.readLock().unlock();
        }
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
 * ==========================================
 * 3. THE COMPOSITE: Directory
 * ==========================================
 */
class Directory extends FileSystemNode {
    // Map handles O(1) lookup and ensures unique filenames per folder
    private final Map<String, FileSystemNode> children = new HashMap<>();
    
    /* INTERVIEW EXPLANATION: Lock Striping allows high concurrency. 
       Multiple threads can READ different folders at once. */
    private final ReentrantReadWriteLock dirLock = new ReentrantReadWriteLock();

    public Directory(String name, Directory parent) {
        super(name, parent);
    }

    public void addNode(FileSystemNode node) {
        dirLock.writeLock().lock();
        try {
            children.put(node.getName(), node);
        } finally {
            dirLock.writeLock().unlock();
        }
    }

    public FileSystemNode getChild(String name) {
        dirLock.readLock().lock();
        try {
            return children.get(name);
        } finally {
            dirLock.readLock().unlock();
        }
    }

    public List<String> ls() {
        dirLock.readLock().lock();
        try {
            return new ArrayList<>(children.keySet());
        } finally {
            dirLock.readLock().unlock();
        }
    }

    @Override
    public int getSize() {
        dirLock.readLock().lock();
        try {
            /* INTERVIEW SCRIPT: "This is the core of the Composite Pattern. 
               The recursion happens here—summing up files and sub-directories." */
            int totalSize = 0;
            for (FileSystemNode child : children.values()) {
                totalSize += child.getSize();
            }
            return totalSize;
        } finally {
            dirLock.readLock().unlock();
        }
    }

    @Override
    public boolean isDirectory() { return true; }
}

/**
 * ==========================================
 * 4. THE ORCHESTRATOR: FileSystem (Singleton)
 * ==========================================
 */
class FileSystem {
    private static FileSystem instance;
    private final Directory root;

    private FileSystem() {
        this.root = new Directory("/", null);
    }

    public static synchronized FileSystem getInstance() {
        if (instance == null) instance = new FileSystem();
        return instance;
    }

    /* INTERVIEW SCRIPT: "Path resolution is the bridge between human strings 
       and our object-oriented tree. I use a tokenizer to walk the nodes." */
    public Directory resolvePath(String path) {
        if (path == null || path.equals("/") || path.isEmpty()) return root;
        
        String[] parts = path.split("/");
        Directory current = root;
        
        for (String part : parts) {
            if (part.isEmpty()) continue;
            FileSystemNode next = current.getChild(part);
            if (next != null && next.isDirectory()) {
                current = (Directory) next;
            } else {
                return null; // Path invalid
            }
        }
        return current;
    }

    public void mkdir(String parentPath, String name) {
        Directory parent = resolvePath(parentPath);
        if (parent != null) {
            parent.addNode(new Directory(name, parent));
        }
    }

    public void createFile(String parentPath, String name, String initialContent) {
        Directory parent = resolvePath(parentPath);
        if (parent != null) {
            File newFile = new File(name, parent);
            newFile.write(initialContent);
            parent.addNode(newFile);
        }
    }
}

/**
 * ==========================================
 * 5. THE MASTER SIMULATION
 * ==========================================
 */
public class FileSystemMain {
    public static void main(String[] args) throws InterruptedException {
        FileSystem fs = FileSystem.getInstance();

        System.out.println("--- PHASE 1: HIERARCHY SETUP ---");
        fs.mkdir("/", "users");
        fs.mkdir("/users", "rishi");
        fs.mkdir("/users/rishi", "projects");
        fs.mkdir("/users/rishi", "notes");

        System.out.println("[INFO] Created structure: /users/rishi/[projects, notes]");

        System.out.println("\n--- PHASE 2: CONCURRENT FILE CREATION ---");
        /* INTERVIEW POINT: We use 3 threads writing to DIFFERENT folders. 
           Because we used Lock Striping, there is ZERO contention here. */
        ExecutorService executor = Executors.newFixedThreadPool(3);

        executor.submit(() -> fs.createFile("/users/rishi/projects", "LLD_Code.java", "public class Main {}")); // 20 bytes
        executor.submit(() -> fs.createFile("/users/rishi/notes", "todo.txt", "Buy milk, Learn LLD")); // 20 bytes
        executor.submit(() -> {
            Directory root = fs.resolvePath("/");
            fs.createFile("/", "system_log.log", "System Booted Successfully"); // 26 bytes
        });

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("\n--- PHASE 3: COMPOSITE SIZE CALCULATION ---");
        /* INTERVIEW POINT: Size of /users/rishi should be projects (20) + notes (20) = 40.
           The recursion automatically handles the depth. */
        Directory rishiDir = fs.resolvePath("/users/rishi");
        Directory rootDir = fs.resolvePath("/");

        System.out.println("Size of /users/rishi: " + rishiDir.getSize() + " bytes");
        System.out.println("Total System Size (/): " + rootDir.getSize() + " bytes");

        System.out.println("\n--- PHASE 4: PATH SEARCH & CONTENT READ ---");
        Directory projectDir = fs.resolvePath("/users/rishi/projects");
        if (projectDir != null) {
            File javaFile = (File) projectDir.getChild("LLD_Code.java");
            System.out.println("Reading 'LLD_Code.java': " + javaFile.read());
        }

        System.out.println("\n--- PHASE 5: DIRECTORY LISTING (ls) ---");
        System.out.println("Contents of /users/rishi: " + rishiDir.ls());
    }
}
