# Desktop 1.0.8

Historical change note. See the [user guide](user-guide.md) for current operations and [promotions](promotions.md) for the later retail-offer workflow. Test counts below belong to this change.

Product details and barcode dialogs offer **Save barcode**, which opens a local PNG save dialog. The saved image includes high-resolution Code 128 bars and the readable barcode value. Default filenames use the shared document convention. Cancelling does nothing; invalid barcodes and write failures show inline feedback. The barcode print button and its printer helper have been removed.

Reports replaces the Business Intelligence screen with a short overview: four sales metrics, daily revenue, payment methods and top products. The report selector opens the detailed sales, products, customers, staff, stock, returns/exchanges and promotions reports available in the edition. Retail omits customer and email campaign reports.

Filters wrap to fit the window. The date summary states the selected range and identifies current stock/account figures that do not follow it. This week begins on Monday; this month begins on the first day of the calendar month. Invalid ranges prevent export. The export button explicitly describes its existing full-workbook behavior, with a neutral Reports filename.

Charts use the shared teal/slate palette, flat bars and subtle gridlines. Customer pie charts use matching legend colours. Request status charts use labelled bars with green for approved, amber for pending and red for declined/rejected. Growth badges describe direction in teal, without treating every increase as good. Existing shared summary cards and action styles are retained.

Checks: all 57 desktop unit tests and report rendering checks in Standard and Retail passed. Desktop unit tests cover saved PNG decoding, readable labels and invalid input. A JavaFX fixture test renders every report without accessing a business database, checks actual bar colours, calendar shortcuts and invalid-range handling, and writes `target/reports-overview.png`. The installers use edition/version-specific input folders to keep older JARs out of the payload. Android is unchanged.
