package com.machina.wards;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(Msg.c("&6--- Wards Help ---"));
            sender.sendMessage(Msg.c("&e/ward help &7- Show this"));
            sender.sendMessage(Msg.c("&e/ward shop &7- Open ward shop"));
            sender.sendMessage(Msg.c("&e/ward list &7- List your wards"));
            sender.sendMessage(Msg.c("&e/ward tp <id> &7- Teleport to one of your wards"));
            sender.sendMessage(Msg.c("&e/ward addmember <name> &7- Add member to nearby ward"));
            sender.sendMessage(Msg.c("&e/ward removemember <name> &7- Remove member from nearby ward"));
            if (sender.hasPermission("wards.admin")) {
                sender.sendMessage(Msg.c("&e/ward reload &7- Reload config"));
                sender.sendMessage(Msg.c("&e/ward admin list [player] &7- List all wards"));
                sender.sendMessage(Msg.c("&e/ward admin delete <id> &7- Delete ward by short ID"));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("wards.admin")) { sender.sendMessage(Msg.c("&cNo permission.")); return true; }
            plugin.reloadConfig();
            sender.sendMessage(Msg.c("&aConfig reloaded."));
            return true;
        }

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

        if (args[0].equalsIgnoreCase("list")) {
            int count = 0;
            for (Ward w : manager.all()) {
                if (w.owner().equals(p.getUniqueId())) {
                    String wardLabel = w.name().isEmpty() ? w.tier() : w.name() + " &7(" + w.tier() + ")";
                    p.sendMessage(Msg.c("&7[&f" + w.shortId() + "&7] &f" + wardLabel
                            + " &7in &f" + w.world()
                            + " &7at &f" + w.bx() + "," + w.by() + "," + w.bz()
                            + " &7r=&f" + w.radius()));
                    count++;
                }
            }
            if (count == 0) p.sendMessage(Msg.c("&7You own no wards."));
            return true;
        }

        if (args[0].equalsIgnoreCase("tp") && args.length >= 2) {
            Ward w = manager.findByShortId(args[1]);
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
                        org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(w.owner());
                        String ownerName = op.getName();
                        if (ownerName == null || !ownerName.toLowerCase(java.util.Locale.ROOT).equals(filterName)) continue;
                    }
                    org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(w.owner());
                    String ownerName = op.getName() != null ? op.getName() : w.owner().toString().substring(0, 8);
                    sender.sendMessage(Msg.c("&7[&f" + w.shortId() + "&7] &f" + w.tier()
                            + " &7owner=&f" + ownerName
                            + " &7world=&f" + w.world()
                            + " &7at &f" + w.bx() + "," + w.by() + "," + w.bz()));
                    count++;
                }
                if (count == 0) sender.sendMessage(Msg.c("&7No wards found."));
                return true;
            }

            if (args[1].equalsIgnoreCase("delete") && args.length >= 3) {
                Ward w = manager.findByShortId(args[2]);
                if (w == null) { sender.sendMessage(Msg.c("&cWard not found: " + args[2])); return true; }
                org.bukkit.World world = org.bukkit.Bukkit.getWorld(w.world());
                if (world != null) {
                    org.bukkit.Location loc = new org.bukkit.Location(world, w.bx() + 0.5, w.by() + 0.5, w.bz() + 0.5);
                    world.spawnParticle(org.bukkit.Particle.END_ROD, loc, 25, 0.5, 0.5, 0.5, 0.05);
                }
                manager.delete(w.id());
                sender.sendMessage(Msg.c("&aDeleted ward &f" + w.shortId() + "&a."));
                return true;
            }

            sender.sendMessage(Msg.c("&6Admin: &e/ward admin list [player] &7| &e/ward admin delete <id>"));
            return true;
        }

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
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
            if (op.getUniqueId() == null) { p.sendMessage(Msg.c("&cUnknown player.")); return true; }
            manager.addMember(near.id(), op.getUniqueId());
            p.sendMessage(Msg.c("&aAdded " + args[1] + " to members."));
            return true;
        }

        if (args[0].equalsIgnoreCase("removemember") && args.length >= 2) {
            Ward near = manager.findAt(p.getLocation());
            if (near == null) { p.sendMessage(Msg.c("&cStand inside a ward to manage it.")); return true; }
            if (!near.owner().equals(p.getUniqueId()) && !p.hasPermission("wards.admin")) {
                p.sendMessage(Msg.c("&cOnly owner or admin.")); return true;
            }
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
            if (op.getUniqueId() == null) { p.sendMessage(Msg.c("&cUnknown player.")); return true; }
            manager.removeMember(near.id(), op.getUniqueId());
            p.sendMessage(Msg.c("&aRemoved " + args[1] + " from members."));
            return true;
        }

        sender.sendMessage(Msg.c("&cUnknown subcommand. Try /ward help"));
        return true;
    }
}
