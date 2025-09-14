package com.machina.wards;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SqliteStore {

    private final File dbFile;

    public SqliteStore(File dataFolder) {
        this.dbFile = new File(dataFolder, "MachinaWards.db");
    }

    public void init() {
        try {
            if (!dbFile.getParentFile().exists()) dbFile.getParentFile().mkdirs();
            try (Connection c = connect(); Statement s = c.createStatement()) {
                s.executeUpdate("CREATE TABLE IF NOT EXISTS wards (" +
                        "id TEXT PRIMARY KEY," +
                        "owner TEXT," +
                        "world TEXT," +
                        "x INTEGER," +
                        "y INTEGER," +
                        "z INTEGER," +
                        "radius INTEGER," +
                        "tier TEXT," +
                        "notify INTEGER," +
                        "created_at INTEGER)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS members (" +
                        "ward_id TEXT," +
                        "uuid TEXT," +
                        "PRIMARY KEY(ward_id, uuid))");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS logs (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "ward_id TEXT," +
                        "intruder TEXT," +
                        "name TEXT," +
                        "ts INTEGER)");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
    }

    public java.util.List<Ward> loadWards() {
        java.util.List<Ward> list = new java.util.ArrayList<>();
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM wards")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                java.util.UUID id = java.util.UUID.fromString(rs.getString("id"));
                java.util.UUID owner = java.util.UUID.fromString(rs.getString("owner"));
                String world = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                int radius = rs.getInt("radius");
                String tier = rs.getString("tier");
                boolean notify = rs.getInt("notify") != 0;
                long created = rs.getLong("created_at");
                list.add(new Ward(id, owner, world, x, y, z, radius, tier, notify, created));
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }

    public java.util.List<java.util.UUID> loadMembers(java.util.UUID wardId) {
        java.util.List<java.util.UUID> list = new java.util.ArrayList<>();
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT uuid FROM members WHERE ward_id=?")) {
            ps.setString(1, wardId.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(java.util.UUID.fromString(rs.getString(1)));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }

    public void saveWard(Ward w) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("REPLACE INTO wards(id,owner,world,x,y,z,radius,tier,notify,created_at) VALUES (?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, w.id().toString());
            ps.setString(2, w.owner().toString());
            ps.setString(3, w.world());
            ps.setInt(4, w.bx());
            ps.setInt(5, w.by());
            ps.setInt(6, w.bz());
            ps.setInt(7, w.radius());
            ps.setString(8, w.tier());
            ps.setInt(9, w.notifyEnabled() ? 1 : 0);
            ps.setLong(10, w.createdAt());
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void deleteWard(java.util.UUID id) {
        try (Connection c = connect()) {
            try (PreparedStatement a = c.prepareStatement("DELETE FROM members WHERE ward_id=?")) {
                a.setString(1, id.toString());
                a.executeUpdate();
            }
            try (PreparedStatement b = c.prepareStatement("DELETE FROM logs WHERE ward_id=?")) {
                b.setString(1, id.toString());
                b.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM wards WHERE id=?")) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void addMember(java.util.UUID wardId, java.util.UUID member) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("REPLACE INTO members(ward_id,uuid) VALUES(?,?)")) {
            ps.setString(1, wardId.toString());
            ps.setString(2, member.toString());
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void removeMember(java.util.UUID wardId, java.util.UUID member) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM members WHERE ward_id=? AND uuid=?")) {
            ps.setString(1, wardId.toString());
            ps.setString(2, member.toString());
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void logEntry(java.util.UUID wardId, java.util.UUID intruder, String name, long ts) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("INSERT INTO logs(ward_id,intruder,name,ts) VALUES(?,?,?,?)")) {
            ps.setString(1, wardId.toString());
            ps.setString(2, intruder.toString());
            ps.setString(3, name);
            ps.setLong(4, ts);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public java.util.List<String> recentLogs(java.util.UUID wardId, int limit) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT name, ts FROM logs WHERE ward_id=? ORDER BY ts DESC LIMIT ?")) {
            ps.setString(1, wardId.toString());
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
            while (rs.next()) {
                String name = rs.getString(1);
                long ts = rs.getLong(2);
                out.add(name + " at " + sdf.format(new java.util.Date(ts)));
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }
}
