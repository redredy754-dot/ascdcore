package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Shield stunning + attribute swapping rules. */
public final class CombatModule implements Listener {
    private final AscendantCore plugin;
    private final Map<UUID, Integer> swapTick = new HashMap<>();

    public CombatModule(AscendantCore plugin) { this.plugin = plugin; }

    /** Shield stunning OFF: whatever disabled the shield is undone one tick later. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onShieldHit(EntityDamageByEntityEvent e) {
        if (plugin.enabled("shield-stunning")) return;
        if (e.getEntity() instanceof Player victim && victim.isBlocking()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (victim.isOnline()) victim.setCooldown(Material.SHIELD, 0);
            });
        }
    }

    @EventHandler
    public void onSwap(PlayerItemHeldEvent e) {
        swapTick.put(e.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
    }

    /** Attribute swapping OFF: a hit landing within 1 tick of a hotbar swap is cancelled. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (plugin.enabled("attribute-swapping") || !(e.getDamager() instanceof Player p)) return;
        Integer t = swapTick.get(p.getUniqueId());
        if (t != null && Bukkit.getCurrentTick() - t <= 1) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { swapTick.remove(e.getPlayer().getUniqueId()); }
}
