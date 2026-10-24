/**
 * JavaFX screens and orchestration of user actions.
 *
 * <p>Build forms from the shared {@code Ui}, {@code Theme}, {@code Icons} and
 * branding helpers. Keep invalid input visible and group related fields under
 * labels. Views collect inputs; services enforce authorization and financial
 * invariants. JavaFX nodes are accessed on the application thread; background
 * work returns UI updates through {@link javafx.application.Platform#runLater}.
 *
 * <p>Several views retain resources across navigation. Follow their cleanup and
 * listener lifecycle before adding polling, connection handlers or async previews.
 */
package com.pos.views;
