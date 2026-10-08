package dev.ascendant.core.gui;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.gui.ConfigMenu.Entry;
import dev.ascendant.core.util.Fx;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Entry lists for every module that has a settings screen. */
public final class ConfigMenus {
    private ConfigMenus() {}

    private static ConfigMenu make(AscendantCore plugin, Player p, String title, List<Entry> entries) {
        return new ConfigMenu(plugin, p, title, entries, () -> new MainMenu(plugin, p));
    }

    public static ConfigMenu lifesteal(AscendantCore plugin, Player p) {
        String m = "modules.lifesteal.";
        return make(plugin, p, "<white><bold>Lifesteal", List.of(
                Entry.integer(m + "starting-hearts", "Starting Hearts", Material.APPLE, "Hearts new players begin with", 1, 100),
                Entry.integer(m + "max-hearts", "Max Hearts", Material.GOLDEN_APPLE, "Upper heart limit", 1, 100),
                Entry.integer(m + "hearts-per-kill", "Hearts Per Kill", Material.IRON_SWORD, "Hearts a killer gains", 1, 20),
                Entry.integer(m + "hearts-lost-on-death", "Hearts Lost On Death", Material.SKELETON_SKULL, "Hearts a victim loses", 1, 20),
                Entry.bool(m + "lose-heart-on-natural-death", "Lose Heart On Nature Death", Material.COBWEB, "Lose hearts to mobs, fall damage, lava..."),
                Entry.choice(m + "elimination", "Elimination", Material.BARRIER, "What happens at 0 hearts", "SPECTATOR", "BAN"),
                Entry.bool(m + "heart-item", "Heart Item (/withdrawheart)", Material.NETHER_STAR, "Allow turning hearts into items"),
                Entry.bool(m + "drop-heart-at-max", "Heart At Max", Material.CHEST, "Killer at max hearts gets a heart item"),
                Entry.bool(m + "heart-drops-instead", "Heart Drops Instead", Material.HOPPER, "Victim drops a heart item, killer gains none"),
                Entry.bool(m + "ignore-same-ip-kills", "Ignore Same-IP Kills", Material.NAME_TAG, "Kills between same-IP players give nothing")));
    }

    public static ConfigMenu clumps(AscendantCore plugin, Player p) {
        String m = "modules.clumps.";
        return make(plugin, p, "<white><bold>Clumps", List.of(
                Entry.decimal(m + "radius", "Merge Radius", Material.COMPASS, "XP orbs within this range merge (blocks)", 0.5, 32),
                Entry.integer(m + "max-orb-value", "Max Orb Value", Material.EXPERIENCE_BOTTLE, "Largest merged orb (xp)", 1, 1_000_000)));
    }

    public static ConfigMenu stacker(AscendantCore plugin, Player p) {
        String m = "modules.item-stacker.";
        return make(plugin, p, "<white><bold>Item Stacker", List.of(
                Entry.decimal(m + "radius", "Merge Radius", Material.COMPASS, "Dropped items within range merge (blocks)", 0.5, 16),
                Entry.integer(m + "max-stack-size", "Max Stack Size", Material.CHEST, "Largest ground stack (shown as Item - 520x)", 2, 100000),
                Entry.choice(m + "mode", "List Mode", Material.WRITABLE_BOOK, "BLACKLIST: stack all but list. WHITELIST: only list", "BLACKLIST", "WHITELIST"),
                Entry.bool(m + "show-name", "Show Amount", Material.NAME_TAG, "Show the amount above stacked items"),
                Entry.action("Edit Item List", Material.BOOK, "Pick the items for the blacklist / whitelist", pl ->
                        ItemPicker.open(plugin, pl, "<white><bold>Stacker list", plugin.stacker()::isListed,
                                mat -> { plugin.stacker().toggle(mat); plugin.changed(); }, false, () -> stacker(plugin, pl)))));
    }

    public static ConfigMenu restarter(AscendantCore plugin, Player p) {
        String m = "modules.server-restarter.";
        Runnable reopen = () -> restarter(plugin, p).open();
        return make(plugin, p, "<white><bold>Server Restarter", List.of(
                Entry.integer(m + "countdown-seconds", "Countdown", Material.CLOCK, "Seconds of warning before restart", 5, 600),
                Entry.integer(m + "protect-seconds", "Combat Protection", Material.SHIELD, "No damage for the last N seconds", 0, 60),
                Entry.choice(m + "method", "Restart Method", Material.COMMAND_BLOCK, "AUTO uses the restart script if there is one", "AUTO", "SPIGOT_RESTART", "SHUTDOWN"),
                Entry.integer(m + "min-uptime-minutes", "Min Uptime", Material.REPEATER, "Ignore RAM/TPS triggers this long after start", 0, 1440),
                Entry.bool(m + "ram-percent.enabled", "RAM % Restart", Material.REDSTONE, "Restart when heap use stays high"),
                Entry.integer(m + "ram-percent.value", "RAM % Limit", Material.REDSTONE_TORCH, "Percent of max heap", 50, 99),
                Entry.bool(m + "ram-mb.enabled", "RAM MB Restart", Material.REDSTONE_BLOCK, "Restart at a specific heap size"),
                Entry.integer(m + "ram-mb.value", "RAM MB Limit", Material.COMPARATOR, "Used heap in MB", 512, 1_048_576),
                Entry.bool(m + "tps.enabled", "TPS Restart", Material.LIGHTNING_ROD, "Restart when TPS stays low"),
                Entry.decimal(m + "tps.value", "TPS Limit", Material.DAYLIGHT_DETECTOR, "5-minute average TPS", 1, 19.5),
                Entry.bool(m + "scheduled.enabled", "Scheduled Restart", Material.RECOVERY_COMPASS, "Restart at fixed times of day"),
                Entry.action("Add Restart Time", Material.WRITABLE_BOOK, "Type a time like 06:00 in the anvil", pl ->
                        plugin.prompts().ask(pl, "Restart time HH:mm", "06:00", text -> {
                            try {
                                LocalTime t = LocalTime.parse(text.length() == 4 ? "0" + text : text);
                                List<String> times = new ArrayList<>(plugin.getConfig().getStringList(m + "scheduled.times"));
                                String s = String.format("%02d:%02d", t.getHour(), t.getMinute());
                                if (!times.contains(s)) times.add(s);
                                plugin.getConfig().set(m + "scheduled.times", times);
                                plugin.saveConfig();
                                Fx.success(pl);
                            } catch (Exception ex) {
                                plugin.msg(pl, "<red>Use the format HH:mm, for example 06:00");
                                Fx.error(pl);
                            }
                            reopen.run();
                        }, reopen)),
                Entry.action("Clear Restart Times", Material.LAVA_BUCKET, "Remove every scheduled time", pl -> {
                    plugin.getConfig().set(m + "scheduled.times", new ArrayList<String>());
                    plugin.saveConfig();
                    Fx.off(pl);
                }),
                Entry.action("Restart Test", Material.PAPER, "Shows RAM, TPS, uptime and method in chat", pl ->
                        plugin.restarter().status().forEach(line -> plugin.msg(pl, "<gray>" + line))),
                Entry.action("Restart Now", Material.TNT, "Starts the countdown immediately", pl -> {
                    plugin.restarter().start("manual (" + pl.getName() + ")");
                    pl.closeInventory();
                }),
                Entry.action("Cancel Restart", Material.MILK_BUCKET, "Stops a running countdown", pl -> {
                    plugin.restarter().cancel();
                    Fx.on(pl);
                })));
    }

    public static ConfigMenu explosions(AscendantCore plugin, Player p) {
        String m = "modules.explosion-control.";
        List<Entry> l = new ArrayList<>();
        String[][] src = {{"global", "All Explosions", "NETHER_STAR"}, {"creeper", "Creeper", "CREEPER_HEAD"},
                {"tnt", "TNT", "TNT"}, {"tnt-minecart", "TNT Minecart", "TNT_MINECART"},
                {"end-crystal", "End Crystal", "END_CRYSTAL"}, {"other", "Other (beds, anchors, fireballs)", "FIRE_CHARGE"}};
        for (String[] s : src) {
            Material icon = Material.valueOf(s[2]);
            l.add(Entry.bool(m + s[0] + ".destruction", s[1] + " Block Damage", icon, "Explosions break blocks"));
            l.add(Entry.decimal(m + s[0] + ".power", s[1] + " Power", icon, "Blast radius multiplier (1.0 = vanilla)", 0, 20));
        }
        return make(plugin, p, "<white><bold>Explosion Control", l);
    }

    public static ConfigMenu optimizations(AscendantCore plugin, Player p) {
        String m = "modules.optimizations.";
        return make(plugin, p, "<white><bold>Optimizations", List.of(
                Entry.integer(m + "spawn-limits.monsters", "Monster Spawn Limit", Material.ZOMBIE_HEAD, "Per-player mob cap", 0, 500),
                Entry.integer(m + "spawn-limits.animals", "Animal Spawn Limit", Material.WHEAT, "Per-player mob cap", 0, 500),
                Entry.integer(m + "spawn-limits.water-animals", "Water Animal Limit", Material.COD, "Per-player mob cap", 0, 500),
                Entry.integer(m + "spawn-limits.water-ambient", "Water Ambient Limit", Material.TROPICAL_FISH, "Per-player mob cap", 0, 500),
                Entry.integer(m + "spawn-limits.ambient", "Ambient Limit", Material.BAT_SPAWN_EGG, "Per-player mob cap", 0, 500),
                Entry.integer(m + "view-distance", "Render Distance", Material.SPYGLASS, "Chunks sent to players", 2, 32),
                Entry.integer(m + "simulation-distance", "Simulation Distance", Material.CLOCK, "Chunks that tick", 2, 32),
                Entry.integer(m + "entity-tracking-range.players", "Tracking: Players", Material.PLAYER_HEAD, "Blocks (needs spigot.yml write)", 1, 512),
                Entry.integer(m + "entity-tracking-range.animals", "Tracking: Animals", Material.BEEF, "Blocks (needs spigot.yml write)", 1, 512),
                Entry.integer(m + "entity-tracking-range.monsters", "Tracking: Monsters", Material.BONE, "Blocks (needs spigot.yml write)", 1, 512),
                Entry.integer(m + "entity-tracking-range.misc", "Tracking: Misc", Material.ITEM_FRAME, "Blocks (needs spigot.yml write)", 1, 512),
                Entry.integer(m + "entity-tracking-range.other", "Tracking: Other", Material.ARMOR_STAND, "Blocks (needs spigot.yml write)", 1, 512),
                Entry.action("Write Tracking Ranges", Material.WRITTEN_BOOK, "Saves ranges to spigot.yml (backup made, restart needed)", pl -> {
                    plugin.msg(pl, plugin.optimization().writeTrackingRanges());
                    Fx.success(pl);
                })));
    }

    public static ConfigMenu villagers(AscendantCore plugin, Player p) {
        String m = "modules.click-villagers.";
        return make(plugin, p, "<white><bold>Click Villagers", List.of(
                Entry.bool(m + "allow-claim", "Claiming", Material.IRON_SHOVEL, "Shift + right-click with a shovel claims"),
                Entry.bool(m + "allow-anchor", "Anchoring", Material.SHEARS, "Shift + right-click with shears anchors"),
                Entry.bool(m + "claimed-invulnerable", "Claimed Invulnerable", Material.TOTEM_OF_UNDYING, "Claimed villagers take no damage")));
    }

    public static ConfigMenu naked(AscendantCore plugin, Player p) {
        String m = "modules.no-naked-kill.";
        return make(plugin, p, "<white><bold>No Naked Killing", List.of(
                Entry.integer(m + "naked-minutes", "Naked For (minutes)", Material.CLOCK, "Minutes without armor before protection starts", 1, 1440),
                Entry.bool(m + "require-poor-inventory", "Must Be Poor", Material.DIRT, "Only protect players carrying cheap stuff"),
                Entry.bool(m + "combat-removes-protection", "Combat Removes Protection", Material.IRON_SWORD, "Players in combat can be killed"),
                Entry.bool(m + "invisible-sword-removes-protection", "Invisible + Sword", Material.GLASS, "Invisible players holding a sword can be killed")));
    }

    public static ConfigMenu antiCrystal(AscendantCore plugin, Player p) {
        String m = "modules.anti-crystal.";
        return make(plugin, p, "<white><bold>Anti Crystal PvP", List.of(
                Entry.bool(m + "disable-crystals", "Disable End Crystals", Material.END_CRYSTAL, "Crystals can't be placed"),
                Entry.bool(m + "disable-anchors", "Disable Respawn Anchors", Material.RESPAWN_ANCHOR, "Anchors can't be placed or used"),
                Entry.decimal(m + "crystal-damage", "Crystal Damage", Material.END_CRYSTAL, "Damage multiplier. Default 1.0 (power 6)", 0, 10),
                Entry.bool(m + "crystal-destruction", "Crystal Block Damage", Material.OBSIDIAN, "Default ON: crystals break blocks"),
                Entry.decimal(m + "anchor-damage", "Anchor Damage", Material.RESPAWN_ANCHOR, "Damage multiplier. Default 1.0 (power 5)", 0, 10)));
    }

    public static ConfigMenu combat(AscendantCore plugin, Player p) {
        String m = "modules.combat.";
        return make(plugin, p, "<white><bold>Combat", List.of(
                Entry.integer(m + "timer-seconds", "Combat Timer", Material.CLOCK, "Seconds you stay tagged", 1, 600),
                Entry.choice(m + "display", "Timer Display", Material.OAK_SIGN, "Where the timer shows", "BOSSBAR", "ACTIONBAR", "NONE"),
                Entry.bool(m + "block-commands", "Block Commands", Material.COMMAND_BLOCK, "No commands while tagged"),
                Entry.bool(m + "disable-elytra", "Disable Elytra", Material.ELYTRA, "No gliding while tagged"),
                Entry.bool(m + "safezone.enabled", "Safezone Wall", Material.GLASS, "Needs WorldGuard: tagged players can't enter pvp=deny regions"),
                Entry.choice(m + "safezone.reaction", "Safezone Reaction", Material.SLIME_BALL, "What happens at the wall", "KNOCKBACK", "DAMAGE", "NONE"),
                Entry.decimal(m + "safezone.damage", "Wall Damage", Material.REDSTONE, "HP per touch (1.0 = half a heart)", 0, 20),
                Entry.choice(m + "safezone.wall-color", "Wall Color", Material.BLUE_STAINED_GLASS, "Color of the glass wall", "RED", "BLUE", "WHITE", "ORANGE",
                        "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE", "BROWN", "GREEN", "BLACK"),
                Entry.bool(m + "combat-log.drop-items", "Drop Items On Combat Log", Material.CHEST, "Logging out tagged drops your items"),
                Entry.bool(m + "combat-log.lightning-on-kill", "Lightning On Kill", Material.LIGHTNING_ROD, "Lightning when a player kills a player"),
                Entry.bool(m + "combat-log.kill-subtitle", "Kill Subtitle", Material.NAME_TAG, "\"X has died to Y\" subtitle"),
                Entry.bool(m + "combat-log.log-subtitle", "Combat Log Subtitle", Material.PAPER, "\"X combat logged to Y\" subtitle"),
                Entry.choice(m + "kill-sound", "Kill Sound", Material.NOTE_BLOCK, "Sound played where a player is killed", "NONE",
                        "DRAGON_GROWL", "WITHER_SPAWN", "WITHER_DEATH", "SKELETON_HURT", "DRAGON_FIREBALL_EXPLODE", "XP_PICKUP", "ANVIL_LAND"),
                Entry.bool(m + "head-drop", "Drop Heads", Material.PLAYER_HEAD, "Killed players drop their head (their real skin)"),
                Entry.integer(m + "head-drop-chance", "Head Drop Chance", Material.COMPARATOR, "Percent chance a head drops", 1, 100),
                Entry.action("Combat Item Cooldowns", Material.CLOCK, "Items that get a cooldown while in combat", pl ->
                        new SlotsMenu(plugin, pl, "<white><bold>Combat Item Cooldowns", "modules.combat.cooldowns.entries", 10, List.of(
                                new SlotsMenu.Col(SlotsMenu.Kind.ITEM, "item", "Item", Material.ITEM_FRAME),
                                new SlotsMenu.Col(SlotsMenu.Kind.SECONDS, "seconds", "Cooldown", Material.CLOCK)),
                                () -> combat(plugin, pl), null).open())));
    }

    public static ConfigMenu enderchest(AscendantCore plugin, Player p) {
        return make(plugin, p, "<white><bold>Enderchest Edit", List.of(
                Entry.choice("modules.enderchest-edit.type", "Slot Type", Material.ENDER_CHEST,
                        "Left: next type  Right: previous type", "HOPPER", "DISPENSER", "CHEST", "LARGE_CHEST")));
    }
}
