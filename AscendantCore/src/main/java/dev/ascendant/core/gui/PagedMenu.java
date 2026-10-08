package dev.ascendant.core.gui;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Fx;
import dev.ascendant.core.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Supplier;

/** 45 entries per page, navigation bar on the bottom row. */
public abstract class PagedMenu<T> extends Menu {
    private static final int PER_PAGE = 45;
    private final List<T> entries;
    private final Supplier<Menu> back;
    private int page;

    protected PagedMenu(AscendantCore plugin, Player viewer, String title, List<T> entries, Supplier<Menu> back) {
        super(plugin, viewer, 6, title);
        this.entries = entries;
        this.back = back;
    }

    protected abstract ItemStack icon(T entry);

    protected abstract void onEntryClick(T entry, ClickType type);

    /** Extra line shown on the info item of the bottom bar. */
    protected String footer() { return "<gray>Pick an entry"; }

    @Override
    protected final void build() {
        int pages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
        if (page >= pages) page = pages - 1;
        fillAll(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= entries.size()) break;
            T entry = entries.get(idx);
            set(i, icon(entry), t -> onEntryClick(entry, t));
        }
        for (int i = 45; i < 54; i++) {
            set(i, Items.pane(i % 2 == 0 ? Material.WHITE_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE));
        }
        if (page > 0) set(48, Items.of(Material.SPECTRAL_ARROW, "<white><bold>‹ Previous page"), t -> { page--; Fx.page(viewer); });
        if (page < pages - 1) set(50, Items.of(Material.SPECTRAL_ARROW, "<white><bold>Next page ›"), t -> { page++; Fx.page(viewer); });
        set(53, Items.of(Material.PAPER, "<white>Page <white>" + (page + 1) + "<gray>/<white>" + pages, footer()));
        backButton(49, back);
    }
}
