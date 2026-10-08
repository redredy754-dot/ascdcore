package dev.ascendant.core.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;

/** Cancels everything inside AscendantCore menus. Editor menus only allow their own item slots. */
public final class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Menu menu)) return;
        if (!(e.getWhoClicked() instanceof Player p)) { e.setCancelled(true); return; }
        int raw = e.getRawSlot();
        boolean inTop = raw >= 0 && raw < top.getSize();

        if (menu.isEditor()) {
            if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR || e.getClick() == ClickType.DOUBLE_CLICK) {
                e.setCancelled(true);
                return;
            }
            if (!inTop) return;                       // own inventory is free to use
            if (menu.editable(raw)) return;           // item slots behave normally
            e.setCancelled(true);                     // buttons / panes
            menu.click(raw, e.getClick());
            return;
        }

        e.setCancelled(true);
        if (!inTop) return;
        menu.click(raw, e.getClick());
        if (p.getOpenInventory().getTopInventory().getHolder() == menu) menu.render();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Menu menu)) return;
        if (!menu.isEditor()) { e.setCancelled(true); return; }
        for (int raw : e.getRawSlots()) {
            if (raw < top.getSize() && !menu.editable(raw)) { e.setCancelled(true); return; }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Menu m && e.getPlayer() instanceof Player p) m.closed(p);
    }
}
