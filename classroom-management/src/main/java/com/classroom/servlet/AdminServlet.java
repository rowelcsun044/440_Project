package com.classroom.servlet;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.classroom.dao.DatabaseConnection;
import com.classroom.service.SchedulerService;

@WebServlet("/admin")
public class AdminServlet extends HttpServlet {

    private final SchedulerService schedulerService = new SchedulerService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session == null || !"ADMIN".equals(session.getAttribute("userType"))) {
            resp.sendRedirect("/classroom-management/login");
            return;
        }

        String action = req.getParameter("action");
        if (action == null) action = "dashboard";

        switch (action) {
            case "runScheduler":
                showRunScheduler(resp);
                break;
            case "blackoutForm":
                showBlackoutForm(resp);
                break;
            case "reportBuilding":
                showReportByBuilding(resp);
                break;
            case "reportTime":
                showReportByTime(resp);
                break;
            default:
                showDashboard(resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session == null || !"ADMIN".equals(session.getAttribute("userType"))) {
            resp.sendRedirect("/classroom-management/login");
            return;
        }

        String action = req.getParameter("action");
        if ("runScheduler".equals(action)) {
            runScheduler(session, resp);
        } else if ("saveBlackout".equals(action)) {
            saveBlackout(session, req, resp);
        } else {
            resp.sendRedirect("/classroom-management/admin");
        }
    }

    private void showDashboard(HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html");
        resp.getWriter().println(
            "<!DOCTYPE html><html><head><title>Admin</title></head><body>" +
            "<h1>Administrator Dashboard</h1>" +
            "<ul>" +
            "<li><a href='?action=runScheduler'>Run Auto-Assignment</a></li>" +
            "<li><a href='?action=blackoutForm'>Manage Blackout Hours</a></li>" +
            "<li><a href='?action=reportBuilding'>Report by Building</a></li>" +
            "<li><a href='?action=reportTime'>Report by Time Slot</a></li>" +
            "</ul>" +
            "<button type='button' onclick='history.back()'>Go Back</button> " +
            "</body></html>"
        );
    }

    private void showRunScheduler(HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html");
        resp.getWriter().println(
            "<!DOCTYPE html><html><head><title>Run Scheduler</title></head><body>" +
            "<h1>Run Auto-Assignment</h1>" +
            "<form method='POST'>" +
            "<input type='hidden' name='action' value='runScheduler'/>" +
            "<button type='submit'>Process Pending Requests</button>" +
            "</form>" +
            "<p><a href='/classroom-management/admin'>Back</a></p>" +
            "</body></html>"
        );
    }

    private void runScheduler(HttpSession session, HttpServletResponse resp) throws IOException {
        int adminId = (int) session.getAttribute("adminId");
        try {
            int count = schedulerService.processPendingRequests(adminId);
            resp.setContentType("text/html");
            resp.getWriter().println("<h2>Processed requests: " + count + "</h2>");
            resp.getWriter().println("<p><a href='/classroom-management/admin'>Back to Dashboard</a></p>");
        } catch (Exception e) {
            resp.getWriter().println("<h3>Error: " + e.getMessage() + "</h3>");
            resp.getWriter().println("<p><a href='/classroom-management/admin'>Back</a></p>");
        }
    }

    private void showBlackoutForm(HttpServletResponse resp) throws IOException {
        Connection conn = null;
        StringBuilder out = new StringBuilder();

        out.append("<!DOCTYPE html><html><head><title>Blackout Hours</title></head><body>");
        out.append("<h1>Add Blackout Hour</h1>");

        try {
            conn = DatabaseConnection.getConnection();
            out.append("<form method='POST'>");
            out.append("<input type='hidden' name='action' value='saveBlackout'/>");

            out.append("Classroom:<br/>");
            out.append("<select name='classroomId'>");
            String roomSql = "SELECT c.classroom_id, b.building_name, c.room_number " +
                             "FROM Classroom c JOIN Building b ON c.building_id = b.building_id " +
                             "ORDER BY b.building_name, c.room_number";
            try (PreparedStatement ps = conn.prepareStatement(roomSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.append("<option value='").append(rs.getInt("classroom_id")).append("'>");
                    out.append(rs.getString("building_name")).append(" ");
                    out.append(rs.getString("room_number"));
                    out.append("</option>");
                    out.append("<button type='button' onclick='history.back()'>Go Back</button> ");
                }
            }
            out.append("</select><br/><br/>");

            out.append("Date (YYYY-MM-DD):<br/>");
            out.append("<input type='text' name='date'/><br/><br/>");
            out.append("Start Time (HH:MM:SS):<br/>");
            out.append("<input type='text' name='startTime'/><br/><br/>");
            out.append("End Time (HH:MM:SS):<br/>");
            out.append("<input type='text' name='endTime'/><br/><br/>");
            out.append("Reason:<br/>");
            out.append("<input type='text' name='reason'/><br/><br/>");

            out.append("<button type='submit'>Save</button>");
            out.append("<button type='button' onclick='history.back()'>Go Back</button> ");
            out.append("</form>");

        } catch (Exception e) {
            out.append("<p>Error: ").append(e.getMessage()).append("</p>");
        } finally {
            DatabaseConnection.closeQuietly(conn);
        }

        out.append("</body></html>");
        resp.setContentType("text/html");
        resp.getWriter().print(out.toString());
    }

    private void saveBlackout(HttpSession session, HttpServletRequest req,
                              HttpServletResponse resp) throws IOException {

        int adminId = (int) session.getAttribute("adminId");
        int classroomId = Integer.parseInt(req.getParameter("classroomId"));
        String date = req.getParameter("date");
        String start = req.getParameter("startTime");
        String end = req.getParameter("endTime");
        String reason = req.getParameter("reason");

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();

            // blackout_id = MAX + 1
            int blackoutId = 0;
            String idSql = "SELECT COALESCE(MAX(blackout_id), 0) + 1 AS new_id FROM BlackoutHour";
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(idSql)) {
                if (rs.next()) blackoutId = rs.getInt("new_id");
            }

            String insSql = "INSERT INTO BlackoutHour " +
                    "(blackout_id, classroom_id, blackout_date, start_time, end_time, reason, admin_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insSql)) {
                ps.setInt(1, blackoutId);
                ps.setInt(2, classroomId);
                ps.setString(3, date);
                ps.setString(4, start);
                ps.setString(5, end);
                ps.setString(6, reason);
                ps.setInt(7, adminId);
                ps.executeUpdate();
            }

            resp.sendRedirect("/classroom-management/admin?action=blackoutForm");
        } catch (Exception e) {
            resp.getWriter().println("<h3>Error: " + e.getMessage() + "</h3>");
            resp.getWriter().println("<p><a href='/classroom-management/admin'>Back</a></p>");
        } finally {
            DatabaseConnection.closeQuietly(conn);
        }
    }

    private void showReportByBuilding(HttpServletResponse resp) throws IOException {
        Connection conn = null;
        StringBuilder out = new StringBuilder();
        out.append("<!DOCTYPE html><html><head><title>Report by Building</title></head><body>");
        out.append("<h1>Room Assignment Report (by Building)</h1>");
        out.append("<button type='button' onclick='history.back()'>Go Back</button> ");

        try {
            conn = DatabaseConnection.getConnection();
            ResultSet rs = schedulerService.getReportByBuilding(conn);

            out.append("<table border='1' cellpadding='5'>");
            out.append("<tr><th>Building</th><th>Room</th><th>Section</th><th>Course</th>" +
                       "<th>Day</th><th>Start</th><th>End</th></tr>");
            while (rs.next()) {
                out.append("<tr>");
                out.append("<td>").append(rs.getString("building_name")).append("</td>");
                out.append("<td>").append(rs.getString("room_number")).append("</td>");
                out.append("<td>").append(rs.getInt("section_id")).append("</td>");
                out.append("<td>").append(rs.getString("course_name")).append("</td>");
                out.append("<td>").append(rs.getString("day_of_week")).append("</td>");
                out.append("<td>").append(rs.getTime("start_time")).append("</td>");
                out.append("<td>").append(rs.getTime("end_time")).append("</td>");
                out.append("</tr>");
            }
            out.append("</table>");

        } catch (Exception e) {
            out.append("<p>Error: ").append(e.getMessage()).append("</p>");
        } finally {
            DatabaseConnection.closeQuietly(conn);
        }

        out.append("</body></html>");
        resp.setContentType("text/html");
        resp.getWriter().print(out.toString());
    }

    private void showReportByTime(HttpServletResponse resp) throws IOException {
        Connection conn = null;
        StringBuilder out = new StringBuilder();
        out.append("<!DOCTYPE html><html><head><title>Report by Time</title></head><body>");
        out.append("<h1>Room Assignment Report (by Time Slot)</h1>");

        try {
            conn = DatabaseConnection.getConnection();
            ResultSet rs = schedulerService.getReportByTimeSlot(conn);

            out.append("<table border='1' cellpadding='5'>");
            out.append("<tr><th>Day</th><th>Start</th><th>End</th>" +
                       "<th>Building</th><th>Room</th><th>Section</th><th>Course</th></tr>");
            while (rs.next()) {
                out.append("<tr>");
                out.append("<td>").append(rs.getString("day_of_week")).append("</td>");
                out.append("<td>").append(rs.getTime("start_time")).append("</td>");
                out.append("<td>").append(rs.getTime("end_time")).append("</td>");
                out.append("<td>").append(rs.getString("building_name")).append("</td>");
                out.append("<td>").append(rs.getString("room_number")).append("</td>");
                out.append("<td>").append(rs.getInt("section_id")).append("</td>");
                out.append("<td>").append(rs.getString("course_name")).append("</td>");
                out.append("</tr>");
            }
            out.append("</table>");

        } catch (Exception e) {
            out.append("<p>Error: ").append(e.getMessage()).append("</p>");
        } finally {
            DatabaseConnection.closeQuietly(conn);
        }

        out.append("<button type='button' onclick='history.back()'>Go Back</button> ");
        out.append("</body></html>");
        resp.setContentType("text/html");
        resp.getWriter().print(out.toString());
    }
}
