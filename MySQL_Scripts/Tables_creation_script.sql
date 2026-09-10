CREATE TABLE Staff (
    StaffID INT AUTO_INCREMENT PRIMARY KEY,
    FullNames VARCHAR(100) NOT NULL,
    EmailAddress VARCHAR(100) NOT NULL UNIQUE,
    UserPassword VARCHAR(255) NOT NULL,
    UserType VARCHAR(10) NOT NULL,
    Status VARCHAR(10),
    TimeStamp DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE CheckIn (
    LogID INT AUTO_INCREMENT PRIMARY KEY,
    StaffID INT NOT NULL,
    LoginStamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    LogoutStamp DATETIME,
    FOREIGN KEY (StaffID) REFERENCES Staff(StaffID) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE Account (
    AccountID INT AUTO_INCREMENT PRIMARY KEY,
    StaffID INT NOT NULL,
    FullNames VARCHAR(100) NOT NULL,
    EmailAddress VARCHAR(100) NOT NULL UNIQUE,
    DateOfBirth DATE NOT NULL,
    ContactNo VARCHAR(20) NOT NULL,
    TimeStamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (StaffID) REFERENCES Staff(StaffID) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE Category (
    CategoryID INT AUTO_INCREMENT PRIMARY KEY,
    CategoryName VARCHAR(30) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE Product (
    ProductID INT AUTO_INCREMENT PRIMARY KEY,
    StaffID INT NOT NULL,
    CategoryID INT NOT NULL,
    ProductName VARCHAR(50) NOT NULL,
    QRCode LONGBLOB,
    BarCode LONGBLOB,
    Quantity INT DEFAULT 0,
    NoSold INT NOT NULL DEFAULT 0,
    Price DECIMAL(10,2) NOT NULL DEFAULT 0.00,  -- Cost price
    FOREIGN KEY (StaffID) REFERENCES Staff(StaffID) ON DELETE CASCADE,
    FOREIGN KEY (CategoryID) REFERENCES Category(CategoryID) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE Promo (
    PromoID INT AUTO_INCREMENT PRIMARY KEY,
    PromoCode VARCHAR(10) NOT NULL UNIQUE,
    ValidFrom DATETIME,
    ValidTill DATETIME,
    PromoQR BLOB
) ENGINE=InnoDB;

CREATE TABLE Transactions (
    TransactionID INT AUTO_INCREMENT PRIMARY KEY,
    SaleID INT NOT NULL DEFAULT 0,  -- Shared across multi-product sales
    StaffID INT NOT NULL,
    AccountID INT NOT NULL,
    ProductID INT NOT NULL,
    PromoID INT,
    Quantity INT NOT NULL DEFAULT 1,
    PromoCode VARCHAR(10),
    SalePrice DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    TransactionDate DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_saleid (SaleID),
    INDEX idx_date (TransactionDate),
    FOREIGN KEY (StaffID) REFERENCES Staff(StaffID) ON DELETE CASCADE,
    FOREIGN KEY (AccountID) REFERENCES Account(AccountID) ON DELETE CASCADE,
    FOREIGN KEY (ProductID) REFERENCES Product(ProductID) ON DELETE CASCADE,
    FOREIGN KEY (PromoID) REFERENCES Promo(PromoID) ON DELETE SET NULL
) ENGINE=InnoDB;


CREATE TABLE AddInventory (
    InventoryID INT AUTO_INCREMENT PRIMARY KEY,
    StaffID INT NOT NULL,
    ProductID INT NOT NULL,
    QuantityLeft INT NOT NULL,
    QuantityAdded INT DEFAULT 0,
    InventoryDate DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (StaffID) REFERENCES Staff(StaffID) ON DELETE CASCADE,
    FOREIGN KEY (ProductID) REFERENCES Product(ProductID) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE Returns (
    ReturnID INT AUTO_INCREMENT PRIMARY KEY,
    TransactionID INT NOT NULL,
    ReturnQuantity INT NOT NULL DEFAULT 1,
    StaffID INT NOT NULL,
    ReturnDate DATETIME DEFAULT CURRENT_TIMESTAMP,
    Reason TEXT,
    RefundAmount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    FOREIGN KEY (TransactionID) REFERENCES Transactions(TransactionID) ON DELETE CASCADE,
    FOREIGN KEY (StaffID) REFERENCES Staff(StaffID) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE SaleSequence (
    nextID INT PRIMARY KEY DEFAULT 1
) ENGINE=InnoDB;

INSERT INTO SaleSequence (nextID) VALUES (1) ON DUPLICATE KEY UPDATE nextID = 1;

-- Add new tables for enhanced features

-- Exchange table
CREATE TABLE Exchanges (
    ExchangeID INT AUTO_INCREMENT PRIMARY KEY,
    OriginalTransactionID INT NOT NULL,
    NewTransactionID INT,
    StaffID INT NOT NULL,
    SupervisorID INT,
    ExchangeDate DATETIME DEFAULT CURRENT_TIMESTAMP,
    Status VARCHAR(20) DEFAULT 'Pending', -- Pending, Approved, Rejected
    Reason TEXT,
    ApprovalCode VARCHAR(50),
    ApprovalDate DATETIME,
    FOREIGN KEY (OriginalTransactionID) REFERENCES Transactions(TransactionID) ON DELETE CASCADE,
    FOREIGN KEY (NewTransactionID) REFERENCES Transactions(TransactionID) ON DELETE CASCADE,
    FOREIGN KEY (StaffID) REFERENCES Staff(StaffID) ON DELETE CASCADE,
    FOREIGN KEY (SupervisorID) REFERENCES Staff(StaffID) ON DELETE SET NULL
) ENGINE=InnoDB;

ALTER TABLE Exchanges 
ADD COLUMN NewProductID INT,
ADD COLUMN NewPrice DECIMAL(10,2);

-- Update Returns table to include approval
ALTER TABLE Returns 
ADD COLUMN SupervisorID INT,
ADD COLUMN Status VARCHAR(20) DEFAULT 'Pending',
ADD COLUMN ApprovalCode VARCHAR(50),
ADD COLUMN ApprovalDate DATETIME,
ADD FOREIGN KEY (SupervisorID) REFERENCES Staff(StaffID) ON DELETE SET NULL;

-- Customer communications preferences
CREATE TABLE CustomerCommunications (
    CommID INT AUTO_INCREMENT PRIMARY KEY,
    AccountID INT NOT NULL,
    MarketingEmails BOOLEAN DEFAULT FALSE,
    ReceiptByEmail BOOLEAN DEFAULT TRUE,
    SMSNotifications BOOLEAN DEFAULT FALSE,
    TermsAccepted BOOLEAN DEFAULT FALSE,
    AcceptanceDate DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (AccountID) REFERENCES Account(AccountID) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Payment methods
ALTER TABLE Transactions
ADD COLUMN PaymentMethod VARCHAR(20) DEFAULT 'Cash', -- Cash, Card
ADD COLUMN AmountPaid DECIMAL(10,2) DEFAULT 0.00,
ADD COLUMN ChangeGiven DECIMAL(10,2) DEFAULT 0.00;

-- Business sessions
CREATE TABLE BusinessSessions (
    SessionID INT AUTO_INCREMENT PRIMARY KEY,
    SupervisorID INT NOT NULL,
    StartDate DATETIME NOT NULL,
    EndDate DATETIME,
    Status VARCHAR(20) DEFAULT 'Active', -- Active, Closed
    TotalCashSales DECIMAL(10,2) DEFAULT 0.00,
    TotalCardSales DECIMAL(10,2) DEFAULT 0.00,
    TotalSales DECIMAL(10,2) DEFAULT 0.00,
    DeclarationSigned BOOLEAN DEFAULT FALSE,
    AuthorisationCode VARCHAR(50),
    Notes TEXT,
    FOREIGN KEY (SupervisorID) REFERENCES Staff(StaffID) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Session transactions mapping
CREATE TABLE SessionTransactions (
    ID INT AUTO_INCREMENT PRIMARY KEY,
    SessionID INT NOT NULL,
    SaleID INT NOT NULL,
    FOREIGN KEY (SessionID) REFERENCES BusinessSessions(SessionID) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Supervisor authorization codes
CREATE TABLE SupervisorCodes (
    CodeID INT AUTO_INCREMENT PRIMARY KEY,
    StaffID INT NOT NULL,
    AuthCode VARCHAR(6) NOT NULL,
    CreatedDate DATETIME DEFAULT CURRENT_TIMESTAMP,
    ExpiryDate DATETIME,
    IsActive BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (StaffID) REFERENCES Staff(StaffID) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS MarketingHistory (
    CampaignID      INT AUTO_INCREMENT PRIMARY KEY,
    Subject         TEXT NOT NULL,
    BodyHtml        TEXT NOT NULL,
    SentBy          INT NOT NULL,           -- StaffID
    SentAt          DATETIME DEFAULT CURRENT_TIMESTAMP,
    RecipientsCount INT DEFAULT 0,
    SuccessCount    INT DEFAULT 0,
    FOREIGN KEY (SentBy) REFERENCES Staff(StaffID)
);


-- Indexes for performance
CREATE INDEX idx_session_status ON BusinessSessions(Status);
CREATE INDEX idx_exchange_status ON Exchanges(Status);
CREATE INDEX idx_return_status ON Returns(Status);
CREATE INDEX idx_transactions_payment ON Transactions(PaymentMethod);
