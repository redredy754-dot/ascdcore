package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** One player sleeping for the full 5 seconds skips the night (and clears storms). */
public final class SleepModule implements Listener {
    private final AscendantCore plugin;
    private final Set<UUID> pending = new HashSet<>();

    public SleepModule(AscendantCore plugin) { this.plugin = plugin; }

    @EventHandler
    public void onBed(PlayerBedEnterEvent e) {
        if (!plugin.enabled("one-player-sleep") || e.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;
        Player p = e.getPlayer();
        World w = p.getWorld();
        if (!pending.add(w.getUID())) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pending.remove(w.getUID());
            if (!p.isOnline() || !p.isSleeping() || !p.getWorld().equals(w)) return;
            boolean night = w.getTime() >= 12500;
            if (night) w.setTime(0);
            if (w.hasStorm() && (night || w.isThundering())) {
                w.setStorm(false);
                w.setThundering(false);
            }
        }, 101L);
    }
}
