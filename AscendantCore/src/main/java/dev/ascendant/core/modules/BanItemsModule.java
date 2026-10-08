package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class BanItemsModule implements Listener {
    public static final List<Material> NETHERITE_ARMOR = List.of(Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS);
    public static final List<Material> NETHERITE_TOOLS = List.of(Material.NETHERITE_SWORD, Material.NETHERITE_PICKAXE,
            Material.NETHERITE_AXE, Material.NETHERITE_SHOVEL, Material.NETHERITE_HOE);

    private final AscendantCore plugin;
    private final Set<Material> banned = EnumSet.noneOf(Material.class);

    public BanItemsModule(AscendantCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        banned.clear();
        for (String s : plugin.getConfig().getStringList("modules.ban-items.banned")) {
            Material m = Material.matchMaterial(s);
            if (m != null) banned.add(m);
        }
    }

    public int count() { return banned.size(); }
    public boolean isBanned(Material m) { return banned.contains(m); }
    public boolean allBanned(Collection<Material> mats) { return banned.containsAll(mats); }

    public void set(Material m, boolean ban) {
        if (ban) banned.add(m); else banned.remove(m);
        save();
    }

    public void setAll(Collection<Material> mats, boolean ban) {
        if (ban) banned.addAll(mats); else banned.removeAll(mats);
        save();
    }

    private void save() {
        List<String> names = new ArrayList<>();
        banned.forEach(m -> names.add(m.name()));
        Collections.sort(names);
        plugin.getConfig().set("modules.ban-items.banned", names);
        plugin.saveConfig();
    }

    private boolean blocks(ItemStack it) {
        return it != null && plugin.enabled("ban-items") && banned.contains(it.getType());
    }

    private void warn(Player p) {
        p.sendActionBar(Text.mm("<red>That item is banned on this server."));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() == org.bukkit.event.block.Action.PHYSICAL) return;
        if (blocks(e.getItem())) { e.setCancelled(true); warn(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (blocks(e.getPlayer().getInventory().getItem(e.getHand()))) { e.setCancelled(true); warn(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (blocks(e.getItemInHand())) { e.setCancelled(true); warn(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player && blocks(e.getItem().getItemStack())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && blocks(p.getInventory().getItemInMainHand())) {
            e.setCancelled(true);
            warn(p);
        }
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        if (!plugin.enabled("ban-items")) return;
        ItemStack r = e.getInventory().getResult();
        if (r != null && banned.contains(r.getType())) e.getInventory().setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!plugin.enabled("ban-items") || !(e.getWhoClicked() instanceof Player p)) return;
        if (e.getSlotType() == SlotType.ARMOR && blocks(e.getCursor())) { e.setCancelled(true); warn(p); return; }
        if (e.isShiftClick() && blocks(e.getCurrentItem())) { e.setCancelled(true); warn(p); return; }
        if (e.getClick() == ClickType.NUMBER_KEY && e.getSlotType() == SlotType.ARMOR
                && blocks(p.getInventory().getItem(e.getHotbarButton()))) { e.setCancelled(true); warn(p); }
    }
}
