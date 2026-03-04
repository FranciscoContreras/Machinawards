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
                s.executeUpdate("CREATE TABLE IF NOT EXISTS ward_features (" +
                        "ward_id TEXT," +
                        "feature TEXT," +
                        "PRIMARY KEY(ward_id, feature))");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS feature_logs (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "ward_id TEXT," +
                        "feature TEXT," +
                        "message TEXT," +
                        "ts INTEGER)");
                // Migrate: add name column if it doesn't exist yet
                try { s.executeUpdate("ALTER TABLE wards ADD COLUMN name TEXT DEFAULT ''"); }
                catch (SQLException ignore) { /* column already exists */ }
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
                String name = rs.getString("name");
                Ward ward = new Ward(id, owner, world, x, y, z, radius, tier, notify, created);
                ward.setName(name != null ? name : "");
                list.add(ward);
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
             PreparedStatement ps = c.prepareStatement("REPLACE INTO wards(id,owner,world,x,y,z,radius,tier,notify,created_at,name) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
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
            ps.setString(11, w.name());
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void deleteWard(java.util.UUID id) {
        try (Connection c = connect()) {
            for (String table : new String[]{"members", "logs", "ward_features", "feature_logs"}) {
                String col = table.equals("wards") ? "id" : "ward_id";
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE " + col + "=?")) {
                    ps.setString(1, id.toString());
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM wards WHERE id=?")) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public java.util.Set<String> loadFeatures(java.util.UUID wardId) {
        java.util.Set<String> out = new java.util.HashSet<>();
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT feature FROM ward_features WHERE ward_id=?")) {
            ps.setString(1, wardId.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(rs.getString(1));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    public void saveFeature(java.util.UUID wardId, String featureId, boolean enabled) {
        try (Connection c = connect()) {
            if (enabled) {
                try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO ward_features(ward_id,feature) VALUES(?,?)")) {
                    ps.setString(1, wardId.toString());
                    ps.setString(2, featureId);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM ward_features WHERE ward_id=? AND feature=?")) {
                    ps.setString(1, wardId.toString());
                    ps.setString(2, featureId);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void logFeatureEvent(java.util.UUID wardId, String featureId, String message, long ts) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("INSERT INTO feature_logs(ward_id,feature,message,ts) VALUES(?,?,?,?)")) {
            ps.setString(1, wardId.toString());
            ps.setString(2, featureId);
            ps.setString(3, message);
            ps.setLong(4, ts);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public java.util.List<String> getFeatureLogs(java.util.UUID wardId, String featureId, int limit) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT message, ts FROM feature_logs WHERE ward_id=? AND feature=? ORDER BY ts DESC LIMIT ?")) {
            ps.setString(1, wardId.toString());
            ps.setString(2, featureId);
            ps.setInt(3, limit);
            ResultSet rs = ps.executeQuery();
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd HH:mm");
            while (rs.next()) out.add("[" + sdf.format(new java.util.Date(rs.getLong(2))) + "] " + rs.getString(1));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    public void clearFeatureLogs(java.util.UUID wardId, String featureId) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM feature_logs WHERE ward_id=? AND feature=?")) {
            ps.setString(1, wardId.toString());
            ps.setString(2, featureId);
            ps.executeUpdate();
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
