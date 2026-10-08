package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Text;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;

/** Dimension ban: portals into the Nether and/or the End are blocked. */
public final class DimensionModule implements Listener {
    private final AscendantCore plugin;

    public DimensionModule(AscendantCore plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        if (e.getTo() == null || e.getTo().getWorld() == null) return;
        if (e.getPlayer().hasPermission("ascendantcore.bypass.dimension")) return;
        World.Environment env = e.getTo().getWorld().getEnvironment();
        var c = plugin.getConfig();
        boolean nether = env == World.Environment.NETHER && c.getBoolean("modules.dimension-ban.nether");
        boolean end = env == World.Environment.THE_END && c.getBoolean("modules.dimension-ban.end");
        if (nether || end) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(Text.mm("<red>The " + (nether ? "Nether" : "End") + " is disabled on this server."));
        }
    }
}
