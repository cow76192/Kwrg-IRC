package me.cookie.kwrg.module.modules.play;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TicTacToeAI {
    private static final Random RANDOM = new Random();

    public static int[] getBestMove(int[][] board, boolean isUltron) {
        if (!isUltron && RANDOM.nextFloat() < 0.3f) {
            return getRandomMove(board);
        }
        return getMinimaxMove(board);
    }

    private static int[] getRandomMove(int[][] board) {
        List<int[]> emptyCells = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (board[i][j] == 0) {
                    emptyCells.add(new int[]{i, j});
                }
            }
        }
        if (emptyCells.isEmpty()) return null;
        return emptyCells.get(RANDOM.nextInt(emptyCells.size()));
    }

    private static int[] getMinimaxMove(int[][] board) {
        int bestScore = -1000;
        int[] bestMove = null;

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (board[i][j] == 0) {
                    board[i][j] = 2;
                    int score = minimax(board, 0, false);
                    board[i][j] = 0;

                    if (score > bestScore) {
                        bestScore = score;
                        bestMove = new int[]{i, j};
                    }
                }
            }
        }
        return bestMove;
    }

    private static int minimax(int[][] board, int depth, boolean isMaximizing) {
        int winner = checkWinner(board);
        if (winner == 2) return 10 - depth;
        if (winner == 1) return depth - 10;
        if (winner == 3) return 0;

        if (isMaximizing) {
            int best = -1000;
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    if (board[i][j] == 0) {
                        board[i][j] = 2;
                        best = Math.max(best, minimax(board, depth + 1, false));
                        board[i][j] = 0;
                    }
                }
            }
            return best;
        } else {
            int best = 1000;
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    if (board[i][j] == 0) {
                        board[i][j] = 1;
                        best = Math.min(best, minimax(board, depth + 1, true));
                        board[i][j] = 0;
                    }
                }
            }
            return best;
        }
    }

    private static int checkWinner(int[][] board) {
        for (int i = 0; i < 3; i++) {
            if (board[i][0] != 0 && board[i][0] == board[i][1] && board[i][1] == board[i][2]) return board[i][0];
            if (board[0][i] != 0 && board[0][i] == board[1][i] && board[1][i] == board[2][i]) return board[0][i];
        }
        if (board[0][0] != 0 && board[0][0] == board[1][1] && board[1][1] == board[2][2]) return board[0][0];
        if (board[0][2] != 0 && board[0][2] == board[1][1] && board[1][1] == board[2][0]) return board[0][2];

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (board[i][j] == 0) return 0;
            }
        }
        return 3;
    }
}