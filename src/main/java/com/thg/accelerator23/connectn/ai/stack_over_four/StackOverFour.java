package com.thg.accelerator23.connectn.ai.stack_over_four;

import com.thehutgroup.accelerator.connectn.player.Board;
import com.thehutgroup.accelerator.connectn.player.Counter;
import com.thehutgroup.accelerator.connectn.player.Player;
import com.thehutgroup.accelerator.connectn.player.Position;

public class StackOverFour extends Player {
    // Core time constants (in milliseconds)
    private static final long ABSOLUTE_LIMIT_MS = 7800;     // Never exceed this
    private static final long CALIBRATION_LIMIT_MS = 1000;  // Max time for calibration
    private static final long QUICK_MOVE_MS = 500;          // Time for simple positions

    // Search parameters
    private static final int MAX_DEPTH = 12;
    private static final int MIN_DEPTH = 4;
    private static final int INFINITY = Integer.MAX_VALUE;
    private static final int CENTER_WEIGHT = 3;
    private static final int[][] DIRECTIONS = {{1,0}, {0,1}, {1,1}, {1,-1}};

    // Instance variables for performance tracking
    private long startTime;
    private int currentDepth;
    private Runtime runtime;

    public StackOverFour(Counter counter) {
        super(counter, "StackOverFour");
        this.currentDepth = MIN_DEPTH;
        this.runtime = Runtime.getRuntime();
    }

    @Override
    public int makeMove(Board board) {
        startTime = System.currentTimeMillis();

        // Handle simple positions quickly
        if (isSimplePosition(board)) {
            return handleSimplePosition(board);
        }

        // Progressive deepening with strict time management
        return performTimedSearch(board);
    }

    private boolean isSimplePosition(Board board) {
        // Empty or near-empty board, or tactical position
        return isEmptyBoard(board) || hasImmediateTacticalMove(board);
    }

    private int handleSimplePosition(Board board) {
        // Check for immediate wins or blocks
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            try {
                // Check for win
                Board testBoard = new Board(board, col, getCounter());
                if (isWinningMove(testBoard, col)) {
                    return col;
                }
                // Check for block
                testBoard = new Board(board, col, getCounter().getOther());
                if (isWinningMove(testBoard, col)) {
                    return col;
                }
            } catch (Exception e) {
                continue;
            }
        }

        // For empty board, return center column
        return board.getConfig().getWidth() / 2;
    }

    private int performTimedSearch(Board board) {
        int bestMove = board.getConfig().getWidth() / 2;
        currentDepth = MIN_DEPTH;

        while (currentDepth <= MAX_DEPTH) {
            if (isTimeExceeded(QUICK_MOVE_MS)) break;

            int[] moves = getOrderedMoves(board);
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

    private int[] getOrderedMoves(Board board) {
        int width = board.getConfig().getWidth();
        int[] moves = new int[width];
        int center = width / 2;
        int index = 0;

        moves[index++] = center;
        for (int offset = 1; offset <= center; offset++) {
            if (center - offset >= 0) moves[index++] = center - offset;
            if (center + offset < width) moves[index++] = center + offset;
        }

        return moves;
    }

    private int evaluateMovesAtDepth(Board board, int[] moves) {
        int bestMove = moves[0];
        int bestValue = -INFINITY;

        for (int move : moves) {
            try {
                Board nextBoard = new Board(board, move, getCounter());
                int value = minimax(nextBoard, currentDepth, -INFINITY, INFINITY, false);

                if (value > bestValue) {
                    bestValue = value;
                    bestMove = move;
                }

                if (isTimeExceeded(ABSOLUTE_LIMIT_MS)) break;
            } catch (Exception e) {
                continue;
            }
        }

        return bestMove;
    }

    private int minimax(Board board, int depth, int alpha, int beta, boolean maximizing) {
        if (depth == 0 || isTimeExceeded(ABSOLUTE_LIMIT_MS)) {
            return evaluatePosition(board);
        }

        int value = maximizing ? -INFINITY : INFINITY;

        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            try {
                Board newBoard = new Board(board, col,
                        maximizing ? getCounter() : getCounter().getOther());

                int eval = minimax(newBoard, depth - 1, alpha, beta, !maximizing);

                if (maximizing) {
                    value = Math.max(value, eval);
                    alpha = Math.max(alpha, eval);
                } else {
                    value = Math.min(value, eval);
                    beta = Math.min(beta, eval);
                }

                if (beta <= alpha) break;
                if (isTimeExceeded(ABSOLUTE_LIMIT_MS)) break;

            } catch (Exception e) {
                continue;
            }
        }

        return value;
    }

    private int evaluatePosition(Board board) {
        int score = 0;
        int center = board.getConfig().getWidth() / 2;

        // Value center control
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            int distanceFromCenter = Math.abs(col - center);
            for (int row = 0; row < board.getConfig().getHeight(); row++) {
                Counter counter = board.getCounterAtPosition(new Position(col, row));
                if (counter != null) {
                    int value = counter == getCounter() ? 1 : -1;
                    score += value * (5 - distanceFromCenter) * CENTER_WEIGHT;
                }
            }
        }

        return score;
    }

    private boolean isTimeExceeded(long limit) {
        return System.currentTimeMillis() - startTime > limit;
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

        // Check all directions for a win
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