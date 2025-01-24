package com.thg.accelerator23.connectn.ai.stack_over_four;

import org.junit.jupiter.api.*;
import com.thehutgroup.accelerator.connectn.player.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

public class StackOverFourTest {
    private StackOverFour ai;
    private GameConfig config;
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    @BeforeEach
    void setUp() {
        ai = new StackOverFour(Counter.O);
        config = new GameConfig(10, 8, 4);
    }

    // Basic Move Tests
    @Test
    void testInitialMove() {
        Board board = new Board(config);
        int move = ai.makeMove(board);
        assertTrue(move >= 3 && move <= 6, "First move should prefer center columns");
    }

    @Test
    void testValidMoveRange() {
        Board board = new Board(config);
        for(int i = 0; i < 10; i++) {
            int move = ai.makeMove(board);
            assertTrue(move >= 0 && move < board.getConfig().getWidth(),
                    "Move should be within board bounds");
        }
    }

    // Winning Move Detection Tests
    @Test
    void testHorizontalWinDetection() throws Exception {
        Board board = new Board(config);
        // Set up three in a row
        board = new Board(board, 0, Counter.O);
        board = new Board(board, 1, Counter.O);
        board = new Board(board, 2, Counter.O);

        int move = ai.makeMove(board);
        assertEquals(3, move, "Should detect horizontal winning move");
    }

    @Test
    void testVerticalWinDetection() throws Exception {
        Board board = new Board(config);
        // Set up three in a column
        board = new Board(board, 0, Counter.O);
        board = new Board(board, 0, Counter.O);
        board = new Board(board, 0, Counter.O);

        int move = ai.makeMove(board);
        assertEquals(0, move, "Should detect vertical winning move");
    }

    @Test
    void testDiagonalWinDetection() throws Exception {
        Board board = new Board(config);
        // Set up diagonal position
        board = new Board(board, 0, Counter.O);
        board = new Board(board, 1, Counter.X);
        board = new Board(board, 1, Counter.O);
        board = new Board(board, 2, Counter.X);
        board = new Board(board, 2, Counter.X);
        board = new Board(board, 2, Counter.O);

        int move = ai.makeMove(board);
        assertEquals(3, move, "Should detect diagonal winning move");
    }

    // Defensive Tests
    @Test
    void testBlockOpponentWin() throws Exception {
        Board board = new Board(config);
        // Set up opponent's three in a row
        board = new Board(board, 0, Counter.X);
        board = new Board(board, 1, Counter.X);
        board = new Board(board, 2, Counter.X);

        int move = ai.makeMove(board);
        assertEquals(3, move, "Should block opponent's winning move");
    }

    @Test
    void testBlockForkThreats() throws Exception {
        Board board = new Board(config);
        // Set up potential fork position
        board = new Board(board, 0, Counter.X);
        board = new Board(board, 2, Counter.X);
        board = new Board(board, 1, Counter.O);

        int move = ai.makeMove(board);
        assertTrue(move == 0 || move == 2, "Should block potential fork setup");
    }

    // Performance Tests
    @Test
    void testTimeLimit() {
        Board board = new Board(config);
        long startTime = System.currentTimeMillis();
        ai.makeMove(board);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(duration < 8000, "Move should complete within 8 seconds");
    }

    @Test
    void testTimeLimitUnderPressure() throws Exception {
        Board board = new Board(config);
        // Fill most of the board to create complex position
        for(int i = 0; i < 7; i++) {
            for(int j = 0; j < 6; j++) {
                board = new Board(board, i, j % 2 == 0 ? Counter.O : Counter.X);
            }
        }

        long startTime = System.currentTimeMillis();
        ai.makeMove(board);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(duration < 8000, "Complex position should still complete within time limit");
    }

    @Test
    void testMemoryUsage() {
        Board board = new Board(config);
        long initialMemory = memoryBean.getHeapMemoryUsage().getUsed();

        // Make multiple moves to stress memory
        for(int i = 0; i < 10; i++) {
            ai.makeMove(board);
        }

        long finalMemory = memoryBean.getHeapMemoryUsage().getUsed();
        long memoryUsed = (finalMemory - initialMemory) / (1024 * 1024); // Convert to MB

        assertTrue(memoryUsed < 2048, "Memory usage should stay under 2GB");
    }

    // Strategic Tests
    @Test
    void testCenterPreference() {
        Board board = new Board(config);
        Map<Integer, Integer> moveFrequency = new HashMap<>();

        // Make multiple first moves and check distribution
        for(int i = 0; i < 100; i++) {
            int move = ai.makeMove(board);
            moveFrequency.merge(move, 1, Integer::sum);
        }

        // Center columns should be chosen more frequently
        int centerMoves = moveFrequency.getOrDefault(4, 0) + moveFrequency.getOrDefault(5, 0);
        assertTrue(centerMoves > 50, "Should prefer center columns");
    }

    @Test
    void testConnectThreeResponse() throws Exception {
        Board board = new Board(config);
        // Create a position with three connected pieces
        board = new Board(board, 3, Counter.O);
        board = new Board(board, 4, Counter.O);
        board = new Board(board, 5, Counter.O);

        int move = ai.makeMove(board);
        assertTrue(move == 2 || move == 6, "Should try to extend three connected pieces");
    }

    // Edge Case Tests
    @Test
    void testFullColumnAvoidance() throws Exception {
        Board board = new Board(config);
        // Fill a column
        for(int i = 0; i < 8; i++) {
            board = new Board(board, 0, Counter.X);
        }

        int move = ai.makeMove(board);
        assertNotEquals(0, move, "Should avoid full columns");
    }

    @Test
    void testNearlyFullBoard() throws Exception {
        Board board = new Board(config);
        // Fill most of the board
        for(int i = 0; i < 9; i++) {
            for(int j = 0; j < 7; j++) {
                board = new Board(board, i, j % 2 == 0 ? Counter.O : Counter.X);
            }
        }

        long startTime = System.currentTimeMillis();
        int move = ai.makeMove(board);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(duration < 8000, "Should handle nearly full board quickly");
        assertTrue(move >= 0 && move < 10, "Move should be valid");
    }

    // Recovery Tests
    @Test
    void testRecoveryFromComplexPosition() throws Exception {
        Board board = new Board(config);
        // Create complex middle game position
        int[][] moves = {{3,0}, {4,0}, {3,1}, {4,1}, {5,0}, {5,1}};
        for(int[] move : moves) {
            board = new Board(board, move[0], move[1] % 2 == 0 ? Counter.O : Counter.X);
        }

        int move = ai.makeMove(board);
        assertTrue(move >= 2 && move <= 6, "Should make reasonable move in complex position");
    }

    @Test
    void testConsistentPerformance() {
        Board board = new Board(config);
        List<Long> moveTimes = new ArrayList<>();

        // Make several moves and measure time
        for(int i = 0; i < 5; i++) {
            long startTime = System.currentTimeMillis();
            ai.makeMove(board);
            moveTimes.add(System.currentTimeMillis() - startTime);
        }

        // Calculate standard deviation
        double mean = moveTimes.stream().mapToLong(Long::valueOf).average().getAsDouble();
        double variance = moveTimes.stream()
                .mapToDouble(time -> Math.pow(time - mean, 2))
                .average().getAsDouble();
        double stdDev = Math.sqrt(variance);

        assertTrue(stdDev < 2000, "Move times should be relatively consistent");
    }
}