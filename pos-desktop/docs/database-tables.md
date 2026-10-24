# Desktop database table reference

Generated from the literal Java DDL and known additive migrations. Read with the [database guide](database.md), which explains meaning, transactions and logical relationships.

Regenerate with `python tools/generate_database_reference.py` from `pos-desktop`. No database is opened. This is the expected current-source schema, not an inspection of an installed database. Column order is not significant. Future migration forms need corresponding generator support.

Definitions below include known column additions, the supervisor hash-width change and the ledger cascade-to-RESTRICT migration. Generated constraint names and implicit MySQL indexes are omitted; use `SHOW CREATE TABLE` on a deployed database for those details.

Tables: **25**.

## Staff

Base owner: [`DatabaseSetup`](../src/main/java/com/pos/database/DatabaseSetup.java).

| Column | SQL definition |
| --- | --- |
| `StaffID` | `INT AUTO_INCREMENT PRIMARY KEY` |
| `FullNames` | `VARCHAR(100) NOT NULL` |
| `EmailAddress` | `VARCHAR(100) NOT NULL UNIQUE` |
| `UserPassword` | `VARCHAR(255) NOT NULL` |
| `UserType` | `VARCHAR(10) NOT NULL` |
| `Status` | `VARCHAR(10)` |
| `TimeStamp` | `DATETIME DEFAULT CURRENT_TIMESTAMP` |

## CheckIn

Base owner: [`DatabaseSetup`](../src/main/java/com/pos/database/DatabaseSetup.java).

| Column | SQL definition |
| --- | --- |
| `LogID` | `INT AUTO_INCREMENT PRIMARY KEY` |
| `StaffID` | `INT NOT NULL` |
| `LoginStamp` | `DATETIME DEFAULT CURRENT_TIMESTAMP` |
| `LogoutStamp` | `DATETIME` |

Declared table constraints/indexes:

- `FOREIGN KEY (StaffID) REFERENCES Staff(StaffID) ON DELETE RESTRICT`

## Account

Base owner: [`DatabaseSetup`](../src/main/java/com/pos/database/DatabaseSetup.java).

| Column | SQL definition |
| --- | --- |
| `AccountID` | `INT AUTO_INCREMENT PRIMARY KEY` |
| `StaffID` | `INT NOT NULL` |
| `FullNames` | `VARCHAR(100) NOT NULL` |
| `EmailAddress` | `VARCHAR(100) NOT NULL UNIQUE` |
| `DateOfBirth` | `DATE NOT NULL` |
| `ContactNo` | `VARCHAR(20) NOT NULL` |
| `TimeStamp` | `DATETIME DEFAULT CURRENT_TIMESTAMP` |
| `IsArchived` | `BOOLEAN NOT NULL DEFAULT FALSE` |

Declared table constraints/indexes:

- `FOREIGN KEY (StaffID) REFERENCES Staff(StaffID) ON DELETE RESTRICT`

## Category

Base owner: [`DatabaseSetup`](../src/main/java/com/pos/database/DatabaseSetup.java).

| Column | SQL definition |
| --- | --- |
| `CategoryID` | `INT AUTO_INCREMENT PRIMARY KEY` |
| `CategoryName` | `VARCHAR(30) NOT NULL` |

## Promo

Base owner: [`DatabaseSetup`](../src/main/java/com/pos/database/DatabaseSetup.java).

| Column | SQL definition |
| --- | --- |
| `PromoID` | `INT AUTO_INCREMENT PRIMARY KEY` |
| `PromoCode` | `VARCHAR(10) NOT NULL UNIQUE` |
| `PromoName` | `VARCHAR(100)` |
| `DiscountType` | `VARCHAR(10) NOT NULL DEFAULT 'PERCENT'` |
| `DiscountValue` | `DECIMAL(10,2) NOT NULL DEFAULT 0.00` |
| `MinimumPurchase` | `DECIMAL(10,2) DEFAULT 0.00` |
| `ValidFrom` | `DATETIME` |
| `ValidTill` | `DATETIME` |
| `IsActive` | `BOOLEAN DEFAULT TRUE` |
| `UsageLimit` | `INT DEFAULT NULL` |
| `UsageCount` | `INT DEFAULT 0` |
| `PromoQR` | `BLOB` |

## Product

Base owner: [`DatabaseSetup`](../src/main/java/com/pos/database/DatabaseSetup.java).

| Column | SQL definition |
| --- | --- |
| `ProductID` | `INT AUTO_INCREMENT PRIMARY KEY` |
| `StaffID` | `INT NOT NULL` |
| `CategoryID` | `INT NOT NULL` |
| `ProductName` | `VARCHAR(50) NOT NULL` |
| `QRCode` | `LONGBLOB` |
| `BarCode` | `LONGBLOB` |
| `Quantity` | `INT DEFAULT 0` |
| `NoSold` | `INT NOT NULL DEFAULT 0` |
| `Price` | `DECIMAL(10,2) NOT NULL DEFAULT 0.00` |
| `SalePrice` | `DECIMAL(10,2) DEFAULT NULL` |
| `CostPrice` | `DECIMAL(10,2) NOT NULL DEFAULT 0.00` |
| `IsArchived` | `BOOLEAN NOT NULL DEFAULT FALSE` |
| `StockVersion` | `BIGINT NOT NULL DEFAULT 0` |

Declared table constraints/indexes:

- `FOREIGN KEY (StaffID) REFERENCES Staff(StaffID) ON DELETE RESTRICT`
- `FOREIGN KEY (CategoryID) REFERENCES Category(CategoryID) ON DELETE RESTRICT`

## Transactions

Base owner: [`DatabaseSetup`](../src/main/java/com/pos/database/DatabaseSetup.java).

| Column | SQL definition |
| --- | --- |
| `TransactionID` | `INT AUTO_INCREMENT PRIMARY KEY` |
| `SaleID` | `INT NOT NULL DEFAULT 0` |
| `StaffID` | `INT NOT NULL` |
| `AccountID` | `INT NOT NULL` |
| `ProductID` | `INT NOT NULL` |
| `PromoID` | `INT` |
| `Quantity` | `INT NOT NULL DEFAULT 1` |
| `PromoCode` | `VARCHAR(10)` |
| `SalePrice` | `DECIMAL(10,2) NOT NULL DEFAULT 0.00` |
| `PaymentMethod` | `VARCHAR(20) DEFAULT 'Cash'` |
| `AmountPaid` | `DECIMAL(10,2) DEFAULT 0.00` |
| `ChangeGiven` | `DECIMAL(10,2) DEFAULT 0.00` |
| `TransactionDate` | `DATETIME DEFAULT CURRENT_TIMESTAMP` |
| `UnitCostAtSale` | `DECIMAL(10,2) NULL` |
| `OperatorID` | `INT NULL` |
| `RegularUnitPrice` | `DECIMAL(10,2) NULL` |
| `PromotionSnapshot` | `TEXT NULL` |

Declared table constraints/indexes:

- `INDEX idx_saleid (SaleID)`
- `INDEX idx_date (TransactionDate)`
- `FOREIGN KEY (StaffID) REFERENCES Staff(StaffID) ON DELETE RESTRICT`
- `FOREIGN KEY (AccountID) REFERENCES Account(AccountID) ON DELETE RESTRICT`
- `FOREIGN KEY (ProductID) REFERENCES Product(ProductID) ON DELETE RESTRICT`
- `FOREIGN KEY (PromoID) REFERENCES Promo(PromoID) ON DELETE SET NULL`

## Returns

Base owner: [`DatabaseSetup`](../src/main/java/com/pos/database/DatabaseSetup.java).

| Column | SQL definition |
| --- | --- |
| `ReturnID` | `INT AUTO_INCREMENT PRIMARY KEY` |
| `TransactionID` | `INT NOT NULL` |
| `ReturnQuantity` | `INT NOT NULL DEFAULT 1` |
| `StaffID` | `INT NOT NULL` |
| `SupervisorID` | `INT` |
| `ReturnDate` | `DATETIME DEFAULT CURRENT_TIMESTAMP` |
| `Reason` | `TEXT` |
| `RefundAmount` | `DECIMAL(10,2) NOT NULL DEFAULT 0.00` |
| `Status` | `VARCHAR(20) DEFAULT 'Pending'` |
| `ApprovalCode` | `VARCHAR(50)` |
| `ApprovalDate` | `DATETIME` |
| `SettlementSessionID` | `INT NULL` |
| `RefundMethod` | `VARCHAR(20) NULL` |
| `IsExchangeCredit` | `BOOLEAN NOT NULL DEFAULT FALSE` |

Declared table constraints/indexes:

- `INDEX idx_return_status (Status)`
- `FOREIGN KEY (TransactionID) REFERENCES Transactions(TransactionID) ON DELETE RESTRICT`
- `FOREIGN KEY (StaffID) REFERENCES Staff(StaffID) ON DELETE RESTRICT`
- `FOREIGN KEY (SupervisorID) REFERENCES Staff(StaffID) ON DELETE SET NULL`

## Exchanges

Base owner: [`DatabaseSetup`](../src/main/java/com/pos/database/DatabaseSetup.java).

| Column | SQL definition |
| --- | --- |
| `ExchangeID` | `INT AUTO_INCREMENT PRIMARY KEY` |
| `OriginalTransactionID` | `INT NOT NULL` |
| `NewTransactionID` | `INT` |
| `StaffID` | `INT NOT NULL` |
| `SupervisorID` | `INT` |
| `ExchangeDate` | `DATETIME DEFAULT CURRENT_TIMESTAMP` |
| `Status` | `VARCHAR(20) DEFAULT 'Pending'` |
| `Reason` | `TEXT` |
| `ApprovalCode` | `VARCHAR(50)` |
| `ApprovalDate` | `DATETIME` |
| `NewProductID` | `INT` |
| `NewPrice` | `DECIMAL(10,2)` |

Declared table constraints/indexes:

- `INDEX idx_exchange_status (Status)`
- `FOREIGN KEY (OriginalTransactionID) REFERENCES Transactions(TransactionID) ON DELETE RESTRICT`
- `FOREIGN KEY (NewTransactionID) REFERENCES Transactions(TransactionID) ON DELETE SET NULL`
- `FOREIGN KEY (StaffID) REFERENCES Staff(StaffID) ON DELETE RESTRICT`
- `FOREIGN KEY (SupervisorID) REFERENCES Staff(StaffID) ON DELETE SET NULL`

## AddInventory

Base owner: [`DatabaseSetup`](../src/main/java/com/pos/database/DatabaseSetup.java).

| Column | SQL definition |
| --- | --- |
| `InventoryID` | `INT AUTO_INCREMENT PRIMARY KEY` |
| `StaffID` | `INT NOT NULL` |
| `ProductID` | `INT NOT NULL` |
| `QuantityLeft` | `INT NOT NULL` |
| `QuantityAdded` | `INT DEFAULT 0` |
| `InventoryDate` | `DATETIME DEFAULT CURRENT_TIMESTAMP` |

Declared table constraints/indexes:

- `FOREIGN KEY (StaffID) REFERENCES Staff(StaffID) ON DELETE RESTRICT`
- `FOREIGN KEY (ProductID) REFERENCES Product(ProductID) ON DELETE RESTRICT`

## CustomerCommunications

Base owner: [`DatabaseSetup`](../src/main/java/com/pos/database/DatabaseSetup.java).

| Column | SQL definition |
| --- | --- |
| `CommID` | `INT AUTO_INCREMENT PRIMARY KEY` |
| `AccountID` | `INT NOT NULL UNIQUE` |
| `MarketingEmails` | `BOOLEAN DEFAULT FALSE` |
| `ReceiptByEmail` | `BOOLEAN DEFAULT TRUE` |
| `SMSNotifications` | `BOOLEAN DEFAULT FALSE` |
| `TermsAccepted` | `BOOLEAN DEFAULT FALSE` |
| `UnsubToken` | `VARCHAR(64) DEFAULT NULL` |
| `AcceptanceDate` | `DATETIME DEFAULT CURRENT_TIMESTAMP` |

Declared table constraints/indexes:

- `FOREIGN KEY (AccountID) REFERENCES Account(AccountID) ON DELETE CASCADE`

## BusinessSessions

Base owner: [`DatabaseSetup`](../src/main/java/com/pos/database/DatabaseSetup.java).

| Column | SQL definition |
| --- | --- |
| `SessionID` | `INT AUTO_INCREMENT PRIMARY KEY` |
| `SupervisorID` | `INT NOT NULL` |
| `StartDate` | `DATETIME NOT NULL` |
| `EndDate` | `DATETIME` |
| `Status` | `VARCHAR(20) DEFAULT 'Active'` |
| `TotalCashSales` | `DECIMAL(10,2) DEFAULT 0.00` |
| `TotalCardSales` | `DECIMAL(10,2) DEFAULT 0.00` |
| `TotalSales` | `DECIMAL(10,2) DEFAULT 0.00` |
| `DeclarationSigned` | `BOOLEAN DEFAULT FALSE` |
| `AuthorisationCode` | `VARCHAR(50)` |
| `Notes` | `TEXT` |

Declared table constraints/indexes:

- `INDEX idx_session_status (Status)`
- `FOREIGN KEY (SupervisorID) REFERENCES Staff(StaffID) ON DELETE RESTRICT`

## SessionTransactions

Base owner: [`DatabaseSetup`](../src/main/java/com/pos/database/DatabaseSetup.java).

| Column | SQL definition |
| --- | --- |
| `ID` | `INT AUTO_INCREMENT PRIMARY KEY` |
| `SessionID` | `INT NOT NULL` |
| `SaleID` | `INT NOT NULL` |

Declared table constraints/indexes:

- `FOREIGN KEY (SessionID) REFERENCES BusinessSessions(SessionID) ON DELETE RESTRICT`

## SupervisorCodes

Base owner: [`DatabaseSetup`](../src/main/java/com/pos/database/DatabaseSetup.java).

| Column | SQL definition |
| --- | --- |
| `CodeID` | `INT AUTO_INCREMENT PRIMARY KEY` |
| `StaffID` | `INT NOT NULL` |
| `AuthCode` | `VARCHAR(255) NOT NULL` |
| `CreatedDate` | `DATETIME DEFAULT CURRENT_TIMESTAMP` |
| `ExpiryDate` | `DATETIME` |
| `IsActive` | `BOOLEAN DEFAULT TRUE` |

Declared table constraints/indexes:

- `FOREIGN KEY (StaffID) REFERENCES Staff(StaffID) ON DELETE CASCADE`

## MarketingSuppression

Base owner: [`DatabaseSetup`](../src/main/java/com/pos/database/DatabaseSetup.java).

| Column | SQL definition |
| --- | --- |
| `EventID` | `INT AUTO_INCREMENT PRIMARY KEY` |
| `AccountID` | `INT NOT NULL` |
| `Email` | `VARCHAR(100)` |
| `OptedIn` | `BOOLEAN NOT NULL` |
| `Method` | `VARCHAR(20) NOT NULL DEFAULT 'Staff'` |
| `Note` | `VARCHAR(255)` |
| `ActionedBy` | `INT` |
| `EventAt` | `DATETIME DEFAULT CURRENT_TIMESTAMP` |

Declared table constraints/indexes:

- `INDEX idx_ms_account (AccountID)`
- `FOREIGN KEY (AccountID) REFERENCES Account(AccountID) ON DELETE CASCADE`
- `FOREIGN KEY (ActionedBy) REFERENCES Staff(StaffID) ON DELETE SET NULL`

## MarketingHistory

Base owner: [`DatabaseSetup`](../src/main/java/com/pos/database/DatabaseSetup.java).

| Column | SQL definition |
| --- | --- |
| `CampaignID` | `INT AUTO_INCREMENT PRIMARY KEY` |
| `Subject` | `TEXT NOT NULL` |
| `BodyHtml` | `TEXT NOT NULL` |
| `SentBy` | `INT NOT NULL` |
| `SentAt` | `DATETIME DEFAULT CURRENT_TIMESTAMP` |
| `RecipientsCount` | `INT DEFAULT 0` |
| `SuccessCount` | `INT DEFAULT 0` |

Declared table constraints/indexes:

- `FOREIGN KEY (SentBy) REFERENCES Staff(StaffID)`

## SaleSequence

Base owner: [`DatabaseSetup`](../src/main/java/com/pos/database/DatabaseSetup.java).

| Column | SQL definition |
| --- | --- |
| `id` | `INT PRIMARY KEY DEFAULT 1` |
| `nextID` | `INT NOT NULL DEFAULT 1` |

## BusinessSettings

Base owner: [`DatabaseSetup`](../src/main/java/com/pos/database/DatabaseSetup.java).

| Column | SQL definition |
| --- | --- |
| `SettingKey` | `VARCHAR(100) PRIMARY KEY` |
| `SettingValue` | `TEXT` |
| `UpdatedAt` | `DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` |

## Stocktake

Base owner: [`CompanionSchema`](../src/main/java/com/pos/database/CompanionSchema.java).

| Column | SQL definition |
| --- | --- |
| `StocktakeID` | `VARCHAR(36) PRIMARY KEY` |
| `StaffID` | `INT NOT NULL` |
| `Status` | `VARCHAR(16) NOT NULL DEFAULT 'Draft'` |
| `Notes` | `VARCHAR(1000) NOT NULL DEFAULT ''` |
| `CreatedAt` | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| `SubmittedAt` | `DATETIME NULL` |
| `ReviewedBy` | `INT NULL` |
| `ReviewedAt` | `DATETIME NULL` |
| `ReviewNotes` | `VARCHAR(1000) NOT NULL DEFAULT ''` |

Declared table constraints/indexes:

- `INDEX idx_stocktake_status (Status, CreatedAt)`

## StocktakeLine

Base owner: [`CompanionSchema`](../src/main/java/com/pos/database/CompanionSchema.java).

| Column | SQL definition |
| --- | --- |
| `StocktakeID` | `VARCHAR(36) NOT NULL` |
| `ProductID` | `INT NOT NULL` |
| `ProductName` | `VARCHAR(255) NOT NULL` |
| `BarCode` | `VARCHAR(255)` |
| `CategoryName` | `VARCHAR(255)` |
| `ExpectedQuantity` | `INT NOT NULL` |
| `ExpectedVersion` | `BIGINT NOT NULL` |
| `CountedQuantity` | `INT NULL` |

Declared table constraints/indexes:

- `PRIMARY KEY (StocktakeID, ProductID)`
- `FOREIGN KEY (StocktakeID) REFERENCES Stocktake(StocktakeID) ON DELETE CASCADE`

## MobileReceiptOutbox

Base owner: [`CompanionSchema`](../src/main/java/com/pos/database/CompanionSchema.java).

| Column | SQL definition |
| --- | --- |
| `ReceiptID` | `VARCHAR(36) PRIMARY KEY` |
| `ReceiptText` | `LONGTEXT NOT NULL` |
| `CreatedAt` | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` |

## PromotionGuard

Base owner: [`PromotionSchema`](../src/main/java/com/pos/database/PromotionSchema.java).

| Column | SQL definition |
| --- | --- |
| `GuardID` | `INT PRIMARY KEY` |
| `Revision` | `BIGINT NOT NULL` |

## PromotionCampaign

Base owner: [`PromotionSchema`](../src/main/java/com/pos/database/PromotionSchema.java).

| Column | SQL definition |
| --- | --- |
| `PromotionID` | `VARCHAR(40) PRIMARY KEY` |
| `Name` | `VARCHAR(120) NOT NULL` |
| `Definition` | `MEDIUMTEXT NOT NULL` |
| `PublishedDefinition` | `MEDIUMTEXT NULL` |
| `Revision` | `INT NOT NULL` |
| `State` | `VARCHAR(20) NOT NULL DEFAULT 'DRAFT'` |
| `CreatedBy` | `INT NOT NULL` |
| `ReviewedBy` | `INT NULL` |
| `ReviewedAt` | `DATETIME NULL` |
| `UpdatedAt` | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` |

## PromotionRedemption

Base owner: [`PromotionSchema`](../src/main/java/com/pos/database/PromotionSchema.java).

| Column | SQL definition |
| --- | --- |
| `SaleID` | `INT NOT NULL` |
| `PromotionID` | `VARCHAR(40) NOT NULL` |
| `Revision` | `INT NOT NULL` |
| `NameSnapshot` | `VARCHAR(120) NOT NULL` |
| `Applications` | `INT NOT NULL` |
| `Discount` | `DECIMAL(14,2) NOT NULL` |
| `CreatedAt` | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` |

Declared table constraints/indexes:

- `PRIMARY KEY(SaleID,PromotionID)`

## PromotionReview

Base owner: [`PromotionSchema`](../src/main/java/com/pos/database/PromotionSchema.java).

| Column | SQL definition |
| --- | --- |
| `ReviewID` | `BIGINT AUTO_INCREMENT PRIMARY KEY` |
| `PromotionID` | `VARCHAR(40) NOT NULL` |
| `Revision` | `INT NOT NULL` |
| `ReviewerID` | `INT NOT NULL` |
| `Action` | `VARCHAR(20) NOT NULL` |
| `DefinitionSnapshot` | `MEDIUMTEXT NOT NULL` |
| `ReviewedAt` | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` |
