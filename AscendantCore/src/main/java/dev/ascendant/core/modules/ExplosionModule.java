package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;

public final class ExplosionModule implements Listener {
    private static final String BASE = "modules.explosion-control.";
    private final AscendantCore plugin;

    public ExplosionModule(AscendantCore plugin) { this.plugin = plugin; }

    private String source(Entity e) {
        EntityType t = e.getType();
        if (t == EntityType.CREEPER) return "creeper";
        if (t == EntityType.TNT) return "tnt";
        if (t == EntityType.TNT_MINECART) return "tnt-minecart";
        if (t == EntityType.END_CRYSTAL) return "end-crystal";
        return "other";
    }

    private double power(String src) {
        return plugin.getConfig().getDouble(BASE + src + ".power", 1.0) * plugin.getConfig().getDouble(BASE + "global.power", 1.0);
    }

    private boolean destroys(String src) {
        return plugin.getConfig().getBoolean(BASE + src + ".destruction", true)
                && plugin.getConfig().getBoolean(BASE + "global.destruction", true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrime(ExplosionPrimeEvent e) {
        if (!plugin.enabled("explosion-control")) return;
        e.setRadius((float) (e.getRadius() * power(source(e.getEntity()))));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (!plugin.enabled("explosion-control")) return;
        if (!destroys(source(e.getEntity()))) e.blockList().clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!plugin.enabled("explosion-control")) return;
        if (!destroys("other")) e.blockList().clear();
    }
}
