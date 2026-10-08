package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

import java.util.*;

/** Custom crafting: built-in cheap presets plus recipes made in the in-game editor. */
public final class CraftingModule {
    public record Preset(String id, String name, Material result, String[] shape, Map<Character, Material> ing, String text) {}

    public static final List<Preset> PRESETS = List.of(
            new Preset("cheap-golden-apple", "Cheap Golden Apple", Material.GOLDEN_APPLE, new String[]{" G ", "GAG", " G "},
                    Map.of('G', Material.GOLD_INGOT, 'A', Material.APPLE), "1 apple middle + 4 gold ingots around it"),
            new Preset("nugget-apple", "Nugget Apple", Material.GOLDEN_APPLE, new String[]{"NNN", "NAN", "NNN"},
                    Map.of('N', Material.GOLD_NUGGET, 'A', Material.APPLE), "1 apple + 8 gold nuggets around it"),
            new Preset("super-cheap-gap", "Super Cheap Gap", Material.GOLDEN_APPLE, new String[]{"G", "A", "G"},
                    Map.of('G', Material.GOLD_INGOT, 'A', Material.APPLE), "1 apple with 1 gold ingot above and below"),
            new Preset("cheap-nugget-gap", "Cheap Nugget Gap", Material.GOLDEN_APPLE, new String[]{" N ", "NAN", " N "},
                    Map.of('N', Material.GOLD_NUGGET, 'A', Material.APPLE), "1 apple middle + 4 gold nuggets around it"),
            new Preset("cheap-anvil", "Cheap Anvil", Material.ANVIL, new String[]{"III", " I ", "III"},
                    Map.of('I', Material.IRON_INGOT), "I shape of iron ingots"),
            new Preset("super-cheap-anvil", "Super Cheap Anvil", Material.ANVIL, new String[]{"III", " I ", "III"},
                    Map.of('I', Material.IRON_NUGGET), "I shape of iron nuggets"),
            new Preset("breeze-rod", "Breeze Rod", Material.BREEZE_ROD, new String[]{"D", "S"},
                    Map.of('D', Material.DIAMOND, 'S', Material.STICK), "1 diamond on top of 1 stick"),
            new Preset("cobweb", "Cobweb", Material.COBWEB, new String[]{"SSS", "SSS", "SSS"},
                    Map.of('S', Material.STRING), "9 string"),
            new Preset("cheap-cobweb", "Cheap Cobweb", Material.COBWEB, new String[]{"S S", " S ", "S S"},
                    Map.of('S', Material.STRING), "X shape of string"));

    private final AscendantCore plugin;
    private final Set<NamespacedKey> registered = new HashSet<>();
    private String lastSig = "";

    public CraftingModule(AscendantCore plugin) { this.plugin = plugin; }

    private String signature() {
        var c = plugin.getConfig();
        StringBuilder sb = new StringBuilder().append(plugin.enabled("custom-crafting"));
        for (Preset p : PRESETS) sb.append(c.getBoolean("modules.custom-crafting.presets." + p.id()));
        ConfigurationSection s = c.getConfigurationSection("modules.custom-crafting.custom");
        if (s != null) for (String k : s.getKeys(false)) {
            sb.append(k).append(s.getString(k + ".output")).append(s.getInt(k + ".amount")).append(s.getStringList(k + ".grid"));
        }
        return sb.toString();
    }

    public void reload() {
        String sig = signature();
        if (sig.equals(lastSig)) return;
        lastSig = sig;
        for (NamespacedKey k : registered) Bukkit.removeRecipe(k);
        registered.clear();
        if (!plugin.enabled("custom-crafting")) return;
        var c = plugin.getConfig();
        for (Preset p : PRESETS) {
            if (!c.getBoolean("modules.custom-crafting.presets." + p.id())) continue;
            ShapedRecipe r = new ShapedRecipe(new NamespacedKey(plugin, p.id()), new ItemStack(p.result()));
            r.shape(p.shape());
            p.ing().forEach(r::setIngredient);
            add(r);
        }
        ConfigurationSection s = c.getConfigurationSection("modules.custom-crafting.custom");
        if (s != null) for (String id : s.getKeys(false)) buildCustom(s, id);
        for (var p : Bukkit.getOnlinePlayers()) p.discoverRecipes(registered);
    }

    private void add(ShapedRecipe r) {
        try {
            if (Bukkit.addRecipe(r)) registered.add(r.getKey());
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not register recipe " + r.getKey() + ": " + ex.getMessage());
        }
    }

    private void buildCustom(ConfigurationSection s, String id) {
        Material out = Material.matchMaterial(s.getString(id + ".output", ""));
        List<String> grid = s.getStringList(id + ".grid");
        if (out == null || grid.size() != 9) return;
        int minR = 3, maxR = -1, minC = 3, maxC = -1;
        for (int i = 0; i < 9; i++) {
            Material m = Material.matchMaterial(grid.get(i));
            if (m == null || m.isAir()) continue;
            minR = Math.min(minR, i / 3); maxR = Math.max(maxR, i / 3);
            minC = Math.min(minC, i % 3); maxC = Math.max(maxC, i % 3);
        }
        if (maxR < 0) return;
        Map<Material, Character> chars = new LinkedHashMap<>();
        String[] shape = new String[maxR - minR + 1];
        for (int r = minR; r <= maxR; r++) {
            StringBuilder row = new StringBuilder();
            for (int col = minC; col <= maxC; col++) {
                Material m = Material.matchMaterial(grid.get(r * 3 + col));
                if (m == null || m.isAir()) { row.append(' '); continue; }
                row.append(chars.computeIfAbsent(m, k -> (char) ('A' + chars.size())));
            }
            shape[r - minR] = row.toString();
        }
        ShapedRecipe rec = new ShapedRecipe(new NamespacedKey(plugin, "custom_" + id),
                new ItemStack(out, Math.max(1, Math.min(64, s.getInt(id + ".amount", 1)))));
        rec.shape(shape);
        chars.forEach((m, ch) -> rec.setIngredient(ch, m));
        add(rec);
    }
}
