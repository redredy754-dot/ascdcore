package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/** Player-placed cobwebs disappear after the configured number of seconds. */
public final class CobwebModule implements Listener {
    private final AscendantCore plugin;

    public CobwebModule(AscendantCore plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (e.getBlockPlaced().getType() != Material.COBWEB || !plugin.enabled("cobweb-cleaner")) return;
        Block b = e.getBlockPlaced();
        long ticks = Math.max(1, plugin.getConfig().getInt("modules.cobweb-cleaner.seconds", 10)) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4) && b.getType() == Material.COBWEB) {
                b.setType(Material.AIR);
            }
        }, ticks);
    }
}
