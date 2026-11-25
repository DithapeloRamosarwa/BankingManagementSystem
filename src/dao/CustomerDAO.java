package dao;

import model.Customer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class CustomerDAO {
    private final Database database;
    public CustomerDAO(Database database) { this.database = database; }

    public long create(Customer validated) throws SQLException {
        try (var c = database.connect(); var p = c.prepareStatement(
                "INSERT INTO customers(first_name,last_name,address,phone,employed) VALUES(?,?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, validated.firstName()); p.setString(2, validated.lastName());
            p.setString(3, validated.address()); p.setString(4, validated.phone()); p.setInt(5, validated.employed() ? 1 : 0);
            p.executeUpdate();
            try (var keys = p.getGeneratedKeys()) { if (keys.next()) return keys.getLong(1); }
        }
        throw new SQLException("Could not save the customer.");
    }

    public Customer find(long id) throws SQLException {
        try (var c = database.connect(); var p = c.prepareStatement("SELECT * FROM customers WHERE customer_id=?")) {
            p.setLong(1, id); try (var r = p.executeQuery()) { return r.next() ? read(r) : null; }
        }
    }

    public void update(Customer validated) throws SQLException {
        try (var c = database.connect(); var p = c.prepareStatement(
                "UPDATE customers SET first_name=?,last_name=?,address=?,phone=?,employed=? WHERE customer_id=?")) {
            p.setString(1, validated.firstName()); p.setString(2, validated.lastName());
            p.setString(3, validated.address()); p.setString(4, validated.phone());
            p.setInt(5, validated.employed() ? 1 : 0); p.setLong(6, validated.id());
            if (p.executeUpdate() != 1) throw new IllegalArgumentException("Customer profile not found.");
        }
    }

    public List<Customer> search(String term) throws SQLException {
        String q = term == null ? "" : term.strip();
        try (var c = database.connect(); var p = c.prepareStatement("""
                SELECT * FROM customers WHERE (? = '' OR first_name LIKE ? OR last_name LIKE ? OR address LIKE ? OR CAST(customer_id AS TEXT) LIKE ?)
                ORDER BY last_name,first_name LIMIT 200
                """)) {
            String like = "%" + q + "%";
            p.setString(1, q); for (int i=2;i<=5;i++) p.setString(i, like);
            try (var r = p.executeQuery()) { List<Customer> result = new ArrayList<>(); while(r.next()) result.add(read(r)); return result; }
        }
    }

    private static Customer read(java.sql.ResultSet r) throws SQLException {
        return new Customer(r.getLong("customer_id"), r.getString("first_name"), r.getString("last_name"),
                r.getString("address"), r.getString("phone"), r.getInt("employed") == 1);
    }
}
