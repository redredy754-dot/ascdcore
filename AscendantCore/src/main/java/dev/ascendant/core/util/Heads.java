package dev.ascendant.core.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.ascendant.core.AscendantCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Player-head items. Player heads copy the skin the server currently shows for that player, so cracked servers
 * that use SkinsRestorer get the restored skin on their head drops. The villager head uses a texture that is either
 * set in config.yml (modules.click-villagers.head-texture) or looked up once from Mojang (MHF_Villager).
 */
public final class Heads {
    private static final UUID VILLAGER_ID = UUID.nameUUIDFromBytes("ascendantcore:villager-head".getBytes(StandardCharsets.UTF_8));
    private final AscendantCore plugin;
    private volatile ProfileProperty villagerTexture;

    public Heads(AscendantCore plugin) { this.plugin = plugin; }

    /** Looks up the villager texture in the background (needs internet once per start; failure is harmless). */
    public void warmUp() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PlayerProfile profile = Bukkit.createProfile("MHF_Villager");
                profile.complete(true);
                for (ProfileProperty pp : profile.getProperties()) {
                    if ("textures".equals(pp.getName())) { villagerTexture = pp; break; }
                }
            } catch (Exception ignored) { }
        });
    }

    public PlayerProfile villagerProfile() {
        String custom = plugin.getConfig().getString("modules.click-villagers.head-texture", "");
        ProfileProperty prop = custom != null && !custom.isBlank() ? new ProfileProperty("textures", custom.trim()) : villagerTexture;
        if (prop == null) return Bukkit.createProfile("MHF_Villager");   // the game resolves the skin by name
        PlayerProfile profile = Bukkit.createProfile(VILLAGER_ID, "Villager");
        profile.setProperty(prop);
        return profile;
    }

    /** A head with the player's CURRENT skin (SkinsRestorer-restored skins included). */
    public ItemStack playerHead(Player p) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        PlayerProfile profile = Bukkit.createProfile(p.getUniqueId(), p.getName());
        boolean textured = false;
        for (ProfileProperty pp : p.getPlayerProfile().getProperties()) {
            if ("textures".equals(pp.getName())) { profile.setProperty(pp); textured = true; }
        }
        if (!textured) {
            ProfileProperty restored = fromSkinsRestorer(p);
            if (restored != null) profile.setProperty(restored);
        }
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }

    /** Optional: asks SkinsRestorer (if installed) for the skin when the live profile has none. */
    private ProfileProperty fromSkinsRestorer(Player p) {
        try {
            Object api = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider").getMethod("get").invoke(null);
            Object storage = Class.forName("net.skinsrestorer.api.SkinsRestorer").getMethod("getPlayerStorage").invoke(api);
            Object result = Class.forName("net.skinsrestorer.api.storage.PlayerStorage")
                    .getMethod("getSkinForPlayer", UUID.class, String.class).invoke(storage, p.getUniqueId(), p.getName());
            if (result instanceof Optional<?> opt && opt.isPresent()) {
                Class<?> sp = Class.forName("net.skinsrestorer.api.property.SkinProperty");
                String value = (String) sp.getMethod("getValue").invoke(opt.get());
                String sig = (String) sp.getMethod("getSignature").invoke(opt.get());
                return new ProfileProperty("textures", value, sig);
            }
        } catch (Throwable ignored) { }
        return null;
    }
}
