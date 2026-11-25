package MultiThreadTetris.src;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(TetrisFrame::new);
    }
}

class TetrisFrame extends JFrame {

    private final GamePanel gamePanel;
    private final JLabel scoreLabel = new JLabel("Score: 0");
    private final JLabel linesLabel = new JLabel("Lines: 0");
    private final JLabel levelLabel = new JLabel("Level: 1");
    private final JLabel statusLabel = new JLabel("Running");
    private final JLabel gameThreadLabel = new JLabel("Game Thread: Starting...");
    private final JLabel statsThreadLabel = new JLabel("Stats Thread: Starting...");

    TetrisFrame() {
        setTitle("MultiThread Tetris");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        gamePanel = new GamePanel(this);

        JPanel sidePanel = new JPanel();
        sidePanel.setBackground(new Color(25, 25, 25));
        sidePanel.setPreferredSize(new Dimension(210, 600));
        sidePanel.setLayout(new BoxLayout(sidePanel, BoxLayout.Y_AXIS));
        sidePanel.setBorder(BorderFactory.createEmptyBorder(25, 15, 15, 15));

        JLabel title = new JLabel("TETRIS");
        title.setFont(new Font("Arial", Font.BOLD, 30));
        title.setForeground(Color.WHITE);

        styleLabel(scoreLabel, 20, Color.WHITE);
        styleLabel(linesLabel, 18, Color.WHITE);
        styleLabel(levelLabel, 18, Color.WHITE);
        styleLabel(statusLabel, 16, Color.GREEN);
        styleLabel(gameThreadLabel, 12, Color.CYAN);
        styleLabel(statsThreadLabel, 12, Color.ORANGE);

        JLabel controlsTitle = new JLabel("CONTROLS");
        styleLabel(controlsTitle, 16, Color.WHITE);

        JLabel controls = new JLabel(
                "<html>← → Move<br><br>↑ Rotate<br><br>↓ Soft Drop<br><br>" +
                        "SPACE Hard Drop<br><br>P Pause / Resume<br><br>R Restart</html>"
        );
        controls.setForeground(Color.LIGHT_GRAY);
        controls.setFont(new Font("Arial", Font.PLAIN, 13));

        JButton restartButton = new JButton("Restart Game");
        restartButton.addActionListener(e -> gamePanel.restart());

        JButton pauseButton = new JButton("Pause / Resume");
        pauseButton.addActionListener(e -> gamePanel.togglePause());

        sidePanel.add(title);
        sidePanel.add(Box.createVerticalStrut(30));
        sidePanel.add(scoreLabel);
        sidePanel.add(Box.createVerticalStrut(15));
        sidePanel.add(linesLabel);
        sidePanel.add(Box.createVerticalStrut(15));
        sidePanel.add(levelLabel);
        sidePanel.add(Box.createVerticalStrut(15));
        sidePanel.add(statusLabel);
        sidePanel.add(Box.createVerticalStrut(30));
        sidePanel.add(controlsTitle);
        sidePanel.add(Box.createVerticalStrut(15));
        sidePanel.add(controls);
        sidePanel.add(Box.createVerticalStrut(30));
        sidePanel.add(restartButton);
        sidePanel.add(Box.createVerticalStrut(10));
        sidePanel.add(pauseButton);
        sidePanel.add(Box.createVerticalStrut(30));

        JLabel threadTitle = new JLabel("THREAD MONITOR");
        styleLabel(threadTitle, 14, Color.WHITE);

        sidePanel.add(threadTitle);
        sidePanel.add(Box.createVerticalStrut(12));
        sidePanel.add(gameThreadLabel);
        sidePanel.add(Box.createVerticalStrut(12));
        sidePanel.add(statsThreadLabel);

        add(gamePanel, BorderLayout.CENTER);
        add(sidePanel, BorderLayout.EAST);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        gamePanel.start();
    }

    private void styleLabel(JLabel label, int size, Color color) {
        label.setFont(new Font("Arial", Font.BOLD, size));
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    void updateScore(int score, int lines, int level) {
        scoreLabel.setText("Score: " + score);
        linesLabel.setText("Lines: " + lines);
        levelLabel.setText("Level: " + level);
    }

    void updateStatus(String status, Color color) {
        statusLabel.setText(status);
        statusLabel.setForeground(color);
    }

    void updateThreadMonitor(String gameState, String statsState) {
        gameThreadLabel.setText("<html>Game Thread:<br>" + gameState + "</html>");
        statsThreadLabel.setText("<html>Stats Thread:<br>" + statsState + "</html>");
    }
}

class GamePanel extends JPanel {

    private static final int ROWS = 20;
    private static final int COLS = 10;
    private static final int CELL_SIZE = 30;

    private final int[][] board = new int[ROWS][COLS];
    private final Random random = new Random();

    private final TetrisFrame frame;
    private final AtomicLong tickCount = new AtomicLong();

    private final ScheduledExecutorService gameExecutor =
            Executors.newSingleThreadScheduledExecutor(
                    new NamedThreadFactory("Tetris-Game-Thread")
            );

    private final ScheduledExecutorService statsExecutor =
            Executors.newSingleThreadScheduledExecutor(
                    new NamedThreadFactory("Tetris-Stats-Thread")
            );

    private volatile Thread gameThread;
    private volatile Thread statsThread;

    private Piece currentPiece;
    private int score;
    private int lines;
    private int level = 1;

    private boolean paused;
    private boolean gameOver;

    private long lastDropTime;
    private long dropInterval = 700;

    private final Color[] colors = {
            Color.BLACK,
            Color.CYAN,
            Color.YELLOW,
            Color.MAGENTA,
            Color.GREEN,
            Color.RED,
            Color.BLUE,
            Color.ORANGE
    };

    GamePanel(TetrisFrame frame) {
        this.frame = frame;
        setPreferredSize(new Dimension(COLS * CELL_SIZE, ROWS * CELL_SIZE));
        setBackground(Color.BLACK);
        setFocusable(true);

        setupControls();
        spawnPiece();
    }

    void start() {
        gameExecutor.scheduleAtFixedRate(() -> {
            gameThread = Thread.currentThread();
            tickCount.incrementAndGet();

            SwingUtilities.invokeLater(this::gameTick);

        }, 0, 50, TimeUnit.MILLISECONDS);

        statsExecutor.scheduleAtFixedRate(() -> {
            statsThread = Thread.currentThread();

            String gameState = gameThread == null
                    ? "Starting"
                    : gameThread.getState().toString();

            String statisticsState = statsThread.getState().toString();

            SwingUtilities.invokeLater(() -> {
                frame.updateThreadMonitor(gameState, statisticsState);
            });

        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void setupControls() {
        bindKey("LEFT", "moveLeft", () -> move(-1));
        bindKey("RIGHT", "moveRight", () -> move(1));
        bindKey("DOWN", "softDrop", this::softDrop);
        bindKey("UP", "rotate", this::rotate);
        bindKey("SPACE", "hardDrop", this::hardDrop);
        bindKey("P", "pause", this::togglePause);
        bindKey("R", "restart", this::restart);
    }

    private void bindKey(String key, String actionName, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(key), actionName
        );

        getActionMap().put(actionName, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void gameTick() {
        if (paused || gameOver) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        if (currentTime - lastDropTime >= dropInterval) {
            if (!moveDown()) {
                lockPiece();
            }

            lastDropTime = currentTime;
        }

        repaint();
    }

    private void spawnPiece() {
        currentPiece = new Piece(random.nextInt(7) + 1);
        currentPiece.x = 3;
        currentPiece.y = 0;

        if (collides(currentPiece, 0, 0, currentPiece.shape)) {
            gameOver = true;
            frame.updateStatus("GAME OVER", Color.RED);
        }
    }

    private boolean collides(Piece piece, int offsetX, int offsetY, int[][] shape) {
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {

                if (shape[row][col] == 0) {
                    continue;
                }

                int newX = piece.x + col + offsetX;
                int newY = piece.y + row + offsetY;

                if (newX < 0 || newX >= COLS || newY >= ROWS) {
                    return true;
                }

                if (newY >= 0 && board[newY][newX] != 0) {
                    return true;
                }
            }
        }

        return false;
    }

    private void move(int direction) {
        if (paused || gameOver) {
            return;
        }

        if (!collides(currentPiece, direction, 0, currentPiece.shape)) {
            currentPiece.x += direction;
            repaint();
        }
    }

    private boolean moveDown() {
        if (!collides(currentPiece, 0, 1, currentPiece.shape)) {
            currentPiece.y++;
            return true;
        }

        return false;
    }

    private void softDrop() {
        if (paused || gameOver) {
            return;
        }

        if (moveDown()) {
            score += 1;
            frame.updateScore(score, lines, level);
        } else {
            lockPiece();
        }

        lastDropTime = System.currentTimeMillis();
        repaint();
    }

    private void hardDrop() {
        if (paused || gameOver) {
            return;
        }

        int distance = 0;

        while (moveDown()) {
            distance++;
        }

        score += distance * 2;
        lockPiece();
        frame.updateScore(score, lines, level);
        repaint();
    }

    private void rotate() {
        if (paused || gameOver) {
            return;
        }

        int[][] rotated = currentPiece.rotate();

        if (!collides(currentPiece, 0, 0, rotated)) {
            currentPiece.shape = rotated;
        }

        repaint();
    }

    private void lockPiece() {
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {

                if (currentPiece.shape[row][col] == 0) {
                    continue;
                }

                int x = currentPiece.x + col;
                int y = currentPiece.y + row;

                if (y >= 0 && y < ROWS && x >= 0 && x < COLS) {
                    board[y][x] = currentPiece.id;
                }
            }
        }

        clearLines();
        spawnPiece();

        lastDropTime = System.currentTimeMillis();
        frame.updateScore(score, lines, level);
        repaint();
    }

    private void clearLines() {
        int cleared = 0;

        for (int row = ROWS - 1; row >= 0; row--) {
            boolean full = true;

            for (int col = 0; col < COLS; col++) {
                if (board[row][col] == 0) {
                    full = false;
                    break;
                }
            }

            if (full) {
                cleared++;

                for (int moveRow = row; moveRow > 0; moveRow--) {
                    System.arraycopy(
                            board[moveRow - 1], 0,
                            board[moveRow], 0,
                            COLS
                    );
                }

                for (int col = 0; col < COLS; col++) {
                    board[0][col] = 0;
                }

                row++;
            }
        }

        if (cleared > 0) {
            int[] points = {0, 100, 300, 500, 800};

            score += points[cleared] * level;
            lines += cleared;
            level = (lines / 10) + 1;
            dropInterval = Math.max(100, 700 - (level - 1) * 60);
        }
    }

    void togglePause() {
        if (gameOver) {
            return;
        }

        paused = !paused;

        frame.updateStatus(
                paused ? "PAUSED" : "RUNNING",
                paused ? Color.ORANGE : Color.GREEN
        );

        repaint();
    }

    void restart() {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                board[row][col] = 0;
            }
        }

        score = 0;
        lines = 0;
        level = 1;
        dropInterval = 700;
        paused = false;
        gameOver = false;
        lastDropTime = System.currentTimeMillis();

        spawnPiece();

        frame.updateScore(score, lines, level);
        frame.updateStatus("RUNNING", Color.GREEN);
        repaint();
        requestFocusInWindow();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        drawBoard(g);
        drawCurrentPiece(g);
        drawGrid(g);

        if (paused || gameOver) {
            drawMessage(g, gameOver ? "GAME OVER" : "PAUSED");
        }
    }

    private void drawBoard(Graphics g) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {

                int id = board[row][col];

                if (id != 0) {
                    drawCell(g, col, row, colors[id]);
                }
            }
        }
    }

    private void drawCurrentPiece(Graphics g) {
        if (currentPiece == null) {
            return;
        }

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {

                if (currentPiece.shape[row][col] != 0) {
                    drawCell(
                            g,
                            currentPiece.x + col,
                            currentPiece.y + row,
                            colors[currentPiece.id]
                    );
                }
            }
        }
    }

    private void drawCell(Graphics g, int col, int row, Color color) {
        int x = col * CELL_SIZE;
        int y = row * CELL_SIZE;

        g.setColor(color);
        g.fillRect(x + 1, y + 1, CELL_SIZE - 2, CELL_SIZE - 2);

        g.setColor(color.brighter());
        g.drawLine(x + 2, y + 2, x + CELL_SIZE - 3, y + 2);
        g.drawLine(x + 2, y + 2, x + 2, y + CELL_SIZE - 3);

        g.setColor(color.darker());
        g.drawLine(x + CELL_SIZE - 3, y + 2,
                x + CELL_SIZE - 3, y + CELL_SIZE - 3);
        g.drawLine(x + 2, y + CELL_SIZE - 3,
                x + CELL_SIZE - 3, y + CELL_SIZE - 3);
    }

    private void drawGrid(Graphics g) {
        g.setColor(new Color(45, 45, 45));

        for (int row = 0; row <= ROWS; row++) {
            g.drawLine(0, row * CELL_SIZE, COLS * CELL_SIZE, row * CELL_SIZE);
        }

        for (int col = 0; col <= COLS; col++) {
            g.drawLine(col * CELL_SIZE, 0, col * CELL_SIZE, ROWS * CELL_SIZE);
        }
    }

    private void drawMessage(Graphics g, String message) {
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(0, 240, COLS * CELL_SIZE, 100);

        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 25));

        FontMetrics metrics = g.getFontMetrics();
        int x = (getWidth() - metrics.stringWidth(message)) / 2;

        g.drawString(message, x, 300);
    }
}

class Piece {

    int id;
    int x;
    int y;
    int[][] shape;

    private static final int[][][] SHAPES = {
            {
                    {0, 0, 0, 0},
                    {1, 1, 1, 1},
                    {0, 0, 0, 0},
                    {0, 0, 0, 0}
            },
            {
                    {0, 1, 1, 0},
                    {0, 1, 1, 0},
                    {0, 0, 0, 0},
                    {0, 0, 0, 0}
            },
            {
                    {0, 1, 0, 0},
                    {1, 1, 1, 0},
                    {0, 0, 0, 0},
                    {0, 0, 0, 0}
            },
            {
                    {0, 1, 0, 0},
                    {0, 1, 0, 0},
                    {0, 1, 1, 0},
                    {0, 0, 0, 0}
            },
            {
                    {0, 1, 0, 0},
                    {0, 1, 0, 0},
                    {1, 1, 0, 0},
                    {0, 0, 0, 0}
            },
            {
                    {1, 1, 0, 0},
                    {0, 1, 1, 0},
                    {0, 0, 0, 0},
                    {0, 0, 0, 0}
            },
            {
                    {0, 1, 1, 0},
                    {1, 1, 0, 0},
                    {0, 0, 0, 0},
                    {0, 0, 0, 0}
            }
    };

    Piece(int id) {
        this.id = id;
        this.shape = copyShape(SHAPES[id - 1]);
    }

    int[][] rotate() {
        int[][] rotated = new int[4][4];

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                rotated[row][col] = shape[3 - col][row];
            }
        }

        return rotated;
    }

    private int[][] copyShape(int[][] original) {
        int[][] copy = new int[4][4];

        for (int row = 0; row < 4; row++) {
            System.arraycopy(original[row], 0, copy[row], 0, 4);
        }

        return copy;
    }
}

class NamedThreadFactory implements ThreadFactory {

    private final String threadName;

    NamedThreadFactory(String threadName) {
        this.threadName = threadName;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, threadName);
        thread.setDaemon(true);
        return thread;
    }
}
