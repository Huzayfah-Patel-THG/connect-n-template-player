package com.thg.accelerator23.connectn.ai.stack_over_four;

import com.thehutgroup.accelerator.connectn.player.Board;
import com.thehutgroup.accelerator.connectn.player.Counter;
import com.thehutgroup.accelerator.connectn.player.Player;
import com.thehutgroup.accelerator.connectn.player.Position;

import java.util.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.atomic.AtomicLong;

public class StackOverFour extends Player {
    // Constants
    private static final int MAX_DEPTH = 12;
    private static final long MOVE_TIME_LIMIT_MS = 8000;
    private static final long SAFETY_BUFFER_MS = 2000;
    private static final int THREAT_SCORE = 1000;
    private static final int POSITION_VALUE_SCALE = 10;

    // Memory management
    private static final int TRANSPOSITION_TABLE_SIZE = 400_000;
    private static final int MEMORY_THRESHOLD_MB = 1500;
    private static final int CRITICAL_MEMORY_MB = 1800;
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    // Search tracking
    private static final AtomicLong nodeCount = new AtomicLong(0);
    private int currentMaxDepth = 8;

    // Move ordering
    private final int[][] historyTable;
    private final int[] killerMoves;

    // Caching
    private final Map<String, TranspositionEntry> transpositionTable;

    private static class TranspositionEntry {
        final int depth;
        final int score;
        final int bestMove;
        final long timestamp;

        TranspositionEntry(int depth, int score, int bestMove) {
            this.depth = depth;
            this.score = score;
            this.bestMove = bestMove;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public StackOverFour(Counter counter) {
        super(counter, "StackOverFour");
        this.historyTable = new int[10][8];
        this.killerMoves = new int[MAX_DEPTH];
        this.transpositionTable = new HashMap<>(TRANSPOSITION_TABLE_SIZE);
    }

    @Override
    public int makeMove(Board board) {
        long startTime = System.currentTimeMillis();
        int usedMemoryMB = getUsedMemoryMB();

        if (usedMemoryMB > MEMORY_THRESHOLD_MB) {
            cleanupMemory();
        }

        // Emergency fast move if under severe constraints
        if (usedMemoryMB > CRITICAL_MEMORY_MB || System.currentTimeMillis() - startTime > 1000) {
            return findFastMove(board);
        }

        nodeCount.set(0);
        return iterativeDeepeningSearch(board, startTime);
    }

    private int iterativeDeepeningSearch(Board board, long startTime) {
        int bestMove = board.getConfig().getWidth() / 2;

        for (int depth = 4; depth <= currentMaxDepth && !isTimeExceeded(startTime); depth++) {
            int move = findMoveAtDepth(board, depth, startTime);
            if (!isTimeExceeded(startTime)) {
                bestMove = move;
            }
        }

        return bestMove;
    }

    private int findMoveAtDepth(Board board, int depth, long startTime) {
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;
        int bestScore = Integer.MIN_VALUE;
        int bestMove = board.getConfig().getWidth() / 2;

        int[] moveOrder = getMoveOrder(board);

        for (int col : moveOrder) {
            if (isTimeExceeded(startTime) || !isValidMove(board, col)) continue;

            try {
                Board nextBoard = new Board(board, col, getCounter());
                int score = -negamax(nextBoard, depth - 1, -beta, -alpha, startTime);

                if (score > bestScore) {
                    bestScore = score;
                    bestMove = col;
                }
                alpha = Math.max(alpha, score);

            } catch (Exception e) {
                continue;
            }
        }

        return bestMove;
    }

    private int negamax(Board board, int depth, int alpha, int beta, long startTime) {
        nodeCount.incrementAndGet();

        if (isTimeExceeded(startTime)) {
            return 0;
        }

        String boardHash = getBoardHash(board);
        TranspositionEntry entry = transpositionTable.get(boardHash);
        if (entry != null && entry.depth >= depth) {
            return entry.score;
        }

        if (depth == 0 || isTerminal(board)) {
            return evaluate(board);
        }

        int bestScore = Integer.MIN_VALUE;
        int bestMove = -1;

        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (!isValidMove(board, col)) continue;

            try {
                Board nextBoard = new Board(board, col, getCounter());
                int score = -negamax(nextBoard, depth - 1, -beta, -alpha, startTime);

                if (score > bestScore) {
                    bestScore = score;
                    bestMove = col;
                }
                alpha = Math.max(alpha, score);
                if (alpha >= beta) break;

            } catch (Exception e) {
                continue;
            }
        }

        if (!isTimeExceeded(startTime)) {
            transpositionTable.put(boardHash, new TranspositionEntry(depth, bestScore, bestMove));
        }

        return bestScore;
    }

    private int findFastMove(Board board) {
        // Check for immediate wins
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (!isValidMove(board, col)) continue;
            try {
                Board nextBoard = new Board(board, col, getCounter());
                if (isWinningMove(nextBoard, col)) return col;
            } catch (Exception e) {
                continue;
            }
        }

        // Default to center region
        return board.getConfig().getWidth() / 2;
    }

    private int evaluate(Board board) {
        int score = 0;

        // Evaluate horizontal, vertical, and diagonal lines
        score += evaluateLines(board);

        // Add position-based evaluation
        score += evaluatePositions(board);

        return score;
    }

    private int evaluateLines(Board board) {
        int score = 0;
        Counter player = getCounter();
        Counter opponent = player.getOther();

        // Horizontal
        for (int row = 0; row < board.getConfig().getHeight(); row++) {
            for (int col = 0; col <= board.getConfig().getWidth() - 4; col++) {
                score += evaluateLine(board, col, row, 1, 0, player, opponent);
            }
        }

        // Vertical
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            for (int row = 0; row <= board.getConfig().getHeight() - 4; row++) {
                score += evaluateLine(board, col, row, 0, 1, player, opponent);
            }
        }

        // Diagonals
        for (int row = 0; row <= board.getConfig().getHeight() - 4; row++) {
            for (int col = 0; col <= board.getConfig().getWidth() - 4; col++) {
                score += evaluateLine(board, col, row, 1, 1, player, opponent);
                score += evaluateLine(board, col, row, 1, -1, player, opponent);
            }
        }

        return score;
    }

    private int evaluateLine(Board board, int startX, int startY, int dx, int dy,
                             Counter player, Counter opponent) {
        int playerCount = 0;
        int opponentCount = 0;

        for (int i = 0; i < 4; i++) {
            int x = startX + i * dx;
            int y = startY + i * dy;
            Counter counter = board.getCounterAtPosition(new Position(x, y));

            if (counter == player) playerCount++;
            else if (counter == opponent) opponentCount++;
        }

        if (opponentCount == 0 && playerCount > 0) return (int)Math.pow(10, playerCount);
        if (playerCount == 0 && opponentCount > 0) return -(int)Math.pow(10, opponentCount);
        return 0;
    }

    private int evaluatePositions(Board board) {
        int score = 0;
        Counter player = getCounter();

        for (int row = 0; row < board.getConfig().getHeight(); row++) {
            for (int col = 0; col < board.getConfig().getWidth(); col++) {
                Counter counter = board.getCounterAtPosition(new Position(col, row));
                if (counter == null) continue;

                int posValue = POSITION_VALUE_SCALE * (board.getConfig().getWidth() / 2 - Math.abs(col - board.getConfig().getWidth() / 2));
                score += (counter == player ? posValue : -posValue);
            }
        }

        return score;
    }

    private boolean isWinningMove(Board board, int lastCol) {
        int lastRow = 0;
        while (lastRow < board.getConfig().getHeight() &&
                board.getCounterAtPosition(new Position(lastCol, lastRow)) != null) {
            lastRow++;
        }
        if (lastRow == 0) return false;
        lastRow--;

        Counter player = getCounter();
        return checkLine(board, lastCol, lastRow, 1, 0, player) || // Horizontal
                checkLine(board, lastCol, lastRow, 0, 1, player) || // Vertical
                checkLine(board, lastCol, lastRow, 1, 1, player) || // Diagonal up
                checkLine(board, lastCol, lastRow, 1, -1, player);  // Diagonal down
    }

    private boolean checkLine(Board board, int startX, int startY, int dx, int dy, Counter player) {
        int count = 0;
        for (int i = -3; i <= 3; i++) {
            int x = startX + i * dx;
            int y = startY + i * dy;

            if (!isValidPosition(board, x, y)) continue;

            Counter counter = board.getCounterAtPosition(new Position(x, y));
            if (counter == player) {
                count++;
                if (count >= 4) return true;
            } else {
                count = 0;
            }
        }
        return false;
    }

    private int[] getMoveOrder(Board board) {
        int[] moves = new int[board.getConfig().getWidth()];
        int[] scores = new int[board.getConfig().getWidth()];

        for (int i = 0; i < moves.length; i++) {
            moves[i] = i;
            scores[i] = board.getConfig().getWidth() / 2 - Math.abs(i - board.getConfig().getWidth() / 2);
        }

        // Simple bubble sort based on scores
        for (int i = 0; i < moves.length - 1; i++) {
            for (int j = 0; j < moves.length - i - 1; j++) {
                if (scores[j] < scores[j + 1]) {
                    int tempScore = scores[j];
                    scores[j] = scores[j + 1];
                    scores[j + 1] = tempScore;

                    int tempMove = moves[j];
                    moves[j] = moves[j + 1];
                    moves[j + 1] = tempMove;
                }
            }
        }

        return moves;
    }

    private boolean isTimeExceeded(long startTime) {
        return System.currentTimeMillis() - startTime > (MOVE_TIME_LIMIT_MS - SAFETY_BUFFER_MS);
    }

    private int getUsedMemoryMB() {
        return (int)(memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024));
    }

    private void cleanupMemory() {
        if (getUsedMemoryMB() > CRITICAL_MEMORY_MB) {
            transpositionTable.clear();
            System.gc();
            return;
        }

        long currentTime = System.currentTimeMillis();
        transpositionTable.entrySet().removeIf(e ->
                currentTime - e.getValue().timestamp > 5000 ||
                        transpositionTable.size() > TRANSPOSITION_TABLE_SIZE / 2);
    }

    private String getBoardHash(Board board) {
        StringBuilder hash = new StringBuilder();
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            for (int row = 0; row < board.getConfig().getHeight(); row++) {
                Counter counter = board.getCounterAtPosition(new Position(col, row));
                hash.append(counter == null ? '-' : counter.getStringRepresentation());
            }
        }
        return hash.toString();
    }

    private boolean isValidMove(Board board, int col) {
        return col >= 0 && col < board.getConfig().getWidth() &&
                !board.hasCounterAtPosition(new Position(col, board.getConfig().getHeight() - 1));
    }

    private boolean isValidPosition(Board board, int x, int y) {
        return x >= 0 && x < board.getConfig().getWidth() &&
                y >= 0 && y < board.getConfig().getHeight();
    }

    private boolean isTerminal(Board board) {
        // Check for win
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            for (int row = 0; row < board.getConfig().getHeight(); row++) {
                Counter counter = board.getCounterAtPosition(new Position(col, row));
                if (counter != null) {
                    if (checkLine(board, col, row, 1, 0, counter) ||
                            checkLine(board, col, row, 0, 1, counter) ||
                            checkLine(board, col, row, 1, 1, counter) ||
                            checkLine(board, col, row, 1, -1, counter)) {
                        return true;
                    }
                }
            }
        }

        // Check for full board
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (!board.hasCounterAtPosition(new Position(col, board.getConfig().getHeight() - 1))) {
                return false;
            }
        }

        return true;
    }
}