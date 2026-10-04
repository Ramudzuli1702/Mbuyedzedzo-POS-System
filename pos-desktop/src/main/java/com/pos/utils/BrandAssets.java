package com.pos.utils;

import com.pos.Branding;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loads the bundled logo images once and hands out fresh ImageViews sized to fit. */
public final class BrandAssets {

    private static final Logger log = LoggerFactory.getLogger(BrandAssets.class);

    private static Image logo;
    private static Image logoMark;
    private static boolean loaded;

    private BrandAssets() {}

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        logo = load(Branding.LOGO_PATH);
        logoMark = load(Branding.LOGO_MARK_PATH);
    }

    private static Image load(String path) {
        try {
            var in = BrandAssets.class.getResourceAsStream(path);
            if (in == null) {
                log.warn("Brand asset missing from the build: {}", path);
                return null;
            }
            return new Image(in);
        } catch (Exception e) {
            log.warn("Could not load brand asset {}: {}", path, e.getMessage());
            return null;
        }
    }

    /** The full logo (mark + wordmark), fit to the given height. Null if the asset is missing. */
    public static ImageView logo(double height) {
        ensureLoaded();
        return view(logo, height);
    }

    /** The mark-only logo (transparent background), fit to the given height. Null if missing. */
    public static ImageView logoMark(double height) {
        ensureLoaded();
        ImageView iv = view(logoMark, height);
        if (iv != null) {
            // The arrow accent extends further right than the M-body extends
            // left, so the image's bounding box isn't visually centered — its
            // alpha-weighted centroid sits ~19.3% of the image width left of the
            // bbox's own center. Nudge the rendered pixels right so it *looks*
            // centered wherever this is placed in a centered container.
            // translateX is a paint-only offset (doesn't affect layout bounds),
            // so it doesn't disturb whatever alignment positions the ImageView.
            double renderedWidth = height * (logoMark.getWidth() / logoMark.getHeight());
            iv.setTranslateX(renderedWidth * 0.193);
        }
        return iv;
    }

    private static final int[] ICON_SIZES = {16, 24, 32, 48, 64, 128, 256};

    /**
     * Sets the window/taskbar icon (title bar, Alt+Tab, taskbar) at every size
     * Windows asks for, so it's never upscaled/blurry in one context and
     * missing in another. These PNGs are pre-centered for the mark's known
     * visual-weight asymmetry (see {@link #logoMark}) — unlike that method,
     * a Stage icon can't be nudged via a paint-only translateX, so the
     * correction has to be baked into the pixels themselves.
     */
    public static void applyAppIcons(Stage stage) {
        for (int size : ICON_SIZES) {
            String path = "/brand/icons/icon-" + size + ".png";
            var in = BrandAssets.class.getResourceAsStream(path);
            if (in == null) {
                log.warn("App icon asset missing from the build: {}", path);
                continue;
            }
            stage.getIcons().add(new Image(in));
        }
    }

    private static ImageView view(Image img, double height) {
        if (img == null) return null;
        ImageView iv = new ImageView(img);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        iv.setFitHeight(height);
        return iv;
    }
}
