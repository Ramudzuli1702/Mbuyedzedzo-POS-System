package com.pos.views;

import com.pos.models.User;
import com.pos.models.StaffPerformance;
import com.pos.services.UserService;
import com.pos.utils.PasswordUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.time.YearMonth;
import java.time.LocalDate;
import java.util.Optional;

public class UserManagementView {
    private User currentUser;
    private UserService userService;
    private TableView<User> userTable;
    private TextField searchField;
    private BorderPane mainLayout;
    private BorderPane performancePane;
    private TableView<StaffPerformance> performanceTable;
    private LineChart<Number, Number> salesChart;
    private LineChart<Number, Number> hoursChart;
    private ComboBox<YearMonth> monthSelector;

    public UserManagementView(User user) {
        this.currentUser = user;
        this.userService = new UserService();
    }

    public BorderPane getView() {
        mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-background-color: #f5f7fa;");

        HBox topBar = createTopBar();
        mainLayout.setTop(topBar);

        VBox mainContent = new VBox(20);
        mainContent.setPadding(new Insets(20));

        VBox tableBox = createUserTable();
        mainContent.getChildren().add(tableBox);

        mainLayout.setCenter(mainContent);

        performancePane = createPerformancePane();

        return mainLayout;
    }

    private HBox createTopBar() {
        HBox topBar = new HBox(20);
        topBar.setPadding(new Insets(20));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: white; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");

        Label title = new Label("👥 User Management");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));
        title.setTextFill(Color.web("#0f766e"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        searchField = new TextField();
        searchField.setPromptText("🔍 Search users...");
        searchField.setPrefWidth(300);
        searchField.setStyle("-fx-font-size: 13; -fx-padding: 10;");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> searchUsers(newVal));

        Button addBtn = new Button("+ Add User");
        addBtn.setStyle("-fx-background-color: #0f766e; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 6; -fx-cursor: hand;");
        addBtn.setOnAction(e -> showAddUserDialog());

        Button performanceBtn = new Button("📊 Performance Report");
        performanceBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 6; -fx-cursor: hand;");
        performanceBtn.setVisible(isAdmin());
        performanceBtn.setOnAction(e -> showPerformanceView());

        topBar.getChildren().addAll(title, spacer, searchField, addBtn, performanceBtn);

        return topBar;
    }

    private boolean isAdmin() {
        return "Admin".equals(currentUser.getUserType());
    }

    private void showPerformanceView() {
        if (mainLayout != null) {
            mainLayout.setCenter(performancePane);
        } else {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setContentText("Unable to load performance view. Ensure the main layout is properly set.");
            alert.showAndWait();
        }
    }

    private BorderPane createPerformancePane() {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: #f5f7fa;");
        pane.setPadding(new Insets(20));

        monthSelector = new ComboBox<>();
        YearMonth current = YearMonth.now();
        for (int i = 0; i < 12; i++) {
            monthSelector.getItems().add(current.minusMonths(i));
        }
        monthSelector.setValue(current);
        monthSelector.setOnAction(e -> loadPerformanceData());

        HBox selectorBox = new HBox(10, new Label("Select Month:"), monthSelector);
        selectorBox.setAlignment(Pos.CENTER);

        Button backBtn = new Button("← Back to Users");
        backBtn.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 6; -fx-cursor: hand;");
        backBtn.setOnAction(e -> showUserView());
        HBox topWithBack = new HBox(20, backBtn, selectorBox);
        topWithBack.setAlignment(Pos.CENTER_LEFT);
        pane.setTop(topWithBack);

        VBox chartsBox = new VBox(20);
        chartsBox.setPadding(new Insets(20));

        NumberAxis xAxisSales = new NumberAxis(1, 31, 1);
        xAxisSales.setLabel("Day of Month");
        NumberAxis yAxisSales = new NumberAxis();
        yAxisSales.setLabel("Total Sales (R)");
        salesChart = new LineChart<>(xAxisSales, yAxisSales);
        salesChart.setTitle("Daily Sales per Staff");
        salesChart.setPrefHeight(300);

        NumberAxis xAxisHours = new NumberAxis(1, 31, 1);
        xAxisHours.setLabel("Day of Month");
        NumberAxis yAxisHours = new NumberAxis();
        yAxisHours.setLabel("Hours Worked");
        hoursChart = new LineChart<>(xAxisHours, yAxisHours);
        hoursChart.setTitle("Daily Hours Logged per Staff");
        hoursChart.setPrefHeight(300);

        chartsBox.getChildren().addAll(salesChart, hoursChart);

        VBox tableBox = new VBox(15);
        tableBox.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);");
        tableBox.setPadding(new Insets(20));

        Label tableTitle = new Label("Staff Performance Ranking");
        tableTitle.setFont(Font.font("System", FontWeight.BOLD, 18));

        performanceTable = new TableView<>();
        performanceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<StaffPerformance, Integer> rankCol = new TableColumn<>("Rank");
        rankCol.setCellValueFactory(new PropertyValueFactory<>("rank"));
        rankCol.setPrefWidth(60);

        TableColumn<StaffPerformance, String> nameCol = new TableColumn<>("Full Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("fullNames"));
        nameCol.setPrefWidth(200);

        TableColumn<StaffPerformance, Double> salesCol = new TableColumn<>("Total Sales (R)");
        salesCol.setCellValueFactory(new PropertyValueFactory<>("totalSales"));
        salesCol.setPrefWidth(150);

        TableColumn<StaffPerformance, Double> hoursCol = new TableColumn<>("Total Hours");
        hoursCol.setCellValueFactory(new PropertyValueFactory<>("totalHours"));
        hoursCol.setPrefWidth(120);

        TableColumn<StaffPerformance, Double> effCol = new TableColumn<>("Efficiency (R/hr)");
        effCol.setCellValueFactory(new PropertyValueFactory<>("efficiency"));
        effCol.setPrefWidth(150);

        performanceTable.getColumns().addAll(rankCol, nameCol, salesCol, hoursCol, effCol);

        VBox.setVgrow(performanceTable, Priority.ALWAYS);
        tableBox.getChildren().addAll(tableTitle, performanceTable);

        HBox contentBox = new HBox(20, chartsBox, tableBox);
        HBox.setHgrow(chartsBox, Priority.ALWAYS);
        pane.setCenter(contentBox);

        loadPerformanceData();

        return pane;
    }

    private void showUserView() {
        if (mainLayout != null) {
            VBox mainContent = new VBox(20);
            mainContent.setPadding(new Insets(20));
            VBox tableBox = createUserTable();
            mainContent.getChildren().add(tableBox);
            mainLayout.setCenter(mainContent);
        }
    }

    private void loadPerformanceData() {
        YearMonth selectedMonth = monthSelector.getValue();
        if (selectedMonth == null) return;

        java.util.List<StaffPerformance> performances = userService.getStaffPerformance(selectedMonth.getYear(), selectedMonth.getMonthValue());
        ObservableList<StaffPerformance> obsList = FXCollections.observableArrayList(performances);
        performanceTable.setItems(obsList);

        loadSalesChart(selectedMonth);
        loadHoursChart(selectedMonth);
    }

    private void loadSalesChart(YearMonth ym) {
        salesChart.getData().clear();
        java.util.Map<Integer, java.util.Map<LocalDate, Double>> dailySales = userService.getDailySalesAllStaff(ym);

        for (java.util.Map.Entry<Integer, java.util.Map<LocalDate, Double>> entry : dailySales.entrySet()) {
            int staffID = entry.getKey();
            String staffName = userService.getStaffNameById(staffID);
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName(staffName);

            entry.getValue().forEach((date, amount) -> {
                int day = date.getDayOfMonth();
                series.getData().add(new XYChart.Data<>(day, amount));
            });

            salesChart.getData().add(series);
        }
    }

    private void loadHoursChart(YearMonth ym) {
        hoursChart.getData().clear();
        java.util.Map<Integer, java.util.Map<LocalDate, Double>> dailyHours = userService.getDailyHoursAllStaff(ym);

        for (java.util.Map.Entry<Integer, java.util.Map<LocalDate, Double>> entry : dailyHours.entrySet()) {
            int staffID = entry.getKey();
            String staffName = userService.getStaffNameById(staffID);
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName(staffName);

            entry.getValue().forEach((date, hours) -> {
                int day = date.getDayOfMonth();
                series.getData().add(new XYChart.Data<>(day, hours));
            });

            hoursChart.getData().add(series);
        }
    }

    private VBox createUserTable() {
        VBox tableBox = new VBox(15);
        tableBox.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);");
        tableBox.setPadding(new Insets(20));

        Label tableTitle = new Label("Staff Members");
        tableTitle.setFont(Font.font("System", FontWeight.BOLD, 18));

        userTable = new TableView<>();
        userTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<User, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("staffID"));
        idCol.setPrefWidth(60);

        TableColumn<User, String> nameCol = new TableColumn<>("Full Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("fullNames"));
        nameCol.setPrefWidth(200);

        TableColumn<User, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(new PropertyValueFactory<>("emailAddress"));
        emailCol.setPrefWidth(250);

        TableColumn<User, String> typeCol = new TableColumn<>("User Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("userType"));
        typeCol.setPrefWidth(120);
        typeCol.setCellFactory(column -> new TableCell<User, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    switch (item) {
                        case "Admin":
                            setStyle("-fx-background-color: #e3f2fd; -fx-text-fill: #1976d2;");
                            break;
                        case "Manager":
                            setStyle("-fx-background-color: #f3e5f5; -fx-text-fill: #7b1fa2;");
                            break;
                        case "Cashier":
                            setStyle("-fx-background-color: #e8f5e9; -fx-text-fill: #388e3c;");
                            break;
                        default:
                            setStyle("");
                    }
                }
            }
        });

        TableColumn<User, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(100);
        statusCol.setCellFactory(column -> new TableCell<User, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("Active".equals(item)) {
                        setStyle("-fx-background-color: #c8e6c9; -fx-text-fill: #2e7d32;");
                    } else {
                        setStyle("-fx-background-color: #ffcdd2; -fx-text-fill: #c62828;");
                    }
                }
            }
        });

        TableColumn<User, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setPrefWidth(180);
        actionCol.setCellFactory(param -> new TableCell<>() {
            private final Button editBtn = new Button("✏️");
            private final Button resetPwdBtn = new Button("🔑");
            private final Button toggleBtn = new Button("⏸️");

            {
                editBtn.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-size: 10; -fx-padding: 5 10; -fx-cursor: hand;");
                resetPwdBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 10; -fx-padding: 5 10; -fx-cursor: hand;");
                toggleBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 10; -fx-padding: 5 10; -fx-cursor: hand;");

                editBtn.setOnAction(e -> {
                    User user = getTableView().getItems().get(getIndex());
                    showEditUserDialog(user);
                });

                resetPwdBtn.setOnAction(e -> {
                    User user = getTableView().getItems().get(getIndex());
                    resetPassword(user);
                });

                toggleBtn.setOnAction(e -> {
                    User user = getTableView().getItems().get(getIndex());
                    toggleUserStatus(user);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    User user = getTableView().getItems().get(getIndex());
                    if (user.getStaffID() == currentUser.getStaffID()) {
                        setGraphic(new Label("(You)"));
                    } else {
                        HBox buttons = new HBox(5, editBtn, resetPwdBtn, toggleBtn);
                        buttons.setAlignment(Pos.CENTER);
                        setGraphic(buttons);
                    }
                }
            }
        });

        userTable.getColumns().addAll(idCol, nameCol, emailCol, typeCol, statusCol, actionCol);

        loadUsers();

        VBox.setVgrow(userTable, Priority.ALWAYS);
        tableBox.getChildren().addAll(tableTitle, userTable);

        return tableBox;
    }

    private void loadUsers() {
        ObservableList<User> users = userService.getAllUsers();
        userTable.setItems(users);
    }

    private void searchUsers(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            loadUsers();
        } else {
            ObservableList<User> results = userService.searchUsers(keyword);
            userTable.setItems(results);
        }
    }

    private void showAddUserDialog() {
        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle("Add New User");
        dialog.setHeaderText("Enter user details");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameField = new TextField();
        TextField emailField = new TextField();
        PasswordField passwordField = new PasswordField();
        PasswordField confirmPasswordField = new PasswordField();
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("Admin", "Manager", "Cashier");
        typeCombo.setValue("Cashier");

        grid.add(new Label("Full Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Email:"), 0, 1);
        grid.add(emailField, 1, 1);
        grid.add(new Label("Password:"), 0, 2);
        grid.add(passwordField, 1, 2);
        grid.add(new Label("Confirm Password:"), 0, 3);
        grid.add(confirmPasswordField, 1, 3);
        grid.add(new Label("User Type:"), 0, 4);
        grid.add(typeCombo, 1, 4);

        Label pwdHint = new Label("Password must be at least 8 characters");
        pwdHint.setFont(Font.font(10));
        pwdHint.setTextFill(Color.GRAY);
        grid.add(pwdHint, 1, 5);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                String password = passwordField.getText();
                String confirmPassword = confirmPasswordField.getText();

                if (!password.equals(confirmPassword)) {
                    showAlert("Error", "Passwords do not match", Alert.AlertType.ERROR);
                    return null;
                }

                if (!PasswordUtil.isValidPassword(password)) {
                    showAlert("Error", "Password must be at least 8 characters", Alert.AlertType.ERROR);
                    return null;
                }

                User user = new User();
                user.setFullNames(nameField.getText());
                user.setEmailAddress(emailField.getText());
                user.setUserType(typeCombo.getValue());
                user.setUserPassword(password);
                return user;
            }
            return null;
        });

        Optional<User> result = dialog.showAndWait();
        result.ifPresent(user -> {
            if (userService.addUser(user, user.getUserPassword())) {
                showAlert("Success", "User added successfully", Alert.AlertType.INFORMATION);
                loadUsers();
            } else {
                showAlert("Error", "Failed to add user. Email might already exist.", Alert.AlertType.ERROR);
            }
        });
    }

    private void showEditUserDialog(User user) {
        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle("Edit User");
        dialog.setHeaderText("Modify user details");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameField = new TextField(user.getFullNames());
        TextField emailField = new TextField(user.getEmailAddress());
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("Admin", "Manager", "Cashier");
        typeCombo.setValue(user.getUserType());
        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("Active", "Inactive");
        statusCombo.setValue(user.getStatus());

        grid.add(new Label("Full Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Email:"), 0, 1);
        grid.add(emailField, 1, 1);
        grid.add(new Label("User Type:"), 0, 2);
        grid.add(typeCombo, 1, 2);
        grid.add(new Label("Status:"), 0, 3);
        grid.add(statusCombo, 1, 3);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                user.setFullNames(nameField.getText());
                user.setEmailAddress(emailField.getText());
                user.setUserType(typeCombo.getValue());
                user.setStatus(statusCombo.getValue());
                return user;
            }
            return null;
        });

        Optional<User> result = dialog.showAndWait();
        result.ifPresent(u -> {
            if (userService.updateUser(u)) {
                showAlert("Success", "User updated successfully", Alert.AlertType.INFORMATION);
                loadUsers();
            } else {
                showAlert("Error", "Failed to update user", Alert.AlertType.ERROR);
            }
        });
    }

    private void resetPassword(User user) {
        String newPassword = PasswordUtil.generateRandomPassword();

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Reset Password");
        alert.setHeaderText("Reset password for " + user.getFullNames() + "?");
        alert.setContentText("New password will be: " + newPassword + "\n\nPlease write this down!");

        if (alert.showAndWait().get() == ButtonType.OK) {
            if (userService.resetPassword(user.getStaffID(), newPassword)) {
                showAlert("Success", "Password reset successfully!\n\nNew Password: " + newPassword, Alert.AlertType.INFORMATION);
            } else {
                showAlert("Error", "Failed to reset password", Alert.AlertType.ERROR);
            }
        }
    }

    private void toggleUserStatus(User user) {
        String newStatus = "Active".equals(user.getStatus()) ? "Inactive" : "Active";
        user.setStatus(newStatus);

        if (userService.updateUser(user)) {
            showAlert("Success", "User status updated", Alert.AlertType.INFORMATION);
            loadUsers();
        } else {
            showAlert("Error", "Failed to update status", Alert.AlertType.ERROR);
        }
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}