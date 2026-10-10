package com.pos.components;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;

/**
 * Renders a generated PDF (declaration, statement) directly inside the app
 * instead of handing it off to whatever PDF viewer Windows has installed —
 * the file still exists on disk as before, and this adds a "Download" button
 * that saves a copy wherever the user picks, so nothing about the previous
 * "find it on disk yourself" path is lost, it's just no longer the only way
 * to see it.
 *
 * Rendering (PDFBox rasterizes each page to a BufferedImage) runs on a
 * background thread — a multi-page PDF can take real time to render, and
 * this follows the same lesson as {@code ReceiptGenerator}: anything that
 * might be slow must never run on the FX Application Thread, or the whole
 * app looks frozen.
 */
public final class PdfViewerDialog {

    private static final Logger log = LoggerFactory.getLogger(PdfViewerDialog.class);
    private static final float RENDER_DPI = 120f;

    private PdfViewerDialog() {}

    public static void show(Window owner, File pdfFile, String title) {
        Stage stage = new Stage();
        stage.setTitle(title);
        if (owner != null) {
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(owner);
        }

        VBox pagesBox = new VBox(12);
        pagesBox.setPadding(new Insets(16));
        pagesBox.setAlignment(Pos.TOP_CENTER);
        pagesBox.setStyle("-fx-background-color: #cbd5e1;");

        ProgressIndicator spinner = new ProgressIndicator();
        VBox loading = new VBox(12, spinner, new Label("Loading " + pdfFile.getName() + "..."));
        loading.setAlignment(Pos.CENTER);
        loading.setPadding(new Insets(40));
        pagesBox.getChildren().add(loading);

        ScrollPane scroll = new ScrollPane(pagesBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #cbd5e1; -fx-background-color: #cbd5e1;");

        Button downloadBtn = Ui.actionButton("Download", "#0f766e", "Save a copy of this PDF");
        downloadBtn.setOnAction(e -> downloadCopy(stage, pdfFile));

        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> stage.close());

        HBox topBar = new HBox(10, new Label(pdfFile.getName()), spacer(), downloadBtn, closeBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(10, 16, 10, 16));
        topBar.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(scroll);

        stage.setScene(new Scene(root, 820, 760));
        stage.show();

        Thread renderThread = new Thread(() -> renderPages(pdfFile, pagesBox, loading), "Pdf-Render");
        renderThread.setDaemon(true);
        renderThread.start();
    }

    private static javafx.scene.layout.Region spacer() {
        javafx.scene.layout.Region r = new javafx.scene.layout.Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private static void renderPages(File pdfFile, VBox pagesBox, VBox loading) {
        try (PDDocument doc = PDDocument.load(pdfFile)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int pageCount = doc.getNumberOfPages();

            for (int i = 0; i < pageCount; i++) {
                BufferedImage buffered = renderer.renderImageWithDPI(i, RENDER_DPI);
                Image fxImage = SwingFXUtils.toFXImage(buffered, null);
                ImageView view = new ImageView(fxImage);
                view.setPreserveRatio(true);
                view.setFitWidth(760);
                view.setStyle("-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.35), 12, 0, 0, 3);");

                Platform.runLater(() -> {
                    if (pagesBox.getChildren().contains(loading)) pagesBox.getChildren().remove(loading);
                    pagesBox.getChildren().add(view);
                });
            }

            if (pageCount == 0) {
                Platform.runLater(() -> {
                    pagesBox.getChildren().setAll(new Label("This PDF has no pages."));
                });
            }
        } catch (Exception e) {
            log.error("Could not render PDF for in-app viewing: {}", pdfFile, e);
            Platform.runLater(() -> pagesBox.getChildren().setAll(
                    new Label("Could not display this PDF in the app (" + e.getMessage() + "). "
                            + "Use Download to save it and open it another way.")));
        }
    }

    private static void downloadCopy(Stage owner, File pdfFile) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save a copy");
        chooser.setInitialFileName(pdfFile.getName());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        File target = chooser.showSaveDialog(owner);
        if (target == null) return;

        try {
            Files.copy(pdfFile.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Ui.showAlert(owner.getScene() != null ? owner.getScene().getRoot() : null,
                    "Saved", "Saved to " + target.getAbsolutePath(), Alert.AlertType.INFORMATION);
        } catch (Exception e) {
            log.error("Could not save PDF copy to {}", target, e);
            Ui.showAlert(owner.getScene() != null ? owner.getScene().getRoot() : null,
                    "Could not save", "Could not save the file: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }
}
