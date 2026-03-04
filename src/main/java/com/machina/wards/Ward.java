package com.machina.wards;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Ward {
    private final UUID id;
    private final UUID owner;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final int radius;
    private final String tier;
    private volatile boolean notify;
    private volatile String name;
    private final long createdAt;
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();
    private final Set<String> enabledFeatures = ConcurrentHashMap.newKeySet();

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
    }

    public UUID id() { return id; }
    public UUID owner() { return owner; }
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
    public long createdAt() { return createdAt; }
    public Set<UUID> members() { return members; }
    public Set<String> enabledFeatures() { return enabledFeatures; }
    public boolean hasFeature(WardFeature f) { return enabledFeatures.contains(f.id()); }
    public void setFeature(WardFeature f, boolean on) {
        if (on) enabledFeatures.add(f.id()); else enabledFeatures.remove(f.id());
    }
    /** First 8 chars of the UUID — human-readable short ID, e.g. A3F2B901 */
    public String shortId() { return id.toString().substring(0, 8).toUpperCase(); }

    public Location loc() {
        return new Location(Bukkit.getWorld(world), x, y, z);
    }
}
