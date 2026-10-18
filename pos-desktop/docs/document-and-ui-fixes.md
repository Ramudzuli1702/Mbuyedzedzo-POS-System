# Desktop 1.0.7 and Scanner 1.4.1

Barcode labels print through a separate AWT printable instead of sending the live JavaFX product detail controls to the printer. Printer selection runs on the AWT event thread and spooling runs on a worker, leaving the till responsive. Missing printers, cancellation and print errors return control to the product screen. Labels fit the selected printable area without enlarging on office paper.

Generated inventory/stocktake spreadsheets, report workbooks, session PDFs and receipt text files use `Mbuyedzedzo_Type_Reference_yyyy-MM-dd_HHmmss_SSS.ext`. References are sanitized for Windows filenames. Timestamps use the desktop's local time; repeated exports receive distinct default filenames. Users can still change filenames in save dialogs. Existing files and release download URLs are unchanged.

Reports, inventory, customers, users and marketing share the same summary card renderer: neutral surface, slate value, teal accent, consistent typography and spacing. Report drilldowns keep mouse and keyboard access. Screen help uses direct instructions throughout.

The Android receipt screen inherits the Material app theme. It previously overrode it with AppCompat, incompatible with its Material cards. Receipt navigation now loads by saved ID and handles missing receipts without ending the staff session. The stocktaking icon uses white strokes on its teal chip.

Validation: 57 desktop unit tests, 13 Android unit tests and 10 MySQL/socket/desktop checks passed. Desktop unit tests include barcode print rendering/scanning and filename safety/uniqueness. MySQL/socket tests and desktop snapshots use an isolated disposable database. Android unit tests and release builds are checked. Device tests for receipt layout inflation and icon contrast are included under `androidTest`; the test APK compiles, but execution requires an Android device. Physical printer output, a phone walkthrough and a clean Windows installation still need manual checks.
