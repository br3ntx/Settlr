package com.settlr.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Opens connections to the MySQL database.
 *
 * Singleton: there is only ever one DBConnection object, reached with
 * DBConnection.getInstance(). It reads the database address, username and
 * password once from resources/config/db.properties (each member has their
 * own copy of that file, and it is never committed to GitHub).
 *
 * Every call to getConnection() returns a NEW connection. DAOs open it inside
 * try-with-resources so it is always closed again.
 */
public final class DBConnection {

    private static final String CONFIG_FILE = "resources/config/db.properties";
    private static DBConnection instance;

    private final String url;
    private final String user;
    private final String password;
    private final String configProblem; // null when the config loaded fine

    private DBConnection() {
        Properties props = new Properties();
        String problem = null;
        try {
            loadConfig(props);
        } catch (IOException e) {
            problem = e.getMessage();
        }
        this.url = props.getProperty("db.url", "jdbc:mysql://localhost:3306/settlr_db");
        this.user = props.getProperty("db.user", "root");
        this.password = props.getProperty("db.password", "");
        this.configProblem = problem;
    }

    public static synchronized DBConnection getInstance() {
        if (instance == null) {
            instance = new DBConnection();
        }
        return instance;
    }

    /**
     * Opens a new connection to settlr_db. Callers must close it, normally with
     * try (Connection c = DBConnection.getInstance().getConnection()) { ... }
     */
    public Connection getConnection() throws SQLException {
        if (configProblem != null) {
            throw new SQLException(configProblem);
        }
        try {
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("No suitable driver")) {
                throw new SQLException("MySQL driver not found. Put mysql-connector-j-<version>.jar "
                        + "in the lib folder and make sure VS Code lists it under Referenced Libraries.", e);
            }
            throw e;
        }
    }

    /** Reads db.properties from the project folder, or from the classpath as a fallback. */
    private static void loadConfig(Properties props) throws IOException {
        Path file = Path.of(CONFIG_FILE);
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
                return;
            }
        }
        try (InputStream in = DBConnection.class.getResourceAsStream("/config/db.properties")) {
            if (in != null) {
                props.load(in);
                return;
            }
        }
        throw new IOException("Can't find " + CONFIG_FILE + ". Copy db.properties.example to "
                + "db.properties in the same folder and fill in your own MySQL username and password.");
    }

    /** Quick test: run this file to check that Java can reach your MySQL database. */
    public static void main(String[] args) {
        try (Connection c = getInstance().getConnection()) {
            System.out.println("Connected! Database: " + c.getCatalog());
        } catch (SQLException e) {
            System.out.println("Could not connect: " + e.getMessage());
        }
    }
}
