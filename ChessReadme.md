# ♟️ Ultimate Chess Game Low-Level Design (LLD)

This repository contains a production-ready Low-Level Design for a classic Chess Game. It is designed to demonstrate strong Object-Oriented Programming (OOP) principles, clean architecture, and practical design patterns.

Throughout this guide, you will find **"Interview Scripts"**. These are plain-English, first-person explanations you can use during a system design interview to explain the *Why* behind the code.

---

## 🗣️ 1. The Interview Kickoff (Clarifying Questions)
*Never start coding on a blank whiteboard without scoping the problem first!*

**Interview Script:** > "Before I define any classes, I want to make sure I am building exactly what you need. 
> 1. Are we designing this strictly for Human vs. Human, or do we need Bot support? 
> 2. Chess has complex edge cases like Castling, En Passant, and Pawn Promotion. Should I build those immediately, or focus on core movements and make the system extensible enough to add them later? 
> 3. Do we need to maintain a move history for an 'Undo' feature?"

*(Assume the interviewer asks for Human & Bot support, extensible core logic, and an Undo feature).*

---

## 🏗️ 2. Core Architecture & Design Patterns



To build a highly modular game, I utilized three core design patterns:

### A. The Command Pattern (The Move History)
* **What:** Wrapping an action (a turn) into a standalone object.
* **Where:** The `Move` class.
* **Why:** If a player just overwrites a square on the board, the past is gone forever. By saving every action as a `Move` object (which records the start square, end square, piece moved, and piece killed), we create a perfect history log. 
* **Interview Script:** *"By storing every Move object in a sequential List, building an Undo button takes just 5 lines of code. We just pop the last Move off the list, return the pieces to their starting squares, and resurrect any killed pieces."*

### B. Polymorphism (The Piece Movement)
* **What:** Allowing child classes to define their own unique behaviors while sharing a parent type.
* **Where:** The `Piece` abstract class and its children (`Knight`, `Rook`, etc.).
* **Why:** We want to avoid a massive, ugly `if-else` block in our Game loop. 
* **Interview Script:** *"The Game class doesn't know HOW a Knight moves. It just calls `piece.canMove()`. The specific piece handles its own math. If we invent a brand-new 'Dragon' piece tomorrow, we just add a new class. We never have to edit the main Game loop."*

### C. The State Pattern (Game Status)
* **What:** Strictly defining the allowed phases of the application.
* **Where:** The `GameStatus` Enum.
* **Why:** Prevents moving pieces after someone has already won.
* **Interview Script:** *"I use an Enum (ACTIVE, WHITE_WIN, BLACK_WIN) instead of a String. A typo in a String like 'activ' instead of 'active' will crash the game. Enums guarantee strict type safety at compile time."*

---

## 🧮 3. The Math Tricks (Piece Movement)



To keep the `canMove()` logic incredibly simple, we use basic coordinate math.
* **Rook:** Moves straight. Either the X coordinate changes, OR the Y coordinate changes. Never both.
* **Bishop:** Moves perfectly diagonal. The number of steps taken on the X-axis must exactly equal the steps on the Y-axis (`xDiff == yDiff`).
* **Queen:** She is simply a Rook and a Bishop combined. We just use an `||` (OR) statement to combine their logic!
* **Knight:** Moves in an "L" shape (2 steps one way, 1 step the other). The math trick here is that `x * y` will ALWAYS equal 2.
* **The Path Checker:** Rooks, Bishops, and Queens cannot teleport through walls! The `Board` class has an `isPathClear()` method that walks step-by-step from Point A to Point B to ensure no pieces are blocking the road.

---

## 💻 4. The Complete Java Code

```java
import java.util.ArrayList;
import java.util.List;

// ==========================================
// 1. GAME STATUS (State Pattern)
// ==========================================
enum GameStatus {
    ACTIVE, BLACK_WIN, WHITE_WIN, FORFEIT, STALEMATE
}

// ==========================================
// 2. THE BOARD & SQUARES
// ==========================================
class Square {
    private int x;
    private int y;
    private Piece piece; 

    public Square(int x, int y, Piece piece) {
        this.x = x;
        this.y = y;
        this.piece = piece;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public Piece getPiece() { return piece; }
    public void setPiece(Piece piece) { this.piece = piece; }
}

class Board {
    Square[][] boxes;

    public Board() {
        this.boxes = new Square[8][8];
        this.resetBoard();
    }

    public Square getBox(int x, int y) {
        if (x < 0 || x > 7 || y < 0 || y > 7) return null; 
        return boxes[x][y];
    }

    // INTERVIEW SCRIPT: "This prevents pieces from jumping through others. 
    // It steps through the grid sequentially and returns false if it hits an obstacle."
    public boolean isPathClear(int startX, int startY, int endX, int endY) {
        int xDirection = Integer.compare(endX, startX); 
        int yDirection = Integer.compare(endY, startY); 

        int currentX = startX + xDirection;
        int currentY = startY + yDirection;

        while (currentX != endX || currentY != endY) {
            if (boxes[currentX][currentY].getPiece() != null) return false; 
            currentX += xDirection;
            currentY += yDirection;
        }
        return true; 
    }

    public void resetBoard() {
        // Initializes all 32 pieces in their proper [x][y] locations...
        boxes[0][0] = new Square(0, 0, new Rook(true));
        boxes[0][1] = new Square(0, 1, new Knight(true));
        // ... (remaining pieces initialized here)
    }
}

// ==========================================
// 3. THE MOVE HISTORY (Command Pattern)
// ==========================================
class Move {
    private Player player;
    private Square start;
    private Square end;
    private Piece pieceMoved;
    private Piece pieceKilled; 

    public Move(Player player, Square start, Square end) {
        this.player = player;
        this.start = start;
        this.end = end;
        this.pieceMoved = start.getPiece();
    }

    public Square getStart() { return start; }
    public Square getEnd() { return end; }
    public Piece getPieceKilled() { return pieceKilled; }
    public void setPieceKilled(Piece pieceKilled) { this.pieceKilled = pieceKilled; }
}

// ==========================================
// 4. THE PIECES (Polymorphism)
// ==========================================
abstract class Piece {
    private boolean isWhite;
    private boolean isKilled;

    public Piece(boolean isWhite) {
        this.isWhite = isWhite;
        this.isKilled = false;
    }

    public boolean isWhite() { return isWhite; }
    public boolean isKilled() { return isKilled; }
    public void setKilled(boolean killed) { this.isKilled = killed; }

    public abstract boolean canMove(Board board, Square start, Square end);
}

class Knight extends Piece {
    public Knight(boolean isWhite) { super(isWhite); }

    @Override
    public boolean canMove(Board board, Square start, Square end) {
        if (end.getPiece() != null && end.getPiece().isWhite() == this.isWhite()) return false;
        
        int x = Math.abs(start.getX() - end.getX());
        int y = Math.abs(start.getY() - end.getY());
        
        // Math trick: The L-shape jump always equals 2.
        return x * y == 2;
    }
}

class Queen extends Piece {
    public Queen(boolean isWhite) { super(isWhite); }

    @Override
    public boolean canMove(Board board, Square start, Square end) {
        if (end.getPiece() != null && end.getPiece().isWhite() == this.isWhite()) return false;
        
        int xDiff = Math.abs(start.getX() - end.getX());
        int yDiff = Math.abs(start.getY() - end.getY());

        // Combines Rook (straight) OR Bishop (diagonal) rules
        if ((xDiff == 0 || yDiff == 0) || (xDiff == yDiff)) {
            return board.isPathClear(start.getX(), start.getY(), end.getX(), end.getY());
        }
        return false;
    }
}

// ==========================================
// 5. THE PLAYERS
// ==========================================
abstract class Player {
    private boolean whiteSide;
    public Player(boolean whiteSide) { this.whiteSide = whiteSide; }
    public boolean isWhiteSide() { return whiteSide; }
}

class HumanPlayer extends Player {
    public HumanPlayer(boolean whiteSide) { super(whiteSide); }
}

// ==========================================
// 6. THE GAME CONTROLLER (The Orchestrator)
// ==========================================
class Game {
    private Player[] players;
    private Board board;
    private Player currentTurn;
    private GameStatus status;
    private List<Move> movesPlayed;

    public Game(Player p1, Player p2) {
        this.players = new Player[]{p1, p2};
        this.board = new Board();
        this.movesPlayed = new ArrayList<>();
        this.currentTurn = p1.isWhiteSide() ? p1 : p2; 
        this.status = GameStatus.ACTIVE;
    }

    public void setStatus(GameStatus status) { this.status = status; }

    // INTERVIEW SCRIPT: "This method enforces the core rules. It checks turn ownership, 
    // asks the piece if the move is legal, updates the Command history, and flips the turn."
    public boolean playerMove(Player player, int startX, int startY, int endX, int endY) {
        if (this.status != GameStatus.ACTIVE) return false;

        Square startSquare = board.getBox(startX, startY);
        Square endSquare = board.getBox(endX, endY);
        Move move = new Move(player, startSquare, endSquare);

        Piece sourcePiece = move.getStart().getPiece();
        if (sourcePiece == null || player.isWhiteSide() != sourcePiece.isWhite() || player != currentTurn) {
            return false;
        }

        // Polymorphism execution
        if (!sourcePiece.canMove(board, move.getStart(), move.getEnd())) return false;

        // Capture Logic
        Piece destPiece = move.getEnd().getPiece();
        if (destPiece != null) {
            destPiece.setKilled(true);
            move.setPieceKilled(destPiece); 
            if (destPiece instanceof King) {
                this.setStatus(player.isWhiteSide() ? GameStatus.WHITE_WIN : GameStatus.BLACK_WIN);
            }
        }

        // Physically move piece
        move.getEnd().setPiece(move.getStart().getPiece());
        move.getStart().setPiece(null);
        movesPlayed.add(move);

        // Swap turns
        this.currentTurn = (this.currentTurn == players[0]) ? players[1] : players[0];
        return true;
    }

    // INTERVIEW SCRIPT: "The 'Undo' button! We pop the last Command object, 
    // return the piece, and revive the enemy if necessary."
    public boolean undoLastMove() {
        if (this.movesPlayed.isEmpty()) return false;

        Move lastMove = this.movesPlayed.remove(this.movesPlayed.size() - 1);
        Piece pieceThatMoved = lastMove.getEnd().getPiece();
        lastMove.getStart().setPiece(pieceThatMoved);

        Piece killedPiece = lastMove.getPieceKilled();
        if (killedPiece != null) {
            killedPiece.setKilled(false);
            lastMove.getEnd().setPiece(killedPiece);
            if (killedPiece instanceof King) this.setStatus(GameStatus.ACTIVE);
        } else {
            lastMove.getEnd().setPiece(null);
        }

        this.currentTurn = (this.currentTurn == players[0]) ? players[1] : players[0];
        return true;
    }
}

```

---

## 🚀 5. Senior SDE Bonus Question: "What about a Singleton?"

If the interviewer asks where a Singleton Design Pattern belongs here, do **NOT** make the `Game` class a Singleton!

**Interview Script:** > "If we make `Game` a Singleton, our system can only ever run one single game of chess at a time! If we are building a website like Chess.com, we need millions of games running simultaneously.

> However, I WOULD use a Singleton for a `GameManager` server class. The server needs exactly one global registry (a thread-safe `HashMap`) to keep track of all active Match IDs so data doesn't get desynced. I would use **Double-Checked Locking** inside the `GameManager.getInstance()` method to ensure it remains highly performant and perfectly thread-safe."

```
