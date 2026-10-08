package dev.ascendant.core;

import dev.ascendant.core.gui.MainMenu;
import dev.ascendant.core.gui.Menu;
import dev.ascendant.core.gui.MenuListener;
import dev.ascendant.core.gui.PromptManager;
import dev.ascendant.core.modules.*;
import dev.ascendant.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class AscendantCore extends JavaPlugin {
    private PromptManager prompts;
    private BanItemsModule banItems;
    private PotionModule potions;
    private CleanerModule cleaner;
    private LifestealModule lifesteal;
    private RestarterModule restarter;
    private OptimizationModule optimization;
    private CombatTagModule combat;
    private StackerModule stacker;
    private CraftingModule crafting;
    private CooldownModule cooldown;
    private PotTimerModule potTimer;
    private TagModule tag;
    private RitualModule ritual;
    private DeathBanModule deathBan;
    private dev.ascendant.core.util.Heads heads;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        heads = new dev.ascendant.core.util.Heads(this);
        heads.warmUp();
        prompts = new PromptManager(this);
        banItems = new BanItemsModule(this);
        potions = new PotionModule(this);
        cleaner = new CleanerModule(this);
        lifesteal = new LifestealModule(this);
        restarter = new RestarterModule(this);
        optimization = new OptimizationModule(this);
        combat = new CombatTagModule(this);
        stacker = new StackerModule(this);
        crafting = new CraftingModule(this);
        cooldown = new CooldownModule(this);
        potTimer = new PotTimerModule(this);
        tag = new TagModule(this);
        ritual = new RitualModule(this);
        deathBan = new DeathBanModule(this);

        PluginManager pm = getServer().getPluginManager();
        Listener[] listeners = {
                new MenuListener(), prompts, new CombatModule(this), new SleepModule(this), new CobwebModule(this),
                new VillagerModule(this), banItems, new EnchantModule(this), potions, lifesteal, new ClumpModule(this),
                stacker, new ExplosionModule(this), optimization, restarter, new AfkModule(this), new AntiCrystalModule(this),
                new AntiMinimapModule(this), combat, cooldown, deathBan, new DimensionModule(this), new EnderModule(this),
                new NakedModule(this), new CombatExtrasModule(this), potTimer, ritual, new SpeedModule(this), tag
        };
        for (Listener l : listeners) pm.registerEvents(l, this);

        Commands commands = new Commands(this);
        for (String name : new String[]{"ascendantcore", "string", "withdrawheart", "ascdrestart"}) {
            var cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(commands);
                cmd.setTabCompleter(commands);
            }
        }
        // /ascendantcore:gui and /ascdcore:gui
        getServer().getCommandMap().register("ascendantcore", new GuiCommand("ascendantcore:gui", this));
        getServer().getCommandMap().register("ascdcore", new GuiCommand("ascdcore:gui", this));

        changed();
        getLogger().info("AscendantCore enabled.");
    }

    @Override
    public void onDisable() {
        if (prompts != null) prompts.closeAll();
        for (Player p : Bukkit.getOnlinePlayers()) {
            var holder = p.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof Menu || holder instanceof EnderModule.Holder) p.closeInventory();   // returns / saves items
        }
        if (ritual != null) ritual.finishAll();
        if (tag != null) tag.clearAll();
        if (optimization != null) optimization.restore();
    }

    /** Called whenever any config value or toggle changes: re-applies the modules that run on timers or world settings. */
    public void changed() {
        if (banItems != null) banItems.reload();
        if (potions != null) potions.reload();
        if (cleaner != null) cleaner.reload();
        if (restarter != null) restarter.reload();
        if (optimization != null) optimization.reload();
        if (stacker != null) stacker.reload();
        if (cooldown != null) cooldown.reload();
        if (potTimer != null) potTimer.reload();
        if (crafting != null) crafting.reload();
        if (tag != null) tag.reload();
    }

    public boolean enabled(String key) { return getConfig().getBoolean("modules." + key + ".enabled"); }

    public void setEnabled(String key, boolean value) {
        getConfig().set("modules." + key + ".enabled", value);
        saveConfig();
        changed();
    }

    public void msg(CommandSender to, String miniMessage) {
        to.sendMessage(Text.mm(getConfig().getString("messages.prefix", "") + miniMessage));
    }

    public PromptManager prompts() { return prompts; }
    public BanItemsModule banItems() { return banItems; }
    public PotionModule potions() { return potions; }
    public LifestealModule lifesteal() { return lifesteal; }
    public RestarterModule restarter() { return restarter; }
    public OptimizationModule optimization() { return optimization; }
    public CombatTagModule combat() { return combat; }
    public StackerModule stacker() { return stacker; }
    public DeathBanModule deathBan() { return deathBan; }
    public dev.ascendant.core.util.Heads heads() { return heads; }

    private static final class GuiCommand extends Command {
        private final AscendantCore plugin;

        GuiCommand(String name, AscendantCore plugin) {
            super(name);
            this.plugin = plugin;
            setDescription("Open the AscendantCore control panel");
            setPermission("ascendantcore.admin");
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!(sender instanceof Player p)) {
                plugin.msg(sender, "<red>Only players can open the GUI.");
                return true;
            }
            if (!testPermission(sender)) return true;
            new MainMenu(plugin, p).open();
            return true;
        }
    }
}
