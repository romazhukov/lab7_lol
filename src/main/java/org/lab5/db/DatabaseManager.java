package org.lab5.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static final String DEFAULT_URL = "jdbc:postgresql://pg:5432/studs";
    private final String url;
    private final String user;
    private final String password;

    public DatabaseManager() {
        this.url = readOptionalSetting("DB_URL", DEFAULT_URL);
        this.user = readSetting("DB_USER");
        this.password = readSetting("DB_PASSWORD");
    }

    private String readOptionalSetting(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key);
        }
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private String readSetting(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing DB setting: " + key);
        }
        return value;
    }

    public Connection openConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public void initializeSchema() throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id SERIAL PRIMARY KEY,
                        username TEXT UNIQUE NOT NULL,
                        password_hash TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE SEQUENCE IF NOT EXISTS organizations_id_seq START WITH 1 INCREMENT BY 1
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS organizations (
                        id INTEGER PRIMARY KEY DEFAULT nextval('organizations_id_seq'),
                        name TEXT NOT NULL,
                        coordinates_x INTEGER NOT NULL,
                        coordinates_y REAL NOT NULL,
                        creation_date TIMESTAMPTZ NOT NULL,
                        annual_turnover DOUBLE PRECISION NOT NULL,
                        employees_count BIGINT NOT NULL,
                        type TEXT,
                        street TEXT,
                        zip_code TEXT,
                        owner_id INTEGER NOT NULL REFERENCES users(id)
                    )
                    """);
            statement.execute("""
                    SELECT setval(
                        'organizations_id_seq',
                        COALESCE((SELECT MAX(id) FROM organizations), 0) + 1,
                        false
                    )
                    """);
        }
    }
}
