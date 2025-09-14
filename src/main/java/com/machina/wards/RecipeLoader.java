package com.machina.wards;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class RecipeLoader {

    private final MachinaWards plugin;
    private final NamespacedKey tierKey;
    private final WardManager manager;

    public RecipeLoader(MachinaWards plugin, NamespacedKey tierKey, WardManager manager) {
        this.plugin = plugin;
        this.tierKey = tierKey;
        this.manager = manager;
    }

    public void registerAll() {
        var cfg = plugin.getConfig();
        ConfigurationSection ws = cfg.getConfigurationSection("wards");
        if (ws == null) return;

        for (String tier : ws.getKeys(false)) {
            ConfigurationSection sec = ws.getConfigurationSection(tier);
            if (sec == null) continue;

            String resultMat = sec.getString("result_material", "SEA_LANTERN");
            Material mat = Material.matchMaterial(resultMat);
            if (mat == null) mat = Material.SEA_LANTERN;

            ItemStack result = createWardItem(tier, mat, sec.getString("display_name", "&aWard Tier"));
            NamespacedKey key = new NamespacedKey(plugin, "recipe_" + tier.toLowerCase(Locale.ROOT));
            ShapedRecipe sr = new ShapedRecipe(key, result);

            java.util.List<?> rows = sec.getList("custom_recipe");
            if (rows != null && rows.size() == 3) {
                sr.shape("ABC", "DEF", "GHI");
                Map<String, Character> map = new HashMap<>();
                char[] labels = "ABCDEFGHI".toCharArray();
                int idx = 0;
                for (int r = 0; r < 3; r++) {
                    java.util.List<?> row = (java.util.List<?>) rows.get(r);
                    for (int c = 0; c < 3; c++) {
                        String token = "";
                        if (row != null && c < row.size() && row.get(c) != null) token = row.get(c).toString();
                        if (token.isEmpty() || token.equalsIgnoreCase("AIR")) continue;
                        Material m = Material.matchMaterial(token);
                        if (m == null || m == Material.AIR) continue;
                        Character ch = map.get(token);
                        if (ch == null) {
                            ch = labels[idx++];
                            map.put(token, ch);
                            sr.setIngredient(ch, m);
                        }
                    }
                }
            } else {
                sr.shape(" A ", "ABA", " A ");
                sr.setIngredient('A', Material.GOLD_INGOT);
                sr.setIngredient('B', Material.GOLD_BLOCK);
            }

            Bukkit.addRecipe(sr);
        }
    }

    public ItemStack createWardItem(String tier, Material mat, String display) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', display));
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }
}
