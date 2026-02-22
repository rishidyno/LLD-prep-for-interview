import java.util.*;

// --- ENUMS ---
enum PlayerType { HUMAN, BOT }
enum GameState { IN_PROGRESS, ENDED, DRAW }
enum BotDifficultyLevel { EASY, MEDIUM, HARD }

// --- MODELS ---
class PlayingPiece {
    private char symbol;
    public PlayingPiece(char symbol) { this.symbol = symbol; }
    public char getSymbol() { return symbol; }
}

class Cell {
    private int row, col;
    private PlayingPiece piece;

    public Cell(int row, int col) {
        this.row = row;
        this.col = col;
        this.piece = null;
    }
    public boolean isEmpty() { return this.piece == null; }
    public int getRow() { return row; }
    public int getCol() { return col; }
    public PlayingPiece getPiece() { return piece; }
    public void setPiece(PlayingPiece piece) { this.piece = piece; }
}

class Board {
    private int size;
    private Cell[][] grid;

    public Board(int size) {
        this.size = size;
        this.grid = new Cell[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                grid[i][j] = new Cell(i, j);
            }
        }
    }
    public int getSize() { return size; }
    public Cell getCell(int row, int col) { return grid[row][col]; }
    public void printBoard() {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (grid[i][j].isEmpty()) System.out.print(" - ");
                else System.out.print(" " + grid[i][j].getPiece().getSymbol() + " ");
            }
            System.out.println();
        }
        System.out.println();
    }
}

class Move {
    private Cell cell;
    private Player player;
    public Move(Cell cell, Player player) {
        this.cell = cell;
        this.player = player;
    }
    public Cell getCell() { return cell; }
    public Player getPlayer() { return player; }
}

// --- PLAYER HIERARCHY ---
abstract class Player {
    private String name;
    private PlayingPiece piece;
    private PlayerType playerType;

    public Player(String name, PlayingPiece piece, PlayerType playerType) {
        this.name = name;
        this.piece = piece;
        this.playerType = playerType;
    }
    public PlayingPiece getPiece() { return piece; }
    public String getName() { return name; }
    public PlayerType getPlayerType() { return playerType; }
    public abstract Move decideMove(Board board);
}

class HumanPlayer extends Player {
    public HumanPlayer(String name, PlayingPiece piece) {
        super(name, piece, PlayerType.HUMAN);
    }
    @Override
    public Move decideMove(Board board) {
        return null; // Handled externally by Game controller in this simulation
    }
}

class BotPlayer extends Player {
    private BotPlayingStrategy botPlayingStrategy;
    public BotPlayer(String name, PlayingPiece piece, BotDifficultyLevel difficultyLevel, BotPlayingStrategy strategy) {
        super(name, piece, PlayerType.BOT);
        this.botPlayingStrategy = strategy;
    }
    @Override
    public Move decideMove(Board board) {
        return botPlayingStrategy.makeMove(board, this);
    }
}

// --- STRATEGIES ---
interface BotPlayingStrategy { Move makeMove(Board board, Player bot); }

class RandomBotPlayingStrategy implements BotPlayingStrategy {
    @Override
    public Move makeMove(Board board, Player bot) {
        for (int i = 0; i < board.getSize(); i++) {
            for (int j = 0; j < board.getSize(); j++) {
                Cell cell = board.getCell(i, j);
                if (cell.isEmpty()) return new Move(cell, bot);
            }
        }
        return null;
    }
}

interface WinningStrategy { boolean checkWinner(Board board, Move lastMove); }

class OrderOneWinningStrategy implements WinningStrategy {
    private int boardSize;
    private List<Map<Character, Integer>> rowCounts;
    private List<Map<Character, Integer>> colCounts;
    private Map<Character, Integer> mainDiagCounts;
    private Map<Character, Integer> antiDiagCounts;

    public OrderOneWinningStrategy(int boardSize) {
        this.boardSize = boardSize;
        rowCounts = new ArrayList<>();
        colCounts = new ArrayList<>();
        for (int i = 0; i < boardSize; i++) {
            rowCounts.add(new HashMap<>());
            colCounts.add(new HashMap<>());
        }
        mainDiagCounts = new HashMap<>();
        antiDiagCounts = new HashMap<>();
    }

    @Override
    public boolean checkWinner(Board board, Move lastMove) {
        int row = lastMove.getCell().getRow();
        int col = lastMove.getCell().getCol();
        char symbol = lastMove.getPlayer().getPiece().getSymbol();

        rowCounts.get(row).put(symbol, rowCounts.get(row).getOrDefault(symbol, 0) + 1);
        colCounts.get(col).put(symbol, colCounts.get(col).getOrDefault(symbol, 0) + 1);

        if (row == col) mainDiagCounts.put(symbol, mainDiagCounts.getOrDefault(symbol, 0) + 1);
        if (row + col == boardSize - 1) antiDiagCounts.put(symbol, antiDiagCounts.getOrDefault(symbol, 0) + 1);

        return rowCounts.get(row).get(symbol) == boardSize ||
               colCounts.get(col).get(symbol) == boardSize ||
               (row == col && mainDiagCounts.get(symbol) == boardSize) ||
               (row + col == boardSize - 1 && antiDiagCounts.get(symbol) == boardSize);
    }
}

// --- GAME CONTROLLER ---
class Game {
    private Board board;
    private List<Player> players;
    private List<Move> moves;
    private int nextPlayerIndex;
    private GameState gameState;
    private Player winner;
    private WinningStrategy winningStrategy;

    public Game(int dimension, List<Player> players) {
        this.board = new Board(dimension);
        this.players = players;
        this.moves = new ArrayList<>();
        this.nextPlayerIndex = 0;
        this.gameState = GameState.IN_PROGRESS;
        this.winningStrategy = new OrderOneWinningStrategy(dimension);
    }

    public GameState getGameState() { return gameState; }
    public Player getWinner() { return winner; }
    public Board getBoard() { return board; }
    public Player getCurrentPlayer() { return players.get(nextPlayerIndex); }

    public void makeMove(int row, int col) {
        if (gameState != GameState.IN_PROGRESS) return;

        Player currentPlayer = players.get(nextPlayerIndex);
        Move move;

        if (currentPlayer.getPlayerType() == PlayerType.BOT) {
            System.out.println("Bot " + currentPlayer.getName() + " is making a move...");
            move = currentPlayer.decideMove(board);
        } else {
            Cell targetCell = board.getCell(row, col);
            if (!targetCell.isEmpty()) {
                System.out.println("Cell is already occupied! Try again.");
                return; // Don't advance turn
            }
            move = new Move(targetCell, currentPlayer);
        }

        Cell cellToUpdate = move.getCell();
        cellToUpdate.setPiece(currentPlayer.getPiece());
        moves.add(move);

        System.out.println(currentPlayer.getName() + " placed " + 
                           currentPlayer.getPiece().getSymbol() + " at (" + 
                           cellToUpdate.getRow() + ", " + cellToUpdate.getCol() + ")");
        board.printBoard();

        if (winningStrategy.checkWinner(board, move)) {
            gameState = GameState.ENDED;
            winner = currentPlayer;
            return;
        }

        if (moves.size() == board.getSize() * board.getSize()) {
            gameState = GameState.DRAW;
            return;
        }

        nextPlayerIndex = (nextPlayerIndex + 1) % players.size();
    }
}

// --- TicTacToeLLD EXECUTION CLASS ---
public class TicTacToeLLD {
    public static void main(String[] args) {
        System.out.println("Starting Tic-Tac-Toe Game...");

        // Setup Players
        Player p1 = new HumanPlayer("Alice", new PlayingPiece('X'));
        Player p2 = new BotPlayer("RoboBob", new PlayingPiece('O'), 
                                  BotDifficultyLevel.EASY, 
                                  new RandomBotPlayingStrategy());

        List<Player> players = new ArrayList<>();
        players.add(p1);
        players.add(p2);

        // Start a 3x3 Game
        Game game = new Game(3, players);
        game.getBoard().printBoard();

        // Simulating some moves
        // Turn 1: Alice (Human) plays at 0, 0
        game.makeMove(0, 0); 
        
        // Turn 2: RoboBob (Bot) plays (row, col ignored by bot logic)
        game.makeMove(-1, -1); 
        
        // Turn 3: Alice plays at 1, 1
        game.makeMove(1, 1);
        
        // Turn 4: RoboBob plays
        game.makeMove(-1, -1);
        
        // Turn 5: Alice plays at 2, 2 (Should win diagonally)
        game.makeMove(2, 2);

        // Check Results
        if (game.getGameState() == GameState.ENDED) {
            System.out.println("Game Over! The winner is: " + game.getWinner().getName());
        } else if (game.getGameState() == GameState.DRAW) {
            System.out.println("Game Over! It's a draw.");
        }
    }
}