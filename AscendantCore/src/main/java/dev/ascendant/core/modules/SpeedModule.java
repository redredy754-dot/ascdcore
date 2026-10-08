package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.Inventory;

/** Insta smelt (furnace speed multiplier) and insta brew (brewing stand time). */
public final class SpeedModule implements Listener {
    private final AscendantCore plugin;

    public SpeedModule(AscendantCore plugin) { this.plugin = plugin; }

    @EventHandler
    public void onSmelt(FurnaceStartSmeltEvent e) {
        if (!plugin.enabled("insta-smelt")) return;
        double speed = Math.max(1.0, plugin.getConfig().getDouble("modules.insta-smelt.speed", 10.0));
        e.setTotalCookTime((int) Math.max(1, Math.round(e.getTotalCookTime() / speed)));
    }

    private void check(Inventory inv) {
        if (!plugin.enabled("insta-brew") || !(inv instanceof BrewerInventory bi)) return;
        Location loc = bi.getLocation();
        if (loc == null) return;
        int ticks = Math.max(1, Math.min(400, plugin.getConfig().getInt("modules.insta-brew.ticks", 5)));
        for (long delay : new long[]{2L, 5L}) {   // the stand starts brewing on its own tick, so check twice
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Block b = loc.getBlock();
                if (b.getState() instanceof BrewingStand bs && bs.getBrewingTime() > ticks) {
                    bs.setBrewingTime(ticks);
                    bs.update();
                }
            }, delay);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) { check(e.getView().getTopInventory()); }

    @EventHandler
    public void onDrag(InventoryDragEvent e) { check(e.getView().getTopInventory()); }

    @EventHandler
    public void onHopper(InventoryMoveItemEvent e) { check(e.getDestination()); }
}
