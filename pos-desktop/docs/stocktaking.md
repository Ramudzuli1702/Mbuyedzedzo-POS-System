# Desktop stocktaking and Excel sheets

Every signed-in staff member can open **Stocktaking** in the sidebar. **New stocktake** captures current inventory; select an existing sheet to resume your own saved counts or see its review status. Each physical count and note is saved to the database. Search by product, category or barcode; a USB barcode scan followed by Enter opens the exact matching product's count dialog.

Enter whole quantities, including **0** when none remain. Leave a quantity blank to mark it **not counted**. Uncounted products stay unchanged. Counted products show their variance from the stock originally loaded.

## Excel import and export

1. Start or select a Draft and choose **Export to Excel**. The workbook contains the full inventory snapshot, saved physical counts and an Instructions sheet. Barcodes export as text, preserving leading zeroes.
2. Fill the highlighted **Physical Count** column in Excel. Keep Stocktake ID, Product ID, Barcode and Loaded Stock unchanged. Rows may be sorted or omitted. Whole counts range from 0 to 1,000,000; formulas are not accepted as counts.
3. Open that same draft and choose **Import from Excel** (`.xlsx` or `.xls`). The app validates the template, snapshot identity, product IDs, barcodes, baseline quantities and counts. It rejects duplicate/unknown products, changed baselines, formulas and invalid quantities. Any error prevents the entire import; correct the reported rows and try again.
4. Review the product/count/variance preview, then confirm. Import replaces draft counts: blank and omitted products become uncounted. It does not create products, edit the original snapshot or change live stock.
5. Choose **Submit for approval**. The sheet becomes read-only. An Admin or Manager reviews it under **Approvals > Stocktakes**. Submitted desktop and phone stocktakes share the same approval process.

Approval applies all counted corrections in one transaction. Any intervening stock movement, including a change that later returns to the original quantity, blocks approval and requires a fresh count. Rejection keeps inventory unchanged and records review notes. Refresh your stocktake to see the decision.

Export remains available for submitted, approved and rejected sheets. Managers can also export any selected stocktake from the approval screen. Historical exports include the original baseline, physical counts, status and review notes.

Desktop and phone counts are associated with the same staff account and snapshot. Phone counts not yet submitted are stored locally on that phone; they do not appear as saved desktop draft counts until the phone submits them. Avoid editing one draft simultaneously on two devices.

## Verification

`mvn test` includes stocktake workbook round trips and validation tests. `src/test/powershell/Verify-Companion.ps1` runs real draft-save/import/submit/approve and rollback checks on a disposable MySQL instance, without reading the shop database configuration. On a Windows machine with JavaFX rendering available, add `-DesktopUi` to render the desktop screen using test inventory and save `target/desktop-stocktaking.png`.
