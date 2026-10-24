# Troubleshooting

These steps apply to the current source. Check the installed desktop edition/version and phone About version first; old builds can have different pairing/login behaviour.

## Database setup does not finish

- New MySQL installations need internet and Windows administrator approval. The bundled web installer still downloads server components.
- For an existing server, enter its current password. Choosing a new password does not repair an existing installation.
- Use **Retry setup** after cancelled elevation, failed downloads or interrupted installation. Keep the error and diagnostic path shown by setup.
- If a database port is already occupied, have the installation administrator check the existing service before changing it.

Normal POS use does not require running the entire app as administrator. Saved database passwords are Windows-protected; copying connection files to another Windows account/computer is not a credential migration procedure.

## Phone rejects the QR or loops between connection and sign-in

1. Update both desktop and phone to compatible versions. A new phone app cannot add TLS/login support to an old desktop installer.
2. Use the connection QR from the running desktop and the phone's connection scanner, rather than its product scanner.
3. Scan again, or copy the complete `POS-TLS:` code. An IP alone omits the desktop identity.
4. Confirm both devices share a reachable local network. Guest Wi-Fi can isolate devices. Use the currently displayed address/port; the port may exceed 8888.
5. If a firewall blocks the listener, allow the desktop app on the shop's trusted private network. Keep firewall protection enabled.
6. Use an active desktop staff account. Initial sign-in allows two minutes. Reconnection, sign-out and session expiry require fresh sign-in.

If the desktop identity changed, obtain its fresh QR. Do not bypass verification or reuse an old identity. If the problem persists, record the exact phone error and desktop log entries at the same time.

## Product scan finds nothing

Scan a product barcode, not the Wi-Fi connection QR. Confirm it belongs to an active desktop product and the pack size matches. Preserve leading zeros in imports. In phone Stocktaking/Add Product, a scan fills/selects the product rather than adding it to a sale.

## Supervisor key is rejected

Generate a fresh key from **Sessions → Auth Key** as the approving Admin or Manager. It must belong to that supervisor, be unused and be less than 15 minutes old. Replacing it invalidates the old key. After too many failures, wait for the retry window rather than repeatedly submitting guesses.

If an offer changed after review opened, refresh and review its new revision before trying again.

## Stocktake approval is blocked

Check whether stock moved after the snapshot, a counted product was removed, or someone already reviewed the sheet. A movement that returns to the original quantity still invalidates the baseline. Take a fresh count; editing spreadsheet baseline columns does not resolve the conflict.

On import, **blank or omitted rows become uncounted**. Use explicit zero for an empty shelf. Keep the draft identity and baseline values. See [Stocktaking](stocktaking.md).

## Promotion does not apply

Check approval, South Africa calendar dates, exact product barcodes and complete quantities/groups. Distinct-product offers need different barcodes. Enter the code when configured.

A competing offer/coupon may provide a better total and win. Draft edits remain unpublished until approved. If pricing reports too many combinations, narrow overlapping selections and test again. See [Promotions](promotions.md).

## Receipt or export is missing

Check the configured folder and destination chosen in Save As. Cancelling creates no file. Check write permissions and whether Excel has the target open.

If the sale was saved, retrieve its receipt from history. Do not repeat the sale to retry printing or delivery. Phone receipt history is scoped to the desktop and staff account. The desktop queues unacknowledged receipts for authenticated reconnects.

## Information for support

Record edition/version, Android version where relevant, action attempted, exact error and approximate time. Logs normally live under `%PROGRAMDATA%\POS System\logs`; setup errors also show a helper diagnostic path.

Remove customer details, passwords, licence keys, supervisor keys and full pairing codes before sharing logs/screenshots. Keep database backups and credential files with the installation administrator, rather than attaching them to public issues.
