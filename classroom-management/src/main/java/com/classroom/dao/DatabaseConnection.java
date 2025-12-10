package com.classroom.dao;
import java.sql.*;
public class DatabaseConnection {
    private static final String URL = "jdbc:mysql://localhost:3306/440_project";
    private static final String USER = "root";
    private static final String PASSWORD = "Playmakers8";
    static {
        try { Class.forName("com.mysql.cj.jdbc.Driver"); }
        catch (ClassNotFoundException e) { throw new RuntimeException(e); }
    }
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
    public static void closeQuietly(Connection conn) {
        if (conn != null) {
            try { conn.close(); } catch (SQLException e) { e.printStackTrace(); }
        }
    }
}
