package dev.ascendant.core.gui;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Fx;
import dev.ascendant.core.util.Items;
import dev.ascendant.core.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.function.Supplier;

/**
 * Table of N configurable entries (item frames), each with columns:
 * ITEM (pick an item), POTION (pick a potion), SECONDS (anvil number), COLOR (boss bar color),
 * TOGGLE_SECONDS (left click on/off, right click anvil number).
 * Values live at {basePath}.{index}.{key}.
 */
public final class SlotsMenu extends Menu {
    public enum Kind { ITEM, POTION, SECONDS, COLOR, TOGGLE_SECONDS }

    public record Col(Kind kind, String key, String label, Material icon) {}

    public static final List<String> COLORS = List.of("PINK", "BLUE", "RED", "GREEN", "YELLOW", "PURPLE", "WHITE");
    private static final Material[] COLOR_ICONS = {Material.PINK_DYE, Material.BLUE_DYE, Material.RED_DYE,
            Material.GREEN_DYE, Material.YELLOW_DYE, Material.PURPLE_DYE, Material.WHITE_DYE};

    private final String base;
    private final int count;
    private final List<Col> cols;
    private final Supplier<Menu> back;
    private final Runnable presets;
    private int page;

    public SlotsMenu(AscendantCore plugin, Player viewer, String title, String base, int count, List<Col> cols,
                     Supplier<Menu> back, Runnable presets) {
        super(plugin, viewer, 6, title);
        this.base = base;
        this.count = count;
        this.cols = cols;
        this.back = back;
        this.presets = presets;
    }

    private int perRow() { return cols.size() == 2 ? 3 : 2; }
    private int perPage() { return perRow() * 4; }

    @Override
    protected void build() {
        background();
        FileConfiguration cfg = plugin.getConfig();
        int pages = Math.max(1, (count + perPage() - 1) / perPage());
        if (page >= pages) page = pages - 1;
        int step = cols.size() == 2 ? 2 : 4;
        for (int n = 0; n < perPage(); n++) {
            int idx = page * perPage() + n;
            if (idx >= count) break;
            int row = n / perRow(), c = n % perRow();
            for (int k = 0; k < cols.size(); k++) {
                int slot = 9 * (1 + row) + 1 + c * step + k;
                Col col = cols.get(k);
                set(slot, icon(cfg, idx, col), t -> click(idx, col, t));
            }
        }
        if (page > 0) set(48, Items.of(Material.SPECTRAL_ARROW, "<white><bold>‹ Previous"), t -> { page--; Fx.page(viewer); });
        if (page < pages - 1) set(50, Items.of(Material.SPECTRAL_ARROW, "<white><bold>Next ›"), t -> { page++; Fx.page(viewer); });
        if (presets != null) set(47, Items.of(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, "<white><bold>Presets", "<gray>Fill with ready-made entries"),
                t -> { presets.run(); plugin.saveConfig(); plugin.changed(); Fx.success(viewer); });
        backButton(49, back);
    }

    private String p(int idx, String key) { return base + "." + idx + "." + key; }

    private ItemStack icon(FileConfiguration cfg, int idx, Col col) {
        String head = "<white><bold>#" + (idx + 1) + " " + col.label();
        switch (col.kind()) {
            case ITEM -> {
                Material m = Material.matchMaterial(cfg.getString(p(idx, col.key()), ""));
                if (m == null || !m.isItem()) return Items.of(Material.ITEM_FRAME, head, "<gray>Empty", "", "<white>Click <gray>» pick an item");
                return Items.of(m, head, "<gray>" + Text.pretty(m.name()), "", "<white>Click <gray>» change  <white>Right <gray>» clear");
            }
            case POTION -> {
                PotionType t = potion(cfg.getString(p(idx, col.key()), ""));
                if (t == null) return Items.of(Material.ITEM_FRAME, head, "<gray>Empty", "", "<white>Click <gray>» pick a potion");
                return PotionPickMenu.potionIcon(t, head, "<gray>" + Text.pretty(t.getKey().getKey()), "", "<white>Click <gray>» change  <white>Right <gray>» clear");
            }
            case SECONDS -> {
                return Items.of(col.icon(), head, "<gray>Value: <white>" + cfg.getInt(p(idx, col.key())) + "s", "", "<white>Click <gray>» edit in anvil");
            }
            case COLOR -> {
                String c = cfg.getString(p(idx, col.key()), "WHITE");
                int i = Math.max(0, COLORS.indexOf(c));
                return Items.of(COLOR_ICONS[i], head, "<gray>Color: <white>" + c, "", "<white>Click <gray>» next color");
            }
            default -> {
                boolean on = cfg.getBoolean(p(idx, col.key() + ".enabled"));
                return Items.glow(Items.of(col.icon(), head, "<gray>Status: " + Text.state(on),
                        "<gray>Time: <white>" + cfg.getInt(p(idx, col.key() + ".seconds")) + "s", "",
                        "<white>Left <gray>» on/off  <white>Right <gray>» set seconds"), on);
            }
        }
    }

    private static PotionType potion(String key) {
        NamespacedKey nk = NamespacedKey.fromString(key.contains(":") ? key : "minecraft:" + key);
        return key.isEmpty() || nk == null ? null : Registry.POTION.get(nk);
    }

    private void click(int idx, Col col, ClickType t) {
        FileConfiguration cfg = plugin.getConfig();
        switch (col.kind()) {
            case ITEM -> {
                if (right(t)) { cfg.set(p(idx, col.key()), null); save(); return; }
                ItemPicker.open(plugin, viewer, "<white><bold>Pick an item", m -> false,
                        m -> { cfg.set(p(idx, col.key()), m.name()); save(); }, true, () -> this);
            }
            case POTION -> {
                if (right(t)) { cfg.set(p(idx, col.key()), null); save(); return; }
                new PotionPickMenu(plugin, viewer, pt -> { cfg.set(p(idx, col.key()), pt.getKey().getKey()); save(); }, () -> this).open();
            }
            case SECONDS -> ConfigMenu.editNumber(plugin, viewer, col.label() + " (seconds)", p(idx, col.key()), 0, 86400, true, this::open);
            case COLOR -> {
                int i = Math.max(0, COLORS.indexOf(cfg.getString(p(idx, col.key()), "WHITE")));
                cfg.set(p(idx, col.key()), COLORS.get(right(t) ? (i + COLORS.size() - 1) % COLORS.size() : (i + 1) % COLORS.size()));
                save();
            }
            default -> {
                if (left(t)) {
                    boolean v = !cfg.getBoolean(p(idx, col.key() + ".enabled"));
                    cfg.set(p(idx, col.key() + ".enabled"), v);
                    save();
                    if (v) Fx.on(viewer); else Fx.off(viewer);
                } else {
                    ConfigMenu.editNumber(plugin, viewer, col.label() + " (seconds)", p(idx, col.key() + ".seconds"), 0, 86400, true, this::open);
                }
            }
        }
    }

    private void save() {
        plugin.saveConfig();
        plugin.changed();
    }
}
