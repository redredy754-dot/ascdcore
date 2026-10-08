package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Sends the well-known invisible "fair play" codes that minimap mods (Xaero, VoxelMap) listen for in chat.
 * Best effort: it only works on mods that honor those codes. The codes are editable in config.yml (& = section sign).
 */
public final class AntiMinimapModule implements Listener {
    private final AscendantCore plugin;

    public AntiMinimapModule(AscendantCore plugin) { this.plugin = plugin; }

    private void send(Player p) {
        if (!plugin.enabled("anti-minimap")) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            for (String code : plugin.getConfig().getStringList("modules.anti-minimap.codes")) {
                p.sendMessage(Component.text(code.replace('&', '\u00a7')));
            }
        }, 20L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { send(e.getPlayer()); }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) { send(e.getPlayer()); }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) { send(e.getPlayer()); }
}
