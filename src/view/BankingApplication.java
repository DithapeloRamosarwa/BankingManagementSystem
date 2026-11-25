package view;

import controller.BankingController;
import controller.LoginController;
import dao.AccountDAO;
import dao.CustomerDAO;
import dao.Database;
import dao.UserDAO;
import model.User;
import service.DemoDataSeeder;
import service.InterestScheduler;
import service.MonthlyInterestService;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;

public final class BankingApplication extends Application {
    private LoginController login;
    private BankingController banking;
    private InterestScheduler scheduler;

    @Override public void start(Stage stage) throws Exception {
        Path databaseFile=Path.of(System.getProperty("bank.database","data/banking.db"));
        Database database=new Database(databaseFile);
        CustomerDAO customers=new CustomerDAO(database);
        AccountDAO accounts=new AccountDAO(database);
        UserDAO users=new UserDAO(database);
        DemoDataSeeder.seed(customers,users,accounts);
        login=new LoginController(users);
        MonthlyInterestService interest=new MonthlyInterestService(accounts);
        banking=new BankingController(login,users,customers,accounts,interest);
        scheduler=new InterestScheduler(interest);scheduler.start();
        showLogin(stage);
    }

    private void showLogin(Stage stage){
        LoginView view=new LoginView(login,user->showDashboard(stage,user));
        stage.setTitle("Banking Management System");
        stage.setMinWidth(850);stage.setMinHeight(620);
        stage.setScene(new Scene(new javafx.scene.layout.StackPane(view.root()),1050,720));stage.show();
    }

    private void showDashboard(Stage stage,User user){
        DashboardView view=new DashboardView(banking,login,user,ignored->showLogin(stage));
        stage.setTitle("Banking Management System · "+user.role());
        stage.setScene(new Scene(view.root(),1120,760));stage.show();
    }

    @Override public void stop(){if(login!=null)login.signOut();if(scheduler!=null)scheduler.close();}
}
