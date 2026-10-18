# Mbuyedzedzo Scanner 1.4.1

Android companion for the [desktop POS](../pos-desktop), using the existing teal Material theme, outlined inputs and rounded action cards. Requires Android 7.0 or later.

## Pairing and barcodes

Connect both devices to the same trusted local network. Enter the desktop's displayed IP address and port, or scan its connection QR (`POS:<ip>:<port>`). The default port is 8888; the desktop tries the next nine ports if necessary. Navigation between phone screens keeps the connection alive.

After connecting, sign in with an existing active desktop staff account. One app-wide session covers scanning, product submissions, receipts and stocktaking. Sign out from the home screen. Reconnecting requires sign-in again; sessions expire after 30 minutes of inactivity. The desktop enforces authentication before accepting scans/products or sending categories/receipts. Use the matching updated desktop; the original v1.0.0 installers cannot authenticate this version.

Product scanning reads Code 128 (including desktop labels), Code 39/93, EAN-13/8, UPC-A/E, ITF and Codabar. QR scanning is reserved for pairing. Leading zeroes are preserved; equivalent UPC-A and zero-prefixed EAN-13 forms also match. In Add Product and Stocktaking, scanning fills/selects a product instead of adding it to a sale.

## Stocktaking

1. Connect and open **Stocktaking**.
2. Inventory loads into a fixed snapshot using the staff account already signed into the app.
3. Search by name, barcode or category, or scan. Enter physical quantities, including zero. Blank means **not counted**; those products stay unchanged.
4. Counts and notes save on the phone after each edit. If disconnected, reconnect and sign in to resume that staff member's saved snapshot for this server.
5. Submit counted products. Submission freezes the sheet and leaves live stock unchanged.
6. An **Admin or Manager** opens desktop **Supervisor Approvals > Stocktakes**, compares loaded, physical and current quantities, and approves or rejects. Rejection requires notes. Approval applies all counted corrections in one transaction and records the reviewer. Products whose stock changed since the snapshot require a fresh count, even if their quantity returned to its original value. Deleted counted products also block approval.

The phone receives review results while connected, or refreshes them when resuming after sign-in. A fresh count replaces the phone's unsubmitted draft; submitted audit records remain on the desktop.

Sign-in uses a short-lived nonce challenge and HMAC proof derived from existing staff password hashes. Passwords are not stored on the phone or sent as plaintext. Sessions expire after 30 minutes of inactivity and clear on disconnect. The local TCP connection has no TLS; use a trusted network.

## Receipts

The desktop queues finished receipts in a persistent outbox even when disconnected. The phone saves before acknowledging; unacknowledged receipts replay on reconnect and deduplicate by ID. Receipt history refreshes on receipt arrival and screen resume. Details retain original text and parse wrapped names, quantities, prices, discounts and final totals.

Legacy multiline receipts ending with `===END_RECEIPT===` are supported. Reliable outbox delivery and stocktaking require the matching desktop source in this release; older desktop binaries do not implement these additions. The desktop creates companion tables and the stock-version trigger during normal database setup.

## Protocol

UTF-8 newline-delimited JSON uses Gson and bounded frames. `auth_challenge`, `auth_login` and `auth_logout` manage the shared session; older stocktake authentication aliases remain supported. All scan, product, category, receipt-acknowledgement and stocktake requests carry a socket-bound token. Responses correlate by `requestId`. The server checks ownership and roles independently of client fields. UUID snapshot IDs make retries safe after lost responses. Protocol version 3 advertises `requiresLogin` in the connection handshake.

## Build and verification

Use JDK 17, Android SDK 34 and the checked-in Gradle 8.9 wrapper (AGP 8.2.0):

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease
.\gradlew.bat installDebug
```

Release builds are unsigned by default. Sign with the installed app's certificate to preserve update compatibility; keep keystores outside the repository. The distributed APK's certificate and SHA-256 checksum are recorded with the GitHub release.

Android unit tests exercise actual Code 128/EAN decoding, receipt parsing/framing, password proofs (including the older Android fallback), and draft/count semantics. From `pos-desktop`, run `mvn test` for desktop unit tests. `src/test/powershell/Verify-Companion.ps1` starts a separate disposable database using installed MySQL binaries, testing real approval transactions/rollback, authentication and receipt reconnects. It never connects to the shop database. Optional `-Interactive` mode enables the emulator workflow against that test server; test-only fixture details are in `CompanionIT`.

Permissions: camera, network access/state, Wi-Fi state and vibration. No additional account service is required.
