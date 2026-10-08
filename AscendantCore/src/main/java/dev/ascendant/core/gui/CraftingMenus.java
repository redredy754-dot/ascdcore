package dev.ascendant.core.gui;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.modules.CraftingModule;
import dev.ascendant.core.util.Fx;
import dev.ascendant.core.util.Items;
import dev.ascendant.core.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Custom crafting screens: hub, presets, custom recipe list and the glass-pane recipe editor. */
public final class CraftingMenus {
    private CraftingMenus() {}

    private static final String BASE = "modules.custom-crafting.";

    /** Hub: netherite upgrade template = presets, crafting table = custom recipes. */
    public static final class Hub extends Menu {
        public Hub(AscendantCore plugin, Player viewer) { super(plugin, viewer, 3, "<white><bold>Custom Crafting"); }

        @Override
        protected void build() {
            fillAll(Material.GRAY_STAINED_GLASS_PANE);
            set(11, Items.of(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, "<white><bold>Presets",
                    "<gray>Ready-made cheap recipes", "", "<white>Click <gray>» open"), t -> new Presets(plugin, viewer).open());
            set(15, Items.of(Material.CRAFTING_TABLE, "<white><bold>Custom Recipes",
                    "<gray>Create your own recipes", "", "<white>Click <gray>» open"), t -> new Recipes(plugin, viewer).open());
            backButton(22, () -> new MainMenu(plugin, viewer));
        }
    }

    public static final class Presets extends Menu {
        private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20};

        public Presets(AscendantCore plugin, Player viewer) { super(plugin, viewer, 4, "<white><bold>Crafting Presets"); }

        @Override
        protected void build() {
            fillAll(Material.GRAY_STAINED_GLASS_PANE);
            for (int i = 0; i < CraftingModule.PRESETS.size(); i++) {
                CraftingModule.Preset p = CraftingModule.PRESETS.get(i);
                String path = BASE + "presets." + p.id();
                boolean on = plugin.getConfig().getBoolean(path);
                set(SLOTS[i], Items.glow(Items.of(p.result(), "<white><bold>" + p.name(), "<gray>" + p.text(),
                        "<gray>Status: " + Text.state(on), "", "<white>Click <gray>» toggle"), on), t -> {
                    plugin.getConfig().set(path, !on);
                    plugin.saveConfig();
                    plugin.changed();
                    if (!on) Fx.on(viewer); else Fx.off(viewer);
                });
            }
            backButton(31, () -> new Hub(plugin, viewer));
        }
    }

    public static final class Recipes extends Menu {
        private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

        public Recipes(AscendantCore plugin, Player viewer) { super(plugin, viewer, 5, "<white><bold>Custom Recipes"); }

        @Override
        protected void build() {
            fillAll(Material.GRAY_STAINED_GLASS_PANE);
            ConfigurationSection s = plugin.getConfig().getConfigurationSection(BASE + "custom");
            int i = 0;
            if (s != null) for (String id : s.getKeys(false)) {
                if (i >= SLOTS.length) break;
                Material out = Material.matchMaterial(s.getString(id + ".output", ""));
                if (out == null) continue;
                int amount = Math.max(1, s.getInt(id + ".amount", 1));
                int ing = 0;
                for (String g : s.getStringList(id + ".grid")) { Material m = Material.matchMaterial(g); if (m != null && !m.isAir()) ing++; }
                ItemStack icon = Items.of(out, "<white><bold>" + Text.pretty(out.name()) + " x" + amount,
                        "<gray>Ingredients: <white>" + ing, "", "<white>Right <gray>» delete");
                set(SLOTS[i++], icon, t -> {
                    if (right(t)) {
                        plugin.getConfig().set(BASE + "custom." + id, null);
                        plugin.saveConfig();
                        plugin.changed();
                        Fx.off(viewer);
                    }
                });
            }
            set(40, Items.of(Material.CRAFTING_TABLE, "<white><bold>New Recipe", "<gray>Opens the recipe editor", "", "<white>Click <gray>» create"),
                    t -> new Editor(plugin, viewer).open());
            backButton(38, () -> new Hub(plugin, viewer));
        }
    }

    /** Glass panes everywhere except the 3x3 grid and the output slot, which take real items (returned on close). */
    public static final class Editor extends Menu {
        private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
        private static final int OUT = 23;
        private boolean returned;

        public Editor(AscendantCore plugin, Player viewer) { super(plugin, viewer, 5, "<white><bold>Recipe Editor"); }

        @Override
        public boolean isEditor() { return true; }

        @Override
        public boolean editable(int slot) {
            if (slot == OUT) return true;
            for (int g : GRID) if (g == slot) return true;
            return false;
        }

        @Override
        protected void build() {
            fillAll(Material.GRAY_STAINED_GLASS_PANE);
            for (int g : GRID) set(g, null);
            set(OUT, null);
            set(22, Items.of(Material.ARROW, "<white><bold>→", "<gray>Recipe becomes the output"));
            set(14, Items.of(Material.CRAFTING_TABLE, "<white><bold>Output", "<gray>Put the crafted item below"));
            set(40, Items.of(Material.PAPER, "<white><bold>How it works",
                    "<gray>Place the ingredients in the 3x3 grid", "<gray>and the result in the output slot,", "<gray>then press Save."));
            set(38, Items.of(Material.BARRIER, "<white><bold>Cancel", "<gray>Items are returned to you"), t -> leave());
            set(42, Items.of(Material.LIME_DYE, "<white><bold>Save", "<gray>Create the recipe"), t -> save());
        }

        private void leave() {
            viewer.closeInventory();
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> new Recipes(plugin, viewer).open());
        }

        private void save() {
            ItemStack out = getInventory().getItem(OUT);
            List<String> grid = new ArrayList<>();
            int count = 0;
            for (int g : GRID) {
                ItemStack it = getInventory().getItem(g);
                if (it == null || it.getType().isAir()) grid.add("AIR");
                else { grid.add(it.getType().name()); count++; }
            }
            if (out == null || out.getType().isAir() || count == 0) {
                plugin.msg(viewer, "<red>Put at least one ingredient and the output item in.");
                Fx.error(viewer);
                return;
            }
            ConfigurationSection s = plugin.getConfig().getConfigurationSection(BASE + "custom");
            int n = 1;
            while (s != null && s.contains("c" + n)) n++;
            String id = "c" + n;
            plugin.getConfig().set(BASE + "custom." + id + ".output", out.getType().name());
            plugin.getConfig().set(BASE + "custom." + id + ".amount", out.getAmount());
            plugin.getConfig().set(BASE + "custom." + id + ".grid", grid);
            plugin.saveConfig();
            plugin.changed();
            Fx.success(viewer);
            leave();
        }

        @Override
        public void closed(Player p) {
            if (returned) return;
            returned = true;
            for (int slot : editableSlots()) {
                ItemStack it = getInventory().getItem(slot);
                if (it != null && !it.getType().isAir()) Items.give(p, it);
                getInventory().setItem(slot, null);
            }
        }

        private int[] editableSlots() {
            int[] all = new int[GRID.length + 1];
            System.arraycopy(GRID, 0, all, 0, GRID.length);
            all[GRID.length] = OUT;
            return all;
        }
    }
}
