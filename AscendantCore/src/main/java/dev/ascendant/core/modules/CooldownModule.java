package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/** Item cooldowns (left/right use per item), mace cooldown, trident cooldown, pearl cooldown. */
public final class CooldownModule implements Listener {
    private record Entry(Material item, boolean left, int leftSec, boolean right, int rightSec) {}

    private final AscendantCore plugin;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private List<Entry> entries = List.of();

    public CooldownModule(AscendantCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        List<Entry> list = new ArrayList<>();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("modules.item-cooldown.entries");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                Material m = Material.matchMaterial(s.getString(k + ".item", ""));
                if (m == null) continue;
                list.add(new Entry(m, s.getBoolean(k + ".left.enabled"), s.getInt(k + ".left.seconds"),
                        s.getBoolean(k + ".right.enabled"), s.getInt(k + ".right.seconds")));
            }
        }
        entries = list;
    }

    private boolean ready(Player p, String key) {
        Long until = cooldowns.getOrDefault(p.getUniqueId(), Map.of()).get(key);
        long now = System.currentTimeMillis();
        if (until != null && until > now) {
            p.sendActionBar(Text.mm("<red>On cooldown: <white>" + (int) Math.ceil((until - now) / 1000.0) + "s"));
            return false;
        }
        return true;
    }

    private void start(Player p, String key, int seconds) {
        cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(key, System.currentTimeMillis() + seconds * 1000L);
    }

    private boolean use(Player p, String key, int seconds) {
        if (!ready(p, key)) return false;
        start(p, key, seconds);
        return true;
    }

    private int sec(String path, int def) { return Math.max(1, plugin.getConfig().getInt(path, def)); }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        ItemStack it = e.getItem();
        if (a == Action.PHYSICAL || it == null) return;
        Player p = e.getPlayer();
        Material m = it.getType();
        boolean left = a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK;
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        if (plugin.enabled("item-cooldown")) {
            for (Entry en : entries) {
                if (en.item() != m) continue;
                if (left && en.left() && !use(p, "i" + m + "L", en.leftSec())) { e.setCancelled(true); return; }
                if (right && en.right() && !use(p, "i" + m + "R", en.rightSec())) { e.setCancelled(true); return; }
            }
        }
        if (right && m == Material.TRIDENT && plugin.enabled("trident-cooldown") && !ready(p, "trident")) e.setCancelled(true);
        if (right && m == Material.ENDER_PEARL && plugin.enabled("pearl-cooldown") && !ready(p, "pearl")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (!plugin.enabled("item-cooldown")) return;
        Player p = e.getPlayer();
        ItemStack it = p.getInventory().getItem(e.getHand());
        if (it == null) return;
        for (Entry en : entries) {
            if (en.item() == it.getType() && en.right() && !use(p, "i" + en.item() + "R", en.rightSec())) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        Material m = p.getInventory().getItemInMainHand().getType();
        if (plugin.enabled("item-cooldown")) {
            for (Entry en : entries) {
                if (en.item() == m && en.left() && !use(p, "i" + m + "L", en.leftSec())) { e.setCancelled(true); return; }
            }
        }
        if (m == Material.MACE && plugin.enabled("mace.cooldown")
                && !use(p, "mace", sec("modules.mace.cooldown.seconds", 10))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player p)) return;
        if (e.getEntity() instanceof Trident && plugin.enabled("trident-cooldown")) {
            int s = sec("modules.trident-cooldown.seconds", 10);
            start(p, "trident", s);
            p.setCooldown(Material.TRIDENT, s * 20);
        } else if (e.getEntity() instanceof EnderPearl && plugin.enabled("pearl-cooldown")) {
            int s = sec("modules.pearl-cooldown.seconds", 10);
            start(p, "pearl", s);
            p.setCooldown(Material.ENDER_PEARL, s * 20);
        }
    }

    @EventHandler
    public void onRiptide(PlayerRiptideEvent e) {
        if (!plugin.enabled("trident-cooldown")) return;
        int s = sec("modules.trident-cooldown.seconds", 10);
        start(e.getPlayer(), "trident", s);
        e.getPlayer().setCooldown(Material.TRIDENT, s * 20);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { cooldowns.remove(e.getPlayer().getUniqueId()); }
}
