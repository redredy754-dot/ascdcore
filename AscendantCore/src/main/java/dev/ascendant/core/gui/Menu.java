package dev.ascendant.core.gui;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Fx;
import dev.ascendant.core.util.Items;
import dev.ascendant.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Base class for every GUI. Clicks are cancelled by {@link MenuListener}; only "editor" menus allow items in chosen slots. */
public abstract class Menu implements InventoryHolder {
    protected final AscendantCore plugin;
    protected final Player viewer;
    private final Inventory inventory;
    private final int rows;
    private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();

    protected Menu(AscendantCore plugin, Player viewer, int rows, String title) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.rows = rows;
        this.inventory = Bukkit.createInventory(this, rows * 9, Text.mm(title));
    }

    protected abstract void build();

    /** Editor menus let the player put real items into {@link #editable(int)} slots. */
    public boolean isEditor() { return false; }
    public boolean editable(int slot) { return false; }
    /** Called when the menu is closed (editors return the items here). */
    public void closed(Player p) {}

    public final void render() {
        inventory.clear();
        actions.clear();
        build();
    }

    /** Renders and opens on the next tick (safe to call from inside click handlers). */
    public final void open() {
        render();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline()) {
                viewer.openInventory(inventory);
                Fx.open(viewer);
            }
        });
    }

    final void click(int slot, ClickType type) {
        if (type != ClickType.LEFT && type != ClickType.RIGHT
                && type != ClickType.SHIFT_LEFT && type != ClickType.SHIFT_RIGHT) return;
        Consumer<ClickType> a = actions.get(slot);
        if (a != null) {
            Fx.click(viewer);
            a.accept(type);
        }
    }

    protected final void set(int slot, ItemStack item, Consumer<ClickType> action) {
        inventory.setItem(slot, item);
        if (action != null) actions.put(slot, action);
    }

    protected final void set(int slot, ItemStack item) { set(slot, item, null); }

    protected final int rows() { return rows; }

    protected final void fillAll(Material pane) {
        for (int i = 0; i < rows * 9; i++) inventory.setItem(i, Items.pane(pane));
    }

    protected final void border(Material a, Material b) {
        int size = rows * 9;
        for (int i = 0; i < size; i++) {
            int r = i / 9, c = i % 9;
            if (r == 0 || r == rows - 1 || c == 0 || c == 8) {
                inventory.setItem(i, Items.pane(((r + c) % 2 == 0) ? a : b));
            }
        }
    }

    /** Black and white look. */
    protected final void background() {
        fillAll(Material.BLACK_STAINED_GLASS_PANE);
        border(Material.WHITE_STAINED_GLASS_PANE, Material.GRAY_STAINED_GLASS_PANE);
    }

    protected final void backButton(int slot, Supplier<Menu> back) {
        if (back == null) return;
        set(slot, Items.of(Material.ARROW, "<white><bold>« Back", "<gray>Return to the previous menu"),
                t -> back.get().open());
    }

    protected static boolean left(ClickType t) { return t == ClickType.LEFT || t == ClickType.SHIFT_LEFT; }
    protected static boolean right(ClickType t) { return t == ClickType.RIGHT || t == ClickType.SHIFT_RIGHT; }

    @Override
    public Inventory getInventory() { return inventory; }
}
