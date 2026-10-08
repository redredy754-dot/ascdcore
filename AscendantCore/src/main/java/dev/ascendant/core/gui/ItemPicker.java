package dev.ascendant.core.gui;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Categories;
import dev.ascendant.core.util.Categories.Category;
import dev.ascendant.core.util.Fx;
import dev.ascendant.core.util.Items;
import dev.ascendant.core.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Item chooser sorted into categories (blocks / tools / armor / potions / food / misc).
 * single=true: picking closes the picker and returns to 'back'. single=false: click toggles, list stays open.
 */
public final class ItemPicker {
    private ItemPicker() {}

    public static void open(AscendantCore plugin, Player p, String title, Predicate<Material> marked,
                            Consumer<Material> onPick, boolean single, Supplier<Menu> back) {
        new Hub(plugin, p, title, marked, onPick, single, back).open();
    }

    /** A single category grid (used directly by the ban items menu). */
    public static Menu grid(AscendantCore plugin, Player p, Category c, Predicate<Material> marked,
                            Consumer<Material> onPick, boolean single, Supplier<Menu> back) {
        return new Grid(plugin, p, c, marked, onPick, single, back, back);
    }

    private static final class Hub extends Menu {
        private final Predicate<Material> marked;
        private final Consumer<Material> onPick;
        private final boolean single;
        private final Supplier<Menu> back;

        Hub(AscendantCore plugin, Player p, String title, Predicate<Material> marked, Consumer<Material> onPick,
            boolean single, Supplier<Menu> back) {
            super(plugin, p, 3, title);
            this.marked = marked;
            this.onPick = onPick;
            this.single = single;
            this.back = back;
        }

        @Override
        protected void build() {
            background();
            Material[] icons = {Material.GRASS_BLOCK, Material.IRON_PICKAXE, Material.IRON_CHESTPLATE,
                    Material.POTION, Material.COOKED_BEEF, Material.NAME_TAG};
            Category[] cats = Category.values();
            for (int i = 0; i < cats.length; i++) {
                Category c = cats[i];
                set(10 + i, Items.of(icons[i], "<white><bold>" + c.label, "<gray>" + Categories.items(c).size() + " items",
                        "", "<white>Click » open"),
                        t -> new Grid(plugin, viewer, c, marked, onPick, single, back, () -> this).open());
            }
            backButton(22, back);
        }
    }

    private static final class Grid extends PagedMenu<Material> {
        private final Predicate<Material> marked;
        private final Consumer<Material> onPick;
        private final boolean single;
        private final Supplier<Menu> afterPick;

        Grid(AscendantCore plugin, Player p, Category c, Predicate<Material> marked, Consumer<Material> onPick,
             boolean single, Supplier<Menu> afterPick, Supplier<Menu> navBack) {
            super(plugin, p, c.label, Categories.items(c), navBack);
            this.marked = marked;
            this.onPick = onPick;
            this.single = single;
            this.afterPick = afterPick;
        }

        @Override
        protected ItemStack icon(Material m) {
            boolean on = marked.test(m);
            return Items.glow(Items.of(m, (on ? "<white><bold>" : "<white>") + Text.pretty(m.name()),
                    "<gray>" + (on ? "Selected" : "Not selected"), "", "<white>Click » " + (single ? "select" : "toggle")), on);
        }

        @Override
        protected void onEntryClick(Material m, ClickType t) {
            onPick.accept(m);
            if (single) {
                Fx.success(viewer);
                afterPick.get().open();
            }
        }

        @Override
        protected String footer() { return single ? "<gray>Click an item to select it" : "<gray>Click an item to toggle it"; }
    }
}
