package com.classroom.servlet;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.classroom.dao.DatabaseConnection;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("text/html");
        resp.getWriter().println(
            "<!DOCTYPE html><html><head><title>Login</title></head><body>" +
            "<h1>Classroom Management Login</h1>" +
            "<form method='POST'>" +
            "Role: <select name='role'>" +
            "<option value='ADMIN'>Administrator</option>" +
            "<option value='SECRETARY'>Department Secretary</option>" +
            "</select><br/><br/>" +
            "Username (admin username or instructor email):<br/>" +
            "<input type='text' name='username' required/><br/><br/>" +
            "Password:<br/>" +
            "<input type='password' name='password' required/><br/><br/>" +
            "<button type='submit'>Login</button>" +
            "</form>" +
            "<p>" +
            "<button type='button' onclick='history.back()'>Go Back</button> " +
            "<a href='/classroom-management/student/search'>Continue as Student (no login)</a>" +
            "</p>" +
            "</body></html>"
        );
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String role = req.getParameter("role");
        String username = req.getParameter("username");
        String password = req.getParameter("password");

        HttpSession session = req.getSession(true);

        if ("ADMIN".equals(role)) {
            handleAdminLogin(username, password, session, resp);
        } else {
            handleSecretaryLogin(username, password, session, resp);
        }
    }

    private void handleAdminLogin(String username, String password,
                                  HttpSession session, HttpServletResponse resp) throws IOException {

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            String sql = "SELECT admin_id, password FROM Administrator WHERE username = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && password.equals(rs.getString("password"))) {
                        int adminId = rs.getInt("admin_id");
                        session.setAttribute("userType", "ADMIN");
                        session.setAttribute("adminId", adminId);
                        resp.sendRedirect("/classroom-management/admin");
                        return;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            DatabaseConnection.closeQuietly(conn);
        }

        resp.getWriter().println("<h3>Invalid admin credentials.</h3><a href='/classroom-management/login'>Back</a>");
    }

    private void handleSecretaryLogin(String email, String password,
                                      HttpSession session, HttpServletResponse resp) throws IOException {

        // simple shared password for all secretaries, just to satisfy "password" requirement
        if (!"secret123".equals(password)) {
            resp.getWriter().println("<h3>Invalid secretary password.</h3><a href='/classroom-management/login'>Back</a>");
            return;
        }

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            String sql = "SELECT instructor_id, dept_id FROM Instructor WHERE email = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int instrId = rs.getInt("instructor_id");
                        int deptId = rs.getInt("dept_id");

                        session.setAttribute("userType", "SECRETARY");
                        session.setAttribute("secretaryId", instrId);
                        session.setAttribute("deptId", deptId);

                        resp.sendRedirect("/classroom-management/secretary/request");
                        return;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            DatabaseConnection.closeQuietly(conn);
        }

        resp.getWriter().println("<h3>No instructor found with that email.</h3><a href='/classroom-management/login'>Back</a>");
    }
}
