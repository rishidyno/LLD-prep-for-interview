import java.util.*;
import java.util.concurrent.*;

/**
 * ============================================================================
 * THE ULTIMATE SNAKE GAME LOW-LEVEL DESIGN (PRODUCTION READY)
 * ============================================================================
 * INTERVIEW SCRIPT / INTRODUCTION:
 * "To design a production-ready Snake Game, I am separating the 'Rules' from 
 * the 'Clock'. I will use 4 main design patterns:
 * 1. COMMAND PATTERN: A thread-safe queue to capture arrow keys so we never drop inputs.
 * 2. FACTORY PATTERN: To generate different types of food without breaking the game loop.
 * 3. OBSERVER PATTERN: A walkie-talkie system so the backend can tell the UI to redraw.
 * 4. GAME LOOP PATTERN: A background thread that automatically runs the game at a 
 * dynamic speed, ensuring the UI thread never freezes."
 * ============================================================================
 */

// ==========================================
// 1. ENUMS (State & Direction)
// ==========================================

/*
 * INTERVIEW EXPLANATION:
 * "I am using Enums here instead of Strings. I do this because if I type 'UP' as a String, 
 * a junior developer might accidentally type 'UPP' later and crash the entire game. 
 * Enums guarantee 100% strict type safety at compile time."
 */
enum Direction { UP, DOWN, LEFT, RIGHT }
enum GameStatus { READY, RUNNING, PAUSED, GAME_OVER }

// ==========================================
// 2. THE GRID (Cells & Board)
// ==========================================

/*
 * INTERVIEW EXPLANATION:
 * "The Cell class represents one exact X and Y square on the grid. Every single thing 
 * in this game—the snake's head, its tail, and the food—is built out of Cells."
 */
class Cell {
    private int row;
    private int col;

    // "This constructor simply sets the exact position of this block."
    public Cell(int row, int col) {
        this.row = row;
        this.col = col;
    }

    // "Standard getters so other classes can read the coordinates, but no setters 
    // because a Cell's location should never magically change once it is created."
    public int getRow() { return row; }
    public int getCol() { return col; }

    /*
     * INTERVIEW BONUS POINT: 
     * "I am overriding equals() and hashCode() specifically so I can put these Cells 
     * inside a Java HashSet later. Java needs these exact math rules to know if two 
     * Cells represent the exact same spot on the board (which means a crash happened!)."
     */
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

/*
 * INTERVIEW EXPLANATION:
 * "The Board class follows the Single Responsibility Principle. Its only job is to 
 * know its width, its height, and what food is sitting on it. It does NOT move the snake."
 */
class Board {
    private int rows;
    private int cols;
    private Food currentFood;

    // "We create the board with strict boundaries."
    public Board(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
    }

    // "Getters so the Game can check the boundaries."
    public int getRows() { return rows; }
    public int getCols() { return cols; }
    
    // "Getters and setters for the food so the Factory can place new apples here."
    public Food getCurrentFood() { return currentFood; }
    public void setCurrentFood(Food currentFood) { this.currentFood = currentFood; }

    // "A helper method to instantly check if a specific cell is outside the map. 
    // If the row or col is less than 0, or greater than the board size, it hit a wall."
    public boolean isOutOfBounds(Cell cell) {
        return cell.getRow() < 0 || cell.getRow() >= rows || 
               cell.getCol() < 0 || cell.getCol() >= cols;
    }
}

// ==========================================
// 3. THE FOOD & FACTORY (Polymorphism)
// ==========================================

/*
 * INTERVIEW EXPLANATION:
 * "I am making Food an abstract class to follow the Open/Closed Principle. 
 * If the business asks for a new 'Poison Apple' tomorrow, I just add a new class 
 * without rewriting my existing Game logic."
 */
abstract class Food {
    private Cell position;
    private int points; 

    // "Every piece of food needs a location and a score value."
    public Food(Cell position, int points) {
        this.position = position;
        this.points = points;
    }
    public Cell getPosition() { return position; }
    public int getPoints() { return points; }
}

// "A concrete implementation. Normal food gives 10 points."
class NormalFood extends Food {
    public NormalFood(Cell position) { super(position, 10); } 
}

// "Another concrete implementation. Bonus food gives 50 points."
class BonusFood extends Food {
    public BonusFood(Cell position) { super(position, 50); }  
}

/*
 * INTERVIEW EXPLANATION:
 * "This is the Factory Pattern. The main game loop shouldn't do complex math 
 * to figure out where to spawn food. We delegate that specific job to this Factory."
 */
class FoodFactory {
    private int boardRows;
    private int boardCols;
    private Random randomGenerator;

    public FoodFactory(int boardRows, int boardCols) {
        this.boardRows = boardRows;
        this.boardCols = boardCols;
        this.randomGenerator = new Random();
    }

    /*
     * INTERVIEW BONUS POINT:
     * "Notice that I require the Factory to look at the snake's 'occupiedCells'. 
     * This prevents a critical bug where an apple randomly spawns inside the snake's 
     * stomach. The while-loop keeps rolling the dice until it finds a totally empty square."
     */
    public Food generateFood(Set<Cell> snakeBodyCells) {
        int randomRow, randomCol;
        Cell randomCell;

        // "Roll the dice for coordinates until the square is empty."
        do {
            randomRow = randomGenerator.nextInt(boardRows);
            randomCol = randomGenerator.nextInt(boardCols);
            randomCell = new Cell(randomRow, randomCol);
        } while (snakeBodyCells.contains(randomCell));

        // "We give a 10% chance to spawn a Bonus Food, and 90% for Normal Food."
        if (randomGenerator.nextInt(100) < 10) {
            return new BonusFood(randomCell);
        }
        return new NormalFood(randomCell);
    }
}

// ==========================================
// 4. THE SNAKE (Data Structure Optimization)
// ==========================================

class Snake {
    
    /*
     * INTERVIEW EXPLANATION:
     * "I chose ArrayDeque over a standard LinkedList. An ArrayDeque gives us O(1) 
     * speed for adding the head and chopping the tail, BUT it uses continuous memory blocks. 
     * This makes it much faster for the CPU to read than a scattered LinkedList."
     */
    private Deque<Cell> snakeBody;
    
    /*
     * INTERVIEW BONUS POINT:
     * "This HashSet is a massive optimization. Checking if the snake bit itself by 
     * looping through the whole body takes O(N) time. By mirroring the coordinates 
     * inside this HashSet, I achieve O(1) instant crash detection."
     */
    private Set<Cell> occupiedCells;
    
    private Cell head; // "A quick reference to the front of the snake."

    // "When the game starts, the snake is just 1 block long."
    public Snake(Cell startingPosition) {
        this.snakeBody = new ArrayDeque<>();
        this.occupiedCells = new HashSet<>();
        this.head = startingPosition;
        
        // "Add the starting head to both our queue and our instant-lookup set."
        this.snakeBody.addFirst(head);
        this.occupiedCells.add(head);
    }

    public Cell getHead() { return head; }
    public Set<Cell> getOccupiedCells() { return occupiedCells; }

    /*
     * INTERVIEW EXPLANATION:
     * "This method physically moves the snake. We always put a new block on the front. 
     * If the snake did NOT eat food, we chop the old block off the back so it stays 
     * the same size. If it DID eat food, we skip the chop, and the snake grows!"
     */
    public void move(Cell nextCell, boolean isEatingFood) {
        head = nextCell;
        snakeBody.addFirst(head); // Add to front
        occupiedCells.add(head);  // Add to lookup set

        if (!isEatingFood) {
            // "Chop the tail off in O(1) time."
            Cell tail = snakeBody.removeLast(); 
            occupiedCells.remove(tail); 
        }
    }

    // "O(1) instant crash detection using our HashSet."
    public boolean checkCrashIntoSelf(Cell nextCell) {
        return occupiedCells.contains(nextCell);
    }
}

// ==========================================
// 5. OBSERVER PATTERN (The Walkie-Talkie)
// ==========================================

/*
 * INTERVIEW EXPLANATION:
 * "This interface is the Observer Pattern. The Game backend should never write code 
 * to color pixels on a screen. Instead, the Game uses this interface to shout 'I Updated!' 
 * and the front-end UI will hear it and redraw the pixels itself."
 */
interface GameObserver {
    void onGameUpdated(); // "Called every time the snake takes a step."
    void onGameOver();    // "Called when the snake crashes."
}

// ==========================================
// 6. THE GAME CONTROLLER (The Rules)
// ==========================================

class Game {
    private Board board;
    private Snake snake;
    private FoodFactory foodFactory;
    private GameStatus status;
    private int score;
    private Direction currentDirection;

    /*
     * INTERVIEW BONUS POINT:
     * "This is the Command Pattern. Notice I am using a ConcurrentLinkedQueue instead 
     * of a basic LinkedList. Because we have a background thread running the clock, 
     * and a main UI thread capturing player arrow keys, they might touch this queue 
     * at the exact same millisecond. ConcurrentLinkedQueue prevents thread-crashing."
     */
    private Queue<Direction> inputQueue;

    public Game(Board board, Snake snake, FoodFactory foodFactory) {
        this.board = board;
        this.snake = snake;
        this.foodFactory = foodFactory;
        this.status = GameStatus.READY;
        this.score = 0;
        this.currentDirection = Direction.RIGHT; // "Snake starts moving Right."
        
        // "Thread-safe queue for our arrow keys."
        this.inputQueue = new ConcurrentLinkedQueue<>();
        
        // "Spawn the very first apple on the board."
        this.board.setCurrentFood(this.foodFactory.generateFood(snake.getOccupiedCells()));
    }

    public GameStatus getStatus() { return status; }
    public int getScore() { return score; }
    
    public void startGame() { 
        this.status = GameStatus.RUNNING; 
    }

    // "The UI calls this the millisecond the player hits an arrow key."
    public void addDirectionInput(Direction newDirection) {
        inputQueue.offer(newDirection);
    }

    /*
     * INTERVIEW EXPLANATION:
     * "This is the core engine loop. I designed it as a strict step-by-step checklist. 
     * It returns TRUE if the snake ate food, so the Engine knows to speed up the clock."
     */
    public boolean tick() {
        // "Step 1: If game is dead or paused, do absolutely nothing."
        if (status != GameStatus.RUNNING) return false;

        // "Step 2: Safely get the next direction from our Command Queue."
        Direction nextDirection = getValidNextDirection();
        
        // "Step 3: Calculate the exact square we are about to step on."
        Cell nextCell = getNextCell(snake.getHead(), nextDirection);

        // "Step 4: CRASH DETECTION (Wall)."
        if (board.isOutOfBounds(nextCell)) {
            System.out.println("CRASH! You hit a wall. Final Score: " + score);
            status = GameStatus.GAME_OVER;
            return false;
        }

        // "Step 5: CRASH DETECTION (Self - O(1) time)."
        if (snake.checkCrashIntoSelf(nextCell)) {
            System.out.println("CRASH! You bit your own tail. Final Score: " + score);
            status = GameStatus.GAME_OVER;
            return false;
        }

        // "Step 6: Did we find food?"
        Food currentFood = board.getCurrentFood();
        boolean isEatingFood = false;

        if (nextCell.equals(currentFood.getPosition())) {
            isEatingFood = true;
            score += currentFood.getPoints();
            System.out.println("Yum! Food eaten. Score: " + score);
            
            // "Ask factory to instantly spawn a new apple."
            board.setCurrentFood(foodFactory.generateFood(snake.getOccupiedCells()));
        }

        // "Step 7: Actually move the snake and update our state."
        snake.move(nextCell, isEatingFood);
        this.currentDirection = nextDirection;

        // "Return true if we ate food, so the Engine knows to speed up!"
        return isEatingFood;
    }

    /*
     * INTERVIEW EXPLANATION:
     * "This handles a critical edge case. If the snake is moving RIGHT, the player 
     * cannot instantly press LEFT and cause the snake to bite its own neck. 
     * This method safely pulls from the queue and ignores suicidal inputs."
     */
    private Direction getValidNextDirection() {
        // "If no keys were pressed, just keep going the way we were going."
        if (inputQueue.isEmpty()) return currentDirection;

        Direction requestedDirection = inputQueue.poll();

        // "Ignore suicidal reverse inputs."
        if (currentDirection == Direction.UP && requestedDirection == Direction.DOWN) return currentDirection;
        if (currentDirection == Direction.DOWN && requestedDirection == Direction.UP) return currentDirection;
        if (currentDirection == Direction.LEFT && requestedDirection == Direction.RIGHT) return currentDirection;
        if (currentDirection == Direction.RIGHT && requestedDirection == Direction.LEFT) return currentDirection;

        return requestedDirection;
    }

    // "Simple math helper to find the next coordinate based on direction."
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
    
    // "We define an absolute speed limit. 40ms is 25 frames per second."
    private static final int MIN_TICK_DELAY_MS = 40;
    private int currentTickDelayMs = 200; // "Snake starts slow."
    
    // "The highly-optimized Java tool for running background timer threads."
    private ScheduledExecutorService gameClock;
    
    /*
     * INTERVIEW BONUS POINT:
     * "Because ScheduledExecutorService locks its speed when it starts, we must hold 
     * onto this 'receipt' (ScheduledFuture). When the snake levels up, we use this 
     * receipt to cancel the old timer, and then we create a new, faster timer."
     */
    private ScheduledFuture<?> currentRunningTask;

    public GameEngine(Game game, GameObserver uiObserver) {
        this.game = game;
        this.uiObserver = uiObserver;
        // "Create a background pool with exactly 1 thread to run our clock."
        this.gameClock = Executors.newScheduledThreadPool(1);
    }

    public void start() {
        game.startGame();
        scheduleClock(); // "Fire up the background thread."
    }

    /*
     * INTERVIEW EXPLANATION:
     * "This is the method running in the background. It tells the game to take a step. 
     * If the game says 'I just ate food!', this engine automatically speeds up the clock."
     */
    private void runGameLoop() {
        boolean ateFood = game.tick();

        // "If game is over, tell the UI and shut down this thread permanently."
        if (game.getStatus() == GameStatus.GAME_OVER) {
            uiObserver.onGameOver();
            stop();
            return;
        }

        // "If we ate food, make the game harder by speeding it up!"
        if (ateFood) {
            levelUpSpeed();
        }

        // "Tell the UI to redraw the screen."
        uiObserver.onGameUpdated();
    }

    /*
     * INTERVIEW EXPLANATION:
     * "This implements the asymptotic speed curve and the hard floor. We reduce the 
     * delay by 10% each time, but Math.max guarantees it NEVER drops below 40ms."
     */
    private void levelUpSpeed() {
        int newSpeed = (int) (this.currentTickDelayMs * 0.9);
        this.currentTickDelayMs = Math.max(MIN_TICK_DELAY_MS, newSpeed);
        
        System.out.println("[ENGINE] Speed increased! Next tick in: " + currentTickDelayMs + "ms");
        
        // "Reboot the clock with the new faster speed."
        scheduleClock();
    }

    // "Cancels the old clock receipt, and schedules a new repeating loop."
    private void scheduleClock() {
        if (currentRunningTask != null && !currentRunningTask.isCancelled()) {
            currentRunningTask.cancel(false); // Cancel safely
        }

        currentRunningTask = this.gameClock.scheduleAtFixedRate(
            () -> runGameLoop(), 
            0, 
            this.currentTickDelayMs, 
            TimeUnit.MILLISECONDS
        );
    }

    // "Gracefully shuts down the background thread to prevent memory leaks."
    public void stop() {
        if (this.gameClock != null && !this.gameClock.isShutdown()) {
            this.gameClock.shutdown();
        }
    }
}

// ==========================================
// 8. MAIN EXECUTION (The UI Simulation)
// ==========================================

/*
 * INTERVIEW EXPLANATION:
 * "The Main class acts as our 'Front End UI'. It implements GameObserver so it 
 * can listen to the background Engine over the walkie-talkie and print updates."
 */
public class Main implements GameObserver {
    
    public static void main(String[] args) {
        System.out.println("=== STARTING SNAKE MULTITHREADED SYSTEM ===");

        // 1. Setup Data
        Board board = new Board(10, 10);
        Snake snake = new Snake(new Cell(5, 5));
        FoodFactory foodFactory = new FoodFactory(10, 10);
        
        // 2. Setup the Game Rules
        Game game = new Game(board, snake, foodFactory);
        
        // 3. Setup the UI and the Background Engine
        Main simulatedUI = new Main();
        GameEngine engine = new GameEngine(game, simulatedUI);

        // 4. Start the background clock thread!
        engine.start();

        // SIMULATE PLAYER INPUTS ON THE MAIN THREAD
        try {
            // "Let it run automatically for 500ms (It will move RIGHT twice)"
            Thread.sleep(500);

            System.out.println("\n[PLAYER INPUT] Pressing DOWN key...");
            game.addDirectionInput(Direction.DOWN);

            // "Wait to see the snake turn down."
            Thread.sleep(300);

            System.out.println("\n[PLAYER INPUT] Attempting suicidal UP key...");
            game.addDirectionInput(Direction.UP); // Engine will safely ignore this!
            
            // "Let the snake eventually hit the bottom wall (Row 10) and trigger Game Over."
            Thread.sleep(2000);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // --- OBSERVER INTERFACE IMPLEMENTATIONS ---

    @Override
    public void onGameUpdated() {
        // "In a real app, we would write code here to draw pixels. For now, we print."
        System.out.println("[UI THREAD] Redrawing board...");
    }

    @Override
    public void onGameOver() {
        System.out.println("[UI THREAD] Showing GAME OVER Screen.");
        System.exit(0); // Safely shut down the whole program for the simulation.
    }
}
