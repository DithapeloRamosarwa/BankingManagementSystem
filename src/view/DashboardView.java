package view;

import controller.BankingController;
import controller.LoginController;
import model.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.Consumer;

public final class DashboardView {
    private final BankingController banking;
    private final LoginController login;
    private final User user;
    private final Consumer<User> onSignedOut;
    private final BorderPane root=new BorderPane();
    private final Label status=new Label(" ");
    private final Label pageTitle=new Label("Overview");
    private final VBox navigation=new VBox(8);
    private final StackPane pageContent=new StackPane();
    private final Map<String,Button> navigationButtons=new LinkedHashMap<>();
    private final Map<String,Supplier<Parent>> pageFactories=new LinkedHashMap<>();
    private final Map<String,Parent> pages=new LinkedHashMap<>();
    private TabPane dashboardTabs;
    private ComboBox<String> customerQuickOperation;
    private ComboBox<String> staffOperation;
    private ComboBox<String> transactionOperation;
    private ComboBox<String> workspaceOperation;

    public DashboardView(BankingController banking,LoginController login,User user,Consumer<User> onSignedOut){
        this.banking=banking;this.login=login;this.user=user;this.onSignedOut=onSignedOut;build();
    }
    public Parent root(){return root;}

    private void build(){
        Label identity=new Label(user.username()+"  ·  "+roleName(user.role()));identity.setStyle("-fx-text-fill:#52677d;-fx-font-weight:bold;");
        Button signOut=new Button("Sign out");signOut.setId("sign-out");signOut.setOnAction(e->{login.signOut();onSignedOut.accept(null);});
        Label initial=new Label(user.username().substring(0,1).toUpperCase());initial.setMinSize(34,34);initial.setAlignment(Pos.CENTER);
        initial.setStyle("-fx-background-color:#dbeafe;-fx-background-radius:18;-fx-text-fill:#075ca8;-fx-font-weight:bold;");
        Region spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
        HBox identityBox=new HBox(9,initial,new VBox(2,new Label(user.username()),new Label(roleName(user.role()))));identityBox.setAlignment(Pos.CENTER_LEFT);
        HBox header=new HBox(16,pageTitle,spacer,identityBox,signOut);header.setAlignment(Pos.CENTER_LEFT);header.setPadding(new Insets(14,24,14,24));
        header.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #dce5ee transparent;");root.setTop(header);
        status.setId("status");status.setPadding(new Insets(10,20,10,20));status.setStyle("-fx-text-fill:#41556b;");root.setBottom(status);
        dashboardTabs=new TabPane();dashboardTabs.setId("dashboard-tabs");
        dashboardTabs.getTabs().add(tab("Overview",new Pane()));
        dashboardTabs.getTabs().add(tab("Accounts & Transactions",new Pane()));
        dashboardTabs.getTabs().add(tab("Transactions",new Pane()));
        if(user.role()==User.Role.CUSTOMER)dashboardTabs.getTabs().add(tab("My Accounts",new Pane()));
        if(user.role()!=User.Role.CUSTOMER)dashboardTabs.getTabs().add(tab("Customers",new Pane()));
        if(user.role()==User.Role.ADMIN)dashboardTabs.getTabs().add(tab("User Access",new Pane()));
        if(user.role()==User.Role.ADMIN)dashboardTabs.getTabs().add(tab("Audit Log",new Pane()));
        pageFactories.put("Overview",this::overview);
        pageFactories.put("Accounts & Transactions",this::accountsAndTransactions);
        pageFactories.put("Transactions",this::transactionsPage);
        if(user.role()==User.Role.CUSTOMER)pageFactories.put("My Accounts",this::customerAccountsPage);
        if(user.role()!=User.Role.CUSTOMER)pageFactories.put("Customers",this::customerTools);
        if(user.role()==User.Role.ADMIN)pageFactories.put("User Access",this::userTools);
        if(user.role()==User.Role.ADMIN)pageFactories.put("Audit Log",this::auditLogPage);
        dashboardTabs.setVisible(false);dashboardTabs.setManaged(false);
        dashboardTabs.getSelectionModel().selectedItemProperty().addListener((o,oldValue,newValue)->{
            if(newValue!=null){showPage(newValue.getText());markNavigationForTab(newValue.getText());}
        });
        showPage("Overview");
        root.setLeft(buildSidebar());
        StackPane center=new StackPane(pageContent,dashboardTabs);root.setCenter(center);root.setStyle("-fx-background-color:#f3f7fb;");
    }

    private void showPage(String name){
        Parent page=pages.computeIfAbsent(name,key->{Supplier<Parent> factory=pageFactories.get(key);return factory==null?new Pane():factory.get();});
        pageContent.getChildren().setAll(page);pageTitle.setText(titleFor(name));
    }

    private VBox buildSidebar(){
        Label mark=new Label("▥");mark.setMinSize(42,42);mark.setAlignment(Pos.CENTER);mark.setStyle("-fx-background-color:#1686ec;-fx-background-radius:12;-fx-text-fill:white;-fx-font-size:22px;-fx-font-weight:bold;");
        Label brand=new Label("Banking\nManagement");brand.setStyle("-fx-text-fill:white;-fx-font-size:16px;-fx-font-weight:bold;");
        HBox logo=new HBox(10,mark,brand);logo.setAlignment(Pos.CENTER_LEFT);logo.setPadding(new Insets(20,16,24,16));
        navigation.setPadding(new Insets(4,10,18,10));
        List<String> items=switch(user.role()){
            case CUSTOMER->List.of("Overview","My Accounts","Open Account","Deposit","Withdraw");
            case TELLER->List.of("Overview","Customers","Open Account","Transactions");
            case ADMIN->List.of("Overview","Manage Users","Accounts","Customers","Audit Log");
        };
        items.forEach(item->{Button button=new Button(item);button.setId("nav-"+item.toLowerCase().replace(' ','-'));button.setAlignment(Pos.CENTER_LEFT);button.setMaxWidth(Double.MAX_VALUE);button.setPrefHeight(40);
            navigationButtons.put(item,button);button.setStyle(item.equals("Overview")?navSelectedStyle():navStyle());button.setOnAction(e->navigate(item));navigation.getChildren().add(button);});
        Region fill=new Region();VBox.setVgrow(fill,Priority.ALWAYS);
        Label footer=new Label("Secure banking workspace");footer.setStyle("-fx-text-fill:#9bb4cb;-fx-font-size:11px;");
        VBox side=new VBox(logo,navigation,fill,footer);side.setPadding(new Insets(0,0,16,0));side.setPrefWidth(205);side.setMinWidth(185);
        side.setStyle("-fx-background-color:#0b2e50;");return side;
    }

    private void navigate(String item){
        String tabName=switch(item){
            case "Customers"->"Customers";case "Manage Users"->"User Access";case "Overview"->"Overview";
            case "Transactions","Deposit","Withdraw"->"Transactions";case "Audit Log"->"Audit Log";
            case "My Accounts"->"My Accounts";
            default->"Accounts & Transactions";
        };
        dashboardTabs.getTabs().stream().filter(t->t.getText().equals(tabName)).findFirst().ifPresent(dashboardTabs.getSelectionModel()::select);
        markNavigation(item);
        if(item.equals("Deposit")){if(customerQuickOperation!=null)customerQuickOperation.setValue("DEPOSIT");if(transactionOperation!=null)transactionOperation.setValue("DEPOSIT");if(workspaceOperation!=null)workspaceOperation.setValue("DEPOSIT");}
        if(item.equals("Withdraw")){if(customerQuickOperation!=null)customerQuickOperation.setValue("WITHDRAWAL");if(transactionOperation!=null)transactionOperation.setValue("WITHDRAWAL");if(workspaceOperation!=null)workspaceOperation.setValue("WITHDRAWAL");}
        if(item.equals("Transactions")){if(staffOperation!=null)staffOperation.setValue("DEPOSIT");if(transactionOperation!=null)transactionOperation.setValue("DEPOSIT");if(workspaceOperation!=null)workspaceOperation.setValue("DEPOSIT");}
    }

    private void markNavigationForTab(String tab){
        String item=switch(tab){case "Accounts & Transactions"->user.role()==User.Role.ADMIN?"Accounts":"Open Account";case "User Access"->"Manage Users";default->tab;};
        markNavigation(item);
    }
    private void markNavigation(String item){
        navigationButtons.forEach((name,button)->button.setStyle(name.equals(item)?navSelectedStyle():navStyle()));
    }

    private static String titleFor(String tab){return switch(tab){case "Accounts & Transactions"->"Open account";case "User Access"->"User access management";default->tab;};}
    private static String roleName(User.Role role){return role==User.Role.CUSTOMER?"Customer":role==User.Role.TELLER?"Teller":"Administrator";}
    private static String navStyle(){return "-fx-background-color:transparent;-fx-text-fill:#d9e7f4;-fx-background-radius:9;-fx-padding:10 12 10 14;-fx-font-size:13px;";}
    private static String navSelectedStyle(){return "-fx-background-color:#1479d2;-fx-text-fill:white;-fx-background-radius:9;-fx-padding:10 12 10 14;-fx-font-size:13px;-fx-font-weight:bold;";}

    private Parent overview(){
        return switch(user.role()){
            case CUSTOMER->customerOverview();
            case TELLER->tellerOverview();
            case ADMIN->adminOverview();
        };
    }

    private Parent customerOverview(){
        VBox page=new VBox(17);page.setPadding(new Insets(24));
        try{
            List<Account> accounts=banking.visibleAccounts();
            Label total=title("BWP "+money(accounts.stream().map(Account::balance).reduce(BigDecimal.ZERO,BigDecimal::add)));
            total.setStyle("-fx-text-fill:white;-fx-font-size:32px;-fx-font-weight:bold;");
            Label totalCaption=new Label("Total balance across "+accounts.size()+" account(s)");totalCaption.setStyle("-fx-text-fill:#d6e7f3;-fx-font-size:14px;");
            Button openAccount=new Button("+ Open Account");openAccount.setId("customer-open-account");
            openAccount.setOnAction(e->dashboardTabs.getTabs().stream().filter(t->t.getText().equals("Accounts & Transactions")).findFirst().ifPresent(dashboardTabs.getSelectionModel()::select));
            HBox banner=new HBox(16,new VBox(6,new Label("TOTAL BALANCE"),total,totalCaption),new Region(),openAccount);
            banner.setAlignment(Pos.CENTER_LEFT);HBox.setHgrow(banner.getChildren().get(1),Priority.ALWAYS);banner.setPadding(new Insets(20));
            banner.setStyle("-fx-background-color:linear-gradient(to right,#10213c,#0b5260);-fx-background-radius:16;");
            ((Label)((VBox)banner.getChildren().getFirst()).getChildren().getFirst()).setStyle("-fx-text-fill:#76e3c1;-fx-font-weight:bold;");
            FlowPane cards=new FlowPane(14,14);cards.setId("customer-account-cards");
            accounts.forEach(a->cards.getChildren().add(accountCard(a)));
            if(accounts.isEmpty())cards.getChildren().add(new Label("No accounts are linked to your profile yet."));

            ComboBox<Account> selected=new ComboBox<>(FXCollections.observableArrayList(accounts));selected.setId("customer-quick-account");selected.setPromptText("Select account");
            if(!accounts.isEmpty())selected.setValue(accounts.getFirst());
            ComboBox<String> operation=new ComboBox<>();customerQuickOperation=operation;operation.setId("customer-quick-operation");operation.getItems().setAll("DEPOSIT","WITHDRAWAL");operation.setValue("DEPOSIT");
            TextField amount=new TextField();amount.setId("customer-quick-amount");amount.setPromptText("Amount (BWP)");
            TextField note=new TextField();note.setId("customer-quick-note");note.setPromptText("Description / reference");
            Button transact=new Button("Confirm Transaction");transact.setId("customer-quick-transaction");
            TableView<Transaction> history=transactionTable();history.setPrefHeight(230);
            selected.valueProperty().addListener((o,oldValue,newValue)->refreshHistory(newValue,history));
            if(selected.getValue()!=null)refreshHistory(selected.getValue(),history);
            transact.setOnAction(e->execute(()->{
                String number=selected.getValue().accountNumber();
                banking.transact(number,operation.getValue(),new BigDecimal(amount.getText()),note.getText());
                List<Account> refreshed=banking.visibleAccounts();
                total.setText("BWP "+money(refreshed.stream().map(Account::balance).reduce(BigDecimal.ZERO,BigDecimal::add)));
                cards.getChildren().setAll(refreshed.stream().map(DashboardView::accountCard).toList());
                selected.getItems().setAll(refreshed);selected.getItems().stream().filter(a->a.accountNumber().equals(number)).findFirst().ifPresent(selected::setValue);
                refreshHistory(selected.getValue(),history);amount.clear();note.clear();status.setText("Transaction completed successfully.");
            }));
            VBox quick=new VBox(12,title("Quick transaction"),new HBox(10,selected,operation),new HBox(10,amount,note,transact));
            transact.disableProperty().bind(selected.valueProperty().isNull());
            quick.setPadding(new Insets(18));
            quick.setStyle("-fx-background-color:white;-fx-background-radius:15;-fx-border-color:#dce5ee;-fx-border-radius:15;");
            Button deposit=new Button("↓  Deposit funds");deposit.setId("customer-deposit-action");stylePrimary(deposit);deposit.setOnAction(e->operation.setValue("DEPOSIT"));
            Button withdraw=new Button("↑  Withdraw funds");withdraw.setId("customer-withdraw-action");withdraw.setOnAction(e->operation.setValue("WITHDRAWAL"));
            Button statement=new Button("View statement");statement.setId("customer-statement-action");statement.setOnAction(e->{if(selected.getValue()!=null)refreshHistory(selected.getValue(),history);});
            HBox actions=new HBox(10,deposit,withdraw,statement);
            page.getChildren().addAll(new VBox(4,title("Customer Dashboard"),new Label("Welcome, "+user.username()+". Here is your account summary and recent activity.")),banner,title("My Accounts"),cards,actions,quick,title("Recent Transactions"),history);
        }catch(Exception ex){page.getChildren().add(error(ex));}
        ScrollPane scroll=new ScrollPane(page);scroll.setFitToWidth(true);return scroll;
    }

    private Parent tellerOverview(){
        VBox page=new VBox(14);page.setPadding(new Insets(22));
        try{
            List<Customer> allCustomers=banking.searchCustomers("");List<Account> allAccounts=banking.visibleAccounts();
            BigDecimal total=allAccounts.stream().map(Account::balance).reduce(BigDecimal.ZERO,BigDecimal::add);
            HBox heading=new HBox(12,title("Teller Dashboard"),pill("Central Branch · Teller"));heading.setAlignment(Pos.CENTER_LEFT);
            page.getChildren().addAll(heading,new Label("Find a customer, review accounts, and process counter transactions."),
                    new FlowPane(12,12,metric("Customers",String.valueOf(allCustomers.size())),metric("Accounts",String.valueOf(allAccounts.size())),metric("Total balances","BWP "+money(total))));

            TextField search=new TextField();search.setId("teller-customer-search");search.setPromptText("Search customer name, ID, or address");Button find=new Button("Search");find.setId("teller-customer-find");stylePrimary(find);
            TableView<Customer> people=customerTable();people.setId("teller-customer-table");people.setPrefHeight(128);people.getItems().setAll(allCustomers);
            Button register=new Button("Register customer");register.setId("teller-register-customer");register.setOnAction(e->navigate("Customers"));
            Button open=new Button("+ Open account");open.setId("teller-open-account");stylePrimary(open);open.setOnAction(e->navigate("Open Account"));
            find.setOnAction(e->execute(()->people.getItems().setAll(banking.searchCustomers(search.getText()))));search.setOnAction(e->find.fire());
            VBox customerPane=new VBox(9,new HBox(9,search,find),people);HBox.setHgrow(search,Priority.ALWAYS);

            TableView<Account> customerAccounts=accountTable(List.of());customerAccounts.setId("teller-account-table");customerAccounts.setPrefHeight(130);
            ComboBox<Account> selected=new ComboBox<>(FXCollections.observableArrayList());selected.setId("teller-selected-account");selected.setPromptText("Select account");
            staffOperation=new ComboBox<>();staffOperation.setId("teller-operation");staffOperation.getItems().setAll("DEPOSIT","WITHDRAWAL");staffOperation.setValue("DEPOSIT");
            TextField amount=new TextField();amount.setId("teller-amount");amount.setPromptText("Amount (BWP)");TextField note=new TextField();note.setId("teller-note");note.setPromptText("Reference / narration");
            Button process=new Button("Process transaction");process.setId("teller-process-transaction");stylePrimary(process);
            TableView<Transaction> history=transactionTable();history.setId("teller-transaction-history");history.setPrefHeight(150);
            Runnable showCustomer=()->{
                Customer selectedCustomer=people.getSelectionModel().getSelectedItem();
                if(selectedCustomer==null){customerAccounts.getItems().clear();selected.getItems().clear();history.getItems().clear();return;}
                List<Account> owned=allAccounts.stream().filter(a->a.customerId()==selectedCustomer.id()).toList();
                customerAccounts.getItems().setAll(owned);selected.getItems().setAll(owned);
                if(!owned.isEmpty()){selected.setValue(owned.getFirst());refreshHistory(selected.getValue(),history);}else history.getItems().clear();
            };
            people.getSelectionModel().selectedItemProperty().addListener((o,a,b)->showCustomer.run());
            selected.valueProperty().addListener((o,a,b)->refreshHistory(b,history));
            if(!allCustomers.isEmpty()){people.getSelectionModel().selectFirst();showCustomer.run();}
            process.disableProperty().bind(selected.valueProperty().isNull());
            process.setOnAction(e->execute(()->{String number=selected.getValue().accountNumber();banking.transact(number,staffOperation.getValue(),new BigDecimal(amount.getText()),note.getText());
                List<Account> refreshed=banking.visibleAccounts();customerAccounts.getItems().setAll(refreshed.stream().filter(a->a.customerId()==selected.getValue().customerId()).toList());
                selected.getItems().setAll(customerAccounts.getItems());selected.getItems().stream().filter(a->a.accountNumber().equals(number)).findFirst().ifPresent(selected::setValue);
                refreshHistory(selected.getValue(),history);amount.clear();note.clear();status.setText("Transaction completed for account "+number+".");}));

            VBox accountsPane=panel(new HBox(10,title("Customer accounts"),register,open),customerAccounts);
            VBox transactionPane=panel(new Label("Process transaction"),new HBox(8,selected,staffOperation,amount),note,process,title("Recent account activity"),history);
            HBox.setHgrow(customerPane,Priority.ALWAYS);customerPane.setPrefWidth(460);
            page.getChildren().addAll(panel(title("Customer lookup"),customerPane),accountsPane,transactionPane);
        }catch(Exception ex){page.getChildren().add(error(ex));}
        ScrollPane scroll=new ScrollPane(page);scroll.setFitToWidth(true);return scroll;
    }

    private Parent adminOverview(){
        VBox page=new VBox(16);page.setPadding(new Insets(22));
        try{
            List<User> users=banking.listUsers();List<Account> accounts=banking.visibleAccounts();
            long active=users.stream().filter(User::active).count();long disabled=users.size()-active;
            HBox heading=new HBox(12,title("Admin Dashboard"),pill("System Administration"));heading.setAlignment(Pos.CENTER_LEFT);
            FlowPane metrics=new FlowPane(12,12,metric("Total users",String.valueOf(users.size())),metric("Active users",String.valueOf(active)),metric("Disabled users",String.valueOf(disabled)));
            TableView<User> access=userTable(users);access.setId("admin-user-table");access.setPrefHeight(185);
            Button manage=new Button("Manage users");manage.setId("admin-manage-users");stylePrimary(manage);manage.setOnAction(e->navigate("Manage Users"));
            VBox accessPane=panel(new HBox(10,title("User access management"),manage),access);
            Button interest=new Button("Run monthly interest");interest.setId("run-interest");stylePrimary(interest);
            interest.setOnAction(e->execute(()->{int count=banking.runMonthlyInterest(YearMonth.now(java.time.ZoneId.of("Africa/Gaborone")).minusMonths(1));status.setText("Interest credited to "+count+" account(s).");}));
            HBox system=new HBox(12,statusCard("Database","Connected"),statusCard("Scheduled interest","Enabled"),interest);system.setAlignment(Pos.CENTER_LEFT);
            List<Transaction> recent=accounts.stream().flatMap(a->{try{return banking.history(a.accountNumber()).stream();}catch(Exception ex){return java.util.stream.Stream.<Transaction>empty();}})
                    .sorted(java.util.Comparator.comparing(Transaction::occurredAt).reversed()).limit(8).toList();
            TableView<Transaction> activity=transactionTable();activity.setId("admin-recent-activity");activity.setPrefHeight(230);activity.getItems().setAll(recent);
            page.getChildren().addAll(heading,new Label("Manage users and review banking activity."),metrics,accessPane,
                    panel(new Label("System status"),system),panel(title("Recent activity"),activity));
        }catch(Exception ex){page.getChildren().add(error(ex));}
        ScrollPane scroll=new ScrollPane(page);scroll.setFitToWidth(true);return scroll;
    }

    private Parent accountsAndTransactions(){
        VBox page=new VBox(16);page.setPadding(new Insets(24));
        try{
            ComboBox<Customer> owner=new ComboBox<>();owner.setPromptText(user.role()==User.Role.CUSTOMER?"Your customer profile":"Account owner");
            owner.setId("account-owner");
            List<Customer> customers=banking.visibleCustomers();owner.getItems().setAll(customers);
            if(user.role()==User.Role.CUSTOMER&&!customers.isEmpty())owner.setValue(customers.getFirst());
            ComboBox<String> type=new ComboBox<>();type.setId("account-type");type.getItems().setAll("SAVINGS","INVESTMENT","CHEQUE");type.setPromptText("Account type");
            TextField branch=new TextField("Gaborone Main");branch.setId("account-branch");branch.setPromptText("Branch");TextField opening=new TextField("0.00");opening.setId("opening-amount");opening.setPromptText("Opening deposit");
            TextField employer=new TextField();employer.setId("employer-name");employer.setPromptText("Employer name (Cheque)");TextField employerAddress=new TextField();employerAddress.setId("employer-address");employerAddress.setPromptText("Employer address (Cheque)");
            Button open=new Button("Open Account");open.setId("open-account");open.disableProperty().bind(type.valueProperty().isNull().or(owner.valueProperty().isNull()));
            open.setOnAction(e->execute(()->{String number=banking.openAccount(owner.getValue().id(),Account.Type.valueOf(type.getValue()),branch.getText(),new BigDecimal(opening.getText()),employer.getText(),employerAddress.getText());status.setText("Account opened: "+number);}));
            page.getChildren().addAll(title(user.role()==User.Role.CUSTOMER?"Open an Account":"Open an Account for a Customer"),
                    new Label("Choose the customer and account product, then enter the opening details."),
                    panel(new Label("Customer and account product"),new HBox(10,owner,type)),
                    panel(new Label("Opening details"),new HBox(10,branch,opening),new HBox(10,employer,employerAddress),open));
        }catch(Exception ex){page.getChildren().add(error(ex));}
        ScrollPane scroll=new ScrollPane(page);scroll.setFitToWidth(true);return scroll;
    }

    private Parent customerAccountsPage(){
        VBox page=new VBox(16);page.setPadding(new Insets(24));
        try{
            List<Account> accounts=banking.visibleAccounts();
            FlowPane cards=new FlowPane(14,14);cards.setId("customer-account-cards");
            accounts.forEach(account->cards.getChildren().add(accountCard(account)));
            if(accounts.isEmpty())cards.getChildren().add(new Label("No accounts are linked to your profile yet."));
            ComboBox<Account> selected=new ComboBox<>(FXCollections.observableArrayList(accounts));selected.setId("my-accounts-statement-account");
            if(!accounts.isEmpty())selected.setValue(accounts.getFirst());
            TableView<Transaction> history=transactionTable();history.setPrefHeight(330);
            selected.valueProperty().addListener((o,a,b)->refreshHistory(b,history));if(selected.getValue()!=null)refreshHistory(selected.getValue(),history);
            Button open=new Button("+ Open Account");open.setId("customer-open-account-from-list");stylePrimary(open);
            open.setOnAction(e->navigate("Open Account"));
            page.getChildren().addAll(title("My Accounts"),new Label("Account balances and product details."),cards,
                    panel(new HBox(10,new Label("Selected account statement"),open),selected,history));
        }catch(Exception ex){page.getChildren().add(error(ex));}
        ScrollPane scroll=new ScrollPane(page);scroll.setFitToWidth(true);return scroll;
    }

    private Parent transactionsPage(){
        VBox page=new VBox(16);page.setPadding(new Insets(24));
        try{
            List<Account> accounts=banking.visibleAccounts();
            ComboBox<Account> selected=new ComboBox<>(FXCollections.observableArrayList(accounts));selected.setId("transaction-workspace-account");selected.setPromptText("Choose an account");
            if(!accounts.isEmpty())selected.setValue(accounts.getFirst());
            workspaceOperation=new ComboBox<>();workspaceOperation.setId("transaction-workspace-operation");workspaceOperation.getItems().setAll("DEPOSIT","WITHDRAWAL");workspaceOperation.setValue("DEPOSIT");
            TextField amount=new TextField();amount.setId("transaction-workspace-amount");amount.setPromptText("Amount (BWP)");
            TextField note=new TextField();note.setId("transaction-workspace-note");note.setPromptText("Narration / reference");
            Button execute=new Button("Execute Transaction");execute.setId("transaction-workspace-execute");stylePrimary(execute);
            TableView<Transaction> history=transactionTable();history.setPrefHeight(360);
            selected.valueProperty().addListener((o,a,b)->refreshHistory(b,history));if(selected.getValue()!=null)refreshHistory(selected.getValue(),history);
            execute.disableProperty().bind(selected.valueProperty().isNull());
            execute.setOnAction(e->execute(()->{
                String number=selected.getValue().accountNumber();
                banking.transact(number,workspaceOperation.getValue(),new BigDecimal(amount.getText()),note.getText());
                List<Account> updated=banking.visibleAccounts();selected.getItems().setAll(updated);
                updated.stream().filter(a->a.accountNumber().equals(number)).findFirst().ifPresent(selected::setValue);
                refreshHistory(selected.getValue(),history);amount.clear();note.clear();status.setText("Transaction completed successfully.");
            }));
            page.getChildren().addAll(title("Transactions"),new Label("Choose an account and process a deposit or withdrawal."),
                    panel(new Label("Account and operation"),new HBox(10,selected,workspaceOperation),new HBox(10,amount,note,execute)),
                    panel(title("Transaction history"),history));
        }catch(Exception ex){page.getChildren().add(error(ex));}
        ScrollPane scroll=new ScrollPane(page);scroll.setFitToWidth(true);return scroll;
    }

    private Parent auditLogPage(){
        VBox page=new VBox(16);page.setPadding(new Insets(24));
        try{
            List<Account> accounts=banking.visibleAccounts();
            List<Transaction> events=accounts.stream().flatMap(account->{try{return banking.history(account.accountNumber()).stream();}catch(Exception ex){return java.util.stream.Stream.<Transaction>empty();}})
                    .sorted(java.util.Comparator.comparing(Transaction::occurredAt).reversed()).limit(200).toList();
            TableView<Transaction> audit=transactionTable();audit.setId("admin-audit-log");audit.getColumns().add(0,column("Account",Transaction::accountNumber));audit.setPrefHeight(470);audit.getItems().setAll(events);
            page.getChildren().addAll(title("Audit Log"),new Label("Recent account openings, deposits, withdrawals, and interest entries."),panel(audit));
        }catch(Exception ex){page.getChildren().add(error(ex));}
        ScrollPane scroll=new ScrollPane(page);scroll.setFitToWidth(true);return scroll;
    }

    private Parent customerTools(){
        VBox page=new VBox(16);page.setPadding(new Insets(24));page.getChildren().add(title("Customer Registration and Lookup"));
        TextField first=new TextField();first.setId("first-name");first.setPromptText("First name");TextField last=new TextField();last.setId("last-name");last.setPromptText("Surname");
        TextField address=new TextField();address.setId("customer-address");address.setPromptText("Residential / postal address");TextField phone=new TextField();phone.setId("customer-phone");phone.setPromptText("Phone number");CheckBox employed=new CheckBox("Employed");employed.setId("customer-employed");
        Button create=new Button("Register Customer");create.setId("register-customer");
        create.setOnAction(e->execute(()->{long id=banking.registerCustomer(first.getText(),last.getText(),address.getText(),phone.getText(),employed.isSelected());status.setText("Customer registered. ID "+id);first.clear();last.clear();address.clear();phone.clear();refreshCustomers(page);}));
        TextField search=new TextField();search.setPromptText("Search by customer, address, or ID");Button find=new Button("Search");
        TableView<Customer> table=customerTable();find.setOnAction(e->execute(()->table.getItems().setAll(banking.searchCustomers(search.getText()))));search.setOnAction(e->find.fire());
        page.getChildren().addAll(new HBox(10,first,last),new HBox(10,address,phone,employed),create,new Separator(),new HBox(10,search,find),table);
        try{table.getItems().setAll(banking.searchCustomers(""));}catch(Exception ex){page.getChildren().add(error(ex));}
        return new ScrollPane(page);
    }

    private Parent userTools(){
        VBox page=new VBox(16);page.setPadding(new Insets(24));page.getChildren().addAll(title("Create Staff and Customer Logins"));
        TextField name=new TextField();name.setId("new-username");name.setPromptText("Username");PasswordField password=new PasswordField();password.setId("new-password");password.setPromptText("Password (8+ characters)");
        ComboBox<User.Role> role=new ComboBox<>();role.setId("new-role");role.getItems().setAll(User.Role.TELLER,User.Role.CUSTOMER);role.setValue(User.Role.TELLER);
        ComboBox<Customer> customer=new ComboBox<>();customer.setId("new-customer");customer.setPromptText("Customer (for customer login)");
        try{customer.getItems().setAll(banking.searchCustomers(""));}catch(Exception ex){page.getChildren().add(error(ex));}
        customer.disableProperty().bind(role.valueProperty().isNotEqualTo(User.Role.CUSTOMER));
        Button create=new Button("Create Login");create.setId("create-login");
        create.setOnAction(e->execute(()->{Long id=role.getValue()==User.Role.CUSTOMER?(customer.getValue()==null?null:customer.getValue().id()):null;banking.createLogin(name.getText(),password.getText(),role.getValue(),id);status.setText("Login created for "+name.getText().strip()+".");name.clear();password.clear();}));
        page.getChildren().addAll(new HBox(10,name,password),new HBox(10,role,customer),create,new Label("Passwords are stored as salted PBKDF2 hashes."));
        TableView<User> users=new TableView<>(FXCollections.observableArrayList());users.setId("user-table");users.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        users.getColumns().add(column("Username",User::username));users.getColumns().add(column("Role",u->u.role().name()));
        users.getColumns().add(column("Status",u->u.active()?"Active":"Inactive"));
        Button changeAccess=new Button("Activate / Deactivate Selected Login");changeAccess.setId("toggle-user-access");
        changeAccess.setOnAction(e->{User selected=users.getSelectionModel().getSelectedItem();if(selected==null){status.setText("Select a login first.");return;}
            execute(()->{banking.setUserActive(selected.id(),!selected.active());users.getItems().setAll(banking.listUsers());status.setText("Login access updated.");});});
        execute(()->users.getItems().setAll(banking.listUsers()));
        page.getChildren().addAll(title("User Access"),users,changeAccess);
        return page;
    }

    private TableView<Account> accountTable(List<Account> rows){
        TableView<Account> table=new TableView<>(FXCollections.observableArrayList(rows));table.setId("account-table");table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getColumns().add(column("Account",a->a.accountNumber()));table.getColumns().add(column("Type",a->a.type().name()));
        table.getColumns().add(column("Branch",Account::branch));table.getColumns().add(column("Balance (BWP)",a->money(a.balance())));return table;
    }
    private TableView<User> userTable(List<User> rows){
        TableView<User> table=new TableView<>(FXCollections.observableArrayList(rows));table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getColumns().add(column("User",User::username));table.getColumns().add(column("Role",u->roleName(u.role())));
        table.getColumns().add(column("Status",u->u.active()?"● Active":"● Disabled"));
        table.getColumns().add(column("Action",u->"Manage"));return table;
    }
    private static VBox panel(javafx.scene.Node... children){VBox box=new VBox(10,children);box.setPadding(new Insets(16));box.setStyle("-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#dce5ee;-fx-border-radius:14;");return box;}
    private static Label pill(String text){Label label=new Label(text);label.setPadding(new Insets(6,12,6,12));label.setStyle("-fx-background-color:#e6f2ff;-fx-background-radius:14;-fx-text-fill:#075ca8;-fx-font-weight:bold;");return label;}
    private static VBox statusCard(String label,String value){Label heading=new Label(label);heading.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");Label status=new Label("●  "+value);status.setStyle("-fx-text-fill:#07845d;-fx-font-weight:bold;");VBox card=new VBox(7,heading,status);card.setPadding(new Insets(12,16,12,16));card.setMinWidth(185);card.setStyle("-fx-background-color:white;-fx-background-radius:12;-fx-border-color:#dce5ee;-fx-border-radius:12;");return card;}
    private static void stylePrimary(Button button){button.setStyle("-fx-background-color:#1479d2;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-padding:9 15 9 15;");}
    private static VBox accountCard(Account account){
        String name=switch(account.type()){case SAVINGS->"Savings Account";case INVESTMENT->"Investment Account";case CHEQUE->"Cheque Account";};
        String detail=switch(account.type()){
            case SAVINGS->"0.05% monthly interest · Deposits only";
            case INVESTMENT->"5% monthly interest · Withdrawals allowed";
            case CHEQUE->"Salary account · Withdrawals allowed";
        };
        Label heading=new Label(name);heading.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#10213c;");
        Label number=new Label(account.accountNumber()+" · "+account.branch());number.setStyle("-fx-text-fill:#718096;");
        Label balance=new Label("BWP "+money(account.balance()));balance.setStyle("-fx-font-size:21px;-fx-font-weight:bold;-fx-text-fill:#10213c;");
        Label terms=new Label(detail);terms.setWrapText(true);terms.setStyle("-fx-text-fill:#536780;");
        VBox card=new VBox(9,heading,number,balance,terms);card.setPadding(new Insets(16));card.setPrefWidth(265);card.setMinHeight(145);
        card.setStyle("-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#dce5ee;-fx-border-radius:14;");return card;
    }
    private TableView<Customer> customerTable(){TableView<Customer> t=new TableView<>();t.setId("customer-table");t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        t.getColumns().add(column("Customer",Customer::fullName));t.getColumns().add(column("Address",Customer::address));t.getColumns().add(column("Phone",Customer::phone));t.getColumns().add(column("Employment",c->c.employed()?"Employed":"Not supplied"));return t;}
    private TableView<Transaction> transactionTable(){TableView<Transaction> t=new TableView<>();t.setId("transaction-history");t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        t.getColumns().add(column("Date / Time",x->DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(java.time.ZoneId.systemDefault()).format(x.occurredAt())));
        t.getColumns().add(column("Type",x->x.type().name()));t.getColumns().add(column("Amount",x->"BWP "+money(x.amount())));t.getColumns().add(column("Balance after",x->"BWP "+money(x.balanceAfter())));t.getColumns().add(column("Note",Transaction::note));return t;}
    private static <T> TableColumn<T,String> column(String name,java.util.function.Function<T,String> get){TableColumn<T,String> c=new TableColumn<>(name);c.setCellValueFactory(data->new ReadOnlyStringWrapper(get.apply(data.getValue())));return c;}
    private void refreshHistory(Account a,TableView<Transaction> table){if(a==null){table.getItems().clear();return;}execute(()->table.getItems().setAll(banking.history(a.accountNumber())));}
    @SuppressWarnings("unchecked") private void refreshCustomers(VBox target){execute(()->{for(javafx.scene.Node n:target.getChildren())if(n instanceof TableView<?> t&&"customer-table".equals(t.getId()))((TableView<Customer>)t).getItems().setAll(banking.searchCustomers(""));});}
    private void execute(Action action){try{action.run();}catch(Exception ex){status.setText(ex.getMessage()==null?"The request could not be completed.":ex.getMessage());}}
    private static Tab tab(String text,Parent content){Tab t=new Tab(text,content);t.setClosable(false);return t;}
    private static VBox metric(String label,String value){Label a=new Label(label);a.setStyle("-fx-text-fill:#64748b;");Label b=new Label(value);b.setStyle("-fx-font-size:24px;-fx-font-weight:bold;-fx-text-fill:#10213c;");VBox box=new VBox(8,a,b);box.setPadding(new Insets(18));box.setMinWidth(210);box.setStyle("-fx-background-color:white;-fx-background-radius:14;-fx-border-color:#dce5ee;-fx-border-radius:14;");return box;}
    private static Label title(String text){Label l=new Label(text);l.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#10213c;");return l;}
    private static Label error(Exception ex){Label l=new Label(ex.getMessage());l.setWrapText(true);l.setStyle("-fx-text-fill:#b42318;");return l;}
    private static String money(BigDecimal value){return value.setScale(2,java.math.RoundingMode.HALF_UP).toPlainString();}
    @FunctionalInterface private interface Action{void run()throws Exception;}
}
