package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.*;

public final class PotionModule implements Listener {
    private final AscendantCore plugin;
    private final Set<String> banned = new HashSet<>();

    public PotionModule(AscendantCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        banned.clear();
        banned.addAll(plugin.getConfig().getStringList("modules.potion-limiter.banned"));
    }

    public boolean isBanned(PotionType t) { return banned.contains(t.getKey().getKey()); }

    public void set(PotionType t, boolean ban) {
        if (ban) banned.add(t.getKey().getKey()); else banned.remove(t.getKey().getKey());
        List<String> l = new ArrayList<>(banned);
        Collections.sort(l);
        plugin.getConfig().set("modules.potion-limiter.banned", l);
        plugin.saveConfig();
    }

    private boolean blocks(ItemStack it) {
        if (it == null || !plugin.enabled("potion-limiter")) return false;
        Material m = it.getType();
        if (m != Material.POTION && m != Material.SPLASH_POTION && m != Material.LINGERING_POTION) return false;
        if (!(it.getItemMeta() instanceof PotionMeta pm)) return false;
        PotionType t = pm.getBasePotionType();
        return t != null && isBanned(t);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent e) {
        if (blocks(e.getItem())) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(Text.mm("<red>That potion is banned on this server."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onThrow(ProjectileLaunchEvent e) {
        if (e.getEntity() instanceof ThrownPotion tp && blocks(tp.getItem())) {
            e.setCancelled(true);
            if (tp.getShooter() instanceof Player p) p.sendActionBar(Text.mm("<red>That potion is banned on this server."));
        }
    }
}
