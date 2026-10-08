package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Item stacker built as an optimization: nearby identical drops become ONE entity that carries the real total
 * (e.g. "Leather - 520x"). The visible ItemStack never exceeds its normal stack size, the extra amount is kept
 * in the entity's data, and a pickup hands the whole pile over in one go. Totals are always preserved.
 */
public final class StackerModule implements Listener {
    private final AscendantCore plugin;
    private final NamespacedKey totalKey;
    private final NamespacedKey labelKey;

    // cached config (rebuilt on every config change, so events never touch the YAML)
    private boolean on;
    private boolean white;
    private boolean showName;
    private double radius = 3.0;
    private int max = 1000;
    private final Set<Material> listed = EnumSet.noneOf(Material.class);

    public StackerModule(AscendantCore plugin) {
        this.plugin = plugin;
        this.totalKey = new NamespacedKey(plugin, "stack_total");
        this.labelKey = new NamespacedKey(plugin, "stack_label");
        reload();
    }

    public void reload() {
        var c = plugin.getConfig();
        on = plugin.enabled("item-stacker");
        white = "WHITELIST".equalsIgnoreCase(c.getString("modules.item-stacker.mode", "BLACKLIST"));
        showName = c.getBoolean("modules.item-stacker.show-name", true);
        radius = c.getDouble("modules.item-stacker.radius", 3.0);
        max = Math.max(2, Math.min(100_000, c.getInt("modules.item-stacker.max-stack-size", 1000)));
        listed.clear();
        for (String s : c.getStringList("modules.item-stacker.list")) {
            Material m = Material.matchMaterial(s);
            if (m != null) listed.add(m);
        }
    }

    public boolean isListed(Material m) { return listed.contains(m); }

    public void toggle(Material m) {
        if (!listed.remove(m)) listed.add(m);
        List<String> names = new ArrayList<>();
        listed.forEach(x -> names.add(x.name()));
        Collections.sort(names);
        plugin.getConfig().set("modules.item-stacker.list", names);
        plugin.saveConfig();
    }

    private boolean allowed(Material m) { return white == listed.contains(m); }

    private int total(Item item) {
        return item.getPersistentDataContainer().getOrDefault(totalKey, PersistentDataType.INTEGER, item.getItemStack().getAmount());
    }

    private void setTotal(Item item, int total) {
        ItemStack st = item.getItemStack();
        int visible = Math.min(total, st.getMaxStackSize());
        if (st.getAmount() != visible) {
            st.setAmount(visible);
            item.setItemStack(st);
        }
        PersistentDataContainer pdc = item.getPersistentDataContainer();
        if (total > visible) pdc.set(totalKey, PersistentDataType.INTEGER, total); else pdc.remove(totalKey);
        label(item, total);
    }

    private void label(Item item, int total) {
        PersistentDataContainer pdc = item.getPersistentDataContainer();
        if (showName && total > 1) {
            item.customName(Component.translatable(item.getItemStack().getType().translationKey(), NamedTextColor.WHITE)
                    .append(Component.text(" - " + total + "x", NamedTextColor.YELLOW)));
            item.setCustomNameVisible(true);
            pdc.set(labelKey, PersistentDataType.BYTE, (byte) 1);
        } else if (pdc.has(labelKey, PersistentDataType.BYTE)) {
            item.customName(null);
            item.setCustomNameVisible(false);
            pdc.remove(labelKey);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent e) {
        if (!on) return;
        Item item = e.getEntity();
        ItemStack s = item.getItemStack();
        if (!allowed(s.getType())) return;
        int amount = s.getAmount();
        for (Item other : item.getLocation().getNearbyEntitiesByType(Item.class, radius)) {
            if (other == item || !other.isValid()) continue;
            if (!other.getItemStack().isSimilar(s)) continue;
            long sum = (long) total(other) + amount;
            if (sum > max) continue;
            setTotal(other, (int) sum);
            e.setCancelled(true);
            return;
        }
        if (showName && amount > 1) label(item, amount);
    }

    /** Vanilla merging would destroy the hidden amount, so it is blocked for stacked piles only. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMerge(ItemMergeEvent e) {
        if (!on) return;
        boolean hidden = e.getEntity().getPersistentDataContainer().has(totalKey, PersistentDataType.INTEGER)
                || e.getTarget().getPersistentDataContainer().has(totalKey, PersistentDataType.INTEGER);
        if (hidden) {
            e.setCancelled(true);
            return;
        }
        Item target = e.getTarget();
        Bukkit.getScheduler().runTask(plugin, () -> { if (target.isValid()) label(target, target.getItemStack().getAmount()); });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        Item item = e.getItem();
        PersistentDataContainer pdc = item.getPersistentDataContainer();
        Integer total = pdc.get(totalKey, PersistentDataType.INTEGER);
        if (total == null) {
            if (pdc.has(labelKey, PersistentDataType.BYTE)) {
                Bukkit.getScheduler().runTask(plugin, () -> { if (item.isValid()) label(item, item.getItemStack().getAmount()); });
            }
            return;
        }
        e.setCancelled(true);                       // we hand the pile over ourselves
        if (e.getEntity() instanceof Player p) give(p, item, total);
    }

    private void give(Player p, Item item, int total) {
        ItemStack base = item.getItemStack();
        int step = base.getMaxStackSize();
        int remaining = total;
        int given = 0;
        while (remaining > 0) {
            int n = Math.min(remaining, step);
            ItemStack chunk = base.clone();
            chunk.setAmount(n);
            Map<Integer, ItemStack> left = p.getInventory().addItem(chunk);
            if (!left.isEmpty()) {
                int notAdded = left.values().iterator().next().getAmount();
                given += n - notAdded;
                remaining = remaining - n + notAdded;
                break;
            }
            given += n;
            remaining -= n;
        }
        if (given > 0) p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.2f, 1.6f);
        if (remaining <= 0) item.remove();
        else if (remaining != total) setTotal(item, remaining);
    }

    /** Hoppers would delete the hidden amount, so piles with extra stock are left for players. */
    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryPickupItemEvent e) {
        if (e.getItem().getPersistentDataContainer().has(totalKey, PersistentDataType.INTEGER)) e.setCancelled(true);
    }
}
