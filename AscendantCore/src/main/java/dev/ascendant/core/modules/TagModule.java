package dev.ascendant.core.modules;

import dev.ascendant.core.AscendantCore;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

/** Health below the name tag (HP or hearts, half heart = 0.5) and ping next to the name tag. */
public final class TagModule implements Listener {
    private static final String OBJECTIVE = "ascd_hp";
    private static final String TEAM_PREFIX = "ascdp_";
    private final AscendantCore plugin;
    private BukkitTask task;

    public TagModule(AscendantCore plugin) { this.plugin = plugin; }

    public void reload() {
        boolean hp = plugin.enabled("health-indicator");
        boolean ping = plugin.enabled("ping");
        if (!hp) clearHealth();
        if (!ping) clearPing();
        if (hp || ping) {
            if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::update, 20L, 20L);
        } else if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void clearAll() {
        if (task != null) task.cancel();
        task = null;
        clearHealth();
        clearPing();
    }

    private Scoreboard board() { return Bukkit.getScoreboardManager().getMainScoreboard(); }

    private void update() {
        Scoreboard sb = board();
        boolean hp = plugin.enabled("health-indicator");
        boolean ping = plugin.enabled("ping");
        Objective obj = null;
        if (hp) {
            obj = sb.getObjective(OBJECTIVE);
            if (obj == null) obj = sb.registerNewObjective(OBJECTIVE, Criteria.DUMMY, Component.empty());
            obj.setDisplaySlot(DisplaySlot.BELOW_NAME);
        }
        boolean hearts = "HEARTS".equalsIgnoreCase(plugin.getConfig().getString("modules.health-indicator.mode", "HP"));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (hp) setHealth(obj, p, hearts);
            if (ping) setPing(sb, p);
        }
    }

    private void setHealth(Objective obj, Player p, boolean hearts) {
        double hp = Math.ceil(p.getHealth());
        Score score = obj.getScore(p.getName());
        score.setScore((int) hp);
        String text;
        if (hearts) {
            double h = hp / 2.0;
            text = (h == Math.rint(h) ? String.valueOf((long) h) : String.valueOf(h)) + " \u2764";
        } else {
            text = (int) hp + " HP";
        }
        score.numberFormat(NumberFormat.fixed(Component.text(text, NamedTextColor.RED)));
    }

    private String teamName(Player p) { return TEAM_PREFIX + p.getUniqueId().toString().substring(0, 8); }

    private void setPing(Scoreboard sb, Player p) {
        String name = teamName(p);
        Team existing = sb.getEntryTeam(p.getName());
        if (existing != null && !existing.getName().equals(name)) return;   // never steal someone from another team
        Team t = sb.getTeam(name);
        if (t == null) t = sb.registerNewTeam(name);
        if (existing == null) t.addEntry(p.getName());
        int ping = p.getPing();
        NamedTextColor color = ping < 80 ? NamedTextColor.GREEN : ping < 150 ? NamedTextColor.YELLOW : NamedTextColor.RED;
        t.suffix(Component.text(" " + ping + "ms", color));
    }

    private void clearHealth() {
        Objective o = board().getObjective(OBJECTIVE);
        if (o != null) o.unregister();
    }

    private void clearPing() {
        for (Team t : board().getTeams()) if (t.getName().startsWith(TEAM_PREFIX)) t.unregister();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Team t = board().getTeam(teamName(e.getPlayer()));
        if (t != null) t.unregister();
        Objective o = board().getObjective(OBJECTIVE);
        if (o != null) board().resetScores(e.getPlayer().getName());
    }
}
