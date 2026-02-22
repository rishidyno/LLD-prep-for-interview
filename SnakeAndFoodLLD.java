import java.util.*;

/**
 * ============================================================================
 * ULTIMATE SNAKE GAME LOW-LEVEL DESIGN (LLD) CHEAT SHEET
 * ============================================================================
 * * DESIGN PATTERNS & DATA STRUCTURES USED:
 * * * 1. COMMAND PATTERN
 * - WHAT: Treating a request (like an arrow key press) as an object and queuing it.
 * - WHERE: The `inputQueue` in the `Game` class.
 * - WHY: If a player presses UP then RIGHT very quickly, a simple variable would 
 * overwrite UP. A Queue ensures we never drop a player's lightning-fast inputs.
 * * * 2. FACTORY PATTERN
 * - WHAT: A dedicated class responsible for creating objects.
 * - WHERE: The `FoodFactory` class.
 * - WHY: If we want to add 10 different types of food (Speed Boost, Poison, etc.) 
 * later, we don't have to rewrite the main Game loop. The factory handles it all.
 * * * 3. STATE PATTERN
 * - WHAT: Using an Enum to strictly control the application's lifecycle.
 * - WHERE: The `GameStatus` enum (READY, RUNNING, PAUSED, GAME_OVER).
 * - WHY: Prevents bugs like the snake continuing to move after hitting a wall.
 * * * 4. ARRAY-DEQUE & HASH-SET (Data Structure Optimization)
 * - WHAT: Using an ArrayDeque for the snake's body, and a HashSet for its occupied cells.
 * - WHERE: The `Snake` class.
 * - WHY: ArrayDeque gives us O(1) time to add a head and remove a tail, and it uses 
 * continuous memory which is faster than a LinkedList. The HashSet gives us O(1) 
 * instant crash detection instead of looping through the whole snake in O(N) time.
 * ============================================================================
 */

// ==========================================
// 1. ENUMS (State & Direction)
// ==========================================

/*
 * INTERVIEW EXPLANATION:
 * "I always use Enums for directions and game states. If we just used Strings, 
 * a simple typo like 'UPP' would crash the system. Enums give us perfect type safety."
 */
enum Direction { UP, DOWN, LEFT, RIGHT }
enum GameStatus { READY, RUNNING, PAUSED, GAME_OVER }

// ==========================================
// 2. THE GRID (Cells & Board)
// ==========================================

/*
 * INTERVIEW EXPLANATION:
 * "The Cell class represents one single x/y coordinate on our grid. It is the core 
 * building block of the board, the food, and the snake's body."
 */
class Cell {
    private int row;
    private int col;

    public Cell(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public int getRow() { return row; }
    public int getCol() { return col; }

    /*
     * INTERVIEW BONUS POINT: 
     * "I am intentionally overriding equals() and hashCode() here. I am doing this 
     * specifically so I can store Cells inside a HashSet later for O(1) crash detection. 
     * Java HashSets rely on these two methods to know if two objects are the same coordinate."
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
 * "The Board class is intentionally kept very simple to follow the Single Responsibility 
 * Principle. It only cares about its physical boundaries and holding the current piece of food."
 */
class Board {
    private int rows;
    private int cols;
    private Food currentFood;

    public Board(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
    }

    public int getRows() { return rows; }
    public int getCols() { return cols; }
    public Food getCurrentFood() { return currentFood; }
    public void setCurrentFood(Food currentFood) { this.currentFood = currentFood; }

    // "A simple helper so the Game loop can quickly check if the snake hit a wall."
    public boolean isOutOfBounds(Cell cell) {
        return cell.getRow() < 0 || cell.getRow() >= rows || 
               cell.getCol() < 0 || cell.getCol() >= cols;
    }
}

// ==========================================
// 3. THE FOOD & FACTORY (Polymorphism & Factory Pattern)
// ==========================================

/*
 * INTERVIEW EXPLANATION:
 * "I am making Food an abstract class. This follows the Open/Closed Principle. 
 * If we want to add 'Golden Apples' tomorrow, I just write a new child class 
 * without modifying my existing, working game code."
 */
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
    public NormalFood(Cell position) { super(position, 10); } // 10 points
}

class BonusFood extends Food {
    public BonusFood(Cell position) { super(position, 50); }  // 50 points
}

/*
 * INTERVIEW EXPLANATION:
 * "This is the Factory Pattern. The main Game loop shouldn't be doing the complex 
 * math to figure out where to spawn an apple. This dedicated factory handles it."
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
     * "Notice how I pass the snake's occupied cells into the factory. This handles 
     * a critical edge case: we absolutely CANNOT spawn food inside the snake's body. 
     * This loop keeps rolling random coordinates until it finds a totally empty square."
     */
    public Food generateFood(Set<Cell> snakeBodyCells) {
        int randomRow, randomCol;
        Cell randomCell;

        do {
            randomRow = randomGenerator.nextInt(boardRows);
            randomCol = randomGenerator.nextInt(boardCols);
            randomCell = new Cell(randomRow, randomCol);
        } while (snakeBodyCells.contains(randomCell));

        // 10% chance for Bonus Food, 90% chance for Normal Food
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
     * "I chose ArrayDeque over a LinkedList for the snake's body. Both give O(1) 
     * insertions and deletions at the ends, but ArrayDeque uses continuous memory. 
     * This is much more CPU cache-friendly and faster in the real world."
     */
    private Deque<Cell> snakeBody;
    
    /*
     * INTERVIEW EXPLANATION:
     * "This HashSet is a massive optimization. Checking if the snake bit itself by 
     * looping through the Deque takes O(N) time. By mirroring the coordinates in 
     * a HashSet, I can check for self-crashes in instant O(1) time."
     */
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

    // "Moves the snake by adding a new head. If we didn't eat, we chop off the tail."
    public void move(Cell nextCell, boolean isEatingFood) {
        head = nextCell;
        snakeBody.addFirst(head);
        occupiedCells.add(head);

        if (!isEatingFood) {
            Cell tail = snakeBody.removeLast(); 
            occupiedCells.remove(tail); 
        }
    }

    // "O(1) instant crash detection."
    public boolean checkCrashIntoSelf(Cell nextCell) {
        return occupiedCells.contains(nextCell);
    }
}

// ==========================================
// 5. THE GAME CONTROLLER (The Orchestrator)
// ==========================================

class Game {
    private Board board;
    private Snake snake;
    private FoodFactory foodFactory;
    private GameStatus status;
    private int score;
    private Direction currentDirection;

    // "The Command Pattern implementation! A queue to hold user inputs."
    private Queue<Direction> inputQueue;

    public Game(Board board, Snake snake, FoodFactory foodFactory) {
        this.board = board;
        this.snake = snake;
        this.foodFactory = foodFactory;
        this.status = GameStatus.READY;
        this.score = 0;
        this.currentDirection = Direction.RIGHT; // Default starting direction
        this.inputQueue = new LinkedList<>();
        
        // Spawn the very first piece of food
        this.board.setCurrentFood(this.foodFactory.generateFood(snake.getOccupiedCells()));
    }

    public GameStatus getStatus() { return status; }
    public int getScore() { return score; }
    
    public void startGame() { 
        this.status = GameStatus.RUNNING; 
        System.out.println("Game started!");
    }

    // "The UI calls this when a player hits an arrow key. We just queue it up."
    public void addDirectionInput(Direction newDirection) {
        inputQueue.offer(newDirection);
    }

    /*
     * INTERVIEW EXPLANATION:
     * "This is the core engine loop. The UI calls this every few milliseconds. 
     * I designed it as a strict 6-step checklist to keep the logic clean."
     */
    public void tick() {
        // Step 1: Ensure game is active
        if (status != GameStatus.RUNNING) return;

        // Step 2: Safely get the next direction from our Command Queue
        Direction nextDirection = getValidNextDirection();
        
        // Step 3: Calculate the exact square we are about to step on
        Cell nextCell = getNextCell(snake.getHead(), nextDirection);

        // Step 4: CRASH DETECTION (Wall)
        if (board.isOutOfBounds(nextCell)) {
            System.out.println("CRASH! You hit a wall.");
            status = GameStatus.GAME_OVER;
            return;
        }

        // Step 5: CRASH DETECTION (Self - O(1) time)
        if (snake.checkCrashIntoSelf(nextCell)) {
            System.out.println("CRASH! You bit your own tail.");
            status = GameStatus.GAME_OVER;
            return;
        }

        // Step 6: Did we find food?
        Food currentFood = board.getCurrentFood();
        boolean isEatingFood = false;

        if (nextCell.equals(currentFood.getPosition())) {
            isEatingFood = true;
            score += currentFood.getPoints();
            System.out.println("Food eaten! Score: " + score);
            
            // Ask factory to instantly spawn a new apple
            board.setCurrentFood(foodFactory.generateFood(snake.getOccupiedCells()));
        }

        // Step 7: Move the snake and update direction state
        snake.move(nextCell, isEatingFood);
        this.currentDirection = nextDirection;
    }

    /*
     * INTERVIEW BONUS POINT:
     * "If the snake is moving RIGHT, it cannot instantly move LEFT and bite its own neck! 
     * This method safely pulls from the queue and ignores suicidal inputs."
     */
    private Direction getValidNextDirection() {
        if (inputQueue.isEmpty()) return currentDirection;

        Direction requestedDirection = inputQueue.poll();

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
// 6. MAIN EXECUTION (Simulation)
// ==========================================

public class SnakeAndFoodLLD {
    public static void main(String[] args) {
        System.out.println("=== STARTING SNAKE GAME SIMULATION ===");

        // 1. Setup a 10x10 Board
        Board board = new Board(10, 10);
        
        // 2. Setup the Snake (Starts at row 5, col 5)
        Snake snake = new Snake(new Cell(5, 5));
        
        // 3. Setup the Food Factory
        FoodFactory foodFactory = new FoodFactory(10, 10);
        
        // 4. Initialize Game Orchestrator
        Game game = new Game(board, snake, foodFactory);
        game.startGame();

        // SIMULATE TICKS AND INPUTS
        
        System.out.println("\n[TICK 1] Moving Right automatically...");
        game.tick(); 

        System.out.println("\n[INPUT] Player rapidly presses UP then LEFT.");
        game.addDirectionInput(Direction.UP);
        game.addDirectionInput(Direction.LEFT);

        System.out.println("\n[TICK 2] Executing first queued command (UP)...");
        game.tick();

        System.out.println("\n[TICK 3] Executing second queued command (LEFT)...");
        game.tick();

        System.out.println("\n[INPUT] Player accidentally presses RIGHT (suicidal move).");
        game.addDirectionInput(Direction.RIGHT);
        
        System.out.println("\n[TICK 4] Game safely ignores suicidal move and continues LEFT...");
        game.tick();
        
        System.out.println("\n=== SIMULATION COMPLETE ===");
    }
}