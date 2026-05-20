package com.machina.wards;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Shared SQL implementation for DataStore backends.
 * All operations run on a single-thread {@code io} executor so DB access is
 * serialised.  Writes are fire-and-forget; reads block until the result is
 * available.
 *
 * Subclasses must implement:
 *   - {@link #openConnection()} — return a fresh JDBC connection
 *   - {@link #insertOrIgnore()}  — "INSERT OR IGNORE" (SQLite) or "INSERT IGNORE" (MySQL)
 *   - {@link #afterConnect(Statement)} — run any post-connect pragmas / session vars
 */
public abstract class AbstractStore implements DataStore {

    protected Connection conn;
    protected final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MachinaWards-DB");
        t.setDaemon(true);
        return t;
    });

    // ── Subclass hooks ────────────────────────────────────────────────────────

    protected abstract Connection openConnection() throws SQLException;
    protected abstract String insertOrIgnore();
    /** DDL snippet for auto-increment PK: "INTEGER PRIMARY KEY AUTOINCREMENT" (SQLite) or "INT AUTO_INCREMENT PRIMARY KEY" (MySQL). */
    protected abstract String idColumn();
    /** Called right after a new connection is opened (e.g. PRAGMA journal_mode). */
    protected void afterConnect(Statement s) throws SQLException {}

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void init() {
        // Open connection and create schema on the IO thread, then block.
        read(() -> {
            ensureConnected();
            try (Statement s = conn.createStatement()) {
                createSchema(s);
                runMigrations(s);
            }
            return null;
        });
    }

    @Override
    public void shutdown() {
        io.shutdown();
        try { io.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
    }

    // ── Schema + Migrations ───────────────────────────────────────────────────

    private void createSchema(Statement s) throws SQLException {
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
                "created_at INTEGER," +
                "name TEXT DEFAULT ''," +
                "entry_message TEXT DEFAULT '')");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS members (" +
                "ward_id TEXT," +
                "uuid TEXT," +
                "trust_level TEXT DEFAULT 'member'," +
                "PRIMARY KEY(ward_id, uuid))");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS logs (" +
                "id " + idColumn() + "," +
                "ward_id TEXT," +
                "intruder TEXT," +
                "name TEXT," +
                "ts INTEGER)");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS ward_features (" +
                "ward_id TEXT," +
                "feature TEXT," +
                "PRIMARY KEY(ward_id, feature))");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS feature_logs (" +
                "id " + idColumn() + "," +
                "ward_id TEXT," +
                "feature TEXT," +
                "message TEXT," +
                "ts INTEGER)");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS ward_flags (" +
                "ward_id TEXT," +
                "flag TEXT," +
                "PRIMARY KEY(ward_id, flag))");
    }

    private void runMigrations(Statement s) {
        tryAlter(s, "ALTER TABLE wards ADD COLUMN name TEXT DEFAULT ''");
        tryAlter(s, "ALTER TABLE wards ADD COLUMN entry_message TEXT DEFAULT ''");
        tryAlter(s, "ALTER TABLE members ADD COLUMN trust_level TEXT DEFAULT 'member'"); // v2.0
    }

    private void tryAlter(Statement s, String sql) {
        try { s.executeUpdate(sql); } catch (SQLException ignore) { /* column already exists */ }
    }

    // ── Connection management ─────────────────────────────────────────────────

    private void ensureConnected() throws SQLException {
        if (conn != null && conn.isValid(2)) return;
        try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
        conn = openConnection();
        try (Statement s = conn.createStatement()) { afterConnect(s); }
    }

    // ── IO helpers ────────────────────────────────────────────────────────────

    @FunctionalInterface
    protected interface SQLRunnable {
        void run() throws SQLException;
    }

    protected void write(SQLRunnable task) {
        io.execute(() -> {
            try {
                ensureConnected();
                task.run();
            } catch (SQLException e) {
                conn = null; // force reconnect on next operation
                e.printStackTrace();
            }
        });
    }

    protected <T> T read(Callable<T> task) {
        try {
            return io.submit(() -> {
                try {
                    ensureConnected();
                    return task.call();
                } catch (Exception e) {
                    conn = null;
                    throw e;
                }
            }).get();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    // ── Bulk reads ────────────────────────────────────────────────────────────

    /** Load all wards from DB. Used by WardManager.loadAll(). */
    public List<Ward> loadWards() {
        return read(() -> {
            List<Ward> list = new ArrayList<>();
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT * FROM wards")) {
                while (rs.next()) {
                    UUID id      = UUID.fromString(rs.getString("id"));
                    UUID owner   = UUID.fromString(rs.getString("owner"));
                    String world = rs.getString("world");
                    int x        = rs.getInt("x");
                    int y        = rs.getInt("y");
                    int z        = rs.getInt("z");
                    int radius   = rs.getInt("radius");
                    String tier  = rs.getString("tier");
                    boolean notify = rs.getInt("notify") != 0;
                    long created = rs.getLong("created_at");
                    String name  = rs.getString("name");
                    Ward ward    = new Ward(id, owner, world, x, y, z, radius, tier, notify, created);
                    ward.setName(name != null ? name : "");
                    list.add(ward);
                }
            }
            return list;
        });
    }

    @Override
    public Map<UUID, Map<UUID, String>> loadAllMembers() {
        return read(() -> {
            Map<UUID, Map<UUID, String>> map = new HashMap<>();
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT ward_id, uuid, trust_level FROM members")) {
                while (rs.next()) {
                    UUID wardId   = UUID.fromString(rs.getString(1));
                    UUID memberId = UUID.fromString(rs.getString(2));
                    String trust  = rs.getString(3);
                    if (trust == null) trust = "member";
                    map.computeIfAbsent(wardId, k -> new HashMap<>()).put(memberId, trust);
                }
            }
            return map;
        });
    }

    @Override
    public Map<UUID, Set<String>> loadAllFeatures() {
        return read(() -> {
            Map<UUID, Set<String>> map = new HashMap<>();
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT ward_id, feature FROM ward_features")) {
                while (rs.next()) {
                    UUID wardId = UUID.fromString(rs.getString(1));
                    String feat = rs.getString(2);
                    map.computeIfAbsent(wardId, k -> new HashSet<>()).add(feat);
                }
            }
            return map;
        });
    }

    @Override
    public Map<UUID, Set<String>> loadAllFlags() {
        return read(() -> {
            Map<UUID, Set<String>> map = new HashMap<>();
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT ward_id, flag FROM ward_flags")) {
                while (rs.next()) {
                    UUID wardId = UUID.fromString(rs.getString(1));
                    String flag = rs.getString(2);
                    map.computeIfAbsent(wardId, k -> new HashSet<>()).add(flag);
                }
            }
            return map;
        });
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Override
    public List<String> recentLogs(UUID wardId, int limit) {
        return read(() -> {
            List<String> out = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT name, ts FROM logs WHERE ward_id=? ORDER BY ts DESC LIMIT ?")) {
                ps.setString(1, wardId.toString());
                ps.setInt(2, limit);
                ResultSet rs = ps.executeQuery();
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
                while (rs.next()) {
                    out.add(rs.getString(1) + " at " + sdf.format(new java.util.Date(rs.getLong(2))));
                }
            }
            return out;
        });
    }

    @Override
    public List<String> getFeatureLogs(UUID wardId, String featureId, int limit) {
        return read(() -> {
            List<String> out = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT message, ts FROM feature_logs WHERE ward_id=? AND feature=? ORDER BY ts DESC LIMIT ?")) {
                ps.setString(1, wardId.toString());
                ps.setString(2, featureId);
                ps.setInt(3, limit);
                ResultSet rs = ps.executeQuery();
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd HH:mm");
                while (rs.next()) {
                    out.add("[" + sdf.format(new java.util.Date(rs.getLong(2))) + "] " + rs.getString(1));
                }
            }
            return out;
        });
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    @Override
    public void saveWard(Ward w) {
        write(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "REPLACE INTO wards(id,owner,world,x,y,z,radius,tier,notify,created_at,name,entry_message) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")) {
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
                ps.setString(12, ""); // entry_message placeholder
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void saveFlag(UUID wardId, String flagId, boolean enabled) {
        write(() -> {
            if (enabled) {
                try (PreparedStatement ps = conn.prepareStatement(
                        insertOrIgnore() + " INTO ward_flags(ward_id,flag) VALUES(?,?)")) {
                    ps.setString(1, wardId.toString());
                    ps.setString(2, flagId);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM ward_flags WHERE ward_id=? AND flag=?")) {
                    ps.setString(1, wardId.toString());
                    ps.setString(2, flagId);
                    ps.executeUpdate();
                }
            }
        });
    }

    @Override
    public void updateOwner(UUID wardId, UUID newOwner) {
        write(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE wards SET owner=? WHERE id=?")) {
                ps.setString(1, newOwner.toString());
                ps.setString(2, wardId.toString());
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void deleteWard(UUID id) {
        write(() -> {
            for (String table : new String[]{"members", "logs", "ward_features", "feature_logs", "ward_flags"}) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM " + table + " WHERE ward_id=?")) {
                    ps.setString(1, id.toString());
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM wards WHERE id=?")) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void addMember(UUID wardId, UUID member, String trustLevel) {
        write(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    insertOrIgnore() + " INTO members(ward_id,uuid,trust_level) VALUES(?,?,?)")) {
                ps.setString(1, wardId.toString());
                ps.setString(2, member.toString());
                ps.setString(3, trustLevel != null ? trustLevel : "member");
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void removeMember(UUID wardId, UUID member) {
        write(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM members WHERE ward_id=? AND uuid=?")) {
                ps.setString(1, wardId.toString());
                ps.setString(2, member.toString());
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void setTrustLevel(UUID wardId, UUID memberId, String trustLevel) {
        write(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE members SET trust_level=? WHERE ward_id=? AND uuid=?")) {
                ps.setString(1, trustLevel);
                ps.setString(2, wardId.toString());
                ps.setString(3, memberId.toString());
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void logEntry(UUID wardId, UUID intruder, String name, long ts) {
        write(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO logs(ward_id,intruder,name,ts) VALUES(?,?,?,?)")) {
                ps.setString(1, wardId.toString());
                ps.setString(2, intruder.toString());
                ps.setString(3, name);
                ps.setLong(4, ts);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void saveFeature(UUID wardId, String featureId, boolean enabled) {
        write(() -> {
            if (enabled) {
                try (PreparedStatement ps = conn.prepareStatement(
                        insertOrIgnore() + " INTO ward_features(ward_id,feature) VALUES(?,?)")) {
                    ps.setString(1, wardId.toString());
                    ps.setString(2, featureId);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM ward_features WHERE ward_id=? AND feature=?")) {
                    ps.setString(1, wardId.toString());
                    ps.setString(2, featureId);
                    ps.executeUpdate();
                }
            }
        });
    }

    @Override
    public void logFeatureEvent(UUID wardId, String featureId, String message, long ts) {
        write(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO feature_logs(ward_id,feature,message,ts) VALUES(?,?,?,?)")) {
                ps.setString(1, wardId.toString());
                ps.setString(2, featureId);
                ps.setString(3, message);
                ps.setLong(4, ts);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void clearFeatureLogs(UUID wardId, String featureId) {
        write(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM feature_logs WHERE ward_id=? AND feature=?")) {
                ps.setString(1, wardId.toString());
                ps.setString(2, featureId);
                ps.executeUpdate();
            }
        });
    }
}
