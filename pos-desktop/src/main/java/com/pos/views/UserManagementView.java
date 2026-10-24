package com.pos.views;

import com.pos.models.User;
import com.pos.models.StaffPerformance;
import com.pos.services.SessionService;
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
    private final SessionService sessionService = new SessionService();
    private TableView<User> userTable;
    private TextField searchField;
    private BorderPane mainLayout;
    private BorderPane performancePane;
    private TableView<StaffPerformance> performanceTable;
    private LineChart<Number, Number> salesChart;
    private LineChart<Number, Number> hoursChart;
    private ComboBox<YearMonth> monthSelector;
    private VBox performanceSummary;
    private Label performanceRange;

    public UserManagementView(User user) {
        this(user,new UserService());
    }

    public UserManagementView(User user,UserService service) {
        this.currentUser = user;
        this.userService = service;
    }

    private HBox createSummaryCards() {
        var users = userService.getAllUsers();
        long active   = users.stream().filter(u -> "Active".equalsIgnoreCase(u.getStatus())).count();
        long admins   = users.stream().filter(u -> u.isAdmin()).count();
        long managers = users.stream().filter(u -> u.isManager()).count();
        long cashiers = users.stream().filter(u -> u.isCashier()).count();

        return com.pos.components.SummaryCards.row(
            new com.pos.components.SummaryCards.Card("Active Staff", String.valueOf(active), "#0f766e", "of " + users.size() + " total"),
            new com.pos.components.SummaryCards.Card("Admins",   String.valueOf(admins),   "#7c3aed", "full access"),
            new com.pos.components.SummaryCards.Card("Managers", String.valueOf(managers), "#0ea5e9", "full access"),
            new com.pos.components.SummaryCards.Card("Cashiers", String.valueOf(cashiers), "#16a34a", "sales only")
        );
    }

    public BorderPane getView() {
        mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-background-color: #f5f7fa;");

        HBox topBar = createTopBar();
        mainLayout.setTop(topBar);

        VBox mainContent = new VBox(20);
        mainContent.setPadding(new Insets(20));

        VBox tableBox = createUserTable();
        mainContent.getChildren().addAll(createSummaryCards(), tableBox);

        mainLayout.setCenter(mainContent);

        performancePane = createPerformancePane();

        return mainLayout;
    }

    private HBox createTopBar() {
        HBox topBar = new HBox(20);
        topBar.setPadding(new Insets(20));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");

        Label title = new Label("User Management");
        title.setFont(Font.font("System", FontWeight.BOLD, 24));
        title.setTextFill(Color.web("#0f766e"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        searchField = new TextField();
        searchField.setPromptText("Search users...");
        searchField.setPrefWidth(300);
        searchField.setStyle("-fx-font-size: 13; -fx-padding: 10;");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> searchUsers(newVal));

        Button addBtn = new Button("+ Add User");
        addBtn.setStyle("-fx-background-color: #0f766e; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 6; -fx-cursor: hand;");
        addBtn.setOnAction(e -> showAddUserDialog());

        Button performanceBtn = new Button("Performance Report");
        performanceBtn.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 6; -fx-cursor: hand;");
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
            mainLayout.setTop(null);
            mainLayout.setCenter(performancePane);
        } else {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setContentText("Unable to load performance view. Ensure the main layout is properly set.");
            alert.showAndWait();
        }
    }

    private BorderPane createPerformancePane() {
        BorderPane pane=new BorderPane();pane.setStyle(com.pos.utils.Theme.page());
        pane.getStyleClass().addAll("reports-view","performance-report");
        pane.getStylesheets().add(getClass().getResource("/css/reports.css").toExternalForm());
        Label title=new Label("Staff performance");title.setFont(Font.font("System",FontWeight.BOLD,24));title.setTextFill(Color.web(com.pos.utils.Theme.BRAND));
        Label subtitle=new Label("Compare staff sales and logged hours for the selected month.");subtitle.setFont(com.pos.utils.Theme.body());subtitle.setTextFill(Color.web(com.pos.utils.Theme.TEXT_MUTED));
        Button back=new Button("Back to users"),refresh=new Button("Refresh");back.setStyle(com.pos.utils.Theme.secondaryButton());refresh.setStyle(com.pos.utils.Theme.secondaryButton());
        back.setOnAction(e->showUserView());refresh.setOnAction(e->loadPerformanceData());
        Region spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
        HBox header=new HBox(12,new VBox(6,title,subtitle),spacer,back,refresh);header.setAlignment(Pos.CENTER_LEFT);header.setPadding(new Insets(20,28,16,28));header.setStyle(com.pos.utils.Theme.topBar());
        monthSelector=new ComboBox<>();YearMonth current=YearMonth.now();for(int i=0;i<12;i++)monthSelector.getItems().add(current.minusMonths(i));
        monthSelector.setValue(current);monthSelector.setStyle(com.pos.utils.Theme.input());monthSelector.setPrefWidth(240);
        monthSelector.setConverter(new javafx.util.StringConverter<YearMonth>() {
            public String toString(YearMonth month){return month==null?"":month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"));}
            public YearMonth fromString(String text){return null;}
        });
        performanceRange=new Label();performanceRange.setWrapText(true);performanceRange.setTextFill(Color.web(com.pos.utils.Theme.TEXT_MUTED));
        Label monthLabel=new Label("Month");monthLabel.setFont(com.pos.utils.Theme.label());
        VBox toolbar=new VBox(8,monthLabel,monthSelector,performanceRange);toolbar.setPadding(new Insets(16,24,16,24));toolbar.setStyle(com.pos.utils.Theme.topBar());
        pane.setTop(new VBox(header,toolbar));
        salesChart=new LineChart<>(new NumberAxis(1,current.lengthOfMonth(),1),new NumberAxis());
        hoursChart=new LineChart<>(new NumberAxis(1,current.lengthOfMonth(),1),new NumberAxis());
        salesChart.getXAxis().setLabel("Day of month");salesChart.getYAxis().setLabel("Sales (R)");hoursChart.getXAxis().setLabel("Day of month");hoursChart.getYAxis().setLabel("Hours worked");
        for(var chart:java.util.List.of(salesChart,hoursChart)){chart.setAnimated(false);chart.setCreateSymbols(true);chart.setPrefHeight(320);chart.setMinHeight(280);}
        performanceTable=new TableView<>();performanceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);performanceTable.setPrefHeight(320);performanceTable.setPlaceholder(new Label("No staff performance recorded for this month."));
        TableColumn<StaffPerformance,Integer> rankCol=new TableColumn<>("Rank");rankCol.setCellValueFactory(new PropertyValueFactory<>("rank"));rankCol.setMaxWidth(80);
        TableColumn<StaffPerformance,String> nameCol=new TableColumn<>("Staff member");nameCol.setCellValueFactory(new PropertyValueFactory<>("fullNames"));
        TableColumn<StaffPerformance,Double> salesCol=new TableColumn<>("Sales (R)");salesCol.setCellValueFactory(new PropertyValueFactory<>("totalSales"));
        TableColumn<StaffPerformance,Double> hoursCol=new TableColumn<>("Hours worked");hoursCol.setCellValueFactory(new PropertyValueFactory<>("totalHours"));
        TableColumn<StaffPerformance,Double> efficiencyCol=new TableColumn<>("Sales per hour (R)");efficiencyCol.setCellValueFactory(new PropertyValueFactory<>("efficiency"));
        for(var column:java.util.List.of(salesCol,hoursCol,efficiencyCol))column.setCellFactory(c->new TableCell<>() {
            @Override protected void updateItem(Double value,boolean empty){super.updateItem(value,empty);setText(empty||value==null?null:String.format(java.util.Locale.ROOT,"%,.2f",value));setAlignment(Pos.CENTER_RIGHT);}
        });
        performanceTable.getColumns().addAll(rankCol,nameCol,salesCol,hoursCol,efficiencyCol);
        performanceSummary=new VBox();
        VBox body=new VBox(20,performanceSummary,com.pos.components.Ui.section("Daily sales",salesChart),
                com.pos.components.Ui.section("Daily hours worked",hoursChart),com.pos.components.Ui.section("Staff ranking",performanceTable));body.setPadding(new Insets(24));
        ScrollPane scroll=new ScrollPane(body);scroll.setFitToWidth(true);scroll.setStyle(com.pos.utils.Theme.page());pane.setCenter(scroll);
        monthSelector.setOnAction(e->loadPerformanceData());loadPerformanceData();return pane;
    }

    private void showUserView() {
        if (mainLayout != null) {
            mainLayout.setTop(createTopBar());
            VBox mainContent = new VBox(20);
            mainContent.setPadding(new Insets(20));
            VBox tableBox = createUserTable();
            mainContent.getChildren().add(tableBox);
            mainLayout.setCenter(mainContent);
        }
    }

    private void loadPerformanceData() {
        YearMonth month=monthSelector.getValue();if(month==null)return;
        var performances=userService.getStaffPerformance(month.getYear(),month.getMonthValue());
        performanceTable.setItems(FXCollections.observableArrayList(performances));
        double sales=performances.stream().mapToDouble(StaffPerformance::getTotalSales).sum();
        double hours=performances.stream().mapToDouble(StaffPerformance::getTotalHours).sum();
        performanceSummary.getChildren().setAll(com.pos.components.SummaryCards.row(
                new com.pos.components.SummaryCards.Card("Staff reported",Integer.toString(performances.size()),com.pos.utils.Theme.BRAND,"Selected month"),
                new com.pos.components.SummaryCards.Card("Total sales",String.format(java.util.Locale.ROOT,"R%,.2f",sales),com.pos.utils.Theme.BRAND),
                new com.pos.components.SummaryCards.Card("Hours worked",String.format(java.util.Locale.ROOT,"%,.2f",hours),com.pos.utils.Theme.BRAND),
                new com.pos.components.SummaryCards.Card("Sales per hour",String.format(java.util.Locale.ROOT,"R%,.2f",hours>0?sales/hours:0),com.pos.utils.Theme.BRAND,"Sales divided by logged hours")));
        performanceRange.setText(month.atDay(1)+" to "+month.atEndOfMonth()+" · Sales and logged hours are shown separately.");
        ((NumberAxis)salesChart.getXAxis()).setUpperBound(month.lengthOfMonth());((NumberAxis)hoursChart.getXAxis()).setUpperBound(month.lengthOfMonth());
        loadSalesChart(month);loadHoursChart(month);
    }

    private void loadSalesChart(YearMonth ym) {
        salesChart.getData().clear();
        java.util.Map<Integer, java.util.Map<LocalDate, Double>> dailySales = userService.getDailySalesAllStaff(ym);

        for (StaffPerformance staff : performanceTable.getItems()) {
            int staffID = staff.getStaffID();
            String staffName = staff.getFullNames();
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName(staffName);

            dailySales.getOrDefault(staffID,java.util.Map.of()).entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
                LocalDate date=entry.getKey();Double amount=entry.getValue();
                int day = date.getDayOfMonth();
                series.getData().add(new XYChart.Data<>(day, amount));
            });

            salesChart.getData().add(series);
        }
    }

    private void loadHoursChart(YearMonth ym) {
        hoursChart.getData().clear();
        java.util.Map<Integer, java.util.Map<LocalDate, Double>> dailyHours = userService.getDailyHoursAllStaff(ym);

        for (StaffPerformance staff : performanceTable.getItems()) {
            int staffID = staff.getStaffID();
            String staffName = staff.getFullNames();
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName(staffName);

            dailyHours.getOrDefault(staffID,java.util.Map.of()).entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
                LocalDate date=entry.getKey();Double hours=entry.getValue();
                int day = date.getDayOfMonth();
                series.getData().add(new XYChart.Data<>(day, hours));
            });

            hoursChart.getData().add(series);
        }
    }

    private VBox createUserTable() {
        VBox tableBox = new VBox(15);
        tableBox.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 18, 0, 0, 4);");
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

        TableColumn<User, Void> actionCol = new TableColumn<>("");
        actionCol.setPrefWidth(90);
        actionCol.setCellFactory(param -> new TableCell<>() {
            private final Button viewBtn = com.pos.components.Ui.viewButton();

            {
                viewBtn.setOnAction(e -> {
                    User user = getTableView().getItems().get(getIndex());
                    showUserDetailsDialog(user);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    User user = getTableView().getItems().get(getIndex());
                    setGraphic(user.getStaffID() == currentUser.getStaffID() ? new Label("(You)") : viewBtn);
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
        TextField name=new TextField(),email=new TextField();PasswordField password=new PasswordField(),confirm=new PasswordField();
        ComboBox<String> role=new ComboBox<>();role.getItems().addAll("Admin","Manager","Cashier");role.setValue("Cashier");
        var profile=com.pos.components.Ui.formGrid();
        com.pos.components.Ui.formField(profile,0,"Full name",name);com.pos.components.Ui.formField(profile,1,"Email address",email);
        com.pos.components.Ui.formField(profile,2,"Role",role);
        var security=com.pos.components.Ui.formGrid();com.pos.components.Ui.formField(security,0,"Password",password);com.pos.components.Ui.formField(security,1,"Confirm password",confirm);
        com.pos.components.Ui.createForm("Add user","New staff member",new VBox(16,
                com.pos.components.Ui.section("Staff information",profile),
                com.pos.components.Ui.section("Login details",security,new Label("Use a password with at least 8 characters."))),"Add user",()->{
            if(name.getText().isBlank() || !email.getText().contains("@") || role.getValue()==null)
                throw new IllegalArgumentException("Enter a full name, valid email address and role.");
            if(!password.getText().equals(confirm.getText()))throw new IllegalArgumentException("The passwords do not match.");
            if(!PasswordUtil.isValidPassword(password.getText()))throw new IllegalArgumentException("The password must contain at least 8 characters.");
            User user=new User();user.setFullNames(name.getText().trim());user.setEmailAddress(email.getText().trim());user.setUserType(role.getValue());
            if(!userService.addUser(user,password.getText()))throw new IllegalArgumentException("The user could not be added. Check whether this email is already registered.");
            password.clear();confirm.clear();loadUsers();return true;
        }).showAndWait();
    }



    private void showUserDetailsDialog(User user) {
        var grid=com.pos.components.Ui.formGrid();
        TextField name=new TextField(user.getFullNames()),email=new TextField(user.getEmailAddress());
        ComboBox<String> role=new ComboBox<>();role.getItems().addAll("Admin","Manager","Cashier");role.setValue(user.getUserType());
        ComboBox<String> status=new ComboBox<>();status.getItems().addAll("Active","Inactive");status.setValue(user.getStatus());
        com.pos.components.Ui.formField(grid,0,"Full name",name);com.pos.components.Ui.formField(grid,1,"Email address",email);
        com.pos.components.Ui.formField(grid,2,"Role",role);com.pos.components.Ui.formField(grid,3,"Account status",status);
        Button reset=new Button("Reset password"),key=new Button("Authorisation key");reset.setStyle(com.pos.utils.Theme.secondaryButton());key.setStyle(com.pos.utils.Theme.secondaryButton());
        reset.setOnAction(e->resetPassword(user));key.setOnAction(e->showAuthKeyDialog(user));
        VBox content=new VBox(16,com.pos.components.Ui.section("Staff information",grid,com.pos.components.Ui.detailRow("Staff ID",""+user.getStaffID())),
            com.pos.components.Ui.section("Security actions",new Label("Password and supervisor keys are managed separately from profile changes."),new HBox(12,reset,key)));
        boolean allowed=currentUser.hasFullAccess();grid.setDisable(!allowed);reset.setDisable(!allowed);key.setDisable(!allowed);
        com.pos.components.Ui.editor("Staff member",user.getFullNames(),content,allowed?()->{
            if(name.getText().isBlank()||!email.getText().contains("@"))throw new IllegalArgumentException("Enter a name and valid email address.");
            User updated=new User(user.getStaffID(),name.getText().trim(),email.getText().trim(),role.getValue(),status.getValue());
            boolean saved=userService.updateUser(updated);if(saved){user.setFullNames(updated.getFullNames());user.setEmailAddress(updated.getEmailAddress());user.setUserType(updated.getUserType());user.setStatus(updated.getStatus());loadUsers();}return saved;
        }:null).showAndWait();
    }

    private void showAuthKeyDialog(User user) {
        com.pos.components.AuthorizationKeyDialog.show(currentUser,user,userService,sessionService);
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
        com.pos.components.Ui.showAlert(userTable, title, content, type);
    }
}
