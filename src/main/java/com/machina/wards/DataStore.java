package com.machina.wards;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Database backend interface. Implementations: SqliteStore, MysqlStore. */
public interface DataStore {

    void init();
    void shutdown();

    // ── Bulk reads (called once on loadAll) ──────────────────────────────────
    /** Returns wardId → (memberId → trustLevel string) */
    Map<UUID, Map<UUID, String>> loadAllMembers();
    Map<UUID, Set<String>> loadAllFeatures();

    // ── Reads ────────────────────────────────────────────────────────────────
    List<String> recentLogs(UUID wardId, int limit);
    List<String> getFeatureLogs(UUID wardId, String featureId, int limit);

    // ── Bulk read ────────────────────────────────────────────────────────────
    Map<UUID, Set<String>> loadAllFlags();

    // ── Writes (async) ───────────────────────────────────────────────────────
    void saveWard(Ward w);
    void saveFlag(UUID wardId, String flagId, boolean enabled);
    void updateOwner(UUID wardId, UUID newOwner);
    void deleteWard(UUID id);
    /** Add member with trust level. trustLevel = "member" or "visitor". */
    void addMember(UUID wardId, UUID member, String trustLevel);
    void removeMember(UUID wardId, UUID member);
    void setTrustLevel(UUID wardId, UUID memberId, String trustLevel);
    void logEntry(UUID wardId, UUID intruder, String name, long ts);
    void saveFeature(UUID wardId, String featureId, boolean enabled);
    void logFeatureEvent(UUID wardId, String featureId, String message, long ts);
    void clearFeatureLogs(UUID wardId, String featureId);
}
