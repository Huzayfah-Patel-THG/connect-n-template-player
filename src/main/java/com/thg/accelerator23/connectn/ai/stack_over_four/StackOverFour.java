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
    private static final long SAFETY_BUFFER_MS = 1500;
    private static final int THREAT_SCORE = 10000;
    private static final int WINNING_SCORE = Integer.MAX_VALUE - 1;
    private static final int[] COLUMN_WEIGHTS = {1, 2, 4, 8, 16, 16, 8, 4, 2, 1};
    private static final int[] ROW_WEIGHTS = {1, 2, 3, 4, 4, 3, 2, 1};
    private static final int CENTER_BONUS = 2000;
    private static final int POSITION_VALUE_SCALE = 100;

    // Pattern recognition scores
    private static final int FOUR_IN_A_ROW = 100000;
    private static final int THREE_IN_A_ROW_OPEN = 5000;
    private static final int THREE_IN_A_ROW_BLOCKED = 100;
    private static final int TWO_IN_A_ROW_OPEN = 50;
    private static final int UNBLOCKED_THREE = 5000;
    private static final int BLOCKED_THREE = 100;

    private static final Runtime runtime = Runtime.getRuntime();

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

    // Pattern matching arrays
    private static final int[][] THREAT_PATTERNS = {
            {1, 1, 1, 0}, {1, 1, 0, 1}, {1, 0, 1, 1}, {0, 1, 1, 1},
            {1, 1, 0, 0}, {0, 0, 1, 1}, {1, 0, 1, 0}, {0, 1, 0, 1}
    };

    private static class TranspositionEntry {
        final int depth;
        final int score;
        final int bestMove;
        final long timestamp;
        final byte flag;

        TranspositionEntry(int depth, int score, int bestMove, byte flag) {
            this.depth = depth;
            this.score = score;
            this.bestMove = bestMove;
            this.flag = flag;
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

        // First move preference for center
        if (isEmptyBoard(board)) {
            return board.getConfig().getWidth() / 2;
        }

        // Check for immediate winning move
        int winningMove = findWinningMove(board, getCounter());
        if (winningMove != -1) return winningMove;

        // Check for immediate defensive move
        int defensiveMove = findWinningMove(board, getCounter().getOther());
        if (defensiveMove != -1) return defensiveMove;

        // Check for fork threats
        int forkMove = findForkMove(board);
        if (forkMove != -1) return forkMove;

        // Emergency fast move if under severe constraints
        if (usedMemoryMB > CRITICAL_MEMORY_MB || System.currentTimeMillis() - startTime > 1000) {
            return findFastMove(board);
        }

        nodeCount.set(0);
        return iterativeDeepeningSearch(board, startTime);
    }

    private boolean isEmptyBoard(Board board) {
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            for (int row = 0; row < board.getConfig().getHeight(); row++) {
                if (board.getCounterAtPosition(new Position(col, row)) != null) {
                    return false;
                }
            }
        }
        return true;
    }

    private int findWinningMove(Board board, Counter player) {
        // Check horizontal, vertical, and diagonals for win/block
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (!isValidMove(board, col)) continue;
            try {
                Board nextBoard = new Board(board, col, player);
                if (checkWin(nextBoard, col, player)) return col;
            } catch (Exception e) {
                continue;
            }
        }
        return -1;
    }

    private boolean checkWin(Board board, int lastCol, Counter player) {
        int row = findLastRow(board, lastCol);

        // Check horizontal
        for (int startCol = Math.max(0, lastCol - 3); startCol <= Math.min(lastCol, board.getConfig().getWidth() - 4); startCol++) {
            int count = 0;
            for (int i = 0; i < 4; i++) {
                if (board.getCounterAtPosition(new Position(startCol + i, row)) == player) count++;
            }
            if (count == 4) return true;
        }

        // Check vertical
        if (row >= 3) {
            int count = 0;
            for (int i = 0; i < 4; i++) {
                if (board.getCounterAtPosition(new Position(lastCol, row - i)) == player) count++;
            }
            if (count == 4) return true;
        }

        // Check both diagonals
        for (int i = -3; i <= 0; i++) {
            int risingCount = 0;
            int fallingCount = 0;
            for (int j = 0; j < 4; j++) {
                int x = lastCol + i + j;
                if (x < 0 || x >= board.getConfig().getWidth()) continue;

                // Rising diagonal
                int y1 = row + i + j;
                if (y1 >= 0 && y1 < board.getConfig().getHeight()) {
                    if (board.getCounterAtPosition(new Position(x, y1)) == player) risingCount++;
                }

                // Falling diagonal
                int y2 = row - i - j;
                if (y2 >= 0 && y2 < board.getConfig().getHeight()) {
                    if (board.getCounterAtPosition(new Position(x, y2)) == player) fallingCount++;
                }
            }
            if (risingCount == 4 || fallingCount == 4) return true;
        }

        return false;
    }

    private int findForkMove(Board board) {
        Counter player = getCounter();
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (!isValidMove(board, col)) continue;
            try {
                Board nextBoard = new Board(board, col, player);
                if (hasMultipleWinningMoves(nextBoard, player)) return col;
            } catch (Exception e) {
                continue;
            }
        }
        return -1;
    }

    private boolean hasMultipleWinningMoves(Board board, Counter player) {
        int threats = 0;
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (!isValidMove(board, col)) continue;
            try {
                Board nextBoard = new Board(board, col, player);
                if (isWinningPosition(nextBoard, col, player) ||
                        evaluateThreatPattern(nextBoard, col, findLastRow(nextBoard, col), player) >= THREE_IN_A_ROW_OPEN) {
                    threats++;
                    if (threats > 1) return true;
                }
            } catch (Exception e) {
                continue;
            }
        }
        return false;
    }

    private int iterativeDeepeningSearch(Board board, long startTime) {
        int bestMove = board.getConfig().getWidth() / 2;
        int lastBestMove = -1;

        for (int depth = 4; depth <= currentMaxDepth && !isTimeExceeded(startTime); depth++) {
            int move = findMoveAtDepth(board, depth, startTime);
            if (!isTimeExceeded(startTime)) {
                bestMove = move;
                if (bestMove == lastBestMove && depth > 6) break;
                lastBestMove = bestMove;
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
                int score = -negamax(nextBoard, depth - 1, -beta, -alpha, getCounter().getOther(), startTime);

                if (score > bestScore) {
                    bestScore = score;
                    bestMove = col;
                }
                alpha = Math.max(alpha, score);
                if (score >= beta) {
                    historyTable[col][depth % board.getConfig().getHeight()] += depth * depth;
                }
            } catch (Exception e) {
                continue;
            }
        }

        return bestMove;
    }

    private int[] getMoveOrder(Board board) {
        int width = board.getConfig().getWidth();
        int[] moves = new int[width];
        int[] scores = new int[width];

        for (int col = 0; col < width; col++) {
            moves[col] = col;
            if (!isValidMove(board, col)) {
                scores[col] = Integer.MIN_VALUE;
                continue;
            }

            scores[col] = 0;
            scores[col] += COLUMN_WEIGHTS[col] * CENTER_BONUS;

            try {
                Board nextBoard = new Board(board, col, getCounter());
                if (isWinningPosition(nextBoard, col, getCounter())) {
                    scores[col] += 100000;
                }

                nextBoard = new Board(board, col, getCounter().getOther());
                if (isWinningPosition(nextBoard, col, getCounter().getOther())) {
                    scores[col] += 90000;
                }

                scores[col] += historyTable[col][board.getConfig().getHeight() % 8];
            } catch (Exception e) {
                continue;
            }
        }

        for (int i = 0; i < width - 1; i++) {
            for (int j = 0; j < width - i - 1; j++) {
                if (scores[j] < scores[j + 1]) {
                    int tempMove = moves[j];
                    moves[j] = moves[j + 1];
                    moves[j + 1] = tempMove;

                    int tempScore = scores[j];
                    scores[j] = scores[j + 1];
                    scores[j + 1] = tempScore;
                }
            }
        }

        return moves;
    }

    private int negamax(Board board, int depth, int alpha, int beta, Counter player, long startTime) {
        nodeCount.incrementAndGet();
        if (isTimeExceeded(startTime)) return 0;

        String boardHash = getBoardHash(board);
        TranspositionEntry entry = transpositionTable.get(boardHash);
        if (entry != null && entry.depth >= depth) {
            if (entry.flag == 0) return entry.score;
            if (entry.flag == 1) alpha = Math.max(alpha, entry.score);
            if (entry.flag == 2) beta = Math.min(beta, entry.score);
            if (alpha >= beta) return entry.score;
        }

        if (isWinningPosition(board, -1, player)) return WINNING_SCORE;
        if (isWinningPosition(board, -1, player.getOther())) return -WINNING_SCORE;

        if (depth == 0) {
            return quiescenceSearch(board, alpha, beta, player, startTime);
        }

        List<Integer> moves = generateMoves(board);
        if (moves.isEmpty()) return evaluatePosition(board, player);

        int bestScore = Integer.MIN_VALUE;
        int bestMove = moves.get(0);

        for (int col : moves) {
            try {
                Board nextBoard = new Board(board, col, player);
                int score = -negamax(nextBoard, depth - 1, -beta, -alpha, player.getOther(), startTime);

                if (score > bestScore) {
                    bestScore = score;
                    bestMove = col;
                }
                alpha = Math.max(alpha, score);
                if (alpha >= beta) {
                    killerMoves[depth] = col;
                    break;
                }
            } catch (Exception e) {
                continue;
            }
        }

        byte flag = 0;
        if (bestScore <= alpha) flag = 2;
        else if (bestScore >= beta) flag = 1;

        if (!isTimeExceeded(startTime)) {
            transpositionTable.put(boardHash, new TranspositionEntry(depth, bestScore, bestMove, flag));
        }

        return bestScore;
    }

    private int quiescenceSearch(Board board, int alpha, int beta, Counter player, long startTime) {
        int standPat = evaluatePosition(board, player);
        if (standPat >= beta) return beta;
        alpha = Math.max(alpha, standPat);

        List<Integer> tacticalMoves = findTacticalMoves(board, player);
        for (int move : tacticalMoves) {
            if (isTimeExceeded(startTime)) break;

            try {
                Board nextBoard = new Board(board, move, player);
                int score = -quiescenceSearch(nextBoard, -beta, -alpha, player.getOther(), startTime);

                if (score >= beta) return beta;
                alpha = Math.max(alpha, score);
            } catch (Exception e) {
                continue;
            }
        }

        return alpha;
    }

    private List<Integer> findTacticalMoves(Board board, Counter player) {
        List<Integer> moves = new ArrayList<>();
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (!isValidMove(board, col)) continue;

            try {
                Board nextBoard = new Board(board, col, player);
                if (isWinningPosition(nextBoard, col, player) ||
                        canCreateThreats(nextBoard, col, player)) {
                    moves.add(col);
                }
            } catch (Exception e) {
                continue;
            }
        }
        return moves;
    }

    private boolean canCreateThreats(Board board, int col, Counter player) {
        int row = findLastRow(board, col);
        return evaluateThreatPattern(board, col, row, player) > THREE_IN_A_ROW_BLOCKED;
    }

    private int evaluateThreatPattern(Board board, int col, int row, Counter player) {
        int maxThreat = 0;
        for (int[] pattern : THREAT_PATTERNS) {
            if (matchesPattern(board, col, row, pattern, player)) {
                maxThreat = Math.max(maxThreat, THREE_IN_A_ROW_OPEN);
            }
        }
        return maxThreat;
    }

    private boolean matchesPattern(Board board, int col, int row, int[] pattern, Counter player) {
        return checkPatternDirection(board, col, row, 1, 0, pattern, player) ||  // horizontal
                checkPatternDirection(board, col, row, 0, 1, pattern, player) ||  // vertical
                checkPatternDirection(board, col, row, 1, 1, pattern, player) ||  // diagonal /
                checkPatternDirection(board, col, row, 1, -1, pattern, player);   // diagonal \
    }

    private boolean checkPatternDirection(Board board, int col, int row, int dx, int dy,
                                          int[] pattern, Counter player) {
        for (int offset = -3; offset <= 0; offset++) {
            boolean matches = true;
            for (int i = 0; i < 4; i++) {
                int x = col + (offset + i) * dx;
                int y = row + (offset + i) * dy;

                if (!isValidPosition(board, x, y)) {
                    matches = false;
                    break;
                }

                Counter current = board.getCounterAtPosition(new Position(x, y));
                if (pattern[i] == 1 && current != player) {
                    matches = false;
                    break;
                }
                if (pattern[i] == 0 && current != null) {
                    matches = false;
                    break;
                }
            }
            if (matches) return true;
        }
        return false;
    }

    private boolean checkWinFromPosition(Board board, int col, int row, Counter player) {
        // Check horizontal
        for (int startCol = Math.max(0, col - 3); startCol <= Math.min(col, board.getConfig().getWidth() - 4); startCol++) {
            if (checkLine(board, startCol, row, 1, 0, player)) return true;
        }

        // Check vertical
        if (row >= 3 && checkLine(board, col, row - 3, 0, 1, player)) return true;

        // Check diagonals
        for (int i = -3; i <= 0; i++) {
            // Rising diagonal /
            if (checkLine(board, col + i, row + i, 1, 1, player)) return true;
            // Falling diagonal \
            if (checkLine(board, col + i, row - i, 1, -1, player)) return true;
        }

        return false;
    }

    private boolean isWinningPosition(Board board, int lastCol, Counter player) {
        if (lastCol == -1) {
            for (int x = 0; x < board.getConfig().getWidth(); x++) {
                for (int y = 0; y < board.getConfig().getHeight(); y++) {
                    if (checkWinFromPosition(board, x, y, player)) {
                        return true;
                    }
                }
            }
            return false;
        }

        int row = findLastRow(board, lastCol);
        return checkWinFromPosition(board, lastCol, row, player);
    }

    private boolean checkLine(Board board, int startX, int startY, int dx, int dy, Counter player) {
        if (!isValidPosition(board, startX, startY)) return false;

        int count = 0;
        for (int i = 0; i < 4; i++) {
            int x = startX + i * dx;
            int y = startY + i * dy;

            if (!isValidPosition(board, x, y)) return false;

            Counter pos = board.getCounterAtPosition(new Position(x, y));
            if (pos == player) {
                count++;
                if (count == 4) return true;
            } else if (pos != null) {
                return false;
            }
        }
        return count == 4;
    }

    private int evaluatePosition(Board board, Counter player) {
        int score = 0;
        Counter opponent = player.getOther();

        // Center control
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            for (int row = 0; row < board.getConfig().getHeight(); row++) {
                Counter counter = board.getCounterAtPosition(new Position(col, row));
                if (counter == player) {
                    score += COLUMN_WEIGHTS[col] * ROW_WEIGHTS[row] * POSITION_VALUE_SCALE;
                } else if (counter == opponent) {
                    score -= COLUMN_WEIGHTS[col] * ROW_WEIGHTS[row] * POSITION_VALUE_SCALE;
                }
            }
        }

        score += evaluateThreats(board, player) - evaluateThreats(board, opponent);
        return score;
    }

    private int evaluateThreats(Board board, Counter player) {
        int threatScore = 0;

        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            for (int row = 0; row < board.getConfig().getHeight(); row++) {
                if (board.getCounterAtPosition(new Position(col, row)) == player) {
                    threatScore += evaluateDirectionalThreat(board, col, row, 1, 0, player);
                    threatScore += evaluateDirectionalThreat(board, col, row, 0, 1, player);
                    threatScore += evaluateDirectionalThreat(board, col, row, 1, 1, player);
                    threatScore += evaluateDirectionalThreat(board, col, row, 1, -1, player);
                }
            }
        }

        return threatScore;
    }

    private int evaluateDirectionalThreat(Board board, int startX, int startY, int dx, int dy, Counter player) {
        int consecutive = 0;
        int openEnds = 0;

        if (isValidPosition(board, startX - dx, startY - dy) &&
                board.getCounterAtPosition(new Position(startX - dx, startY - dy)) == null) {
            openEnds++;
        }

        for (int i = 0; i < 4; i++) {
            int x = startX + (i * dx);
            int y = startY + (i * dy);

            if (!isValidPosition(board, x, y)) break;

            Counter pos = board.getCounterAtPosition(new Position(x, y));
            if (pos == player) {
                consecutive++;
            } else if (pos == null) {
                if (y == 0 || board.hasCounterAtPosition(new Position(x, y - 1))) {
                    openEnds++;
                }
                break;
            } else {
                break;
            }
        }

        if (consecutive == 3) {
            return openEnds > 0 ? UNBLOCKED_THREE : BLOCKED_THREE;
        } else if (consecutive == 2) {
            return openEnds == 2 ? TWO_IN_A_ROW_OPEN : 0;
        }

        return 0;
    }

    private List<Integer> generateMoves(Board board) {
        List<Integer> moves = new ArrayList<>();
        int[] columnOrder = {4, 5, 3, 6, 2, 7, 1, 8, 0, 9};

        for (int col : columnOrder) {
            if (isValidMove(board, col)) {
                moves.add(col);
            }
        }
        return moves;
    }

    private boolean isTimeExceeded(long startTime) {
        return System.currentTimeMillis() - startTime > (MOVE_TIME_LIMIT_MS - SAFETY_BUFFER_MS);
    }

    private int getUsedMemoryMB() {
        return (int)((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
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

    private int findLastRow(Board board, int col) {
        for (int row = 0; row < board.getConfig().getHeight(); row++) {
            if (board.getCounterAtPosition(new Position(col, row)) == null) {
                return row > 0 ? row - 1 : 0;
            }
        }
        return board.getConfig().getHeight() - 1;
    }

    private int findFastMove(Board board) {
        int centerCol = board.getConfig().getWidth() / 2;

        if (isValidMove(board, centerCol)) return centerCol;

        for (int offset = 1; offset <= centerCol; offset++) {
            if (isValidMove(board, centerCol - offset)) return centerCol - offset;
            if (isValidMove(board, centerCol + offset)) return centerCol + offset;
        }

        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (isValidMove(board, col)) return col;
        }

        return centerCol;
    }
}