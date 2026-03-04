package com.machina.wards;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class WardItemGuardListener implements Listener {

    private final MachinaWards plugin;

    public WardItemGuardListener(MachinaWards plugin) {
        this.plugin = plugin;
    }

    /** Prevent ward items from being used as crafting ingredients. */
    @EventHandler
    public void onCraftPrepare(PrepareItemCraftEvent e) {
        for (ItemStack ingredient : e.getInventory().getMatrix()) {
            if (isWardItem(ingredient)) {
                e.getInventory().setResult(new ItemStack(Material.AIR));
                return;
            }
        }
    }

    /** Prevent ward items from being placed into beacon payment slots. */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getInventory().getType() != InventoryType.BEACON) return;

        // Direct click into the beacon payment slot (slot 0)
        if (e.getRawSlot() == 0 && isWardItem(e.getCursor())) {
            e.setCancelled(true);
            return;
        }

        // Shift-click from player inventory into beacon
        if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && isWardItem(e.getCurrentItem())) {
            e.setCancelled(true);
        }
    }

    private boolean isWardItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer()
                .has(plugin.tierKey(), PersistentDataType.STRING);
    }
}
