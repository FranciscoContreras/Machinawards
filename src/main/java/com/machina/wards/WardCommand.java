package com.machina.wards;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6Wards help:"));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/ward help &7- Show this"));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/ward reload &7- Reload config"));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/ward shop &7- Open ward shop"));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/ward list &7- List your wards"));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/ward addmember <name> &7- Add member to nearby ward"));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/ward removemember <name> &7- Remove member from nearby ward"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("wards.admin")) { sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cNo permission.")); return true; }
            plugin.reloadConfig();
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aConfig reloaded."));
            return true;
        }

        if (args[0].equalsIgnoreCase("shop")) {
            if (!(sender instanceof Player p)) { sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cPlayers only.")); return true; }
            if (plugin.economy() == null) { sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cShop disabled.")); return true; }
            new ShopMenuListener(plugin, manager, plugin.tierKey(), plugin.economy()).open(p);
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cPlayers only for this command."));
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            int count = 0;
            for (Ward w : manager.all()) {
                if (w.owner().equals(p.getUniqueId())) {
                    p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7- &f" + w.tier() + " &7at &f" + w.bx() + "," + w.by() + "," + w.bz() + " &7radius &f" + w.radius()));
                    count++;
                }
            }
            if (count == 0) p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7You own no wards."));
            return true;
        }

        if (args[0].equalsIgnoreCase("addmember") && args.length >= 2) {
            Ward near = manager.findAt(p.getLocation());
            if (near == null) { p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cStand inside a ward to manage it.")); return true; }
            if (!near.owner().equals(p.getUniqueId()) && !p.hasPermission("wards.admin")) { p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cOnly owner or admin.")); return true; }
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
            manager.addMember(near.id(), op.getUniqueId());
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aAdded " + args[1] + " to members."));
            return true;
        }

        if (args[0].equalsIgnoreCase("removemember") && args.length >= 2) {
            Ward near = manager.findAt(p.getLocation());
            if (near == null) { p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cStand inside a ward to manage it.")); return true; }
            if (!near.owner().equals(p.getUniqueId()) && !p.hasPermission("wards.admin")) { p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cOnly owner or admin.")); return true; }
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
            manager.removeMember(near.id(), op.getUniqueId());
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aRemoved " + args[1] + " from members."));
            return true;
        }

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cUnknown subcommand. Try /ward help"));
        return true;
    }
}
