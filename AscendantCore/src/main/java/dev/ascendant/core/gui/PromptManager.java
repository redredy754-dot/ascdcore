package dev.ascendant.core.gui;

import dev.ascendant.core.AscendantCore;
import dev.ascendant.core.util.Fx;
import dev.ascendant.core.util.Items;
import dev.ascendant.core.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Anvil text prompt: a paper is shown in the anvil, the player types a value and clicks the result.
 * Every click is cancelled and the anvil is emptied on close, so nothing can be taken out or duplicated.
 */
public final class PromptManager implements Listener {
    private static final class Prompt {
        final Consumer<String> onSubmit;
        final Runnable onCancel;
        boolean done;

        Prompt(Consumer<String> onSubmit, Runnable onCancel) {
            this.onSubmit = onSubmit;
            this.onCancel = onCancel;
        }
    }

    private final AscendantCore plugin;
    private final Map<UUID, Prompt> active = new HashMap<>();

    public PromptManager(AscendantCore plugin) { this.plugin = plugin; }

    public boolean isActive(Player p) { return active.containsKey(p.getUniqueId()); }

    public void ask(Player p, String title, String current, Consumer<String> onSubmit, Runnable onCancel) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            AnvilView view = MenuType.ANVIL.builder().title(Text.mm(title)).checkReachable(false).build(p);
            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta meta = paper.getItemMeta();
            meta.displayName(Component.text(current).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Text.mm("<gray>Type the new value, then click the result.")));
            paper.setItemMeta(meta);
            view.getTopInventory().setItem(0, paper);
            active.put(p.getUniqueId(), new Prompt(onSubmit, onCancel));
            p.openInventory(view);
            Fx.open(p);
        });
    }

    private ItemStack confirm(String typed) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Value: <white>" + (typed.isBlank() ? "-" : typed.replace("<", "").replace(">", "")));
        lore.add("<white>Click to confirm");
        return Items.of(Material.LIME_DYE, "<white><bold>✔ Confirm", lore.toArray(new String[0]));
    }

    @EventHandler
    public void onPrepare(PrepareAnvilEvent e) {
        if (!(e.getView().getPlayer() instanceof Player p) || !active.containsKey(p.getUniqueId())) return;
        String text = e.getView().getRenameText();
        e.setResult(confirm(text == null ? "" : text));
        e.getView().setRepairCost(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Prompt pr = active.get(p.getUniqueId());
        if (pr == null || e.getView().getType() != InventoryType.ANVIL) return;
        e.setCancelled(true);
        if (e.getRawSlot() != 2 || pr.done) return;
        if (!(e.getView() instanceof AnvilView av)) return;
        String text = av.getRenameText();
        if (text == null || text.isBlank()) {
            Fx.error(p);
            return;
        }
        pr.done = true;
        String value = text.trim();
        Fx.success(p);
        p.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> pr.onSubmit.accept(value));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && active.containsKey(p.getUniqueId())
                && e.getView().getType() == InventoryType.ANVIL) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Prompt pr = active.get(p.getUniqueId());
        if (pr == null || e.getView().getType() != InventoryType.ANVIL) return;
        active.remove(p.getUniqueId());
        e.getInventory().clear(); // paper + result never go back to the player
        if (!pr.done) Bukkit.getScheduler().runTask(plugin, pr.onCancel);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { active.remove(e.getPlayer().getUniqueId()); }

    public void closeAll() {
        for (UUID id : new ArrayList<>(active.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.closeInventory();
        }
        active.clear();
    }
}
