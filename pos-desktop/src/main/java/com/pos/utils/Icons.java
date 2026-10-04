package com.pos.utils;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;

/**
 * Small line-style icons built from plain JavaFX shape primitives — no icon
 * font, no SVG path strings to get wrong, no extra Maven dependency that
 * would have to survive the jlink/jpackage shaded-jar pipeline. Every icon
 * renders identically on every machine because it isn't a font glyph that
 * depends on what's installed.
 *
 * All icons are drawn on an 18x18 logical grid and returned at a caller-given
 * size, stroked (not filled) in a caller-given colour, matching the outline
 * style used throughout {@link Theme}.
 */
public final class Icons {
    private Icons() {}

    private static final double GRID = 18;

    /** Marks a small shape (e.g. the dot under an exclamation mark) as solid-filled instead of outlined. */
    private static Circle solid(Circle c) {
        c.setUserData("filled");
        return c;
    }

    private static Node frame(double size, Node... shapes) {
        for (Node n : shapes) {
            if (n instanceof Shape s) {
                boolean filled = "filled".equals(s.getUserData());
                if (s.getStroke() == null) s.setStroke(Color.web(Theme.TEXT));
                if (filled) {
                    s.setFill(s.getStroke());
                    s.setStrokeWidth(0);
                } else {
                    s.setFill(Color.TRANSPARENT);
                    s.setStrokeWidth(1.6);
                }
                s.setStrokeLineCap(StrokeLineCap.ROUND);
                s.setStrokeLineJoin(StrokeLineJoin.ROUND);
            }
        }
        Group g = new Group(shapes);
        double scale = size / GRID;
        g.setScaleX(scale);
        g.setScaleY(scale);
        StackPane pane = new StackPane(g);
        pane.setPrefSize(size, size);
        pane.setMinSize(size, size);
        pane.setMaxSize(size, size);
        return pane;
    }

    /** Recolours an icon built by this class (the nav uses this to show the active/hover tint). */
    public static Node tinted(Node icon, String colourHex) {
        if (icon instanceof StackPane p && !p.getChildren().isEmpty() && p.getChildren().get(0) instanceof Group g) {
            Color c = Color.web(colourHex);
            for (Node n : g.getChildren()) {
                if (n instanceof Shape s) {
                    s.setStroke(c);
                    if ("filled".equals(s.getUserData())) s.setFill(c);
                }
            }
        }
        return icon;
    }

    // ── Nav icons ────────────────────────────────────────────────────────

    public static Node sales(double size) {
        Rectangle card = new Rectangle(1, 4, 16, 11);
        card.setArcWidth(3); card.setArcHeight(3);
        Line stripe = new Line(1, 7.5, 17, 7.5);
        Line chip = new Line(3.5, 11, 7, 11);
        return frame(size, card, stripe, chip);
    }

    public static Node inventory(double size) {
        Polyline box = new Polyline(2,6, 9,2, 16,6, 16,14, 9,18, 2,14, 2,6);
        Line mid = new Line(2, 6, 9, 10);
        Line mid2 = new Line(16, 6, 9, 10);
        Line vert = new Line(9, 10, 9, 18);
        return frame(size, box, mid, mid2, vert);
    }

    public static Node users(double size) {
        Circle head = new Circle(7, 6, 2.6);
        Arc body = new Arc(7, 17, 6.5, 6.5, 0, 180);
        body.setType(ArcType.OPEN);
        Circle head2 = new Circle(13.5, 7.5, 2.1);
        Arc body2 = new Arc(13.5, 17.5, 4.8, 4.8, 0, 160);
        body2.setType(ArcType.OPEN);
        return frame(size, body, head, body2, head2);
    }

    public static Node customers(double size) {
        Polyline bag = new Polyline(3,6, 15,6, 14,17, 4,17, 3,6);
        Arc handle = new Arc(9, 6, 3.2, 4.5, 0, 180);
        handle.setType(ArcType.OPEN);
        return frame(size, bag, handle);
    }

    public static Node reports(double size) {
        Line axisV = new Line(2, 2, 2, 16);
        Line axisH = new Line(2, 16, 16, 16);
        Line bar1 = new Line(5, 13, 5, 8);
        Line bar2 = new Line(9, 13, 9, 5);
        Line bar3 = new Line(13, 13, 13, 10);
        return frame(size, axisV, axisH, bar1, bar2, bar3);
    }

    public static Node sessions(double size) {
        Circle face = new Circle(9, 9, 7);
        Line hourHand = new Line(9, 9, 9, 5);
        Line minHand = new Line(9, 9, 12, 10);
        return frame(size, face, hourHand, minHand);
    }

    public static Node approvals(double size) {
        Circle ring = new Circle(9, 9, 7.5);
        Polyline tick = new Polyline(5.5,9.2, 8,12, 13,6.5);
        return frame(size, ring, tick);
    }

    public static Node marketing(double size) {
        Rectangle env = new Rectangle(1.5, 4, 15, 10);
        env.setArcWidth(2); env.setArcHeight(2);
        Polyline flap = new Polyline(1.5,4.5, 9,10.5, 16.5,4.5);
        return frame(size, env, flap);
    }

    public static Node subscription(double size) {
        Circle bow = new Circle(6, 6, 3.4);
        Line shaft = new Line(8.4, 8.4, 16, 16);
        Line tooth1 = new Line(12.5, 12.5, 14.5, 10.5);
        Line tooth2 = new Line(14.5, 14.5, 16.5, 12.5);
        return frame(size, bow, shaft, tooth1, tooth2);
    }

    public static Node settings(double size) {
        Circle outer = new Circle(9, 9, 6.5);
        Circle inner = new Circle(9, 9, 2.6);
        Shape ring = Shape.subtract(outer, inner);
        ring.setStroke(Color.web(Theme.TEXT));
        ring.setStrokeWidth(0.4);
        Line n = new Line(9, 0.8, 9, 2.6);
        Line s = new Line(9, 15.4, 9, 17.2);
        Line e = new Line(15.4, 9, 17.2, 9);
        Line w = new Line(0.8, 9, 2.6, 9);
        Line ne = new Line(13.6, 3.4, 14.8, 4.6);
        Line sw = new Line(3.2, 13.4, 4.4, 14.6);
        Line nw = new Line(3.2, 4.6, 4.4, 3.4);
        Line se = new Line(13.6, 14.6, 14.8, 13.4);
        return frame(size, ring, n, s, e, w, ne, sw, nw, se);
    }

    public static Node logout(double size) {
        Rectangle frameRect = new Rectangle(2, 2, 8, 14);
        frameRect.setArcWidth(2); frameRect.setArcHeight(2);
        Line arrowShaft = new Line(8, 9, 16, 9);
        Polyline arrowHead = new Polyline(12.5,5.5, 16,9, 12.5,12.5);
        return frame(size, frameRect, arrowShaft, arrowHead);
    }

    // ── Small action/status glyphs (table buttons, alerts) ──────────────

    public static Node check(double size) {
        Polyline tick = new Polyline(3,9.5, 7.5,14, 15,4);
        return frame(size, tick);
    }

    public static Node close(double size) {
        Line a = new Line(4, 4, 14, 14);
        Line b = new Line(14, 4, 4, 14);
        return frame(size, a, b);
    }

    public static Node warning(double size) {
        Polyline tri = new Polyline(9,2, 16.5,15.5, 1.5,15.5, 9,2);
        Line stem = new Line(9, 7, 9, 11.5);
        Circle dot = solid(new Circle(9, 13.5, 0.9));
        return frame(size, tri, stem, dot);
    }

    public static Node edit(double size) {
        Line shaft = new Line(3, 15, 11, 7);
        Polyline tip = new Polyline(11,7, 14,4, 16,6, 13,9, 11,7);
        Line underline = new Line(3, 15, 5, 15);
        return frame(size, shaft, tip, underline);
    }

    public static Node delete(double size) {
        Rectangle bin = new Rectangle(4, 5, 10, 11);
        bin.setArcWidth(2); bin.setArcHeight(2);
        Line lid = new Line(2.5, 5, 15.5, 5);
        Line cap = new Line(6.5, 5, 6.5, 3);
        Line cap2 = new Line(11.5, 5, 11.5, 3);
        return frame(size, bin, lid, cap, cap2);
    }

    public static Node refresh(double size) {
        Arc sweep = new Arc(9, 9, 6, 6, 20, 280);
        sweep.setType(ArcType.OPEN);
        Polyline head = new Polyline(13.5,2.5, 15,6.5, 11,5.5);
        return frame(size, sweep, head);
    }

    public static Node wifi(double size) {
        Arc big = new Arc(9, 15, 8, 8, 20, 140);
        big.setType(ArcType.OPEN);
        Arc mid = new Arc(9, 15, 5, 5, 20, 140);
        mid.setType(ArcType.OPEN);
        Circle dot = solid(new Circle(9, 14.5, 1));
        return frame(size, big, mid, dot);
    }

    /** A plain solid status dot (online/offline indicators), recolour with {@link #tinted}. */
    public static Node dot(double size) {
        Circle c = solid(new Circle(9, 9, 6));
        return frame(size, c);
    }

    public static Node plus(double size) {
        Line h = new Line(3, 9, 15, 9);
        Line v = new Line(9, 3, 9, 15);
        return frame(size, h, v);
    }
}
