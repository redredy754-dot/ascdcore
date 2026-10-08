package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;

/** Merges newly spawned XP orbs into the nearest existing orb. Total XP is always preserved. */
public final class ClumpModule implements Listener {
    private final AscendantCore plugin;

    public ClumpModule(AscendantCore plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent e) {
        if (!(e.getEntity() instanceof ExperienceOrb orb) || !plugin.enabled("clumps")) return;
        double radius = plugin.getConfig().getDouble("modules.clumps.radius", 4.0);
        int max = plugin.getConfig().getInt("modules.clumps.max-orb-value", 2000);
        ExperienceOrb best = null;
        double bestDist = Double.MAX_VALUE;
        for (ExperienceOrb o : orb.getLocation().getNearbyEntitiesByType(ExperienceOrb.class, radius)) {
            if (o == orb || !o.isValid() || o.isDead()) continue;
            if ((long) o.getExperience() + orb.getExperience() > max) continue;
            double d = o.getLocation().distanceSquared(orb.getLocation());
            if (d < bestDist) { bestDist = d; best = o; }
        }
        if (best == null) return;
        best.setExperience(best.getExperience() + orb.getExperience());
        e.setCancelled(true);
    }
}
