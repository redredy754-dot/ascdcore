package dev.ascendant.core.gui;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Fx;
import dev.ascendant.core.util.Items;
import dev.ascendant.core.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.function.Supplier;

/** All potion types in alphabetical order. Left click = ban, right click = unban. */
public final class PotionMenu extends PagedMenu<PotionType> {

    public PotionMenu(AscendantCore plugin, Player viewer, List<PotionType> types, Supplier<Menu> back) {
        super(plugin, viewer, "<white><bold>Potion Limiter</bold></white>", types, back);
    }

    @Override
    protected ItemStack icon(PotionType t) {
        boolean banned = plugin.potions().isBanned(t);
        ItemStack it = Items.of(Material.POTION, (banned ? "<white><bold>" : "<white><bold>") + Text.pretty(t.getKey().getKey()),
                "<gray>Status: " + (banned ? "<white>BANNED" : "<white>allowed"), "",
                "<white>Left <gray>» ban  <white>Right <gray>» unban");
        PotionMeta pm = (PotionMeta) it.getItemMeta();
        pm.setBasePotionType(t);
        pm.addItemFlags(ItemFlag.values());
        it.setItemMeta(pm);
        return Items.glow(it, banned);
    }

    @Override
    protected void onEntryClick(PotionType t, ClickType click) {
        if (left(click)) { plugin.potions().set(t, true); Fx.off(viewer); }
        else { plugin.potions().set(t, false); Fx.on(viewer); }
    }

    @Override
    protected String footer() { return "<gray>Left: ban  Right: unban"; }
}
