package dev.ascendant.core.util;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** GUI sounds: noteblock "bit" (the sound you get on top of an emerald block) at different pitches. */
public final class Fx {
    private Fx() {}

    private static void bit(Player p, float pitch) {
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.7f, pitch);
    }

    public static void click(Player p) { bit(p, 1.2f); }
    public static void on(Player p) { bit(p, 1.9f); }
    public static void off(Player p) { bit(p, 0.6f); }
    public static void error(Player p) { bit(p, 0.5f); }
    public static void success(Player p) { bit(p, 2.0f); }
    public static void open(Player p) { bit(p, 1.0f); }
    public static void page(Player p) { bit(p, 1.5f); }
}
