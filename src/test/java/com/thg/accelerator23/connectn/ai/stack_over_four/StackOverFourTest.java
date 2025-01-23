package com.thg.accelerator23.connectn.ai.stack_over_four;

import org.junit.jupiter.api.Test;

import com.thehutgroup.accelerator.connectn.player.Board;
import com.thehutgroup.accelerator.connectn.player.Counter;
import com.thehutgroup.accelerator.connectn.player.Player;
import com.thehutgroup.accelerator.connectn.player.Position;
import com.thehutgroup.accelerator.connectn.player.GameConfig;

import static org.junit.jupiter.api.Assertions.*;


public class StackOverFourTest {
    @Test
    void testPlaceCounter() {
        StackOverFour ai = new StackOverFour(Counter.O);
        Board board = new Board(new GameConfig(10, 8, 4));
        int move = ai.makeMove(board);

        assertTrue(move >= 0 && move < 10, "Move should be within valid column range");
        assertDoesNotThrow(() -> new Board(board, move, Counter.O), "Move should be valid");
    }

    @Test
    void testAvoidFullColumn() {
        StackOverFour ai = new StackOverFour(Counter.O);
        Board board = new Board(new GameConfig(10, 8, 4));

        // Fill column 0
        try {
            for(int i = 0; i < 8; i++) {
                board = new Board(board, 0, i % 2 == 0 ? Counter.O : Counter.X);
            }
        } catch(Exception e) {
            fail("Test setup failed");
        }

        int move = ai.makeMove(board);
        assertNotEquals(0, move, "AI should not choose full column");
    }

    @Test
    void testTimeoutFallback() {
        StackOverFour ai = new StackOverFour(Counter.O);
        Board board = new Board(new GameConfig(10, 8, 4));

        // Sleep for 8 seconds to simulate time passing
        try {
            Thread.sleep(8000);
        } catch (InterruptedException e) {
            fail("Sleep interrupted");
        }

        int move = ai.makeMove(board);

        assertTrue(move >= 0 && move < 10, "Move should be within valid range");
        assertTrue(!board.hasCounterAtPosition(new Position(move, board.getConfig().getHeight() - 1)),
                "Chosen column should not be full");
    }
}