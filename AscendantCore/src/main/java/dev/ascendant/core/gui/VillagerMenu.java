package dev.ascendant.core.gui;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.modules.VillagerModule;
import dev.ascendant.core.util.Fx;
import dev.ascendant.core.util.Items;
import dev.ascendant.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Menu of a claimed villager: biome, trading open/closed, partners, anchor, reset trades, pick up, unclaim. */
public final class VillagerMenu extends Menu {
    private final VillagerModule villagers;
    private final Villager villager;

    public VillagerMenu(AscendantCore plugin, Player viewer, VillagerModule villagers, Villager villager) {
        super(plugin, viewer, 3, "<white><bold>Villager");
        this.villagers = villagers;
        this.villager = villager;
    }

    private boolean ok() {
        if (!villager.isValid() || !villagers.mayManage(viewer, villager)) {
            viewer.closeInventory();
            Fx.error(viewer);
            return false;
        }
        return true;
    }

    @Override
    protected void build() {
        background();
        boolean closed = villagers.isClosed(villager);
        List<String> names = new ArrayList<>();
        for (UUID id : villagers.partners(villager)) {
            String n = Bukkit.getOfflinePlayer(id).getName();
            names.add("<gray>- <white>" + (n == null ? id.toString().substring(0, 8) : n));
        }
        String type = villager.getVillagerType().getKey().getKey();
        set(10, Items.of(Material.GRASS_BLOCK, "<white><bold>Biome", "<gray>Current: <white>" + Text.pretty(type), "",
                "<white>Left <gray>» next  <white>Right <gray>» previous"), t -> {
            if (!ok()) return;
            villagers.cycleBiome(villager, right(t) ? -1 : 1);
        });
        set(11, Items.glow(Items.of(closed ? Material.BARRIER : Material.EMERALD, "<white><bold>Trading",
                "<gray>Status: " + (closed ? "<dark_gray><bold>CLOSED <gray>(owner + partners only)" : "<white><bold>OPEN <gray>(everyone)"), "",
                "<white>Click <gray>» toggle"), !closed), t -> {
            if (!ok()) return;
            villagers.setClosed(villager, !closed);
            if (closed) Fx.on(viewer); else Fx.off(viewer);
        });
        List<String> partnerLore = new ArrayList<>();
        partnerLore.add("<gray>Players allowed to trade when closed:");
        partnerLore.addAll(names.isEmpty() ? List.of("<dark_gray>none") : names);
        partnerLore.add("");
        partnerLore.add("<white>Left <gray>» add partner  <white>Right <gray>» clear all");
        set(12, Items.of(Material.PLAYER_HEAD, "<white><bold>Trading Partners", partnerLore.toArray(new String[0])), t -> {
            if (!ok()) return;
            if (right(t)) { villagers.clearPartners(villager); Fx.off(viewer); return; }
            plugin.prompts().ask(viewer, "Partner name", "Player name", text -> {
                OfflinePlayer op = Bukkit.getPlayerExact(text);
                if (op == null) op = Bukkit.getOfflinePlayerIfCached(text);
                if (op == null) { plugin.msg(viewer, "<red>Unknown player."); Fx.error(viewer); }
                else if (villager.isValid()) { villagers.addPartner(villager, op.getUniqueId()); Fx.success(viewer); }
                open();
            }, this::open);
        });
        boolean anchored = villagers.isAnchored(villager);
        set(13, Items.glow(Items.of(Material.SHEARS, "<white><bold>Anchor", "<gray>Status: " + Text.state(anchored),
                "<gray>Anchored villagers don't walk on their own", "", "<white>Click <gray>» toggle"), anchored), t -> {
            if (!ok()) return;
            villagers.setAnchored(villager, !anchored);
            if (!anchored) Fx.on(viewer); else Fx.off(viewer);
        });
        set(14, Items.of(Material.EMERALD_BLOCK, "<white><bold>Reset Trades", "<gray>Gives the villager fresh trades", "", "<white>Click <gray>» reset"), t -> {
            if (!ok()) return;
            villager.resetOffers();
            Fx.success(viewer);
        });
        set(15, Items.of(Material.VILLAGER_SPAWN_EGG, "<white><bold>Pick Up", "<gray>Turns the villager into an item", "", "<white>Click <gray>» pick up"), t -> {
            if (!ok()) return;
            viewer.closeInventory();
            villagers.pickUp(viewer, villager);
        });
        set(16, Items.of(Material.IRON_DOOR, "<white><bold>Unclaim", "<gray>Removes owner, partners and protection", "", "<white>Click <gray>» unclaim"), t -> {
            if (!ok()) return;
            villagers.unclaim(villager);
            viewer.closeInventory();
            Fx.off(viewer);
        });
    }
}
