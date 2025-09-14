package com.machina.wards;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WardManager {

    private final MachinaWards plugin;
    private final SqliteStore store;
    private final Map<UUID, Ward> wards = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> byWorld = new ConcurrentHashMap<>();

    private final Map<UUID, Map<UUID, Long>> lastAlert = new ConcurrentHashMap<>();

    public WardManager(MachinaWards plugin, SqliteStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public void loadAll() {
        wards.clear();
        byWorld.clear();
        for (Ward w : store.loadWards()) {
            wards.put(w.id(), w);
            byWorld.computeIfAbsent(w.world(), k -> ConcurrentHashMap.newKeySet()).add(w.id());
            for (UUID m : store.loadMembers(w.id())) w.members().add(m);
        }
    }

    public void flush() {}

    public void save(Ward w) { store.saveWard(w); }

    public void delete(UUID id) {
        Ward w = wards.remove(id);
        if (w != null) {
            Set<UUID> s = byWorld.get(w.world());
            if (s != null) s.remove(id);
        }
        store.deleteWard(id);
    }

    public void addMember(UUID wardId, UUID member) {
        Ward w = wards.get(wardId);
        if (w == null) return;
        w.members().add(member);
        store.addMember(wardId, member);
    }

    public void removeMember(UUID wardId, UUID member) {
        Ward w = wards.get(wardId);
        if (w == null) return;
        w.members().remove(member);
        store.removeMember(wardId, member);
    }

    public java.util.List<String> recentLogs(UUID wardId, int limit) { return store.recentLogs(wardId, limit); }

    public void logEntry(UUID wardId, UUID intruder, String name) { store.logEntry(wardId, intruder, name, System.currentTimeMillis()); }

    public void add(Ward w) {
        wards.put(w.id(), w);
        byWorld.computeIfAbsent(w.world(), k -> ConcurrentHashMap.newKeySet()).add(w.id());
        save(w);
    }

    public Ward get(UUID id) { return wards.get(id); }

    public Collection<Ward> all() { return Collections.unmodifiableCollection(wards.values()); }

    public Set<UUID> idsInWorld(String world) { return byWorld.getOrDefault(world, Collections.emptySet()); }

    public boolean contains(Ward w, Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(w.world())) return false;

        FileConfiguration cfg = plugin.getConfig();
        String shape = cfg.getString("region.shape", "column").toLowerCase(java.util.Locale.ROOT);
        boolean column = shape.equals("column");

        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();

        boolean insideXZ = Math.abs(bx - w.bx()) <= w.radius() && Math.abs(bz - w.bz()) <= w.radius();
        if (!insideXZ) return false;
        if (column) return true;
        return Math.abs(by - w.by()) <= w.radius();
    }

    public Ward findAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        String world = loc.getWorld().getName();
        for (UUID id : idsInWorld(world)) {
            Ward w = wards.get(id);
            if (w != null && contains(w, loc)) return w;
        }
        return null;
    }

    public int getLimit(Player p) {
        int max = 0;
        for (var pai : p.getEffectivePermissions()) {
            String perm = pai.getPermission().toLowerCase(java.util.Locale.ROOT);
            if (perm.startsWith("wards.player.")) {
                try {
                    int v = Integer.parseInt(perm.substring("wards.player.".length()));
                    if (v > max) max = v;
                } catch (NumberFormatException ignore) {}
            }
        }
        return max;
    }

    public long lastAlertAt(UUID wardId, UUID intruder) { return lastAlert.computeIfAbsent(wardId, k -> new java.util.HashMap<>()).getOrDefault(intruder, 0L); }

    public void setLastAlert(UUID wardId, UUID intruder, long when) { lastAlert.computeIfAbsent(wardId, k -> new java.util.HashMap<>()).put(intruder, when); }

    public int countOwned(Player p) {
        int c = 0;
        UUID id = p.getUniqueId();
        for (Ward w : wards.values()) if (w.owner().equals(id)) c++;
        return c;
    }

    public String tierOf(ItemStack stack) {
        if (stack == null) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String t = pdc.get(plugin.tierKey(), PersistentDataType.STRING);
        if (t != null) return t;
        return null;
    }

    public boolean allowedWorld(String world) {
        var list = plugin.getConfig().getStringList("worlds");
        if (list == null || list.isEmpty()) return true;
        return list.contains(world);
    }

    public boolean withinHeight(int y) {
        int min = plugin.getConfig().getInt("height.min_y", -64);
        int max = plugin.getConfig().getInt("height.max_y", 320);
        return y >= min && y <= max;
    }
}
