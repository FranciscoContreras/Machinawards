package com.machina.wards;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Holder identifying our chest menus by object identity instead of title string. */
final class WardGui implements InventoryHolder {

    enum Kind { MAIN, MEMBERS, TRUST, SHOP, FEATURES, FEATURE }

    final Kind kind;
    final UUID wardId;      // null for SHOP
    final UUID memberId;    // TRUST only, else null
    final String featureId; // FEATURE only, else null
    final int page;         // MEMBERS: the page shown; TRUST: the members page to return to; else 0
    private Inventory inv;

    WardGui(Kind kind, UUID wardId, UUID memberId, String featureId, int page) {
        this.kind = kind;
        this.wardId = wardId;
        this.memberId = memberId;
        this.featureId = featureId;
        this.page = page;
    }

    static WardGui shop() {
        return new WardGui(Kind.SHOP, null, null, null, 0);
    }

    static WardGui of(Kind kind, UUID wardId) {
        return new WardGui(kind, wardId, null, null, 0);
    }

    Inventory create(int size, String title) {
        inv = Bukkit.createInventory(this, size, title);
        return inv;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    static boolean canManage(Player p, Ward w) {
        return w.owner().equals(p.getUniqueId()) || p.hasPermission("wards.admin");
    }

    static List<String> lore(List<String> description, String state, String action) {
        List<String> lines = new ArrayList<>();
        for (String d : description) lines.add(Msg.c(d));
        if (!description.isEmpty() && (state != null || action != null)) lines.add("");
        if (state != null) lines.add(Msg.c("&7State: " + state));
        if (action != null) lines.add(Msg.c("&e» " + action));
        return lines;
    }

    static void glint(ItemMeta m, boolean on) {
        if (on) m.setEnchantmentGlintOverride(true);
    }

    static List<String> wrap(String text, int width) {
        String prefix = "";
        String body = text;
        if (text.length() >= 2 && text.charAt(0) == '&') {
            prefix = text.substring(0, 2);
            body = text.substring(2);
        }
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : body.split(" ")) {
            if (cur.length() == 0) {
                cur.append(word);
            } else if (cur.length() + 1 + word.length() <= width) {
                cur.append(' ').append(word);
            } else {
                lines.add(prefix + cur);
                cur = new StringBuilder(word);
            }
        }
        if (cur.length() > 0 || lines.isEmpty()) lines.add(prefix + cur);
        return lines;
    }

    static ItemStack button(MachinaWards plugin, Material mat, String name, List<String> lore, String action) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        m.setDisplayName(Msg.c(name));
        if (lore != null) m.setLore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (action != null) m.getPersistentDataContainer().set(plugin.actionKey(), PersistentDataType.STRING, action);
        it.setItemMeta(m);
        return it;
    }

    static void sound(MachinaWards plugin, Player p, String key) {
        Sound s = Msg.resolveSound(plugin.getConfig().getString("sounds." + key, ""));
        if (s != null) p.playSound(p.getLocation(), s, 1f, 1f);
    }

    static String label(Ward w) {
        String name = WardManager.plainName(w.name());
        return !name.isEmpty() ? name : w.shortId();
    }

    static String tierName(MachinaWards plugin, String tier) {
        return ChatColor.stripColor(Msg.c(plugin.getConfig().getString("wards." + tier + ".display_name", tier)));
    }

    static ItemStack infoItem(MachinaWards plugin, Ward w) {
        List<String> lore = new ArrayList<>();
        lore.add(Msg.c("&7Tier: &f" + tierName(plugin, w.tier())));
        lore.add(Msg.c("&7Radius: &f" + w.radius() + " blocks"));
        int n = w.members().size();
        int max = plugin.manager().maxMembers(w);
        if (max < 0) {
            lore.add(Msg.c("&7Members: &f" + n + " &7(no limit)"));
        } else {
            lore.add(Msg.c("&7Members: &f" + n + "&7/&f" + max));
        }
        lore.add(Msg.c("&7Location: &f" + w.world() + " " + w.bx() + ", " + w.by() + ", " + w.bz()));
        lore.add(Msg.c("&7ID: &f" + w.shortId()));

        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        m.setDisplayName(Msg.c("&3&l" + label(w)));
        m.setLore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(m);
        return it;
    }

    private record Armed(String key, long at) {}

    private static final ConcurrentHashMap<UUID, Armed> ARMED = new ConcurrentHashMap<>();

    static boolean confirm(MachinaWards plugin, Player p, String key) {
        long window = plugin.getConfig().getLong("pickup.confirm_ms", 5000);
        UUID id = p.getUniqueId();
        Armed armed = ARMED.get(id);
        long now = System.currentTimeMillis();
        if (armed != null && armed.key().equals(key) && now - armed.at() <= window) {
            ARMED.remove(id);
            return true;
        }
        ARMED.put(id, new Armed(key, now));
        return false;
    }

    static boolean isArmed(MachinaWards plugin, Player p, String key) {
        long window = plugin.getConfig().getLong("pickup.confirm_ms", 5000);
        Armed armed = ARMED.get(p.getUniqueId());
        return armed != null && armed.key().equals(key) && System.currentTimeMillis() - armed.at() <= window;
    }

    static void clearConfirm(UUID playerId) {
        ARMED.remove(playerId);
    }

    static void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof WardGui) {
                p.closeInventory();
            }
        }
    }
}
