package dev.ascendant.core.gui;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.modules.BanItemsModule;
import dev.ascendant.core.util.Fx;
import dev.ascendant.core.util.Categories;
import dev.ascendant.core.util.Categories.Category;
import dev.ascendant.core.util.Items;
import dev.ascendant.core.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Ban items hub: two presets on top, the item categories in the middle. */
public final class BanMenu extends Menu {

    public BanMenu(AscendantCore plugin, Player viewer) {
        super(plugin, viewer, 5, "<white><bold>Ban Items");
    }

    @Override
    protected void build() {
        background();
        BanItemsModule bans = plugin.banItems();
        set(11, preset("Ban Netherite Armor", Material.NETHERITE_CHESTPLATE, BanItemsModule.NETHERITE_ARMOR),
                t -> toggle(BanItemsModule.NETHERITE_ARMOR));
        set(15, preset("Ban Netherite Tools", Material.NETHERITE_PICKAXE, BanItemsModule.NETHERITE_TOOLS),
                t -> toggle(BanItemsModule.NETHERITE_TOOLS));
        set(13, Items.of(Material.BARRIER, "<white><bold>Ban Items", "<gray>Status: " + Text.state(plugin.enabled("ban-items")),
                "<gray>Banned items: <white>" + bans.count(), "", "<white>Click <gray>» toggle"), t -> {
            boolean v = !plugin.enabled("ban-items");
            plugin.setEnabled("ban-items", v);
            if (v) Fx.on(viewer); else Fx.off(viewer);
        });
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            Category c = cats[i];
            Material[] icons = {Material.GRASS_BLOCK, Material.NETHERITE_PICKAXE, Material.NETHERITE_CHESTPLATE,
                    Material.POTION, Material.COOKED_BEEF, Material.NAME_TAG};
            set(19 + i, Items.of(icons[i], "<white><bold>" + c.label,
                    "<gray>" + Categories.items(c).size() + " items", "", "<white>Click <gray>» open"),
                    t -> ItemPicker.grid(plugin, viewer, c, plugin.banItems()::isBanned, m -> {
                        boolean ban = !plugin.banItems().isBanned(m);
                        plugin.banItems().set(m, ban);
                        if (ban) Fx.off(viewer); else Fx.on(viewer);
                    }, false, () -> new BanMenu(plugin, viewer)).open());
        }
        backButton(40, () -> new MainMenu(plugin, viewer));
    }

    private ItemStack preset(String name, Material icon, List<Material> mats) {
        boolean all = plugin.banItems().allBanned(mats);
        return Items.glow(Items.of(icon, "<white><bold>" + name,
                "<gray>Preset: " + (all ? "<white>banned" : "<dark_gray>not banned"), "", "<white>Click <gray>» " + (all ? "unban all" : "ban all")), all);
    }

    private void toggle(List<Material> mats) {
        boolean ban = !plugin.banItems().allBanned(mats);
        plugin.banItems().setAll(mats, ban);
        if (ban) Fx.off(viewer); else Fx.on(viewer);
    }
}
