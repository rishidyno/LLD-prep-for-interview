import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * ULTIMATE CHESS LOW-LEVEL DESIGN (LLD) CHEAT SHEET
 * ============================================================================
 * * DESIGN PATTERNS USED IN THIS CODE:
 * * 1. COMMAND PATTERN
 * - WHAT: It turns an action (like moving a piece) into a standalone object 
 * that holds all the details of that action.
 * - WHERE: The `Move` class.
 * - WHY: Without this, moving a piece just overwrites the board and the history 
 * is lost forever. By saving every action as a `Move` object in a list, 
 * we can easily build an "Undo" button by reading the list backwards.
 * * 2. POLYMORPHISM (Behavioral Pattern / OOP Principle)
 * - WHAT: Allowing different child classes to be treated as their parent class, 
 * but each child does the task in its own unique way.
 * - WHERE: The `Piece` abstract class and its children (Rook, Knight, etc.), 
 * specifically the `canMove()` method.
 * - WHY: Instead of the `Game` class having a massive, ugly `if-else` block 
 * (if piece == knight do this, else if piece == rook do this), the Game 
 * just yells "Move!" and the specific piece knows its own math.
 * * 3. SINGLE RESPONSIBILITY PRINCIPLE (SOLID Principle)
 * - WHAT: A class should have only one reason to change. It should do one job.
 * - WHERE: The `Board` class just holds the grid. The `Game` class just runs 
 * the turns. The `Piece` classes just calculate math.
 * - WHY: If we want to change how a Knight moves, we ONLY touch the `Knight` class. 
 * We don't accidentally break the Board or the Game loop.
 * ============================================================================
 */

// ==========================================
// 1. GAME STATUS (The Rules of State)
// ==========================================

/*
 * INTERVIEW BONUS POINT: 
 * "I am using an Enum for the GameStatus instead of a String. Strings are dangerous 
 * because a typo like 'activ' instead of 'active' will crash the game logic. 
 * Enums guarantee strict type safety."
 */
enum GameStatus {
    ACTIVE, BLACK_WIN, WHITE_WIN, FORFEIT, STALEMATE
}

// ==========================================
// 2. THE BOARD & SQUARES (The Table)
// ==========================================

/*
 * The Square class represents one single tile on the 8x8 chessboard.
 * It only knows two things: Where it is (x, y), and who is sitting on it (Piece).
 */
class Square {
    private int x;
    private int y;
    private Piece piece; // Can be null if the tile is empty

    public Square(int x, int y, Piece piece) {
        this.x = x;
        this.y = y;
        this.piece = piece;
    }

    // Getters and Setters
    public int getX() { return x; }
    public int getY() { return y; }
    public Piece getPiece() { return piece; }
    public void setPiece(Piece piece) { this.piece = piece; }
}

/*
 * The Board class is just the physical table. 
 * It sets up the pieces and checks if a path is physically blocked by other pieces.
 */
class Board {
    Square[][] boxes;

    public Board() {
        this.boxes = new Square[8][8];
        this.resetBoard(); // Automatically put all pieces in their starting spots
    }

    public Square getBox(int x, int y) {
        // Safety check to prevent crashing if a player clicks outside the 8x8 grid
        if (x < 0 || x > 7 || y < 0 || y > 7) {
            return null; 
        }
        return boxes[x][y];
    }

    /*
     * INTERVIEW BONUS POINT:
     * "To prevent pieces like the Queen from teleporting through other pieces, 
     * I wrote this isPathClear method. It mathematically walks step-by-step from 
     * the start square to the end square. If it hits any piece along the way, 
     * it instantly returns false, meaning the path is blocked."
     */
    public boolean isPathClear(int startX, int startY, int endX, int endY) {
        // Integer.compare returns +1 if moving forward, -1 if moving backward, 0 if not moving on that axis
        int xDirection = Integer.compare(endX, startX); 
        int yDirection = Integer.compare(endY, startY);

        // Take the very first step away from the starting square
        int currentX = startX + xDirection;
        int currentY = startY + yDirection;

        // Keep walking step-by-step until we reach the final destination
        while (currentX != endX || currentY != endY) {
            // Look at the square we are standing on right now. Is someone there?
            if (boxes[currentX][currentY].getPiece() != null) {
                return false; // Crash! We hit a piece. The path is NOT clear.
            }
            // Take the next step
            currentX += xDirection;
            currentY += yDirection;
        }
        return true; // We made it to the end without hitting anything!
    }

    // This method physically places all 32 pieces onto their correct starting squares.
    public void resetBoard() {
        // Setup White Major Pieces (Row 0)
        boxes[0][0] = new Square(0, 0, new Rook(true));
        boxes[0][1] = new Square(0, 1, new Knight(true));
        boxes[0][2] = new Square(0, 2, new Bishop(true));
        boxes[0][3] = new Square(0, 3, new Queen(true));
        boxes[0][4] = new Square(0, 4, new King(true));
        boxes[0][5] = new Square(0, 5, new Bishop(true));
        boxes[0][6] = new Square(0, 6, new Knight(true));
        boxes[0][7] = new Square(0, 7, new Rook(true));
        
        // Setup White Pawns (Row 1)
        for (int i = 0; i < 8; i++) {
            boxes[1][i] = new Square(1, i, new Pawn(true));
        }

        // Setup Empty Middle Squares (Rows 2, 3, 4, 5)
        for (int i = 2; i < 6; i++) {
            for (int j = 0; j < 8; j++) {
                boxes[i][j] = new Square(i, j, null);
            }
        }

        // Setup Black Pawns (Row 6)
        for (int i = 0; i < 8; i++) {
            boxes[6][i] = new Square(6, i, new Pawn(false));
        }

        // Setup Black Major Pieces (Row 7)
        boxes[7][0] = new Square(7, 0, new Rook(false));
        boxes[7][1] = new Square(7, 1, new Knight(false));
        boxes[7][2] = new Square(7, 2, new Bishop(false));
        boxes[7][3] = new Square(7, 3, new Queen(false));
        boxes[7][4] = new Square(7, 4, new King(false));
        boxes[7][5] = new Square(7, 5, new Bishop(false));
        boxes[7][6] = new Square(7, 6, new Knight(false));
        boxes[7][7] = new Square(7, 7, new Rook(false));
    }
}

// ==========================================
// 3. THE MOVE HISTORY (Implementation of COMMAND PATTERN)
// ==========================================



/*
 * WHAT THIS IS: The Command Pattern. 
 * WHY IT MATTERS: Instead of just moving a piece and forgetting the past, we create 
 * a "Receipt" of every single move. 
 * INTERVIEW BONUS POINT: "By creating a Move object, I can save it to a database 
 * for game replays, or I can use it to instantly reverse an action for an Undo feature."
 */
class Move {
    private Player player;       // Who made the move?
    private Square start;        // Where did they start?
    private Square end;          // Where did they land?
    private Piece pieceMoved;    // What piece did they move?
    private Piece pieceKilled;   // Did they kill an enemy? (Can be null)

    public Move(Player player, Square start, Square end) {
        this.player = player;
        this.start = start;
        this.end = end;
        this.pieceMoved = start.getPiece(); // The piece sitting on the start square
    }

    public Square getStart() { return start; }
    public Square getEnd() { return end; }
    public Piece getPieceMoved() { return pieceMoved; }
    public Piece getPieceKilled() { return pieceKilled; }
    public void setPieceKilled(Piece pieceKilled) { this.pieceKilled = pieceKilled; }
}

// ==========================================
// 4. THE PIECES (Implementation of POLYMORPHISM)
// ==========================================

/*
 * The base class for every piece on the board. 
 * It is 'abstract' because you cannot hold a generic "Piece" in your hand, 
 * you can only hold a specific child piece (like a Knight).
 */
abstract class Piece {
    private boolean isWhite;  // True if White team, False if Black team
    private boolean isKilled; // True if captured and off the board

    public Piece(boolean isWhite) {
        this.isWhite = isWhite;
        this.isKilled = false; // Everyone starts alive
    }

    public boolean isWhite() { return isWhite; }
    public boolean isKilled() { return isKilled; }
    public void setKilled(boolean killed) { this.isKilled = killed; }

    /*
     * INTERVIEW BONUS POINT:
     * "This abstract method forces every child piece to define its own math rules. 
     * This follows the Open/Closed Principle. If we invent a new piece later, 
     * we just add a new class. We never have to edit our existing Game loop code."
     */
    public abstract boolean canMove(Board board, Square start, Square end);
}

// --- SPECIFIC PIECE IMPLEMENTATIONS ---

class Knight extends Piece {
    public Knight(boolean isWhite) { super(isWhite); }

    @Override
    public boolean canMove(Board board, Square start, Square end) {
        // Universal Rule: You cannot land on a piece from your own team.
        if (end.getPiece() != null && end.getPiece().isWhite() == this.isWhite()) {
            return false;
        }

        // Calculate the physical distance moved on the X and Y axis
        int x = Math.abs(start.getX() - end.getX());
        int y = Math.abs(start.getY() - end.getY());
        
        // Knight math: Moves in an "L" shape (2 steps one way, 1 step the other).
        // Therefore, x * y will ALWAYS equal 2 (either 2*1 or 1*2).
        // Note: Knights can jump over pieces, so we do NOT call board.isPathClear() here!
        return x * y == 2;
    }
}

class Rook extends Piece {
    public Rook(boolean isWhite) { super(isWhite); }

    @Override
    public boolean canMove(Board board, Square start, Square end) {
        // Universal Rule: Cannot land on own team
        if (end.getPiece() != null && end.getPiece().isWhite() == this.isWhite()) return false;

        int xDiff = Math.abs(start.getX() - end.getX());
        int yDiff = Math.abs(start.getY() - end.getY());

        // Rook math: Moves straight. This means either X changes OR Y changes, never both.
        if (xDiff == 0 || yDiff == 0) {
            // Rooks cannot jump! We must ask the board if the road is empty.
            return board.isPathClear(start.getX(), start.getY(), end.getX(), end.getY());
        }
        return false;
    }
}

class Bishop extends Piece {
    public Bishop(boolean isWhite) { super(isWhite); }

    @Override
    public boolean canMove(Board board, Square start, Square end) {
        // Universal Rule: Cannot land on own team
        if (end.getPiece() != null && end.getPiece().isWhite() == this.isWhite()) return false;

        int xDiff = Math.abs(start.getX() - end.getX());
        int yDiff = Math.abs(start.getY() - end.getY());

        // Bishop math: Moves perfectly diagonal. Steps on X must exactly equal steps on Y.
        if (xDiff == yDiff) {
            // Bishops cannot jump! Ask the board if the road is empty.
            return board.isPathClear(start.getX(), start.getY(), end.getX(), end.getY());
        }
        return false;
    }
}

class Queen extends Piece {
    public Queen(boolean isWhite) { super(isWhite); }

    @Override
    public boolean canMove(Board board, Square start, Square end) {
        // Universal Rule: Cannot land on own team
        if (end.getPiece() != null && end.getPiece().isWhite() == this.isWhite()) return false;

        int xDiff = Math.abs(start.getX() - end.getX());
        int yDiff = Math.abs(start.getY() - end.getY());

        // Queen math: She is a combination of a Rook (straight) AND a Bishop (diagonal).
        // We just combine their two rules with an "OR" statement.
        if ((xDiff == 0 || yDiff == 0) || (xDiff == yDiff)) {
            // Queens cannot jump! Ask the board if the road is empty.
            return board.isPathClear(start.getX(), start.getY(), end.getX(), end.getY());
        }
        return false;
    }
}

class King extends Piece {
    public King(boolean isWhite) { super(isWhite); }

    @Override
    public boolean canMove(Board board, Square start, Square end) {
        // Universal Rule: Cannot land on own team
        if (end.getPiece() != null && end.getPiece().isWhite() == this.isWhite()) return false;

        int xDiff = Math.abs(start.getX() - end.getX());
        int yDiff = Math.abs(start.getY() - end.getY());

        // King math: Can move in any direction, but ONLY 1 single step.
        // We do not need board.isPathClear() because he only takes one step. There is no path.
        return xDiff <= 1 && yDiff <= 1;
    }
}

class Pawn extends Piece {
    public Pawn(boolean isWhite) { super(isWhite); }

    @Override
    public boolean canMove(Board board, Square start, Square end) {
        // Universal Rule: Cannot land on own team
        if (end.getPiece() != null && end.getPiece().isWhite() == this.isWhite()) return false;

        // Pawns only move forward, so we need real direction, not absolute values for X.
        int xDiff = end.getX() - start.getX(); 
        int yDiff = Math.abs(start.getY() - end.getY());

        // White pieces start at the bottom and move UP (+1). 
        // Black pieces start at the top and move DOWN (-1).
        int direction = this.isWhite() ? 1 : -1;

        // Scenario A: Normal move. Walking straight ahead 1 step. 
        // Rule: The destination square MUST be completely empty.
        if (xDiff == direction && yDiff == 0 && end.getPiece() == null) {
            return true;
        }
        
        // Scenario B: Attack move. Walking diagonal 1 step.
        // Rule: The destination square MUST have an enemy piece on it.
        if (xDiff == direction && yDiff == 1 && end.getPiece() != null) {
            return true;
        }

        // Note: For advanced LLD, you would add the "move 2 steps on first turn" rule here.
        return false;
    }
}

// ==========================================
// 5. THE PLAYERS (Abstraction)
// ==========================================

/*
 * Abstract player class. Holds shared details. 
 * Will be extended by HumanPlayer and BotPlayer.
 */
abstract class Player {
    private boolean whiteSide;
    public Player(boolean whiteSide) { this.whiteSide = whiteSide; }
    public boolean isWhiteSide() { return whiteSide; }
}

class HumanPlayer extends Player {
    public HumanPlayer(boolean whiteSide) { super(whiteSide); }
}

class BotPlayer extends Player {
    public BotPlayer(boolean whiteSide) { super(whiteSide); }
}

// ==========================================
// 6. THE GAME CONTROLLER (The Brains / Orchestrator)
// ==========================================

/*
 * WHAT THIS IS: The facade or controller. 
 * WHY IT MATTERS: The user interface (or API) only ever talks to this class. 
 * It coordinates the Board, the Players, and the Move History all in one place.
 */
class Game {
    private Player[] players;          // Array holding exactly 2 players
    private Board board;               // The chess table
    private Player currentTurn;        // Whose turn is it right now?
    private GameStatus status;         // Is the game active or over?
    private List<Move> movesPlayed;    // The Command Pattern history list

    public Game(Player p1, Player p2) {
        this.players = new Player[]{p1, p2};
        this.board = new Board();
        this.movesPlayed = new ArrayList<>();
        
        // Chess rules: White always makes the very first move.
        this.currentTurn = p1.isWhiteSide() ? p1 : p2; 
        this.status = GameStatus.ACTIVE;
    }

    public Board getBoard() { return board; }
    public GameStatus getStatus() { return status; }
    public void setStatus(GameStatus status) { this.status = status; }

    /*
     * INTERVIEW BONUS POINT:
     * "This is the core business logic flow. I built it as a strict checklist. 
     * It validates the game state, validates ownership, validates piece math, 
     * executes the move, and updates history. If any check fails, it returns false safely."
     */
    public boolean playerMove(Player player, int startX, int startY, int endX, int endY) {
        
        // Check 1: Is the game actually still running?
        if (this.status != GameStatus.ACTIVE) {
            System.out.println("Move failed. The game is already over.");
            return false;
        }

        // Grab the start and end squares from the board
        Square startSquare = board.getBox(startX, startY);
        Square endSquare = board.getBox(endX, endY);
        
        // Package this action into a Command object right away
        Move move = new Move(player, startSquare, endSquare);

        // Check 2: Did the player click on an empty square?
        Piece sourcePiece = move.getStart().getPiece();
        if (sourcePiece == null) {
            System.out.println("Move failed. There is no piece on the starting square.");
            return false;
        }

        // Check 3: Is the player trying to move the enemy's piece?
        if (player.isWhiteSide() != sourcePiece.isWhite()) {
            System.out.println("Move failed. You cannot move your opponent's piece!");
            return false;
        }

        // Check 4: Is it actually this player's turn?
        if (player != currentTurn) {
            System.out.println("Move failed. Please wait for your turn.");
            return false;
        }

        // Check 5: POLYMORPHISM IN ACTION! 
        // We just ask the piece, "Hey, can you do this math?"
        if (!sourcePiece.canMove(board, move.getStart(), move.getEnd())) {
            System.out.println("Move failed. That piece is not allowed to move there.");
            return false;
        }

        // Check 6: Capture Logic. Is there an enemy piece on the landing square?
        Piece destPiece = move.getEnd().getPiece();
        if (destPiece != null) {
            destPiece.setKilled(true);      // Mark enemy as dead
            move.setPieceKilled(destPiece); // Save the dead enemy in our history receipt!
            
            // If the piece we just killed was the King, the game is over!
            if (destPiece instanceof King) {
                this.setStatus(player.isWhiteSide() ? GameStatus.WHITE_WIN : GameStatus.BLACK_WIN);
                System.out.println("CHECKMATE! Game Over.");
            }
        }

        // EXECUTION: Physically pick up the piece and put it on the new square
        move.getEnd().setPiece(move.getStart().getPiece()); // Put on destination
        move.getStart().setPiece(null);                     // Empty the start square

        // Save the receipt to our history list
        movesPlayed.add(move);

        // Flip the turn to the other player
        this.currentTurn = (this.currentTurn == players[0]) ? players[1] : players[0];
        
        System.out.println("Move successfully completed.");
        return true;
    }

    /*
     * INTERVIEW BONUS POINT:
     * "Because I utilized the Command Pattern to store a List of Move objects, 
     * building an Undo feature takes just 5 lines of code. We pop the last object, 
     * reverse the locations, and resurrect any killed pieces."
     */
    public boolean undoLastMove() {
        // Check if there is actually any history to undo
        if (this.movesPlayed.isEmpty()) {
            System.out.println("Undo failed. No moves have been made yet.");
            return false;
        }

        // Get the very last move from the list and remove it
        Move lastMove = this.movesPlayed.remove(this.movesPlayed.size() - 1);

        // 1. Put the piece that moved BACK onto its starting square
        Piece pieceThatMoved = lastMove.getEnd().getPiece();
        lastMove.getStart().setPiece(pieceThatMoved);

        // 2. Bring the dead enemy piece back to life (if there was one)
        Piece killedPiece = lastMove.getPieceKilled();
        if (killedPiece != null) {
            killedPiece.setKilled(false); // Resurrect
            lastMove.getEnd().setPiece(killedPiece); // Put back on board
            
            // If we just brought a King back to life, the game is no longer over!
            if (killedPiece instanceof King) {
                this.setStatus(GameStatus.ACTIVE); 
            }
        } else {
            // If nobody died, the ending square just becomes empty again
            lastMove.getEnd().setPiece(null);
        }

        // 3. Flip the turn back to the person who just undid their move
        this.currentTurn = (this.currentTurn == players[0]) ? players[1] : players[0];
        
        System.out.println("Undo successfully completed. Turn reverted.");
        return true;
    }
}

// ==========================================
// 7. MAIN EXECUTION (Simulation)
// ==========================================

public class ChessLLD {
    public static void main(String[] args) {
        System.out.println("=== STARTING CHESS LLD SIMULATION ===");
        
        // Create players
        Player p1 = new HumanPlayer(true);  // White Team
        Player p2 = new BotPlayer(false);   // Black Team (AI/Bot)
        
        // Initialize Game
        Game game = new Game(p1, p2);

        // --- SIMULATING MOVES ---

        System.out.println("\n[TURN 1] White moves a Pawn forward:");
        // Moving White Pawn from (1,0) to (2,0)
        game.playerMove(p1, 1, 0, 2, 0); 

        System.out.println("\n[TURN 2] Black moves a Knight (L-shape):");
        // Moving Black Knight from (7,1) to (5,2)
        game.playerMove(p2, 7, 1, 5, 2); 

        System.out.println("\n[UNDO ACTION] Player clicks Undo button to reverse Black's turn:");
        // This will put the Black Knight back and make it Black's turn again
        game.undoLastMove();

        System.out.println("\n[INVALID MOVE CHECK] White tries to jump a Rook through a Pawn:");
        // Rooks cannot jump! This should be caught by our isPathClear() method and fail safely.
        game.playerMove(p1, 0, 0, 3, 0); 
        
        System.out.println("\n=== SIMULATION COMPLETE ===");
    }
}
