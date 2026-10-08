package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitTask;

import java.lang.management.ManagementFactory;
import java.time.LocalTime;
import java.util.List;

/** Restart by RAM %, RAM MB, TPS or time of day, with an announcer and damage protection during the last seconds. */
public final class RestarterModule implements Listener {
    private final AscendantCore plugin;
    private BukkitTask task;
    private int counter;
    private int countdown = -1;
    private int ramPctHits;
    private int ramMbHits;

    public RestarterModule(AscendantCore plugin) { this.plugin = plugin; }

    private boolean cfgBool(String p) { return plugin.getConfig().getBoolean("modules.server-restarter." + p); }
    private int cfgInt(String p) { return plugin.getConfig().getInt("modules.server-restarter." + p); }

    public boolean running() { return countdown >= 0; }

    /** The ticker always runs so manual restarts work even when the automatic triggers are switched off. */
    public void reload() {
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void start(String reason) {
        if (running()) return;
        countdown = Math.max(5, cfgInt("countdown-seconds"));
        plugin.getLogger().info("Restart countdown started: " + reason);
        announce();
    }

    public void cancel() {
        if (!running()) return;
        countdown = -1;
        Bukkit.broadcast(Text.mm(plugin.getConfig().getString("messages.prefix", "") + "<green>Restart cancelled."));
    }

    private void tick() {
        if (running()) {
            countdown--;
            if (countdown <= 0) { finish(); return; }
            announce();
            return;
        }
        if (!plugin.enabled("server-restarter")) return;
        counter++;
        if (cfgBool("scheduled.enabled")) checkSchedule();
        if (counter % 30 == 0) checkResources();
    }

    private void announce() {
        List<Integer> at = plugin.getConfig().getIntegerList("modules.server-restarter.announce-at");
        if (!at.contains(countdown)) return;
        Bukkit.broadcast(Text.mm(plugin.getConfig().getString("messages.prefix", "")
                        + plugin.getConfig().getString("messages.restart-announce", "<red>Restarting in <time>"),
                Placeholder.unparsed("time", CleanerModule.format(countdown))));
    }

    private void checkSchedule() {
        int now = LocalTime.now().toSecondOfDay();
        int lead = Math.max(5, cfgInt("countdown-seconds"));
        for (String s : plugin.getConfig().getStringList("modules.server-restarter.scheduled.times")) {
            try {
                int target = LocalTime.parse(s.length() == 4 ? "0" + s : s).toSecondOfDay();
                int startAt = (target - lead + 86400) % 86400;
                int diff = (now - startAt + 86400) % 86400;
                if (diff < 3) { start("scheduled " + s); return; }
            } catch (Exception ignored) { }
        }
    }

    private void checkResources() {
        long uptimeMin = ManagementFactory.getRuntimeMXBean().getUptime() / 60000L;
        if (uptimeMin < cfgInt("min-uptime-minutes")) return;
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        if (cfgBool("ram-percent.enabled")) {
            double pct = used * 100.0 / rt.maxMemory();
            ramPctHits = pct >= cfgInt("ram-percent.value") ? ramPctHits + 1 : 0;
            if (ramPctHits >= Math.max(1, cfgInt("ram-percent.checks"))) { start("RAM at " + Math.round(pct) + "%"); return; }
        }
        if (cfgBool("ram-mb.enabled")) {
            long mb = used / (1024 * 1024);
            ramMbHits = mb >= cfgInt("ram-mb.value") ? ramMbHits + 1 : 0;
            if (ramMbHits >= 3) { start("RAM at " + mb + " MB"); return; }
        }
        if (cfgBool("tps.enabled") && Bukkit.getTPS()[1] < plugin.getConfig().getDouble("modules.server-restarter.tps.value")) {
            start("low TPS");
        }
    }

    /** AUTO / SPIGOT_RESTART use Spigot's restart script when it exists; otherwise the server shuts down (panel restarts it). */
    private boolean useRestartScript() {
        String method = plugin.getConfig().getString("modules.server-restarter.method", "AUTO").toUpperCase();
        if (method.equals("SHUTDOWN")) return false;
        String script = Bukkit.spigot().getSpigotConfig().getString("settings.restart-script", "./start.sh");
        if (new java.io.File(script).exists()) return true;
        plugin.getLogger().warning("restart-script '" + script + "' not found - shutting down instead. "
                + "Your host/panel must start the server again (most panels do this automatically).");
        return false;
    }

    /** Used by /ascdrestart status so you can see whether the triggers would fire. */
    public List<String> status() {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        List<String> l = new java.util.ArrayList<>();
        l.add("Module: " + (plugin.enabled("server-restarter") ? "ON" : "OFF") + (running() ? "  (countdown: " + countdown + "s)" : ""));
        l.add("Uptime: " + ManagementFactory.getRuntimeMXBean().getUptime() / 60000L + " min (resource triggers wait for "
                + cfgInt("min-uptime-minutes") + " min)");
        l.add("RAM: " + used / (1024 * 1024) + " MB = " + Math.round(used * 100.0 / rt.maxMemory()) + "% of max");
        l.add("TPS (5m): " + String.format("%.2f", Bukkit.getTPS()[1]));
        l.add("Method: " + (useRestartScript() ? "restart script" : "shutdown"));
        return l;
    }

    private void finish() {
        for (World w : Bukkit.getWorlds()) w.save();
        Bukkit.savePlayers();
        var kick = Text.mm(plugin.getConfig().getString("messages.restart-kick", "<red>Restarting"));
        for (Player p : Bukkit.getOnlinePlayers()) p.kick(kick);
        boolean spigot = useRestartScript();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (spigot) Bukkit.spigot().restart(); else Bukkit.shutdown();
        }, 20L);
    }

    /** Nobody takes damage in the final seconds, so nobody dies mid-fight right before a restart. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (running() && e.getEntity() instanceof Player && countdown <= cfgInt("protect-seconds")) e.setCancelled(true);
    }
}
