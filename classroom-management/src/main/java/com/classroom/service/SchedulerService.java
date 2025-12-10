package com.classroom.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import com.classroom.dao.DatabaseConnection;

public class SchedulerService {

    public int processPendingRequests(int adminId) throws SQLException {
        Connection conn = null;
        int processed = 0;

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            List<RequestInfo> requests = getPendingRequests(conn);

            for (RequestInfo req : requests) {
                boolean ok = assignClassroom(conn, req, adminId);
                if (ok) processed++;
            }

            conn.commit();
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) conn.setAutoCommit(true);
            DatabaseConnection.closeQuietly(conn);
        }

        return processed;
    }

    private List<RequestInfo> getPendingRequests(Connection conn) throws SQLException {
        List<RequestInfo> list = new ArrayList<>();

        String sql =
                "SELECT r.request_id, r.section_id, r.preferred_time_slot_id, r.classroom_id, r.equipment_id, " +
                "       s.capacity AS section_capacity, s.time_slot_id AS section_time_slot_id, " +
                "       c.dept_id, d.location AS dept_building_name " +
                "FROM Request r " +
                "JOIN Section s ON r.section_id = s.section_id " +
                "JOIN Course c ON s.course_id = c.course_id " +
                "JOIN Department d ON c.dept_id = d.dept_id " +
                "WHERE r.status = 'PENDING'";

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                RequestInfo info = new RequestInfo();
                info.requestId = rs.getInt("request_id");
                info.sectionId = rs.getInt("section_id");
                int prefTs = rs.getInt("preferred_time_slot_id");
                if (rs.wasNull()) prefTs = 0;
                int sectionTs = rs.getInt("section_time_slot_id");
                if (rs.wasNull()) sectionTs = 0;
                info.timeSlotId = (prefTs != 0 ? prefTs : sectionTs);
                info.preferredClassroomId = rs.getInt("classroom_id");
                if (rs.wasNull()) info.preferredClassroomId = 0;
                info.equipmentId = rs.getInt("equipment_id");
                if (rs.wasNull()) info.equipmentId = 0;
                info.sectionCapacity = rs.getInt("section_capacity");
                info.deptBuildingName = rs.getString("dept_building_name");
                list.add(info);
            }
        }

        return list;
    }

    private boolean assignClassroom(Connection conn, RequestInfo req, int adminId) throws SQLException {
        if (req.timeSlotId == 0) {
            updateRequest(conn, req.requestId, null, adminId, "UNASSIGNED");
            return false;
        }

        Integer assigned = null;

        // 1) Try preferred classroom if given
        if (req.preferredClassroomId > 0) {
            if (isClassroomSuitable(conn, req.preferredClassroomId, req.timeSlotId,
                    req.equipmentId, req.sectionCapacity)) {
                assigned = req.preferredClassroomId;
            }
        }

        // 2) Try classrooms in department building
        if (assigned == null && req.deptBuildingName != null) {
            assigned = findAvailableClassroom(conn, req.timeSlotId, req.equipmentId,
                    req.sectionCapacity, req.deptBuildingName, true);
        }

        // 3) Try any classroom
        if (assigned == null) {
            assigned = findAvailableClassroom(conn, req.timeSlotId, req.equipmentId,
                    req.sectionCapacity, null, false);
        }

        if (assigned != null) {
            updateRequest(conn, req.requestId, assigned, adminId, "APPROVED");
            updateSection(conn, req.sectionId, assigned, req.timeSlotId);
            return true;
        } else {
            updateRequest(conn, req.requestId, null, adminId, "UNASSIGNED");
            return false;
        }
    }

    private boolean isClassroomSuitable(Connection conn, int classroomId, int timeSlotId,
                                        int equipmentId, int neededCapacity) throws SQLException {

        // capacity
        String capSql = "SELECT capacity FROM Classroom WHERE classroom_id = ?";
        int roomCap = 0;
        try (PreparedStatement ps = conn.prepareStatement(capSql)) {
            ps.setInt(1, classroomId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    roomCap = rs.getInt("capacity");
                }
            }
        }
        if (roomCap > 0 && neededCapacity > 0 && roomCap < neededCapacity) {
            return false;
        }

        // conflict with other sections
        String conflictSql = "SELECT COUNT(*) FROM Section " +
                "WHERE classroom_id = ? AND time_slot_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(conflictSql)) {
            ps.setInt(1, classroomId);
            ps.setInt(2, timeSlotId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return false;
                }
            }
        }

        // blackout hours
        String blackoutSql =
                "SELECT COUNT(*) " +
                "FROM BlackoutHour bh " +
                "JOIN TimeSlot ts ON ts.time_slot_id = ? " +
                "WHERE bh.classroom_id = ? " +
                "  AND ts.day_of_week = DAYNAME(bh.blackout_date) " +
                "  AND NOT (ts.end_time <= bh.start_time OR ts.start_time >= bh.end_time)";
        try (PreparedStatement ps = conn.prepareStatement(blackoutSql)) {
            ps.setInt(1, timeSlotId);
            ps.setInt(2, classroomId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return false;
                }
            }
        }

        // equipment: only check that requested equipment is globally "Available"
        if (equipmentId > 0) {
            String eqSql = "SELECT status FROM Equipment WHERE equipment_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(eqSql)) {
                ps.setInt(1, equipmentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String status = rs.getString("status");
                        if (status == null || !status.equalsIgnoreCase("Available")) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    private Integer findAvailableClassroom(Connection conn, int timeSlotId, int equipmentId,
                                           int neededCapacity, String deptBuildingName,
                                           boolean useDeptBuilding) throws SQLException {

        String sql;
        if (useDeptBuilding) {
            sql = "SELECT c.classroom_id " +
                  "FROM Classroom c " +
                  "JOIN Building b ON c.building_id = b.building_id " +
                  "WHERE b.building_name = ? " +
                  "ORDER BY c.capacity";
        } else {
            sql = "SELECT classroom_id FROM Classroom ORDER BY capacity";
        }

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (useDeptBuilding) {
                ps.setString(1, deptBuildingName);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int cid = rs.getInt("classroom_id");
                    if (isClassroomSuitable(conn, cid, timeSlotId, equipmentId, neededCapacity)) {
                        return cid;
                    }
                }
            }
        }

        return null;
    }

    private void updateRequest(Connection conn, int requestId, Integer classroomId,
                               int adminId, String status) throws SQLException {

        String sql = "UPDATE Request SET status = ?, classroom_id = ?, admin_id = ? " +
                     "WHERE request_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            if (classroomId != null) {
                ps.setInt(2, classroomId);
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setInt(3, adminId);
            ps.setInt(4, requestId);
            ps.executeUpdate();
        }
    }

    private void updateSection(Connection conn, int sectionId, int classroomId, int timeSlotId)
            throws SQLException {

        String sql = "UPDATE Section SET classroom_id = ?, time_slot_id = ? " +
                     "WHERE section_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, classroomId);
            ps.setInt(2, timeSlotId);
            ps.setInt(3, sectionId);
            ps.executeUpdate();
        }
    }

    // Simple report by building
    public ResultSet getReportByBuilding(Connection conn) throws SQLException {
        String sql =
                "SELECT b.building_name, c.room_number, s.section_id, co.course_name, " +
                "       ts.day_of_week, ts.start_time, ts.end_time " +
                "FROM Section s " +
                "JOIN Classroom c ON s.classroom_id = c.classroom_id " +
                "JOIN Building b ON c.building_id = b.building_id " +
                "JOIN Course co ON s.course_id = co.course_id " +
                "JOIN TimeSlot ts ON s.time_slot_id = ts.time_slot_id " +
                "ORDER BY b.building_name, c.room_number, ts.day_of_week, ts.start_time";
        PreparedStatement ps = conn.prepareStatement(sql);
        return ps.executeQuery();
    }

    // Simple report by time slot
    public ResultSet getReportByTimeSlot(Connection conn) throws SQLException {
        String sql =
                "SELECT ts.day_of_week, ts.start_time, ts.end_time, " +
                "       b.building_name, c.room_number, s.section_id, co.course_name " +
                "FROM Section s " +
                "JOIN Classroom c ON s.classroom_id = c.classroom_id " +
                "JOIN Building b ON c.building_id = b.building_id " +
                "JOIN Course co ON s.course_id = co.course_id " +
                "JOIN TimeSlot ts ON s.time_slot_id = ts.time_slot_id " +
                "ORDER BY ts.day_of_week, ts.start_time, b.building_name, c.room_number";
        PreparedStatement ps = conn.prepareStatement(sql);
        return ps.executeQuery();
    }

    private static class RequestInfo {
        int requestId;
        int sectionId;
        int timeSlotId;
        int preferredClassroomId;
        int equipmentId;
        int sectionCapacity;
        String deptBuildingName;
    }
}
