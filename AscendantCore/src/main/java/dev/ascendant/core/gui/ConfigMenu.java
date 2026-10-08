package dev.ascendant.core.gui;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Fx;
import dev.ascendant.core.util.Items;
import dev.ascendant.core.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Generic settings menu: booleans toggle, enums cycle, numbers open the anvil prompt. */
public final class ConfigMenu extends Menu {
    public enum Type { BOOL, INT, DOUBLE, ENUM, ACTION }

    public record Entry(Type type, String path, String name, Material icon, String desc,
                        double min, double max, List<String> options, Consumer<Player> action) {
        public static Entry bool(String path, String name, Material icon, String desc) {
            return new Entry(Type.BOOL, path, name, icon, desc, 0, 0, List.of(), null);
        }
        public static Entry integer(String path, String name, Material icon, String desc, int min, int max) {
            return new Entry(Type.INT, path, name, icon, desc, min, max, List.of(), null);
        }
        public static Entry decimal(String path, String name, Material icon, String desc, double min, double max) {
            return new Entry(Type.DOUBLE, path, name, icon, desc, min, max, List.of(), null);
        }
        public static Entry choice(String path, String name, Material icon, String desc, String... options) {
            return new Entry(Type.ENUM, path, name, icon, desc, 0, 0, List.of(options), null);
        }
        public static Entry action(String name, Material icon, String desc, Consumer<Player> action) {
            return new Entry(Type.ACTION, null, name, icon, desc, 0, 0, List.of(), action);
        }
    }

    private final List<Entry> entries;
    private final Supplier<Menu> back;

    public ConfigMenu(AscendantCore plugin, Player viewer, String title, List<Entry> entries, Supplier<Menu> back) {
        super(plugin, viewer, (entries.size() + 6) / 7 + 2, title);
        this.entries = entries;
        this.back = back;
    }

    @Override
    protected void build() {
        background();
        FileConfiguration cfg = plugin.getConfig();
        for (int i = 0; i < entries.size(); i++) {
            Entry en = entries.get(i);
            int slot = 9 * (1 + i / 7) + 1 + (i % 7);
            String value;
            String hint;
            switch (en.type()) {
                case BOOL -> { value = "<gray>Status: " + Text.state(cfg.getBoolean(en.path())); hint = "<white>Click <gray>» toggle"; }
                case INT -> { value = "<gray>Value: <white>" + cfg.getInt(en.path()); hint = "<white>Click <gray>» edit in anvil"; }
                case DOUBLE -> { value = "<gray>Value: <white>" + fmt(cfg.getDouble(en.path())); hint = "<white>Click <gray>» edit in anvil"; }
                case ENUM -> { value = "<gray>Value: <white>" + cfg.getString(en.path()); hint = "<white>Left/Right <gray>» cycle"; }
                default -> { value = ""; hint = "<white>Click <gray>» run"; }
            }
            set(slot, Items.of(en.icon(), "<white><bold>" + en.name(), "<gray>" + en.desc(), "", value, hint),
                    t -> handle(en, t));
        }
        backButton((rows() - 1) * 9 + 4, back);
    }

    private void handle(Entry en, ClickType t) {
        FileConfiguration cfg = plugin.getConfig();
        switch (en.type()) {
            case BOOL -> {
                boolean v = !cfg.getBoolean(en.path());
                cfg.set(en.path(), v);
                plugin.saveConfig();
                plugin.changed();
                if (v) Fx.on(viewer); else Fx.off(viewer);
            }
            case ENUM -> {
                int idx = en.options().indexOf(cfg.getString(en.path()));
                int n = en.options().size();
                idx = right(t) ? (idx - 1 + n) % n : (idx + 1) % n;
                cfg.set(en.path(), en.options().get(idx));
                plugin.saveConfig();
                plugin.changed();
                Fx.on(viewer);
            }
            case INT -> editNumber(plugin, viewer, en.name(), en.path(), en.min(), en.max(), true, this::open);
            case DOUBLE -> editNumber(plugin, viewer, en.name(), en.path(), en.min(), en.max(), false, this::open);
            case ACTION -> en.action().accept(viewer);
        }
    }

    /** Opens the anvil prompt for one numeric config value; validates and clamps the typed number. */
    public static void editNumber(AscendantCore plugin, Player p, String label, String path,
                                  double min, double max, boolean integer, Runnable back) {
        FileConfiguration cfg = plugin.getConfig();
        String current = integer ? String.valueOf(cfg.getInt(path)) : fmt(cfg.getDouble(path));
        plugin.prompts().ask(p, "Edit " + label, current, text -> {
            try {
                double v = Double.parseDouble(text.replace(',', '.'));
                if (!Double.isFinite(v)) throw new NumberFormatException();
                v = Math.max(min, Math.min(max, v));
                if (integer) cfg.set(path, (int) Math.round(v)); else cfg.set(path, v);
                plugin.saveConfig();
                plugin.changed();
                Fx.success(p);
            } catch (NumberFormatException ex) {
                plugin.msg(p, "<#8a8a8a>That is not a valid number.");
                Fx.error(p);
            }
            back.run();
        }, back);
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(Math.round(d * 100.0) / 100.0);
    }
}
