package dev.ascendant.core.gui;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Fx;
import dev.ascendant.core.util.Items;
import dev.ascendant.core.util.Text;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.List;
import java.util.function.Supplier;

/** Per-enchantment max level editor. Left click = decrease, right click = increase (shift = 5). 0 bans it. */
public final class LimitMenu extends PagedMenu<Enchantment> {
    private final String path;

    public LimitMenu(AscendantCore plugin, Player viewer, String title, List<Enchantment> enchants,
                     String path, Supplier<Menu> back) {
        super(plugin, viewer, title, enchants, back);
        this.path = path;
    }

    private int limit(Enchantment e) {
        return plugin.getConfig().getInt(path + "." + e.getKey().getKey(), e.getMaxLevel());
    }

    @Override
    protected ItemStack icon(Enchantment e) {
        int lim = limit(e);
        ItemStack book = Items.of(Material.ENCHANTED_BOOK, "<white><bold>" + Text.pretty(e.getKey().getKey()),
                "<gray>Max level: " + (lim <= 0 ? "<white>banned" : "<white>" + lim),
                "<gray>Vanilla max: <white>" + e.getMaxLevel(), "",
                "<white>Left <gray>» decrease  <white>Right <gray>» increase",
                "<gray>Hold shift for steps of 5");
        // a REAL enchanted book that carries the enchantment, so resource packs can recognise it
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(e, Math.max(1, Math.min(lim, 255)), true);
        book.setItemMeta(meta);
        return book;
    }

    @Override
    protected void onEntryClick(Enchantment e, ClickType t) {
        int step = t.isShiftClick() ? 5 : 1;
        int lim = limit(e);
        int next = left(t) ? Math.max(0, lim - step) : Math.min(255, lim + step);
        plugin.getConfig().set(path + "." + e.getKey().getKey(), next);
        plugin.saveConfig();
        if (left(t)) Fx.off(viewer); else Fx.on(viewer);
    }

    @Override
    protected String footer() { return "<gray>Left: lower  Right: raise"; }
}
