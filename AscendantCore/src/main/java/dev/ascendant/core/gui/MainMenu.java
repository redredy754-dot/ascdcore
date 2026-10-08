package dev.ascendant.core.gui;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Fx;
import dev.ascendant.core.util.Items;
import dev.ascendant.core.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** The control panel: 21 buttons per page, black and white. */
public final class MainMenu extends Menu {
    private record Def(String name, Material icon, String[] desc, Supplier<List<String>> status, Supplier<Boolean> glow,
                       String[] hints, BiConsumer<Player, ClickType> click) {}

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private int page;

    public MainMenu(AscendantCore plugin, Player viewer) { this(plugin, viewer, 0); }

    public MainMenu(AscendantCore plugin, Player viewer, int page) {
        super(plugin, viewer, 6, "<white><bold>AscendantCore <dark_gray>» <gray>Control Panel");
        this.page = page;
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }

    private void saveAll() {
        plugin.saveConfig();
        plugin.changed();
    }

    private void flip(String path) {
        boolean v = !cfg().getBoolean(path);
        cfg().set(path, v);
        saveAll();
        if (v) Fx.on(viewer); else Fx.off(viewer);
    }

    private Menu self() { return new MainMenu(plugin, viewer, page); }

    private void number(String label, String path, double min, double max, boolean integer) {
        ConfigMenu.editNumber(plugin, viewer, label, path, min, max, integer, () -> self().open());
    }

    /** Standard button: left click toggles modules.<key>.enabled, right click runs the configure action. */
    private Def toggle(String key, String name, Material icon, String desc, Runnable configure) {
        String[] hints = configure != null
                ? new String[]{"<white>Left Click <gray>» toggle on/off", "<white>Right Click <gray>» configure"}
                : new String[]{"<white>Left Click <gray>» toggle on/off"};
        return new Def(name, icon, desc.split("\n"), () -> List.of("<gray>Status: " + Text.state(plugin.enabled(key))),
                () -> plugin.enabled(key), hints, (p, t) -> {
            if (left(t)) {
                boolean v = !plugin.enabled(key);
                plugin.setEnabled(key, v);
                if (v) Fx.on(p); else Fx.off(p);
            } else if (right(t)) {
                if (configure != null) configure.run(); else Fx.error(p);
            }
        });
    }

    private List<Def> defs() {
        Player p = viewer;
        Supplier<Menu> home = () -> new MainMenu(plugin, p, page);
        List<Def> d = new ArrayList<>();
        d.add(toggle("shield-stunning", "Shield Stunning", Material.SHIELD, "ON: axes disable shields (vanilla)\nOFF: shields can't be stunned", null));
        d.add(toggle("attribute-swapping", "Attribute Swapping", Material.DIAMOND_SWORD, "ON: allowed (vanilla)\nOFF: hits right after a hotbar swap fail", null));
        d.add(toggle("one-player-sleep", "One Player Sleep", Material.RED_BED, "One sleeping player skips the night", null));
        d.add(toggle("string-command", "/string", Material.STRING, "/string fills your inventory with string",
                () -> number("/string cooldown (seconds)", "modules.string-command.cooldown-seconds", 0, 86400, true)));
        d.add(toggle("click-villagers", "Click Villagers", Material.VILLAGER_SPAWN_EGG,
                "Shift + right-click: pick up, claim (shovel),\nanchor (shears), manage", () -> ConfigMenus.villagers(plugin, p).open()));
        d.add(toggle("cobweb-cleaner", "Cobweb Cleaner", Material.COBWEB, "Placed cobwebs vanish after a while",
                () -> number("Cobweb clean time (seconds)", "modules.cobweb-cleaner.seconds", 1, 3600, true)));
        d.add(toggle("ban-items", "Ban Items", Material.BARRIER, "Ban any item, with netherite presets", () -> new BanMenu(plugin, p).open()));
        d.add(new Def("Mace", Material.MACE, new String[]{"Mace enchant limiter, mace limiter", "and mace cooldown"},
                () -> List.of("<gray>Enchant limiter: " + Text.state(plugin.enabled("mace.enchant-limiter")),
                        "<gray>Mace limiter: " + Text.state(plugin.enabled("mace.limiter")),
                        "<gray>Cooldown: " + Text.state(plugin.enabled("mace.cooldown"))),
                () -> false, new String[]{"<white>Left Click <gray>» open"}, (pl, t) -> new MaceMenu(plugin, pl).open()));
        d.add(toggle("potion-limiter", "Potion Limiter", Material.POTION, "Ban individual potions", () -> {
            List<PotionType> types = PotionPickMenu.allSorted();
            new PotionMenu(plugin, p, types, home).open();
        }));
        d.add(toggle("enchant-limiter", "Enchant Limiter", Material.ENCHANTED_BOOK, "Cap the level of every enchantment", () -> {
            List<Enchantment> list = new ArrayList<>();
            Registry.ENCHANTMENT.forEach(list::add);
            list.sort(Comparator.comparing(e -> e.getKey().getKey()));
            new LimitMenu(plugin, p, "<white><bold>Enchant Limiter", list, "modules.enchant-limiter.limits", home).open();
        }));
        d.add(toggle("server-cleaner", "Server Cleaner", Material.HOPPER, "Clears dropped items on a timer\nMessages are editable in config.yml",
                () -> number("Cleaner cooldown (seconds)", "modules.server-cleaner.interval-seconds", 10, 86400, true)));
        d.add(toggle("lifesteal", "Lifesteal", Material.TOTEM_OF_UNDYING, "Gain hearts by killing, lose them by dying",
                () -> ConfigMenus.lifesteal(plugin, p).open()));
        d.add(toggle("infinite-restock", "Infinite Villager Restock", Material.EMERALD, "Villager trades never run out", null));
        d.add(toggle("clumps", "Clumps", Material.EXPERIENCE_BOTTLE, "Merges nearby XP orbs", () -> ConfigMenus.clumps(plugin, p).open()));
        d.add(toggle("item-stacker", "Item Stacker", Material.CHEST, "Merges dropped items into one entity\n(shown as \"Leather - 520x\")",
                () -> ConfigMenus.stacker(plugin, p).open()));
        d.add(toggle("server-restarter", "Server Restarter", Material.CLOCK, "Auto restart by RAM, TPS or time",
                () -> ConfigMenus.restarter(plugin, p).open()));
        d.add(toggle("explosion-control", "Explosion Control", Material.TNT, "Power and block damage per explosion type",
                () -> ConfigMenus.explosions(plugin, p).open()));
        d.add(toggle("optimizations", "Optimizations", Material.COMPARATOR, "Spawn limits, render distance,\nsimulation distance, tracking range",
                () -> ConfigMenus.optimizations(plugin, p).open()));
        d.add(toggle("insta-smelt", "Insta Smelt", Material.FURNACE, "Furnaces, smokers and blast furnaces\nsmelt much faster",
                () -> number("Smelt speed multiplier", "modules.insta-smelt.speed", 1, 1000, false)));
        d.add(toggle("insta-brew", "Insta Brew", Material.BREWING_STAND, "Brewing stands finish almost instantly",
                () -> number("Brew time (ticks, 20 = 1 second)", "modules.insta-brew.ticks", 1, 400, true)));
        d.add(toggle("anti-minimap", "Anti Minimap", Material.MAP, "Tells supported minimap mods to disable\nthemselves (codes in config.yml)", null));
        d.add(new Def("Health Indicator", Material.GOLDEN_APPLE, new String[]{"Shows health below player names"},
                () -> List.of("<gray>Status: " + Text.state(plugin.enabled("health-indicator")),
                        "<gray>Shows: <white>" + cfg().getString("modules.health-indicator.mode", "HP")),
                () -> plugin.enabled("health-indicator"),
                new String[]{"<white>Left Click <gray>» toggle on/off", "<white>Right Click <gray>» HP / HEARTS"}, (pl, t) -> {
            if (left(t)) flip("modules.health-indicator.enabled");
            else {
                boolean hearts = "HEARTS".equalsIgnoreCase(cfg().getString("modules.health-indicator.mode", "HP"));
                cfg().set("modules.health-indicator.mode", hearts ? "HP" : "HEARTS");
                saveAll();
                Fx.on(pl);
            }
        }));
        d.add(toggle("no-naked-kill", "No Naked Killing", Material.LEATHER_CHESTPLATE, "Protects players with no armor\nwho are really naked and poor",
                () -> ConfigMenus.naked(plugin, p).open()));
        d.add(toggle("rituals", "Rituals", Material.BELL, "Crafting chosen items takes time and\nbroadcasts a boss bar with coords",
                () -> new SlotsMenu(plugin, p, "<white><bold>Rituals", "modules.rituals.entries", 20, List.of(
                        new SlotsMenu.Col(SlotsMenu.Kind.ITEM, "item", "Item", Material.ITEM_FRAME),
                        new SlotsMenu.Col(SlotsMenu.Kind.SECONDS, "seconds", "Craft time", Material.CLOCK),
                        new SlotsMenu.Col(SlotsMenu.Kind.COLOR, "color", "Boss bar color", Material.WHITE_DYE)), home, null).open()));
        d.add(toggle("anti-crystal", "Anti Crystal PvP", Material.END_CRYSTAL, "Disable or weaken end crystals\nand respawn anchors",
                () -> ConfigMenus.antiCrystal(plugin, p).open()));
        d.add(toggle("custom-crafting", "Custom Crafting", Material.CRAFTING_TABLE, "Cheap presets and your own recipes",
                () -> new CraftingMenus.Hub(plugin, p).open()));
        d.add(toggle("item-cooldown", "Item Cooldown", Material.CLOCK, "Cooldowns for left / right click use",
                () -> new SlotsMenu(plugin, p, "<white><bold>Item Cooldown", "modules.item-cooldown.entries", 20, List.of(
                        new SlotsMenu.Col(SlotsMenu.Kind.ITEM, "item", "Item", Material.ITEM_FRAME),
                        new SlotsMenu.Col(SlotsMenu.Kind.TOGGLE_SECONDS, "left", "Left click use", Material.CLOCK),
                        new SlotsMenu.Col(SlotsMenu.Kind.TOGGLE_SECONDS, "right", "Right click use", Material.REPEATER)), home, null).open()));
        d.add(toggle("ping", "Ping", Material.NOTE_BLOCK, "Shows ping next to player name tags", null));
        d.add(new Def("Death Ban", Material.WITHER_SKELETON_SKULL, new String[]{"Ban players when they die"},
                () -> List.of("<gray>Status: " + Text.state(plugin.enabled("death-ban")),
                        "<gray>Spectator instead of ban: " + Text.state(cfg().getBoolean("modules.death-ban.spectator"))),
                () -> plugin.enabled("death-ban"),
                new String[]{"<white>Left Click <gray>» toggle ban on death", "<white>Right Click <gray>» toggle spectator"}, (pl, t) -> {
            if (left(t)) flip("modules.death-ban.enabled"); else flip("modules.death-ban.spectator");
        }));
        d.add(new Def("Dimension Ban", Material.OBSIDIAN, new String[]{"Block portals to a dimension"},
                () -> List.of("<gray>Nether banned: " + Text.state(cfg().getBoolean("modules.dimension-ban.nether")),
                        "<gray>End banned: " + Text.state(cfg().getBoolean("modules.dimension-ban.end"))),
                () -> cfg().getBoolean("modules.dimension-ban.nether") || cfg().getBoolean("modules.dimension-ban.end"),
                new String[]{"<white>Left Click <gray>» toggle Nether", "<white>Right Click <gray>» toggle End"}, (pl, t) -> {
            if (left(t)) flip("modules.dimension-ban.nether"); else flip("modules.dimension-ban.end");
        }));
        d.add(toggle("afk-protection", "AFK Protection", Material.CAMPFIRE, "AFK players can't be hurt",
                () -> number("AFK time (seconds)", "modules.afk-protection.seconds", 5, 86400, true)));
        d.add(toggle("combat", "Combat", Material.IRON_SWORD, "Combat tag, combat log, safezone wall\n(WorldGuard pvp=deny regions)",
                () -> ConfigMenus.combat(plugin, p).open()));
        d.add(toggle("trident-cooldown", "Trident Cooldown", Material.TRIDENT, "Cooldown between trident uses",
                () -> number("Trident cooldown (seconds)", "modules.trident-cooldown.seconds", 1, 3600, true)));
        d.add(toggle("pearl-cooldown", "Pearl Cooldown", Material.ENDER_PEARL, "Cooldown between ender pearls",
                () -> number("Pearl cooldown (seconds)", "modules.pearl-cooldown.seconds", 1, 3600, true)));
        d.add(toggle("pot-timer", "Pot Timer", Material.BREWING_STAND, "Change how long potions last",
                () -> new SlotsMenu(plugin, p, "<white><bold>Pot Timer", "modules.pot-timer.entries", 10, List.of(
                        new SlotsMenu.Col(SlotsMenu.Kind.POTION, "type", "Potion", Material.ITEM_FRAME),
                        new SlotsMenu.Col(SlotsMenu.Kind.SECONDS, "seconds", "Duration", Material.CLOCK)), home, () -> {
                    String[][] presets = {{"strong_strength", "480"}, {"strong_swiftness", "480"}, {"weaving", "480"}};
                    for (int i = 0; i < presets.length; i++) {
                        cfg().set("modules.pot-timer.entries." + i + ".type", presets[i][0]);
                        cfg().set("modules.pot-timer.entries." + i + ".seconds", Integer.parseInt(presets[i][1]));
                    }
                }).open()));
        d.add(toggle("enderchest-edit", "Enderchest Edit", Material.ENDER_CHEST, "Change the size of the ender chest",
                () -> ConfigMenus.enderchest(plugin, p).open()));
        return d;
    }

    @Override
    protected void build() {
        background();
        List<Def> defs = defs();
        int pages = (defs.size() + SLOTS.length - 1) / SLOTS.length;
        if (page >= pages) page = pages - 1;
        for (int i = 0; i < SLOTS.length; i++) {
            int idx = page * SLOTS.length + i;
            if (idx >= defs.size()) break;
            Def def = defs.get(idx);
            List<String> lore = new ArrayList<>();
            for (String line : def.desc()) lore.add("<gray>" + line);
            lore.add("");
            lore.addAll(def.status().get());
            lore.add("");
            lore.addAll(List.of(def.hints()));
            set(SLOTS[i], Items.glow(Items.of(def.icon(), "<white><bold>" + def.name(), lore.toArray(new String[0])), def.glow().get()),
                    t -> def.click().accept(viewer, t));
        }
        if (page > 0) set(48, Items.of(Material.SPECTRAL_ARROW, "<white><bold>‹ Previous page"), t -> { page--; Fx.page(viewer); });
        if (page < pages - 1) set(50, Items.of(Material.SPECTRAL_ARROW, "<white><bold>Next page ›"), t -> { page++; Fx.page(viewer); });
        set(49, Items.of(Material.NETHER_STAR, "<white><bold>AscendantCore",
                "<gray>Page " + (page + 1) + "/" + pages, "", "<dark_gray>Left click toggles, right click configures"));
    }
}
