package controller;

import dao.UserDAO;
import model.User;

import java.sql.SQLException;

public final class LoginController {
    private final UserDAO users;
    private User currentUser;
    public LoginController(UserDAO users) { this.users = users; }

    public boolean needsAdministratorSetup() throws SQLException { return !users.hasAdministrator(); }
    public void setupAdministrator(String username, String password) throws SQLException {
        if (!needsAdministratorSetup()) throw new SecurityException("Administrator setup has already been completed.");
        users.create(username, password, User.Role.ADMIN, null);
    }
    public User signIn(String username, String password) throws SQLException {
        User user = users.authenticate(username, password);
        if (user == null) throw new SecurityException("The username or password is incorrect, or the account is inactive.");
        currentUser = user;
        return user;
    }
    public User currentUser() {
        if (currentUser == null) throw new SecurityException("Sign in before using banking functions.");
        return currentUser;
    }
    public void signOut() { currentUser = null; }
}
