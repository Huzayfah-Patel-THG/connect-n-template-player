package com.thg.accelerator23.connectn.ai.stack_over_four;

import com.thehutgroup.accelerator.connectn.player.Board;
import com.thehutgroup.accelerator.connectn.player.Counter;
import com.thehutgroup.accelerator.connectn.player.Player;
import com.thehutgroup.accelerator.connectn.player.Position;

public class StackOverFour extends Player {
  // Scoring constants for position evaluation
  private static final int INFINITY = Integer.MAX_VALUE;
  private static final int WIN_SCORE = 1000000;
  private static final int CENTER_WEIGHT = 3;
  private static final int HEIGHT_WEIGHT = 1;

  // Time management constants - carefully chosen to maximize performance
  private static final long TARGET_TIME_MS = 7800;  // 7.8 seconds leaves safety margin
  private static final long MINIMUM_MOVE_TIME_MS = 100;  // Never use less than 100ms

  // Search depth parameters to balance analysis depth with time constraints
  private static final int INITIAL_MAX_DEPTH = 6;
  private static final int MIN_DEPTH = 4;
  private static final int MAX_DEPTH = 12;

  // Direction vectors for checking connected pieces
  private static final int[][] DIRECTIONS = {
          {1, 0},   // Horizontal
          {0, 1},   // Vertical
          {1, 1},   // Diagonal down-right
          {1, -1}   // Diagonal up-right
  };

  // Instance variables for performance tracking and optimization
  private long startTime;                    // Start time of current move
  private int currentMaxDepth;               // Current maximum search depth
  private boolean isCalibrated;              // Whether initial calibration is complete
  private int[] depthTimings;               // Historical timing data for each depth
  private long lastMoveTime;                 // Duration of the previous move
  private Runtime runtime;                   // Reference to runtime for memory monitoring

  public StackOverFour(Counter counter) {
    super(counter, "StackOverFour");
    this.currentMaxDepth = INITIAL_MAX_DEPTH;
    this.depthTimings = new int[MAX_DEPTH + 1];
    this.isCalibrated = false;
    this.lastMoveTime = 0;
    this.runtime = Runtime.getRuntime();
  }

  @Override
  public int makeMove(Board board) {
    startTime = System.currentTimeMillis();

    // Quick check for immediate wins or blocks
    int tacticalMove = findTacticalMove(board);
    if (tacticalMove != -1) {
      return tacticalMove;
    }

    // Set initial search depth based on resources
    currentMaxDepth = getOptimalSearchDepth();

    // Use iterative deepening to maximize our time usage
    int bestMove = board.getConfig().getWidth() / 2;  // Default to center
    int bestValue = -INFINITY;
    int actualDepth = MIN_DEPTH;

    // Iteratively deepen search until we run out of time
    for (int depth = MIN_DEPTH; depth <= currentMaxDepth; depth++) {
      long depthStartTime = System.currentTimeMillis();
      int[] columns = getCenterBasedMoveOrder(board.getConfig().getWidth());

      int tempBestMove = bestMove;
      int tempBestValue = -INFINITY;

      // Evaluate all possible moves at this depth
      for (int column : columns) {
        try {
          Board nextPosition = new Board(board, column, getCounter());
          int moveValue = minimax(nextPosition, depth, -INFINITY, INFINITY, false);

          if (moveValue > tempBestValue) {
            tempBestValue = moveValue;
            tempBestMove = column;
          }

          if (isTimeUp()) {
            break;
          }
        } catch (Exception e) {
          continue;
        }
      }

      // Only update best move if we completed this depth
      if (!isTimeUp()) {
        bestMove = tempBestMove;
        bestValue = tempBestValue;
        actualDepth = depth;
      } else {
        break;
      }
    }

    // Update performance profile with timing data
    lastMoveTime = System.currentTimeMillis() - startTime;
    updateResourceProfile(lastMoveTime, actualDepth);

    return bestMove;
  }

  private int findTacticalMove(Board board) {
    // Check for winning moves first
    for (int col = 0; col < board.getConfig().getWidth(); col++) {
      try {
        Board testBoard = new Board(board, col, getCounter());
        if (isWinningMove(testBoard, col, board.getConfig().getnInARowForWin())) {
          return col;
        }
      } catch (Exception e) {
        continue;
      }
    }

    // Then check for blocking opponent's wins
    for (int col = 0; col < board.getConfig().getWidth(); col++) {
      try {
        Board testBoard = new Board(board, col, getCounter().getOther());
        if (isWinningMove(testBoard, col, board.getConfig().getnInARowForWin())) {
          return col;
        }
      } catch (Exception e) {
        continue;
      }
    }

    return -1;  // No immediate tactical moves found
  }

  private int minimax(Board board, int depth, int alpha, int beta, boolean isMaximizing) {
    if (isTimeUp() || depth == 0) {
      return evaluatePosition(board);
    }

    if (isMaximizing) {
      int maxEval = -INFINITY;
      for (int col = 0; col < board.getConfig().getWidth(); col++) {
        try {
          Board newBoard = new Board(board, col, getCounter());
          int eval = minimax(newBoard, depth - 1, alpha, beta, false);
          maxEval = Math.max(maxEval, eval);
          alpha = Math.max(alpha, eval);
          if (beta <= alpha) break;  // Alpha-beta pruning
        } catch (Exception e) {
          continue;
        }
      }
      return maxEval;
    } else {
      int minEval = INFINITY;
      for (int col = 0; col < board.getConfig().getWidth(); col++) {
        try {
          Board newBoard = new Board(board, col, getCounter().getOther());
          int eval = minimax(newBoard, depth - 1, alpha, beta, true);
          minEval = Math.min(minEval, eval);
          beta = Math.min(beta, eval);
          if (beta <= alpha) break;  // Alpha-beta pruning
        } catch (Exception e) {
          continue;
        }
      }
      return minEval;
    }
  }

  private boolean isEmptyBoard(Board board) {
    for (int x = 0; x < board.getConfig().getWidth(); x++) {
      for (int y = 0; y < board.getConfig().getHeight(); y++) {
        if (board.getCounterAtPosition(new Position(x, y)) != null) {
          return false;
        }
      }
    }
    return true;
  }

  private void updateResourceProfile(long moveTime, int depth) {
    // Update timing data for this depth
    depthTimings[depth] = (depthTimings[depth] == 0) ?
            (int)moveTime : (depthTimings[depth] + (int)moveTime) / 2;

    // Adjust depth based on performance
    if (moveTime < TARGET_TIME_MS / 2 && currentMaxDepth < MAX_DEPTH) {
      currentMaxDepth++;
    } else if (moveTime > TARGET_TIME_MS * 0.9 && currentMaxDepth > MIN_DEPTH) {
      currentMaxDepth--;
    }
  }

  private int getOptimalSearchDepth() {
    // Get current memory usage
    long maxMemory = runtime.maxMemory();
    long totalMemory = runtime.totalMemory();
    long freeMemory = runtime.freeMemory();
    double memoryUsageRatio = (double)(totalMemory - freeMemory) / maxMemory;

    // Start with current depth
    int optimalDepth = currentMaxDepth;

    // Adjust based on memory usage
    if (memoryUsageRatio > 0.8 && optimalDepth > MIN_DEPTH) {
      optimalDepth--;
    } else if (memoryUsageRatio < 0.5 && optimalDepth < MAX_DEPTH &&
            (lastMoveTime == 0 || lastMoveTime < TARGET_TIME_MS * 0.8)) {
      optimalDepth++;
    }

    // Check historical timing data
    if (depthTimings[optimalDepth] > 0) {
      while (depthTimings[optimalDepth] > TARGET_TIME_MS && optimalDepth > MIN_DEPTH) {
        optimalDepth--;
      }
    }

    return optimalDepth;
  }

  private boolean isTimeUp() {
    long elapsed = System.currentTimeMillis() - startTime;

    // Primary time check
    if (elapsed >= TARGET_TIME_MS) {
      return true;
    }

    // Predict if next iteration would exceed time limit
    if (lastMoveTime > 0) {
      long predictedNextMoveTime = lastMoveTime * 3;  // Conservative estimate
      if (elapsed + predictedNextMoveTime > TARGET_TIME_MS) {
        return true;
      }
    }

    return false;
  }

  private boolean isWinningMove(Board board, int lastCol, int needToWin) {
    // Find the row of the last placed piece
    int row = 0;
    for (int r = 0; r < board.getConfig().getHeight(); r++) {
      if (board.getCounterAtPosition(new Position(lastCol, r)) != null) {
        row = r;
        break;
      }
    }

    Counter counter = board.getCounterAtPosition(new Position(lastCol, row));
    if (counter == null) return false;

    // Check all directions for a win
    for (int[] direction : DIRECTIONS) {
      int count = 1;  // Start with 1 for the current piece
      int dx = direction[0], dy = direction[1];

      // Check positive direction
      int x = lastCol + dx, y = row + dy;
      while (isValidPosition(board, x, y) &&
              board.getCounterAtPosition(new Position(x, y)) == counter) {
        count++;
        x += dx;
        y += dy;
      }

      // Check negative direction
      x = lastCol - dx;
      y = row - dy;
      while (isValidPosition(board, x, y) &&
              board.getCounterAtPosition(new Position(x, y)) == counter) {
        count++;
        x -= dx;
        y -= dy;
      }

      if (count >= needToWin) return true;
    }
    return false;
  }

  private int evaluatePosition(Board board) {
    int score = 0;
    int center = board.getConfig().getWidth() / 2;

    // Evaluate center control and height advantage
    for (int col = 0; col < board.getConfig().getWidth(); col++) {
      int distanceFromCenter = Math.abs(col - center);
      for (int row = 0; row < board.getConfig().getHeight(); row++) {
        Counter counter = board.getCounterAtPosition(new Position(col, row));
        if (counter != null) {
          int value = counter == getCounter() ? 1 : -1;
          score += value * (5 - distanceFromCenter) * CENTER_WEIGHT;
          score += value * (board.getConfig().getHeight() - row) * HEIGHT_WEIGHT;
        }
      }
    }

    return score;
  }

  private int[] getCenterBasedMoveOrder(int width) {
    int[] moves = new int[width];
    int center = width / 2;
    int index = 0;

    // Start with center column
    moves[index++] = center;

    // Add columns alternating outward
    for (int offset = 1; offset <= center; offset++) {
      if (center - offset >= 0) {
        moves[index++] = center - offset;
      }
      if (center + offset < width) {
        moves[index++] = center + offset;
      }
    }

    return moves;
  }

  private boolean isValidPosition(Board board, int x, int y) {
    return x >= 0 && x < board.getConfig().getWidth() &&
            y >= 0 && y < board.getConfig().getHeight();
  }
}