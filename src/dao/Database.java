package dao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Database {
    private final String url;

    public Database(Path file) throws SQLException, IOException {
        Path absolute = file.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        url = "jdbc:sqlite:" + absolute;
        try (var input = Database.class.getResourceAsStream("/schema.sql")) {
            if (input == null) throw new IOException("Database schema is missing.");
            String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection connection = connect(); var statement = connection.createStatement()) {
                for (String sql : schema.split(";")) if (!sql.isBlank()) statement.execute(sql);
            }
        }
    }

    public Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }
}
