package dev.ascendant.core.gui;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Items;
import dev.ascendant.core.util.Text;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Pick one potion type (alphabetical). */
public final class PotionPickMenu extends PagedMenu<PotionType> {
    private final Consumer<PotionType> onPick;
    private final Supplier<Menu> back;

    public static List<PotionType> allSorted() {
        List<PotionType> types = new ArrayList<>();
        Registry.POTION.forEach(types::add);
        types.sort(Comparator.comparing(t -> t.getKey().getKey()));
        return types;
    }

    public PotionPickMenu(AscendantCore plugin, Player viewer, Consumer<PotionType> onPick, Supplier<Menu> back) {
        super(plugin, viewer, "<white><bold>Pick a potion", allSorted(), back);
        this.onPick = onPick;
        this.back = back;
    }

    public static ItemStack potionIcon(PotionType t, String name, String... lore) {
        ItemStack it = Items.of(Material.POTION, name, lore);
        PotionMeta pm = (PotionMeta) it.getItemMeta();
        pm.setBasePotionType(t);
        pm.addItemFlags(ItemFlag.values());
        it.setItemMeta(pm);
        return it;
    }

    @Override
    protected ItemStack icon(PotionType t) {
        return potionIcon(t, "<white><bold>" + Text.pretty(t.getKey().getKey()), "<white>Click <gray>» select");
    }

    @Override
    protected void onEntryClick(PotionType t, ClickType click) {
        onPick.accept(t);
        back.get().open();
    }
}
