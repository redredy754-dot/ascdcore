package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;

/** Death ban: dying bans the player, or (spectator option) turns them into a spectator instead of banning. */
public final class DeathBanModule implements Listener {
    private final AscendantCore plugin;
    private final NamespacedKey flag;

    public DeathBanModule(AscendantCore plugin) {
        this.plugin = plugin;
        this.flag = new NamespacedKey(plugin, "death_banned");
    }

    private boolean flagged(Player p) { return p.getPersistentDataContainer().has(flag, PersistentDataType.BYTE); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        if (!plugin.enabled("death-ban")) return;
        Player p = e.getEntity();
        if (p.hasPermission("ascendantcore.bypass.deathban")) return;
        if (plugin.getConfig().getBoolean("modules.death-ban.spectator")) {
            p.getPersistentDataContainer().set(flag, PersistentDataType.BYTE, (byte) 1);
        } else {
            Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + p.getName() + " Death ban"));
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (plugin.enabled("death-ban") && flagged(p)) Bukkit.getScheduler().runTask(plugin, () -> p.setGameMode(GameMode.SPECTATOR));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!flagged(p)) return;
        if (plugin.enabled("death-ban")) p.setGameMode(GameMode.SPECTATOR);
        else revive(p);   // module turned off: everyone who was death-banned is freed
    }

    public void revive(Player p) {
        p.getPersistentDataContainer().remove(flag);
        p.setGameMode(GameMode.SURVIVAL);
    }
}
