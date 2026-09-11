# Mbuyedzedzo Scanner (mobile companion)

*Part of the [Mbuyedzedzo](../README.md) suite — see the root README for
how this fits with [`pos-desktop`](../pos-desktop) and [`licensing-server`](../licensing-server).*

An Android app that turns a phone into a wireless barcode/QR scanner for the
[desktop POS](../pos-desktop) — scan a product at the shelf, or from the till
without a USB scanner attached, and it lands directly in the sale on the
desktop app over the local network.

## How it connects

The desktop POS runs a small TCP server (`WiFiHandler`, port `8888` by
default, auto-incrementing up to 10 times if that's taken) and speaks
line-delimited JSON. The phone is the client (`WiFiCommunication`).

Pairing is one of:
- **Scan the "Connect" QR code** shown on the desktop (encodes `POS:<ip>:<port>`) — no typing required.
- **Enter the IP manually** in the app's connect dialog.

Once connected, the app auto-reconnects with backoff (up to 5 attempts) if
the connection drops, and the desktop pushes its product categories down
immediately so the "Add Product" screen's dropdown is ready without a
separate request.

| Direction | `type` | Payload | Purpose |
| --- | --- | --- | --- |
| Phone → Desktop | `scan` | `data`: the scanned code | A barcode/QR was scanned — desktop adds it to the current sale |
| Phone → Desktop | `add_product` | `data`: `{name, category, barcode, quantity, price}` | Submit a brand-new product from the phone |
| Phone → Desktop | `request_categories` | — | Ask for the category list again (e.g. after reconnecting) |
| Desktop → Phone | `connected` | `message` | Handshake acknowledgement |
| Desktop → Phone | `categories` | `data`: string array | Product categories, for the dropdown |
| Desktop → Phone | `scan_ack` / `product_ack` | `message` | Confirms a scan/product was received |
| Desktop → Phone | `error` | `message` | Something went wrong server-side |

JSON is built with Gson on both ends (not string concatenation), so a
product name or barcode containing a quote can't corrupt the stream.

**Known gap:** the desktop can push a finished sale receipt down to the
phone (`ReceiptActivity` has the list/detail screens for it), but the
phone-side handler for the `===END_RECEIPT===` marker isn't wired up yet —
receipts sent that way are currently dropped. Scanning, product submission,
and category sync are all live.

## Screens

| Activity | What it does |
| --- | --- |
| `MainActivity` | Home screen — connection status, and entry points to Scan / Add Product / Receipts |
| `ScannerActivity` | Full-screen camera scanner (ZXing), also reused in "add product" mode (fills the barcode field instead of sending a scan) and "connect" mode (reads the pairing QR) |
| `AddProductActivity` | Form to submit a new product to the desktop's inventory, with barcode-scan-to-fill |
| `ReceiptActivity` / `ReceiptDetailActivity` | Local receipt history (see the gap above — not currently populated from the desktop) |

## Build & run

This is a plain Android Gradle module — **always use the wrapper**
(`gradlew`/`gradlew.bat`), never a bare `gradle` on your PATH: the project
is pinned to Gradle 8.9 to match Android Gradle Plugin 8.2.0, and a newer
system-wide Gradle will fail in confusing ways (Gradle 9's stricter
configuration locking breaks AGP 8.2.0's data/view-binding task).

```bash
# Windows
.\gradlew.bat assembleDebug      # build only
.\gradlew.bat installDebug       # build + install on a connected device/emulator
.\gradlew.bat run                # installDebug, then launch it via adb
```

Requirements: JDK 17, Android SDK (compileSdk 34, minSdk 24), a device or
emulator on the same Wi-Fi network as the desktop POS for the socket
connection to work — `adb devices` to confirm one is attached.

## Permissions

`CAMERA` (scanning), `INTERNET` + `ACCESS_NETWORK_STATE` + `ACCESS_WIFI_STATE`
(the socket connection), `VIBRATE` (scan feedback).

## Stack

Java · Android SDK 34 (min 24) · ZXing (`zxing-android-embedded`) for
scanning · Gson for the wire protocol · Material Components · Gradle 8.9 /
AGP 8.2.0.
