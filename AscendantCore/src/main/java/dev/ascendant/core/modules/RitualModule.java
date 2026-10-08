package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Fx;
import dev.ascendant.core.util.Items;
import dev.ascendant.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rituals: crafting a configured item does not hand it over instantly. Everyone sees a boss bar with the crafter's
 * coordinates and the time left; when it ends the item is delivered. The item is created exactly once - quitting or
 * a plugin shutdown delivers it immediately instead of losing it.
 */
public final class RitualModule implements Listener {
    private static final class Ritual {
        final UUID id;
        final String name;
        final ItemStack item;
        final BossBar bar;
        final int total;
        int remaining;
        Location last;

        Ritual(UUID id, String name, ItemStack item, BossBar bar, int total, Location last) {
            this.id = id;
            this.name = name;
            this.item = item;
            this.bar = bar;
            this.total = total;
            this.remaining = total;
            this.last = last;
        }
    }

    private final AscendantCore plugin;
    private final Map<UUID, Ritual> active = new HashMap<>();

    public RitualModule(AscendantCore plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private ConfigurationSection entryFor(org.bukkit.Material m) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("modules.rituals.entries");
        if (s == null) return null;
        for (String k : s.getKeys(false)) {
            if (m.name().equalsIgnoreCase(s.getString(k + ".item", ""))) return s.getConfigurationSection(k);
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!plugin.enabled("rituals") || !(e.getWhoClicked() instanceof Player p)) return;
        ItemStack res = e.getCurrentItem();
        if (res == null) return;
        ConfigurationSection s = entryFor(res.getType());
        if (s == null) return;
        e.setCancelled(true);
        if (e.isShiftClick()) { plugin.msg(p, "<red>Rituals can't be shift-crafted."); Fx.error(p); return; }
        if (active.containsKey(p.getUniqueId())) { plugin.msg(p, "<red>You are already performing a ritual."); Fx.error(p); return; }
        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        for (int i = 0; i < matrix.length; i++) {          // ingredients are consumed once, here
            if (matrix[i] == null) continue;
            if (matrix[i].getAmount() <= 1) matrix[i] = null;
            else { matrix[i] = matrix[i].clone(); matrix[i].setAmount(matrix[i].getAmount() - 1); }
        }
        inv.setMatrix(matrix);
        BarColor color;
        try { color = BarColor.valueOf(s.getString("color", "RED").toUpperCase()); } catch (IllegalArgumentException ex) { color = BarColor.RED; }
        int seconds = Math.max(1, s.getInt("seconds", 60));
        BossBar bar = Bukkit.createBossBar("", color, BarStyle.SOLID);
        for (Player o : Bukkit.getOnlinePlayers()) bar.addPlayer(o);
        Ritual r = new Ritual(p.getUniqueId(), p.getName(), res.clone(), bar, seconds, p.getLocation());
        active.put(p.getUniqueId(), r);
        update(r);
        Fx.success(p);
        Location table = inv.getLocation() != null ? inv.getLocation().toCenterLocation() : p.getLocation();
        effects(table);
    }

    /** Wither spawn sound + 4 lightning strikes in a circle, 5 blocks around the crafting table. */
    private void effects(Location center) {
        World w = center.getWorld();
        if (w == null) return;
        if (plugin.getConfig().getBoolean("modules.rituals.sound", true)) {
            w.playSound(center, Sound.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 16f, 1f);
        }
        if (!plugin.getConfig().getBoolean("modules.rituals.lightning", true)) return;
        for (int i = 0; i < 4; i++) {
            double angle = i * (Math.PI / 2);
            Location strike = center.clone().add(Math.cos(angle) * 5, 0, Math.sin(angle) * 5);
            w.strikeLightningEffect(strike);          // visual + thunder only: nothing is burned or damaged
        }
    }

    private void update(Ritual r) {
        Location l = r.last;
        String coords = l == null ? "" : " | X: " + l.getBlockX() + " Y: " + l.getBlockY() + " Z: " + l.getBlockZ();
        r.bar.setTitle(r.name + " is crafting the " + Text.pretty(r.item.getType().name()) + coords
                + " | " + (r.remaining / 60) + ":" + String.format("%02d", r.remaining % 60));
        r.bar.setProgress(Math.max(0.0, Math.min(1.0, r.remaining / (double) r.total)));
    }

    private void tick() {
        for (Ritual r : new ArrayList<>(active.values())) {
            Player p = Bukkit.getPlayer(r.id);
            if (p != null) r.last = p.getLocation();
            r.remaining--;
            if (r.remaining <= 0) finish(r, p);
            else update(r);
        }
    }

    private void finish(Ritual r, Player p) {
        if (active.remove(r.id) == null) return;           // can only ever finish once
        r.bar.removeAll();
        if (p != null && p.isOnline() && !p.isDead()) Items.give(p, r.item);
        else if (r.last != null && r.last.getWorld() != null) r.last.getWorld().dropItemNaturally(r.last, r.item);
        Bukkit.broadcast(Text.mm(plugin.getConfig().getString("messages.prefix", "") + "<white>" + r.name
                + " <gray>finished crafting the <white>" + Text.pretty(r.item.getType().name())));
    }

    public void finishAll() {
        for (Ritual r : new ArrayList<>(active.values())) finish(r, Bukkit.getPlayer(r.id));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Ritual r = active.get(e.getPlayer().getUniqueId());
        if (r != null) finish(r, e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        for (Ritual r : active.values()) r.bar.addPlayer(e.getPlayer());
    }
}
