package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;

/** Anti crystal pvp: disable crystals / anchors, or scale their damage and block destruction. */
public final class AntiCrystalModule implements Listener {
    private static final String B = "modules.anti-crystal.";
    private final AscendantCore plugin;
    private int lastAnchorTick = -1;

    public AntiCrystalModule(AscendantCore plugin) { this.plugin = plugin; }

    private boolean on() { return plugin.enabled("anti-crystal"); }
    private boolean flag(String k) { return plugin.getConfig().getBoolean(B + k); }
    private double num(String k) { return plugin.getConfig().getDouble(B + k, 1.0); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrystalPlace(EntityPlaceEvent e) {
        if (on() && flag("disable-crystals") && e.getEntityType() == EntityType.END_CRYSTAL) {
            e.setCancelled(true);
            if (e.getPlayer() != null) e.getPlayer().sendActionBar(Text.mm("<red>End crystals are disabled."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnchorPlace(BlockPlaceEvent e) {
        if (on() && flag("disable-anchors") && e.getBlockPlaced().getType() == Material.RESPAWN_ANCHOR) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(Text.mm("<red>Respawn anchors are disabled."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnchorUse(PlayerInteractEvent e) {
        if (on() && flag("disable-anchors") && e.getAction() == Action.RIGHT_CLICK_BLOCK
                && e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.RESPAWN_ANCHOR) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrystalDamage(EntityDamageByEntityEvent e) {
        if (on() && e.getDamager() instanceof EnderCrystal) e.setDamage(e.getDamage() * num("crystal-damage"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrystalExplode(EntityExplodeEvent e) {
        if (on() && e.getEntityType() == EntityType.END_CRYSTAL && !flag("crystal-destruction")) e.blockList().clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnchorExplode(BlockExplodeEvent e) {
        if (!on() || e.getExplodedBlockState().getType() != Material.RESPAWN_ANCHOR) return;
        lastAnchorTick = Bukkit.getCurrentTick();
        if (!flag("anchor-destruction")) e.blockList().clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnchorDamage(EntityDamageEvent e) {
        if (on() && e.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION && lastAnchorTick == Bukkit.getCurrentTick()) {
            e.setDamage(e.getDamage() * num("anchor-damage"));
        }
    }
}
