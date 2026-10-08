package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import java.util.HashMap;
import java.util.Map;

/** Pot timer: overrides how long a chosen potion's effect lasts (e.g. Strength II for 8 minutes). */
public final class PotTimerModule implements Listener {
    private final AscendantCore plugin;
    private final Map<String, Integer> seconds = new HashMap<>();

    public PotTimerModule(AscendantCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        seconds.clear();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("modules.pot-timer.entries");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            String type = s.getString(k + ".type", "");
            int sec = s.getInt(k + ".seconds");
            if (!type.isEmpty() && sec > 0) seconds.put(type, sec);
        }
    }

    private PotionType typeOf(ItemStack it) {
        return it != null && it.getItemMeta() instanceof PotionMeta pm ? pm.getBasePotionType() : null;
    }

    private void apply(LivingEntity target, PotionType type, double seconds, double scale) {
        for (PotionEffect ef : type.getPotionEffects()) {
            if (ef.getType().isInstant()) continue;
            int ticks = (int) Math.max(1, seconds * 20 * scale);
            target.addPotionEffect(new PotionEffect(ef.getType(), ticks, ef.getAmplifier(), ef.isAmbient(), ef.hasParticles(), ef.hasIcon()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent e) {
        if (!plugin.enabled("pot-timer")) return;
        PotionType t = typeOf(e.getItem());
        Integer s = t == null ? null : seconds.get(t.getKey().getKey());
        if (s == null) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> apply(p, t, s, 1.0));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent e) {
        if (!plugin.enabled("pot-timer")) return;
        PotionType t = typeOf(e.getPotion().getItem());
        Integer s = t == null ? null : seconds.get(t.getKey().getKey());
        if (s == null) return;
        for (LivingEntity le : e.getAffectedEntities()) {
            double intensity = e.getIntensity(le);
            Bukkit.getScheduler().runTask(plugin, () -> apply(le, t, s, intensity));
        }
    }
}
