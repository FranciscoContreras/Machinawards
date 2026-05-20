package com.machina.wards;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Ward {
    private final UUID id;
    private volatile UUID owner;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final int radius;
    private final String tier;
    private volatile boolean notify;
    private volatile String name;
    private volatile String entryMessage;
    private final long createdAt;
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();
    private final Set<String> enabledFeatures = ConcurrentHashMap.newKeySet();
    private final Set<String> enabledFlags = ConcurrentHashMap.newKeySet();
    private final Map<UUID, TrustLevel> memberTrust = new ConcurrentHashMap<>();

    public Ward(UUID id, UUID owner, String world, int x, int y, int z, int radius, String tier, boolean notify, long createdAt) {
        this.id = id;
        this.owner = owner;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.tier = tier;
        this.notify = notify;
        this.createdAt = createdAt;
        this.name = "";
        this.entryMessage = "";
    }

    public UUID id() { return id; }
    public UUID owner() { return owner; }
    public void setOwner(UUID o) { this.owner = o; }
    public String world() { return world; }
    public int bx() { return x; }
    public int by() { return y; }
    public int bz() { return z; }
    public int radius() { return radius; }
    public String tier() { return tier; }
    public boolean notifyEnabled() { return notify; }
    public void setNotify(boolean n) { this.notify = n; }
    public String name() { return name == null ? "" : name; }
    public void setName(String n) { this.name = n == null ? "" : n; }
    public String entryMessage() { return entryMessage == null ? "" : entryMessage; }
    public void setEntryMessage(String m) { this.entryMessage = m == null ? "" : m; }
    public long createdAt() { return createdAt; }
    public Set<UUID> members() { return members; }
    public Set<String> enabledFeatures() { return enabledFeatures; }
    public boolean hasFeature(WardFeature f) { return enabledFeatures.contains(f.id()); }
    public void setFeature(WardFeature f, boolean on) {
        if (on) enabledFeatures.add(f.id()); else enabledFeatures.remove(f.id());
    }
    public Set<String> enabledFlags() { return enabledFlags; }
    public boolean hasFlag(WardFlag flag) { return enabledFlags.contains(flag.id()); }
    public void setFlag(WardFlag flag, boolean on) {
        if (on) enabledFlags.add(flag.id()); else enabledFlags.remove(flag.id());
    }
    /** First 8 chars of the UUID — human-readable short ID, e.g. A3F2B901 */
    public String shortId() { return id.toString().substring(0, 8).toUpperCase(); }

    /** Returns trust level. Owner always returns MEMBER; unknown UUIDs default to MEMBER. */
    public TrustLevel getMemberTrust(UUID uuid) {
        if (uuid.equals(owner)) return TrustLevel.MEMBER;
        return memberTrust.getOrDefault(uuid, TrustLevel.MEMBER);
    }

    public void setMemberTrust(UUID uuid, TrustLevel level) {
        memberTrust.put(uuid, level);
    }

    public void removeMemberTrust(UUID uuid) {
        memberTrust.remove(uuid);
    }

    public Location loc() {
        return new Location(Bukkit.getWorld(world), x, y, z);
    }
}
