package com.machina.wards;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ShopMenuListener implements Listener {

    private final MachinaWards plugin;
    private final WardManager manager;
    private final NamespacedKey tierKey;
    private final Economy econ;

    public ShopMenuListener(MachinaWards plugin, WardManager manager, NamespacedKey tierKey, Economy econ) {
        this.plugin = plugin;
        this.manager = manager;
        this.tierKey = tierKey;
        this.econ = econ;
    }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(p, 27, ChatColor.DARK_GREEN + "Ward Shop");

        var sec = plugin.getConfig().getConfigurationSection("wards");
        if (sec != null) {
            int slot = 10;
            for (String tier : sec.getKeys(false)) {
                ConfigurationSection t = sec.getConfigurationSection(tier);
                if (t == null) continue;
                String matName = t.getString("result_material", "SEA_LANTERN");
                Material mat = Material.matchMaterial(matName);
                if (mat == null) mat = Material.SEA_LANTERN;
                ItemStack it = new RecipeLoader(plugin, tierKey, manager).createWardItem(tier, mat, t.getString("display_name", "&aWard"));
                ItemMeta im = it.getItemMeta();
                java.util.List<String> lore = new java.util.ArrayList<>();
                lore.add(ChatColor.translateAlternateColorCodes('&', "&7Price: &f" + t.getInt("price", 100)));
                lore.add(ChatColor.translateAlternateColorCodes('&', "&7Radius: &f" + t.getInt("radius", 12)));
                im.setLore(lore);
                it.setItemMeta(im);
                inv.setItem(slot++, it);
                if (slot == 17) slot = 19;
            }
        }

        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        HumanEntity he = e.getWhoClicked();
        if (!(he instanceof Player)) return;
        if (e.getView().getTitle() == null || !ChatColor.stripColor(e.getView().getTitle()).equalsIgnoreCase("Ward Shop")) return;
        e.setCancelled(true);

        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        String tier = it.getItemMeta().getPersistentDataContainer().get(tierKey, org.bukkit.persistence.PersistentDataType.STRING);
        if (tier == null) return;

        Player p = (Player) he;

        int price = plugin.getConfig().getInt("wards." + tier + ".price", 100);
        if (econ == null) { p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cShop disabled, Vault not hooked.")); return; }

        if (!econ.has(p, price)) { p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cYou need " + price + " to buy this.")); return; }

        econ.withdrawPlayer(p, price);
        p.getInventory().addItem(it.clone());
        p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aPurchased " + ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("wards." + tier + ".display_name", "&aWard")) + "&a."));
    }
}
