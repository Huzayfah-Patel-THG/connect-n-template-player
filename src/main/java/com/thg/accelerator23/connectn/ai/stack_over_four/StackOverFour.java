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
    private static final int POSITION_VALUE_SCALE = 10;
    // Pattern recognition scores
    private static final int FOUR_IN_A_ROW = 100000;
    private static final int THREE_IN_A_ROW_OPEN = 5000;
    private static final int THREE_IN_A_ROW_BLOCKED = 100;
    private static final int TWO_IN_A_ROW_OPEN = 50;

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
        final byte flag; // 0: exact, 1: lower bound, 2: upper bound

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

    private int findWinningMove(Board board, Counter player) {
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (!isValidMove(board, col)) continue;
            try {
                Board nextBoard = new Board(board, col, player);
                if (isWinningPosition(nextBoard, col, player)) return col;
            } catch (Exception e) {
                continue;
            }
        }
        return -1;
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
        int winningMoves = 0;
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (!isValidMove(board, col)) continue;
            try {
                Board nextBoard = new Board(board, col, player);
                if (isWinningPosition(nextBoard, col, player)) {
                    winningMoves++;
                    if (winningMoves > 1) return true;
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
        // Check horizontal
        if (checkPatternDirection(board, col, row, 1, 0, pattern, player)) return true;
        // Check vertical
        if (checkPatternDirection(board, col, row, 0, 1, pattern, player)) return true;
        // Check diagonals
        if (checkPatternDirection(board, col, row, 1, 1, pattern, player)) return true;
        if (checkPatternDirection(board, col, row, 1, -1, pattern, player)) return true;
        return false;
    }

    private boolean checkPatternDirection(Board board, int col, int row, int dx, int dy,
                                          int[] pattern, Counter player) {
        for (int i = -3; i <= 0; i++) {
            boolean matches = true;
            for (int j = 0; j < 4; j++) {
                int x = col + (i + j) * dx;
                int y = row + (i + j) * dy;

                if (!isValidPosition(board, x, y)) {
                    matches = false;
                    break;
                }

                Counter counter = board.getCounterAtPosition(new Position(x, y));
                if (pattern[j] == 1 && counter != player) {
                    matches = false;
                    break;
                }
                if (pattern[j] == 0 && counter != null) {
                    matches = false;
                    break;
                }
            }
            if (matches) return true;
        }
        return false;
    }

    private int evaluatePosition(Board board, Counter player) {
        return evaluateConnections(board, player) +
                evaluateCenter(board, player) +
                evaluateMobility(board, player);
    }

    private int evaluateConnections(Board board, Counter player) {
        int score = 0;
        Counter opponent = player.getOther();

        // Evaluate all possible lines
        for (int row = 0; row < board.getConfig().getHeight(); row++) {
            for (int col = 0; col < board.getConfig().getWidth(); col++) {
                score += evaluateDirection(board, col, row, 1, 0, player, opponent);
                score += evaluateDirection(board, col, row, 0, 1, player, opponent);
                score += evaluateDirection(board, col, row, 1, 1, player, opponent);
                score += evaluateDirection(board, col, row, 1, -1, player, opponent);
            }
        }

        return score;
    }

    private int evaluateDirection(Board board, int startX, int startY, int dx, int dy,
                                  Counter player, Counter opponent) {
        int score = 0;
        int count = 0;
        int spaces = 0;

        for (int i = 0; i < 4; i++) {
            int x = startX + i * dx;
            int y = startY + i * dy;

            if (!isValidPosition(board, x, y)) return 0;

            Counter counter = board.getCounterAtPosition(new Position(x, y));
            if (counter == player) count++;
            else if (counter == null) spaces++;
            else return 0;
        }

        if (count == 3 && spaces == 1) score += THREE_IN_A_ROW_OPEN;
        else if (count == 2 && spaces == 2) score += TWO_IN_A_ROW_OPEN;

        return score;
    }

    private int evaluateCenter(Board board, Counter player) {
        int score = 0;
        int centerCol = board.getConfig().getWidth() / 2;

        for (int row = 0; row < board.getConfig().getHeight(); row++) {
            for (int col = 0; col < board.getConfig().getWidth(); col++) {
                Counter counter = board.getCounterAtPosition(new Position(col, row));
                if (counter == null) continue;

                int value = POSITION_VALUE_SCALE * (4 - Math.abs(col - centerCol));
                score += (counter == player) ? value : -value;
            }
        }

        return score;
    }

    private int evaluateMobility(Board board, Counter player) {
        int mobility = 0;
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (isValidMove(board, col)) {
                mobility++;
                try {
                    Board nextBoard = new Board(board, col, player);
                    if (canCreateThreats(nextBoard, col, player)) {
                        mobility += 2;
                    }
                } catch (Exception e) {
                    continue;
                }
            }
        }
        return mobility * 50;
    }

    private List<Integer> generateMoves(Board board) {
        List<Integer> moves = new ArrayList<>();
        int centerCol = board.getConfig().getWidth() / 2;

        if (isValidMove(board, centerCol)) moves.add(centerCol);

        for (int offset = 1; offset <= centerCol; offset++) {
            int leftCol = centerCol - offset;
            int rightCol = centerCol + offset;

            if (isValidMove(board, leftCol)) moves.add(leftCol);
            if (rightCol < board.getConfig().getWidth() && isValidMove(board, rightCol)) {
                moves.add(rightCol);
            }
        }

        return moves;
    }

    private boolean isWinningPosition(Board board, int lastCol, Counter player) {
        int lastRow = lastCol == -1 ? -1 : findLastRow(board, lastCol);

        for (int row = 0; row < board.getConfig().getHeight(); row++) {
            for (int col = 0; col < board.getConfig().getWidth(); col++) {
                if (lastCol != -1 && (row != lastRow || col != lastCol)) continue;

                if (checkLine(board, col, row, 1, 0, player) || // Horizontal
                        checkLine(board, col, row, 0, 1, player) || // Vertical
                        checkLine(board, col, row, 1, 1, player) || // Diagonal up
                        checkLine(board, col, row, 1, -1, player)) { // Diagonal down
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkLine(Board board, int startX, int startY, int dx, int dy, Counter player) {
        int count = 0;
        for (int i = 0; i < 4; i++) {
            int x = startX + i * dx;
            int y = startY + i * dy;

            if (!isValidPosition(board, x, y)) return false;

            Counter counter = board.getCounterAtPosition(new Position(x, y));
            if (counter != player) return false;

            count++;
        }
        return count == 4;
    }

    private int findLastRow(Board board, int col) {
        for (int row = 0; row < board.getConfig().getHeight(); row++) {
            if (board.getCounterAtPosition(new Position(col, row)) == null) {
                return row > 0 ? row - 1 : 0;
            }
        }
        return board.getConfig().getHeight() - 1;
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
            scores[col] += POSITION_VALUE_SCALE * (4 - Math.abs(col - width/2));

            try {
                Board nextBoard = new Board(board, col, getCounter());
                if (isWinningPosition(nextBoard, col, getCounter())) {
                    scores[col] += 10000;
                }

                nextBoard = new Board(board, col, getCounter().getOther());
                if (isWinningPosition(nextBoard, col, getCounter().getOther())) {
                    scores[col] += 9000;
                }

                scores[col] += historyTable[col][board.getConfig().getHeight() % 8];

                if (col == killerMoves[currentMaxDepth % MAX_DEPTH]) {
                    scores[col] += 5000;
                }
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
