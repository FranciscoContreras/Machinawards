package com.machina.wards;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class MachinaWards extends JavaPlugin {

    private WardManager manager;
    private DataStore store;
    private Economy economy;
    private NamespacedKey tierKey;
    private NamespacedKey actionKey;
    private NamespacedKey memberKey;
    private NamespacedKey featureKey;
    private BukkitTask particleTask;

    // Offline member notifications — delivered on next player join (stored as color-coded strings)
    private final java.util.Map<java.util.UUID, java.util.List<String>>
        pendingNotifications = new java.util.concurrent.ConcurrentHashMap<>();

    public void queueNotification(java.util.UUID uuid, String message) {
        pendingNotifications.computeIfAbsent(uuid,
            k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(message);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        tierKey = new NamespacedKey(this, "ward-tier");
        actionKey = new NamespacedKey(this, "ward-action");
        memberKey = new NamespacedKey(this, "ward-member");
        featureKey = new NamespacedKey(this, "ward-feature");

        store = createStore();
        store.init();

        manager = new WardManager(this, store);
        manager.loadAll();

        RecipeLoader recipes = new RecipeLoader(this, tierKey, manager);
        recipes.registerAll();

        int intervalTicks = getConfig().getInt("particles.interval_ticks", 40);
        particleTask = new WardParticleTask(this, manager).runTaskTimer(this, 20L, intervalTicks);

        getServer().getPluginManager().registerEvents(new ProtectionListener(this, manager), this);
        getServer().getPluginManager().registerEvents(new EntryListener(this, manager), this);
        getServer().getPluginManager().registerEvents(new WardBlocksListener(this, manager, tierKey), this);
        getServer().getPluginManager().registerEvents(new WardMenuListener(this, manager, tierKey, actionKey, memberKey), this);
        getServer().getPluginManager().registerEvents(new SuperWardMenuListener(this), this);
        getServer().getPluginManager().registerEvents(new SuperWardEventListener(this), this);
        getServer().getPluginManager().registerEvents(new WardItemGuardListener(this), this);

        // Deliver queued notifications (from member add while offline) on join
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
                java.util.List<String> msgs =
                    pendingNotifications.remove(e.getPlayer().getUniqueId());
                if (msgs == null || msgs.isEmpty()) return;
                getServer().getScheduler().runTaskLater(MachinaWards.this, () ->
                    msgs.forEach(msg -> e.getPlayer().sendMessage(Msg.c(msg))), 40L);
            }
        }, this);

        // Defer economy hook to next tick so all economy providers finish registering
        getServer().getScheduler().runTask(this, () -> {
            setupEconomy();
            if (economy != null) {
                getServer().getPluginManager().registerEvents(new ShopMenuListener(this, manager, tierKey, economy), this);
            }
            if (getCommand("ward") != null) {
                getCommand("ward").setExecutor(new WardCommand(this, manager, economy));
                getCommand("ward").setTabCompleter(new WardTab(manager));
            }
        });

        getLogger().info("MachinaWards enabled");
    }

    @Override
    public void onDisable() {
        if (particleTask != null) particleTask.cancel();
        if (manager != null) manager.flush();
        getLogger().info("MachinaWards disabled");
    }

    public WardManager manager() { return manager; }
    public DataStore store()    { return store; }
    public Economy economy()    { return economy; }
    public NamespacedKey tierKey() { return tierKey; }
    public NamespacedKey actionKey() { return actionKey; }
    public NamespacedKey memberKey() { return memberKey; }
    public NamespacedKey featureKey() { return featureKey; }

    private DataStore createStore() {
        String type = getConfig().getString("database.type", "sqlite").toLowerCase(java.util.Locale.ROOT);
        if (type.equals("mysql")) {
            String host     = getConfig().getString("database.mysql.host",     "localhost");
            int    port     = getConfig().getInt   ("database.mysql.port",     3306);
            String database = getConfig().getString("database.mysql.database", "machinawards");
            String user     = getConfig().getString("database.mysql.username", "root");
            String pass     = getConfig().getString("database.mysql.password", "");
            getLogger().info("Using MySQL backend (" + host + ":" + port + "/" + database + ")");
            return new MysqlStore(host, port, database, user, pass);
        }
        getLogger().info("Using SQLite backend");
        return new SqliteStore(getDataFolder());
    }

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
