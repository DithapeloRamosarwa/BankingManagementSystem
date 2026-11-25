package dao;

import model.User;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Base64;

public final class UserDAO {
    private static final int ITERATIONS = 210_000;
    private final Database database;
    private final SecureRandom random = new SecureRandom();

    public UserDAO(Database database) { this.database = database; }

    public boolean hasAdministrator() throws SQLException {
        try (var c = database.connect(); var q = c.createStatement();
             var r = q.executeQuery("SELECT 1 FROM users WHERE role='ADMIN' LIMIT 1")) { return r.next(); }
    }

    public long create(String username, String password, User.Role role, Long customerId) throws SQLException {
        String name = username == null ? "" : username.strip();
        if (name.isBlank() || name.length() > 60) throw new IllegalArgumentException("Enter a username of 1 to 60 characters.");
        if (password == null || password.length() < 8) throw new IllegalArgumentException("Password must have at least 8 characters.");
        if ((role == User.Role.CUSTOMER) != (customerId != null)) throw new IllegalArgumentException("Customer logins must be linked to one customer.");
        try (var c = database.connect(); var p = c.prepareStatement(
                "INSERT INTO users(username,password_hash,role,customer_id) VALUES(?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, name);
            p.setString(2, hash(password));
            p.setString(3, role.name());
            if (customerId == null) p.setNull(4, java.sql.Types.INTEGER); else p.setLong(4, customerId);
            p.executeUpdate();
            try (var keys = p.getGeneratedKeys()) { if (keys.next()) return keys.getLong(1); }
        } catch (SQLException ex) {
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unique"))
                throw new IllegalArgumentException("That username is already in use.");
            throw ex;
        }
        throw new SQLException("Could not create the login.");
    }

    public User authenticate(String username, String password) throws SQLException {
        try (var c = database.connect(); var p = c.prepareStatement(
                "SELECT user_id,username,password_hash,role,customer_id,active FROM users WHERE username=? COLLATE NOCASE")) {
            p.setString(1, username == null ? "" : username.strip());
            try (var r = p.executeQuery()) {
                if (!r.next() || r.getInt("active") != 1 || !matches(password == null ? "" : password, r.getString("password_hash"))) return null;
                long customer = r.getLong("customer_id");
                Long customerId = r.wasNull() ? null : customer;
                return new User(r.getLong("user_id"), r.getString("username"), User.Role.valueOf(r.getString("role")), customerId, true);
            }
        }
    }

    public List<User> list() throws SQLException {
        try(var c=database.connect();var q=c.createStatement();var r=q.executeQuery("SELECT user_id,username,role,customer_id,active FROM users ORDER BY role,username")){
            List<User> rows=new ArrayList<>();
            while(r.next()){long id=r.getLong("customer_id");Long customerId=r.wasNull()?null:id;
                rows.add(new User(r.getLong("user_id"),r.getString("username"),User.Role.valueOf(r.getString("role")),customerId,r.getInt("active")==1));}
            return rows;
        }
    }

    public void setActive(long id,boolean active)throws SQLException{
        try(var c=database.connect();var p=c.prepareStatement("UPDATE users SET active=? WHERE user_id=?")){
            p.setInt(1,active?1:0);p.setLong(2,id);if(p.executeUpdate()!=1)throw new IllegalArgumentException("Login account not found.");
        }
    }

    public void rename(long id, String username) throws SQLException {
        String name = username == null ? "" : username.strip();
        if (name.isBlank() || name.length() > 60) throw new IllegalArgumentException("Enter a username of 1 to 60 characters.");
        try (var c = database.connect(); var p = c.prepareStatement("UPDATE users SET username=? WHERE user_id=?")) {
            p.setString(1, name); p.setLong(2, id);
            if (p.executeUpdate() != 1) throw new IllegalArgumentException("Login account not found.");
        } catch (SQLException ex) {
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("unique"))
                throw new IllegalArgumentException("That username is already in use.");
            throw ex;
        }
    }

    private String hash(String password) throws SQLException {
        byte[] salt = new byte[16]; random.nextBytes(salt);
        byte[] derived = derive(password, salt);
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(derived);
    }

    private static boolean matches(String password, String encoded) throws SQLException {
        try {
            String[] parts = encoded.split(":");
            return parts.length == 2 && MessageDigest.isEqual(Base64.getDecoder().decode(parts[1]),
                    derive(password, Base64.getDecoder().decode(parts[0])));
        } catch (IllegalArgumentException ex) { return false; }
    }

    private static byte[] derive(String password, byte[] salt) throws SQLException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (Exception ex) { throw new SQLException("Password hashing is unavailable.", ex); }
        finally { spec.clearPassword(); }
    }
}
