package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Enderchest edit: choose the container type shown for the ender chest (hopper / dispenser / chest / large chest).
 * Items live in one 54-slot store on the player, so switching types can never delete anything - slots that don't fit the
 * chosen type are simply hidden until a bigger type is chosen. The store is saved after every click and on close.
 */
public final class EnderModule implements Listener {
    private static final int STORE = 54;

    public static final class Holder implements InventoryHolder {
        final UUID id;
        Inventory inv;

        Holder(UUID id) { this.id = id; }

        @Override
        public Inventory getInventory() { return inv; }
    }

    private final AscendantCore plugin;
    private final NamespacedKey key;

    public EnderModule(AscendantCore plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "enderchest_store");
    }

    private ItemStack[] load(Player p) {
        byte[] b = p.getPersistentDataContainer().get(key, PersistentDataType.BYTE_ARRAY);
        if (b == null) return null;
        ItemStack[] data = new ItemStack[STORE];
        ItemStack[] got = ItemStack.deserializeItemsFromBytes(b);
        System.arraycopy(got, 0, data, 0, Math.min(got.length, STORE));
        return data;
    }

    private void store(Player p, ItemStack[] data) {
        p.getPersistentDataContainer().set(key, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(data));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (e.getInventory().getType() != InventoryType.ENDER_CHEST || !(e.getPlayer() instanceof Player p)) return;
        if (e.getInventory().getHolder() instanceof Player owner && !owner.equals(p)) return;   // admins viewing others
        ItemStack[] data = load(p);
        if (data == null && !plugin.enabled("enderchest-edit")) return;                         // plain vanilla
        e.setCancelled(true);
        if (data == null) {
            data = new ItemStack[STORE];
            ItemStack[] vanilla = p.getEnderChest().getContents();
            System.arraycopy(vanilla, 0, data, 0, Math.min(vanilla.length, STORE));
            store(p, data);                      // stored first ...
            p.getEnderChest().clear();           // ... cleared second: items exist in exactly one place
        }
        Bukkit.getScheduler().runTask(plugin, () -> openCustom(p));
    }

    private void openCustom(Player p) {
        if (!p.isOnline() || load(p) == null) return;
        String type = plugin.enabled("enderchest-edit")
                ? plugin.getConfig().getString("modules.enderchest-edit.type", "CHEST") : "LARGE_CHEST";
        Holder h = new Holder(p.getUniqueId());
        Component title = Component.text("Ender Chest");
        Inventory inv = switch (type.toUpperCase()) {
            case "HOPPER" -> Bukkit.createInventory(h, InventoryType.HOPPER, title);
            case "DISPENSER" -> Bukkit.createInventory(h, InventoryType.DISPENSER, title);
            case "CHEST" -> Bukkit.createInventory(h, 27, title);
            default -> Bukkit.createInventory(h, 54, title);
        };
        h.inv = inv;
        ItemStack[] data = load(p);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, data[i]);
        p.openInventory(inv);
    }

    private void save(Inventory inv) {
        if (!(inv.getHolder() instanceof Holder h)) return;
        Player p = Bukkit.getPlayer(h.id);
        if (p == null) return;
        ItemStack[] data = load(p);
        if (data == null) data = new ItemStack[STORE];
        for (int i = 0; i < inv.getSize(); i++) data[i] = inv.getItem(i);   // slots beyond the view are kept untouched
        store(p, data);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getHolder() instanceof Holder) Bukkit.getScheduler().runTask(plugin, () -> save(top));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getHolder() instanceof Holder) Bukkit.getScheduler().runTask(plugin, () -> save(top));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) { save(e.getInventory()); }
}
