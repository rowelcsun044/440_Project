package com.classroom.servlet;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.classroom.dao.DatabaseConnection;

@WebServlet("/secretary/request")
public class SecretaryRequestServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session == null || !"SECRETARY".equals(session.getAttribute("userType"))) {
            resp.sendRedirect("/classroom-management/login");
            return;
        }

        String action = req.getParameter("action");
        if (action == null) {
            // default: show list of all requests
            showRequestList(resp);
        } else if ("new".equals(action)) {
            // show empty form for new request
            showRequestForm(resp, null);
        } else if ("edit".equals(action)) {
            String reqIdParam = req.getParameter("requestId");
            if (reqIdParam != null && !reqIdParam.isEmpty()) {
                int requestId = Integer.parseInt(reqIdParam);
                showRequestForm(resp, requestId);
            } else {
                showRequestList(resp);
            }
        } else {
            showRequestList(resp);
        }
    }

    private void showRequestList(HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html");
        StringBuilder out = new StringBuilder();
        out.append("<!DOCTYPE html><html><head><title>All Requests</title></head><body>");
        out.append("<h1>All Classroom Requests</h1>");
        out.append("<p><a href='/classroom-management/secretary/request?action=new'>➕ New Request</a></p>");

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();

            // show all requests, for all departments
            String sql =
                    "SELECT r.request_id, r.status, s.section_id, co.course_name, d.dept_name, " +
                    "       r.preferred_time_slot_id, r.classroom_id, r.equipment_id " +
                    "FROM Request r " +
                    "JOIN Section s ON r.section_id = s.section_id " +
                    "JOIN Course co ON s.course_id = co.course_id " +
                    "JOIN Department d ON co.dept_id = d.dept_id " +
                    "ORDER BY r.request_id DESC";

            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                out.append("<table border='1' cellpadding='5'>");
                out.append("<tr>");
                out.append("<th>ID</th><th>Section</th><th>Course</th><th>Department</th>");
                out.append("<th>Status</th><th>Pref TimeSlot</th><th>Pref Room</th><th>Equipment</th><th>Actions</th>");
                out.append("</tr>");

                while (rs.next()) {
                    out.append("<tr>");
                    out.append("<td>").append(rs.getInt("request_id")).append("</td>");
                    out.append("<td>").append(rs.getInt("section_id")).append("</td>");
                    out.append("<td>").append(rs.getString("course_name")).append("</td>");
                    out.append("<td>").append(rs.getString("dept_name")).append("</td>");
                    out.append("<td>").append(rs.getString("status")).append("</td>");
                    out.append("<td>").append(rs.getObject("preferred_time_slot_id")).append("</td>");
                    out.append("<td>").append(rs.getObject("classroom_id")).append("</td>");
                    out.append("<td>").append(rs.getObject("equipment_id")).append("</td>");
                    out.append("<td>");
                    out.append("<a href='/classroom-management/secretary/request?action=edit&requestId=")
                       .append(rs.getInt("request_id")).append("'>Edit</a>");
                    out.append("</td>");
                    out.append("</tr>");
                }

                out.append("</table>");
            }

        } catch (Exception e) {
            out.append("<p>Error: ").append(e.getMessage()).append("</p>");
        } finally {
            DatabaseConnection.closeQuietly(conn);
        }

        out.append("<p>");
        out.append("<button type='button' onclick='history.back()'>Go Back</button> ");
        out.append("<a href='/classroom-management/login'>Logout</a>");
        out.append("</p>");

        out.append("</body></html>");
        resp.getWriter().print(out.toString());
    }

    private void showRequestForm(HttpServletResponse resp, Integer requestId) throws IOException {
        resp.setContentType("text/html");
        StringBuilder out = new StringBuilder();
        out.append("<!DOCTYPE html><html><head><title>Submit Request</title></head><body>");

        boolean isEdit = (requestId != null);
        out.append("<h1>").append(isEdit ? "Edit Classroom Request" : "Submit Classroom Request").append("</h1>");

        // values for editing
        Integer existingSectionId = null;
        Integer existingTimeSlotId = null;
        Integer existingClassroomId = null;
        Integer existingEquipmentId = null;

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();

            // load existing request values if editing
            if (isEdit) {
                String loadSql = "SELECT section_id, preferred_time_slot_id, classroom_id, equipment_id " +
                                 "FROM Request WHERE request_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(loadSql)) {
                    ps.setInt(1, requestId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            existingSectionId = rs.getInt("section_id");
                            if (rs.wasNull()) existingSectionId = null;
                            
                            existingTimeSlotId = rs.getInt("preferred_time_slot_id");
                            if (rs.wasNull()) existingTimeSlotId = null;
                            
                            existingClassroomId = rs.getInt("classroom_id");
                            if (rs.wasNull()) existingClassroomId = null;
                            
                            existingEquipmentId = rs.getInt("equipment_id");
                            if (rs.wasNull()) existingEquipmentId = null;
                        }
                    }
                }
            }

            out.append("<form method='POST'>");

            if (isEdit) {
                out.append("<input type='hidden' name='requestId' value='").append(requestId).append("'/>");
            }

            // sections (ALL departments)
            out.append("Section (required):<br/>");
            out.append("<select name='sectionId' required>");
            out.append("<option value=''>-- Select --</option>");
            String secSql = "SELECT s.section_id, co.course_name " +
                            "FROM Section s " +
                            "JOIN Course co ON s.course_id = co.course_id " +
                            "ORDER BY s.section_id";
            try (PreparedStatement ps = conn.prepareStatement(secSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int sid = rs.getInt("section_id");
                    out.append("<option value='").append(sid).append("'");
                    if (existingSectionId != null && existingSectionId == sid) {
                        out.append(" selected");
                    }
                    out.append(">");
                    out.append("Section ").append(sid).append(" - ");
                    out.append(rs.getString("course_name"));
                    out.append("</option>");
                }
            }
            out.append("</select><br/><br/>");

            // time slot
            out.append("Preferred Time Slot (optional):<br/>");
            out.append("<select name='timeSlotId'>");
            out.append("<option value=''>-- No preference --</option>");
            String tsSql = "SELECT time_slot_id, day_of_week, start_time, end_time " +
                           "FROM TimeSlot ORDER BY day_of_week, start_time";
            try (PreparedStatement ps = conn.prepareStatement(tsSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int tsId = rs.getInt("time_slot_id");
                    out.append("<option value='").append(tsId).append("'");
                    if (existingTimeSlotId != null && existingTimeSlotId == tsId) {
                        out.append(" selected");
                    }
                    out.append(">");
                    out.append(rs.getString("day_of_week")).append(" ");
                    out.append(rs.getString("start_time")).append("-");
                    out.append(rs.getString("end_time"));
                    out.append("</option>");
                }
            }
            out.append("</select><br/><br/>");

            // classroom
            out.append("Preferred Classroom (optional):<br/>");
            out.append("<select name='classroomId'>");
            out.append("<option value=''>-- No preference --</option>");
            String roomSql = "SELECT c.classroom_id, b.building_name, c.room_number, c.capacity " +
                             "FROM Classroom c JOIN Building b ON c.building_id = b.building_id " +
                             "ORDER BY b.building_name, c.room_number";
            try (PreparedStatement ps = conn.prepareStatement(roomSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int cid = rs.getInt("classroom_id");
                    out.append("<option value='").append(cid).append("'");
                    if (existingClassroomId != null && existingClassroomId == cid) {
                        out.append(" selected");
                    }
                    out.append(">");
                    out.append(rs.getString("building_name")).append(" ");
                    out.append(rs.getString("room_number"));
                    out.append(" (cap ").append(rs.getInt("capacity")).append(")");
                    out.append("</option>");
                }
            }
            out.append("</select><br/><br/>");

            // equipment
            out.append("Required Equipment (optional):<br/>");
            out.append("<select name='equipmentId'>");
            out.append("<option value=''>-- None --</option>");
            String eqSql = "SELECT equipment_id, equipment_name FROM Equipment";
            try (PreparedStatement ps = conn.prepareStatement(eqSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int eid = rs.getInt("equipment_id");
                    out.append("<option value='").append(eid).append("'");
                    if (existingEquipmentId != null && existingEquipmentId == eid) {
                        out.append(" selected");
                    }
                    out.append(">");
                    out.append(rs.getString("equipment_name"));
                    out.append("</option>");
                }
            }
            out.append("</select><br/><br/>");

            out.append("<button type='submit'>")
               .append(isEdit ? "Save Changes" : "Submit Request")
               .append("</button>");
            out.append(" <a href='/classroom-management/secretary/request'>Cancel</a>");

            out.append("</form>");

        } catch (Exception e) {
            out.append("<p>Error: ").append(e.getMessage()).append("</p>");
        } finally {
            DatabaseConnection.closeQuietly(conn);
        }

        out.append("<p><button type='button' onclick='history.back()'>Go Back</button></p>");
        out.append("</body></html>");
        resp.getWriter().print(out.toString());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session == null || !"SECRETARY".equals(session.getAttribute("userType"))) {
            resp.sendRedirect("/classroom-management/login");
            return;
        }

        int secretaryId = (int) session.getAttribute("secretaryId");

        String reqIdParam = req.getParameter("requestId"); // null/empty for new, value for edit
        int sectionId = Integer.parseInt(req.getParameter("sectionId"));

        String tsParam = req.getParameter("timeSlotId");
        Integer timeSlotId = (tsParam != null && !tsParam.isEmpty()) ? Integer.parseInt(tsParam) : null;

        String roomParam = req.getParameter("classroomId");
        Integer classroomId = (roomParam != null && !roomParam.isEmpty()) ? Integer.parseInt(roomParam) : null;

        String eqParam = req.getParameter("equipmentId");
        Integer equipmentId = (eqParam != null && !eqParam.isEmpty()) ? Integer.parseInt(eqParam) : null;

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();

            // If no preferred time, use section's current time_slot_id (if any)
            if (timeSlotId == null) {
                String tsSql = "SELECT time_slot_id FROM Section WHERE section_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(tsSql)) {
                    ps.setInt(1, sectionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            int sTs = rs.getInt("time_slot_id");
                            if (!rs.wasNull()) {
                                timeSlotId = sTs;
                            }
                        }
                    }
                }
            }

            if (reqIdParam == null || reqIdParam.isEmpty()) {
                // NEW request: INSERT
                int requestId = 0;
                String idSql = "SELECT COALESCE(MAX(request_id), 9000) + 1 AS new_id FROM Request";
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery(idSql)) {
                    if (rs.next()) {
                        requestId = rs.getInt("new_id");
                    }
                }

                String insSql =
                        "INSERT INTO Request (request_id, section_id, submitted_by, " +
                        "preferred_time_slot_id, classroom_id, equipment_id, status, submission_date, admin_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 'PENDING', CURDATE(), NULL)";

                try (PreparedStatement ps = conn.prepareStatement(insSql)) {
                    ps.setInt(1, requestId);
                    ps.setInt(2, sectionId);
                    ps.setInt(3, secretaryId);

                    if (timeSlotId != null) ps.setInt(4, timeSlotId);
                    else ps.setNull(4, Types.INTEGER);

                    if (classroomId != null) ps.setInt(5, classroomId);
                    else ps.setNull(5, Types.INTEGER);

                    if (equipmentId != null) ps.setInt(6, equipmentId);
                    else ps.setNull(6, Types.INTEGER);

                    ps.executeUpdate();
                }

            } else {
                // EXISTING request: UPDATE (change it later)
                int requestId = Integer.parseInt(reqIdParam);

                String updSql =
                        "UPDATE Request SET section_id = ?, submitted_by = ?, " +
                        "preferred_time_slot_id = ?, classroom_id = ?, equipment_id = ?, " +
                        "status = 'PENDING', submission_date = CURDATE(), admin_id = NULL " +
                        "WHERE request_id = ?";

                try (PreparedStatement ps = conn.prepareStatement(updSql)) {
                    ps.setInt(1, sectionId);
                    ps.setInt(2, secretaryId);

                    if (timeSlotId != null) ps.setInt(3, timeSlotId);
                    else ps.setNull(3, Types.INTEGER);

                    if (classroomId != null) ps.setInt(4, classroomId);
                    else ps.setNull(4, Types.INTEGER);

                    if (equipmentId != null) ps.setInt(5, equipmentId);
                    else ps.setNull(5, Types.INTEGER);

                    ps.setInt(6, requestId);
                    ps.executeUpdate();
                }
            }

            resp.sendRedirect("/classroom-management/secretary/request");
        } catch (Exception e) {
            resp.getWriter().println("<h3>Error: " + e.getMessage() + "</h3>");
            resp.getWriter().println("<button type='button' onclick='history.back()'>Go Back</button>");
        } finally {
            DatabaseConnection.closeQuietly(conn);
        }
    }
}
