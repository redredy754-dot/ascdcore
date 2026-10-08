package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** AFK players are tagged in the tab list and cannot be hurt or targeted until they move again. */
public final class AfkModule implements Listener {
    private final AscendantCore plugin;
    private final Map<UUID, Long> last = new HashMap<>();
    private final Set<UUID> afk = new HashSet<>();

    public AfkModule(AscendantCore plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::scan, 100L, 100L);
    }

    private void scan() {
        if (!plugin.enabled("afk-protection")) {
            for (UUID id : new HashSet<>(afk)) wake(Bukkit.getPlayer(id));
            return;
        }
        long limit = Math.max(5, plugin.getConfig().getInt("modules.afk-protection.seconds", 300)) * 1000L;
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            long l = last.getOrDefault(p.getUniqueId(), now);
            if (!afk.contains(p.getUniqueId()) && now - l > limit) {
                afk.add(p.getUniqueId());
                p.playerListName(Component.text("[AFK] ", NamedTextColor.GRAY).append(p.name()));
            }
        }
    }

    private void wake(Player p) {
        if (p == null) return;
        if (afk.remove(p.getUniqueId())) p.playerListName(null);
    }

    private void touch(Player p) {
        last.put(p.getUniqueId(), System.currentTimeMillis());
        if (afk.contains(p.getUniqueId())) wake(p);
    }

    public boolean isAfk(Player p) { return afk.contains(p.getUniqueId()); }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        var f = e.getFrom();
        var t = e.getTo();
        Player p = e.getPlayer();
        boolean looked = Math.abs(f.getYaw() - t.getYaw()) > 1 || Math.abs(f.getPitch() - t.getPitch()) > 1;
        double dx = f.getX() - t.getX();
        double dz = f.getZ() - t.getZ();
        boolean walked = dx * dx + dz * dz > 0.09 && !p.isInWater() && !p.isInsideVehicle() && !p.isGliding();
        if (looked || walked) touch(p);
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> touch(p));
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) { touch(e.getPlayer()); }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) { touch(e.getPlayer()); }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) { touch(e.getPlayer()); }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { last.put(e.getPlayer().getUniqueId(), System.currentTimeMillis()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        last.remove(e.getPlayer().getUniqueId());
        afk.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (plugin.enabled("afk-protection") && e.getEntity() instanceof Player p && afk.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (plugin.enabled("afk-protection") && e.getTarget() instanceof Player p && afk.contains(p.getUniqueId())) e.setCancelled(true);
    }
}
