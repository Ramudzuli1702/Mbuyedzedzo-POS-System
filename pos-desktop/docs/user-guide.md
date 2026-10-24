# User guide

This guide covers the current source. Screens in an older installer may differ. Standard includes customer accounts and marketing; Retail uses walk-in sales and hides those screens.

## Install and sign in

1. Run the Windows installer for your edition. Java is included in packaged installers.
2. Accept the licence agreement. If no local MySQL server is available, choose and confirm a server password, then approve the Windows administrator prompt. New installations need internet. If MySQL already exists, enter its current server password.
3. Activate the matching licence or start an eligible trial.
4. If there are no staff accounts, register the first Admin. Otherwise, sign in with your existing staff account.

The server password is for database setup. Staff sign in with their own credentials. Keep the server password with the person responsible for the installation.

## Who can do what

| Role | Main access |
| --- | --- |
| Admin / Manager | Sales, inventory, stocktaking, staff, reports, sessions, approvals and settings; customer/marketing features in Standard |
| Cashier | Sales, stocktaking, sessions and subscription status; Retail also exposes Sales History |

Opening a screen does not authorize every action in it. Staff can enter counts and submit requests; manager review controls changes requiring approval.

## Daily sales

1. Open **Sessions** and start a business session using the workflow available to your role. Sales require an active session.
2. Open **Sales**. Scan product barcodes, search, or use product shortcuts to build the basket.
3. In Standard, select the customer and complete any required communication consent. Retail uses the walk-in account automatically.
4. Check quantities and the total. Active automatic promotions appear when the basket qualifies.
5. Proceed to payment. Enter an optional coupon, choose Cash or Card and confirm the amount. For cash, enter the amount received and check change.
6. Complete the sale once. Receipt saving, printing, email and phone delivery depend on settings and available devices.

If a price or promotion changes before checkout finishes, refresh the basket and confirm the revised payment. If the sale was saved but receipt delivery failed, retrieve the existing sale rather than ringing it up again.

Use **Switch Cashier** on Sales when another staff member takes over. Log out when leaving the terminal. Close/review the business session using the declaration and authorization steps shown in Sessions.

## Inventory and barcode labels

Managers maintain products and categories in **Inventory**. **View** opens labeled product information, editable fields, stock actions and the barcode preview in the same display. Save using the form's action button; invalid input stays available for correction.

Use inventory Excel import/export for bulk product work. Start from a system-generated workbook and review its import preview. Inventory import maintains products; stocktake import records counts against a saved snapshot. Use stocktaking for stock reconciliation.

Choose **Save barcode** in product details to save a Code 128 PNG label with its readable value. Barcode printing is no longer available in this screen.

## Stocktaking

Open **Stocktaking** on desktop or sign into the connected phone. Start a snapshot, count products and submit for review. **0** means counted with none present; blank means not counted. Submission does not immediately change live stock.

An Admin or Manager reviews under **Approvals → Stocktakes** with an authorization key. Intervening stock movements require a fresh count. See [Stocktaking](stocktaking.md) for Excel instructions, conflicts and phone drafts.

## Promotions

Open **Inventory → Promotions → Retail offers**. Create an offer manually or export the Excel template. Imports save drafts; an Admin or Manager publishes them with an authorization key.

Offers include temporary prices, percentage/rand discounts, quantity deals, buy/get rewards and bundles of specific products from different categories. Test a sample basket before approving. Automatic offers apply during their dates without a code. A unit cannot receive two competing offer discounts.

See [Retail promotions](promotions.md) for workbook columns, alternatives, date boundaries, limits and discounted returns. Existing whole-basket coupons remain in **Basket coupons**.

## Authorization keys

1. Sign into the desktop as an active Admin or Manager.
2. Open **Sessions → Auth Key**.
3. Confirm your current staff password and choose **Generate key**.
4. Enter the displayed six-digit key in the approval or session review requiring it.

A key lasts 15 minutes and works for one successful authorized action. Generating another replaces the previous key. Stocktake review, promotion publishing/disabling, financial approvals and session reviews use this mechanism. Staff passwords, licence keys and supervisor keys serve different purposes.

## Phone companion

Connect both devices to the same local network. Open the desktop Wi-Fi connection panel, then scan its connection QR in the phone app or paste its complete connection code. After pairing, sign in with an active desktop staff account.

The connection QR pairs devices; product barcodes identify inventory. A bare IP or old connection QR cannot verify the current desktop identity. Use matching updated desktop and phone versions. See the [phone guide](../../mobile-scanner/README.md) for barcode support, receipt history and reconnection.

## Receipts, returns and reports

Retrieve existing receipts from Sessions or the relevant sales history/customer purchase history. Request returns/exchanges against the recorded sale and follow manager approval. Discounted products retain their paid amounts for refund checks.

In **Reports**, choose a report and date range. Some stock/account figures describe current state and are labeled accordingly. Check the export action's description: export produces a workbook of supported reports, rather than only the chart currently visible.

Promotion applications/savings describe discounts at checkout. They are not profit and are not reduced by later returns; see the promotion guide.

## Settings and saved documents

Managers configure business/receipt information, printer size, receipt behaviour and destinations in **Settings**. Under **File Save Locations**, choose receipt, report, session document, inventory-sheet and stocktake-sheet folders, then save.

Inventory, stocktake and promotion spreadsheet exports open Save As. Promotion exports start in the inventory-sheet folder. Choose another destination for any individual export. Folder preferences belong to the Windows user and edition on that terminal.

Generated names follow `Mbuyedzedzo_Type_Reference_yyyy-MM-dd_HHmmss_SSS.ext`; references can be omitted when unnecessary. Existing files are not renamed.

For setup, pairing or document errors, use [Troubleshooting](troubleshooting.md).
