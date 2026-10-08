package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitTask;

/** Clears dropped items every N seconds with editable chat messages (config.yml: messages.cleaner-*). */
public final class CleanerModule {
    private final AscendantCore plugin;
    private BukkitTask task;
    private int remaining;
    private int interval;

    public CleanerModule(AscendantCore plugin) { this.plugin = plugin; }

    public void reload() {
        int newInterval = Math.max(10, plugin.getConfig().getInt("modules.server-cleaner.interval-seconds", 300));
        if (!plugin.enabled("server-cleaner")) {
            if (task != null) task.cancel();
            task = null;
            return;
        }
        if (task != null && newInterval == interval) return;   // nothing changed: keep the running countdown
        if (task != null) task.cancel();
        interval = newInterval;
        remaining = interval;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        remaining--;
        if (remaining <= 0) {
            clean();
            remaining = interval;
            return;
        }
        if (plugin.getConfig().getIntegerList("modules.server-cleaner.warn-seconds").contains(remaining)) {
            Bukkit.broadcast(Text.mm(plugin.getConfig().getString("messages.prefix", "")
                            + plugin.getConfig().getString("messages.cleaner-warning", "<yellow>Cleaning items in <time>"),
                    Placeholder.unparsed("time", format(remaining))));
        }
    }

    private void clean() {
        int grace = plugin.getConfig().getInt("modules.server-cleaner.ignore-newer-than-seconds", 5) * 20;
        int count = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Item item : w.getEntitiesByClass(Item.class)) {
                if (item.getTicksLived() < grace) continue;
                count += item.getItemStack().getAmount();
                item.remove();
            }
        }
        Bukkit.broadcast(Text.mm(plugin.getConfig().getString("messages.prefix", "")
                        + plugin.getConfig().getString("messages.cleaner-done", "<green>Cleaned <count> items."),
                Placeholder.unparsed("count", String.valueOf(count))));
    }

    static String format(int s) {
        if (s >= 60 && s % 60 == 0) return (s / 60) + (s / 60 == 1 ? " min" : " mins");
        return s + (s == 1 ? " sec" : " secs");
    }
}
