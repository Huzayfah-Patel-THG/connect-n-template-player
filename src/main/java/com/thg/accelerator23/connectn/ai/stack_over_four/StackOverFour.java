package com.thg.accelerator23.connectn.ai.stack_over_four;

import com.thehutgroup.accelerator.connectn.player.Board;
import com.thehutgroup.accelerator.connectn.player.Counter;
import com.thehutgroup.accelerator.connectn.player.Player;
import com.thehutgroup.accelerator.connectn.player.Position;
import com.thehutgroup.accelerator.connectn.player.InvalidMoveException;

public class StackOverFour extends Player {

    private static final int MAX_DEPTH = 6;  // Depth for Minimax search

    public StackOverFour(Counter counter) {
        super(counter, StackOverFour.class.getName());
    }

    @Override
    public int makeMove(Board board) {
        int bestMove = -1;
        int bestScore = Integer.MIN_VALUE;

        // Loop through all columns and evaluate each move
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (!isColumnFull(board, col)) {
                try {
                    // Copy the board and simulate the move
                    Board newBoard = new Board(board, col, this.getCounter());

                    // Minimax evaluation (AI is maximizing, opponent is minimizing)
                    int moveScore = minimax(newBoard, MAX_DEPTH, false, Integer.MIN_VALUE, Integer.MAX_VALUE);
                    if (moveScore > bestScore) {
                        bestScore = moveScore;
                        bestMove = col;
                    }
                } catch (InvalidMoveException e) {
                    // Skip invalid moves
                    continue;
                }
            }
        }

        return bestMove;
    }

    private int minimax(Board board, int depth, boolean isMaximizingPlayer, int alpha, int beta) {
        if (depth == 0 || isBoardFull(board)) {
            // Evaluate the board from the perspective of the AI
            return evaluate(board, this.getCounter(), getOpponentCounter());
        }

        int bestScore = isMaximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (!isColumnFull(board, col)) {
                try {
                    Board newBoard = new Board(board, col, isMaximizingPlayer ? this.getCounter() : getOpponentCounter());
                    int score = minimax(newBoard, depth - 1, !isMaximizingPlayer, alpha, beta);

                    if (isMaximizingPlayer) {
                        bestScore = Math.max(bestScore, score);
                        alpha = Math.max(alpha, bestScore);
                    } else {
                        bestScore = Math.min(bestScore, score);
                        beta = Math.min(beta, bestScore);
                    }

                    // Alpha-Beta pruning
                    if (beta <= alpha) {
                        break;
                    }
                } catch (InvalidMoveException e) {
                    // Skip invalid moves
                    continue;
                }
            }
        }

        return bestScore;
    }

    private int evaluate(Board board, Counter aiCounter, Counter opponentCounter) {
        int score = 0;

        // Evaluate the lines for AI and opponent
        score += evaluateLines(board, aiCounter);
        score -= evaluateLines(board, opponentCounter);

        // Evaluate center control for AI
        score += evaluateCenterControl(board, aiCounter);

        return score;
    }

    private int evaluateLines(Board board, Counter counter) {
        int score = 0;

        // Horizontal, Vertical, and Diagonal evaluations
        for (int row = 0; row < board.getConfig().getHeight(); row++) {
            for (int col = 0; col < board.getConfig().getWidth() - 3; col++) {
                score += evaluateLine(board, row, col, 0, 1, counter);  // Horizontal line
            }
        }

        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            for (int row = 0; row < board.getConfig().getHeight() - 3; row++) {
                score += evaluateLine(board, row, col, 1, 0, counter);  // Vertical line
            }
        }

        for (int row = 0; row < board.getConfig().getHeight() - 3; row++) {
            for (int col = 0; col < board.getConfig().getWidth() - 3; col++) {
                score += evaluateLine(board, row, col, 1, 1, counter);  // Diagonal down-right
            }
        }

        for (int row = 0; row < board.getConfig().getHeight() - 3; row++) {
            for (int col = 3; col < board.getConfig().getWidth(); col++) {
                score += evaluateLine(board, row, col, 1, -1, counter);  // Diagonal down-left
            }
        }

        return score;
    }

    private int evaluateLine(Board board, int startRow, int startCol, int rowIncrement, int colIncrement, Counter counter) {
        int score = 0;
        int counterCount = 0;
        int emptyCount = 0;
        int opponentCount = 0;

        for (int i = 0; i < 4; i++) {
            int row = startRow + i * rowIncrement;
            int col = startCol + i * colIncrement;
            Position position = new Position(col, row);

            if (board.isWithinBoard(position)) {
                Counter current = board.getCounterAtPosition(position);
                if (current == counter) {
                    counterCount++;
                } else if (current != null) {
                    opponentCount++;
                } else {
                    emptyCount++;
                }
            }
        }

        if (counterCount == 4) {
            score += 1000;  // AI wins
        } else if (opponentCount == 4) {
            score -= 1000;  // Opponent wins
        } else if (counterCount == 3 && emptyCount == 1) {
            score += 100;  // AI can win soon
        } else if (opponentCount == 3 && emptyCount == 1) {
            score -= 100;  // Opponent can win soon
        } else if (counterCount == 2 && emptyCount == 2) {
            score += 10;  // AI has a two-in-a-row setup
        } else if (opponentCount == 2 && emptyCount == 2) {
            score -= 10;  // Opponent has a two-in-a-row setup
        }

        return score;
    }

    private int evaluateCenterControl(Board board, Counter counter) {
        int score = 0;
        int centerColumn = board.getConfig().getWidth() / 2;

        for (int row = 0; row < board.getConfig().getHeight(); row++) {
            Position position = new Position(centerColumn, row);
            if (board.getCounterAtPosition(position) == counter) {
                score += 1;
            }
        }

        return score;
    }

    private Counter getOpponentCounter() {
        return this.getCounter() == Counter.X ? Counter.O : Counter.X;
    }

    private boolean isColumnFull(Board board, int col) {
        return board.hasCounterAtPosition(new Position(col, board.getConfig().getHeight() - 1));
    }

    private boolean isBoardFull(Board board) {
        for (int col = 0; col < board.getConfig().getWidth(); col++) {
            if (!isColumnFull(board, col)) {
                return false;
            }
        }
        return true;
    }
}
