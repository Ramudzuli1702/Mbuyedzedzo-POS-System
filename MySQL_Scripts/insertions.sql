-- Task 1: Insert 5 staff members
INSERT INTO Staff (FullNames, EmailAddress, UserPassword, UserType, Status, TimeStamp) VALUES ('John Admin', 'john.admin@company.com', 'PrP+ZrMeO00Q+nC1ytSccRIpSvauTkdqHEBRVdRaoSE=', 'Admin', 'Active', '2025-01-01 00:00:00');
INSERT INTO Staff (FullNames, EmailAddress, UserPassword, UserType, Status, TimeStamp) VALUES ('Jane Admin', 'jane.admin@company.com', 'PrP+ZrMeO00Q+nC1ytSccRIpSvauTkdqHEBRVdRaoSE=', 'Admin', 'Active', '2025-01-01 00:00:00');
INSERT INTO Staff (FullNames, EmailAddress, UserPassword, UserType, Status, TimeStamp) VALUES ('Mike Manager', 'mike.manager@company.com', 'uanAgU6dGhDv8EZl0AVG+D4lFEmcekO1zFnWY4eCkfc=', 'Manager', 'Active', '2025-01-01 00:00:00');
INSERT INTO Staff (FullNames, EmailAddress, UserPassword, UserType, Status, TimeStamp) VALUES ('Alice Cashier', 'alice.cashier@company.com', 'oWgOHcPVpVtv5Nkr04y/8n5fzTHZkx1RLZNvjPYO4pk=', 'Cashier', 'Active', '2025-01-01 00:00:00');
INSERT INTO Staff (FullNames, EmailAddress, UserPassword, UserType, Status, TimeStamp) VALUES ('Bob Cashier', 'bob.cashier@company.com', 'oWgOHcPVpVtv5Nkr04y/8n5fzTHZkx1RLZNvjPYO4pk=', 'Cashier', 'Active', '2025-01-01 00:00:00');
INSERT INTO Staff (FullNames, EmailAddress, UserPassword, UserType, Status, TimeStamp) VALUES ('Ramudzuli Ralidzhivha', 'ralidzhivhar@gmail.com', 'a4ayc/80/OGda4BO/1o/V0etpOqiLx1JwB5S3beHW0s=', 'Admin', 'Active', '2025-01-01 00:00:00');
INSERT INTO Staff (FullNames, EmailAddress, UserPassword, UserType, Status, TimeStamp) VALUES ('Ricardo Ralidzhivha', '1', 'a4ayc/80/OGda4BO/1o/V0etpOqiLx1JwB5S3beHW0s=', 'Admin', 'Active', '2025-01-01 00:00:00');


-- Task 2: Sample check-ins (full ~442; generate more via pattern: random 1-7 day intervals, 30% with logout)
INSERT INTO CheckIn (StaffID, LoginStamp) VALUES (1, '2025-01-01 09:00:00');
INSERT INTO CheckIn (StaffID, LoginStamp) VALUES (1, '2025-01-08 11:00:00');
INSERT INTO CheckIn (StaffID, LoginStamp, LogoutStamp) VALUES (1, '2025-01-12 00:00:00', '2025-01-12 06:00:00');
INSERT INTO CheckIn (StaffID, LoginStamp) VALUES (1, '2025-01-14 00:00:00');
INSERT INTO CheckIn (StaffID, LoginStamp) VALUES (1, '2025-01-19 00:00:00');
INSERT INTO CheckIn (StaffID, LoginStamp, LogoutStamp) VALUES (1, '2025-01-25 00:00:00', '2025-01-25 09:00:00');
INSERT INTO CheckIn (StaffID, LoginStamp) VALUES (1, '2025-01-31 00:00:00');
INSERT INTO CheckIn (StaffID, LoginStamp, LogoutStamp) VALUES (1, '2025-02-02 00:00:00', '2025-02-02 09:00:00');
INSERT INTO CheckIn (StaffID, LoginStamp) VALUES (1, '2025-02-05 00:00:00');
INSERT INTO CheckIn (StaffID, LoginStamp, LogoutStamp) VALUES (1, '2025-02-07 00:00:00', '2025-02-07 07:00:00');
-- ... Continue for staff 1-5 with similar random dates/logouts up to 2025-12-23 (total ~442 records).

-- Task 3: 20 customer accounts (South Africa)

INSERT INTO Account (StaffID, FullNames, EmailAddress, DateOfBirth, ContactNo, TimeStamp)
VALUES (1, 'Thabo Mokoena', 'thabo.mokoena@gmail.com', '1989-02-28', '0713835783', '2025-02-28 00:00:00');

INSERT INTO Account (StaffID, FullNames, EmailAddress, DateOfBirth, ContactNo, TimeStamp) VALUES
(3, 'Naledi Khumalo', 'naledi.khumalo@gmail.com', '1989-10-02', '0721917840', '2025-10-02 00:00:00');

INSERT INTO Account (StaffID, FullNames, EmailAddress, DateOfBirth, ContactNo, TimeStamp) VALUES
(4, 'Sipho Dlamini', 'sipho.dlamini@yahoo.com', '1985-02-26', '0731293535', '2025-02-26 00:00:00');

INSERT INTO Account (StaffID, FullNames, EmailAddress, DateOfBirth, ContactNo, TimeStamp) VALUES
(5, 'Lerato Molefe', 'lerato.molefe@gmail.com', '1972-03-17', '0747741196', '2025-03-17 00:00:00');

INSERT INTO Account (StaffID, FullNames, EmailAddress, DateOfBirth, ContactNo, TimeStamp) VALUES
(2, 'Kagiso Ndlovu', 'kagiso.ndlovu@gmail.com', '1977-07-10', '0761657174', '2025-07-10 00:00:00');

INSERT INTO Account (StaffID, FullNames, EmailAddress, DateOfBirth, ContactNo, TimeStamp) VALUES
(1, 'Zanele Mthembu', 'zanele.mthembu@gmail.com', '1971-07-04', '0788124926', '2025-07-04 00:00:00');

INSERT INTO Account (StaffID, FullNames, EmailAddress, DateOfBirth, ContactNo, TimeStamp) VALUES
(1, 'Sibusiso Nkosi', 'sibusiso.nkosi@gmail.com', '1996-07-04', '0799727972', '2025-07-04 00:00:00');

INSERT INTO Account (StaffID, FullNames, EmailAddress, DateOfBirth, ContactNo, TimeStamp) VALUES
(2, 'Ayanda Cele', 'ayanda.cele@gmail.com', '2000-05-15', '0716981170', '2025-05-15 00:00:00');

INSERT INTO Account (StaffID, FullNames, EmailAddress, DateOfBirth, ContactNo, TimeStamp) VALUES
(2, 'Refiloe Matlala', 'refiloe.matlala@gmail.com', '2003-10-04', '0721533160', '2025-10-04 00:00:00');

INSERT INTO Account (StaffID, FullNames, EmailAddress, DateOfBirth, ContactNo, TimeStamp) VALUES
(4, 'Mpho Kgosi', 'mpho.kgosi@gmail.com', '1992-06-14', '0734062199', '2025-06-14 00:00:00');

INSERT INTO Account (StaffID, FullNames, EmailAddress, DateOfBirth, ContactNo, TimeStamp) VALUES
(4, 'Pieter van Wyk', 'pieter.vw@outlook.com', '1969-11-10', '0829721441', '2025-11-10 00:00:00');

INSERT INTO Account (StaffID, FullNames, EmailAddress, DateOfBirth, ContactNo, TimeStamp) VALUES
(4, 'Anelise Botha', 'anelise.botha@gmail.com', '1996-07-27', '0839309361', '2025-07-27 00:00:00');

INSERT INTO Account (StaffID, FullNames, EmailAddress, DateOfBirth, ContactNo, TimeStamp) VALUES
(1, 'Jason Naidoo', 'jason.naidoo@gmail.com', '2001-05-19', '0843694256', '2025-05-19 00:00:00');

INSERT INTO Account (StaffID, FullNames, EmailAddress, DateOfBirth, ContactNo, TimeStamp) VALUES
(5, 'Farai Chikonde', 'farai.chikonde@gmail.com', '1982-10-13', '0856521228', '2025-10-13 00:00:00');

INSERT INTO Account (StaffID, FullNames, EmailAddress, DateOfBirth, ContactNo, TimeStamp) VALUES
(1, 'Nomvula Zungu', 'nomvula.zungu@gmail.com', '2000-08-08', '0717134358', '2025-08-08 00:00:00');

INSERT INTO Account (StaffID, FullNames, EmailAddress, DateOfBirth, ContactNo, TimeStamp) VALUES
(3, 'Daniel Pretorius', 'daniel.pretorius@gmail.com', '2001-05-16', '0723403643', '2025-05-16 00:00:00');

INSERT INTO Account (StaffID, FullNames, EmailAddress, DateOfBirth, ContactNo, TimeStamp) VALUES
(3, 'Tumi Seabi', 'tumi.seabi@gmail.com', '2005-08-01', '0737777476', '2025-08-01 00:00:00');

INSERT INTO Account (StaffID, FullNames, EmailAddress, DateOfBirth, ContactNo, TimeStamp) VALUES
(2, 'Andile Madonsela', 'andile.madonsela@gmail.com', '1987-02-21', '0741836500', '2025-02-21 00:00:00');

INSERT INTO Account(StaffID, FullNames, EmailAddress, DateOfBirth, ContactNo, TimeStamp) VALUES
(5, 'Monique Jacobs', 'monique.jacobs@gmail.com', '1976-04-23', '0828414475', '2025-04-23 00:00:00');

INSERT INTO Account (StaffID, FullNames, EmailAddress, DateOfBirth, ContactNo, TimeStamp) VALUES
(3, 'Heinrich Kruger', 'heinrich.kruger@gmail.com', '1979-02-21', '0837559350', '2025-02-21 00:00:00');

-- Task 4: Categories
INSERT INTO Category (CategoryName) VALUES ('Disposable Vapes');
INSERT INTO Category (CategoryName) VALUES ('Vape Kits');
INSERT INTO Category (CategoryName) VALUES ('E-Liquids');
INSERT INTO Category (CategoryName) VALUES ('Nicotine Salts');
INSERT INTO Category (CategoryName) VALUES ('Vape Coils');
INSERT INTO Category (CategoryName) VALUES ('Accessories');

-- Task 5: Products (12 given + 30 additional = 42 total)
-- Task 5: Products (12 given + 30 additional = 42 total)

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (1, 1, 'Elf Bar 600 Blue Razz', NULL, '6000000000011', 120, 35, 129.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (1, 1, 'Vuse Go Grape Ice', NULL, '6000000000012', 90, 20, 149.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (2, 2, 'SMOK Nord 5 Kit', NULL, '6000000000013', 25, 8, 599.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (2, 2, 'Vaporesso XROS 3', NULL, '6000000000014', 30, 12, 549.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (2, 3, 'Nasty Juice Cush Man 60ml', NULL, '6000000000015', 40, 15, 249.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (2, 3, 'Dinner Lady Lemon Tart 60ml', NULL, '6000000000016', 35, 10, 269.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (3, 4, 'VGOD Cubano Salt 30ml', NULL, '6000000000017', 50, 18, 199.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (3, 4, 'Nasty Salt Mango 30ml', NULL, '6000000000018', 60, 22, 189.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (4, 5, 'SMOK Nord Coils 0.6Ω (5 Pack)', NULL, '6000000000019', 70, 25, 179.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (4, 5, 'Vaporesso GTX Coils 0.8Ω', NULL, '6000000000020', 65, 19, 169.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (1, 6, '18650 Vape Battery', NULL, '6000000000021', 45, 14, 159.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (1, 6, 'USB-C Vape Charger', NULL, '6000000000022', 80, 30, 99.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (1, 1, 'Elf Bar 600 Watermelon', NULL, '6000000000023', 100, 25, 129.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (1, 1, 'Lost Mary QM600 Blueberry', NULL, '6000000000024', 110, 30, 139.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (1, 1, 'SKE Crystal Bar Pink Lemonade', NULL, '6000000000025', 95, 22, 119.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (1, 1, 'IVG Bar 2400 Mango', NULL, '6000000000026', 85, 18, 149.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (1, 1, 'Elux Zero Nic Salt Lemon & Lime', NULL, '6000000000027', 105, 28, 129.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (2, 2, 'GeekVape Aegis Solo 100W', NULL, '6000000000028', 20, 5, 699.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (2, 2, 'Aspire Favostrix Kit', NULL, '6000000000029', 28, 7, 499.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (2, 2, 'Innokin Endura T20', NULL, '6000000000030', 35, 10, 399.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (2, 2, 'Voopoo Argus Pro', NULL, '6000000000031', 22, 6, 599.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (2, 2, 'Uwell Caliburn G3', NULL, '6000000000032', 32, 9, 449.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (3, 3, 'IVG Tropical Mango 100ml', NULL, '6000000000033', 45, 12, 299.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (3, 3, 'Dinner Lady Fruits 50ml', NULL, '6000000000034', 38, 14, 259.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (3, 3, 'Pachamama Fuji Apple', NULL, '6000000000035', 42, 11, 279.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (3, 3, 'Riot Squad Pink Grenade', NULL, '6000000000036', 50, 16, 239.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (3, 3, 'Elfliq Blue Razz Lemonade', NULL, '6000000000037', 55, 20, 229.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (4, 4, 'Dinner Lady Lemon Tart Salt 10ml', NULL, '6000000000038', 55, 21, 129.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (4, 4, 'Pachamama Mango Pitaya Pineapple', NULL, '6000000000039', 48, 17, 139.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (4, 4, 'Riot Squad Mango E-Liquid Salt', NULL, '6000000000040', 52, 19, 119.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (4, 4, 'IVG Salt Blue Raspberry', NULL, '6000000000041', 60, 23, 109.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (4, 4, 'Nasty Juice Trap Queen Salt', NULL, '6000000000042', 58, 24, 149.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (1, 5, 'GeekVape Z Series Coils 0.4Ω', NULL, '6000000000043', 75, 28, 199.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (1, 5, 'Aspire Nautilus 2 Coils', NULL, '6000000000044', 68, 20, 189.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (1, 5, 'Freemax Mesh Coils', NULL, '6000000000045', 72, 26, 209.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (1, 5, 'Voopoo PnP Coils', NULL, '6000000000046', 80, 32, 179.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (1, 5, 'Uwell UN2 Pods', NULL, '6000000000047', 62, 15, 169.99);

select * from product;
INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (2, 6, 'Vape Case Silicone', NULL, '6000000000048', 50, 16, 79.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (2, 6, 'Cotton Bacon Prime', NULL, '6000000000049', 40, 12, 89.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (2, 6, 'Drip Tip Collection', NULL, '6000000000050', 55, 18, 49.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (2, 6, 'Battery Charger Nitecore', NULL, '6000000000051', 35, 8, 299.99);

INSERT INTO Product (StaffID, CategoryID, ProductName, QRCode, BarCode, Quantity, NoSold, Price)
VALUES (2, 6, 'Mod Carry Bag', NULL, '6000000000052', 60, 22, 119.99);
select * from product;

drop table returns;
drop table transactions;
-- Task 6
-- Full Transactions Inserts (513 total across 200 sales)
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (1, 1, 9, 7, NULL, 3, '', '2025-05-06 07:08:47');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (2, 5, 14, 15, NULL, 3, '', '2025-01-17 00:05:13');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (3, 5, 14, 18, NULL, 1, '', '2025-04-23 14:37:17');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (3, 5, 14, 22, NULL, 2, '', '2025-04-23 14:37:17');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (4, 1, 13, 39, NULL, 2, '', '2025-02-19 11:54:22');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (5, 5, 4, 41, NULL, 3, '', '2025-07-13 02:35:18');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (5, 5, 4, 24, NULL, 3, '', '2025-07-13 02:35:18');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (5, 5, 4, 3, NULL, 3, '', '2025-07-13 02:35:18');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (5, 5, 4, 19, NULL, 1, '', '2025-07-13 02:35:18');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (6, 4, 9, 11, NULL, 2, '', '2025-08-21 20:53:23');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (7, 1, 20, 16, NULL, 1, '', '2025-11-22 05:34:46');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (7, 1, 20, 18, NULL, 3, '', '2025-11-22 05:34:46');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (7, 1, 20, 15, NULL, 3, '', '2025-11-22 05:34:46');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (8, 2, 2, 14, NULL, 3, '', '2025-06-11 12:17:04');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (9, 2, 16, 17, NULL, 1, '', '2025-07-22 20:29:09');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (9, 2, 16, 36, NULL, 3, '', '2025-07-22 20:29:09');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (9, 2, 16, 38, NULL, 2, '', '2025-07-22 20:29:09');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (10, 3, 8, 4, NULL, 1, '', '2025-03-12 16:31:05');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (10, 3, 8, 28, NULL, 3, '', '2025-03-12 16:31:05');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (10, 3, 8, 39, NULL, 2, '', '2025-03-12 16:31:05');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (10, 3, 8, 36, NULL, 1, '', '2025-03-12 16:31:05');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (11, 5, 9, 28, NULL, 1, '', '2025-11-25 10:07:18');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (12, 5, 6, 20, NULL, 3, '', '2025-09-17 03:55:40');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (12, 5, 6, 13, NULL, 1, '', '2025-09-17 03:55:40');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (12, 5, 6, 11, NULL, 3, '', '2025-09-17 03:55:40');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (13, 5, 11, 24, NULL, 2, '', '2025-09-08 00:07:59');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (14, 5, 3, 5, NULL, 3, '', '2025-02-13 23:31:52');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (14, 5, 3, 9, NULL, 3, '', '2025-02-13 23:31:52');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (15, 3, 17, 35, NULL, 3, '', '2025-11-07 13:13:59');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (15, 3, 17, 20, NULL, 2, '', '2025-11-07 13:13:59');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (16, 4, 17, 5, NULL, 2, '', '2025-08-20 03:15:14');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (16, 4, 17, 15, NULL, 3, '', '2025-08-20 03:15:14');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (16, 4, 17, 5, NULL, 3, '', '2025-08-20 03:15:14');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (17, 1, 2, 18, NULL, 3, '', '2025-06-19 02:32:15');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (17, 1, 2, 35, NULL, 1, '', '2025-06-19 02:32:15');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (18, 2, 16, 28, NULL, 2, '', '2025-07-28 06:06:06');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (18, 2, 16, 30, NULL, 3, '', '2025-07-28 06:06:06');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (18, 2, 16, 4, NULL, 2, '', '2025-07-28 06:06:06');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (18, 2, 16, 7, NULL, 1, '', '2025-07-28 06:06:06');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (19, 2, 14, 5, NULL, 2, '', '2025-04-04 08:29:15');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (19, 2, 14, 36, NULL, 1, '', '2025-04-04 08:29:15');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (19, 2, 14, 19, NULL, 1, '', '2025-04-04 08:29:15');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (19, 2, 14, 16, NULL, 1, '', '2025-04-04 08:29:15');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (20, 2, 13, 25, NULL, 2, '', '2025-01-31 05:24:00');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (20, 2, 13, 30, NULL, 2, '', '2025-01-31 05:24:00');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (20, 2, 13, 36, NULL, 3, '', '2025-01-31 05:24:00');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (20, 2, 13, 10, NULL, 1, '', '2025-01-31 05:24:00');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (21, 5, 18, 4, NULL, 3, '', '2025-02-01 23:20:03');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (22, 1, 17, 5, NULL, 3, '', '2025-02-11 05:04:38');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (22, 1, 17, 26, NULL, 1, '', '2025-02-11 05:04:38');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (23, 5, 20, 38, NULL, 3, '', '2025-01-21 19:05:26');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (23, 5, 20, 17, NULL, 1, '', '2025-01-21 19:05:26');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (24, 2, 9, 20, NULL, 2, '', '2025-07-22 04:42:41');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (24, 2, 9, 5, NULL, 1, '', '2025-07-22 04:42:41');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (24, 2, 9, 37, NULL, 1, '', '2025-07-22 04:42:41');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (25, 2, 12, 11, NULL, 2, '', '2025-02-05 07:23:18');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (25, 2, 12, 20, NULL, 3, '', '2025-02-05 07:23:18');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (25, 2, 12, 42, NULL, 3, '', '2025-02-05 07:23:18');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (26, 1, 5, 36, NULL, 1, '', '2025-05-16 03:56:06');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (26, 1, 5, 39, NULL, 1, '', '2025-05-16 03:56:06');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (26, 1, 5, 14, NULL, 3, '', '2025-05-16 03:56:06');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (27, 5, 16, 28, NULL, 2, '', '2025-05-09 01:05:40');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (27, 5, 16, 9, NULL, 3, '', '2025-05-09 01:05:40');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (27, 5, 16, 11, NULL, 3, '', '2025-05-09 01:05:40');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (28, 5, 1, 10, NULL, 3, '', '2025-02-27 02:56:44');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (28, 5, 1, 38, NULL, 3, '', '2025-02-27 02:56:44');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (28, 5, 1, 3, NULL, 2, '', '2025-02-27 02:56:44');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (28, 5, 1, 33, NULL, 2, '', '2025-02-27 02:56:44');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (29, 1, 12, 10, NULL, 1, '', '2025-10-14 13:39:47');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (29, 1, 12, 12, NULL, 2, '', '2025-10-14 13:39:47');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (30, 2, 9, 3, NULL, 2, '', '2025-03-23 22:06:24');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (30, 2, 9, 30, NULL, 2, '', '2025-03-23 22:06:24');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (30, 2, 9, 15, NULL, 1, '', '2025-03-23 22:06:24');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (30, 2, 9, 26, NULL, 2, '', '2025-03-23 22:06:24');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (31, 3, 12, 35, NULL, 2, '', '2025-11-25 16:25:43');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (32, 3, 6, 39, NULL, 2, '', '2025-10-25 08:02:06');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (33, 4, 20, 37, NULL, 1, '', '2025-09-19 03:24:57');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (33, 4, 20, 28, NULL, 1, '', '2025-09-19 03:24:57');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (33, 4, 20, 35, NULL, 3, '', '2025-09-19 03:24:57');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (34, 3, 14, 40, NULL, 2, '', '2025-02-05 21:58:21');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (34, 3, 14, 8, NULL, 3, '', '2025-02-05 21:58:21');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (35, 4, 11, 9, NULL, 1, '', '2025-07-26 22:18:35');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (35, 4, 11, 25, NULL, 3, '', '2025-07-26 22:18:35');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (35, 4, 11, 12, NULL, 3, '', '2025-07-26 22:18:35');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (36, 5, 1, 38, NULL, 3, '', '2025-06-05 09:13:27');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (36, 5, 1, 30, NULL, 2, '', '2025-06-05 09:13:27');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (36, 5, 1, 14, NULL, 3, '', '2025-06-05 09:13:27');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (36, 5, 1, 11, NULL, 3, '', '2025-06-05 09:13:27');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (37, 1, 8, 13, NULL, 1, '', '2025-12-11 09:14:51');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (37, 1, 8, 31, NULL, 3, '', '2025-12-11 09:14:51');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (37, 1, 8, 5, NULL, 2, '', '2025-12-11 09:14:51');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (38, 4, 16, 14, NULL, 1, '', '2025-07-24 07:09:41');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (38, 4, 16, 15, NULL, 1, '', '2025-07-24 07:09:41');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (39, 1, 18, 30, NULL, 3, '', '2025-05-08 03:29:08');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (39, 1, 18, 36, NULL, 3, '', '2025-05-08 03:29:08');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (39, 1, 18, 29, NULL, 3, '', '2025-05-08 03:29:08');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (39, 1, 18, 33, NULL, 2, '', '2025-05-08 03:29:08');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (40, 2, 16, 41, NULL, 2, '', '2025-08-19 08:48:15');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (40, 2, 16, 34, NULL, 2, '', '2025-08-19 08:48:15');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (40, 2, 16, 18, NULL, 2, '', '2025-08-19 08:48:15');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (40, 2, 16, 16, NULL, 2, '', '2025-08-19 08:48:15');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (41, 2, 5, 14, NULL, 1, '', '2025-04-29 12:44:09');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (42, 5, 15, 27, NULL, 2, '', '2025-08-01 01:13:53');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (42, 5, 15, 38, NULL, 3, '', '2025-08-01 01:13:53');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (42, 5, 15, 25, NULL, 2, '', '2025-08-01 01:13:53');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (43, 4, 14, 39, NULL, 1, '', '2025-10-03 23:47:34');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (43, 4, 14, 18, NULL, 2, '', '2025-10-03 23:47:34');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (43, 4, 14, 25, NULL, 2, '', '2025-10-03 23:47:34');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (44, 2, 15, 26, NULL, 3, '', '2025-03-07 19:34:01');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (44, 2, 15, 2, NULL, 1, '', '2025-03-07 19:34:01');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (44, 2, 15, 9, NULL, 2, '', '2025-03-07 19:34:01');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (44, 2, 15, 25, NULL, 2, '', '2025-03-07 19:34:01');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (45, 3, 13, 6, NULL, 2, '', '2025-05-23 13:16:53');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (45, 3, 13, 4, NULL, 2, '', '2025-05-23 13:16:53');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (45, 3, 13, 5, NULL, 3, '', '2025-05-23 13:16:53');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (46, 2, 1, 31, NULL, 3, '', '2025-11-15 04:15:08');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (46, 2, 1, 30, NULL, 3, '', '2025-11-15 04:15:08');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (47, 2, 20, 11, NULL, 2, '', '2025-11-07 23:45:07');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (47, 2, 20, 20, NULL, 3, '', '2025-11-07 23:45:07');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (47, 2, 20, 25, NULL, 2, '', '2025-11-07 23:45:07');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (48, 1, 19, 20, NULL, 3, '', '2025-12-20 20:15:06');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (48, 1, 19, 8, NULL, 3, '', '2025-12-20 20:15:06');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (49, 5, 14, 42, NULL, 2, '', '2025-12-05 11:04:32');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (49, 5, 14, 32, NULL, 1, '', '2025-12-05 11:04:32');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (49, 5, 14, 24, NULL, 3, '', '2025-12-05 11:04:32');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (50, 2, 14, 18, NULL, 3, '', '2025-04-01 23:33:41');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (50, 2, 14, 35, NULL, 2, '', '2025-04-01 23:33:41');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (50, 2, 14, 38, NULL, 2, '', '2025-04-01 23:33:41');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (50, 2, 14, 16, NULL, 1, '', '2025-04-01 23:33:41');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (51, 2, 15, 22, NULL, 1, '', '2025-10-19 19:42:24');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (51, 2, 15, 21, NULL, 1, '', '2025-10-19 19:42:24');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (51, 2, 15, 23, NULL, 2, '', '2025-10-19 19:42:24');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (51, 2, 15, 39, NULL, 3, '', '2025-10-19 19:42:24');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (52, 5, 7, 32, NULL, 3, '', '2025-02-13 07:46:26');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (53, 4, 15, 26, NULL, 3, '', '2025-01-09 02:18:14');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (53, 4, 15, 38, NULL, 2, '', '2025-01-09 02:18:14');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (53, 4, 15, 34, NULL, 2, '', '2025-01-09 02:18:14');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (53, 4, 15, 36, NULL, 2, '', '2025-01-09 02:18:14');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (54, 3, 10, 13, NULL, 2, '', '2025-05-09 07:07:46');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (54, 3, 10, 12, NULL, 1, '', '2025-05-09 07:07:46');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (54, 3, 10, 31, NULL, 2, '', '2025-05-09 07:07:46');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (54, 3, 10, 34, NULL, 3, '', '2025-05-09 07:07:46');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (55, 2, 10, 12, NULL, 3, '', '2025-04-27 11:11:19');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (56, 1, 2, 41, NULL, 2, '', '2025-10-11 09:44:08');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (56, 1, 2, 37, NULL, 2, '', '2025-10-11 09:44:08');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (56, 1, 2, 29, NULL, 2, '', '2025-10-11 09:44:08');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (57, 4, 4, 37, NULL, 3, '', '2025-02-03 12:31:04');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (57, 4, 4, 10, NULL, 1, '', '2025-02-03 12:31:04');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (57, 4, 4, 20, NULL, 1, '', '2025-02-03 12:31:04');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (58, 5, 14, 15, NULL, 3, '', '2025-11-07 19:50:39');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (59, 3, 19, 4, NULL, 3, '', '2025-08-08 09:36:39');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (59, 3, 19, 7, NULL, 1, '', '2025-08-08 09:36:39');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (59, 3, 19, 17, NULL, 3, '', '2025-08-08 09:36:39');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (59, 3, 19, 12, NULL, 3, '', '2025-08-08 09:36:39');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (60, 4, 20, 19, NULL, 3, '', '2025-08-29 09:02:14');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (60, 4, 20, 30, NULL, 1, '', '2025-08-29 09:02:14');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (60, 4, 20, 17, NULL, 3, '', '2025-08-29 09:02:14');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (60, 4, 20, 13, NULL, 2, '', '2025-08-29 09:02:14');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (61, 3, 5, 20, NULL, 3, '', '2025-02-06 01:10:50');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (61, 3, 5, 37, NULL, 2, '', '2025-02-06 01:10:50');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (62, 3, 13, 29, NULL, 1, '', '2025-05-20 16:34:31');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (62, 3, 13, 28, NULL, 3, '', '2025-05-20 16:34:31');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (62, 3, 13, 17, NULL, 1, '', '2025-05-20 16:34:31');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (62, 3, 13, 38, NULL, 1, '', '2025-05-20 16:34:31');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (63, 5, 2, 29, NULL, 2, '', '2025-03-31 15:33:41');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (63, 5, 2, 28, NULL, 3, '', '2025-03-31 15:33:41');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (63, 5, 2, 6, NULL, 2, '', '2025-03-31 15:33:41');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (64, 3, 4, 32, NULL, 2, '', '2025-03-24 10:26:44');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (64, 3, 4, 26, NULL, 3, '', '2025-03-24 10:26:44');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (64, 3, 4, 21, NULL, 2, '', '2025-03-24 10:26:44');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (65, 5, 1, 4, NULL, 1, '', '2025-12-03 17:29:26');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (65, 5, 1, 40, NULL, 2, '', '2025-12-03 17:29:26');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (65, 5, 1, 4, NULL, 1, '', '2025-12-03 17:29:26');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (65, 5, 1, 9, NULL, 2, '', '2025-12-03 17:29:26');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (66, 1, 1, 11, NULL, 2, '', '2025-11-19 19:51:15');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (66, 1, 1, 36, NULL, 2, '', '2025-11-19 19:51:15');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (66, 1, 1, 30, NULL, 1, '', '2025-11-19 19:51:15');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (66, 1, 1, 10, NULL, 2, '', '2025-11-19 19:51:15');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (67, 5, 9, 30, NULL, 3, '', '2025-08-01 15:30:15');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (67, 5, 9, 39, NULL, 3, '', '2025-08-01 15:30:15');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (67, 5, 9, 9, NULL, 1, '', '2025-08-01 15:30:15');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (68, 3, 17, 20, NULL, 3, '', '2025-05-17 00:18:46');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (68, 3, 17, 32, NULL, 1, '', '2025-05-17 00:18:46');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (68, 3, 17, 31, NULL, 2, '', '2025-05-17 00:18:46');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (68, 3, 17, 35, NULL, 2, '', '2025-05-17 00:18:46');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (69, 2, 8, 27, NULL, 1, '', '2025-10-20 12:14:54');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (69, 2, 8, 31, NULL, 3, '', '2025-10-20 12:14:54');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (69, 2, 8, 25, NULL, 2, '', '2025-10-20 12:14:54');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (70, 4, 2, 7, NULL, 2, '', '2025-03-06 16:37:21');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (70, 4, 2, 16, NULL, 3, '', '2025-03-06 16:37:21');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (71, 4, 9, 42, NULL, 1, '', '2025-06-23 19:44:25');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (72, 3, 16, 16, NULL, 3, '', '2025-10-05 01:39:04');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (72, 3, 16, 19, NULL, 1, '', '2025-10-05 01:39:04');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (72, 3, 16, 28, NULL, 1, '', '2025-10-05 01:39:04');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (72, 3, 16, 7, NULL, 2, '', '2025-10-05 01:39:04');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (73, 1, 11, 28, NULL, 1, '', '2025-01-29 09:22:23');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (74, 5, 6, 25, NULL, 3, '', '2025-03-29 05:05:39');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (74, 5, 6, 32, NULL, 3, '', '2025-03-29 05:05:39');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (74, 5, 6, 41, NULL, 2, '', '2025-03-29 05:05:39');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (74, 5, 6, 13, NULL, 2, '', '2025-03-29 05:05:39');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (75, 1, 15, 28, NULL, 3, '', '2025-06-26 18:19:40');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (75, 1, 15, 20, NULL, 1, '', '2025-06-26 18:19:40');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (76, 1, 8, 19, NULL, 3, '', '2025-07-15 18:22:36');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (76, 1, 8, 26, NULL, 2, '', '2025-07-15 18:22:36');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (76, 1, 8, 39, NULL, 3, '', '2025-07-15 18:22:36');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (76, 1, 8, 19, NULL, 1, '', '2025-07-15 18:22:36');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (77, 2, 7, 9, NULL, 3, '', '2025-11-14 08:43:48');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (77, 2, 7, 20, NULL, 2, '', '2025-11-14 08:43:48');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (77, 2, 7, 9, NULL, 1, '', '2025-11-14 08:43:48');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (78, 4, 6, 24, NULL, 3, '', '2025-04-13 04:50:34');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (78, 4, 6, 18, NULL, 1, '', '2025-04-13 04:50:34');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (78, 4, 6, 31, NULL, 2, '', '2025-04-13 04:50:34');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (79, 1, 15, 26, NULL, 3, '', '2025-02-08 04:48:14');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (79, 1, 15, 26, NULL, 1, '', '2025-02-08 04:48:14');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (79, 1, 15, 8, NULL, 2, '', '2025-02-08 04:48:14');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (80, 5, 13, 15, NULL, 2, '', '2025-11-23 11:06:43');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (80, 5, 13, 21, NULL, 3, '', '2025-11-23 11:06:43');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (80, 5, 13, 5, NULL, 3, '', '2025-11-23 11:06:43');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (81, 4, 4, 32, NULL, 1, '', '2025-03-13 01:02:19');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (81, 4, 4, 9, NULL, 2, '', '2025-03-13 01:02:19');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (81, 4, 4, 35, NULL, 2, '', '2025-03-13 01:02:19');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (82, 4, 4, 3, NULL, 3, '', '2025-09-08 19:26:17');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (82, 4, 4, 29, NULL, 2, '', '2025-09-08 19:26:17');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (83, 1, 12, 26, NULL, 2, '', '2025-10-06 20:22:03');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (83, 1, 12, 30, NULL, 1, '', '2025-10-06 20:22:03');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (83, 1, 12, 42, NULL, 3, '', '2025-10-06 20:22:03');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (84, 1, 11, 14, NULL, 1, '', '2025-05-05 04:50:36');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (85, 5, 7, 39, NULL, 1, '', '2025-04-30 10:49:09');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (85, 5, 7, 10, NULL, 1, '', '2025-04-30 10:49:09');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (86, 1, 1, 16, NULL, 3, '', '2025-03-09 00:22:50');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (86, 1, 1, 12, NULL, 2, '', '2025-03-09 00:22:50');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (87, 1, 16, 7, NULL, 2, '', '2025-08-18 11:32:37');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (88, 5, 10, 31, NULL, 2, '', '2025-08-23 20:01:03');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (89, 4, 15, 10, NULL, 1, '', '2025-02-07 02:20:38');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (90, 4, 20, 39, NULL, 2, '', '2025-09-29 09:29:32');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (90, 4, 20, 42, NULL, 3, '', '2025-09-29 09:29:32');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (90, 4, 20, 36, NULL, 3, '', '2025-09-29 09:29:32');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (91, 4, 8, 26, NULL, 2, '', '2025-07-31 10:52:29');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (91, 4, 8, 21, NULL, 2, '', '2025-07-31 10:52:29');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (91, 4, 8, 17, NULL, 2, '', '2025-07-31 10:52:29');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (91, 4, 8, 31, NULL, 1, '', '2025-07-31 10:52:29');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (92, 4, 4, 38, NULL, 3, '', '2025-07-10 04:35:03');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (93, 4, 12, 4, NULL, 2, '', '2025-12-07 13:55:58');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (94, 1, 19, 31, NULL, 1, '', '2025-09-17 06:09:42');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (94, 1, 19, 23, NULL, 3, '', '2025-09-17 06:09:42');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (94, 1, 19, 18, NULL, 3, '', '2025-09-17 06:09:42');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (95, 5, 20, 2, NULL, 3, '', '2025-11-11 21:41:35');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (95, 5, 20, 18, NULL, 1, '', '2025-11-11 21:41:35');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (95, 5, 20, 22, NULL, 2, '', '2025-11-11 21:41:35');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (95, 5, 20, 37, NULL, 3, '', '2025-11-11 21:41:35');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (96, 1, 3, 30, NULL, 2, '', '2025-09-29 06:24:26');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (96, 1, 3, 21, NULL, 3, '', '2025-09-29 06:24:26');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (97, 2, 6, 18, NULL, 2, '', '2025-11-13 01:43:05');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (98, 5, 15, 33, NULL, 1, '', '2025-08-01 08:13:48');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (98, 5, 15, 8, NULL, 2, '', '2025-08-01 08:13:48');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (98, 5, 15, 38, NULL, 2, '', '2025-08-01 08:13:48');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (98, 5, 15, 20, NULL, 1, '', '2025-08-01 08:13:48');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (99, 1, 7, 17, NULL, 2, '', '2025-06-04 06:49:08');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (100, 4, 14, 15, NULL, 3, '', '2025-03-31 04:24:34');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (101, 1, 13, 5, NULL, 2, '', '2025-01-22 13:01:29');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (101, 1, 13, 37, NULL, 2, '', '2025-01-22 13:01:29');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (101, 1, 13, 27, NULL, 2, '', '2025-01-22 13:01:29');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (102, 2, 20, 6, NULL, 2, '', '2025-08-24 22:58:23');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (102, 2, 20, 16, NULL, 2, '', '2025-08-24 22:58:23');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (102, 2, 20, 34, NULL, 1, '', '2025-08-24 22:58:23');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (103, 3, 8, 41, NULL, 1, '', '2025-06-20 05:04:32');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (103, 3, 8, 13, NULL, 2, '', '2025-06-20 05:04:32');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (103, 3, 8, 42, NULL, 1, '', '2025-06-20 05:04:32');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (104, 3, 7, 42, NULL, 1, '', '2025-03-30 19:09:48');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (104, 3, 7, 30, NULL, 3, '', '2025-03-30 19:09:48');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (105, 5, 20, 29, NULL, 1, '', '2025-06-15 20:20:09');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (105, 5, 20, 41, NULL, 2, '', '2025-06-15 20:20:09');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (105, 5, 20, 38, NULL, 1, '', '2025-06-15 20:20:09');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (105, 5, 20, 5, NULL, 2, '', '2025-06-15 20:20:09');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (106, 1, 12, 6, NULL, 3, '', '2025-05-27 02:41:55');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (107, 4, 19, 29, NULL, 3, '', '2025-10-11 23:57:02');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (107, 4, 19, 21, NULL, 3, '', '2025-10-11 23:57:02');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (107, 4, 19, 10, NULL, 1, '', '2025-10-11 23:57:02');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (107, 4, 19, 22, NULL, 3, '', '2025-10-11 23:57:02');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (108, 2, 15, 11, NULL, 2, '', '2025-08-13 16:33:39');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (109, 4, 14, 41, NULL, 3, '', '2025-06-23 21:38:03');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (109, 4, 14, 22, NULL, 1, '', '2025-06-23 21:38:03');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (109, 4, 14, 25, NULL, 2, '', '2025-06-23 21:38:03');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (110, 3, 3, 23, NULL, 2, '', '2025-10-26 21:09:58');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (110, 3, 3, 26, NULL, 1, '', '2025-10-26 21:09:58');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (111, 3, 18, 9, NULL, 3, '', '2025-07-12 20:50:21');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (112, 4, 17, 12, NULL, 1, '', '2025-07-05 00:23:19');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (113, 2, 8, 7, NULL, 3, '', '2025-03-12 04:04:18');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (113, 2, 8, 34, NULL, 1, '', '2025-03-12 04:04:18');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (113, 2, 8, 40, NULL, 1, '', '2025-03-12 04:04:18');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (113, 2, 8, 10, NULL, 1, '', '2025-03-12 04:04:18');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (114, 4, 2, 16, NULL, 2, '', '2025-07-30 11:43:46');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (114, 4, 2, 29, NULL, 1, '', '2025-07-30 11:43:46');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (115, 4, 7, 30, NULL, 2, '', '2025-07-08 21:36:28');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (115, 4, 7, 33, NULL, 3, '', '2025-07-08 21:36:28');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (115, 4, 7, 11, NULL, 1, '', '2025-07-08 21:36:28');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (116, 3, 2, 36, NULL, 1, '', '2025-11-25 15:55:23');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (116, 3, 2, 34, NULL, 1, '', '2025-11-25 15:55:23');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (117, 3, 15, 6, NULL, 1, '', '2025-09-20 04:53:27');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (117, 3, 15, 23, NULL, 1, '', '2025-09-20 04:53:27');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (118, 5, 12, 15, NULL, 1, '', '2025-05-01 12:05:23');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (118, 5, 12, 7, NULL, 3, '', '2025-05-01 12:05:23');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (118, 5, 12, 10, NULL, 1, '', '2025-05-01 12:05:23');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (118, 5, 12, 9, NULL, 3, '', '2025-05-01 12:05:23');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (119, 1, 1, 36, NULL, 3, '', '2025-05-12 06:53:09');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (120, 1, 10, 16, NULL, 2, '', '2025-05-02 09:07:03');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (120, 1, 10, 40, NULL, 2, '', '2025-05-02 09:07:03');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (120, 1, 10, 39, NULL, 3, '', '2025-05-02 09:07:03');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (120, 1, 10, 37, NULL, 1, '', '2025-05-02 09:07:03');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (121, 4, 1, 27, NULL, 1, '', '2025-11-11 11:15:36');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (121, 4, 1, 6, NULL, 3, '', '2025-11-11 11:15:36');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (121, 4, 1, 5, NULL, 3, '', '2025-11-11 11:15:36');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (122, 4, 16, 17, NULL, 3, '', '2025-01-23 20:24:23');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (123, 2, 4, 9, NULL, 1, '', '2025-10-25 23:48:21');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (123, 2, 4, 22, NULL, 3, '', '2025-10-25 23:48:21');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (123, 2, 4, 31, NULL, 3, '', '2025-10-25 23:48:21');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (124, 4, 2, 13, NULL, 1, '', '2025-05-31 06:02:50');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (125, 5, 13, 42, NULL, 1, '', '2025-10-06 15:16:02');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (125, 5, 13, 4, NULL, 3, '', '2025-10-06 15:16:02');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (125, 5, 13, 8, NULL, 2, '', '2025-10-06 15:16:02');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (126, 4, 13, 32, NULL, 2, '', '2025-06-23 05:31:44');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (126, 4, 13, 34, NULL, 2, '', '2025-06-23 05:31:44');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (126, 4, 13, 28, NULL, 1, '', '2025-06-23 05:31:44');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (126, 4, 13, 12, NULL, 3, '', '2025-06-23 05:31:44');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (127, 1, 11, 39, NULL, 3, '', '2025-12-05 09:19:28');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (128, 3, 15, 23, NULL, 3, '', '2025-01-22 23:55:58');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (128, 3, 15, 18, NULL, 3, '', '2025-01-22 23:55:58');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (128, 3, 15, 4, NULL, 1, '', '2025-01-22 23:55:58');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (128, 3, 15, 26, NULL, 2, '', '2025-01-22 23:55:58');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (129, 1, 5, 3, NULL, 1, '', '2025-11-08 21:50:28');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (129, 1, 5, 24, NULL, 2, '', '2025-11-08 21:50:28');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (130, 5, 5, 29, NULL, 1, '', '2025-12-14 14:23:23');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (131, 4, 11, 2, NULL, 3, '', '2025-11-29 08:15:07');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (131, 4, 11, 25, NULL, 3, '', '2025-11-29 08:15:07');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (131, 4, 11, 29, NULL, 1, '', '2025-11-29 08:15:07');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (132, 4, 7, 29, NULL, 1, '', '2025-03-04 04:54:04');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (132, 4, 7, 29, NULL, 1, '', '2025-03-04 04:54:04');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (132, 4, 7, 21, NULL, 3, '', '2025-03-04 04:54:04');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (133, 5, 18, 41, NULL, 1, '', '2025-05-29 09:54:10');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (134, 1, 7, 2, NULL, 2, '', '2025-03-13 07:50:31');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (134, 1, 7, 24, NULL, 2, '', '2025-03-13 07:50:31');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (135, 5, 3, 31, NULL, 3, '', '2025-02-03 09:25:45');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (135, 5, 3, 27, NULL, 3, '', '2025-02-03 09:25:45');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (136, 4, 15, 36, NULL, 3, '', '2025-12-15 16:22:08');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (137, 4, 17, 20, NULL, 1, '', '2025-01-29 03:33:09');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (137, 4, 17, 23, NULL, 3, '', '2025-01-29 03:33:09');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (138, 3, 7, 41, NULL, 2, '', '2025-11-22 17:17:08');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (139, 5, 6, 40, NULL, 3, '', '2025-10-31 18:09:10');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (140, 5, 2, 37, NULL, 2, '', '2025-01-15 02:02:41');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (140, 5, 2, 37, NULL, 2, '', '2025-01-15 02:02:41');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (140, 5, 2, 2, NULL, 2, '', '2025-01-15 02:02:41');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (141, 3, 16, 30, NULL, 1, '', '2025-05-06 21:25:19');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (141, 3, 16, 11, NULL, 2, '', '2025-05-06 21:25:19');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (141, 3, 16, 30, NULL, 1, '', '2025-05-06 21:25:19');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (142, 3, 11, 24, NULL, 3, '', '2025-06-26 12:08:48');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (142, 3, 11, 21, NULL, 1, '', '2025-06-26 12:08:48');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (143, 4, 8, 25, NULL, 3, '', '2025-03-14 03:03:18');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (143, 4, 8, 11, NULL, 2, '', '2025-03-14 03:03:18');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (143, 4, 8, 21, NULL, 1, '', '2025-03-14 03:03:18');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (144, 5, 15, 6, NULL, 2, '', '2025-09-13 09:31:01');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (144, 5, 15, 16, NULL, 1, '', '2025-09-13 09:31:01');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (144, 5, 15, 4, NULL, 1, '', '2025-09-13 09:31:01');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (144, 5, 15, 39, NULL, 3, '', '2025-09-13 09:31:01');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (145, 5, 1, 17, NULL, 3, '', '2025-02-25 13:08:56');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (145, 5, 1, 26, NULL, 2, '', '2025-02-25 13:08:56');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (145, 5, 1, 37, NULL, 3, '', '2025-02-25 13:08:56');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (146, 1, 13, 18, NULL, 3, '', '2025-09-16 14:48:35');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (146, 1, 13, 8, NULL, 1, '', '2025-09-16 14:48:35');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (146, 1, 13, 26, NULL, 2, '', '2025-09-16 14:48:35');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (147, 5, 12, 26, NULL, 3, '', '2025-03-15 06:38:32');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (147, 5, 12, 9, NULL, 3, '', '2025-03-15 06:38:32');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (147, 5, 12, 31, NULL, 3, '', '2025-03-15 06:38:32');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (148, 3, 20, 20, NULL, 3, '', '2025-06-13 05:25:39');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (148, 3, 20, 33, NULL, 3, '', '2025-06-13 05:25:39');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (149, 4, 1, 27, NULL, 3, '', '2025-07-08 10:43:07');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (149, 4, 1, 41, NULL, 1, '', '2025-07-08 10:43:07');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (149, 4, 1, 17, NULL, 3, '', '2025-07-08 10:43:07');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (150, 1, 19, 40, NULL, 2, '', '2025-09-03 05:33:40');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (150, 1, 19, 3, NULL, 3, '', '2025-09-03 05:33:40');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (151, 2, 1, 27, NULL, 3, '', '2025-08-14 10:26:09');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (152, 1, 5, 31, NULL, 3, '', '2025-09-23 06:35:20');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (152, 1, 5, 12, NULL, 2, '', '2025-09-23 06:35:20');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (152, 1, 5, 22, NULL, 3, '', '2025-09-23 06:35:20');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (152, 1, 5, 42, NULL, 3, '', '2025-09-23 06:35:20');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (153, 2, 8, 20, NULL, 2, '', '2025-05-23 17:19:14');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (153, 2, 8, 32, NULL, 2, '', '2025-05-23 17:19:14');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (153, 2, 8, 36, NULL, 3, '', '2025-05-23 17:19:14');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (153, 2, 8, 8, NULL, 3, '', '2025-05-23 17:19:14');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (154, 4, 12, 6, NULL, 2, '', '2025-03-16 09:02:18');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (154, 4, 12, 42, NULL, 2, '', '2025-03-16 09:02:18');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (154, 4, 12, 14, NULL, 1, '', '2025-03-16 09:02:18');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (154, 4, 12, 18, NULL, 3, '', '2025-03-16 09:02:18');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (155, 1, 20, 34, NULL, 1, '', '2025-10-28 07:15:03');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (155, 1, 20, 4, NULL, 1, '', '2025-10-28 07:15:03');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (156, 1, 5, 27, NULL, 3, '', '2025-01-03 17:59:10');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (156, 1, 5, 31, NULL, 2, '', '2025-01-03 17:59:10');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (156, 1, 5, 19, NULL, 2, '', '2025-01-03 17:59:10');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (156, 1, 5, 4, NULL, 1, '', '2025-01-03 17:59:10');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (157, 5, 2, 12, NULL, 1, '', '2025-03-31 13:56:53');
INSERT INTO Transactions (SaleID, StaffID, AccountID, ProductID, PromoID, Quantity, PromoCode, TransactionDate) VALUES (157, 5, 2, 26, NULL, 2, '', '2025-03-31 13:58:59');
-- Update SaleSequence after transactions
UPDATE SaleSequence SET nextID = 158;

select * from transactions;

-- Tasks 7-8: 40 returns (linked to valid TransactionIDs 1-550)
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (107, 1, 5, '2025-06-12 00:00:00', 'Duplicate', 348.82);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (293, 1, 3, '2025-05-03 00:00:00', 'Wrong item', 437.48);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (68, 1, 3, '2025-01-13 00:00:00', 'Duplicate', 136.95);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (253, 1, 5, '2025-01-19 00:00:00', 'Changed mind', 164.12);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (263, 1, 3, '2025-12-16 00:00:00', 'Duplicate', 188.09);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (101, 2, 1, '2025-05-13 00:00:00', 'Defective', 208.28);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (376, 2, 2, '2025-06-05 00:00:00', 'Expired', 347.04);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (250, 1, 4, '2025-06-14 00:00:00', 'Changed mind', 499.52);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (354, 2, 2, '2025-04-14 00:00:00', 'Changed mind', 64.94);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (225, 1, 3, '2025-01-11 00:00:00', 'Defective', 440.83);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (347, 2, 3, '2025-04-09 00:00:00', 'Defective', 294.98);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (209, 1, 3, '2025-08-07 00:00:00', 'Expired', 347.46);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (260, 1, 1, '2025-08-01 00:00:00', 'Wrong item', 217.87);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (213, 2, 3, '2025-07-27 00:00:00', 'Wrong item', 55.71);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (183, 1, 2, '2025-10-13 00:00:00', 'Changed mind', 272.34);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (348, 2, 2, '2025-07-11 00:00:00', 'Wrong item', 415.97);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (375, 1, 3, '2025-08-22 00:00:00', 'Defective', 319.29);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (14, 1, 3, '2025-08-20 00:00:00', 'Defective', 171.64);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (116, 2, 2, '2025-11-02 00:00:00', 'Wrong item', 488.80);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (12, 1, 1, '2025-05-20 00:00:00', 'Defective', 134.44);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (43, 1, 1, '2025-01-12 00:00:00', 'Expired', 270.16);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (328, 1, 1, '2025-11-16 00:00:00', 'Wrong item', 52.04);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (106, 1, 4, '2025-07-03 00:00:00', 'Changed mind', 186.50);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (377, 1, 1, '2025-11-23 00:00:00', 'Wrong item', 473.94);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (103, 2, 3, '2025-03-26 00:00:00', 'Wrong item', 429.88);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (127, 1, 3, '2025-08-16 00:00:00', 'Changed mind', 95.74);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (240, 2, 3, '2025-07-04 00:00:00', 'Expired', 211.68);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (33, 2, 4, '2025-03-24 00:00:00', 'Defective', 443.89);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (389, 1, 2, '2025-02-13 00:00:00', 'Expired', 246.01);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (227, 2, 4, '2025-04-08 00:00:00', 'Defective', 180.38);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (236, 2, 2, '2025-02-22 00:00:00', 'Changed mind', 231.92);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (338, 1, 5, '2025-11-17 00:00:00', 'Defective', 281.04);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (214, 2, 1, '2025-04-19 00:00:00', 'Duplicate', 288.32);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (96, 2, 3, '2025-08-23 00:00:00', 'Wrong item', 248.86);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (364, 2, 1, '2025-06-24 00:00:00', 'Duplicate', 117.11);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (375, 1, 1, '2025-06-09 00:00:00', 'Expired', 208.23);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (163, 1, 2, '2025-08-20 00:00:00', 'Wrong item', 213.68);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (391, 1, 3, '2025-12-09 00:00:00', 'Wrong item', 439.10);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (120, 1, 2, '2025-02-09 00:00:00', 'Defective', 356.85);
INSERT INTO Returns (TransactionID, ReturnQuantity, StaffID, ReturnDate, Reason, RefundAmount) VALUES (269, 2, 4, '2025-09-19 00:00:00', 'Duplicate', 122.22);