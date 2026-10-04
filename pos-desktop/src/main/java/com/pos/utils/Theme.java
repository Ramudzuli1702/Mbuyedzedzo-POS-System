package com.pos.utils;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * The single design system every view should build on. Centralising this is
 * what makes "premium" hold together app-wide instead of being one nice
 * screen next to a dozen ad hoc ones — before this, views had accumulated
 * three generations of palette (flat-UI blues/greys, Bootstrap reds/greens,
 * Tailwind slates) side by side. Everything here standardises on one: brand
 * teal + a slate neutral scale + one colour per semantic meaning.
 */
public final class Theme {
    private Theme() {}

    // ── Brand ──────────────────────────────────────────────────────────────
    public static final String BRAND         = "#0f766e";
    public static final String BRAND_DARK    = "#0c5c57";
    public static final String BRAND_TINT    = "#e6f4f2"; // chips, selected rows
    public static final String BRAND_TINT_2  = "#ccfbf1"; // softer hover tint

    // ── Neutrals (slate) ─────────────────────────────────────────────────
    public static final String BG            = "#f5f7fa"; // app/page background
    public static final String SURFACE       = "#ffffff"; // cards, panels
    public static final String BORDER        = "#e2e8f0";
    public static final String BORDER_STRONG = "#cbd5e1";
    public static final String TEXT          = "#1e293b"; // primary text
    public static final String TEXT_MUTED    = "#64748b"; // secondary text
    public static final String TEXT_FAINT    = "#94a3b8"; // placeholders, meta

    // ── Semantic (one colour per meaning, used everywhere) ─────────────────
    public static final String SUCCESS      = "#16a34a";
    public static final String SUCCESS_TINT = "#dcfce7";
    public static final String DANGER       = "#dc2626";
    public static final String DANGER_TINT  = "#fee2e2";
    public static final String WARNING      = "#d97706";
    public static final String WARNING_TINT = "#fef3c7";
    public static final String INFO         = "#2563eb";
    public static final String INFO_TINT    = "#dbeafe";

    // ── Shape ────────────────────────────────────────────────────────────
    public static final int RADIUS_SM = 8;   // buttons, inputs, chips
    public static final int RADIUS_MD = 14;  // cards, panels
    public static final int RADIUS_LG = 20;  // modals, hero surfaces

    /** Soft, layered shadow — reads as "premium" instead of the flat 1-px-offset default. */
    public static final String SHADOW_CARD =
            "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 18, 0, 0, 4);";
    public static final String SHADOW_RAISED =
            "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.14), 24, 0, 0, 8);";

    // ── Surfaces ─────────────────────────────────────────────────────────
    public static String page() {
        return "-fx-background-color: " + BG + ";";
    }

    public static String card() {
        return "-fx-background-color: " + SURFACE + "; -fx-background-radius: " + RADIUS_MD + "px;" + SHADOW_CARD;
    }

    public static String cardRaised() {
        return "-fx-background-color: " + SURFACE + "; -fx-background-radius: " + RADIUS_MD + "px;" + SHADOW_RAISED;
    }

    /**
     * A stat/KPI card with a permanent colour-coded top accent bar — not just
     * a card background. A card that relies only on dynamically-shown content
     * (a growth badge, a status chip) for its colour can end up looking
     * completely undesigned the moment that content is absent (e.g. a fresh
     * database with no growth to show yet); this guarantees every card has a
     * visible identity regardless of its data.
     */
    public static String statCard(String accentColorHex) {
        return "-fx-background-color: " + SURFACE + "; -fx-background-radius: " + RADIUS_MD + "px;"
            + "-fx-border-color: " + accentColorHex + " transparent transparent transparent;"
            + "-fx-border-width: 3 0 0 0; -fx-border-radius: " + RADIUS_MD + " " + RADIUS_MD + " 0 0;"
            + SHADOW_CARD;
    }

    public static String topBar() {
        return "-fx-background-color: " + SURFACE + "; -fx-border-color: " + BORDER + "; -fx-border-width: 0 0 1 0;";
    }

    public static String input() {
        return "-fx-background-color: " + SURFACE + "; -fx-background-radius: " + RADIUS_SM + "px;"
                + "-fx-border-color: " + BORDER + "; -fx-border-radius: " + RADIUS_SM + "px; -fx-border-width: 1.5px;"
                + "-fx-padding: 10 14; -fx-font-size: 13px;";
    }

    public static String chip(String bg, String fg) {
        return "-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; -fx-font-weight: bold;"
                + "-fx-padding: 6 14; -fx-background-radius: 20px; -fx-font-size: 12px;";
    }

    // ── Buttons ──────────────────────────────────────────────────────────
    private static String buttonBase(String bg, String fg) {
        return "-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; -fx-font-weight: bold;"
                + "-fx-font-size: 13px; -fx-padding: 10 20; -fx-background-radius: " + RADIUS_SM + "px;"
                + "-fx-cursor: hand;";
    }

    public static String primaryButton()   { return buttonBase(BRAND, "white"); }
    public static String primaryHover()    { return buttonBase(BRAND_DARK, "white"); }
    public static String dangerButton()    { return buttonBase(DANGER, "white"); }
    public static String dangerHover()     { return buttonBase("#b91c1c", "white"); }
    public static String successButton()   { return buttonBase(SUCCESS, "white"); }
    public static String successHover()    { return buttonBase("#15803d", "white"); }

    public static String secondaryButton() {
        return "-fx-background-color: " + SURFACE + "; -fx-text-fill: " + BRAND + "; -fx-font-weight: bold;"
                + "-fx-font-size: 13px; -fx-padding: 9 19; -fx-background-radius: " + RADIUS_SM + "px;"
                + "-fx-border-color: " + BRAND + "; -fx-border-width: 1.5px; -fx-border-radius: " + RADIUS_SM + "px;"
                + "-fx-cursor: hand;";
    }
    public static String secondaryHover() {
        return "-fx-background-color: " + BRAND_TINT + "; -fx-text-fill: " + BRAND_DARK + "; -fx-font-weight: bold;"
                + "-fx-font-size: 13px; -fx-padding: 9 19; -fx-background-radius: " + RADIUS_SM + "px;"
                + "-fx-border-color: " + BRAND + "; -fx-border-width: 1.5px; -fx-border-radius: " + RADIUS_SM + "px;"
                + "-fx-cursor: hand;";
    }

    public static String ghostButton() {
        return "-fx-background-color: transparent; -fx-text-fill: " + TEXT_MUTED + "; -fx-font-weight: bold;"
                + "-fx-font-size: 13px; -fx-padding: 9 16; -fx-background-radius: " + RADIUS_SM + "px;"
                + "-fx-cursor: hand;";
    }
    public static String ghostHover() {
        return "-fx-background-color: " + BG + "; -fx-text-fill: " + TEXT + "; -fx-font-weight: bold;"
                + "-fx-font-size: 13px; -fx-padding: 9 16; -fx-background-radius: " + RADIUS_SM + "px;"
                + "-fx-cursor: hand;";
    }

    /** Wires a hover swap so buttons give real feedback instead of sitting dead/flat. */
    public static void hover(Button button, String base, String hovered) {
        button.setStyle(base);
        button.setOnMouseEntered(e -> button.setStyle(hovered));
        button.setOnMouseExited(e -> button.setStyle(base));
    }

    // ── Typography ───────────────────────────────────────────────────────
    public static Font pageTitle()    { return Font.font("System", FontWeight.BOLD, 24); }
    public static Font sectionTitle() { return Font.font("System", FontWeight.BOLD, 18); }
    public static Font label()        { return Font.font("System", FontWeight.SEMI_BOLD, 13); }
    public static Font body()         { return Font.font("System", FontWeight.NORMAL, 13); }
    public static Font meta()         { return Font.font("System", FontWeight.NORMAL, 11); }

    public static Label pageTitleLabel(String text) {
        Label l = new Label(text);
        l.setFont(pageTitle());
        l.setTextFill(Color.web(BRAND));
        return l;
    }

    public static Label sectionTitleLabel(String text) {
        Label l = new Label(text);
        l.setFont(sectionTitle());
        l.setTextFill(Color.web(TEXT));
        return l;
    }
}
