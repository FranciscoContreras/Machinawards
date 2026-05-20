package com.machina.wards;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WardManager {

    private final MachinaWards plugin;
    private final DataStore store;
    private final Map<UUID, Ward> wards = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> byWorld = new ConcurrentHashMap<>();

    // Spatial chunk index: world → chunkKey → set of ward UUIDs
    private final Map<String, Map<Long, Set<UUID>>> chunkIndex = new ConcurrentHashMap<>();

    // Owner index: owner UUID → set of ward UUIDs
    private final Map<UUID, Set<UUID>> ownerIndex = new ConcurrentHashMap<>();

    // Name/shortId indexes: lowercase key → ward UUID (exact O(1) lookups)
    private final Map<String, UUID> shortIdIndex = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameIndex     = new ConcurrentHashMap<>();

    private final Map<UUID, Map<UUID, Long>> lastAlert = new ConcurrentHashMap<>();

    // Block position index: world → "bx,by,bz" → wardId (O(1) exact block lookup)
    private final Map<String, Map<String, UUID>> blockIndex = new ConcurrentHashMap<>();

    // Cached from config — avoids a config read on every block/move event
    private volatile boolean columnShape = true;

    public WardManager(MachinaWards plugin, DataStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    /** Call after (re)loading config to sync the shape cache. */
    public void reloadShape() {
        String shape = plugin.getConfig().getString("region.shape", "column")
                .toLowerCase(java.util.Locale.ROOT);
        columnShape = shape.equals("column");
    }

    public void loadAll() {
        wards.clear();
        byWorld.clear();
        chunkIndex.clear();
        blockIndex.clear();
        ownerIndex.clear();
        shortIdIndex.clear();
        nameIndex.clear();
        reloadShape();

        List<Ward> wardList = store.loadWards();
        // Four queries total instead of 4N
        Map<UUID, Map<UUID, String>> allMembers  = store.loadAllMembers();
        Map<UUID, Set<String>>       allFeatures = store.loadAllFeatures();
        Map<UUID, Set<String>>       allFlags    = store.loadAllFlags();

        for (Ward w : wardList) {
            wards.put(w.id(), w);
            byWorld.computeIfAbsent(w.world(), k -> ConcurrentHashMap.newKeySet()).add(w.id());
            indexChunk(w);
            indexBlock(w);
            indexOwner(w);
            indexName(w);

            Map<UUID, String> membersWithTrust = allMembers.get(w.id());
            if (membersWithTrust != null) {
                membersWithTrust.forEach((memberId, trustId) -> {
                    w.members().add(memberId);
                    w.setMemberTrust(memberId, TrustLevel.fromId(trustId));
                });
            }

            Set<String> features = allFeatures.get(w.id());
            if (features != null) w.enabledFeatures().addAll(features);

            Set<String> flags = allFlags.get(w.id());
            if (flags != null) w.enabledFlags().addAll(flags);
        }
    }

    /** Drain the async DB write queue and close the connection. */
    public void flush() {
        store.shutdown();
    }

    public void save(Ward w) { store.saveWard(w); }

    public void delete(UUID id) {
        Ward w = wards.remove(id);
        if (w != null) {
            Set<UUID> s = byWorld.get(w.world());
            if (s != null) s.remove(id);
            unindexChunk(w);
            unindexBlock(w);
            unindexOwner(w);
            unindexName(w);
        }
        store.deleteWard(id);
    }

    /** Add a member with MEMBER trust (backward-compatible default). */
    public void addMember(UUID wardId, UUID member) {
        addMember(wardId, member, TrustLevel.MEMBER);
    }

    public void addMember(UUID wardId, UUID member, TrustLevel level) {
        Ward w = wards.get(wardId);
        if (w == null) return;
        w.members().add(member);
        w.setMemberTrust(member, level);
        store.addMember(wardId, member, level.id());
        notifyMemberAdded(w, member, level);
    }

    private void notifyMemberAdded(Ward w, UUID memberId, TrustLevel level) {
        if (!plugin.getConfig().getBoolean("members.notify_on_add", true)) return;
        String wardLabel = w.name().isEmpty() ? w.shortId() : w.name();
        String msg = "&a[Ward: &f" + wardLabel + "&a] You were added as a &f" + level.displayName() + "&a.";
        org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayer(memberId);
        if (target != null) { target.sendMessage(Msg.c(msg)); return; }
        plugin.queueNotification(memberId, msg);
    }

    private void notifyMemberRemoved(Ward w, UUID memberId) {
        if (!plugin.getConfig().getBoolean("members.notify_on_add", true)) return;
        String wardLabel = w.name().isEmpty() ? w.shortId() : w.name();
        String msg = "&c[Ward: &f" + wardLabel + "&c] You were removed as a member.";
        org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayer(memberId);
        if (target != null) target.sendMessage(Msg.c(msg));
    }

    public void setTrustLevel(UUID wardId, UUID memberId, TrustLevel level) {
        Ward w = wards.get(wardId);
        if (w == null) return;
        w.setMemberTrust(memberId, level);
        store.setTrustLevel(wardId, memberId, level.id());
    }

    public void removeMember(UUID wardId, UUID member) {
        Ward w = wards.get(wardId);
        if (w == null) return;
        w.members().remove(member);
        w.removeMemberTrust(member);
        store.removeMember(wardId, member);
        notifyMemberRemoved(w, member);
    }

    /** Rename a ward and keep name index consistent. */
    public void renameWard(UUID wardId, String newName) {
        Ward w = wards.get(wardId);
        if (w == null) return;
        if (!w.name().isEmpty()) nameIndex.remove(w.name().toLowerCase(java.util.Locale.ROOT));
        w.setName(newName);
        if (!newName.isEmpty()) nameIndex.put(newName.toLowerCase(java.util.Locale.ROOT), wardId);
        store.saveWard(w);
    }

    public List<String> recentLogs(UUID wardId, int limit) { return store.recentLogs(wardId, limit); }

    public void logEntry(UUID wardId, UUID intruder, String name) {
        store.logEntry(wardId, intruder, name, System.currentTimeMillis());
    }

    public void add(Ward w) {
        wards.put(w.id(), w);
        byWorld.computeIfAbsent(w.world(), k -> ConcurrentHashMap.newKeySet()).add(w.id());
        indexChunk(w);
        indexBlock(w);
        indexOwner(w);
        indexName(w);
        save(w);
    }

    public Ward get(UUID id) { return wards.get(id); }

    /** O(1) for exact shortId or name; falls back to linear scan for UUID prefix / name prefix. */
    public Ward findWard(String input) {
        if (input == null || input.isEmpty()) return null;
        String lower = input.toLowerCase(java.util.Locale.ROOT);
        // Exact shortId
        UUID id = shortIdIndex.get(lower);
        if (id != null) return wards.get(id);
        // Exact name
        id = nameIndex.get(lower);
        if (id != null) return wards.get(id);
        // UUID prefix — linear scan (rare admin use)
        for (Ward w : wards.values()) if (w.id().toString().startsWith(lower)) return w;
        // Name prefix — linear scan (rare)
        for (Ward w : wards.values())
            if (!w.name().isEmpty() && w.name().toLowerCase(java.util.Locale.ROOT).startsWith(lower)) return w;
        return null;
    }

    public void transferOwner(UUID wardId, UUID newOwner) {
        Ward w = wards.get(wardId);
        if (w == null) return;
        unindexOwner(w);
        w.setOwner(newOwner);
        indexOwner(w);
        store.updateOwner(wardId, newOwner);
    }

    public Collection<Ward> all() { return Collections.unmodifiableCollection(wards.values()); }

    /** All wards owned by the given player. O(owned) instead of O(all). */
    public Collection<Ward> wardsOwnedBy(UUID owner) {
        Set<UUID> ids = ownerIndex.get(owner);
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        List<Ward> result = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            Ward w = wards.get(id);
            if (w != null) result.add(w);
        }
        return result;
    }

    public Set<UUID> idsInWorld(String world) { return byWorld.getOrDefault(world, Collections.emptySet()); }

    public boolean contains(Ward w, Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(w.world())) return false;

        int bx = loc.getBlockX();
        int bz = loc.getBlockZ();

        boolean insideXZ = Math.abs(bx - w.bx()) <= w.radius() && Math.abs(bz - w.bz()) <= w.radius();
        if (!insideXZ) return false;
        if (columnShape) return true;
        return Math.abs(loc.getBlockY() - w.by()) <= w.radius();
    }

    public Ward findAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        String world = loc.getWorld().getName();
        Set<UUID> candidates = getCandidates(world, loc.getBlockX(), loc.getBlockZ());
        for (UUID id : candidates) {
            Ward w = wards.get(id);
            if (w != null && contains(w, loc)) return w;
        }
        return null;
    }

    public List<Ward> findAllAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return Collections.emptyList();
        String world = loc.getWorld().getName();
        List<Ward> result = new ArrayList<>();
        Set<UUID> candidates = getCandidates(world, loc.getBlockX(), loc.getBlockZ());
        for (UUID id : candidates) {
            Ward w = wards.get(id);
            if (w != null && contains(w, loc)) result.add(w);
        }
        return result;
    }

    public boolean overlaps(String world, int x, int z, int radius, UUID excludeId) {
        Map<Long, Set<UUID>> worldChunks = chunkIndex.get(world);
        if (worldChunks == null) return false;
        Set<UUID> seen = new HashSet<>();
        int cxMin = (x - radius) >> 4;
        int cxMax = (x + radius) >> 4;
        int czMin = (z - radius) >> 4;
        int czMax = (z + radius) >> 4;
        for (int cx = cxMin; cx <= cxMax; cx++) {
            for (int cz = czMin; cz <= czMax; cz++) {
                Set<UUID> bucket = worldChunks.get(chunkKey(cx, cz));
                if (bucket == null) continue;
                for (UUID id : bucket) {
                    if (id.equals(excludeId) || !seen.add(id)) continue;
                    Ward w = wards.get(id);
                    if (w == null) continue;
                    if (Math.abs(w.bx() - x) <= w.radius() + radius + 1 &&
                        Math.abs(w.bz() - z) <= w.radius() + radius + 1) return true;
                }
            }
        }
        return false;
    }

    /** Ward UUIDs registered in a specific chunk — used by the particle task. */
    public Set<UUID> wardsInChunk(String world, int cx, int cz) {
        Map<Long, Set<UUID>> worldChunks = chunkIndex.get(world);
        if (worldChunks == null) return Collections.emptySet();
        Set<UUID> bucket = worldChunks.get(chunkKey(cx, cz));
        return bucket != null ? bucket : Collections.emptySet();
    }

    public void setFeature(UUID wardId, WardFeature f, boolean enabled) {
        Ward w = wards.get(wardId);
        if (w == null) return;
        w.setFeature(f, enabled);
        store.saveFeature(wardId, f.id(), enabled);
    }

    public void logFeatureEvent(UUID wardId, WardFeature f, String message) {
        store.logFeatureEvent(wardId, f.id(), message, System.currentTimeMillis());
    }

    public List<String> getFeatureLogs(UUID wardId, WardFeature f, int limit) {
        return store.getFeatureLogs(wardId, f.id(), limit);
    }

    public void clearFeatureLogs(UUID wardId, WardFeature f) {
        store.clearFeatureLogs(wardId, f.id());
    }

    public void setFlag(UUID wardId, WardFlag flag, boolean enabled) {
        Ward w = wards.get(wardId);
        if (w == null) return;
        w.setFlag(flag, enabled);
        store.saveFlag(wardId, flag.id(), enabled);
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

    public long lastAlertAt(UUID wardId, UUID intruder) {
        return lastAlert.computeIfAbsent(wardId, k -> new HashMap<>()).getOrDefault(intruder, 0L);
    }

    public void setLastAlert(UUID wardId, UUID intruder, long when) {
        Map<UUID, Long> wardAlerts = lastAlert.computeIfAbsent(wardId, k -> new HashMap<>());
        wardAlerts.put(intruder, when);
        // Prune stale entries if the map has grown large
        if (wardAlerts.size() > 200) {
            long cutoff = when - 2 * plugin.getConfig().getLong("alerts.cooldown_ms", 90_000);
            wardAlerts.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        }
    }

    public int countOwned(Player p) {
        Set<UUID> owned = ownerIndex.get(p.getUniqueId());
        return owned == null ? 0 : owned.size();
    }

    public String tierOf(ItemStack stack) {
        if (stack == null) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(plugin.tierKey(), PersistentDataType.STRING);
    }

    public int maxMembers(Ward w) {
        return plugin.getConfig().getInt("wards." + w.tier() + ".max_members", -1);
    }

    public boolean allowedWorld(String world) {
        var list = plugin.getConfig().getStringList("worlds");
        if (list == null || list.isEmpty()) return true;
        return list.contains(world);
    }

    /** Copy all in-memory ward data to a target store. Blocks until all writes are flushed. */
    public int migrate(DataStore target) {
        int count = 0;
        for (Ward w : wards.values()) {
            target.saveWard(w);
            for (UUID m : w.members())           target.addMember(w.id(), m, w.getMemberTrust(m).id());
            for (String f : w.enabledFeatures()) target.saveFeature(w.id(), f, true);
            for (String f : w.enabledFlags())    target.saveFlag(w.id(), f, true);
            count++;
        }
        target.shutdown(); // drain the async write queue before returning
        return count;
    }

    /** Server-wide ward statistics. */
    public String[] stats() {
        int total = wards.size();
        Map<String, Integer> perWorld = new java.util.TreeMap<>();
        for (Ward w : wards.values())
            perWorld.merge(w.world(), 1, Integer::sum);
        StringBuilder worlds = new StringBuilder();
        perWorld.forEach((wld, cnt) -> {
            if (worlds.length() > 0) worlds.append(", ");
            worlds.append(wld).append('(').append(cnt).append(')');
        });
        int totalMembers = 0;
        for (Ward w : wards.values()) totalMembers += w.members().size();
        return new String[]{
            "&7Total wards: &f" + total,
            "&7Per world: &f"   + (worlds.length() == 0 ? "none" : worlds),
            "&7Total member slots used: &f" + totalMembers,
            "&7Owners: &f" + ownerIndex.size()
        };
    }

    public boolean withinHeight(int y) {
        int min = plugin.getConfig().getInt("height.min_y", -64);
        int max = plugin.getConfig().getInt("height.max_y", 320);
        return y >= min && y <= max;
    }

    // ── Block position index ─────────────────────────────────────────────────

    private static String blockKey(int x, int y, int z) { return x + "," + y + "," + z; }

    private void indexBlock(Ward w) {
        blockIndex.computeIfAbsent(w.world(), k -> new ConcurrentHashMap<>())
                  .put(blockKey(w.bx(), w.by(), w.bz()), w.id());
    }

    private void unindexBlock(Ward w) {
        Map<String, UUID> worldBlocks = blockIndex.get(w.world());
        if (worldBlocks != null) worldBlocks.remove(blockKey(w.bx(), w.by(), w.bz()));
    }

    /** O(1) lookup for the ward whose center block is at exactly this location. */
    public Ward findWardByBlock(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        Map<String, UUID> worldBlocks = blockIndex.get(loc.getWorld().getName());
        if (worldBlocks == null) return null;
        UUID id = worldBlocks.get(blockKey(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        return id != null ? wards.get(id) : null;
    }

    /** Wards whose center block is within radius of (x, z) in the given world. */
    public List<Ward> nearbyWards(String world, int x, int z, int radius) {
        Set<UUID> seen = new HashSet<>();
        List<Ward> result = new ArrayList<>();
        int cxMin = (x - radius) >> 4;
        int cxMax = (x + radius) >> 4;
        int czMin = (z - radius) >> 4;
        int czMax = (z + radius) >> 4;
        for (int cx = cxMin; cx <= cxMax; cx++) {
            for (int cz = czMin; cz <= czMax; cz++) {
                for (UUID id : wardsInChunk(world, cx, cz)) {
                    if (!seen.add(id)) continue;
                    Ward w = wards.get(id);
                    if (w == null) continue;
                    double dx = w.bx() - x;
                    double dz = w.bz() - z;
                    if (dx * dx + dz * dz <= (double) radius * radius) result.add(w);
                }
            }
        }
        return result;
    }

    // ── Chunk index ──────────────────────────────────────────────────────────

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private void indexChunk(Ward w) {
        Map<Long, Set<UUID>> worldChunks = chunkIndex
                .computeIfAbsent(w.world(), k -> new ConcurrentHashMap<>());
        int cxMin = (w.bx() - w.radius()) >> 4;
        int cxMax = (w.bx() + w.radius()) >> 4;
        int czMin = (w.bz() - w.radius()) >> 4;
        int czMax = (w.bz() + w.radius()) >> 4;
        for (int cx = cxMin; cx <= cxMax; cx++) {
            for (int cz = czMin; cz <= czMax; cz++) {
                worldChunks.computeIfAbsent(chunkKey(cx, cz), k -> ConcurrentHashMap.newKeySet()).add(w.id());
            }
        }
    }

    private void unindexChunk(Ward w) {
        Map<Long, Set<UUID>> worldChunks = chunkIndex.get(w.world());
        if (worldChunks == null) return;
        int cxMin = (w.bx() - w.radius()) >> 4;
        int cxMax = (w.bx() + w.radius()) >> 4;
        int czMin = (w.bz() - w.radius()) >> 4;
        int czMax = (w.bz() + w.radius()) >> 4;
        for (int cx = cxMin; cx <= cxMax; cx++) {
            for (int cz = czMin; cz <= czMax; cz++) {
                Set<UUID> bucket = worldChunks.get(chunkKey(cx, cz));
                if (bucket != null) bucket.remove(w.id());
            }
        }
    }

    private Set<UUID> getCandidates(String world, int bx, int bz) {
        Map<Long, Set<UUID>> worldChunks = chunkIndex.get(world);
        if (worldChunks == null) return Collections.emptySet();
        Set<UUID> bucket = worldChunks.get(chunkKey(bx >> 4, bz >> 4));
        return bucket != null ? bucket : Collections.emptySet();
    }

    // ── Owner index ──────────────────────────────────────────────────────────

    private void indexOwner(Ward w) {
        ownerIndex.computeIfAbsent(w.owner(), k -> ConcurrentHashMap.newKeySet()).add(w.id());
    }

    private void unindexOwner(Ward w) {
        Set<UUID> owned = ownerIndex.get(w.owner());
        if (owned != null) owned.remove(w.id());
    }

    // ── Name / shortId indexes ───────────────────────────────────────────────

    private void indexName(Ward w) {
        shortIdIndex.put(w.shortId().toLowerCase(java.util.Locale.ROOT), w.id());
        if (!w.name().isEmpty())
            nameIndex.put(w.name().toLowerCase(java.util.Locale.ROOT), w.id());
    }

    private void unindexName(Ward w) {
        shortIdIndex.remove(w.shortId().toLowerCase(java.util.Locale.ROOT));
        if (!w.name().isEmpty())
            nameIndex.remove(w.name().toLowerCase(java.util.Locale.ROOT));
    }
}
