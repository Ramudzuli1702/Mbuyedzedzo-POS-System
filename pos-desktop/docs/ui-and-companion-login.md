# Consistent detail screens and companion sign-in

Stocktaking uses a distinct clipboard/checklist icon and the shared desktop layout: white heading bar, teal title, neutral background, white cards, labeled controls and standard primary/secondary actions.

**View** opens editable fields directly for products, staff, customers and promos. Product information, stock adjustments and barcode preview/printing share the same display. Customer profiles retain purchase history beside the editable information. Staff security actions remain explicitly labeled. Historical sessions, returns, exchanges and campaigns use the same detail presentation; their business actions remain available. Invalid input stays visible for correction instead of dismissing the form.

## Spreadsheet folders

In **Settings > File Save Locations**, browse for **Inventory Sheets** and **Stocktake Sheets**, then save. These preferences use the existing encrypted local configuration, so one terminal's folders do not change another terminal's paths. Each export opens Save As in its configured folder, creating it if necessary. You can choose another destination for an individual export. Stocktake review exports use the same stocktake preference. Missing or relative preferences fall back to separate absolute desktop folders.

Local configuration now saves under `%LOCALAPPDATA%\Mbuyedzedzo\<edition>\app.config`, so installed applications do not need write access to Program Files. Existing encrypted `config/app.config` files remain readable; their values are retained when preferences are next saved to the user directory.

## Android sign-in and design

The phone connects first, then requires an active desktop staff account before using scans, products, receipts or stocktaking. One session covers all screens; stocktaking no longer has a separate login form. Sign-out, reconnect, session expiry and staff password changes require a new sign-in. The desktop independently rejects unauthenticated operations and holds queued receipts until sign-in succeeds.

Receipt history, details, items, totals, original receipt text and login use the shared teal/white/slate palette, rounded outlined cards and labeled navigation. Original receipt text scrolls horizontally, preserving thermal receipt columns. QR pairing remains available before sign-in.

Update the desktop and Android companion together. Older desktop builds do not support app-wide login.

## Verification

Passed: 53 desktop unit tests, 13 Android unit tests, and 10 MySQL/socket/desktop-rendering checks. Tests cover separate configured export folders, encrypted preference migration, invalid form inputs, barcode images, mandatory sign-in, reconnects, logout, password-change revocation and receipt acknowledgement/replay. Database checks use isolated test data.

The Android build installs in the emulator, but its display/automation failed, so the live phone walkthrough remains unverified. Physical-camera testing and a complete installation on a clean Windows machine also remain acceptance checks.
