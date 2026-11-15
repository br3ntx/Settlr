package com.settlr.tools;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

/**
 * Wipes settlr_db and loads the defense demo data: database/schema.sql, then database/demo.sql.
 * Run it before every practice and right before the defense, so every run starts the same way.
 *
 * Two ways to run it:
 *   - double-click reset-demo.bat in the project folder, or
 *   - in VS Code, open this file and click "Run" above main.
 *
 * It uses the same resources/config/db.properties as the app. It is a separate tool, not part
 * of the app: nothing in the app calls it, and it works even when settlr_db doesn't exist yet.
 */
public final class ResetDemo {

    private static final String CONFIG = "resources/config/db.properties";
    private static final String[] SCRIPTS = {"database/schema.sql", "database/demo.sql"};

    /** Not meant to be created: everything is static. */
    private ResetDemo() { }

    /** Asks for YES (unless run with --yes), then drops settlr_db and runs schema.sql and demo.sql. */
    public static void main(String[] args) {
        System.out.println("This ERASES everything in settlr_db and loads the demo data.");
        if (!(args.length > 0 && args[0].equals("--yes"))) {
            System.out.print("Type YES and press Enter to continue: ");
            String answer = new Scanner(System.in, StandardCharsets.UTF_8.name()).nextLine().trim();
            if (!answer.equalsIgnoreCase("YES")) {
                System.out.println("Cancelled. Nothing was changed.");
                return;
            }
        }
        try {
            Properties db = loadConfig();
            // Connect to the server itself, not to settlr_db, so this works when it doesn't exist yet.
            String serverUrl = db.getProperty("db.url", "jdbc:mysql://localhost:3306/settlr_db")
                    .replaceFirst("/settlr_db", "/");
            try (Connection c = DriverManager.getConnection(serverUrl,
                    db.getProperty("db.user", "root"), db.getProperty("db.password", ""));
                 Statement st = c.createStatement()) {
                st.execute("DROP DATABASE IF EXISTS settlr_db");
                int count = 0;
                for (String script : SCRIPTS) {
                    for (String sql : splitStatements(read(script))) {
                        st.execute(sql);
                        count++;
                    }
                    System.out.println("Ran " + script);
                }
                System.out.println("Done: " + count + " statements. Log in as ana / settlr123 to start the demo.");
            }
        } catch (IOException e) {
            System.out.println("Could not read a file: " + e.getMessage());
            System.out.println("Run this from the Settlr project folder.");
        } catch (SQLException e) {
            System.out.println("MySQL said: " + e.getMessage());
            System.out.println("Is MySQL running, and is resources/config/db.properties right?");
        }
    }

    /** Reads the MySQL address, user and password from resources/config/db.properties. */
    private static Properties loadConfig() throws IOException {
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(CONFIG)) {
            props.load(in);
        }
        return props;
    }

    /** The whole text of a .sql file. */
    private static String read(String file) throws IOException {
        Path path = Paths.get(file);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /**
     * Splits a .sql file into statements at each ';' that is not inside quotes or a comment,
     * and drops the comments. (The scripts use no DELIMITER blocks.)
     */
    static List<String> splitStatements(String script) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int i = 0;
        int n = script.length();
        while (i < n) {
            char ch = script.charAt(i);
            char next = i + 1 < n ? script.charAt(i + 1) : '\0';
            if (ch == '-' && next == '-') {                 // "-- comment" to the end of the line
                while (i < n && script.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (ch == '/' && next == '*') {                 // "/* comment */"
                int end = script.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                continue;
            }
            if (ch == '\'' || ch == '"' || ch == '`') {     // a quoted string: copy it as it is
                current.append(ch);
                i++;
                while (i < n) {
                    char q = script.charAt(i);
                    current.append(q);
                    i++;
                    if (q == '\\' && i < n) {
                        current.append(script.charAt(i));
                        i++;
                    } else if (q == ch) {
                        if (i < n && script.charAt(i) == ch) { // '' inside a string
                            current.append(ch);
                            i++;
                        } else {
                            break;
                        }
                    }
                }
                continue;
            }
            if (ch == ';') {
                addIfNotBlank(out, current);
                current.setLength(0);
                i++;
                continue;
            }
            current.append(ch);
            i++;
        }
        addIfNotBlank(out, current);
        return out;
    }

    /** Adds the statement to the list unless it is only spaces (e.g. after the last ';'). */
    private static void addIfNotBlank(List<String> out, StringBuilder sql) {
        String s = sql.toString().trim();
        if (!s.isEmpty()) {
            out.add(s);
        }
    }
}
