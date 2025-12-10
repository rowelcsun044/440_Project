package com.classroom.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.classroom.dao.DatabaseConnection;

@WebServlet("/test")
public class TestServlet extends HttpServlet {
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        
        resp.setContentType("text/html");
        PrintWriter out = resp.getWriter();
        
        out.println("<!DOCTYPE html>");
        out.println("<html>");
        out.println("<head><title>Database Test</title></head>");
        out.println("<body>");
        out.println("<h1>Database Connection Test</h1>");
        
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            out.println("<p style='color:green;'>✓ Successfully connected to database!</p>");
            
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as count FROM Department");
            
            if (rs.next()) {
                int count = rs.getInt("count");
                out.println("<p>Found <strong>" + count + "</strong> departments in the database.</p>");
            }
            
            rs.close();
            stmt.close();
            
        } catch (Exception e) {
            out.println("<p style='color:red;'>✗ Database connection failed!</p>");
            out.println("<p>Error: " + e.getMessage() + "</p>");
            out.println("<pre>");
            e.printStackTrace(out);
            out.println("</pre>");
        } finally {
            if (conn != null) {
                DatabaseConnection.closeQuietly(conn);
            }
        }
        
        out.println("</body>");
        out.println("</html>");
    }
}
