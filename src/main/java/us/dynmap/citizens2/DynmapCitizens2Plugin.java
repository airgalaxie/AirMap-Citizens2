package us.dynmap.citizens2;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.CitizensPlugin;
import net.citizensnpcs.api.event.NPCCreateEvent;
import net.citizensnpcs.api.event.NPCDeathEvent;
import net.citizensnpcs.api.event.NPCDespawnEvent;
import net.citizensnpcs.api.event.NPCRemoveEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;

public class DynmapCitizens2Plugin extends JavaPlugin {
    private static Logger log;

    Plugin dynmap;
    DynmapCommonAPI api;
    MarkerAPI markerapi;
    CitizensPlugin citizens;
    private MarkerIcon deficon;
    private MarkerSet npcset;
    private boolean dynamicMarkerGroups;
    private final Map<MarkerGroup, MarkerIcon> groupIcons = new EnumMap<MarkerGroup, MarkerIcon>(MarkerGroup.class);
    boolean reload = false;
    
    FileConfiguration cfg;
    
    private Set<String> existingnpcs = new HashSet<String>();

    private static final Set<String> HUMANOID_TYPES = new HashSet<String>(Arrays.asList(
            "PLAYER", "MANNEQUIN", "ARMOR_STAND", "VILLAGER", "WANDERING_TRADER",
            "PIGLIN", "PIGLIN_BRUTE", "ZOMBIFIED_PIGLIN", "PILLAGER", "VINDICATOR",
            "EVOKER", "ILLUSIONER", "WITCH"));

    private static final Set<String> LARGE_SPECIAL_TYPES = new HashSet<String>(Arrays.asList(
            "ENDER_DRAGON", "WITHER", "WARDEN", "GIANT", "RAVAGER", "ELDER_GUARDIAN",
            "IRON_GOLEM", "HAPPY_GHAST"));

    private enum MarkerGroup {
        HUMANOID("humanoid"),
        ANIMAL("animal"),
        MONSTER("monster"),
        LARGE_SPECIAL("large_special"),
        OTHER("other");

        private final String id;

        MarkerGroup(String id) {
            this.id = id;
        }
    }

    private MarkerGroup classifyNPC(NPC npc) {
        EntityType type = npc.getCosmeticEntityType();
        if (type == null) {
            return MarkerGroup.OTHER;
        }

        String typeName = type.name();
        if (LARGE_SPECIAL_TYPES.contains(typeName)) {
            return MarkerGroup.LARGE_SPECIAL;
        }
        if (HUMANOID_TYPES.contains(typeName)) {
            return MarkerGroup.HUMANOID;
        }

        Class<? extends Entity> entityClass = type.getEntityClass();
        if (entityClass != null) {
            if (Animals.class.isAssignableFrom(entityClass)
                    || WaterMob.class.isAssignableFrom(entityClass)
                    || Ambient.class.isAssignableFrom(entityClass)) {
                return MarkerGroup.ANIMAL;
            }
            if (Monster.class.isAssignableFrom(entityClass)) {
                return MarkerGroup.MONSTER;
            }
        }
        return MarkerGroup.OTHER;
    }

    private MarkerIcon getNPCIcon(NPC npc) {
        if (dynamicMarkerGroups) {
            MarkerIcon icon = groupIcons.get(classifyNPC(npc));
            if (icon != null) {
                return icon;
            }
        }
        return deficon;
    }

    private MarkerIcon registerBundledIcon(String id, String resourcePath) {
        MarkerIcon icon = markerapi.getMarkerIcon(id);
        if (icon != null) {
            return icon;
        }

        try (InputStream in = getResource(resourcePath)) {
            if (in == null) {
                severe("Cannot find bundled marker icon '" + resourcePath + "'");
                return null;
            }
            icon = markerapi.createMarkerIcon(id, id, in);
            if (icon == null) {
                severe("Cannot register Dynmap marker icon '" + id + "'");
            }
            return icon;
        } catch (IOException iox) {
            severe("Cannot load bundled marker icon '" + resourcePath + "': " + iox.getMessage());
            return null;
        }
    }

    private MarkerIcon loadBundledIcons() {
        MarkerIcon citizensIcon = registerBundledIcon("citizens", "marker/citizens.png");
        for (MarkerGroup group : MarkerGroup.values()) {
            MarkerIcon icon = registerBundledIcon("citizens_" + group.id, "marker/" + group.id + ".png");
            if (icon != null) {
                groupIcons.put(group, icon);
            }
        }
        return citizensIcon;
    }

    private void processNPC(MarkerSet set, NPC npc) {
        processNPC(set, npc, null);
    }
    
    private void processNPC(MarkerSet set, NPC npc, Set<String> toremove) {
        UUID uuid = npc.getUniqueId();
        String id = "npc_" + Long.toHexString(uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits());
        Location loc = null;
        if (npc.isSpawned()) {
            Entity ent = npc.getEntity();
            if (ent != null) {
                loc = ent.getLocation();
            }
        }

        // Citizens despawns NPC entities when their chunk is unloaded. The stored
        // location remains available and is the position Dynmap should display.
        if (loc == null) {
            loc = npc.getStoredLocation();
        }

        World world = loc == null ? null : loc.getWorld();
        if (world == null) {  // If null, see if we need to remove it
            if (existingnpcs.contains(id)) {    // Found?
                Marker m = set.findMarker(id);
                if (m != null) {
                    m.deleteMarker();
                }
                existingnpcs.remove(id);
            }
        }
        else {
            MarkerIcon icon = getNPCIcon(npc);
            Marker m = set.findMarker(id);
            if (m == null) {
                m = set.createMarker(id, npc.getName(), false, world.getName(), loc.getX(), loc.getY(), loc.getZ(), icon, false);
                if (m == null) {
                    severe("Cannot create Dynmap marker for NPC " + npc.getName() + " (" + uuid + ")");
                    return;
                }
                existingnpcs.add(id);
            }
            else {
                existingnpcs.add(id);
                m.setLocation(world.getName(), loc.getX(), loc.getY(), loc.getZ());
                m.setLabel(npc.getName());
                if (!m.getMarkerIcon().getMarkerIconID().equals(icon.getMarkerIconID())) {
                    m.setMarkerIcon(icon);
                }
            }
            if (toremove != null) {
                toremove.remove(id);
            }
        }
    }
    
    private void updateAllNPCs(MarkerSet npcset) {
        Iterable<NPCRegistry> reg = CitizensAPI.getNPCRegistries();
        HashSet<String> toremove = new HashSet<String>(existingnpcs);
        if (reg != null) {
            for (NPCRegistry r : reg) {
                for (NPC npc : r) {
                    processNPC(npcset, npc, toremove);
                }
            }
        }
        for (String s : toremove) {
            Marker m = npcset.findMarker(s);
            if (m != null) {
                m.deleteMarker();
            }
            existingnpcs.remove(s);
        }
    }

    @Override
    public void onLoad() {
        log = this.getLogger();
    }

    long updperiod;
    long playerupdperiod;
    boolean stop;
    
    public static void info(String msg) {
        log.log(Level.INFO, msg);
    }
    public static void severe(String msg) {
        log.log(Level.SEVERE, msg);
    }


    private class OurNPCListener implements Listener {
        @EventHandler
        public void onNPCCreate(NPCCreateEvent evt) {
            processNPC(npcset, evt.getNPC());
        }
        @EventHandler
        public void onNPCRemove(NPCRemoveEvent evt) {
            processNPC(npcset, evt.getNPC());
        }
        @EventHandler
        public void onNPCSpawn(NPCSpawnEvent evt) {
            processNPC(npcset, evt.getNPC());
        }
        @EventHandler
        public void onNPCDespawn(NPCDespawnEvent evt) {
            processNPC(npcset, evt.getNPC());
        }
        @EventHandler
        public void onNPCDeath(NPCDeathEvent evt) {
            processNPC(npcset, evt.getNPC());
        }
    }
    
    private class OurServerListener implements Listener {
        @EventHandler
        public void onPluginEnable(PluginEnableEvent event) {
            Plugin p = event.getPlugin();
            String name = p.getDescription().getName();
            if(name.equals("dynmap") || name.equals("Essentials")) {
                if(dynmap.isEnabled() && citizens.isEnabled())
                    activate();
            }
        }
    }
    
    public void onEnable() {
        info("initializing");
        PluginManager pm = getServer().getPluginManager();
        /* Get dynmap */
        dynmap = pm.getPlugin("dynmap");
        if(dynmap == null) {
            severe("Cannot find dynmap!");
            return;
        }
        api = (DynmapCommonAPI) dynmap; /* Get API */
        /* Get Citizens */
        Plugin p = pm.getPlugin("Citizens");
        if(p == null) {
            severe("Cannot find Citizens!");
            return;
        }
        citizens = (CitizensPlugin) p;

        getServer().getPluginManager().registerEvents(new OurServerListener(), this);        

        /* If both enabled, activate */
        if(dynmap.isEnabled() && citizens.isEnabled()) {
            activate();
            try { 
                MetricsLite ml = new MetricsLite(this);
                ml.start();
            } catch (IOException iox) {
            }
        }
    }

    private class MarkerUpdate implements Runnable {
        public void run() {
            if (!stop) {
                updateAllNPCs(npcset);
                getServer().getScheduler().scheduleSyncDelayedTask(DynmapCitizens2Plugin.this, this, updperiod);
            }
        }
    }
    
    private void activate() {
        /* Now, get markers API */
        markerapi = api.getMarkerAPI();
        if(markerapi == null) {
            severe("Error loading Dynmap marker API!");
            return;
        }
            
        /* Load configuration */
        if(reload) {
            reloadConfig();
        }
        else {
            reload = true;
        }
        FileConfiguration cfg = getConfig();
        cfg.options().copyDefaults(true);   /* Load defaults, if needed */
        this.saveConfig();  /* Save updates, if needed */
        MarkerIcon dynmapFallback = markerapi.getMarkerIcon("offlineuser");
        if (dynmapFallback == null) {
            dynmapFallback = markerapi.getMarkerIcon("default");
        }
        dynamicMarkerGroups = cfg.getBoolean("marker.dynamic-groups", true);
        groupIcons.clear();
        MarkerIcon citizensIcon = loadBundledIcons();
        deficon = citizensIcon != null ? citizensIcon : dynmapFallback;
        if (deficon == null) {
            severe("Cannot load marker icon 'citizens', 'offlineuser', or 'default'!");
            return;
        }
        npcset = markerapi.getMarkerSet("Citizens2");
        if (npcset == null) {
            npcset = markerapi.createMarkerSet("Citizens2", "NPCs", null, false);
        }
        if (npcset == null) {
            severe("Error creating Dynmap marker set 'Citizens2'!");
            return;
        }
        getServer().getPluginManager().registerEvents(new OurNPCListener(), this);        
        
        /* Set up update job - based on period */
        double per = cfg.getDouble("update.period", 5.0);
        if(per < 2.0) per = 2.0;
        updperiod = (long)(per*20.0);
        stop = false;
        getServer().getScheduler().scheduleSyncDelayedTask(this, new MarkerUpdate(), 5*20);

        info("version " + this.getDescription().getVersion() + " is activated");
    }

    public void onDisable() {
        stop = true;
    }

}
