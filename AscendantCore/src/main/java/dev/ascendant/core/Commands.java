package dev.ascendant.core;

import dev.ascendant.core.gui.MainMenu;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class Commands implements TabExecutor {
    private final AscendantCore plugin;
    private final Map<UUID, Long> stringCooldown = new HashMap<>();

    public Commands(AscendantCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        return switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "string" -> string(sender);
            case "withdrawheart" -> withdraw(sender, args);
            case "ascdrestart" -> restart(sender, args);
            default -> main(sender, args);
        };
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("ascendantcore.admin")) return true;
        plugin.msg(s, "<red>You don't have permission.");
        return false;
    }

    private boolean main(CommandSender s, String[] a) {
        if (!admin(s)) return true;
        String sub = a.length == 0 ? "gui" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "gui" -> {
                if (s instanceof Player p) new MainMenu(plugin, p).open();
                else plugin.msg(s, "<red>Only players can open the GUI.");
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.changed();
                plugin.msg(s, "<green>Config reloaded.");
            }
            case "revive" -> {
                Player t = a.length > 1 ? plugin.getServer().getPlayerExact(a[1]) : null;
                if (t == null) { plugin.msg(s, "<red>Usage: /ascendantcore revive <online player>"); break; }
                plugin.lifesteal().revive(t);
                plugin.deathBan().revive(t);
                plugin.msg(s, "<green>Revived " + t.getName() + ".");
            }
            case "restart" -> restart(s, java.util.Arrays.copyOfRange(a, 1, a.length));
            default -> plugin.msg(s, "<gray>/ascendantcore <gui|reload|revive|restart>");
        }
        return true;
    }

    /** /ascdrestart            start the restart countdown
     *  /ascdrestart cancel     stop it
     *  /ascdrestart status     show RAM / TPS / uptime and whether it would use the restart script */
    private boolean restart(CommandSender s, String[] a) {
        if (!admin(s)) return true;
        String sub = a.length == 0 ? "now" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "cancel" -> plugin.restarter().cancel();
            case "status" -> plugin.restarter().status().forEach(line -> plugin.msg(s, "<gray>" + line));
            default -> {
                plugin.restarter().start("manual (" + s.getName() + ")");
                plugin.msg(s, "<green>Restart countdown started. Use /ascdrestart cancel to stop it.");
            }
        }
        return true;
    }

    /** Fills every empty slot of your inventory with a stack of string. */
    private boolean string(CommandSender s) {
        if (!(s instanceof Player p)) return true;
        if (!p.hasPermission("ascendantcore.string")) { plugin.msg(p, "<red>You don't have permission."); return true; }
        if (!plugin.enabled("string-command")) { plugin.msg(p, "<red>This command is disabled."); return true; }
        long cooldown = plugin.getConfig().getInt("modules.string-command.cooldown-seconds", 30) * 1000L;
        long now = System.currentTimeMillis();
        Long until = stringCooldown.get(p.getUniqueId());
        if (until != null && until > now) {
            plugin.msg(p, "<red>Wait " + (int) Math.ceil((until - now) / 1000.0) + "s before using /string again.");
            return true;
        }
        PlayerInventory inv = p.getInventory();
        int filled = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) {
                inv.setItem(i, new ItemStack(Material.STRING, 64));
                filled++;
            }
        }
        stringCooldown.put(p.getUniqueId(), now + cooldown);
        plugin.msg(p, filled == 0 ? "<red>Your inventory is full." : "<green>Filled " + filled + " slot(s) with string.");
        return true;
    }

    private boolean withdraw(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) return true;
        if (!p.hasPermission("ascendantcore.lifesteal.withdraw")) { plugin.msg(p, "<red>You don't have permission."); return true; }
        int n = 1;
        if (a.length > 0) {
            try { n = Integer.parseInt(a[0]); } catch (NumberFormatException e) { plugin.msg(p, "<red>Usage: /withdrawheart [amount]"); return true; }
        }
        plugin.lifesteal().withdraw(p, n);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission("ascendantcore.admin")) return out;
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (name.equals("ascendantcore")) {
            if (a.length == 1) for (String o : List.of("gui", "reload", "revive", "restart"))
                if (o.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(o);
            if (a.length == 2 && a[0].equalsIgnoreCase("restart")) { out.add("cancel"); out.add("status"); }
            if (a.length == 2 && a[0].equalsIgnoreCase("revive")) plugin.getServer().getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
        } else if (name.equals("ascdrestart") && a.length == 1) {
            out.add("cancel");
            out.add("status");
        }
        return out;
    }
}
