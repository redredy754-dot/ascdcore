package dev.ascendant.core.gui;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Fx;
import dev.ascendant.core.util.Items;
import dev.ascendant.core.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class MaceMenu extends Menu {
    private static final String[] MACE_ENCHANTS = {"density", "wind_burst", "breach", "fire_aspect", "unbreaking", "mending"};

    public MaceMenu(AscendantCore plugin, Player viewer) {
        super(plugin, viewer, 3, "<white><bold>Mace");
    }

    @Override
    protected void build() {
        background();
        boolean ench = plugin.enabled("mace.enchant-limiter");
        set(10, Items.glow(Items.of(Material.ENCHANTED_BOOK, "<white><bold>Mace Enchant Limiter",
                "<gray>Caps density, wind burst, breach,", "<gray>fire aspect, unbreaking and mending", "",
                "<gray>Status: " + Text.state(ench), "<white>Left <gray>» toggle  <white>Right <gray>» levels"), ench), t -> {
            if (left(t)) {
                plugin.setEnabled("mace.enchant-limiter", !ench);
                if (!ench) Fx.on(viewer); else Fx.off(viewer);
            } else {
                List<Enchantment> list = new ArrayList<>();
                for (String key : MACE_ENCHANTS) {
                    Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
                    if (e != null) list.add(e);
                }
                new LimitMenu(plugin, viewer, "<white><bold>Mace Enchants", list,
                        "modules.mace.enchant-limiter.limits", () -> new MaceMenu(plugin, viewer)).open();
            }
        });
        boolean lim = plugin.enabled("mace.limiter");
        set(13, Items.glow(Items.of(Material.MACE, "<white><bold>Mace Limiter",
                "<gray>Max maces that can ever be crafted", "",
                "<gray>Status: " + Text.state(lim),
                "<gray>Limit: <white>" + plugin.getConfig().getInt("modules.mace.limiter.max-maces"),
                "<gray>Crafted so far: <white>" + plugin.getConfig().getInt("modules.mace.limiter.crafted"), "",
                "<white>Left <gray>» toggle  <white>Right <gray>» set limit"), lim), t -> {
            if (left(t)) {
                plugin.setEnabled("mace.limiter", !lim);
                if (!lim) Fx.on(viewer); else Fx.off(viewer);
            } else {
                ConfigMenu.editNumber(plugin, viewer, "Max Maces", "modules.mace.limiter.max-maces", 0, 100000, true,
                        () -> new MaceMenu(plugin, viewer).open());
            }
        });
        boolean cd = plugin.enabled("mace.cooldown");
        set(16, Items.glow(Items.of(Material.CLOCK, "<white><bold>Mace Cooldown",
                "<gray>Time between mace hits", "",
                "<gray>Status: " + Text.state(cd),
                "<gray>Cooldown: <white>" + plugin.getConfig().getInt("modules.mace.cooldown.seconds") + "s", "",
                "<white>Left <gray>» toggle  <white>Right <gray>» set seconds"), cd), t -> {
            if (left(t)) {
                plugin.setEnabled("mace.cooldown", !cd);
                if (!cd) Fx.on(viewer); else Fx.off(viewer);
            } else {
                ConfigMenu.editNumber(plugin, viewer, "Mace Cooldown (seconds)", "modules.mace.cooldown.seconds", 0, 3600, true,
                        () -> new MaceMenu(plugin, viewer).open());
            }
        });
        backButton(22, () -> new MainMenu(plugin, viewer));
    }
}
