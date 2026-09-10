# Point of Sale System

A comprehensive JavaFX-based Point of Sale (POS) System with a modern, beautiful UI designed for retail business management.

## Features

### 🎯 Core Functionality
- **Sales Terminal**: Fast product scanning via QR/Barcode, shopping cart management, customer selection
- **Inventory Management**: Product CRUD operations, stock tracking, automatic QR code generation, low-stock alerts
- **User Management**: Role-based access control (Admin, Manager, Cashier), secure password hashing
- **Customer Management**: Customer database, purchase history tracking, customer analytics
- **Reports & Analytics**: Sales trends, top products, category analysis, Excel export functionality

### 🔐 Security
- Password hashing using SHA-256
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
- **Database**: Microsoft SQL Server
- **Build Tool**: Maven
- **Libraries**:
  - Microsoft SQL Server JDBC Driver
  - ZXing (QR Code generation)
  - Apache POI (Excel export)

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

### Prerequisites
1. Microsoft SQL Server Management Studio installed
2. SQL Server running on `RAMOS1702\SQLEXPRESS`

### Database Schema Updates

Before running the application, update your database schema with these corrections:

```sql
-- 1. Update PromoCode data type in Transactions table
ALTER TABLE Transactions
ALTER COLUMN PromoCode VARCHAR(10);

-- 2. Update ContactNo data type in Account table
ALTER TABLE Account
ALTER COLUMN ContactNo VARCHAR(20);

-- 3. Update Price data type in Product table
ALTER TABLE Product
ALTER COLUMN Price DECIMAL(10, 2);

-- 4. Update QRCode and BarCode to store strings
ALTER TABLE Product
ALTER COLUMN QRCode VARCHAR(MAX);

ALTER TABLE Product
ALTER COLUMN BarCode VARCHAR(100);

-- 5. Add some default categories (optional but recommended)
INSERT INTO Category (CategoryName) VALUES 
('Electronics'),
('Groceries'),
('Clothing'),
('Books'),
('Toys'),
('Sports'),
('Home & Garden'),
('Health & Beauty');

-- 6. Create a default admin user (password: Admin123!)
-- Note: This is a hashed password, do not change it
INSERT INTO Staff (FullNames, EmailAddress, UserPassword, UserType, Status) 
VALUES ('System Admin', 'admin@pos.com', 'jGl25bVBBBW96Qi9Te4V37Fnqchz/Eu4qB9vKrRIqRg=', 'Admin', 'Active');
```

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

This will download all required dependencies:
- JavaFX libraries
- SQL Server JDBC driver
- ZXing (QR code library)
- Apache POI (Excel export)

### Step 5: Configure Database Connection
The database connection is already configured for your server in `DatabaseConnection.java`:
- Server: `RAMOS1702\SQLEXPRESS`
- Database: `PointOfSale`
- Authentication: Windows Authentication

### Step 6: Run the Application

Using Maven:
```bash
mvn javafx:run
```

Or compile and run directly:
```bash
mvn clean package
java -jar target/point-of-sale-system-1.0.0.jar
```

## Default Login Credentials

After running the database setup script:
- **Email**: admin@pos.com
- **Password**: Admin123!

**Important**: Change this password immediately after first login!

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
- Verify SQL Server is running
- Check Windows Authentication is enabled
- Ensure database name is exactly "PointOfSale"

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
If SQL Server port is blocked, check firewall settings or use SQL Server Configuration Manager.

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