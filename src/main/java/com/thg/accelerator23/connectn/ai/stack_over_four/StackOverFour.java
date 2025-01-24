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

    private static final int[][] POSITION_VALUES = {
            {3, 4, 5, 7, 8, 8, 7, 5, 4, 3},
            {4, 6, 8, 10, 11, 11, 10, 8, 6, 4},
            {5, 8, 11, 13, 14, 14, 13, 11, 8, 5},
            {5, 8, 11, 13, 14, 14, 13, 11, 8, 5},
            {4, 6, 8, 10, 11, 11, 10, 8, 6, 4},
            {3, 4, 5, 7, 8, 8, 7, 5, 4, 3},
            {2, 3, 4, 5, 6, 6, 5, 4, 3, 2},
            {1, 2, 3, 4, 5, 5, 4, 3, 2, 1}
    };

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

    private int findFastMove(Board board) {
        // Check for winning moves
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

        return -1;
    }

    @Override
    public int makeMove(Board board) {
        long startTime = System.currentTimeMillis();
        int usedMemoryMB = getUsedMemoryMB();

        if (usedMemoryMB > MEMORY_THRESHOLD_MB) {
            cleanupMemory();
        }

        // Check for immediate winning move or block
        int quickMove = findFastMove(board);
        if (quickMove != -1 && isValidMove(board, quickMove)) {
            return quickMove;
        }

        // If under memory pressure, use simpler strategy
        if (usedMemoryMB > CRITICAL_MEMORY_MB) {
            return findFirstValidMove(board);
        }

        nodeCount.set(0);
        int move = iterativeDeepeningSearch(board, startTime);

        // Validate move and fallback if invalid
        if (!isValidMove(board, move)) {
            return findFirstValidMove(board);
        }

        return move;
    }

    private int findFirstValidMove(Board board) {
        int center = board.getConfig().getWidth() / 2;
        int[] colOrder = new int[board.getConfig().getWidth()];

        int left = center - 1;
        int right = center + 1;
        int idx = 1;

        colOrder[0] = center;
        while (idx < colOrder.length) {
            if (left >= 0) {
                colOrder[idx++] = left--;
            }
            if (right < board.getConfig().getWidth() && idx < colOrder.length) {
                colOrder[idx++] = right++;
            }
        }

        for (int col : colOrder) {
            if (isValidMove(board, col)) {
                return col;
            }
        }

        throw new IllegalStateException("No valid moves available");
    }

    private int iterativeDeepeningSearch(Board board, long startTime) {
        int bestMove = findFirstValidMove(board);

        for (int depth = 4; depth <= currentMaxDepth && !isTimeExceeded(startTime); depth++) {
            int move = findMoveAtDepth(board, depth, startTime);
            if (!isTimeExceeded(startTime) && isValidMove(board, move)) {
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
        int bestMove = findFirstValidMove(board);

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
            Board nullBoard = board;
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

    private boolean isCapturingMove(Board board, int col) {
        return isWinningMove(board, col) || createsThreat(board, col);
    }

    private boolean createsThreat(Board board, int col) {
        if (!isValidMove(board, col)) return false;

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
        if (checkPatternDirection(board, col, row, pattern, player, 1, 0)) return true;

        // Check vertical
        if (row >= pattern.length - 1 &&
                checkPatternDirection(board, col, row, pattern, player, 0, -1)) return true;

        // Check diagonals
        if (checkPatternDirection(board, col, row, pattern, player, 1, 1)) return true;
        if (checkPatternDirection(board, col, row, pattern, player, 1, -1)) return true;

        return false;
    }

    private boolean checkPatternDirection(Board board, int startX, int startY,
                                          int[] pattern, Counter player, int dx, int dy) {
        int patternLength = pattern.length;

        for (int offset = -patternLength + 1; offset <= 0; offset++) {
            boolean matches = true;
            for (int i = 0; i < patternLength; i++) {
                int x = startX + (offset + i) * dx;
                int y = startY + (offset + i) * dy;

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
        return false;
    }

    private int getRowForMove(Board board, int col) {
        if (!isValidMove(board, col)) return -1;

        for (int row = 0; row < board.getConfig().getHeight(); row++) {
            if (!board.hasCounterAtPosition(new Position(col, row))) {
                return row;
            }
        }
        return -1;
    }

    private boolean isInCheck(Board board) {
        Counter opponent = getCounter().getOther();
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (!isValidMove(board, col)) continue;
            try {
                Board nextBoard = new Board(board, col, opponent);
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
                // Position scoring
                score += POSITION_VALUES[0][col];

                // Check winning moves
                Board nextBoard = new Board(board, col, getCounter());
                if (isWinningMove(nextBoard, col)) score += 10000;

                // Block opponent wins
                Board opponentBoard = new Board(board, col, getCounter().getOther());
                if (isWinningMove(opponentBoard, col)) score += 9000;

                // Threat scoring
                if (createsThreat(nextBoard, col)) score += 5000;

                // History and killer moves
                score += historyTable[col][0];
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
                    if (counter != null && isWinningPosition(board, col, row, counter)) {
                        return counter == getCounter() ? 1000000 : -1000000;
                    }
                }
            }
            return 0;
        }

        int score = 0;

        // Position scoring
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            for (int row = 0; row < board.getConfig().getHeight(); row++) {
                Counter counter = board.getCounterAtPosition(new Position(col, row));
                if (counter != null) {
                    score += counter == getCounter() ?
                            POSITION_VALUES[row][col] :
                            -POSITION_VALUES[row][col];
                }
            }
        }

        // Threat scoring
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (!isValidMove(board, col)) continue;
            try {
                Board nextBoard = new Board(board, col, getCounter());
                if (createsThreat(nextBoard, col)) score += THREAT_SCORE;

                Board opponentBoard = new Board(board, col, getCounter().getOther());
                if (createsThreat(opponentBoard, col)) score -= THREAT_SCORE;
            } catch (Exception e) {
                continue;
            }
        }

        return score;
    }

    private boolean isValidMove(Board board, int col) {
        if (col < 0 || col >= board.getConfig().getWidth()) return false;
        return !board.hasCounterAtPosition(new Position(col, board.getConfig().getHeight() - 1));
    }

    private boolean isValidPosition(Board board, int x, int y) {
        return x >= 0 && x < board.getConfig().getWidth() &&
                y >= 0 && y < board.getConfig().getHeight();
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

    private boolean isTerminal(Board board) {
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            for (int row = 0; row < board.getConfig().getHeight(); row++) {
                Counter counter = board.getCounterAtPosition(new Position(col, row));
                if (counter != null && isWinningPosition(board, col, row, counter)) {
                    return true;
                }
            }
        }

        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (isValidMove(board, col)) return false;
        }

        return true;
    }

    private boolean isWinningMove(Board board, int col) {
        int row = getRowForMove(board, col);
        if (row < 0) return false;

        Counter player = getCounter();
        return isWinningPosition(board, col, row, player);
    }

    private boolean isWinningPosition(Board board, int col, int row, Counter player) {
        int required = board.getConfig().getnInARowForWin();

        // Check horizontal
        int count = 1;
        for (int dx = 1; col + dx < board.getConfig().getWidth(); dx++) {
            Position pos = new Position(col + dx, row);
            if (board.getCounterAtPosition(pos) != player) break;
            if (++count >= required) return true;
        }
        for (int dx = 1; col - dx >= 0; dx++) {
            Position pos = new Position(col - dx, row);
            if (board.getCounterAtPosition(pos) != player) break;
            if (++count >= required) return true;
        }

        // Check vertical
        count = 1;
        for (int dy = 1; row + dy < board.getConfig().getHeight(); dy++) {
            Position pos = new Position(col, row + dy);
            if (board.getCounterAtPosition(pos) != player) break;
            if (++count >= required) return true;
        }
        for (int dy = 1; row - dy >= 0; dy++) {
            Position pos = new Position(col, row - dy);
            if (board.getCounterAtPosition(pos) != player) break;
            if (++count >= required) return true;
        }

        // Check diagonal 1
        count = 1;
        for (int d = 1; col + d < board.getConfig().getWidth() && row + d < board.getConfig().getHeight(); d++) {
            Position pos = new Position(col + d, row + d);
            if (board.getCounterAtPosition(pos) != player) break;
            if (++count >= required) return true;
        }
        for (int d = 1; col - d >= 0 && row - d >= 0; d++) {
            Position pos = new Position(col - d, row - d);
            if (board.getCounterAtPosition(pos) != player) break;
            if (++count >= required) return true;
        }

        // Check diagonal 2
        count = 1;
        for (int d = 1; col + d < board.getConfig().getWidth() && row - d >= 0; d++) {
            Position pos = new Position(col + d, row - d);
            if (board.getCounterAtPosition(pos) != player) break;
            if (++count >= required) return true;
        }
        for (int d = 1; col - d >= 0 && row + d < board.getConfig().getHeight(); d++) {
            Position pos = new Position(col - d, row + d);
            if (board.getCounterAtPosition(pos) != player) break;
            if (++count >= required) return true;
        }

        return false;
    }

    private boolean isTimeExceeded(long startTime) {
        return System.currentTimeMillis() - startTime > (MOVE_TIME_LIMIT_MS - SAFETY_BUFFER_MS);
    }
}