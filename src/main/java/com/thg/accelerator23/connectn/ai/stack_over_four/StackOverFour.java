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
    private static final int MAX_DEPTH = 12;
    private static final long MOVE_TIME_LIMIT_MS = 8000;
    private static final long SAFETY_BUFFER_MS = 2000;
    private static final int THREAT_SCORE = 1000;
    private static final int POSITION_VALUE_SCALE = 10;

    private static final int TRANSPOSITION_TABLE_SIZE = 400_000;
    private static final int MEMORY_THRESHOLD_MB = 1500;
    private static final int CRITICAL_MEMORY_MB = 1800;
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private static final AtomicLong nodeCount = new AtomicLong(0);

    private static final int[][] THREAT_PATTERNS = {
            {0,1,1,1,0},   // Open four
            {1,1,1,0,1},   // Split four
            {1,1,0,1,1},   // Split four
            {0,1,1,0,1,0}  // Double threat
    };

    private int currentMaxDepth = 8;
    private final int[][] historyTable;
    private final int[] killerMoves;
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

    private static class MoveScore {
        final int col;
        final int score;
        MoveScore(int col, int score) {
            this.col = col;
            this.score = score;
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
                if (isWinningMove(board, move)) break;
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
                int score = -negamax(nextBoard, depth - 1, -beta, -alpha, startTime, true);

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

    private int negamax(Board board, int depth, int alpha, int beta, long startTime, boolean allowNull) {
        nodeCount.incrementAndGet();

        if (isTimeExceeded(startTime)) return 0;

        String boardHash = getBoardHash(board);
        TranspositionEntry entry = transpositionTable.get(boardHash);
        if (entry != null && entry.depth >= depth) {
            return entry.score;
        }

        if (depth <= 0) {
            return quiescenceSearch(board, alpha, beta, startTime);
        }

        if (depth >= 4 && allowNull && !isInCheck(board)) {
            Board nullBoard = board; // No move made
            int nullScore = -negamax(nullBoard, depth - 4, -beta, -beta + 1, startTime, false);
            if (nullScore >= beta) {
                return beta;
            }
        }

        if (isTerminal(board)) {
            return evaluate(board);
        }

        int bestScore = Integer.MIN_VALUE;
        int bestMove = -1;

        for (int col : getMoveOrder(board)) {
            if (!isValidMove(board, col)) continue;

            try {
                Board nextBoard = new Board(board, col, getCounter());
                int score = -negamax(nextBoard, depth - 1, -beta, -alpha, startTime, true);

                if (score > bestScore) {
                    bestScore = score;
                    bestMove = col;
                }
                alpha = Math.max(alpha, score);
                if (alpha >= beta) {
                    updateHistoryTable(col, depth);
                    break;
                }

            } catch (Exception e) {
                continue;
            }
        }

        if (!isTimeExceeded(startTime)) {
            transpositionTable.put(boardHash, new TranspositionEntry(depth, bestScore, bestMove));
        }

        return bestScore;
    }

    private int quiescenceSearch(Board board, int alpha, int beta, long startTime) {
        int standPat = evaluate(board);
        if (isTimeExceeded(startTime)) return standPat;

        if (standPat >= beta) return beta;
        if (alpha < standPat) alpha = standPat;

        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (!isValidMove(board, col)) continue;
            try {
                Board nextBoard = new Board(board, col, getCounter());
                if (isCapturingMove(nextBoard, col)) {
                    int score = -quiescenceSearch(nextBoard, -beta, -alpha, startTime);
                    if (score >= beta) return beta;
                    if (score > alpha) alpha = score;
                }
            } catch (Exception e) {
                continue;
            }
        }
        return alpha;
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

        // Block opponent wins
        Counter opponent = getCounter().getOther();
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (!isValidMove(board, col)) continue;
            try {
                Board nextBoard = new Board(board, col, opponent);
                if (isWinningMove(nextBoard, col)) return col;
            } catch (Exception e) {
                continue;
            }
        }

        // Prefer center columns
        int center = board.getConfig().getWidth() / 2;
        if (isValidMove(board, center)) return center;

        // Try columns near center
        for (int offset = 1; offset < board.getConfig().getWidth() / 2; offset++) {
            if (isValidMove(board, center - offset)) return center - offset;
            if (isValidMove(board, center + offset)) return center + offset;
        }

        // Fall back to first available column
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (isValidMove(board, col)) return col;
        }

        return board.getConfig().getWidth() / 2;
    }

    private boolean isCapturingMove(Board board, int col) {
        return isWinningMove(board, col) || createsThreat(board, col);
    }

    private boolean createsThreat(Board board, int col) {
        Counter player = getCounter();
        for (int[] pattern : THREAT_PATTERNS) {
            if (matchesPattern(board, col, pattern, player)) return true;
        }
        return false;
    }

    private boolean matchesPattern(Board board, int col, int[] pattern, Counter player) {
        int row = getRowForMove(board, col);
        if (row < 0) return false;

        // Check horizontal
        for (int offset = -pattern.length + 1; offset < board.getConfig().getWidth(); offset++) {
            boolean matches = true;
            for (int i = 0; i < pattern.length; i++) {
                int x = col + offset + i;
                if (!isValidPosition(board, x, row)) {
                    matches = false;
                    break;
                }
                Counter counter = board.getCounterAtPosition(new Position(x, row));
                if (pattern[i] == 1 && counter != player) {
                    matches = false;
                    break;
                }
                if (pattern[i] == 0 && counter != null) {
                    matches = false;
                    break;
                }
            }
            if (matches) return true;
        }

        // Check vertical
        if (row >= pattern.length - 1) {
            boolean matches = true;
            for (int i = 0; i < pattern.length; i++) {
                Counter counter = board.getCounterAtPosition(new Position(col, row - i));
                if (pattern[i] == 1 && counter != player) {
                    matches = false;
                    break;
                }
                if (pattern[i] == 0 && counter != null) {
                    matches = false;
                    break;
                }
            }
            if (matches) return true;
        }

        // Check diagonals
        for (int dx : new int[]{-1, 1}) {
            for (int offset = -pattern.length + 1; offset < pattern.length; offset++) {
                boolean matches = true;
                for (int i = 0; i < pattern.length; i++) {
                    int x = col + (offset + i) * dx;
                    int y = row + (offset + i);
                    if (!isValidPosition(board, x, y)) {
                        matches = false;
                        break;
                    }
                    Counter counter = board.getCounterAtPosition(new Position(x, y));
                    if (pattern[i] == 1 && counter != player) {
                        matches = false;
                        break;
                    }
                    if (pattern[i] == 0 && counter != null) {
                        matches = false;
                        break;
                    }
                }
                if (matches) return true;
            }
        }

        return false;
    }

    private int getRowForMove(Board board, int col) {
        for (int row = 0; row < board.getConfig().getHeight(); row++) {
            if (!board.hasCounterAtPosition(new Position(col, row))) {
                return row;
            }
        }
        return -1;
    }

    private boolean isInCheck(Board board) {
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (!isValidMove(board, col)) continue;
            try {
                Board nextBoard = new Board(board, col, getCounter().getOther());
                if (isWinningMove(nextBoard, col)) return true;
            } catch (Exception e) {
                continue;
            }
        }
        return false;
    }

    private void updateHistoryTable(int col, int depth) {
        historyTable[col][depth % 8] += depth * depth;
    }

    private int[] getMoveOrder(Board board) {
        List<MoveScore> moveScores = new ArrayList<>();

        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (!isValidMove(board, col)) continue;

            int score = 0;
            try {
                Board nextBoard = new Board(board, col, getCounter());
                // Winning moves
                if (isWinningMove(nextBoard, col)) score += 10000;
                // Block opponent wins
                Board opponentBoard = new Board(board, col, getCounter().getOther());
                if (isWinningMove(opponentBoard, col)) score += 9000;
                // Threat creation
                if (createsThreat(nextBoard, col)) score += 5000;
                // Center control
                score += 100 * (board.getConfig().getWidth()/2 - Math.abs(col - board.getConfig().getWidth()/2));
                // History heuristic
                score += historyTable[col][0];
                // Killer move bonus
                if (col == killerMoves[0]) score += 3000;

            } catch (Exception e) {
                continue;
            }

            moveScores.add(new MoveScore(col, score));
        }

        moveScores.sort((a, b) -> b.score - a.score);
        return moveScores.stream().mapToInt(ms -> ms.col).toArray();
    }

    private int evaluate(Board board) {
        if (isTerminal(board)) {
            for (int col = 0; col < board.getConfig().getWidth(); col++) {
                for (int row = 0; row < board.getConfig().getHeight(); row++) {
                    Counter counter = board.getCounterAtPosition(new Position(col, row));
                    if (counter != null) {
                        if (isWinningPosition(board, col, row, counter)) {
                            return counter == getCounter() ? Integer.MAX_VALUE / 2 : Integer.MIN_VALUE / 2;
                        }
                    }
                }
            }
            return 0; // Draw
        }

        int score = 0;

        // Position evaluation
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            for (int row = 0; row < board.getConfig().getHeight(); row++) {
                Counter counter = board.getCounterAtPosition(new Position(col, row));
                if (counter == null) continue;

                // Positional value
                int posValue = POSITION_VALUE_SCALE * (board.getConfig().getWidth() / 2 - Math.abs(col - board.getConfig().getWidth() / 2));
                score += counter == getCounter() ? posValue : -posValue;

                // Line evaluations
                score += evaluateLines(board, col, row, counter);
            }
        }

        // Threat evaluation
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (!isValidMove(board, col)) continue;
            try {
                Board nextBoard = new Board(board, col, getCounter());
                if (createsThreat(nextBoard, col)) score += THREAT_SCORE;
            } catch (Exception e) {
                continue;
            }
        }

        return score;
    }

    private int evaluateLines(Board board, int col, int row, Counter counter) {
        int score = 0;
        Counter player = getCounter();
        boolean isPlayer = counter == player;

        // Horizontal
        score += evaluateLine(board, col, row, 1, 0, counter) * (isPlayer ? 1 : -1);
        // Vertical
        score += evaluateLine(board, col, row, 0, 1, counter) * (isPlayer ? 1 : -1);
        // Diagonal 1
        score += evaluateLine(board, col, row, 1, 1, counter) * (isPlayer ? 1 : -1);
        // Diagonal 2
        score += evaluateLine(board, col, row, 1, -1, counter) * (isPlayer ? 1 : -1);

        return score;
    }

    private int evaluateLine(Board board, int startX, int startY, int dx, int dy, Counter counter) {
        int count = 0;
        int maxCount = 0;
        int openEnds = 0;

        // Look backwards
        int x = startX - dx;
        int y = startY - dy;
        if (isValidPosition(board, x, y) && board.getCounterAtPosition(new Position(x, y)) == null) {
            openEnds++;
        }

        // Count sequence
        count = 1;
        x = startX;
        y = startY;
        while (isValidPosition(board, x + dx, y + dy)) {
            x += dx;
            y += dy;
            Counter next = board.getCounterAtPosition(new Position(x, y));
            if (next == counter) {
                count++;
            } else if (next == null) {
                openEnds++;
                break;
            } else {
                break;
            }
        }

        maxCount = Math.max(maxCount, count);

        // Score based on length and openness
        if (maxCount >= 4) return 10000;
        if (maxCount == 3 && openEnds == 2) return 1000;
        if (maxCount == 3 && openEnds == 1) return 100;
        if (maxCount == 2 && openEnds == 2) return 50;
        return maxCount * openEnds * 10;
    }

    private boolean isWinningMove(Board board, int lastCol) {
        int lastRow = getRowForMove(board, lastCol);
        if (lastRow < 0) return false;

        Counter player = getCounter();
        return isWinningPosition(board, lastCol, lastRow, player);
    }

    private boolean isWinningPosition(Board board, int col, int row, Counter player) {
        return checkLine(board, col, row, 1, 0, player) || // Horizontal
                checkLine(board, col, row, 0, 1, player) || // Vertical
                checkLine(board, col, row, 1, 1, player) || // Diagonal up
                checkLine(board, col, row, 1, -1, player);  // Diagonal down
    }

    private boolean checkLine(Board board, int startX, int startY, int dx, int dy, Counter player) {
        int count = 0;
        int x = startX;
        int y = startY;

        while (isValidPosition(board, x, y) &&
                board.getCounterAtPosition(new Position(x, y)) == player) {
            count++;
            if (count >= board.getConfig().getnInARowForWin()) return true;
            x += dx;
            y += dy;
        }

        x = startX - dx;
        y = startY - dy;
        while (isValidPosition(board, x, y) &&
                board.getCounterAtPosition(new Position(x, y)) == player) {
            count++;
            if (count >= board.getConfig().getnInARowForWin()) return true;
            x -= dx;
            y -= dy;
        }

        return false;
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
                if (counter != null && isWinningPosition(board, col, row, counter)) {
                    return true;
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