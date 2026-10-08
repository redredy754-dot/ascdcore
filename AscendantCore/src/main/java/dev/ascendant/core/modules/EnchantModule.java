package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Fx;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

/** Global enchant limiter + mace enchant limiter + mace craft limiter. */
public final class EnchantModule implements Listener {
    private static final String MACE = "modules.mace.limiter";
    private final AscendantCore plugin;

    public EnchantModule(AscendantCore plugin) { this.plugin = plugin; }

    private int limit(Enchantment e, Material item) {
        String key = e.getKey().getKey();
        int lim = Integer.MAX_VALUE;
        if (plugin.enabled("enchant-limiter")) {
            lim = plugin.getConfig().getInt("modules.enchant-limiter.limits." + key, e.getMaxLevel());
        }
        if (item == Material.MACE && plugin.enabled("mace.enchant-limiter")) {
            int m = plugin.getConfig().getInt("modules.mace.enchant-limiter.limits." + key, Integer.MAX_VALUE);
            lim = Math.min(lim, m);
        }
        return lim;
    }

    private boolean anyLimiter() {
        return plugin.enabled("enchant-limiter") || plugin.enabled("mace.enchant-limiter");
    }

    /** Returns a capped copy, or null when nothing needed changing. */
    private ItemStack cap(ItemStack item) {
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) return null;
        boolean changed = false;
        if (meta instanceof EnchantmentStorageMeta sm) {
            for (Map.Entry<Enchantment, Integer> en : new HashMap<>(sm.getStoredEnchants()).entrySet()) {
                int lim = limit(en.getKey(), item.getType());
                if (en.getValue() > lim) {
                    sm.removeStoredEnchant(en.getKey());
                    if (lim > 0) sm.addStoredEnchant(en.getKey(), lim, true);
                    changed = true;
                }
            }
        } else {
            for (Map.Entry<Enchantment, Integer> en : new HashMap<>(meta.getEnchants()).entrySet()) {
                int lim = limit(en.getKey(), item.getType());
                if (en.getValue() > lim) {
                    meta.removeEnchant(en.getKey());
                    if (lim > 0) meta.addEnchant(en.getKey(), lim, true);
                    changed = true;
                }
            }
        }
        if (!changed) return null;
        copy.setItemMeta(meta);
        return copy;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent e) {
        if (e.getView().getPlayer() instanceof Player p && plugin.prompts().isActive(p)) return;
        if (!anyLimiter()) return;
        ItemStack r = e.getResult();
        if (r == null || r.getType().isAir()) return;
        ItemStack capped = cap(r);
        if (capped != null) e.setResult(capped);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        if (!anyLimiter()) return;
        Material type = e.getItem().getType();
        Map<Enchantment, Integer> add = e.getEnchantsToAdd();
        for (Map.Entry<Enchantment, Integer> en : new HashMap<>(add).entrySet()) {
            int lim = limit(en.getKey(), type);
            if (lim <= 0) add.remove(en.getKey());
            else if (en.getValue() > lim) add.put(en.getKey(), lim);
        }
    }

    private boolean maceLimitReached() {
        if (!plugin.enabled("mace.limiter")) return false;
        return plugin.getConfig().getInt(MACE + ".crafted") >= plugin.getConfig().getInt(MACE + ".max-maces");
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        Recipe r = e.getRecipe();
        if (r != null && r.getResult().getType() == Material.MACE && maceLimitReached()) e.getInventory().setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (e.getRecipe().getResult().getType() != Material.MACE || !plugin.enabled("mace.limiter")) return;
        if (maceLimitReached()) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player p) {
                plugin.msg(p, "<red>The server's mace limit has been reached.");
                Fx.error(p);
            }
            return;
        }
        plugin.getConfig().set(MACE + ".crafted", plugin.getConfig().getInt(MACE + ".crafted") + 1);
        plugin.saveConfig();
    }
}
