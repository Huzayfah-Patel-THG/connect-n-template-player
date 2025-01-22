package com.thg.accelerator23.connectn.ai.stack_over_four;

import com.thehutgroup.accelerator.connectn.player.Board;
import com.thehutgroup.accelerator.connectn.player.Counter;
import com.thehutgroup.accelerator.connectn.player.Player;
import com.thehutgroup.accelerator.connectn.player.Position;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class StackOverFour extends Player {
    private static final long ABSOLUTE_LIMIT_MS = 7800;
    private static final long CALIBRATION_LIMIT_MS = 1000;
    private static final long QUICK_MOVE_MS = 500;

    private static final int MAX_DEPTH = 12;
    private static final int MIN_DEPTH = 4;
    private static final int INFINITY = Integer.MAX_VALUE;

    private static final int PIECE_CONNECTION_WEIGHT = 10;
    private static final int MOBILITY_WEIGHT = 5;
    private static final int TERRITORY_WEIGHT = 3;
    private static final int THREAT_WEIGHT = 50;
    private static final int PREVENTION_WEIGHT = 40;
    private static final int CENTER_WEIGHT = 3;
    private static final int[][] DIRECTIONS = {{1,0}, {0,1}, {1,1}, {1,-1}};

    private static final int TRANSPOSITION_TABLE_SIZE = 1_000_000;
    private static final int TRANSPOSITION_ENTRY_LIFETIME = 3;

    private long startTime;
    private int currentDepth;
    private Runtime runtime;
    private Map<Long, TranspositionEntry> transpositionTable;
    private int moveCount;

    private static class TranspositionEntry {
        int depth;
        int value;
        int moveCount;
        int bestMove;
        byte flag;

        TranspositionEntry(int depth, int value, int moveCount, int bestMove, byte flag) {
            this.depth = depth;
            this.value = value;
            this.moveCount = moveCount;
            this.bestMove = bestMove;
            this.flag = flag;
        }
    }

    public StackOverFour(Counter counter) {
        super(counter, "StackOverFour");
        this.currentDepth = MIN_DEPTH;
        this.runtime = Runtime.getRuntime();
        this.transpositionTable = new LinkedHashMap<Long, TranspositionEntry>(
                TRANSPOSITION_TABLE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, TranspositionEntry> eldest) {
                return size() > TRANSPOSITION_TABLE_SIZE;
            }
        };
        this.moveCount = 0;
    }

    private long computeZobristKey(Board board) {
        long key = 17;
        int width = board.getConfig().getWidth();
        int height = board.getConfig().getHeight();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Counter counter = board.getCounterAtPosition(new Position(x, y));
                if (counter != null) {
                    int value = counter == Counter.O ? 1 : 2;
                    key = key * 31 + value * (x + 1) * (y + 1);
                }
            }
        }
        return key;
    }

    @Override
    public int makeMove(Board board) {
        startTime = System.currentTimeMillis();
        moveCount++;

        if (isSimplePosition(board)) {
            return handleSimplePosition(board);
        }

        return performTimedSearch(board);
    }

    private int performTimedSearch(Board board) {
        int bestMove = board.getConfig().getWidth() / 2;
        currentDepth = MIN_DEPTH;
        long zobristKey = computeZobristKey(board);

        while (currentDepth <= MAX_DEPTH && !isTimeExceeded(QUICK_MOVE_MS)) {
            TranspositionEntry entry = transpositionTable.get(zobristKey);
            if (entry != null && entry.depth >= currentDepth &&
                    Math.abs(moveCount - entry.moveCount) <= TRANSPOSITION_ENTRY_LIFETIME) {
                bestMove = entry.bestMove;
                currentDepth++;
                continue;
            }

            int[] moves = getOrderedMoves(board, entry);
            int tempBestMove = evaluateMovesAtDepth(board, moves);

            if (!isTimeExceeded(ABSOLUTE_LIMIT_MS)) {
                bestMove = tempBestMove;
                currentDepth++;
            } else {
                break;
            }
        }

        return bestMove;
    }

    private int[] getOrderedMoves(Board board, TranspositionEntry entry) {
        int width = board.getConfig().getWidth();
        int[] moves = new int[width];
        int index = 0;

        if (entry != null) {
            moves[index++] = entry.bestMove;
        }

        int center = width / 2;
        if (entry == null || entry.bestMove != center) {
            moves[index++] = center;
        }

        for (int offset = 1; offset <= center; offset++) {
            if (center - offset >= 0 && (entry == null || entry.bestMove != center - offset)) {
                moves[index++] = center - offset;
            }
            if (center + offset < width && (entry == null || entry.bestMove != center + offset)) {
                moves[index++] = center + offset;
            }
        }

        return moves;
    }

    private int evaluateMovesAtDepth(Board board, int[] moves) {
        int bestMove = moves[0];
        int bestValue = -INFINITY;
        long zobristKey = computeZobristKey(board);

        for (int move : moves) {
            try {
                Board nextBoard = new Board(board, move, getCounter());
                int value = minimax(nextBoard, currentDepth, -INFINITY, INFINITY, false,
                        computeZobristKey(nextBoard));

                if (value > bestValue) {
                    bestValue = value;
                    bestMove = move;
                }

                if (isTimeExceeded(ABSOLUTE_LIMIT_MS)) break;
            } catch (Exception e) {
                continue;
            }
        }

        transpositionTable.put(zobristKey,
                new TranspositionEntry(currentDepth, bestValue, moveCount, bestMove, (byte)0));

        return bestMove;
    }

    private int minimax(Board board, int depth, int alpha, int beta, boolean maximizing,
                        long zobristKey) {
        TranspositionEntry entry = transpositionTable.get(zobristKey);
        if (entry != null && entry.depth >= depth &&
                Math.abs(moveCount - entry.moveCount) <= TRANSPOSITION_ENTRY_LIFETIME) {
            if (entry.flag == 0) return entry.value;
            if (entry.flag == 1 && entry.value <= alpha) return alpha;
            if (entry.flag == 2 && entry.value >= beta) return beta;
        }

        if (depth == 0 || isTimeExceeded(ABSOLUTE_LIMIT_MS)) {
            return evaluatePosition(board);
        }

        int value = maximizing ? -INFINITY : INFINITY;
        int bestMove = -1;
        int originalAlpha = alpha;
        int originalBeta = beta;

        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            try {
                Board newBoard = new Board(board, col,
                        maximizing ? getCounter() : getCounter().getOther());
                long newKey = computeZobristKey(newBoard);

                int eval = minimax(newBoard, depth - 1, alpha, beta, !maximizing, newKey);

                if (maximizing && eval > value) {
                    value = eval;
                    bestMove = col;
                    alpha = Math.max(alpha, eval);
                } else if (!maximizing && eval < value) {
                    value = eval;
                    bestMove = col;
                    beta = Math.min(beta, eval);
                }

                if (beta <= alpha || isTimeExceeded(ABSOLUTE_LIMIT_MS)) break;

            } catch (Exception e) {
                continue;
            }
        }

        byte flag;
        if (value <= originalAlpha) flag = 1;
        else if (value >= originalBeta) flag = 2;
        else flag = 0;

        transpositionTable.put(zobristKey,
                new TranspositionEntry(depth, value, moveCount, bestMove, flag));

        return value;
    }

    private int evaluatePosition(Board board) {
        int score = 0;

        // Prioritize strategic positions that create multiple threats
        int strategicScore = evaluateStrategicPositions(board);
        if (strategicScore > 0) {
            return strategicScore;
        }

        score += evaluateConnections(board) * PIECE_CONNECTION_WEIGHT;
        score += evaluateTerritory(board) * TERRITORY_WEIGHT;
        score += evaluateThreats(board, getCounter()) * THREAT_WEIGHT;
        score -= evaluateThreats(board, getCounter().getOther()) * PREVENTION_WEIGHT;
        score += evaluateMobility(board) * MOBILITY_WEIGHT;

        return score;
    }

    private int evaluateStrategicPositions(Board board) {
        int width = board.getConfig().getWidth();
        int center = width / 2;

        for (int col = 0; col < width; col++) {
            try {
                Board testBoard = new Board(board, col, getCounter());
                int row = 0;
                while (row < board.getConfig().getHeight() &&
                        board.getCounterAtPosition(new Position(col, row)) != null) {
                    row++;
                }
                if (row >= board.getConfig().getHeight()) continue;

                // Count connected pieces horizontally
                int horizontal = 1;
                // Check left
                for (int x = col-1; x >= 0; x--) {
                    if (testBoard.getCounterAtPosition(new Position(x, row)) == getCounter()) {
                        horizontal++;
                    } else break;
                }
                // Check right
                for (int x = col+1; x < width; x++) {
                    if (testBoard.getCounterAtPosition(new Position(x, row)) == getCounter()) {
                        horizontal++;
                    } else break;
                }

                // Check vertical potential
                int vertical = 1;
                int spaceAbove = 0;
                for (int y = row+1; y < board.getConfig().getHeight(); y++) {
                    if (testBoard.getCounterAtPosition(new Position(col, y)) == null) {
                        spaceAbove++;
                    } else break;
                }

                // High scores for strong positions
                if (horizontal >= 3 && spaceAbove >= 1) return 10000;  // Horizontal threat
                if (horizontal >= 2 && spaceAbove >= 2 && Math.abs(col - center) <= 1) return 8000;  // Strong center
                if (spaceAbove >= 3) return 5000;  // Vertical potential
            } catch (Exception e) {
                continue;
            }
        }
        return 0;
    }

    private int evaluateConnections(Board board) {
        int score = 0;
        int winLength = board.getConfig().getnInARowForWin();

        for (int[] dir : DIRECTIONS) {
            for (int col = 0; col < board.getConfig().getWidth(); col++) {
                for (int row = 0; row < board.getConfig().getHeight(); row++) {
                    Position pos = new Position(col, row);
                    Counter counter = board.getCounterAtPosition(pos);
                    if (counter != null) {
                        int length = countInDirection(board, col, row, dir[0], dir[1], counter);
                        if (length >= 2) {
                            int value = counter == getCounter() ? 1 : -1;
                            score += value * (length * length);
                        }
                    }
                }
            }
        }
        return score;
    }

    private int evaluateTerritory(Board board) {
        int score = 0;
        int center = board.getConfig().getWidth() / 2;
        int height = board.getConfig().getHeight();

        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            int territoryValue = calculateTerritoryValue(col, center);
            for (int row = 0; row < height; row++) {
                Counter counter = board.getCounterAtPosition(new Position(col, row));
                if (counter != null) {
                    int value = counter == getCounter() ? 1 : -1;
                    int heightValue = (height - row);
                    score += value * territoryValue * heightValue;
                }
            }
        }
        return score;
    }

    private int calculateTerritoryValue(int col, int center) {
        int distanceFromCenter = Math.abs(col - center);
        return (int) Math.pow(2, 4 - distanceFromCenter);
    }

    private int evaluateThreats(Board board, Counter counter) {
        int score = 0;
        int winLength = board.getConfig().getnInARowForWin();

        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            try {
                Board testBoard = new Board(board, col, counter);
                if (isWinningMove(testBoard, col)) {
                    score += 1000;
                }
            } catch (Exception e) {
                continue;
            }
        }

        for (int[] dir : DIRECTIONS) {
            for (int col = 0; col < board.getConfig().getWidth(); col++) {
                for (int row = 0; row < board.getConfig().getHeight(); row++) {
                    Position pos = new Position(col, row);
                    if (board.getCounterAtPosition(pos) == counter) {
                        int length = countInDirection(board, col, row, dir[0], dir[1], counter);
                        if (length == winLength - 1) {
                            if (hasSpaceToComplete(board, col, row, dir[0], dir[1], length)) {
                                score += 100;
                            }
                        }
                    }
                }
            }
        }
        return score;
    }

    private boolean hasSpaceToComplete(Board board, int startX, int startY,
                                       int dx, int dy, int length) {
        int x1 = startX - dx;
        int y1 = startY - dy;
        int x2 = startX + dx * length;
        int y2 = startY + dy * length;

        return (isValidPosition(board, x1, y1) &&
                board.getCounterAtPosition(new Position(x1, y1)) == null) ||
                (isValidPosition(board, x2, y2) &&
                        board.getCounterAtPosition(new Position(x2, y2)) == null);
    }

    private int evaluateMobility(Board board) {
        int score = 0;
        int width = board.getConfig().getWidth();

        for (int col = 0; col < width; col++) {
            try {
                Board testBoard = new Board(board, col, getCounter());
                score += 1;

                if (Math.abs(col - width/2) <= 1) {
                    score += 2;
                }
            } catch (Exception e) {
                continue;
            }
        }
        return score;
    }

    private boolean isTimeExceeded(long limit) {
        return System.currentTimeMillis() - startTime > limit;
    }

    private boolean isSimplePosition(Board board) {
        return isEmptyBoard(board) || hasImmediateTacticalMove(board);
    }

    private int handleSimplePosition(Board board) {
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            try {
                Board testBoard = new Board(board, col, getCounter());
                if (isWinningMove(testBoard, col)) {
                    return col;
                }
                testBoard = new Board(board, col, getCounter().getOther());
                if (isWinningMove(testBoard, col)) {
                    return col;
                }
            } catch (Exception e) {
                continue;
            }
        }
        return board.getConfig().getWidth() / 2;
    }

    private boolean hasImmediateTacticalMove(Board board) {
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            try {
                Board testBoard = new Board(board, col, getCounter());
                if (isWinningMove(testBoard, col)) return true;
                testBoard = new Board(board, col, getCounter().getOther());
                if (isWinningMove(testBoard, col)) return true;
            } catch (Exception e) {
                continue;
            }
        }
        return false;
    }

    private boolean isWinningMove(Board board, int col) {
        int row = findLastPieceRow(board, col);
        if (row == -1) return false;

        Counter counter = board.getCounterAtPosition(new Position(col, row));
        if (counter == null) return false;

        for (int[] dir : DIRECTIONS) {
            if (countInDirection(board, col, row, dir[0], dir[1], counter) >=
                    board.getConfig().getnInARowForWin()) {
                return true;
            }
        }
        return false;
    }

    private int findLastPieceRow(Board board, int col) {
        for (int row = 0; row < board.getConfig().getHeight(); row++) {
            if (board.getCounterAtPosition(new Position(col, row)) != null) {
                return row;
            }
        }
        return -1;
    }

    private int countInDirection(Board board, int startX, int startY,
                                 int dx, int dy, Counter counter) {
        int count = 1;

        // Count in positive direction
        int x = startX + dx, y = startY + dy;
        while (isValidPosition(board, x, y) &&
                board.getCounterAtPosition(new Position(x, y)) == counter) {
            count++;
            x += dx;
            y += dy;
        }

        // Count in negative direction
        x = startX - dx;
        y = startY - dy;
        while (isValidPosition(board, x, y) &&
                board.getCounterAtPosition(new Position(x, y)) == counter) {
            count++;
            x -= dx;
            y -= dy;
        }

        return count;
    }

    private boolean isValidPosition(Board board, int x, int y) {
        return x >= 0 && x < board.getConfig().getWidth() &&
                y >= 0 && y < board.getConfig().getHeight();
    }

    private boolean isEmptyBoard(Board board) {
        for (int x = 0; x < board.getConfig().getWidth(); x++) {
            if (board.getCounterAtPosition(new Position(x, 0)) != null) {
                return false;
            }
        }
        return true;
    }
}