package com.thg.accelerator23.connectn.ai.stack_over_four;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;

import com.thehutgroup.accelerator.connectn.player.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StackOverFourTest {
    private StackOverFour player;
    private GameConfig config;
    private static Map<String, Long> testTimes;

    @BeforeAll
    static void initTimingMap() {
        testTimes = new HashMap<>();
    }

    @BeforeEach
    void setUp() {
        player = new StackOverFour(Counter.O);
        config = new GameConfig(10, 8, 4);
    }

    /**
     * Records and analyzes test execution time, providing detailed performance feedback
     * and optimization suggestions when necessary.
     */
    private void recordTestTime(String testName, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        testTimes.put(testName, duration);

        System.out.println("\n=== Performance Analysis for: " + testName + " ===");
        System.out.printf("Time taken: %d ms%n", duration);

        if (duration > 5000) {
            System.out.println("⚠️  Performance Warning: Test exceeded 5 seconds");
            provideOptimizationSuggestions(testName, duration);
        } else if (duration > 2000) {
            System.out.println("⚠️  Note: Test took over 2 seconds");
            System.out.println("💡 Consider optimization if this is a common scenario");
        } else if (duration > 1000) {
            System.out.println("ℹ️  Acceptable performance, but room for improvement");
            System.out.println("💡 Consider minor optimizations during refactoring");
        } else if (duration > 500) {
            System.out.println("✅ Good performance");
            System.out.println("💡 No immediate optimization needed");
        } else {
            System.out.println("🏆 Excellent performance!");
        }
        System.out.println("=====================================");
    }

    /**
     * Provides context-specific optimization suggestions based on test type and performance.
     */
    private void provideOptimizationSuggestions(String testName, long duration) {
        System.out.println("\nOptimization Suggestions:");

        if (testName.contains("Initial")) {
            System.out.println("💡 Consider caching opening moves");
            System.out.println("💡 Implement simple pattern matching for early game");
            System.out.println("💡 Pre-calculate common opening sequences");
        }
        else if (testName.contains("Blocking")) {
            System.out.println("💡 Add threat detection patterns");
            System.out.println("💡 Implement immediate threat response cache");
            System.out.println("💡 Consider reducing search depth for obvious threats");
        }
        else if (testName.contains("Fork")) {
            System.out.println("💡 Implement transposition tables");
            System.out.println("💡 Add pattern recognition for common fork positions");
            System.out.println("💡 Consider principal variation search");
        }
        else if (testName.contains("Memory")) {
            System.out.println("💡 Review board state representation");
            System.out.println("💡 Implement memory-efficient caching");
            System.out.println("💡 Consider bitboard implementation");
        }
        else if (testName.contains("Performance")) {
            System.out.println("💡 Implement iterative deepening");
            System.out.println("💡 Add move ordering optimizations");
            System.out.println("💡 Consider null-move pruning");
        }
        else if (testName.contains("Full")) {
            System.out.println("💡 Add quick validation for full columns");
            System.out.println("💡 Implement column availability tracking");
        }
    }

    @Test
    @DisplayName("Initial move should prefer center columns")
    void testInitialMove(TestInfo testInfo) {
        long startTime = System.currentTimeMillis();

        Board emptyBoard = new Board(config);
        int move = player.makeMove(emptyBoard);

        assertTrue(move >= 3 && move <= 6,
                "First move should be in center region (columns 3-6), but was: " + move);

        recordTestTime(testInfo.getDisplayName(), startTime);
    }

    @Test
    @DisplayName("AI should understand double threats")
    void testDoubleThreats(TestInfo testInfo) {
        long startTime = System.currentTimeMillis();

        Board board = new Board(config);
        try {
            // Create a double threat position:
            // O O - -
            // X X O -
            // X O X O
            board = new Board(board, 0, Counter.X);
            board = new Board(board, 0, Counter.O);
            board = new Board(board, 0, Counter.X);
            board = new Board(board, 1, Counter.X);
            board = new Board(board, 1, Counter.O);
            board = new Board(board, 1, Counter.O);
            board = new Board(board, 2, Counter.X);
            board = new Board(board, 2, Counter.O);
            board = new Board(board, 3, Counter.O);

            int move = player.makeMove(board);

            assertEquals(2, move, "AI should choose column 2 to create a double threat");

        } catch (Exception e) {
            fail("Error during double threat test: " + e.getMessage());
        }

        recordTestTime(testInfo.getDisplayName(), startTime);
    }

    @Test
    @DisplayName("AI should understand strategic territory control")
    void testStrategicTerritory(TestInfo testInfo) {
        long startTime = System.currentTimeMillis();

        Board board = new Board(config);
        try {
            // Create a position where territory control is crucial
            // - - - -
            // - O - -
            // X O X -
            board = new Board(board, 1, Counter.X);
            board = new Board(board, 1, Counter.O);
            board = new Board(board, 1, Counter.O);
            board = new Board(board, 3, Counter.X);

            int move = player.makeMove(board);

            // Should play in column 2 to maintain central control
            assertEquals(2, move,
                    "AI should prioritize central territory control");

        } catch (Exception e) {
            fail("Error during strategic territory test: " + e.getMessage());
        }

        recordTestTime(testInfo.getDisplayName(), startTime);
    }

    @Test
    @DisplayName("AI should recognize trapped positions")
    void testTrappedPositions(TestInfo testInfo) {
        long startTime = System.currentTimeMillis();

        Board board = new Board(config);
        try {
            // Create a trapped position where opponent controls both sides
            // - - - - -
            // X - - - X
            // X O O O X
            for (int i = 1; i < 4; i++) {
                board = new Board(board, i, Counter.O);
            }
            board = new Board(board, 0, Counter.X);
            board = new Board(board, 0, Counter.X);
            board = new Board(board, 4, Counter.X);
            board = new Board(board, 4, Counter.X);

            int move = player.makeMove(board);

            // Should avoid trapped center columns
            assertTrue(move < 1 || move > 3,
                    "AI should avoid trapped central positions");

        } catch (Exception e) {
            fail("Error during trapped position test: " + e.getMessage());
        }

        recordTestTime(testInfo.getDisplayName(), startTime);
    }

    @Test
    @DisplayName("AI should block opponent's winning move")
    void testBlockingWin(TestInfo testInfo) {
        long startTime = System.currentTimeMillis();

        Board board = new Board(config);
        try {
            // Create a situation where opponent has three in a row
            board = new Board(board, 0, Counter.X);
            board = new Board(board, 1, Counter.X);
            board = new Board(board, 2, Counter.X);

            int move = player.makeMove(board);
            assertEquals(3, move, "AI should block opponent's winning move in column 3");
        } catch (Exception e) {
            fail("Error setting up test board: " + e.getMessage());
        }

        recordTestTime(testInfo.getDisplayName(), startTime);
    }

    @Test
    @DisplayName("AI should take winning move when available")
    void testTakeWinningMove(TestInfo testInfo) {
        long startTime = System.currentTimeMillis();

        Board board = new Board(config);
        try {
            // Create a situation where AI has three in a row
            board = new Board(board, 0, Counter.O);
            board = new Board(board, 1, Counter.O);
            board = new Board(board, 2, Counter.O);

            int move = player.makeMove(board);
            assertEquals(3, move, "AI should take winning move in column 3");
        } catch (Exception e) {
            fail("Error setting up test board: " + e.getMessage());
        }

        recordTestTime(testInfo.getDisplayName(), startTime);
    }

    @Test
    @DisplayName("AI should calibrate performance based on system capabilities")
    void testPerformanceCalibration(TestInfo testInfo) {
        long startTime = System.currentTimeMillis();

        // Create boards with increasing complexity
        List<Board> testBoards = new ArrayList<>();
        Board currentBoard = new Board(config);

        try {
            // Add boards with different complexity levels
            testBoards.add(currentBoard);

            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    currentBoard = new Board(currentBoard, j,
                            (i + j) % 2 == 0 ? Counter.O : Counter.X);
                }
                testBoards.add(currentBoard);
            }

            // Test each board and measure performance
            List<Long> moveTimes = new ArrayList<>();
            for (Board board : testBoards) {
                long moveStart = System.currentTimeMillis();
                player.makeMove(board);
                moveTimes.add(System.currentTimeMillis() - moveStart);
            }

            // Verify that move times stay within limits even with increasing complexity
            for (int i = 0; i < moveTimes.size(); i++) {
                assertTrue(moveTimes.get(i) < 10000,
                        String.format("Move %d took %d ms, exceeding limit", i, moveTimes.get(i)));
            }

            // Verify that later moves adapt based on earlier performance
            if (moveTimes.size() > 1) {
                long firstMove = moveTimes.get(0);
                long lastMove = moveTimes.get(moveTimes.size() - 1);
                assertTrue(Math.abs(lastMove - firstMove) < 5000,
                        "Move times should stabilize after calibration");
            }

        } catch (Exception e) {
            fail("Error during calibration test: " + e.getMessage());
        }

        recordTestTime(testInfo.getDisplayName(), startTime);
    }

    @Test
    @DisplayName("AI should adapt to performance constraints")
    void testPerformanceAdaptation(TestInfo testInfo) {
        long startTime = System.currentTimeMillis();

        Board board = new Board(config);
        try {
            // Create a moderately complex board position
            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 5; j++) {
                    board = new Board(board, j,
                            (i + j) % 2 == 0 ? Counter.O : Counter.X);
                }
            }

            // Test multiple moves to verify adaptation
            List<Long> moveTimes = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                long moveStart = System.currentTimeMillis();
                int move = player.makeMove(board);
                long moveTime = System.currentTimeMillis() - moveStart;
                moveTimes.add(moveTime);

                assertTrue(moveTime < 10000,
                        String.format("Move %d took %d ms, exceeding 10 second limit", i, moveTime));

                // Make the move to advance the position
                board = new Board(board, move, Counter.O);
                if (i < 2) { // Make opponent move except after last iteration
                    // Choose a valid column for opponent's move
                    int opponentMove = (move + 1) % board.getConfig().getWidth();
                    board = new Board(board, opponentMove, Counter.X);
                }
            }

            // Verify that move times are adapting and stabilizing
            if (moveTimes.size() > 1) {
                long maxDifference = Collections.max(moveTimes) - Collections.min(moveTimes);
                assertTrue(maxDifference < 5000,
                        "Move times should stabilize with performance adaptation");
            }

        } catch (Exception e) {
            fail("Error during performance test: " + e.getMessage());
        }

        recordTestTime(testInfo.getDisplayName(), startTime);
    }

    @Test
    @DisplayName("AI should handle full columns gracefully")
    void testFullColumnHandling(TestInfo testInfo) {
        long startTime = System.currentTimeMillis();

        Board board = new Board(config);
        try {
            // Fill first column completely
            for (int i = 0; i < config.getHeight(); i++) {
                board = new Board(board, 0,
                        i % 2 == 0 ? Counter.O : Counter.X);
            }

            int move = player.makeMove(board);
            assertNotEquals(0, move, "AI should not choose a full column");
            assertTrue(move >= 0 && move < config.getWidth(),
                    "Move should be within board bounds");
        } catch (Exception e) {
            fail("Error during full column test: " + e.getMessage());
        }

        recordTestTime(testInfo.getDisplayName(), startTime);
    }

    @Test
    @DisplayName("AI should prevent opponent's fork")
    void testPreventFork(TestInfo testInfo) {
        long startTime = System.currentTimeMillis();

        Board board = new Board(config);
        try {
            // Create a potential fork situation
            board = new Board(board, 1, Counter.X);
            board = new Board(board, 2, Counter.X);
            board = new Board(board, 4, Counter.X);

            int move = player.makeMove(board);
            assertEquals(3, move, "AI should block opponent's fork opportunity");
        } catch (Exception e) {
            fail("Error during fork prevention test: " + e.getMessage());
        }

        recordTestTime(testInfo.getDisplayName(), startTime);
    }

    @Test
    @DisplayName("AI should respect memory constraints")
    void testMemoryConstraints(TestInfo testInfo) {
        long startTime = System.currentTimeMillis();

        Board board = new Board(config);
        Runtime runtime = Runtime.getRuntime();

        try {
            System.gc();
            long beforeUsed = runtime.totalMemory() - runtime.freeMemory();

            // Create a complex board position
            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 5; j++) {
                    board = new Board(board, j,
                            (i + j) % 2 == 0 ? Counter.O : Counter.X);
                }
            }

            // Make multiple moves to stress memory usage
            for (int i = 0; i < 3; i++) {
                int move = player.makeMove(board);
                board = new Board(board, move, Counter.O);
            }

            System.gc();
            long afterUsed = runtime.totalMemory() - runtime.freeMemory();
            double gbUsed = (afterUsed - beforeUsed) / (1024.0 * 1024.0 * 1024.0);

            assertTrue(gbUsed < 2.0,
                    String.format("Memory usage (%.2f GB) exceeded 2GB limit", gbUsed));

        } catch (Exception e) {
            fail("Error during memory test: " + e.getMessage());
        }

        recordTestTime(testInfo.getDisplayName(), startTime);
    }

    @Test
    @DisplayName("Final Performance Summary")
    void printPerformanceSummary() {
        System.out.println("\n=== Connect 4 AI Performance Summary ===");
        System.out.println("Ordered by execution time (longest first):\n");

        testTimes.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> {
                    String testName = entry.getKey();
                    if (testName.contains("Summary")) return;

                    long timeMs = entry.getValue();
                    System.out.printf("Test: %s%n", testName);
                    System.out.printf("Time: %d ms%n", timeMs);

                    // Add performance indicators
                    if (timeMs > 5000) {
                        System.out.println("Status: ⚠️  Needs optimization");
                    } else if (timeMs > 2000) {
                        System.out.println("Status: ⚠️  Consider optimization");
                    } else if (timeMs > 1000) {
                        System.out.println("Status: ℹ️  Acceptable");
                    } else if (timeMs > 500) {
                        System.out.println("Status: ✅ Good");
                    } else {
                        System.out.println("Status: 🏆 Excellent");
                    }

                    // Add optimization suggestions for slower tests
                    if (timeMs > 2000) {
                        System.out.println("\nSuggested Optimizations:");
                        provideOptimizationSuggestions(testName, timeMs);
                    }

                    System.out.println("----------------------------------------\n");
                });
    }
}
