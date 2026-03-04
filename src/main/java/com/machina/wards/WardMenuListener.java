package com.machina.wards;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WardMenuListener implements Listener {

    private static final String TITLE_MAIN    = "Ward Menu";
    private static final String TITLE_MEMBERS = "Ward Members";
    private static final String TITLE_REMOVE  = "Remove Member";

    private final MachinaWards plugin;
    private final WardManager manager;
    private final NamespacedKey wardKey;
    private final NamespacedKey actionKey;
    private final NamespacedKey memberKey;

    private static final Map<UUID, UUID> pendingAdd    = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> pendingRename = new ConcurrentHashMap<>();

    public WardMenuListener(MachinaWards plugin, WardManager manager,
                            NamespacedKey wardKey, NamespacedKey actionKey, NamespacedKey memberKey) {
        this.plugin = plugin;
        this.manager = manager;
        this.wardKey = wardKey;
        this.actionKey = actionKey;
        this.memberKey = memberKey;
    }

    // ── Main menu ────────────────────────────────────────────────────────────

    public static void openMain(MachinaWards plugin, Player p, Ward w) {
        Inventory inv = Bukkit.createInventory(p, 27, ChatColor.DARK_AQUA + TITLE_MAIN);
        String nameLabel = w.name().isEmpty() ? "&6Rename" : "&6Rename &7(" + w.name() + ")";
        inv.setItem(10, item(plugin, w, Material.NAME_TAG,    nameLabel,         "rename"));
        inv.setItem(11, item(plugin, w, Material.BELL,        "&eToggle alerts",  "toggle_alerts"));
        inv.setItem(12, item(plugin, w, Material.PLAYER_HEAD, "&bMembers",        "members"));
        inv.setItem(13, item(plugin, w, Material.PAPER,       "&aHistory",        "history"));
        inv.setItem(14, item(plugin, w, Material.SPYGLASS,    "&dShow Radius",    "show_radius"));
        inv.setItem(15, item(plugin, w, Material.EMERALD,     "&aAdd member",     "add_member"));
        inv.setItem(16, item(plugin, w, Material.BARRIER,     "&cRemove member",  "remove_member"));
        // Show Ward Intelligence button only for tiers that have features configured
        if (plugin.getConfig().isList("wards." + w.tier() + ".features")) {
            inv.setItem(22, item(plugin, w, Material.NETHER_STAR, "&5\u2726 Ward Intelligence", "features"));
        }
        p.openInventory(inv);
    }

    private static ItemStack item(MachinaWards plugin, Ward w, Material mat, String name, String action) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        m.setDisplayName(Msg.c(name));
        m.getPersistentDataContainer().set(plugin.tierKey(),   PersistentDataType.STRING, w.id().toString());
        m.getPersistentDataContainer().set(plugin.actionKey(), PersistentDataType.STRING, action);
        it.setItemMeta(m);
        return it;
    }

    // ── Members list (view only) ──────────────────────────────────────────────

    private void openMembersList(Player p, Ward w) {
        if (w.members().isEmpty()) {
            p.sendMessage(Msg.c("&7This ward has no members."));
            return;
        }
        int size = Math.min(6, (int) Math.ceil(w.members().size() / 9.0)) * 9;
        Inventory inv = Bukkit.createInventory(p, size, ChatColor.AQUA + TITLE_MEMBERS);
        for (UUID memberId : w.members()) {
            inv.addItem(memberHead(memberId, w, false));
        }
        p.openInventory(inv);
    }

    // ── Remove member (click to remove) ──────────────────────────────────────

    private void openRemoveMembers(Player p, Ward w) {
        if (w.members().isEmpty()) {
            p.sendMessage(Msg.c("&7No members to remove."));
            return;
        }
        int size = Math.min(6, (int) Math.ceil(w.members().size() / 9.0)) * 9;
        Inventory inv = Bukkit.createInventory(p, size, ChatColor.RED + TITLE_REMOVE);
        for (UUID memberId : w.members()) {
            inv.addItem(memberHead(memberId, w, true));
        }
        p.openInventory(inv);
    }

    private ItemStack memberHead(UUID memberId, Ward w, boolean forRemoval) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(memberId);
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        if (sm == null) return head;
        sm.setOwningPlayer(op);
        String name = op.getName() != null ? op.getName() : memberId.toString();
        sm.setDisplayName(forRemoval ? Msg.c("&c" + name) : Msg.c("&f" + name));
        sm.getPersistentDataContainer().set(wardKey,   PersistentDataType.STRING, w.id().toString());
        sm.getPersistentDataContainer().set(memberKey, PersistentDataType.STRING, memberId.toString());
        head.setItemMeta(sm);
        return head;
    }

    // ── Click handler ─────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        HumanEntity he = e.getWhoClicked();
        if (!(he instanceof Player p)) return;
        String title = ChatColor.stripColor(e.getView().getTitle());
        if (title == null) return;

        if (title.equalsIgnoreCase(TITLE_MAIN)) {
            handleMainClick(e, p);
        } else if (title.equalsIgnoreCase(TITLE_MEMBERS)) {
            e.setCancelled(true); // view only
        } else if (title.equalsIgnoreCase(TITLE_REMOVE)) {
            handleRemoveClick(e, p);
        }
    }

    private void handleMainClick(InventoryClickEvent e, Player p) {
        e.setCancelled(true);
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;

        String wardId = it.getItemMeta().getPersistentDataContainer().get(wardKey,   PersistentDataType.STRING);
        String action  = it.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (wardId == null || action == null) return;

        Ward w = manager.get(UUID.fromString(wardId));
        if (w == null) { p.closeInventory(); return; }

        switch (action) {
            case "toggle_alerts" -> {
                w.setNotify(!w.notifyEnabled());
                manager.save(w);
                p.sendMessage(Msg.c("&7Alerts: " + (w.notifyEnabled() ? "&aON" : "&cOFF")));
            }
            case "members" -> {
                p.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> openMembersList(p, w));
            }
            case "history" -> {
                var lines = manager.recentLogs(w.id(), 20);
                if (lines.isEmpty()) p.sendMessage(Msg.c("&7No recent entries."));
                for (String s : lines) p.sendMessage(Msg.c("&7" + s));
            }
            case "rename" -> {
                pendingRename.put(p.getUniqueId(), w.id());
                p.sendMessage(Msg.c("&eType a new name for this ward in chat."));
                p.closeInventory();
            }
            case "add_member" -> {
                int max = manager.maxMembers(w);
                if (max >= 0 && w.members().size() >= max) {
                    p.sendMessage(Msg.c("&cThis ward has reached its member limit (" + max + ")."));
                    return;
                }
                pendingAdd.put(p.getUniqueId(), w.id());
                p.sendMessage(Msg.c("&eType a player name in chat to add as member."));
                p.closeInventory();
            }
            case "remove_member" -> {
                p.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> openRemoveMembers(p, w));
            }
            case "show_radius" -> {
                p.closeInventory();
                p.sendMessage(Msg.c("&dShowing ward boundary for &f10 &dseconds."));
                new BukkitRunnable() {
                    int elapsed = 0;
                    @Override public void run() {
                        if (elapsed >= 200 || !p.isOnline()) { cancel(); return; }
                        drawBoundary(plugin, p, w);
                        elapsed += 5;
                    }
                }.runTaskTimer(plugin, 0L, 5L);
            }
            case "features" -> {
                p.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> SuperWardMenuListener.openFeatureList(plugin, p, w));
            }
        }
    }

    private void handleRemoveClick(InventoryClickEvent e, Player p) {
        e.setCancelled(true);
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;

        String wardIdStr   = it.getItemMeta().getPersistentDataContainer().get(wardKey,   PersistentDataType.STRING);
        String memberIdStr = it.getItemMeta().getPersistentDataContainer().get(memberKey, PersistentDataType.STRING);
        if (wardIdStr == null || memberIdStr == null) return;

        Ward w = manager.get(UUID.fromString(wardIdStr));
        if (w == null) { p.closeInventory(); return; }

        UUID memberId = UUID.fromString(memberIdStr);
        manager.removeMember(w.id(), memberId);

        OfflinePlayer op = Bukkit.getOfflinePlayer(memberId);
        String name = op.getName() != null ? op.getName() : memberIdStr;
        p.sendMessage(Msg.c("&aRemoved &f" + name + "&a from members."));

        // Refresh or close
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (w.members().isEmpty()) {
                p.closeInventory();
            } else {
                openRemoveMembers(p, w);
            }
        });
    }

    // ── Radius visualizer ────────────────────────────────────────────────────

    private static void drawBoundary(MachinaWards plugin, Player p, Ward w) {
        World world = Bukkit.getWorld(w.world());
        if (world == null) return;

        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(180, 0, 255), 0.85f);
        String shape = plugin.getConfig().getString("region.shape", "column")
                .toLowerCase(java.util.Locale.ROOT);

        int wardY   = w.by();
        int playerY = p.getLocation().getBlockY();

        // Always draw at ward block level and at the player's current Y
        drawSquare(world, w.bx(), w.bz(), w.radius(), wardY,   dust);
        if (playerY != wardY)
            drawSquare(world, w.bx(), w.bz(), w.radius(), playerY, dust);

        // For sphere mode also show the top and bottom caps + vertical corners
        if (shape.equals("sphere")) {
            int r = w.radius();
            drawSquare(world, w.bx(), w.bz(), r, wardY + r, dust);
            drawSquare(world, w.bx(), w.bz(), r, wardY - r, dust);
            for (int dy = -r; dy <= r; dy += 2) {
                for (int[] c : new int[][]{{-r, -r}, {-r, r}, {r, -r}, {r, r}}) {
                    world.spawnParticle(Particle.DUST,
                            w.bx() + c[0] + 0.5, wardY + dy + 0.5, w.bz() + c[1] + 0.5,
                            1, 0, 0, 0, 0, dust);
                }
            }
        }
    }

    private static void drawSquare(World world, int cx, int cz, int r, int y, Particle.DustOptions dust) {
        double yd = y + 0.5;
        // North (z = cz-r) and South (z = cz+r) walls
        for (int dx = -r; dx <= r; dx++) {
            world.spawnParticle(Particle.DUST, cx + dx + 0.5, yd, cz - r + 0.5, 1, 0, 0, 0, 0, dust);
            world.spawnParticle(Particle.DUST, cx + dx + 0.5, yd, cz + r + 0.5, 1, 0, 0, 0, 0, dust);
        }
        // West (x = cx-r) and East (x = cx+r) walls — skip corners to avoid double-spawn
        for (int dz = -r + 1; dz < r; dz++) {
            world.spawnParticle(Particle.DUST, cx - r + 0.5, yd, cz + dz + 0.5, 1, 0, 0, 0, 0, dust);
            world.spawnParticle(Particle.DUST, cx + r + 0.5, yd, cz + dz + 0.5, 1, 0, 0, 0, 0, dust);
        }
    }

    // ── Chat listener (add member only) ──────────────────────────────────────

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        UUID uid = player.getUniqueId();

        UUID renameWid = pendingRename.remove(uid);
        if (renameWid != null) {
            e.setCancelled(true);
            String newName = e.getMessage().trim();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Ward w = manager.get(renameWid);
                if (w == null) return;
                w.setName(newName);
                manager.save(w);
                player.sendMessage(Msg.c("&aWard renamed to: &f" + newName));
            });
            return;
        }

        UUID addWid = pendingAdd.remove(uid);
        if (addWid == null) return;

        e.setCancelled(true);
        String name = e.getMessage().trim();
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        if (op.getUniqueId() == null) {
            player.sendMessage(Msg.c("&cUnknown player."));
            return;
        }
        final UUID memberUuid = op.getUniqueId();
        final UUID wardId = addWid;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Ward w = manager.get(wardId);
            if (w == null) return;
            int max = manager.maxMembers(w);
            if (max >= 0 && w.members().size() >= max) {
                player.sendMessage(Msg.c("&cThis ward has reached its member limit (" + max + ")."));
                return;
            }
            manager.addMember(wardId, memberUuid);
            player.sendMessage(Msg.c("&aAdded &f" + name + "&a as member."));
        });
    }
}
