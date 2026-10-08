package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Live spawn limits + view/simulation distance. Original values are restored when disabled. */
public final class OptimizationModule implements Listener {
    private static final String BASE = "modules.optimizations.";
    private static final SpawnCategory[] CATS = {SpawnCategory.MONSTER, SpawnCategory.ANIMAL, SpawnCategory.WATER_ANIMAL,
            SpawnCategory.WATER_AMBIENT, SpawnCategory.AMBIENT};
    private static final String[] KEYS = {"monsters", "animals", "water-animals", "water-ambient", "ambient"};

    private final AscendantCore plugin;
    private final Map<UUID, int[]> originals = new HashMap<>();

    public OptimizationModule(AscendantCore plugin) { this.plugin = plugin; }

    public void reload() {
        if (plugin.enabled("optimizations")) applyAll(); else restore();
    }

    private void applyAll() {
        for (World w : Bukkit.getWorlds()) apply(w);
    }

    private void apply(World w) {
        FileConfiguration c = plugin.getConfig();
        originals.computeIfAbsent(w.getUID(), id -> {
            int[] o = new int[CATS.length + 2];
            for (int i = 0; i < CATS.length; i++) o[i] = w.getSpawnLimit(CATS[i]);
            o[CATS.length] = w.getViewDistance();
            o[CATS.length + 1] = w.getSimulationDistance();
            return o;
        });
        for (int i = 0; i < CATS.length; i++) {
            w.setSpawnLimit(CATS[i], c.getInt(BASE + "spawn-limits." + KEYS[i]));
        }
        w.setViewDistance(Math.max(2, c.getInt(BASE + "view-distance", 8)));
        w.setSimulationDistance(Math.max(2, c.getInt(BASE + "simulation-distance", 6)));
    }

    public void restore() {
        for (World w : Bukkit.getWorlds()) {
            int[] o = originals.get(w.getUID());
            if (o == null) continue;
            for (int i = 0; i < CATS.length; i++) w.setSpawnLimit(CATS[i], o[i]);
            w.setViewDistance(o[CATS.length]);
            w.setSimulationDistance(o[CATS.length + 1]);
        }
        originals.clear();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        if (plugin.enabled("optimizations")) apply(e.getWorld());
    }

    /** Entity tracking range has no live API, so it is written to spigot.yml (backup first) and applies on restart. */
    public String writeTrackingRanges() {
        try {
            File f = new File("spigot.yml");
            if (!f.exists()) return "<red>spigot.yml was not found next to the server jar.";
            Files.copy(f.toPath(), new File("spigot.yml.ascendant-backup").toPath(), StandardCopyOption.REPLACE_EXISTING);
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            for (String k : new String[]{"players", "animals", "monsters", "misc", "other"}) {
                y.set("world-settings.default.entity-tracking-range." + k,
                        plugin.getConfig().getInt(BASE + "entity-tracking-range." + k));
            }
            y.save(f);
            return "<green>Tracking ranges written to spigot.yml <gray>(backup: spigot.yml.ascendant-backup). Restart to apply.";
        } catch (Exception ex) {
            return "<red>Could not write spigot.yml: " + ex.getMessage();
        }
    }
}
