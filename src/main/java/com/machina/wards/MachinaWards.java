package com.machina.wards;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class MachinaWards extends JavaPlugin {

    private WardManager manager;
    private Economy economy;
    private NamespacedKey tierKey;
    private NamespacedKey actionKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        tierKey = new NamespacedKey(this, "ward-tier");
        actionKey = new NamespacedKey(this, "ward-action");

        SqliteStore store = new SqliteStore(getDataFolder());
        store.init();

        manager = new WardManager(this, store);
        manager.loadAll();

        RecipeLoader recipes = new RecipeLoader(this, tierKey, manager);
        recipes.registerAll();

        setupEconomy();

        getServer().getPluginManager().registerEvents(new ProtectionListener(this, manager), this);
        getServer().getPluginManager().registerEvents(new EntryListener(this, manager), this);
        getServer().getPluginManager().registerEvents(new WardBlocksListener(this, manager, tierKey), this);
        getServer().getPluginManager().registerEvents(new WardMenuListener(this, manager, tierKey, actionKey), this);
        if (economy != null) {
            getServer().getPluginManager().registerEvents(new ShopMenuListener(this, manager, tierKey, economy), this);
        }

        if (getCommand("ward") != null) {
            getCommand("ward").setExecutor(new WardCommand(this, manager, economy));
            getCommand("ward").setTabCompleter(new WardTab(manager));
        }

        getLogger().info("MachinaWards enabled");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.flush();
        getLogger().info("MachinaWards disabled");
    }

    public WardManager manager() { return manager; }
    public Economy economy() { return economy; }
    public NamespacedKey tierKey() { return tierKey; }
    public NamespacedKey actionKey() { return actionKey; }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault not found. Shop disabled.");
            this.economy = null;
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().warning("Vault economy provider not found. Shop disabled.");
            this.economy = null;
            return;
        }
        economy = rsp.getProvider();
    }
}
