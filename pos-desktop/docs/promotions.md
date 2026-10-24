# Retail promotions

Open **Inventory → Promotions → Retail offers**. Create offers in the form or use **Excel template**, fill the workbook, then **Import drafts**. The preview explains each offer and lists errors and margin warnings before anything is saved.

An active Admin or Manager can save drafts. Open an offer with **View** to see its labeled fields, edit, test a basket, approve or disable it. Generate an authorization key through **Sessions → Auth Key**, then enter it when approving or disabling. Keys expire after 15 minutes and work once. A failed approval does not consume a valid key. Approval history stores the reviewer, definition and revision.

Editing an approved offer only changes its draft. Its last approved version remains live until the replacement is approved. Saving an unchanged workbook is safe: stable promotion IDs prevent duplicate offers or unnecessary revisions. A form cannot overwrite a newer revision saved elsewhere; refresh and review it again.

## Offer types

| Type in Excel | What it does | Value column | Product groups |
|---|---|---|---|
| `PRICE` | Temporary unit price | Offer price | `ITEM` |
| `PERCENT` | Percentage off selected units | Discount % | `ITEM` |
| `AMOUNT` | Rand off selected units | Discount amount | `ITEM` |
| `MULTIBUY` | Buy a quantity for one total, such as 3 colas for R60 | Offer price | `ITEM` |
| `BUY_GET` | Buy X, receive Y free or discounted | Discount %: 100 = free, 50 = half price | `BUY`, `GET` |
| `BUNDLE` | Complete every required group for one total | Offer price | Named groups, such as `MAIZE`, `FLOUR`, `RICE`, `OIL`, `EGGS` |

Select exact product barcodes and pack sizes. A category name alone never makes all products in that category eligible. To allow alternative brands, put their barcodes in the same group. All alternatives in a bundle group must specify the same required quantity. One product cannot fill two different bundle groups.

For buy/get offers, the BUY and GET lists must be identical or completely separate. Identical lists discount the cheapest reward units within each qualifying group. Scan all supplied units, including free items. The engine compares possible groupings with competing offers to find the best complete combination.

## Workbook

The workbook has four sheets:

- **Instructions:** field meanings and examples.
- **Promotions:** one row per promotion ID, dates, type, pricing, quantities, limits and optional code.
- **Promotion Items:** one row per eligible barcode and group.
- **Product Lookup:** current active products, normal prices and unit costs for reference.

IDs and optional codes use uppercase letters, digits, `_` and `-`. Keep IDs stable when changing existing offers. Dates use `yyyy-MM-dd` and include both days, in South Africa time. Promotions switch on at midnight on their start date and expire at midnight after the end date. Normal inventory prices remain unchanged.

Fill only the value column for the chosen type. **Buy quantity** is the group size for MULTIBUY or the qualifying quantity for BUY_GET. **Reward quantity** applies to BUY_GET. **Required quantity** is the quantity needed from each bundle group; use 1 for other types. **Different products = YES** applies to MULTIBUY and requires distinct barcodes, rather than repeated units of one product. A **Per-sale limit** of 0 permits repeated complete groups; a positive number limits applications of that offer.

Leave **Coupon code** blank for automatic application. Codes on retail offers trigger only those offers when entered. Existing basket coupon codes cannot be reused for retail offers. Type and Different products columns have dropdowns. Keep barcode cells as text so leading zeros survive. Formulas, numeric barcode cells, unknown/archived products and invalid rules are rejected. Every row is validated before a draft import is committed; an error leaves the database unchanged.

Use **Export offers** to export current drafts for editing. Workbook imports never activate offers. Review margin warnings and use **Test basket** before approving. Test baskets change neither stock nor sales. They use the selected draft alongside other approved offers for the test date.

The worked example workbook is at `release-artifacts-new/promotions/Sample_Promotions.xlsx`. Its dates are 25 October through 1 November 2026. Its barcodes and costs are illustrative: replace them with your Product Lookup barcodes and desired dates before importing. It includes the five-product R100 basket, 3 colas for R60, temporary price, percentage off, rand off, buy/get and a distinct-product deal.

## Checkout, receipts and returns

Qualifying automatic offers appear in the basket total and payment screen. Complete groups repeat; remainders stay at normal price. A promotion never raises a price. Overlapping offers compete for units: a unit can be used only once. The engine chooses the combination with the greatest total saving, rather than taking the largest single discount first.

Existing whole-basket coupons remain available in **Basket coupons**. They compete with the complete automatic/retail-offer result and do not stack with it. Only a winning basket coupon consumes its usage allowance. Existing basket-coupon administration retains its existing workflow; the new authenticated publishing flow applies to retail offers.

Checkout revalidates approved definitions, dates, current product prices, coupon limits and stock inside the sale transaction. If the preview changed or expired, payment must be confirmed again. Stock, discounted ledger rows, promotion evidence, coupon usage and session totals commit together or roll back together.

Discounts are allocated proportionally across qualifying products in cents. A multi-quantity line can split into two prices one cent apart so the receipt, ledger and payment agree exactly. Unrelated items retain their normal price. Receipts show offer names, application counts and savings. History retains the original offer version and paid unit amounts even after an offer changes or expires. Partial refunds use those recorded paid amounts, preventing shelf-price refunds of discounted items.

Retail-offer **Applications** and **Savings** are lifetime checkout figures; they are not reduced by later returns and do not represent profit. Financial reports and approved refunds use the saved net sale prices and existing refund accounting.

## Operational limits

An import accepts up to 200 offers and 10,000 product-selection rows. Each offer can contain up to 500 product entries. Checkout supports up to 500 different basket products, with up to 1,000,000 units per product; offer group quantities are capped at 1,000. Exact optimization has bounded combination and search limits. Extremely broad overlapping selections can exceed these limits. In that case the system explains the problem and refuses checkout rather than charging an approximate amount. Narrow the overlapping selections or dates and test again.

The schema migration is additive and runs through normal database setup. Back up the shop database before installing an updated desktop build. This source change does not replace the published installers or APK.
