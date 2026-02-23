
# 🐍 Ultimate Snake & Food Game Low-Level Design (Multithreaded)

This repository contains a production-ready, multithreaded Low-Level Design for the classic Snake Game. Unlike a basic console script, this design separates the game rules from the game clock, utilizing background threads, thread-safe queues, and highly optimized data structures to create a scalable, arcade-style engine.

Throughout this guide, you will find **"Interview Scripts"**. These are plain-English, first-person explanations you can use during a system design interview to explain the *Why* behind the code.

---

## 🗣️ 1. The Interview Kickoff (Clarifying Questions)
*Never start coding on a blank whiteboard without scoping the problem first!*

**Interview Script:** > "Before I define any core models, I'd like to ask a few clarifying questions to ensure I am building exactly what you need:
> 1. Are we designing just the backend Game API (where a front-end UI calls `tick()` manually), or do you want a fully automated, multithreaded Game Clock Engine?
> 2. Does the snake die if it hits a wall, or does it wrap around the board (Pac-Man style)?
> 3. Should I build just standard food, or should I design the system to easily handle special items (like 'Bonus Apples' or 'Poison') in the future?"

*(Assume the interviewer asks for a fully automated multithreaded engine, hard walls, and extensible food types).*

---

## 🏗️ 2. Core Architecture & Design Patterns



To make this production-ready, I utilized 4 major design patterns:

### A. The Factory Pattern (Extensible Food)
* **What:** A dedicated class (`FoodFactory`) responsible for generating food objects.
* **Why:** The main game loop shouldn't do complex math to figure out empty coordinates. 
* **Interview Script:** *"I am making Food an abstract base class. If the product manager asks us to add 'Golden Apples' that give 50 points tomorrow, I don't want to rewrite the entire Game loop. I just add a `BonusFood` class, and the Factory handles the rest. This perfectly follows the Open/Closed Principle."*

### B. The Command Pattern (Input Handling)
* **What:** Wrapping arrow-key presses into a queue.
* **Why:** A simple `currentDirection` variable will drop inputs if a player types too fast.
* **Interview Script:** *"If a player presses UP then RIGHT in just 20 milliseconds, a normal variable would overwrite 'UP'. By pushing commands into an `inputQueue`, I save both commands in a line and execute them one by one perfectly, ensuring zero dropped frames."*

### C. The Game Loop Pattern & Multithreading (The Engine)
* **What:** An automated background thread that ticks the game clock.
* **Why:** Putting a `while(true)` loop inside the `Game` class freezes the main UI thread. 
* **Interview Script:** *"I am separating the 'Rules' from the 'Clock'. The `Game` class holds the rules. The `GameEngine` class holds a `ScheduledExecutorService` background thread. This ticks the game automatically while keeping the main UI thread 100% free to listen for lightning-fast keystrokes."*

### D. The Observer Pattern (The Walkie-Talkie)

* **What:** An interface (`GameObserver`) that the UI listens to.
* **Why:** The backend shouldn't know how to draw pixels on a screen.
* **Interview Script:** *"The Game backend should never write code to color pixels. Instead, it uses this Observer interface to shout 'I Updated!' over the radio. The front-end UI hears it and redraws the screen itself."*

---

## 🚀 3. Data Structure Optimizations & Thread Safety

To achieve maximum performance and prevent server crashes, we use highly specific Java structures.



* **ArrayDeque (Snake Body):** Both `LinkedList` and `ArrayDeque` give $O(1)$ insertions/deletions at the ends. However, `LinkedList` scatters nodes randomly in RAM. `ArrayDeque` uses continuous memory blocks, which is incredibly fast for CPU caching. 
* **HashSet (Crash Detection):** Checking if a 100-block-long snake bit itself takes $O(N)$ time if we loop through the Deque. By mirroring the coordinates inside a `HashSet`, we achieve instant $O(1)$ crash detection.
* **ConcurrentLinkedQueue (Thread Safety):** Because our background Game Engine thread reads the `inputQueue`, and our main UI thread writes to the `inputQueue` at the exact same time, a standard `LinkedList` would throw a `ConcurrentModificationException` and crash the server. `ConcurrentLinkedQueue` provides lock-free, thread-safe operations.

---

## 💻 4. The Complete Java Code

```java
import java.util.*;
import java.util.concurrent.*;

// ==========================================
// 1. ENUMS (State & Direction)
// ==========================================
enum Direction { UP, DOWN, LEFT, RIGHT }
enum GameStatus { READY, RUNNING, PAUSED, GAME_OVER }

// ==========================================
// 2. THE GRID (Cells & Board)
// ==========================================
class Cell {
    private int row;
    private int col;

    public Cell(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public int getRow() { return row; }
    public int getCol() { return col; }

    // INTERVIEW SCRIPT: "I override these specifically so I can store Cells inside 
    // a HashSet later for O(1) crash detection. Java HashSets need this to verify coordinates."
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Cell cell = (Cell) obj;
        return row == cell.row && col == cell.col;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, col);
    }
}

class Board {
    private int rows;
    private int cols;
    private Food currentFood;

    public Board(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
    }

    public Food getCurrentFood() { return currentFood; }
    public void setCurrentFood(Food currentFood) { this.currentFood = currentFood; }

    public boolean isOutOfBounds(Cell cell) {
        return cell.getRow() < 0 || cell.getRow() >= rows || 
               cell.getCol() < 0 || cell.getCol() >= cols;
    }
}

// ==========================================
// 3. THE FOOD & FACTORY 
// ==========================================
abstract class Food {
    private Cell position;
    private int points; 

    public Food(Cell position, int points) {
        this.position = position;
        this.points = points;
    }
    public Cell getPosition() { return position; }
    public int getPoints() { return points; }
}

class NormalFood extends Food {
    public NormalFood(Cell position) { super(position, 10); } 
}

class FoodFactory {
    private int boardRows;
    private int boardCols;
    private Random randomGenerator;

    public FoodFactory(int boardRows, int boardCols) {
        this.boardRows = boardRows;
        this.boardCols = boardCols;
        this.randomGenerator = new Random();
    }

    // INTERVIEW SCRIPT: "Passing the snake's occupied cells prevents a critical bug 
    // where an apple randomly spawns inside the snake's stomach! The loop rolls 
    // the dice until it finds a 100% empty square."
    public Food generateFood(Set<Cell> snakeBodyCells) {
        int randomRow, randomCol;
        Cell randomCell;

        do {
            randomRow = randomGenerator.nextInt(boardRows);
            randomCol = randomGenerator.nextInt(boardCols);
            randomCell = new Cell(randomRow, randomCol);
        } while (snakeBodyCells.contains(randomCell));

        return new NormalFood(randomCell);
    }
}

// ==========================================
// 4. THE SNAKE 
// ==========================================
class Snake {
    
    // INTERVIEW SCRIPT: "ArrayDeque gives O(1) speed for moving, but uses fast continuous memory."
    private Deque<Cell> snakeBody;
    
    // INTERVIEW SCRIPT: "HashSet gives O(1) instant crash detection instead of O(N) loops."
    private Set<Cell> occupiedCells;
    private Cell head;

    public Snake(Cell startingPosition) {
        this.snakeBody = new ArrayDeque<>();
        this.occupiedCells = new HashSet<>();
        this.head = startingPosition;
        
        this.snakeBody.addFirst(head);
        this.occupiedCells.add(head);
    }

    public Cell getHead() { return head; }
    public Set<Cell> getOccupiedCells() { return occupiedCells; }

    public void move(Cell nextCell, boolean isEatingFood) {
        head = nextCell;
        snakeBody.addFirst(head); 
        occupiedCells.add(head);  

        if (!isEatingFood) {
            Cell tail = snakeBody.removeLast(); 
            occupiedCells.remove(tail); 
        }
    }

    public boolean checkCrashIntoSelf(Cell nextCell) {
        return occupiedCells.contains(nextCell);
    }
}

// ==========================================
// 5. OBSERVER PATTERN (The Walkie-Talkie)
// ==========================================
interface GameObserver {
    void onGameUpdated(); 
    void onGameOver();    
}

// ==========================================
// 6. THE GAME CONTROLLER 
// ==========================================
class Game {
    private Board board;
    private Snake snake;
    private FoodFactory foodFactory;
    private GameStatus status;
    private int score;
    private Direction currentDirection;
    
    // COMMAND PATTERN: Thread-safe queue for player inputs
    private Queue<Direction> inputQueue;

    public Game(Board board, Snake snake, FoodFactory foodFactory) {
        this.board = board;
        this.snake = snake;
        this.foodFactory = foodFactory;
        this.status = GameStatus.READY;
        this.score = 0;
        this.currentDirection = Direction.RIGHT; 
        this.inputQueue = new ConcurrentLinkedQueue<>();
        this.board.setCurrentFood(this.foodFactory.generateFood(snake.getOccupiedCells()));
    }

    public GameStatus getStatus() { return status; }
    public void startGame() { this.status = GameStatus.RUNNING; }

    public void addDirectionInput(Direction newDirection) {
        inputQueue.offer(newDirection);
    }

    public boolean tick() {
        if (status != GameStatus.RUNNING) return false;

        Direction nextDirection = getValidNextDirection();
        Cell nextCell = getNextCell(snake.getHead(), nextDirection);

        if (board.isOutOfBounds(nextCell) || snake.checkCrashIntoSelf(nextCell)) {
            status = GameStatus.GAME_OVER;
            return false;
        }

        Food currentFood = board.getCurrentFood();
        boolean isEatingFood = false;

        if (nextCell.equals(currentFood.getPosition())) {
            isEatingFood = true;
            score += currentFood.getPoints();
            board.setCurrentFood(foodFactory.generateFood(snake.getOccupiedCells()));
        }

        snake.move(nextCell, isEatingFood);
        this.currentDirection = nextDirection;
        return isEatingFood; // Tells the Engine to speed up!
    }

    // INTERVIEW SCRIPT: "This safely ignores suicidal reverse inputs."
    private Direction getValidNextDirection() {
        if (inputQueue.isEmpty()) return currentDirection;
        Direction requestedDirection = inputQueue.poll();

        if (currentDirection == Direction.UP && requestedDirection == Direction.DOWN) return currentDirection;
        if (currentDirection == Direction.DOWN && requestedDirection == Direction.UP) return currentDirection;
        if (currentDirection == Direction.LEFT && requestedDirection == Direction.RIGHT) return currentDirection;
        if (currentDirection == Direction.RIGHT && requestedDirection == Direction.LEFT) return currentDirection;

        return requestedDirection;
    }

    private Cell getNextCell(Cell currentHead, Direction direction) {
        int row = currentHead.getRow();
        int col = currentHead.getCol();

        if (direction == Direction.UP) row--;
        else if (direction == Direction.DOWN) row++;
        else if (direction == Direction.LEFT) col--;
        else if (direction == Direction.RIGHT) col++;

        return new Cell(row, col);
    }
}

// ==========================================
// 7. MULTITHREADED GAME ENGINE (The Clock)
// ==========================================


class GameEngine {
    private Game game;
    private GameObserver uiObserver;
    
    private static final int MIN_TICK_DELAY_MS = 40;
    private int currentTickDelayMs = 200; 
    private ScheduledExecutorService gameClock;
    
    // INTERVIEW SCRIPT: "We keep this 'receipt' so we can cancel and reboot the timer to speed it up."
    private ScheduledFuture<?> currentRunningTask;

    public GameEngine(Game game, GameObserver uiObserver) {
        this.game = game;
        this.uiObserver = uiObserver;
        this.gameClock = Executors.newScheduledThreadPool(1);
    }

    public void start() {
        game.startGame();
        scheduleClock(); 
    }

    private void runGameLoop() {
        boolean ateFood = game.tick();

        if (game.getStatus() == GameStatus.GAME_OVER) {
            uiObserver.onGameOver();
            stop();
            return;
        }

        if (ateFood) levelUpSpeed();
        uiObserver.onGameUpdated();
    }

    // INTERVIEW SCRIPT: "We use an asymptotic curve (-10%) and Math.max to enforce a hard speed limit."
    private void levelUpSpeed() {
        int newSpeed = (int) (this.currentTickDelayMs * 0.9);
        this.currentTickDelayMs = Math.max(MIN_TICK_DELAY_MS, newSpeed);
        scheduleClock();
    }

    // INTERVIEW SCRIPT: "You cannot change the speed of a running executor. 
    // You must cancel the old task and schedule a brand new one."
    private void scheduleClock() {
        if (currentRunningTask != null && !currentRunningTask.isCancelled()) {
            currentRunningTask.cancel(false); 
        }

        currentRunningTask = this.gameClock.scheduleAtFixedRate(
            () -> runGameLoop(), 0, this.currentTickDelayMs, TimeUnit.MILLISECONDS
        );
    }

    public void stop() {
        if (this.gameClock != null && !this.gameClock.isShutdown()) {
            this.gameClock.shutdown();
        }
    }
}

```

---

## 🌟 5. Senior SDE Bonus (Advanced Improvements)

If the interviewer asks: *"How would you improve this system?"*, propose these advanced features:

1. **Board Wrapping (Pac-Man Style):** Use Modulo Arithmetic (`newRow = (currentRow + 1) % boardRows`) in `getNextCell()` to safely wrap the snake around the board without massive `if/else` checks.
2. **Mazes & Obstacles:** Add a `Set<Cell> obstacles` to the `Board` class. Update the `isOutOfBounds` method to check for obstacle collisions, and pass the obstacle set to the `FoodFactory` so apples don't spawn inside brick walls.
3. **Auto-Pilot Bot:** Use the **Strategy Pattern** to swap `HumanInput` with an `AIInputStrategy`. The AI uses **BFS (Breadth-First Search)** to find the absolute shortest path to the apple and automatically pushes directions into the `inputQueue`.
4. **Replay System:** Save the starting seed of the `Random` number generator in the `FoodFactory`, and log a timestamp of every arrow key press. Replaying the game uses almost zero database storage because we just replay the exact inputs against the same math seed.
