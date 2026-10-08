package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.gui.VillagerMenu;
import dev.ascendant.core.util.Fx;
import dev.ascendant.core.util.Items;
import dev.ascendant.core.util.Text;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.Villager;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/** ClickVillagers (pick up, place back, claim, anchor, partners, biome) + infinite restock. */
public final class VillagerModule implements Listener {
    private static final int HUGE = 1_000_000;
    private final AscendantCore plugin;
    private final NamespacedKey dataKey, ownerKey, partnersKey, closedKey, anchorKey;

    public VillagerModule(AscendantCore plugin) {
        this.plugin = plugin;
        this.dataKey = new NamespacedKey(plugin, "villager_data");
        this.ownerKey = new NamespacedKey(plugin, "v_owner");
        this.partnersKey = new NamespacedKey(plugin, "v_partners");
        this.closedKey = new NamespacedKey(plugin, "v_closed");
        this.anchorKey = new NamespacedKey(plugin, "v_anchor");
    }

    private boolean cfg(String key, boolean def) { return plugin.getConfig().getBoolean("modules.click-villagers." + key, def); }

    // ---------- claim data (stored on the villager itself) ----------

    public UUID owner(Villager v) {
        String s = v.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        try { return s == null ? null : UUID.fromString(s); } catch (IllegalArgumentException ex) { return null; }
    }

    public boolean mayManage(Player p, Villager v) {
        UUID o = owner(v);
        return o == null || o.equals(p.getUniqueId()) || p.hasPermission("ascendantcore.admin");
    }

    public Set<UUID> partners(Villager v) {
        Set<UUID> set = new LinkedHashSet<>();
        String s = v.getPersistentDataContainer().get(partnersKey, PersistentDataType.STRING);
        if (s != null && !s.isEmpty()) for (String part : s.split(",")) {
            try { set.add(UUID.fromString(part)); } catch (IllegalArgumentException ignored) { }
        }
        return set;
    }

    private void savePartners(Villager v, Set<UUID> set) {
        PersistentDataContainer pdc = v.getPersistentDataContainer();
        if (set.isEmpty()) { pdc.remove(partnersKey); return; }
        StringJoiner j = new StringJoiner(",");
        set.forEach(u -> j.add(u.toString()));
        pdc.set(partnersKey, PersistentDataType.STRING, j.toString());
    }

    public void addPartner(Villager v, UUID id) { Set<UUID> s = partners(v); s.add(id); savePartners(v, s); }
    public void clearPartners(Villager v) { v.getPersistentDataContainer().remove(partnersKey); }
    public boolean isClosed(Villager v) { return v.getPersistentDataContainer().has(closedKey, PersistentDataType.BYTE); }

    public void setClosed(Villager v, boolean closed) {
        if (closed) v.getPersistentDataContainer().set(closedKey, PersistentDataType.BYTE, (byte) 1);
        else v.getPersistentDataContainer().remove(closedKey);
    }

    public boolean isAnchored(Villager v) { return v.getPersistentDataContainer().has(anchorKey, PersistentDataType.BYTE); }

    public void setAnchored(Villager v, boolean on) {
        if (on) v.getPersistentDataContainer().set(anchorKey, PersistentDataType.BYTE, (byte) 1);
        else v.getPersistentDataContainer().remove(anchorKey);
        v.setAware(!on);
    }

    public void unclaim(Villager v) {
        PersistentDataContainer pdc = v.getPersistentDataContainer();
        pdc.remove(ownerKey);
        pdc.remove(partnersKey);
        pdc.remove(closedKey);
    }

    public void cycleBiome(Villager v, int dir) {
        List<Villager.Type> types = new ArrayList<>();
        Registry.VILLAGER_TYPE.forEach(types::add);
        types.sort(Comparator.comparing(t -> t.getKey().getKey()));
        int i = Math.max(0, types.indexOf(v.getVillagerType()));
        v.setVillagerType(types.get((i + dir + types.size()) % types.size()));
    }

    private boolean mayTrade(Player p, Villager v) {
        UUID o = owner(v);
        return o == null || !isClosed(v) || o.equals(p.getUniqueId()) || partners(v).contains(p.getUniqueId())
                || p.hasPermission("ascendantcore.admin");
    }

    // ---------- eggs ----------

    private boolean isOurEgg(ItemStack it) {
        if (it == null || it.getType() != Material.PLAYER_HEAD || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(dataKey, PersistentDataType.STRING);
    }

    public void pickUp(Player p, Villager v) {
        if (!v.isValid() || v.isDead()) return;
        ItemStack egg = toEgg(v);
        v.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, v.getLocation().add(0, 1, 0), 12, 0.3, 0.4, 0.3);
        v.remove();                  // removed first, item given second: never both
        Items.give(p, egg);
        Fx.success(p);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.6f, 1.2f);
        p.sendActionBar(Text.mm("<white>Villager picked up."));
    }

    private ItemStack toEgg(Villager v) {
        List<String> lore = new ArrayList<>();
        String prof = v.getProfession().getKey().getKey();
        lore.add("<gray>Profession: <white>" + Text.pretty(prof) + " <dark_gray>(level " + v.getVillagerLevel() + ")");
        UUID o = owner(v);
        if (o != null) {
            String n = Bukkit.getOfflinePlayer(o).getName();
            lore.add("<gray>Owner: <white>" + (n == null ? "unknown" : n));
        }
        lore.add("<gray>Anchored: " + Text.state(isAnchored(v)) + "  <gray>Trading: " + (isClosed(v) ? "<dark_gray>closed" : "<white>open"));
        lore.add("");
        lore.addAll(tradeLines(v, prof));
        lore.add("");
        lore.add("<white>Place the head <gray>» villager comes back");
        ItemStack egg = Items.of(Material.PLAYER_HEAD, "<white><bold>Villager Head", lore.toArray(new String[0]));
        org.bukkit.inventory.meta.SkullMeta skull = (org.bukkit.inventory.meta.SkullMeta) egg.getItemMeta();
        skull.setPlayerProfile(plugin.heads().villagerProfile());
        egg.setItemMeta(skull);
        ItemMeta meta = egg.getItemMeta();
        meta.getPersistentDataContainer().set(dataKey, PersistentDataType.STRING, serialize(v));
        egg.setItemMeta(meta);
        return egg;
    }

    /** Only the trades that matter for the profession: enchanted books for librarians, enchanted gear for smiths. */
    private List<String> tradeLines(Villager v, String prof) {
        List<String> out = new ArrayList<>();
        List<MerchantRecipe> relevant = new ArrayList<>();
        for (MerchantRecipe r : v.getRecipes()) {
            ItemMeta m = r.getResult().getItemMeta();
            boolean book = m instanceof EnchantmentStorageMeta sm && !sm.getStoredEnchants().isEmpty();
            boolean gear = m != null && m.hasEnchants();
            if (prof.equals("librarian") ? book : (book || gear)) relevant.add(r);
        }
        if (relevant.isEmpty() && !prof.equals("librarian")) {
            for (MerchantRecipe r : v.getRecipes()) { if (relevant.size() < 4) relevant.add(r); }
        }
        for (MerchantRecipe r : relevant) {
            if (out.size() >= 8) { out.add("<dark_gray>…"); break; }
            ItemStack res = r.getResult();
            StringBuilder name = new StringBuilder();
            ItemMeta m = res.getItemMeta();
            Map<org.bukkit.enchantments.Enchantment, Integer> ench = m instanceof EnchantmentStorageMeta sm ? sm.getStoredEnchants()
                    : (m != null ? m.getEnchants() : Map.of());
            if (!ench.isEmpty()) {
                ench.forEach((en, lvl) -> name.append(Text.pretty(en.getKey().getKey())).append(' ').append(lvl).append(' '));
                lore(out, "<light_purple>✦ <white>" + name.toString().trim() + " <gray>« " + cost(r), r);
            } else {
                lore(out, "<white>" + res.getAmount() + "x " + Text.pretty(res.getType().name()) + " <gray>« " + cost(r), r);
            }
        }
        return out;
    }

    private void lore(List<String> out, String line, MerchantRecipe r) {
        out.add(r.getUses() >= r.getMaxUses() ? line + " <dark_gray>(sold out)" : line);
    }

    private String cost(MerchantRecipe r) {
        StringJoiner j = new StringJoiner(" + ");
        for (ItemStack in : r.getIngredients()) {
            if (in == null || in.getType().isAir()) continue;
            j.add("<green>" + in.getAmount() + " " + (in.getType() == Material.EMERALD ? "◆ emerald" : Text.pretty(in.getType().name())) + "<gray>");
        }
        return j.toString();
    }

    private String serialize(Villager v) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("profession", v.getProfession().getKey().toString());
        y.set("type", v.getVillagerType().getKey().toString());
        y.set("level", v.getVillagerLevel());
        y.set("xp", v.getVillagerExperience());
        y.set("baby", !v.isAdult());
        y.set("anchored", isAnchored(v));
        y.set("closed", isClosed(v));
        UUID o = owner(v);
        if (o != null) y.set("owner", o.toString());
        y.set("partners", new ArrayList<>(partners(v).stream().map(UUID::toString).toList()));
        if (v.customName() != null) y.set("name", LegacyComponentSerializer.legacySection().serialize(v.customName()));
        List<MerchantRecipe> recipes = v.getRecipes();
        for (int i = 0; i < recipes.size(); i++) {
            MerchantRecipe r = recipes.get(i);
            String p = "recipes." + i + ".";
            y.set(p + "result", r.getResult());               // keeps enchantments and all item data
            y.set(p + "ingredients", r.getIngredients());
            y.set(p + "uses", r.getUses());
            y.set(p + "max-uses", r.getMaxUses());
            y.set(p + "xp-reward", r.hasExperienceReward());
            y.set(p + "villager-xp", r.getVillagerExperience());
            y.set(p + "multiplier", (double) r.getPriceMultiplier());
            y.set(p + "demand", r.getDemand());
            y.set(p + "special", r.getSpecialPrice());
        }
        return y.saveToString();
    }

    private void apply(Villager v, String data) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(data);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not read stored villager data: " + ex.getMessage());
            return;
        }
        NamespacedKey prof = NamespacedKey.fromString(y.getString("profession", "minecraft:none"));
        NamespacedKey type = NamespacedKey.fromString(y.getString("type", "minecraft:plains"));
        if (type != null && Registry.VILLAGER_TYPE.get(type) != null) v.setVillagerType(Registry.VILLAGER_TYPE.get(type));
        if (prof != null && Registry.VILLAGER_PROFESSION.get(prof) != null) v.setProfession(Registry.VILLAGER_PROFESSION.get(prof));
        v.setVillagerLevel(Math.max(1, Math.min(5, y.getInt("level", 1))));
        v.setVillagerExperience(Math.max(1, y.getInt("xp", 0)));   // xp 0 + no workstation makes villagers drop their job
        if (y.getBoolean("baby")) v.setBaby(); else v.setAdult();
        String name = y.getString("name");
        if (name != null) v.customName(LegacyComponentSerializer.legacySection().deserialize(name));
        PersistentDataContainer pdc = v.getPersistentDataContainer();
        if (y.getString("owner") != null) pdc.set(ownerKey, PersistentDataType.STRING, y.getString("owner"));
        List<String> ps = y.getStringList("partners");
        if (!ps.isEmpty()) pdc.set(partnersKey, PersistentDataType.STRING, String.join(",", ps));
        if (y.getBoolean("closed")) pdc.set(closedKey, PersistentDataType.BYTE, (byte) 1);
        if (y.getBoolean("anchored")) setAnchored(v, true);
        ConfigurationSection rs = y.getConfigurationSection("recipes");
        if (rs == null) return;
        List<MerchantRecipe> list = new ArrayList<>();
        for (String k : rs.getKeys(false)) {
            String p = "recipes." + k + ".";
            ItemStack result = y.getItemStack(p + "result");
            if (result == null) continue;
            MerchantRecipe r = new MerchantRecipe(result, y.getInt(p + "uses"), Math.max(1, y.getInt(p + "max-uses", 12)),
                    y.getBoolean(p + "xp-reward", true), y.getInt(p + "villager-xp"), (float) y.getDouble(p + "multiplier"),
                    y.getInt(p + "demand"), y.getInt(p + "special"));
            List<ItemStack> ing = new ArrayList<>();
            List<?> raw = y.getList(p + "ingredients");
            if (raw != null) for (Object o : raw) if (o instanceof ItemStack is) ing.add(is);
            r.setIngredients(ing);
            list.add(r);
        }
        v.setRecipes(list);
    }

    /** Villagers without a workstation and without xp lose their profession - re-apply if that ever happens. */
    private void keepJob(Villager v, String data) {
        for (long delay : new long[]{3L, 60L}) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (v.isValid() && !jobOk(v, data)) apply(v, data);
            }, delay);
        }
    }

    private boolean jobOk(Villager v, String data) {
        YamlConfiguration y = new YamlConfiguration();
        try { y.loadFromString(data); } catch (Exception ex) { return true; }
        String want = y.getString("profession");
        return want == null || v.getProfession().getKey().toString().equals(want);
    }

    private Villager spawnFrom(Location loc, String data) {
        Villager v = loc.getWorld().spawn(loc, Villager.class, vil -> apply(vil, data));
        keepJob(v, data);
        if (v.isValid()) {
            v.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, v.getLocation().add(0, 1, 0), 12, 0.3, 0.4, 0.3);
            v.getWorld().playSound(v.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, 0.6f, 1.2f);
        }
        return v;
    }

    private void consumeOne(Player p, EquipmentSlot hand) {
        if (p.getGameMode() == GameMode.CREATIVE) return;
        ItemStack s = p.getInventory().getItem(hand);
        if (s == null) return;
        if (s.getAmount() > 1) { s.setAmount(s.getAmount() - 1); p.getInventory().setItem(hand, s); }
        else p.getInventory().setItem(hand, null);
    }

    // ---------- events ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClickEntity(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack held = p.getInventory().getItemInMainHand();
        Entity clicked = e.getRightClicked();

        if (isOurEgg(held)) {                                   // never let the vanilla egg act on anything
            e.setCancelled(true);
            if (clicked instanceof Vehicle veh) {
                String data = held.getItemMeta().getPersistentDataContainer().get(dataKey, PersistentDataType.STRING);
                Villager v = spawnFrom(veh.getLocation(), data);
                if (v.isValid()) { veh.addPassenger(v); consumeOne(p, EquipmentSlot.HAND); Fx.success(p); }
            }
            return;
        }
        if (!(clicked instanceof Villager v)) return;

        if (!p.isSneaking()) {
            if (!mayTrade(p, v)) {
                e.setCancelled(true);
                p.sendActionBar(Text.mm("<red>This villager's trading is closed."));
            }
            return;
        }
        if (!plugin.enabled("click-villagers") || !p.hasPermission("ascendantcore.clickvillagers")) return;
        Material h = held.getType();

        if (h == Material.SHEARS) {
            if (!cfg("allow-anchor", true)) return;
            e.setCancelled(true);
            if (!mayManage(p, v)) { deny(p); return; }
            boolean now = !isAnchored(v);
            setAnchored(v, now);
            p.sendActionBar(Text.mm(now ? "<white>Villager anchored." : "<white>Villager released."));
            if (now) Fx.on(p); else Fx.off(p);
            return;
        }
        if (h.name().endsWith("_SHOVEL")) {
            if (!cfg("allow-claim", true)) return;
            e.setCancelled(true);
            UUID o = owner(v);
            if (o == null) {
                v.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, p.getUniqueId().toString());
                p.sendActionBar(Text.mm("<white>Villager claimed. Shift + right-click it to manage."));
                Fx.success(p);
            } else if (mayManage(p, v)) new VillagerMenu(plugin, p, this, v).open();
            else deny(p);
            return;
        }
        if (!h.isAir()) return;
        e.setCancelled(true);
        UUID o = owner(v);
        if (o != null) {
            if (mayManage(p, v)) new VillagerMenu(plugin, p, this, v).open(); else deny(p);
            return;
        }
        pickUp(p, v);
    }

    private void deny(Player p) {
        p.sendActionBar(Text.mm("<red>This villager belongs to someone else."));
        Fx.error(p);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlaceEgg(PlayerInteractEvent e) {
        if (e.getHand() == null || !isOurEgg(e.getItem())) return;
        Player p = e.getPlayer();
        if (e.getAction() == Action.RIGHT_CLICK_AIR) { e.setCancelled(true); return; }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;
        if (clicked.getType().isInteractable() && !p.isSneaking()) {
            e.setUseItemInHand(Event.Result.DENY);
            return;
        }
        e.setCancelled(true);
        ItemStack hand = p.getInventory().getItem(e.getHand());
        if (!isOurEgg(hand)) return;
        String data = hand.getItemMeta().getPersistentDataContainer().get(dataKey, PersistentDataType.STRING);
        BlockFace face = e.getBlockFace();
        Location loc = clicked.getRelative(face).getLocation().add(0.5, 0, 0.5);
        if (!loc.getBlock().isPassable()) return;
        if (spawnFrom(loc, data).isValid()) {
            consumeOne(p, e.getHand());
            Fx.success(p);
        }
    }

    /** Dispensers place villagers back: the egg is removed from the dispenser first, then the villager spawns. */
    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) {
        if (!isOurEgg(e.getItem())) return;
        e.setCancelled(true);
        Block b = e.getBlock();
        if (!(b.getBlockData() instanceof Directional d)) return;
        ItemStack proto = e.getItem().clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!(b.getState() instanceof Dispenser disp)) return;
            Inventory inv = disp.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack it = inv.getItem(i);
                if (!isOurEgg(it) || !it.isSimilar(proto)) continue;
                Location loc = b.getRelative(d.getFacing()).getLocation().add(0.5, 0, 0.5);
                if (!loc.getBlock().isPassable()) return;
                String data = it.getItemMeta().getPersistentDataContainer().get(dataKey, PersistentDataType.STRING);
                if (it.getAmount() > 1) { it.setAmount(it.getAmount() - 1); inv.setItem(i, it); } else inv.setItem(i, null);
                spawnFrom(loc, data);
                return;
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Villager v) || owner(v) == null || !plugin.enabled("click-villagers")) return;
        if (!cfg("claimed-invulnerable", true)) return;
        EntityDamageEvent.DamageCause c = e.getCause();
        if (c == EntityDamageEvent.DamageCause.VOID || c == EntityDamageEvent.DamageCause.KILL) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) if (en instanceof Villager v && isAnchored(v)) v.setAware(false);
    }

    // ---------- infinite restock ----------

    private void refill(Villager v) {
        List<MerchantRecipe> list = new ArrayList<>(v.getRecipes());
        for (MerchantRecipe r : list) {
            r.setMaxUses(HUGE);
            r.setUses(0);
            r.setDemand(0);
        }
        v.setRecipes(list);
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        if (!plugin.enabled("infinite-restock")) return;
        if (e.getInventory() instanceof MerchantInventory mi && mi.getMerchant() instanceof Villager v) refill(v);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrade(InventoryClickEvent e) {
        if (!plugin.enabled("infinite-restock")) return;
        if (e.getInventory() instanceof MerchantInventory mi && e.getRawSlot() == 2 && mi.getMerchant() instanceof Villager v) {
            Bukkit.getScheduler().runTask(plugin, () -> { if (v.isValid()) refill(v); });
        }
    }
}
