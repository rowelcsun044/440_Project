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

import com.classroom.dao.DatabaseConnection;

@WebServlet("/student/search")
public class StudentSearchServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String courseName = req.getParameter("courseName");
        String building = req.getParameter("building");
        String deptName = req.getParameter("deptName");
        String day = req.getParameter("day");

        resp.setContentType("text/html");
        StringBuilder out = new StringBuilder();
        out.append("<!DOCTYPE html><html><head><title>Student Search</title></head><body>");
        out.append("<h1>Classroom Search (Student)</h1>");

        out.append("<form method='GET'>");
        out.append("Course Name: <input type='text' name='courseName' value='" +
                (courseName != null ? courseName : "") + "'/><br/><br/>");
        out.append("Department: <input type='text' name='deptName' value='" +
                (deptName != null ? deptName : "") + "'/><br/><br/>");
        out.append("Building: <input type='text' name='building' value='" +
                (building != null ? building : "") + "'/><br/><br/>");
        out.append("Day of Week: <input type='text' name='day' value='" +
                (day != null ? day : "") + "' placeholder='Monday'/><br/><br/>");
        out.append("<button type='submit'>Search</button>");
        out.append("</form><hr/>");

        if (courseName != null || building != null || deptName != null || day != null) {
            Connection conn = null;
            try {
                conn = DatabaseConnection.getConnection();

                String sql =
                        "SELECT s.section_id, co.course_name, d.dept_name, b.building_name, c.room_number, " +
                        "       ts.day_of_week, ts.start_time, ts.end_time " +
                        "FROM Section s " +
                        "JOIN Course co ON s.course_id = co.course_id " +
                        "JOIN Department d ON co.dept_id = d.dept_id " +
                        "JOIN Classroom c ON s.classroom_id = c.classroom_id " +
                        "JOIN Building b ON c.building_id = b.building_id " +
                        "JOIN TimeSlot ts ON s.time_slot_id = ts.time_slot_id " +
                        "WHERE 1=1 ";

                StringBuilder where = new StringBuilder();
                if (courseName != null && !courseName.isEmpty()) {
                    where.append(" AND co.course_name LIKE ? ");
                }
                if (deptName != null && !deptName.isEmpty()) {
                    where.append(" AND d.dept_name LIKE ? ");
                }
                if (building != null && !building.isEmpty()) {
                    where.append(" AND b.building_name LIKE ? ");
                }
                if (day != null && !day.isEmpty()) {
                    where.append(" AND ts.day_of_week LIKE ? ");
                }

                sql += where.toString() + " ORDER BY co.course_name, ts.day_of_week, ts.start_time";

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    int idx = 1;
                    if (courseName != null && !courseName.isEmpty()) {
                        ps.setString(idx++, "%" + courseName + "%");
                    }
                    if (deptName != null && !deptName.isEmpty()) {
                        ps.setString(idx++, "%" + deptName + "%");
                    }
                    if (building != null && !building.isEmpty()) {
                        ps.setString(idx++, "%" + building + "%");
                    }
                    if (day != null && !day.isEmpty()) {
                        ps.setString(idx++, "%" + day + "%");
                    }

                    try (ResultSet rs = ps.executeQuery()) {
                        out.append("<table border='1' cellpadding='5'>");
                        out.append("<tr><th>Section</th><th>Course</th><th>Department</th>" +
                                   "<th>Building</th><th>Room</th><th>Day</th><th>Start</th><th>End</th></tr>");
                        while (rs.next()) {
                            out.append("<tr>");
                            out.append("<td>").append(rs.getInt("section_id")).append("</td>");
                            out.append("<td>").append(rs.getString("course_name")).append("</td>");
                            out.append("<td>").append(rs.getString("dept_name")).append("</td>");
                            out.append("<td>").append(rs.getString("building_name")).append("</td>");
                            out.append("<td>").append(rs.getString("room_number")).append("</td>");
                            out.append("<td>").append(rs.getString("day_of_week")).append("</td>");
                            out.append("<td>").append(rs.getTime("start_time")).append("</td>");
                            out.append("<td>").append(rs.getTime("end_time")).append("</td>");
                            out.append("</tr>");
                        }
                        out.append("</table>");
                    }
                }
            } catch (Exception e) {
                out.append("<p>Error: ").append(e.getMessage()).append("</p>");
            } finally {
                DatabaseConnection.closeQuietly(conn);
            }
        }
        out.append("<button type='button' onclick='history.back()'>Go Back</button> ");
        out.append("<p><a href='/classroom-management/login'>Login</a></p>");
        out.append("</body></html>");
        resp.getWriter().print(out.toString());
    }
}
