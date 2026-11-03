// SPDX-License-Identifier: GPL-3.0-or-later
package com.kingfischer.engine.gui;

import com.kingfischer.engine.chess.Move;
import com.kingfischer.engine.chess.Position;
import com.kingfischer.engine.movegen.MoveGenerator;
import java.util.function.IntConsumer;
import javafx.animation.*;
import javafx.beans.property.*;
import javafx.geometry.VPos;
import javafx.scene.canvas.*;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.util.Duration;

/**
 * Responsive board with click/drag input and a short move animation.
 */
public final class BoardPanel extends Region {

    private final Canvas canvas = new Canvas();
    private final ChessImages images = new ChessImages();
    private final IntConsumer action;
    private Position position = new Position();
    private BoardTheme theme = BoardTheme.SAGE;
    private boolean flipped, input;
    private int selected = -1, last = 0, press = -1, dragPiece;
    private double dragX, dragY, startX, startY;
    private boolean dragging;
    private final DoubleProperty progress = new SimpleDoubleProperty(1);
    private Timeline animation;
    private int animMove, animPiece;

    public BoardPanel(IntConsumer action) {
        this.action = action;
        getChildren().add(canvas);
        setMinSize(280, 280);
        setPrefSize(640, 640);
        progress.addListener((o, a, b) -> draw());
        canvas.setOnMousePressed(
                e -> {
                    if (!input || e.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
                        return;
                    }
                    stopAnimation();
                    press = squareAt(e.getX(), e.getY());
                    if (press < 0) {
                        return;
                    }
                    startX = e.getX();
                    startY = e.getY();
                    dragX = startX;
                    dragY = startY;
                    dragPiece = position.board[press];
                    action.accept(press);
                });
        canvas.setOnMouseDragged(
                e -> {
                    if (!input || press < 0 || selected != press || dragPiece == 0) {
                        return;
                    }
                    if (Math.hypot(e.getX() - startX, e.getY() - startY) > 4) {
                        dragging = true;
                    }
                    dragX = e.getX();
                    dragY = e.getY();
                    draw();
                });
        canvas.setOnMouseReleased(
                e -> {
                    boolean moved = dragging;
                    int target = squareAt(e.getX(), e.getY());
                    dragging = false;
                    press = -1;
                    if (moved && target >= 0) {
                        action.accept(target);
                    }
                    draw();
                });
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        draw();
    }

    public void show(
            Position p, BoardTheme t, boolean flip, int selection, int lastMove, boolean enabled) {
        position = p;
        theme = t;
        flipped = flip;
        selected = selection;
        last = lastMove;
        input = enabled;
        if (!enabled) {
            dragging = false;
            press = -1;
        }
        draw();
    }

    public void animate(int move, int piece) {
        stopAnimation();
        animMove = move;
        animPiece = piece;
        progress.set(0);
        animation
                = new Timeline(
                        new KeyFrame(Duration.millis(170), new KeyValue(progress, 1, Interpolator.EASE_BOTH)));
        animation.play();
    }

    public void stopAnimation() {
        if (animation != null) {
            animation.stop();
        }
        progress.set(1);
    }

    private double cell() {
        return Math.min(getWidth(), getHeight()) / 8;
    }

    private double ox() {
        return (getWidth() - cell() * 8) / 2;
    }

    private double oy() {
        return (getHeight() - cell() * 8) / 2;
    }

    public int squareAt(double x, double y) {
        double c = cell();
        x -= ox();
        y -= oy();
        if (c <= 0 || x < 0 || y < 0 || x >= c * 8 || y >= c * 8) {
            return -1;
        }
        int col = (int) (x / c), row = (int) (y / c);
        return flipped ? row * 8 + 7 - col : (7 - row) * 8 + col;
    }

    private double x(int sq) {
        return ox() + (flipped ? 7 - (sq & 7) : sq & 7) * cell();
    }

    private double y(int sq) {
        return oy() + (flipped ? sq >>> 3 : 7 - (sq >>> 3)) * cell();
    }

    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, getWidth(), getHeight());
        double c = cell();
        if (c <= 0) {
            return;
        }
        for (int sq = 0; sq < 64; sq++) {
            double x = x(sq), y = y(sq);
            g.setFill(((sq & 7) + (sq >>> 3)) % 2 == 1 ? theme.light : theme.dark);
            g.fillRect(x, y, c, c);
            if (last != 0 && (Move.from(last) == sq || Move.to(last) == sq)) {
                g.setFill(Color.web("#efd457", .42));
                g.fillRect(x, y, c, c);
            }
            if (sq == selected) {
                g.setFill(Color.web("#ead675", .70));
                g.fillRect(x, y, c, c);
            }
            if (position.inCheck() && sq == position.king(position.side)) {
                g.setFill(Color.web("#d55757", .75));
                g.fillRect(x, y, c, c);
            }
            int piece = position.board[sq];
            if (piece != 0
                    && !(dragging && sq == selected)
                    && !(progress.get() < 1 && sq == Move.to(animMove))) {
                paintPiece(g, piece, x, y, c);
            }
        }
        if (selected >= 0 && input) {
            int[] legal = new int[256];
            int n = MoveGenerator.legal(position, legal);
            for (int i = 0; i < n; i++) {
                if (Move.from(legal[i]) == selected) {
                    int to = Move.to(legal[i]);
                    g.setFill(Color.web("#193e2b", .26));
                    if (position.board[to] == 0) {
                        g.fillOval(x(to) + c * .39, y(to) + c * .39, c * .22, c * .22);
                    } else {
                        g.setStroke(Color.web("#193e2b", .45));
                        g.setLineWidth(c * .065);
                        g.strokeOval(x(to) + c * .08, y(to) + c * .08, c * .84, c * .84);
                    }
                }
            }
        }
        g.setFont(Font.font("System", FontWeight.BOLD, Math.max(10, c * .14)));
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.TOP);
        for (int i = 0; i < 8; i++) {
            int rank = flipped ? i + 1 : 8 - i, file = flipped ? 7 - i : i;
            g.setFill(i % 2 == 0 ? theme.dark : theme.light);
            g.fillText("" + rank, ox() + c * .05, oy() + i * c + c * .04);
            g.setFill(i % 2 == 0 ? theme.light : theme.dark);
            g.fillText("" + (char) ('a' + file), ox() + (i + 1) * c - c * .16, oy() + 8 * c - c * .20);
        }
        if (progress.get() < 1) {
            double t = progress.get();
            paintPiece(
                    g,
                    animPiece,
                    x(Move.from(animMove)) * (1 - t) + x(Move.to(animMove)) * t,
                    y(Move.from(animMove)) * (1 - t) + y(Move.to(animMove)) * t,
                    c);
        }
        if (dragging) {
            paintPiece(g, dragPiece, dragX - c / 2, dragY - c / 2, c);
        }
    }

    private void paintPiece(GraphicsContext g, int code, double x, double y, double c) {
        Image img = images.piece(code);
        if (img != null) {
            g.drawImage(img, x + c * .045, y + c * .045, c * .91, c * .91);
            return;
        }
        String glyph
                = new String(
                        Character.toChars(
                                new int[]{0, 0x265f, 0x265e, 0x265d, 0x265c, 0x265b, 0x265a}[code & 7]));
        g.setFont(Font.font("DejaVu Sans", c * .82));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.setLineWidth(c * .025);
        g.setStroke(code < 8 ? Color.web("#393c39") : Color.web("#e9e9df"));
        g.strokeText(glyph, x + c * .5, y + c * .49);
        g.setFill(code < 8 ? Color.web("#fffdf3") : Color.web("#242925"));
        g.fillText(glyph, x + c * .5, y + c * .49);
    }
}
