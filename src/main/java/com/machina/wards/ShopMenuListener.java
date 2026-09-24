package com.machina.wards;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ShopMenuListener implements Listener {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private final MachinaWards plugin;

    public ShopMenuListener(MachinaWards plugin, WardManager manager, NamespacedKey tierKey, Economy econ) {
        this.plugin = plugin;
    }

    static void open(MachinaWards plugin, Player p) {
        Economy econ = plugin.economy();
        if (econ == null) {
            p.sendMessage(Msg.c("&cShop disabled."));
            return;
        }

        WardGui g = WardGui.shop();
        Inventory inv = g.create(27, Msg.c("&2Ward Shop"));

        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("wards");
        if (sec != null) {
            int slotIdx = 0;
            int skipped = 0;
            for (String tier : sec.getKeys(false)) {
                ItemStack it = RecipeLoader.wardItem(plugin, tier);
                if (it == null) continue;
                if (slotIdx >= SLOTS.length) { skipped++; continue; }

                ConfigurationSection t = sec.getConfigurationSection(tier);
                double price = t.getDouble("price", 100);
                int radius = t.getInt("radius", 12);
                int max = t.getInt("max_members", -1);
                int n = 0;
                for (String id : t.getStringList("features")) {
                    if (WardFeature.fromId(id).isPresent()) n++;
                }

                ItemMeta im = it.getItemMeta();
                if (im != null) {
                    im.getPersistentDataContainer().set(plugin.actionKey(), PersistentDataType.STRING, "buy:" + tier);

                    List<String> lore = new ArrayList<>();
                    lore.add(Msg.c("&7Price: &f" + econ.format(price)));
                    lore.add(Msg.c("&7Radius: &f" + radius + " blocks"));
                    if (max < 0) {
                        lore.add(Msg.c("&7Members: &fNo limit"));
                    } else {
                        lore.add(Msg.c("&7Members: &fUp to " + max));
                    }
                    if (n > 0) {
                        lore.add(Msg.c("&7Ward Intelligence: &f" + n + (n == 1 ? " feature" : " features")));
                    }
                    lore.add("");
                    lore.add(econ.has(p, price) ? Msg.c("&e» Click to buy") : Msg.c("&cYou can't afford this"));
                    im.setLore(lore);
                    it.setItemMeta(im);
                }

                inv.setItem(SLOTS[slotIdx++], it);
            }
            if (skipped > 0) {
                plugin.getLogger().warning("Ward Shop has room for 14 tiers; " + skipped + " tier(s) not shown.");
            }
        }

        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof WardGui g)) return;   // 1 holder
        e.setCancelled(true);                                                            // 2 cancel, unconditionally
        if (g.kind != WardGui.Kind.SHOP) return;                                         //   kinds this listener owns
        if (e.getRawSlot() >= e.getView().getTopInventory().getSize()) return;           // 3 top inventory only
        if (e.getClick() == ClickType.DOUBLE_CLICK) return;                              // 4 no double-fire
        if (!(e.getWhoClicked() instanceof Player p)) return;

        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        String action = it.getItemMeta().getPersistentDataContainer().get(plugin.actionKey(), PersistentDataType.STRING);
        if (action == null || !action.startsWith("buy:")) return;
        String tier = action.substring(4);

        Economy econ = plugin.economy();
        ConfigurationSection t = plugin.getConfig().getConfigurationSection("wards." + tier);
        if (t == null) {
            p.sendMessage(Msg.c("&cThat ward is no longer sold."));
            WardGui.sound(plugin, p, "menu_error");
            Bukkit.getScheduler().runTask(plugin, p::closeInventory);
            return;
        }
        if (econ == null) {
            p.sendMessage(Msg.c("&cShop disabled, Vault not hooked."));
            WardGui.sound(plugin, p, "menu_error");
            return;
        }

        double price = t.getDouble("price", 100);
        if (!econ.has(p, price)) {
            p.sendMessage(Msg.c("&cYou need " + econ.format(price) + " to buy this."));
            WardGui.sound(plugin, p, "menu_error");
            return;
        }

        ItemStack ward = RecipeLoader.wardItem(plugin, tier);
        if (ward == null) {
            p.sendMessage(Msg.c("&cThat ward is no longer sold."));
            WardGui.sound(plugin, p, "menu_error");
            Bukkit.getScheduler().runTask(plugin, p::closeInventory);
            return;
        }

        EconomyResponse r = econ.withdrawPlayer(p, price);
        if (r == null || !r.transactionSuccess()) {
            p.sendMessage(Msg.c("&cPayment failed. You were not charged."));
            WardGui.sound(plugin, p, "menu_error");
            return;
        }

        var overflow = p.getInventory().addItem(ward);
        overflow.values().forEach(stack -> p.getWorld().dropItemNaturally(p.getLocation(), stack));

        p.sendMessage(Msg.c("&aPurchased " + t.getString("display_name", "&aWard") + "&a for &f" + econ.format(price) + "&a."));
        WardGui.sound(plugin, p, "menu_success");

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() == g) open(plugin, p);
        });
    }
}
