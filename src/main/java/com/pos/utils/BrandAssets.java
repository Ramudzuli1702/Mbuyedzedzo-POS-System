package com.pos.utils;

import com.pos.Branding;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
        return view(logoMark, height);
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
