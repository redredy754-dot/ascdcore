package dev.ascendant.core.util;

import org.bukkit.Material;

import java.util.*;

/** Item categories used by every item list in the GUI. */
public final class Categories {
    public enum Category {
        BLOCKS("Blocks"), TOOLS("Tools"), ARMOR("Armor"), POTS("Potions"), FOOD("Food"), MISC("Misc");
        public final String label;
        Category(String label) { this.label = label; }
    }

    private static Map<Category, List<Material>> cache;

    private Categories() {}

    public static synchronized List<Material> items(Category c) {
        if (cache == null) {
            cache = new EnumMap<>(Category.class);
            for (Category cat : Category.values()) cache.put(cat, new ArrayList<>());
            for (Material m : Material.values()) {
                if (m.isLegacy() || !m.isItem() || m.isAir()) continue;
                cache.get(classify(m)).add(m);
            }
            cache.values().forEach(l -> l.sort(Comparator.comparing(Enum::name)));
        }
        return cache.get(c);
    }

    public static Category classify(Material m) {
        String n = m.name();
        if (m == Material.POTION || m == Material.SPLASH_POTION || m == Material.LINGERING_POTION
                || m == Material.TIPPED_ARROW) return Category.POTS;
        if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")
                || n.endsWith("HORSE_ARMOR") || n.equals("WOLF_ARMOR") || m == Material.ELYTRA || m == Material.SHIELD
                || m == Material.TURTLE_HELMET) return Category.ARMOR;
        if (n.endsWith("_SWORD") || n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL")
                || n.endsWith("_HOE") || m == Material.TRIDENT || m == Material.MACE || m == Material.BOW
                || m == Material.CROSSBOW || m == Material.FISHING_ROD || m == Material.SHEARS
                || m == Material.FLINT_AND_STEEL || m == Material.BRUSH || m == Material.SPYGLASS
                || m == Material.COMPASS || m == Material.CLOCK) return Category.TOOLS;
        if (m.isEdible()) return Category.FOOD;
        if (m.isBlock()) return Category.BLOCKS;
        return Category.MISC;
    }
}
