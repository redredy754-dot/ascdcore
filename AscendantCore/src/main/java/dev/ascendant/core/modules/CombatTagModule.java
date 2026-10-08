package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;

/**
 * Combat tag + safezone wall (WorldGuard pvp=deny regions) + combat log handling.
 * The safezone wall only ever affects players who ARE in combat; everybody else walks in freely.
 */
public final class CombatTagModule implements Listener {
    private record Tag(long until, UUID opponent) {}

    private final AscendantCore plugin;
    private final WgHook wg;
    private final Map<UUID, Tag> tags = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Long> wallCooldown = new HashMap<>();

    public CombatTagModule(AscendantCore plugin) {
        this.plugin = plugin;
        this.wg = WgHook.create();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    private boolean on() { return plugin.enabled("combat"); }
    private String cfgStr(String p, String d) { return plugin.getConfig().getString("modules.combat." + p, d); }
    private boolean cfgBool(String p) { return plugin.getConfig().getBoolean("modules.combat." + p); }

    public boolean inCombat(Player p) {
        Tag t = tags.get(p.getUniqueId());
        return t != null && t.until() > System.currentTimeMillis();
    }

    private boolean safe(Location loc) {
        return wg != null && cfgBool("safezone.enabled") && wg.isSafe(loc);
    }

    private void tag(Player p, Player opponent) {
        boolean already = inCombat(p);
        long ms = Math.max(1, plugin.getConfig().getInt("modules.combat.timer-seconds", 30)) * 1000L;
        tags.put(p.getUniqueId(), new Tag(System.currentTimeMillis() + ms, opponent.getUniqueId()));
        if (!already) p.sendMessage(Text.mm("<red>You are now in combat! Do not log out."));
    }

    private void untag(UUID id, boolean notify) {
        tags.remove(id);
        BossBar bar = bars.remove(id);
        if (bar != null) bar.removeAll();
        Player p = Bukkit.getPlayer(id);
        if (notify && p != null) p.sendMessage(Text.mm("<green>You are no longer in combat."));
    }

    private void tick() {
        if (tags.isEmpty()) return;
        String display = cfgStr("display", "BOSSBAR").toUpperCase(Locale.ROOT);
        long total = Math.max(1, plugin.getConfig().getInt("modules.combat.timer-seconds", 30)) * 1000L;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Tag> en : new ArrayList<>(tags.entrySet())) {
            Player p = Bukkit.getPlayer(en.getKey());
            long left = en.getValue().until() - now;
            if (p == null || left <= 0 || !on()) { untag(en.getKey(), p != null && left <= 0 && on()); continue; }
            int sec = (int) Math.ceil(left / 1000.0);
            if (display.equals("BOSSBAR")) {
                BossBar bar = bars.computeIfAbsent(p.getUniqueId(), k -> {
                    BossBar b = Bukkit.createBossBar("", BarColor.RED, BarStyle.SOLID);
                    b.addPlayer(p);
                    return b;
                });
                bar.setTitle("In combat: " + sec + "s");
                bar.setProgress(Math.max(0.0, Math.min(1.0, left / (double) total)));
            } else {
                BossBar old = bars.remove(p.getUniqueId());
                if (old != null) old.removeAll();
                if (display.equals("ACTIONBAR")) p.sendActionBar(Text.mm("<white>In combat: <gray>" + sec + "s"));
            }
        }
    }

    private Player attacker(Entity d) {
        if (d instanceof Player p) return p;
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!on() || !(e.getEntity() instanceof Player victim)) return;
        Player a = attacker(e.getDamager());
        if (a == null || a.equals(victim)) return;
        if (safe(victim.getLocation()) || safe(a.getLocation())) return;   // no combat tags inside safezones
        tag(a, victim);
        tag(victim, a);
    }

    private void announce(String mini) {
        Title t = Title.title(Component.empty(), Text.mm(mini),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(500)));
        for (Player p : Bukkit.getOnlinePlayers()) p.showTitle(t);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        untag(victim.getUniqueId(), false);
        if (!on()) return;
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;
        if (cfgBool("combat-log.lightning-on-kill")) victim.getWorld().strikeLightningEffect(victim.getLocation());
        if (cfgBool("combat-log.kill-subtitle")) {
            announce("<white>" + victim.getName() + " <gray>has died to <white>" + killer.getName());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        boolean wasTagged = on() && inCombat(p);
        Tag tag = tags.get(p.getUniqueId());
        untag(p.getUniqueId(), false);
        if (!wasTagged || plugin.restarter().running()) return;
        PlayerQuitEvent.QuitReason r = e.getReason();
        if (r != PlayerQuitEvent.QuitReason.DISCONNECTED && r != PlayerQuitEvent.QuitReason.TIMED_OUT) return;
        if (cfgBool("combat-log.drop-items")) {
            Location loc = p.getLocation();
            for (ItemStack it : p.getInventory().getContents()) {
                if (it != null && !it.getType().isAir()) p.getWorld().dropItemNaturally(loc, it);
            }
            p.getInventory().clear();               // dropped first, cleared second: items exist exactly once
        }
        if (cfgBool("combat-log.log-subtitle")) {
            String opp = tag == null ? "someone" : String.valueOf(Bukkit.getOfflinePlayer(tag.opponent()).getName());
            announce("<white>" + p.getName() + " <gray>combat logged to <white>" + opp);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (on() && cfgBool("block-commands") && inCombat(e.getPlayer()) && !e.getPlayer().hasPermission("ascendantcore.admin")) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Text.mm("<red>You can't use commands while in combat."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent e) {
        if (on() && cfgBool("disable-elytra") && e.isGliding() && e.getEntity() instanceof Player p && inCombat(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedBlock() || !on() || wg == null) return;
        Player p = e.getPlayer();
        if (!inCombat(p) || !cfgBool("safezone.enabled")) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (!wg.isSafe(to) || wg.isSafe(from)) return;
        e.setCancelled(true);
        Vector away = from.toVector().subtract(to.toVector());
        String reaction = cfgStr("safezone.reaction", "KNOCKBACK").toUpperCase(Locale.ROOT);
        if (reaction.equals("KNOCKBACK") && away.lengthSquared() > 0) {
            p.setVelocity(away.normalize().multiply(0.6).setY(0.25));
        } else if (reaction.equals("DAMAGE")) {
            double dmg = plugin.getConfig().getDouble("modules.combat.safezone.damage", 1.0);
            if (p.getHealth() > dmg + 0.5) p.damage(dmg);
        }
        wall(p, from, to);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (!on() || wg == null || !cfgBool("safezone.enabled") || e.getTo() == null) return;
        PlayerTeleportEvent.TeleportCause c = e.getCause();
        if (c != PlayerTeleportEvent.TeleportCause.ENDER_PEARL && c != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) return;
        Player p = e.getPlayer();
        if (inCombat(p) && wg.isSafe(e.getTo()) && !wg.isSafe(e.getFrom())) e.setCancelled(true);
    }

    /** Shows a short-lived glass wall (client side only) where the player tried to enter. */
    private void wall(Player p, Location from, Location to) {
        long now = System.currentTimeMillis();
        Long last = wallCooldown.get(p.getUniqueId());
        if (last != null && now - last < 800) return;
        wallCooldown.put(p.getUniqueId(), now);
        Material glass = Material.matchMaterial(cfgStr("safezone.wall-color", "RED") + "_STAINED_GLASS");
        if (glass == null) glass = Material.RED_STAINED_GLASS;
        boolean alongZ = Math.abs(to.getX() - from.getX()) >= Math.abs(to.getZ() - from.getZ());
        World w = to.getWorld();
        List<Block> changed = new ArrayList<>();
        for (int a = -2; a <= 2; a++) {
            for (int y = -1; y <= 3; y++) {
                Block b = w.getBlockAt(to.getBlockX() + (alongZ ? 0 : a), to.getBlockY() + y, to.getBlockZ() + (alongZ ? a : 0));
                if (b.isPassable() && !b.isLiquid()) {
                    p.sendBlockChange(b.getLocation(), glass.createBlockData());
                    changed.add(b);
                }
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            for (Block b : changed) p.sendBlockChange(b.getLocation(), b.getBlockData());
        }, 40L);
    }
}
