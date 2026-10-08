package dev.ascendant.core.modules;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.lang.reflect.Array;
import java.lang.reflect.Method;

/**
 * Optional WorldGuard link done with reflection, so AscendantCore loads fine without WorldGuard installed.
 * A location counts as a safezone when the region flag PVP is set to DENY there.
 */
final class WgHook {
    private final Object query;
    private final Object flags;
    private final Method adapt;
    private final Method getRegions;
    private final Method queryState;

    private WgHook(Object query, Object flags, Method adapt, Method getRegions, Method queryState) {
        this.query = query;
        this.flags = flags;
        this.adapt = adapt;
        this.getRegions = getRegions;
        this.queryState = queryState;
    }

    static WgHook create() {
        try {
            if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) return null;
            Class<?> wgc = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object wg = wgc.getMethod("getInstance").invoke(null);
            Object platform = wgc.getMethod("getPlatform").invoke(wg);
            Object container = Class.forName("com.sk89q.worldguard.internal.platform.WorldGuardPlatform")
                    .getMethod("getRegionContainer").invoke(platform);
            Object query = Class.forName("com.sk89q.worldguard.protection.regions.RegionContainer")
                    .getMethod("createQuery").invoke(container);
            Class<?> queryC = Class.forName("com.sk89q.worldguard.protection.regions.RegionQuery");
            Class<?> weLoc = Class.forName("com.sk89q.worldedit.util.Location");
            Method adapt = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter").getMethod("adapt", Location.class);
            Method getRegions = queryC.getMethod("getApplicableRegions", weLoc);
            Class<?> setC = Class.forName("com.sk89q.worldguard.protection.ApplicableRegionSet");
            Class<?> stateFlag = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
            Class<?> assoc = Class.forName("com.sk89q.worldguard.protection.association.RegionAssociable");
            Method queryState = setC.getMethod("queryState", assoc, Array.newInstance(stateFlag, 0).getClass());
            Object pvp = Class.forName("com.sk89q.worldguard.protection.flags.Flags").getField("PVP").get(null);
            Object arr = Array.newInstance(stateFlag, 1);
            Array.set(arr, 0, pvp);
            return new WgHook(query, arr, adapt, getRegions, queryState);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[AscendantCore] WorldGuard found but the safezone link failed: " + t);
            return null;
        }
    }

    boolean isSafe(Location loc) {
        try {
            Object set = getRegions.invoke(query, adapt.invoke(null, loc));
            Object state = queryState.invoke(set, null, flags);
            return state != null && "DENY".equals(state.toString());
        } catch (Throwable t) {
            return false;
        }
    }
}
