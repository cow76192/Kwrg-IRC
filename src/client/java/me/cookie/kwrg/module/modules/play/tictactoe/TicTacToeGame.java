package me.cookie.kwrg.module.modules.play.tictactoe;

public class TicTacToeGame {
    private int[][] board;
    private int currentPlayer;
    private int winner;

    public TicTacToeGame() {
        reset();
    }

    public void reset() {
        this.board = new int[3][3];
        this.currentPlayer = 1;
        this.winner = 0;
    }

    public int[][] getBoard() {
        return this.board;
    }

    public int getCurrentPlayer() {
        return this.currentPlayer;
    }

    public int getWinner() {
        return this.winner;
    }

    public boolean makeMove(int r, int c) {
        if (this.winner != 0) return false;
        if (r < 0 || r >= 3 || c < 0 || c >= 3) return false;
        if (this.board[r][c] != 0) return false;

        this.board[r][c] = this.currentPlayer;
        this.winner = checkWinner();

        if (this.winner == 0) {
            this.currentPlayer = (this.currentPlayer == 1) ? 2 : 1;
        }
        return true;
    }

    private int checkWinner() {
        for (int i = 0; i < 3; i++) {
            if (this.board[i][0] != 0 && this.board[i][0] == this.board[i][1] && this.board[i][1] == this.board[i][2]) {
                return this.board[i][0];
            }
            if (this.board[0][i] != 0 && this.board[0][i] == this.board[1][i] && this.board[1][i] == this.board[2][i]) {
                return this.board[0][i];
            }
        }
        if (this.board[0][0] != 0 && this.board[0][0] == this.board[1][1] && this.board[1][1] == this.board[2][2]) {
            return this.board[0][0];
        }
        if (this.board[0][2] != 0 && this.board[0][2] == this.board[1][1] && this.board[1][1] == this.board[2][0]) {
            return this.board[0][2];
        }

        boolean isFull = true;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (this.board[i][j] == 0) {
                    isFull = false;
                    break;
                }
            }
        }
        return isFull ? 3 : 0;
    }
}