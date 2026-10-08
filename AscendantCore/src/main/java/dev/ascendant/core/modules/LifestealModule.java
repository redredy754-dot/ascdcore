package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Fx;
import dev.ascendant.core.util.Items;
import dev.ascendant.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Hearts live in the player's own data (PDC), so they survive restarts and can't be edited by anyone else. */
public final class LifestealModule implements Listener {
    private final AscendantCore plugin;
    private final NamespacedKey heartsKey;
    private final NamespacedKey elimKey;
    private final NamespacedKey itemKey;

    public LifestealModule(AscendantCore plugin) {
        this.plugin = plugin;
        this.heartsKey = new NamespacedKey(plugin, "hearts");
        this.elimKey = new NamespacedKey(plugin, "eliminated");
        this.itemKey = new NamespacedKey(plugin, "heart_item");
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }
    private int start() { return cfg().getInt("modules.lifesteal.starting-hearts", 10); }
    private int max() { return Math.max(1, cfg().getInt("modules.lifesteal.max-hearts", 20)); }

    public int hearts(Player p) {
        return Math.min(max(), p.getPersistentDataContainer().getOrDefault(heartsKey, PersistentDataType.INTEGER, start()));
    }

    private void setHearts(Player p, int h) {
        p.getPersistentDataContainer().set(heartsKey, PersistentDataType.INTEGER, Math.max(0, h));
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        if (a == null) return;
        a.setBaseValue(Math.max(1, h) * 2.0);
        if (p.getHealth() > a.getValue()) p.setHealth(a.getValue());
    }

    private boolean eliminated(Player p) {
        return p.getPersistentDataContainer().has(elimKey, PersistentDataType.BYTE);
    }

    public ItemStack heartItem(int amount) {
        ItemStack it = Items.of(Material.NETHER_STAR, "<red><bold>❤ Heart", "<gray>Right-click to gain a heart");
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        it.setAmount(amount);
        return it;
    }

    private boolean isHeartItem(ItemStack it) {
        return it != null && it.getType() == Material.NETHER_STAR && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.enabled("lifesteal")) return;
        Player p = e.getPlayer();
        if (eliminated(p)) {
            if ("BAN".equalsIgnoreCase(cfg().getString("modules.lifesteal.elimination"))) {
                p.getPersistentDataContainer().remove(elimKey);     // they were unbanned, start over
                setHearts(p, start());
            } else {
                setHearts(p, 0);
                p.setGameMode(GameMode.SPECTATOR);
                return;
            }
        }
        setHearts(p, hearts(p));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (!plugin.enabled("lifesteal")) return;
        Player p = e.getPlayer();
        if (eliminated(p) && !"BAN".equalsIgnoreCase(cfg().getString("modules.lifesteal.elimination"))) {
            Bukkit.getScheduler().runTask(plugin, () -> p.setGameMode(GameMode.SPECTATOR));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        if (!plugin.enabled("lifesteal")) return;
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        boolean pvp = killer != null && !killer.equals(victim);
        if (pvp && cfg().getBoolean("modules.lifesteal.ignore-same-ip-kills", true) && sameIp(killer, victim)) return;
        boolean dropInstead = cfg().getBoolean("modules.lifesteal.heart-drops-instead", false);
        if (!pvp && !cfg().getBoolean("modules.lifesteal.lose-heart-on-natural-death")) return;

        int remaining = hearts(victim) - cfg().getInt("modules.lifesteal.hearts-lost-on-death", 1);
        setHearts(victim, remaining);
        if (remaining <= 0) eliminate(victim);

        if (pvp && dropInstead) {
            victim.getWorld().dropItemNaturally(victim.getLocation(),
                    heartItem(Math.max(1, cfg().getInt("modules.lifesteal.hearts-per-kill", 1))));
        } else if (pvp) {
            int gain = cfg().getInt("modules.lifesteal.hearts-per-kill", 1);
            int have = hearts(killer);
            if (have + gain <= max()) {
                setHearts(killer, have + gain);
                Fx.success(killer);
            } else {
                setHearts(killer, max());
                if (cfg().getBoolean("modules.lifesteal.drop-heart-at-max", true)) {
                    Items.give(killer, heartItem(1));
                    plugin.msg(killer, "<gray>You are at max hearts - you received a heart item instead.");
                }
            }
        }
    }

    private boolean sameIp(Player a, Player b) {
        if (a.getAddress() == null || b.getAddress() == null) return false;
        return a.getAddress().getAddress().equals(b.getAddress().getAddress());
    }

    private void eliminate(Player p) {
        p.getPersistentDataContainer().set(elimKey, PersistentDataType.BYTE, (byte) 1);
        plugin.msg(p, "<red>You ran out of hearts!");
        if ("BAN".equalsIgnoreCase(cfg().getString("modules.lifesteal.elimination"))) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + p.getName() + " Out of hearts"));
        }
    }

    public void revive(Player p) {
        p.getPersistentDataContainer().remove(elimKey);
        setHearts(p, start());
        p.setGameMode(GameMode.SURVIVAL);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!isHeartItem(hand)) return;
        e.setUseItemInHand(Event.Result.DENY);
        if (!plugin.enabled("lifesteal")) return;
        if (hearts(p) >= max()) {
            plugin.msg(p, "<red>You are already at max hearts.");
            Fx.error(p);
            return;
        }
        if (hand.getAmount() > 1) { hand.setAmount(hand.getAmount() - 1); p.getInventory().setItemInMainHand(hand); }
        else p.getInventory().setItemInMainHand(null);          // consume first, grant second
        setHearts(p, hearts(p) + 1);
        Fx.success(p);
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (isHeartItem(it)) { e.getInventory().setResult(null); return; }
        }
    }

    public void withdraw(Player p, int amount) {
        if (!plugin.enabled("lifesteal") || !cfg().getBoolean("modules.lifesteal.heart-item", true)) {
            plugin.msg(p, "<red>Heart withdrawing is disabled.");
            return;
        }
        int have = hearts(p);
        if (amount < 1 || have - amount < 1) {
            plugin.msg(p, "<red>You must keep at least 1 heart.");
            Fx.error(p);
            return;
        }
        setHearts(p, have - amount);                            // take first, give second
        Items.give(p, heartItem(amount));
        Fx.success(p);
        plugin.msg(p, "<gray>You withdrew <red>" + amount + " <gray>heart(s).");
    }
}
