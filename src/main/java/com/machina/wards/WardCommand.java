package com.machina.wards;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class WardCommand implements CommandExecutor {

    private final MachinaWards plugin;
    private final WardManager manager;
    private final Economy econ;

    public WardCommand(MachinaWards plugin, WardManager manager, Economy econ) {
        this.plugin = plugin;
        this.manager = manager;
        this.econ = econ;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String cmdLabel, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(Msg.c("&6--- Wards Help ---"));
            sender.sendMessage(Msg.c("&e/ward shop &7- Open ward shop"));
            sender.sendMessage(Msg.c("&e/ward list &7- List your wards"));
            sender.sendMessage(Msg.c("&e/ward tp <id|name> &7- Teleport to one of your wards"));
            sender.sendMessage(Msg.c("&e/ward compass [id|name] &7- Point your compass at a ward"));
            sender.sendMessage(Msg.c("&e/ward transfer <player> &7- Transfer nearby ward to another player"));
            sender.sendMessage(Msg.c("&e/ward addmember <name> &7- Add member to nearby ward"));
            sender.sendMessage(Msg.c("&e/ward removemember <name> &7- Remove member from nearby ward"));
            sender.sendMessage(Msg.c("&e/ward info [id|name] &7- Show info about a nearby or specific ward"));
            sender.sendMessage(Msg.c("&e/ward nearby [radius] &7- List wards near your location"));
            if (sender.hasPermission("wards.admin")) {
                sender.sendMessage(Msg.c("&e/ward reload &7- Reload config and re-register recipes"));
                sender.sendMessage(Msg.c("&e/ward admin list [player] &7- List all wards"));
                sender.sendMessage(Msg.c("&e/ward admin delete <id> &7- Delete ward by ID or name"));
                sender.sendMessage(Msg.c("&e/ward admin tp <id|name> &7- Teleport to any ward"));
                sender.sendMessage(Msg.c("&e/ward admin migrate mysql &7- Copy all ward data to MySQL"));
            }
            return true;
        }

        // ── reload ────────────────────────────────────────────────────────────
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("wards.admin")) { sender.sendMessage(Msg.c("&cNo permission.")); return true; }
            plugin.reloadConfig();
            manager.reloadShape();
            plugin.reloadParticleTask();
            RecipeLoader rl = new RecipeLoader(plugin, plugin.tierKey(), manager);
            rl.unregisterAll();
            rl.registerAll();
            sender.sendMessage(Msg.c("&aConfig reloaded and recipes re-registered."));
            return true;
        }

        // ── shop ──────────────────────────────────────────────────────────────
        if (args[0].equalsIgnoreCase("shop")) {
            if (!(sender instanceof Player p)) { sender.sendMessage(Msg.c("&cPlayers only.")); return true; }
            if (plugin.economy() == null) { sender.sendMessage(Msg.c("&cShop disabled.")); return true; }
            new ShopMenuListener(plugin, manager, plugin.tierKey(), plugin.economy()).open(p);
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage(Msg.c("&cPlayers only for this command."));
            return true;
        }

        // ── list ──────────────────────────────────────────────────────────────
        if (args[0].equalsIgnoreCase("list")) {
            int count = 0;
            int limit = manager.getLimit(p);
            String limitStr = limit > 0 ? "/" + limit : "";
            for (Ward w : manager.wardsOwnedBy(p.getUniqueId())) {
                if (count == 0) p.sendMessage(Msg.c("&6--- Your Wards (" + manager.countOwned(p) + limitStr + ") ---"));
                String wardLabel = w.name().isEmpty() ? w.tier() : w.name() + " &7(" + w.tier() + ")";
                String msgLine = "&7[&f" + w.shortId() + "&7] &f" + wardLabel
                        + " &7in &f" + w.world()
                        + " &7at &f" + w.bx() + "," + w.by() + "," + w.bz()
                        + " &7r=&f" + w.radius();
                if (!w.entryMessage().isEmpty()) msgLine += " &7[msg set]";
                p.sendMessage(Msg.c(msgLine));
                count++;
            }
            if (count == 0) p.sendMessage(Msg.c("&7You own no wards."));
            return true;
        }

        // ── tp ────────────────────────────────────────────────────────────────
        if (args[0].equalsIgnoreCase("tp") && args.length >= 2) {
            Ward w = manager.findWard(args[1]);
            if (w == null) { p.sendMessage(Msg.c("&cWard not found: " + args[1])); return true; }
            if (!w.owner().equals(p.getUniqueId()) && !p.hasPermission("wards.admin")) {
                p.sendMessage(Msg.c("&cOnly the owner or admin can teleport to this ward."));
                return true;
            }
            org.bukkit.World world = Bukkit.getWorld(w.world());
            if (world == null) { p.sendMessage(Msg.c("&cWard world not loaded.")); return true; }
            Location dest = new Location(world, w.bx() + 0.5, w.by() + 1, w.bz() + 0.5, p.getLocation().getYaw(), 0);
            p.teleport(dest);
            String wardLabel = w.name().isEmpty() ? w.shortId() : w.name();
            p.sendMessage(Msg.c("&aTeleported to ward &f" + wardLabel + "&a."));
            return true;
        }

        // ── compass ───────────────────────────────────────────────────────────
        if (args[0].equalsIgnoreCase("compass")) {
            Ward target = null;
            if (args.length >= 2) {
                target = manager.findWard(args[1]);
                if (target == null) { p.sendMessage(Msg.c("&cWard not found: " + args[1])); return true; }
                if (!target.owner().equals(p.getUniqueId()) && !p.hasPermission("wards.admin")) {
                    p.sendMessage(Msg.c("&cOnly the owner or admin can target this ward.")); return true;
                }
            } else {
                double best = Double.MAX_VALUE;
                for (Ward w : manager.wardsOwnedBy(p.getUniqueId())) {
                    if (!w.world().equals(p.getWorld().getName())) continue;
                    double dist = p.getLocation().distanceSquared(
                            new Location(p.getWorld(), w.bx() + 0.5, w.by(), w.bz() + 0.5));
                    if (dist < best) { best = dist; target = w; }
                }
                if (target == null) { p.sendMessage(Msg.c("&cYou have no wards in this world.")); return true; }
            }
            org.bukkit.World world = Bukkit.getWorld(target.world());
            if (world == null) { p.sendMessage(Msg.c("&cWard world not loaded.")); return true; }
            p.setCompassTarget(new Location(world, target.bx() + 0.5, target.by(), target.bz() + 0.5));
            String wardLabel = target.name().isEmpty() ? target.shortId() : target.name();
            p.sendMessage(Msg.c("&aCompass pointing to ward &f" + wardLabel + "&a. Use any compass to navigate."));
            return true;
        }

        // ── transfer ──────────────────────────────────────────────────────────
        if (args[0].equalsIgnoreCase("transfer") && args.length >= 2) {
            Ward w;
            String targetName;
            if (args.length >= 3) {
                w = manager.findWard(args[1]);
                targetName = args[2];
            } else {
                w = manager.findAt(p.getLocation());
                targetName = args[1];
            }
            if (w == null) { p.sendMessage(Msg.c("&cNo ward found. Stand inside a ward or specify its ID.")); return true; }
            if (!w.owner().equals(p.getUniqueId()) && !p.hasPermission("wards.admin")) {
                p.sendMessage(Msg.c("&cOnly the owner or admin can transfer this ward.")); return true;
            }

            final Ward wardToTransfer = w;
            final String senderName = p.getName();

            OfflinePlayer newOwnerOp = Msg.resolveOfflinePlayer(targetName);
            if (newOwnerOp.getUniqueId() == null) {
                p.sendMessage(Msg.c("&cPlayer not found (must have joined this server): " + targetName));
                return true;
            }

            UUID newOwnerId = newOwnerOp.getUniqueId();
            manager.transferOwner(wardToTransfer.id(), newOwnerId);
            String ownerDisplay = newOwnerOp.getName() != null ? newOwnerOp.getName() : targetName;
            String label = wardToTransfer.name().isEmpty() ? wardToTransfer.shortId() : wardToTransfer.name();
            p.sendMessage(Msg.c("&aWard &f" + label + "&a transferred to &f" + ownerDisplay + "&a."));
            Player newOnline = Bukkit.getPlayer(newOwnerId);
            if (newOnline != null)
                newOnline.sendMessage(Msg.c("&f" + senderName + "&a transferred ward &f" + label + "&a to you."));
            return true;
        }

        // ── admin ─────────────────────────────────────────────────────────────
        if (args[0].equalsIgnoreCase("admin")) {
            if (!sender.hasPermission("wards.admin")) { sender.sendMessage(Msg.c("&cNo permission.")); return true; }
            if (args.length < 2) {
                sender.sendMessage(Msg.c("&6Admin: &e/ward admin list [player] &7| &e/ward admin delete <id>"));
                return true;
            }

            if (args[1].equalsIgnoreCase("list")) {
                String filterName = args.length >= 3 ? args[2].toLowerCase(java.util.Locale.ROOT) : null;
                int count = 0;
                for (Ward w : manager.all()) {
                    if (filterName != null) {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(w.owner()); // UUID overload — safe
                        String ownerName = op.getName();
                        if (ownerName == null || !ownerName.toLowerCase(java.util.Locale.ROOT).equals(filterName)) continue;
                    }
                    OfflinePlayer op = Bukkit.getOfflinePlayer(w.owner()); // UUID overload — safe
                    String ownerName = op.getName() != null ? op.getName() : w.owner().toString().substring(0, 8);
                    String nameStr = w.name().isEmpty() ? "" : " \"" + w.name() + "\"";
                    sender.sendMessage(Msg.c("&7[&f" + w.shortId() + "&7]" + nameStr + " &f" + w.tier()
                            + " &7owner=&f" + ownerName
                            + " &7world=&f" + w.world()
                            + " &7at &f" + w.bx() + "," + w.by() + "," + w.bz()));
                    count++;
                }
                if (count == 0) sender.sendMessage(Msg.c("&7No wards found."));
                return true;
            }

            if (args[1].equalsIgnoreCase("delete") && args.length >= 3) {
                Ward w = manager.findWard(args[2]);
                if (w == null) { sender.sendMessage(Msg.c("&cWard not found: " + args[2])); return true; }
                org.bukkit.World world = Bukkit.getWorld(w.world());
                if (world != null) {
                    org.bukkit.Location loc = new org.bukkit.Location(world, w.bx() + 0.5, w.by() + 0.5, w.bz() + 0.5);
                    world.spawnParticle(org.bukkit.Particle.END_ROD, loc, 25, 0.5, 0.5, 0.5, 0.05);
                }
                manager.delete(w.id());
                sender.sendMessage(Msg.c("&aDeleted ward &f" + w.shortId() + "&a."));
                return true;
            }

            if (args[1].equalsIgnoreCase("tp") && args.length >= 3) {
                if (!(sender instanceof Player admin)) { sender.sendMessage(Msg.c("&cPlayers only.")); return true; }
                Ward w = manager.findWard(args[2]);
                if (w == null) { sender.sendMessage(Msg.c("&cWard not found: " + args[2])); return true; }
                org.bukkit.World world = Bukkit.getWorld(w.world());
                if (world == null) { sender.sendMessage(Msg.c("&cWard world not loaded.")); return true; }
                Location dest = new Location(world, w.bx() + 0.5, w.by() + 1, w.bz() + 0.5,
                        admin.getLocation().getYaw(), 0);
                admin.teleport(dest);
                String wardLabel = w.name().isEmpty() ? w.shortId() : w.name();
                admin.sendMessage(Msg.c("&aTeleported to ward &f" + wardLabel + "&a."));
                return true;
            }

            if (args[1].equalsIgnoreCase("stats")) {
                sender.sendMessage(Msg.c("&6--- Ward Stats ---"));
                for (String line : manager.stats()) sender.sendMessage(Msg.c(line));
                return true;
            }

            if (args[1].equalsIgnoreCase("migrate") && args.length >= 3
                    && args[2].equalsIgnoreCase("mysql")) {
                if (plugin.store() instanceof MysqlStore) {
                    sender.sendMessage(Msg.c("&cAlready using MySQL. Nothing to migrate."));
                    return true;
                }
                sender.sendMessage(Msg.c("&eStarting migration to MySQL... this may take a moment."));
                String host     = plugin.getConfig().getString("database.mysql.host",     "localhost");
                int    port     = plugin.getConfig().getInt   ("database.mysql.port",     3306);
                String database = plugin.getConfig().getString("database.mysql.database", "machinawards");
                String user     = plugin.getConfig().getString("database.mysql.username", "root");
                String pass     = plugin.getConfig().getString("database.mysql.password", "");
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        MysqlStore target = new MysqlStore(host, port, database, user, pass);
                        target.init();
                        int count = manager.migrate(target);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            sender.sendMessage(Msg.c("&a✔ Migrated &f" + count + "&a wards to MySQL."));
                            sender.sendMessage(Msg.c("&7Set &fdatabase.type: mysql&7 in config.yml and restart."));
                        });
                    } catch (Exception ex) {
                        Bukkit.getScheduler().runTask(plugin, () ->
                            sender.sendMessage(Msg.c("&cMigration failed: " + ex.getMessage())));
                        plugin.getLogger().severe("MySQL migration error: " + ex.getMessage());
                    }
                });
                return true;
            }

            sender.sendMessage(Msg.c("&6Admin: &e/ward admin list [player] &7| &e/ward admin delete <id> &7| &e/ward admin migrate mysql"));
            return true;
        }

        // ── info ──────────────────────────────────────────────────────────────
        if (args[0].equalsIgnoreCase("info")) {
            Ward w;
            if (args.length >= 2) {
                w = manager.findWard(args[1]);
                if (w == null) { p.sendMessage(Msg.c("&cWard not found: " + args[1])); return true; }
            } else {
                w = manager.findAt(p.getLocation());
                if (w == null) { p.sendMessage(Msg.c("&cYou are not inside a ward.")); return true; }
            }
            OfflinePlayer ownerOp = Bukkit.getOfflinePlayer(w.owner()); // UUID overload — safe
            String ownerName = ownerOp.getName() != null ? ownerOp.getName() : w.owner().toString().substring(0, 8);
            String wardLabel = w.name().isEmpty() ? w.shortId() : w.name() + " &7[" + w.shortId() + "]";
            boolean isOwner  = w.owner().equals(p.getUniqueId());
            boolean isMember = w.members().contains(p.getUniqueId());
            p.sendMessage(Msg.c("&6--- Ward Info ---"));
            p.sendMessage(Msg.c("&7Name: &f"    + wardLabel));
            p.sendMessage(Msg.c("&7Tier: &f"    + w.tier() + "  &7Radius: &f" + w.radius()));
            p.sendMessage(Msg.c("&7Owner: &f"   + ownerName
                    + (isOwner ? " &a(you)" : "")));
            p.sendMessage(Msg.c("&7Members: &f" + w.members().size()
                    + (isMember ? " &a(you are a member)" : "")));
            p.sendMessage(Msg.c("&7World: &f"   + w.world()
                    + "  &7at &f" + w.bx() + "," + w.by() + "," + w.bz()));
            return true;
        }

        // ── nearby ────────────────────────────────────────────────────────────
        if (args[0].equalsIgnoreCase("nearby")) {
            int radius = 100;
            if (args.length >= 2) {
                try { radius = Math.max(1, Math.min(500, Integer.parseInt(args[1]))); }
                catch (NumberFormatException ignore) {}
            }
            int px = p.getLocation().getBlockX();
            int pz = p.getLocation().getBlockZ();
            List<Ward> nearby = manager.nearbyWards(p.getWorld().getName(), px, pz, radius);
            if (nearby.isEmpty()) {
                p.sendMessage(Msg.c("&7No wards within &f" + radius + "&7 blocks."));
                return true;
            }
            nearby.sort(Comparator.comparingDouble(w -> {
                double dx = w.bx() - px;
                double dz = w.bz() - pz;
                return dx * dx + dz * dz;
            }));
            p.sendMessage(Msg.c("&6--- Nearby wards (r=" + radius + ") ---"));
            for (Ward w : nearby) {
                double dist = Math.sqrt(Math.pow(w.bx() - px, 2) + Math.pow(w.bz() - pz, 2));
                OfflinePlayer op = Bukkit.getOfflinePlayer(w.owner()); // UUID overload — safe
                String ownerName = op.getName() != null ? op.getName() : w.owner().toString().substring(0, 8);
                String wardLabel = w.name().isEmpty() ? w.shortId() : w.name();
                p.sendMessage(Msg.c("&7[&f" + wardLabel + "&7] &f" + w.tier()
                        + " &7owner=&f" + ownerName
                        + " &7dist=&f" + (int) dist + "m"));
            }
            return true;
        }

        // ── addmember ─────────────────────────────────────────────────────────
        if (args[0].equalsIgnoreCase("addmember") && args.length >= 2) {
            Ward near = manager.findAt(p.getLocation());
            if (near == null) { p.sendMessage(Msg.c("&cStand inside a ward to manage it.")); return true; }
            if (!near.owner().equals(p.getUniqueId()) && !p.hasPermission("wards.admin")) {
                p.sendMessage(Msg.c("&cOnly owner or admin.")); return true;
            }
            int max = manager.maxMembers(near);
            if (max >= 0 && near.members().size() >= max) {
                p.sendMessage(Msg.c("&cThis ward has reached its member limit (" + max + ")."));
                return true;
            }
            String targetName = args[1];
            OfflinePlayer addOp = Msg.resolveOfflinePlayer(targetName);
            if (addOp.getUniqueId() == null) {
                p.sendMessage(Msg.c("&cPlayer not found (must have joined this server): " + targetName));
                return true;
            }
            manager.addMember(near.id(), addOp.getUniqueId());
            p.sendMessage(Msg.c("&aAdded &f" + (addOp.getName() != null ? addOp.getName() : targetName) + "&a to members."));
            return true;
        }

        // ── removemember ──────────────────────────────────────────────────────
        if (args[0].equalsIgnoreCase("removemember") && args.length >= 2) {
            Ward near = manager.findAt(p.getLocation());
            if (near == null) { p.sendMessage(Msg.c("&cStand inside a ward to manage it.")); return true; }
            if (!near.owner().equals(p.getUniqueId()) && !p.hasPermission("wards.admin")) {
                p.sendMessage(Msg.c("&cOnly owner or admin.")); return true;
            }
            String targetName = args[1];
            OfflinePlayer removeOp = Msg.resolveOfflinePlayer(targetName);
            if (removeOp.getUniqueId() == null) {
                p.sendMessage(Msg.c("&cPlayer not found (must have joined this server): " + targetName));
                return true;
            }
            manager.removeMember(near.id(), removeOp.getUniqueId());
            p.sendMessage(Msg.c("&aRemoved &f" + (removeOp.getName() != null ? removeOp.getName() : targetName) + "&a from members."));
            return true;
        }

        sender.sendMessage(Msg.c("&cUnknown subcommand. Try /ward help"));
        return true;
    }
}
