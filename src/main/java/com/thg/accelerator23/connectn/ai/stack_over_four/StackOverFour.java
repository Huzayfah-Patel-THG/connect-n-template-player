package com.thg.accelerator23.connectn.ai.stack_over_four;

import com.thehutgroup.accelerator.connectn.player.Board;
import com.thehutgroup.accelerator.connectn.player.Counter;
import com.thehutgroup.accelerator.connectn.player.Player;
import com.thehutgroup.accelerator.connectn.player.Position;


public class StackOverFour extends Player {
  // -----------------------------
  // Configuration Constants
  // -----------------------------

  // Maximum depth for minimax search - tuned for performance within 10-second limit
  private static final int MAX_DEPTH = 8;

  // Used for win/loss scoring in position evaluation
  private static final int INFINITY = Integer.MAX_VALUE;

  // Time limit set to 8 seconds to leave safety margin
  private static final long TIME_LIMIT_MS = 8000;

  // Evaluation weights for different aspects of position scoring
  private static final int CONNECT_THREE_SCORE = 100;   // Three in a row
  private static final int CONNECT_TWO_SCORE = 10;      // Two in a row
  private static final int CENTER_WEIGHT = 3;           // Center control importance
  private static final int HEIGHT_WEIGHT = 1;           // Vertical positioning importance

  // Vectors for checking different directions on the board
  private static final int[][] DIRECTIONS = {
          {1, 0},   // Horizontal
          {0, 1},   // Vertical
          {1, 1},   // Diagonal down-right
          {1, -1}   // Diagonal up-right
  };

  // -----------------------------
  // State Variables
  // -----------------------------
  private long startTime;

  // -----------------------------
  // Constructor
  // -----------------------------
  public StackOverFour(Counter counter) {
    super(counter, StackOverFour.class.getName());
  }

  // -----------------------------
  // Main Move Selection Logic
  // -----------------------------
  @Override
  public int makeMove(Board board) {
    startTime = System.currentTimeMillis();

    // Default to center column as it's often strategically valuable
    int bestMove = board.getConfig().getWidth() / 2;
    int bestValue = -INFINITY;

    // Get columns ordered by strategic value (center-out pattern)
    int[] columns = getOrderedColumns(board.getConfig().getWidth());

    // Try each possible move, starting with most promising columns
    for (int column : columns) {
      try {
        Board nextPosition = new Board(board, column, getCounter());

        // Evaluate this move using minimax with alpha-beta pruning
        int moveValue = minimax(nextPosition, MAX_DEPTH, -INFINITY, INFINITY, false);

        // Update best move if this one is better
        if (moveValue > bestValue) {
          bestValue = moveValue;
          bestMove = column;
        }

        // Emergency time check
        if (isTimeUp()) {
          break;
        }
      } catch (Exception e) {
        // Skip invalid moves silently
        continue;
      }
    }

    return bestMove;
  }

  // -----------------------------
  // Minimax Algorithm
  // -----------------------------
  private int minimax(Board board, int depth, int alpha, int beta, boolean isMaximizing) {
    // Check for timeout first to ensure we respond in time
    if (isTimeUp()) {
      return evaluatePosition(board);
    }

    // Check for terminal states (win/loss/draw)
    if (isWinningBoard(board, getCounter())) {
      return INFINITY;
    }
    if (isWinningBoard(board, getCounter().getOther())) {
      return -INFINITY;
    }
    if (isBoardFull(board)) {
      return 0;
    }

    // Return position evaluation at maximum depth
    if (depth == 0) {
      return evaluatePosition(board);
    }

    // Maximizing player's turn (our AI)
    if (isMaximizing) {
      int maxEval = -INFINITY;

      // Try each possible move
      for (int col = 0; col < board.getConfig().getWidth(); col++) {
        try {
          Board newBoard = new Board(board, col, getCounter());
          int eval = minimax(newBoard, depth - 1, alpha, beta, false);
          maxEval = Math.max(maxEval, eval);
          alpha = Math.max(alpha, eval);

          // Alpha-beta pruning
          if (beta <= alpha) {
            break;
          }
        } catch (Exception e) {
          continue;
        }
      }

      return maxEval;
    }
    // Minimizing player's turn (opponent)
    else {
      int minEval = INFINITY;

      // Try each possible move
      for (int col = 0; col < board.getConfig().getWidth(); col++) {
        try {
          Board newBoard = new Board(board, col, getCounter().getOther());
          int eval = minimax(newBoard, depth - 1, alpha, beta, true);
          minEval = Math.min(minEval, eval);
          beta = Math.min(beta, eval);

          // Alpha-beta pruning
          if (beta <= alpha) {
            break;
          }
        } catch (Exception e) {
          continue;
        }
      }

      return minEval;
    }
  }

  // -----------------------------
  // Board Analysis Methods
  // -----------------------------
  private boolean isWinningBoard(Board board, Counter counter) {
    // Check each starting position for a winning line
    for (int x = 0; x < board.getConfig().getWidth(); x++) {
      for (int y = 0; y < board.getConfig().getHeight(); y++) {
        // Try each direction from this position
        for (int[] dir : DIRECTIONS) {
          if (checkWinningLine(board, x, y, dir[0], dir[1], counter)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private boolean checkWinningLine(Board board, int startX, int startY, int dx, int dy, Counter counter) {
    // Check if this line could even contain 4 pieces
    if (!isValidPosition(board, startX, startY) ||
            !isValidPosition(board, startX + dx * 3, startY + dy * 3)) {
      return false;
    }

    // Check for four matching counters in a row
    for (int i = 0; i < 4; i++) {
      Position pos = new Position(startX + dx * i, startY + dy * i);
      Counter current = board.getCounterAtPosition(pos);
      if (current != counter) {
        return false;
      }
    }

    return true;
  }

  private boolean isBoardFull(Board board) {
    // Check top row for any empty spaces
    for (int x = 0; x < board.getConfig().getWidth(); x++) {
      if (!board.hasCounterAtPosition(
              new Position(x, board.getConfig().getHeight() - 1))) {
        return false;
      }
    }
    return true;
  }

  // -----------------------------
  // Position Evaluation Methods
  // -----------------------------
  private int evaluatePosition(Board board) {
    int score = 0;

    // Add up scores from different evaluation aspects
    score += evaluateConnectedLines(board);
    score += evaluateCenterControl(board);
    score += evaluateHeightAdvantage(board);

    return score;
  }

  private int evaluateConnectedLines(Board board) {
    int score = 0;

    // Check each possible starting position
    for (int x = 0; x < board.getConfig().getWidth(); x++) {
      for (int y = 0; y < board.getConfig().getHeight(); y++) {
        // Look in each direction for connected counters
        for (int[] dir : DIRECTIONS) {
          score += evaluateLine(board, x, y, dir[0], dir[1]);
        }
      }
    }

    return score;
  }

  private int evaluateLine(Board board, int startX, int startY, int dx, int dy) {
    // Initialize counters for both players
    int myCount = 0;
    int opponentCount = 0;
    int emptyCount = 0;

    // Check four positions in this direction
    for (int i = 0; i < 4; i++) {
      int x = startX + dx * i;
      int y = startY + dy * i;

      // Skip if outside board
      if (!isValidPosition(board, x, y)) {
        return 0;
      }

      Counter counter = board.getCounterAtPosition(new Position(x, y));
      if (counter == getCounter()) {
        myCount++;
      } else if (counter == getCounter().getOther()) {
        opponentCount++;
      } else {
        emptyCount++;
      }
    }

    // Calculate score for this line
    if (myCount > 0 && opponentCount > 0) {
      return 0; // Line is blocked
    }

    // Score based on number of connected counters
    if (myCount > 0) {
      return myCount == 3 ? CONNECT_THREE_SCORE :
              myCount == 2 ? CONNECT_TWO_SCORE : 1;
    }
    if (opponentCount > 0) {
      return -(opponentCount == 3 ? CONNECT_THREE_SCORE :
              opponentCount == 2 ? CONNECT_TWO_SCORE : 1);
    }

    return 0;
  }

  private int evaluateCenterControl(Board board) {
    int centerScore = 0;
    int center = board.getConfig().getWidth() / 2;

    // Value positions closer to center more highly
    for (int col = 0; col < board.getConfig().getWidth(); col++) {
      int distanceFromCenter = Math.abs(col - center);
      for (int row = 0; row < board.getConfig().getHeight(); row++) {
        Counter counter = board.getCounterAtPosition(new Position(col, row));
        if (counter != null) {
          int value = counter == getCounter() ? CENTER_WEIGHT : -CENTER_WEIGHT;
          centerScore += value * (5 - distanceFromCenter);
        }
      }
    }

    return centerScore;
  }

  private int evaluateHeightAdvantage(Board board) {
    int heightScore = 0;

    // Prefer lower positions for better stacking
    for (int col = 0; col < board.getConfig().getWidth(); col++) {
      for (int row = 0; row < board.getConfig().getHeight(); row++) {
        Counter counter = board.getCounterAtPosition(new Position(col, row));
        if (counter != null) {
          int value = counter == getCounter() ? HEIGHT_WEIGHT : -HEIGHT_WEIGHT;
          heightScore += value * (board.getConfig().getHeight() - row);
        }
      }
    }

    return heightScore;
  }

  // -----------------------------
  // Utility Methods
  // -----------------------------
  private int[] getOrderedColumns(int width) {
    int[] columns = new int[width];
    int center = width / 2;
    int index = 0;

    // Start with center column
    columns[index++] = center;

    // Add columns outward from center
    for (int offset = 1; offset <= center; offset++) {
      if (center + offset < width) {
        columns[index++] = center + offset;
      }
      if (center - offset >= 0) {
        columns[index++] = center - offset;
      }
    }

    return columns;
  }

  private boolean isTimeUp() {
    return System.currentTimeMillis() - startTime > TIME_LIMIT_MS;
  }

  private boolean isValidPosition(Board board, int x, int y) {
    return x >= 0 && x < board.getConfig().getWidth() &&
            y >= 0 && y < board.getConfig().getHeight();
  }
}
