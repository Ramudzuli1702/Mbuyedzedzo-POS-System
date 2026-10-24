# Desktop stocktaking and Excel sheets

See the [user guide](user-guide.md) for first setup and the [troubleshooting guide](troubleshooting.md#stocktake-approval-is-blocked) for approval conflicts. These instructions describe the current source, which may be newer than installed releases.

Every signed-in staff member can open **Stocktaking** in the sidebar. **New stocktake** captures current inventory; select an existing sheet to resume your own saved counts or see its review status. Each physical count and note is saved to the database. Search by product, category or barcode; a USB barcode scan followed by Enter opens the exact matching product's count dialog.

Enter whole quantities, including **0** when none remain. Leave a quantity blank to mark it **not counted**. Uncounted products stay unchanged. Counted products show their variance from the stock originally loaded.

## Excel import and export

1. Start or select a Draft and choose **Export to Excel**. The workbook contains the full inventory snapshot, saved physical counts and an Instructions sheet. Barcodes export as text, preserving leading zeroes.
2. Fill the highlighted **Physical Count** column in Excel. Keep Stocktake ID, Product ID, Barcode and Loaded Stock unchanged. Rows may be sorted or omitted. Whole counts range from 0 to 1,000,000; formulas are not accepted as counts.
3. Open that same draft and choose **Import from Excel** (`.xlsx` or `.xls`). The app validates the template, snapshot identity, product IDs, barcodes, baseline quantities and counts. It rejects duplicate/unknown products, changed baselines, formulas and invalid quantities. Any error prevents the entire import; correct the reported rows and try again.
4. Review the product/count/variance preview, then confirm. Import replaces draft counts: blank and omitted products become uncounted. It does not create products, edit the original snapshot or change live stock.
5. Choose **Submit for approval**. The sheet becomes read-only. An Admin or Manager reviews it under **Approvals > Stocktakes**. Submitted desktop and phone stocktakes share the same approval process.

Approval applies all counted corrections in one transaction. Any intervening stock movement, including a change that later returns to the original quantity, blocks approval and requires a fresh count. Rejection keeps inventory unchanged and records review notes. Refresh your stocktake to see the decision.

The approving Admin or Manager generates a key through **Sessions > Auth Key**, confirms their current staff password, and enters the six-digit key in the review. Keys last 15 minutes and work once; a replacement invalidates the previous key. Both approval and rejection require authorization.

Export remains available for submitted, approved and rejected sheets. Managers can also export any selected stocktake from the approval screen. Historical exports include the original baseline, physical counts, status and review notes.

Desktop and phone counts are associated with the same staff account and snapshot. Phone counts not yet submitted are stored locally on that phone; they do not appear as saved desktop draft counts until the phone submits them. Avoid editing one draft simultaneously on two devices.

## Verification

`mvn test` includes stocktake workbook round trips and validation tests. See [desktop verification](../README.md#verification) for real draft/save/submit/approve and rollback fixtures. Configure a disposable database for integration checks; fixtures clear test data. JavaFX rendering and physical phone workflows require their respective UI/device environments in addition to JVM tests.
