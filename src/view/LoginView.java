package view;

import controller.LoginController;
import model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.function.Consumer;

public final class LoginView {
    private final LoginController controller;
    private final Consumer<User> onSignedIn;
    private final VBox root = new VBox(14);
    private final TextField username = new TextField();
    private final PasswordField password = new PasswordField();
    private final Label message = new Label();
    private ToggleGroup accessGroup;
    private ToggleButton customerAccess;
    private ToggleButton staffAccess;

    public LoginView(LoginController controller, Consumer<User> onSignedIn) {
        this.controller = controller;
        this.onSignedIn = onSignedIn;
        build();
    }
    public Parent root() { return root; }

    private void build() {
        Label title=new Label("Banking Management System"); title.setStyle("-fx-font-size:28px;-fx-font-weight:bold;-fx-text-fill:#0c2943;");
        Label sub=new Label("Secure banking in Botswana · BWP"); sub.setStyle("-fx-text-fill:#64748b;-fx-font-size:14px;");
        username.setPromptText("Username"); password.setPromptText("Password");
        username.setId("username");password.setId("password");
        Button signIn=new Button("Sign In");signIn.setId("sign-in");signIn.setMaxWidth(Double.MAX_VALUE);
        signIn.setStyle("-fx-background-color:#07966f;-fx-text-fill:white;-fx-font-size:15px;-fx-font-weight:bold;-fx-background-radius:10;-fx-padding:12;");
        signIn.setOnAction(e->authenticate(false));password.setOnAction(e->{if(!signIn.isDisabled())authenticate(false);});
        message.setWrapText(true);message.setStyle("-fx-text-fill:#b42318;");message.setId("login-message");
        root.getChildren().addAll(title,sub,new Separator());
        try {
            boolean setupNeeded=controller.needsAdministratorSetup();
            if(!setupNeeded){
                accessGroup=new ToggleGroup();
                customerAccess=new ToggleButton("Customer");customerAccess.setId("access-customer");customerAccess.setToggleGroup(accessGroup);
                staffAccess=new ToggleButton("Teller / Admin");staffAccess.setId("access-staff");staffAccess.setToggleGroup(accessGroup);staffAccess.setSelected(true);
                styleAccess(customerAccess,false);styleAccess(staffAccess,true);
                customerAccess.setOnAction(e->{styleAccess(customerAccess,true);styleAccess(staffAccess,false);});
                staffAccess.setOnAction(e->{styleAccess(customerAccess,false);styleAccess(staffAccess,true);});
                HBox access=new HBox(6,customerAccess,staffAccess);access.setPadding(new Insets(5));
                HBox.setHgrow(customerAccess,Priority.ALWAYS);HBox.setHgrow(staffAccess,Priority.ALWAYS);
                access.setStyle("-fx-background-color:#edf3f8;-fx-background-radius:12;");root.getChildren().add(access);
            }
            root.getChildren().addAll(labelled("USERNAME",username),labelled("PASSWORD",password),signIn,message);
            if(setupNeeded){
                signIn.setDisable(true);
                Label firstRunMessage=new Label("First time setup: create the system administrator login.");
                firstRunMessage.setWrapText(true); firstRunMessage.setStyle("-fx-text-fill:#64748b;");
                Button setup=new Button("Create Administrator");setup.setId("create-admin");setup.setMaxWidth(Double.MAX_VALUE);
                setup.setStyle(signIn.getStyle());setup.setOnAction(e->authenticate(true));
                root.getChildren().add(root.getChildren().size()-1,new Separator());
                root.getChildren().add(root.getChildren().size()-1,firstRunMessage);
                root.getChildren().add(root.getChildren().size()-1,setup);
            }
        }catch(Exception ex){message.setText(ex.getMessage());}
        root.setAlignment(Pos.CENTER_LEFT);root.setPadding(new Insets(34));root.setMaxWidth(480);
        root.setStyle("-fx-background-color:white;-fx-background-radius:22;-fx-border-color:#e2e8f0;-fx-border-radius:22;");
    }

    private void authenticate(boolean setup) {
        try {
            if(setup) controller.setupAdministrator(username.getText(),password.getText());
            User user=controller.signIn(username.getText(),password.getText());
            if(!setup&&((accessGroup.getSelectedToggle()==customerAccess)!=(user.role()==User.Role.CUSTOMER))){
                controller.signOut();
                throw new SecurityException("Choose Customer or Teller / Admin to match this login.");
            }
            onSignedIn.accept(user);
        }catch(Exception ex){message.setText(ex.getMessage()==null?"Unable to sign in.":ex.getMessage());}
    }
    private static void styleAccess(ToggleButton button,boolean selected){
        button.setMaxWidth(Double.MAX_VALUE);button.setPrefHeight(42);
        button.setStyle(selected?"-fx-background-color:#07966f;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:9;"
                :"-fx-background-color:transparent;-fx-text-fill:#43556b;-fx-background-radius:9;");
    }
    private static VBox labelled(String label,Control field){Label l=new Label(label);l.setStyle("-fx-font-size:11px;-fx-font-weight:bold;-fx-text-fill:#43556b;");return new VBox(6,l,field);}
}
