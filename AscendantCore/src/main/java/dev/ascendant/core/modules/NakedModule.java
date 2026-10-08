package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * No naked killing. A player is protected only if ALL of these hold: no armor for N minutes, only "poor" items,
 * not in combat (stripping mid-fight does not protect you) and not (invisible + holding a sword). Everything is configurable.
 */
public final class NakedModule implements Listener {
    private static final Set<Material> VALUABLE = EnumSet.of(Material.ELYTRA, Material.TOTEM_OF_UNDYING,
            Material.ENDER_PEARL, Material.ENCHANTED_GOLDEN_APPLE, Material.GOLDEN_APPLE, Material.END_CRYSTAL,
            Material.MACE, Material.TRIDENT, Material.EXPERIENCE_BOTTLE, Material.POTION, Material.SPLASH_POTION,
            Material.LINGERING_POTION, Material.RESPAWN_ANCHOR, Material.TNT, Material.CROSSBOW, Material.SHIELD);

    private final AscendantCore plugin;
    private final Map<UUID, Long> nakedSince = new HashMap<>();

    public NakedModule(AscendantCore plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::scan, 100L, 100L);
    }

    private boolean hasArmor(Player p) {
        for (ItemStack a : p.getInventory().getArmorContents()) if (a != null && !a.getType().isAir()) return true;
        return false;
    }

    private void scan() {
        if (!plugin.enabled("no-naked-kill")) { nakedSince.clear(); return; }
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (hasArmor(p)) nakedSince.remove(p.getUniqueId());
            else nakedSince.putIfAbsent(p.getUniqueId(), now);
        }
    }

    private boolean valuable(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        String n = it.getType().name();
        return it.hasItemMeta() && it.getItemMeta().hasEnchants()
                || VALUABLE.contains(it.getType())
                || n.startsWith("NETHERITE_") || n.startsWith("DIAMOND_") || n.startsWith("IRON_") || n.startsWith("GOLDEN_");
    }

    private boolean poor(Player p) {
        for (ItemStack it : p.getInventory().getContents()) if (valuable(it)) return false;
        return true;
    }

    private boolean hasSword(Player p) {
        for (ItemStack it : p.getInventory().getContents()) if (it != null && it.getType().name().endsWith("_SWORD")) return true;
        return false;
    }

    private boolean protectedNaked(Player v) {
        var c = plugin.getConfig();
        Long since = nakedSince.get(v.getUniqueId());
        if (since == null || hasArmor(v)) return false;
        long needed = c.getInt("modules.no-naked-kill.naked-minutes", 30) * 60_000L;
        if (System.currentTimeMillis() - since < needed) return false;
        if (c.getBoolean("modules.no-naked-kill.require-poor-inventory", true) && !poor(v)) return false;
        if (c.getBoolean("modules.no-naked-kill.combat-removes-protection", true) && plugin.combat().inCombat(v)) return false;
        if (c.getBoolean("modules.no-naked-kill.invisible-sword-removes-protection", true)
                && v.hasPotionEffect(PotionEffectType.INVISIBILITY) && hasSword(v)) return false;
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!plugin.enabled("no-naked-kill") || !(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        Entity d = e.getDamager();
        if (d instanceof Player pl) attacker = pl;
        else if (d instanceof Projectile pr && pr.getShooter() instanceof Player pl) attacker = pl;
        if (attacker == null || attacker.equals(victim)) return;
        if (protectedNaked(victim)) {
            e.setCancelled(true);
            attacker.sendActionBar(Text.mm("<red>That player is naked and protected."));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { nakedSince.remove(e.getPlayer().getUniqueId()); }
}
