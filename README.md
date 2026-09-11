# Point of Sale System

A comprehensive JavaFX-based Point of Sale (POS) System with a modern, beautiful UI designed for retail business management.

## Editions — two separate products from one codebase

The build produces **two distinct installers**. A customer licenses one; the
other isn't in their build (it's not a setting they can flip).

| Edition | For | Difference | Build |
| --- | --- | --- | --- |
| **Standard POS** | Shops that keep customer accounts (loyalty, contracts, age-restricted goods) | Full Customer and Marketing screens, promo targeting, receipt-by-email, customer-insight reports | `mvn clean verify` → `target/dist/POS-*.exe` |
| **Retail POS** | Shops that just sell over the counter | No Customer or Marketing screens. Every sale is booked against a single hidden **"Walk-in"** account, so no schema change is needed. Reports drop the customer-centric views. | `mvn -P retail clean verify` → `target/dist/RetailPOS-*.exe` |

The edition is baked in at build time: the Maven `pos.edition` property is
filtered into `pos-edition.properties` inside the jar and read by
`com.pos.Edition`. The window title, receipts and installer name follow it
(e.g. *Mbuyedzedzo Retail POS*).

## Licensing

Without a valid licence this build shows an **Activation screen** instead of the
login screen. On activation the app sends a licence key + a machine fingerprint
(hashed MACs + machine name) to the licensing server and stores a short‑lived
**RS256 token**, which it then verifies **offline** on every launch using the
bundled public key (`src/main/resources/license-public.pem`). It re‑checks with
the server in the background each launch, with a 14‑day offline grace window.
A **30‑day trial** can be started from the same screen.

Keys are product‑specific — a Retail key won't activate a Standard build.

- Server + admin portal: **[`../Mbuyedzedzo-Licensing`](../Mbuyedzedzo-Licensing)**
  (Spring Boot; issue / revoke / renew / transfer keys, sales‑agent roles).
- Licence status and a "Deactivate this machine" button live in **Settings**.
- Payment gateway integration (PayFast) is out of scope for this portfolio
  build — keys are issued by hand from the admin portal. In a real deployment a
  payment webhook would call the same "issue licence" service.

## Features

### 🎯 Core Functionality
- **Sales Terminal**: Fast product scanning via QR/Barcode, shopping cart management, customer selection
- **Inventory Management**: Product CRUD operations, stock tracking, automatic QR code generation, low-stock alerts
- **User Management**: Role-based access control (Admin, Manager, Cashier), secure password hashing
- **Customer Management**: Customer database, purchase history tracking, customer analytics
- **Reports & Analytics**: Sales trends, top products, category analysis, Excel export functionality

### 🔐 Security
- Password hashing using salted PBKDF2-HMAC-SHA256 (legacy SHA-256 hashes upgrade on next login)
- Minimum 8-character password requirement
- Role-based access control
- Session management with login/logout tracking

### 📊 Business Intelligence
- Daily sales tracking
- Top-selling products analysis
- Category performance metrics
- Low stock alerts
- Customer purchase patterns
- Exportable Excel reports

## Technology Stack

- **Language**: Java 22
- **UI Framework**: JavaFX 22
- **Database**: MySQL 8.4 (bundled installer, set up automatically on first run)
- **Build Tool**: Maven
- **Packaging**: Shade fat-JAR → `jlink` custom runtime → `jpackage` Windows `.exe`
- **Libraries**:
  - MySQL Connector/J
  - ZXing (QR code generation)
  - Apache POI (Excel export)
  - iText 5 (PDF reports)
  - Jakarta Mail (email receipts & marketing)
  - Gson (JSON for the WiFi scanner companion)

## Project Structure

```
point-of-sale-system/
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── pos/
│                   ├── POSApplication.java (Main entry point)
│                   ├── database/
│                   │   └── DatabaseConnection.java
│                   ├── models/
│                   │   ├── User.java
│                   │   ├── Product.java
│                   │   └── Customer.java
│                   ├── services/
│                   │   ├── UserService.java
│                   │   ├── ProductService.java
│                   │   ├── CustomerService.java
│                   │   ├── TransactionService.java
│                   │   ├── CategoryService.java
│                   │   └── ReportService.java
│                   ├── utils/
│                   │   ├── PasswordUtil.java
│                   │   ├── QRCodeUtil.java
│                   │   └── ReceiptGenerator.java
│                   └── views/
│                       ├── LoginView.java
│                       ├── MainDashboard.java
│                       ├── SalesView.java
│                       ├── InventoryView.java
│                       ├── UserManagementView.java
│                       ├── CustomerView.java
│                       └── ReportsView.java
├── pom.xml
└── README.md
```

## Database Setup

**No manual setup is required.** On first launch the application:

1. Detects or silently installs MySQL 8.4 (`installer/mysql-installer.msi`, bundled by `jpackage`), generating a random root password.
2. Writes the connection details to `%PROGRAMDATA%\POS System\config\db.properties`.
3. Creates the `pos_db` schema and every table (`com.pos.database.DatabaseSetup`, idempotent — safe to re-run on every launch).
4. Runs any pending column migrations.

For development against an existing local MySQL, create `%PROGRAMDATA%\POS System\config\db.properties`:

```properties
db.host=127.0.0.1
db.port=3306
db.name=pos_db
db.username=root
db.password=your-password
# Optional — defaults to Africa/Johannesburg
db.timezone=Africa/Johannesburg
```

`MySQL_Scripts/Tables_creation_script.sql` is a **reference copy** of the schema; the app never reads it. `MySQL_Scripts/insertions.sql` (git-ignored) is optional demo data.

## Installation & Setup

### Step 1: Install Java 22
Ensure you have Java JDK 22 installed. Verify with:
```bash
java -version
javac -version
```

### Step 2: Install Maven
Download and install Apache Maven from https://maven.apache.org/download.cgi

Verify installation:
```bash
mvn -version
```

### Step 3: Clone/Download Project
Download all the Java files and organize them according to the project structure above.

### Step 4: Install Dependencies
Navigate to the project root directory (where pom.xml is located) and run:
```bash
mvn clean install
```

This downloads JavaFX, MySQL Connector/J, ZXing, Apache POI, iText, Jakarta Mail and Gson.

### Step 5: Run the Application

Development:
```bash
mvn javafx:run
```

Fat JAR:
```bash
mvn clean package
java -jar target/point-of-sale-system-1.0.0.jar
```

Windows installer (`.exe`) — see the `pom.xml` build section for the `jlink` / `jpackage` prerequisites:
```bash
mvn clean verify
```

## First Login

There is **no default account**. On the first run — when the `Staff` table is empty — the app opens the registration screen and the first account you create becomes the Admin. Passwords are stored as salted PBKDF2-HMAC-SHA256 hashes; older unsalted hashes are upgraded automatically on next login.

## User Roles & Permissions

### Admin
- Full access to all features
- User management
- Full inventory control
- Access to all reports
- System configuration

### Manager
- Full access to all features
- User management
- Full inventory control
- Access to all reports

### Cashier
- Access to Sales terminal only
- Cannot access other management features

## Usage Guide

### 1. Sales Terminal
1. Select a customer (or add new customer)
2. Scan product QR/Barcode or use quick-add buttons
3. Review shopping cart
4. Click "Checkout" to complete transaction
5. Receipt will be printed/saved

### 2. Inventory Management
1. View all products with stock levels
2. Add new products (QR codes generated automatically)
3. Edit product details
4. Restock products
5. Monitor low-stock alerts
6. View QR codes for products

### 3. User Management
1. Add new staff members
2. Assign roles (Admin/Manager/Cashier)
3. Reset passwords
4. Activate/Deactivate users
5. Search and filter users

### 4. Customer Management
1. Add customer accounts
2. View customer details and purchase history
3. Track customer patterns
4. Search customers

### 5. Reports & Analytics
1. View sales trends (line chart)
2. Analyze top-selling products (bar chart)
3. Category performance (pie chart)
4. Export reports to Excel
5. Customize date ranges

## Features to Implement Later

If you want to extend the system, consider:
1. Barcode scanner integration (USB/Bluetooth)
2. Webcam QR code scanning
3. Email receipts to customers
4. SMS notifications
5. Loyalty program
6. Multi-store support
7. Tax calculations
8. Discount/coupon system (structure is already there)
9. Employee performance tracking
10. Backup/restore functionality

## Troubleshooting

### Database Connection Issues
- Verify the MySQL service is running
- Check `%PROGRAMDATA%\POS System\config\db.properties` has the right host/port/user/password
- The default database name is `pos_db` and is created automatically

### JavaFX Issues
If you get JavaFX runtime errors:
```bash
mvn clean javafx:run
```

### Missing Dependencies
```bash
mvn clean install -U
```

### Port Already in Use
If MySQL's port (3306) is blocked, check firewall settings or MySQL's `my.ini`.
The WiFi scanner companion server uses port 8888 (auto-increments if taken).

## Screenshots

The system features:
- Modern gradient design with purple/blue theme
- Clean, professional interface
- Responsive layouts
- Interactive charts and graphs
- Beautiful card-based dashboards
- Smooth animations and hover effects

## Support & Maintenance

For issues or questions:
1. Check the console for error messages
2. Verify database connection
3. Ensure all Maven dependencies are installed
4. Check Java version compatibility

## License

This project is for educational/business purposes.

## Version History

**Version 1.0.0** (Current)
- Initial release
- Core POS functionality
- Inventory management
- User management
- Customer management
- Reports and analytics
- QR code generation
- Excel export

---

**Built with ❤️ using Java & JavaFX**