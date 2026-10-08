package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Combat extras: kill sound, head drops, and item cooldowns that only apply while a player is in combat. */
public final class CombatExtrasModule implements Listener {
    private final AscendantCore plugin;

    public CombatExtrasModule(AscendantCore plugin) { this.plugin = plugin; }

    private boolean on() { return plugin.enabled("combat"); }

    private static Sound sound(String name) {
        return switch (name.toUpperCase()) {
            case "DRAGON_GROWL" -> Sound.ENTITY_ENDER_DRAGON_GROWL;
            case "WITHER_SPAWN" -> Sound.ENTITY_WITHER_SPAWN;
            case "WITHER_DEATH" -> Sound.ENTITY_WITHER_DEATH;
            case "SKELETON_HURT" -> Sound.ENTITY_SKELETON_HURT;
            case "DRAGON_FIREBALL_EXPLODE" -> Sound.ENTITY_DRAGON_FIREBALL_EXPLODE;
            case "XP_PICKUP" -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            case "ANVIL_LAND" -> Sound.BLOCK_ANVIL_LAND;
            default -> null;
        };
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(PlayerDeathEvent e) {
        if (!on()) return;
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;
        Location loc = victim.getLocation();

        Sound s = sound(plugin.getConfig().getString("modules.combat.kill-sound", "NONE"));
        if (s != null && loc.getWorld() != null) loc.getWorld().playSound(loc, s, 4f, 1f);

        if (plugin.getConfig().getBoolean("modules.combat.head-drop")) {
            int chance = plugin.getConfig().getInt("modules.combat.head-drop-chance", 100);
            if (ThreadLocalRandom.current().nextInt(100) < chance) {
                ItemStack head = plugin.heads().playerHead(victim);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                meta.displayName(Text.mm("<white><bold>" + victim.getName() + "'s Head"));
                meta.lore(List.of(Text.mm("<gray>Killed by <white>" + killer.getName())));
                head.setItemMeta(meta);
                e.getDrops().add(head);               // dropped once, together with the death drops
            }
        }
    }

    // ---------- item cooldowns while in combat ----------

    private int secondsFor(Material m) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("modules.combat.cooldowns.entries");
        if (s == null) return 0;
        for (String k : s.getKeys(false)) {
            if (m.name().equalsIgnoreCase(s.getString(k + ".item", ""))) return s.getInt(k + ".seconds");
        }
        return 0;
    }

    /** The first use still goes through; the cooldown starts right after it. */
    private void startCooldown(Player p, Material m) {
        if (!on() || !plugin.combat().inCombat(p)) return;
        int seconds = secondsFor(m);
        if (seconds <= 0 || p.getCooldown(m) > 0) return;
        Bukkit.getScheduler().runTask(plugin, () -> p.setCooldown(m, seconds * 20));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent e) {
        if (e.getItem() == null || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        Material m = e.getItem().getType();
        if (m.isEdible() || m == Material.POTION) return;      // eating / drinking is handled below
        startCooldown(e.getPlayer(), m);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        startCooldown(e.getPlayer(), e.getItem().getType());
    }
}
