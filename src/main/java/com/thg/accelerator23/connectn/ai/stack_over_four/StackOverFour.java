package com.thg.accelerator23.connectn.ai.stack_over_four;

import com.thehutgroup.accelerator.connectn.player.Board;
import com.thehutgroup.accelerator.connectn.player.Counter;
import com.thehutgroup.accelerator.connectn.player.Player;
import com.thehutgroup.accelerator.connectn.player.Position;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StackOverFour extends Player {
    private static final int beta = Integer.MAX_VALUE;
    private static final int INITIAL_DEPTH = 4;
    private static int MAX_DEPTH = 12;
    private static long TIME_BUFFER_MS = 1000;
    private static final int THREAT_SCORE = 1000;

    private static final int HISTORY_TABLE_SIZE = 1000;
    private final int[][] historyTable = new int[10][8];  // width x height
    private final int[] killerMoves = new int[MAX_DEPTH];

    private static final int[][] POSITION_VALUES = {
            {3, 4, 5, 7, 7, 7, 5, 4, 3, 3},  // Bottom row
            {4, 6, 8, 10, 10, 10, 8, 6, 4, 4},
            {5, 8, 11, 13, 13, 13, 11, 8, 5, 5},
            {5, 8, 11, 13, 13, 13, 11, 8, 5, 5},
            {4, 6, 8, 10, 10, 10, 8, 6, 4, 4},
            {3, 4, 5, 7, 7, 7, 5, 4, 3, 3},
            {2, 3, 4, 5, 5, 5, 4, 3, 2, 2},
            {1, 2, 3, 4, 4, 4, 3, 2, 1, 1}   // Top row
    };

    private static final int TRANSPOSITION_TABLE_SIZE = 1_000_000;
    private static final class BoardState {
        final int depth;
        final int score;
        final int alpha;
        final int beta;
        final long timestamp;

        BoardState(int depth, int score, int alpha, int beta) {
            this.depth = depth;
            this.score = score;
            this.alpha = alpha;
            this.beta = beta;
            this.timestamp = System.currentTimeMillis();
        }
    }
    private final Map<String, BoardState> transpositionTable = new HashMap<>(TRANSPOSITION_TABLE_SIZE);

    private static final class Pattern {
        final Counter[] sequence;
        final int score;

        Pattern(Counter[] sequence, int score) {
            this.sequence = sequence;
            this.score = score;
        }
    }

    private static final Pattern[] PATTERNS = {
            new Pattern(new Counter[]{Counter.X, Counter.X, Counter.X, null}, 800),
            new Pattern(new Counter[]{Counter.X, Counter.X, null, Counter.X}, 800),
            new Pattern(new Counter[]{Counter.X, null, Counter.X, Counter.X}, 800),
            new Pattern(new Counter[]{null, Counter.X, Counter.X, Counter.X}, 800),
            new Pattern(new Counter[]{Counter.X, Counter.X, null, null}, 400),
            new Pattern(new Counter[]{Counter.X, null, Counter.X, null}, 400),
            new Pattern(new Counter[]{null, Counter.X, Counter.X, null}, 400),
            new Pattern(new Counter[]{Counter.X, null, null, null}, 200)
    };

    private static final int ENDGAME_PIECE_THRESHOLD = 52; // 80% of board filled

    // After existing static fields
    private static long firstMoveStartTime = -1;
    private static int performanceCalibrationDepth = 6;
    private static double avgNodesPerMs = 0;
    private static final int MEMORY_THRESHOLD_MB = 1500;
    private static final Runtime runtime = Runtime.getRuntime();

    private static final class OpponentMove {
        final int column;
        final long timeSpent;
        final boolean wasWinningMove;

        OpponentMove(int column, long timeSpent, boolean wasWinningMove) {
            this.column = column;
            this.timeSpent = timeSpent;
            this.wasWinningMove = wasWinningMove;
        }
    }

    private static int currentMaxDepth = 8;
    private static long timeBuffer = 1000;

    private static final class DatabaseEntry {
        final int bestMove;
        final int score;
        final int depth;
        final long timestamp;

        DatabaseEntry(int bestMove, int score, int depth) {
            this.bestMove = bestMove;
            this.score = score;
            this.depth = depth;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private static final Map<String, DatabaseEntry> positionDatabase = new HashMap<>();
    private static final int CLEANUP_THRESHOLD = 450000;
    private final List<OpponentMove> opponentMoves = new ArrayList<>();
    private long lastOpponentMoveStart = 0;

    private enum OpponentStyle {
        AGGRESSIVE,
        DEFENSIVE,
        BALANCED,
        UNKNOWN
    }
    private OpponentStyle opponentStyle = OpponentStyle.UNKNOWN;

    private boolean isEndgame(Board board) {
        int pieces = 0;
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            for (int row = 0; row < board.getConfig().getHeight(); row++) {
                if (board.getCounterAtPosition(new Position(col, row)) != null) {
                    pieces++;
                }
            }
        }
        return pieces >= ENDGAME_PIECE_THRESHOLD;
    }

    public StackOverFour(Counter counter) {
        super(counter, StackOverFour.class.getName());
    }

    @Override
    public int makeMove(Board board) {
        if (firstMoveStartTime == -1) {
            firstMoveStartTime = System.currentTimeMillis();
            int nodes = calibratePerformance(board);
            avgNodesPerMs = nodes / (System.currentTimeMillis() - firstMoveStartTime);
            adjustSearchParameters();
        }

        if (getUsedMemoryMB() > MEMORY_THRESHOLD_MB) {
            cleanupMemory();
        }

        if (lastOpponentMoveStart > 0) {
            updateOpponentModel(board);
        }
        lastOpponentMoveStart = System.currentTimeMillis();

        String boardKey = getBoardKey(board);
        DatabaseEntry dbEntry = positionDatabase.get(boardKey);
        if (dbEntry != null && dbEntry.depth >= currentMaxDepth) {
            return dbEntry.bestMove;
        }

        return findBestMove(board);
    }

    private int calibratePerformance(Board board) {
        int nodes = 0;
        for (int i = 1; i <= performanceCalibrationDepth; i++) {
            nodes += countNodes(board, i);
        }
        return nodes;
    }

    private void adjustSearchParameters() {
        long availableTime = 7000;
        long expectedNodes = (long)(availableTime * avgNodesPerMs);
        currentMaxDepth = estimateReachableDepth(expectedNodes);
        timeBuffer = Math.max(500, 1000 - (currentMaxDepth * 50));
    }

    private int estimateReachableDepth(long nodes) {
        int depth = 1;
        long estimatedNodes = 1;
        while (estimatedNodes * 7 < nodes && depth < 15) {
            estimatedNodes *= 7;
            depth++;
        }
        return Math.min(depth, 12);
    }

    private void cleanupMemory() {
        long currentTime = System.currentTimeMillis();
        transpositionTable.entrySet().removeIf(e ->
                currentTime - e.getValue().timestamp > 10000 ||
                        transpositionTable.size() > CLEANUP_THRESHOLD);
        System.gc();
    }

    private int getUsedMemoryMB() {
        return (int)((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
    }

    private void updateOpponentModel(Board board) {
        long moveTime = System.currentTimeMillis() - lastOpponentMoveStart;
        boolean wasWinning = false;
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            for (int row = 0; row < board.getConfig().getHeight(); row++) {
                Position pos = new Position(col, row);
                if (board.getCounterAtPosition(pos) == getCounter().getOther()) {
                    wasWinning = evaluatePosition(board) < -THREAT_SCORE;
                    opponentMoves.add(new OpponentMove(col, moveTime, wasWinning));
                    break;
                }
            }
        }

        if (opponentMoves.size() >= 3) {
            updateOpponentStyle();
        }
    }

    private void updateOpponentStyle() {
        int aggressiveCount = 0;
        int defensiveCount = 0;

        for (OpponentMove move : opponentMoves) {
            if (move.wasWinningMove) aggressiveCount++;
            if (move.timeSpent > 3000) defensiveCount++;
        }

        double aggressiveRatio = (double)aggressiveCount / opponentMoves.size();
        double defensiveRatio = (double)defensiveCount / opponentMoves.size();

        if (aggressiveRatio > 0.6) opponentStyle = OpponentStyle.AGGRESSIVE;
        else if (defensiveRatio > 0.6) opponentStyle = OpponentStyle.DEFENSIVE;
        else opponentStyle = OpponentStyle.BALANCED;
    }

    private int findBestMove(Board board) {
        long startTime = System.currentTimeMillis();
        if (isEndgame(board)) {
            MAX_DEPTH = 15; // Search deeper in endgame
            TIME_BUFFER_MS = 500; // Reduce buffer for deeper search
        }
        int bestMove = board.getConfig().getWidth() / 2;

        for (int depth = INITIAL_DEPTH; depth <= MAX_DEPTH; depth++) {
            if (isTimeExceeded(startTime)) break;

            int move = findMoveAtDepth(board, depth, startTime);
            if (!isTimeExceeded(startTime)) {
                bestMove = move;
            }
        }

        return bestMove;
    }

    private int findMoveAtDepth(Board board, int depth, long startTime) {
        int bestScore = Integer.MIN_VALUE;
        int bestMove = board.getConfig().getWidth() / 2;

        int[] moveOrder = getOrderedMoves(board, depth);
        for (int col : moveOrder) {
            if (isTimeExceeded(startTime) || !isColumnNotFull(board, col)) continue;

            try {
                Board nextBoard = new Board(board, col, getCounter());
                int score = minimax(nextBoard, depth, Integer.MIN_VALUE, Integer.MAX_VALUE, false);

                if (score > bestScore) {
                    bestScore = score;
                    bestMove = col;
                }
            } catch (Exception e) {
                continue;
            }
        }

        return bestMove;
    }

    private void updateHistoryAndKillers(int depth, int move, int score, Board board) {
        if (score >= beta) {
            killerMoves[depth] = move;
            historyTable[move][depth % board.getConfig().getHeight()] += depth * depth;
        }
    }

    private int countNodes(Board board, int depth) {
        if (depth == 0) return 1;
        int nodes = 1;
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (!isColumnNotFull(board, col)) continue;
            try {
                Board nextBoard = new Board(board, col, getCounter());
                nodes += countNodes(nextBoard, depth - 1);
            } catch (Exception e) {
                continue;
            }
        }
        return nodes;
    }

    private int minimax(Board board, int depth, int alpha, int beta, boolean maximizing) {
        String boardKey = getBoardKey(board);
        BoardState cached = transpositionTable.get(boardKey);

        if (cached != null && cached.depth >= depth) {
            if (cached.alpha >= beta) return cached.alpha;
            if (cached.beta <= alpha) return cached.beta;
            alpha = Math.max(alpha, cached.alpha);
            beta = Math.min(beta, cached.beta);
        }

        if (depth == 0 || isTerminal(board)) {
            int score = evaluatePosition(board);
            transpositionTable.put(boardKey, new BoardState(depth, score, score, score));
            return score;
        }

        Counter currentPlayer = maximizing ? getCounter() : getCounter().getOther();
        int originalAlpha = alpha;
        int originalBeta = beta;
        int bestScore = maximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (!isColumnNotFull(board, col)) continue;

            try {
                Board nextBoard = new Board(board, col, currentPlayer);
                int score = minimax(nextBoard, depth - 1, alpha, beta, !maximizing);

                bestScore = maximizing ? Math.max(bestScore, score) : Math.min(bestScore, score);
                if (maximizing) alpha = Math.max(alpha, score);
                else beta = Math.min(beta, score);

                if (beta <= alpha) {
                    updateHistoryAndKillers(depth, col, bestScore, board);
                    break;
                }
            } catch (Exception e) {
                continue;
            }
        }

        if (bestScore <= originalAlpha) {
            transpositionTable.put(boardKey, new BoardState(depth, bestScore, Integer.MIN_VALUE, bestScore));
        } else if (bestScore >= originalBeta) {
            transpositionTable.put(boardKey, new BoardState(depth, bestScore, bestScore, Integer.MAX_VALUE));
        } else {
            transpositionTable.put(boardKey, new BoardState(depth, bestScore, bestScore, bestScore));
        }

        return bestScore;
    }

    private String getBoardKey(Board board) {
        StringBuilder key = new StringBuilder();
        for (int x = 0; x < board.getConfig().getWidth(); x++) {
            for (int y = 0; y < board.getConfig().getHeight(); y++) {
                Counter counter = board.getCounterAtPosition(new Position(x, y));
                key.append(counter == null ? '-' : counter.getStringRepresentation());
            }
        }
        return key.toString();
    }

    private int evaluatePosition(Board board) {
        int score = 0;

        // Pattern evaluation
        for (int row = 0; row < board.getConfig().getHeight(); row++) {
            for (int col = 0; col < board.getConfig().getWidth(); col++) {
                score += evaluatePattern(board, col, row, 1, 0);
                score += evaluatePattern(board, col, row, 0, 1);
                score += evaluatePattern(board, col, row, 1, 1);
                score += evaluatePattern(board, col, row, 1, -1);
            }
        }

        // Threat detection
        if (detectThreat(board, getCounter())) score += THREAT_SCORE;
        if (detectThreat(board, getCounter().getOther())) score -= THREAT_SCORE;

        // Add position-based evaluation
        for (int row = 0; row < board.getConfig().getHeight(); row++) {
            for (int col = 0; col < board.getConfig().getWidth(); col++) {
                Counter counter = board.getCounterAtPosition(new Position(col, row));
                if (counter == getCounter()) {
                    score += POSITION_VALUES[row][col];
                } else if (counter == getCounter().getOther()) {
                    score -= POSITION_VALUES[row][col];
                }
            }
        }

        if (isEndgame(board)) {
            score *= 2; // Weight endgame positions more heavily
        }

        return score;
    }

    private int evaluatePattern(Board board, int startX, int startY, int dx, int dy) {
        Counter[] line = new Counter[4];
        for (int i = 0; i < 4; i++) {
            int x = startX + (i * dx);
            int y = startY + (i * dy);
            line[i] = isValidPosition(board, x, y) ? board.getCounterAtPosition(new Position(x, y)) : null;
        }

        int score = 0;
        for (Pattern pattern : PATTERNS) {
            if (matchesPattern(line, pattern.sequence, getCounter())) {
                score += pattern.score;
            }
            if (matchesPattern(line, pattern.sequence, getCounter().getOther())) {
                score -= pattern.score;
            }
        }
        return score;
    }

    private boolean matchesPattern(Counter[] line, Counter[] pattern, Counter player) {
        for (int i = 0; i < 4; i++) {
            if (pattern[i] != null && line[i] != player) return false;
            if (pattern[i] == null && line[i] != null) return false;
        }
        return true;
    }

    private boolean detectThreat(Board board, Counter player) {
        for (int row = 0; row < board.getConfig().getHeight(); row++) {
            for (int col = 0; col <= board.getConfig().getWidth() - 4; col++) {
                if (isThreatenedLine(board, col, row, 1, 0, player)) return true;
            }
        }

        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            for (int row = 0; row <= board.getConfig().getHeight() - 4; row++) {
                if (isThreatenedLine(board, col, row, 0, 1, player)) return true;
            }
        }

        for (int row = 0; row <= board.getConfig().getHeight() - 4; row++) {
            for (int col = 0; col <= board.getConfig().getWidth() - 4; col++) {
                if (isThreatenedLine(board, col, row, 1, 1, player)) return true;
                if (isThreatenedLine(board, col, row + 3, 1, -1, player)) return true;
            }
        }

        return false;
    }

    private boolean isThreatenedLine(Board board, int startX, int startY, int dx, int dy, Counter player) {
        int pieces = 0;
        int emptySpaces = 0;
        Position emptyPos = null;

        for (int i = 0; i < 4; i++) {
            int x = startX + (i * dx);
            int y = startY + (i * dy);
            Position pos = new Position(x, y);

            Counter counter = board.getCounterAtPosition(pos);
            if (counter == player) {
                pieces++;
            } else if (counter == null) {
                emptySpaces++;
                emptyPos = pos;
            }
        }

        return pieces == 3 && emptySpaces == 1 && canPlacePiece(board, emptyPos);
    }

    private boolean canPlacePiece(Board board, Position pos) {
        return pos.getY() == 0 || board.hasCounterAtPosition(new Position(pos.getX(), pos.getY() - 1));
    }

    private int[] getOrderedMoves(Board board, int depth) {
        int width = board.getConfig().getWidth();
        int[] moves = new int[width];
        int[] scores = new int[width];

        // Score moves
        for (int col = 0; col < width; col++) {
            if (!isColumnNotFull(board, col)) {
                scores[col] = Integer.MIN_VALUE;
                continue;
            }

            scores[col] = 0;
            // Killer move bonus
            if (col == killerMoves[depth]) {
                scores[col] += 10000;
            }
            // History table score
            scores[col] += historyTable[col][depth % board.getConfig().getHeight()];

            // Center preference
            scores[col] -= Math.abs(col - width/2) * 100;
        }

        // Sort moves by score
        for (int i = 0; i < width; i++) {
            moves[i] = i;
        }
        for (int i = 0; i < width-1; i++) {
            for (int j = i+1; j < width; j++) {
                if (scores[moves[j]] > scores[moves[i]]) {
                    int temp = moves[i];
                    moves[i] = moves[j];
                    moves[j] = temp;
                }
            }
        }

        return moves;
    }

    private boolean isTimeExceeded(long startTime) {
        return System.currentTimeMillis() - startTime > (8000 - TIME_BUFFER_MS);
    }

    private boolean isTerminal(Board board) {
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            for (int row = 0; row < board.getConfig().getHeight(); row++) {
                if (isWinningPosition(board, col, row)) return true;
            }
        }
        return isBoardFull(board);
    }

    private boolean isWinningPosition(Board board, int startX, int startY) {
        if (checkDirection(board, startX, startY, 1, 0)) return true;
        if (checkDirection(board, startX, startY, 0, 1)) return true;
        if (checkDirection(board, startX, startY, 1, 1)) return true;
        if (checkDirection(board, startX, startY, 1, -1)) return true;

        return false;
    }

    private boolean checkDirection(Board board, int startX, int startY, int dx, int dy) {
        Counter startCounter = board.getCounterAtPosition(new Position(startX, startY));
        if (startCounter == null) return false;

        int count = 1;

        for (int i = 1; i < 4; i++) {
            int x = startX + (i * dx);
            int y = startY + (i * dy);

            if (!isValidPosition(board, x, y)) break;
            if (board.getCounterAtPosition(new Position(x, y)) != startCounter) break;
            count++;
        }

        for (int i = 1; i < 4; i++) {
            int x = startX - (i * dx);
            int y = startY - (i * dy);

            if (!isValidPosition(board, x, y)) break;
            if (board.getCounterAtPosition(new Position(x, y)) != startCounter) break;
            count++;
        }

        return count >= board.getConfig().getnInARowForWin();
    }

    private boolean isValidPosition(Board board, int x, int y) {
        return x >= 0 && x < board.getConfig().getWidth() &&
                y >= 0 && y < board.getConfig().getHeight();
    }

    private boolean isBoardFull(Board board) {
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (isColumnNotFull(board, col)) return false;
        }
        return true;
    }

    private boolean isColumnNotFull(Board board, int col) {
        return !board.hasCounterAtPosition(new Position(col, board.getConfig().getHeight() - 1));
    }
}