package net.aufdemrand.sentry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import java.util.logging.Level;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.TraitInfo;
import net.citizensnpcs.api.trait.trait.Equipment;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;
import vg.civcraft.mc.namelayer.GroupManager;
import vg.civcraft.mc.namelayer.NameAPI;

public class Sentry extends JavaPlugin {

    public int archer = -1;

    public Map<Integer, Double> ArmorBuffs = new HashMap<Integer, Double>();
    public Map<Integer, Double> SpeedBuffs = new HashMap<Integer, Double>();
    public Map<Integer, Double> StrengthBuffs = new HashMap<Integer, Double>();
    public Map<Integer, List<PotionEffect>> WeaponEffects = new HashMap<Integer, List<PotionEffect>>();

    public Queue<Projectile> arrows = new LinkedList<Projectile>();

    public List<Integer> Boots = new LinkedList<Integer>(java.util.Arrays.asList(301, 305, 309, 313, 317));
    public List<Integer> Chestplates = new LinkedList<Integer>(java.util.Arrays.asList(299, 303, 307, 311, 315));
    public List<Integer> Leggings = new LinkedList<Integer>(java.util.Arrays.asList(300, 304, 308, 312, 316));
    public List<Integer> Helmets = new LinkedList<Integer>(java.util.Arrays.asList(298, 302, 306, 310, 314, 91, 86));

    public boolean debug = false;

    public boolean DieLikePlayers = false;
    public boolean BodyguardsObeyProtection = true;
    public boolean IgnoreListInvincibility = true;
    public boolean GroupsChecked = false;

    public int SentryEXP = 5;

    protected GroupManager gm;

    boolean checkPlugin(String name) {
        if (getServer().getPluginManager().getPlugin(name) != null) {
            if (getServer().getPluginManager().getPlugin(name).isEnabled() == true) {
                return true;
            }
        }
        return false;
    }

    public void debug(String s) {
        if (debug) {
            this.getServer().getLogger().info(s);
        }
    }

    public boolean equip(NPC npc, ItemStack hand) {
        Equipment trait = npc.getTrait(Equipment.class);
        if (trait == null) {
            return false;
        }
        int slot = 0;
        Material type = hand == null ? Material.AIR : hand.getType();

        if (Helmets.contains(type.getId())) {
            slot = 1;
        } else if (Chestplates.contains(type.getId())) {
            slot = 2;
        } else if (Leggings.contains(type.getId())) {
            slot = 3;
        } else if (Boots.contains(type.getId())) {
            slot = 4;
        }

        if (type == Material.AIR) {
            for (int i = 0; i < 5; i++) {
                if (trait.get(i) != null && trait.get(i).getType() != Material.AIR) {
                    try {
                        trait.set(i, null);
                    } catch (Exception e) {
                    }
                }
            }
            return true;
        } else {
            ItemStack clone = hand.clone();
            clone.setAmount(1);

            try {
                trait.set(slot, clone);
            } catch (Exception e) {
                return false;
            }
            return true;
        }

    }

    public GroupManager getGM() {
        return this.gm;
    }

    private int GetMat(String S) {
        int item = -1;

        if (S == null) {
            return item;
        }

        String[] args = S.toUpperCase().split(":");

        org.bukkit.Material M = org.bukkit.Material.getMaterial(args[0]);

        if (item == -1) {
            try {
                item = Integer.parseInt(S.split(":")[0]);
            } catch (Exception e) {
            }
        }

        if (M != null) {
            item = M.getId();
        }

        return item;
    }

    private PotionEffect getpot(String S) {
        if (S == null) {
            return null;
        }
        String[] args = S.trim().split(":");

        PotionEffectType type = null;

        int dur = 10;
        int amp = 1;

        type = PotionEffectType.getByName((args[0].toUpperCase()));

        if (type == null) {
            try {
                type = PotionEffectType.getById(Integer.parseInt(args[0]));
            } catch (Exception e) {
            }
        }

        if (type == null) {
            return null;
        }

        if (args.length > 1) {
            try {
                dur = Integer.parseInt(args[1]);
            } catch (Exception e) {
            }
        }

        if (args.length > 2) {
            try {
                amp = Integer.parseInt(args[2]);
            } catch (Exception e) {
            }
        }

        return new PotionEffect(type, dur, amp);
    }

    public SentryInstance getSentry(Entity ent) {
        if (ent == null) {
            return null;
        }
        if (!(ent instanceof org.bukkit.entity.LivingEntity)) {
            return null;
        }
        NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getNPC(ent);
        if (npc != null && npc.hasTrait(SentryTrait.class)) {
            return npc.getTrait(SentryTrait.class).getInstance();
        }

        return null;
    }

    public SentryInstance getSentry(NPC npc) {
        if (npc != null && npc.hasTrait(SentryTrait.class)) {
            return npc.getTrait(SentryTrait.class).getInstance();
        }
        return null;
    }

    public String getMCTeamName(Player player) {
        Team t = getServer().getScoreboardManager().getMainScoreboard().getPlayerTeam(player);
        if (t != null) {
            return t.getName();
        }
        return null;
    }

    public void loaditemlist(String key, List<Integer> list) {
        List<String> strs = getConfig().getStringList(key);

        if (strs.size() > 0) {
            list.clear();
        }

        for (String s : getConfig().getStringList(key)) {
            int item = GetMat(s.trim());
            list.add(item);
        }

    }

    private void loadmap(String node, Map<Integer, Double> map) {
        map.clear();
        for (String s : getConfig().getStringList(node)) {
            String[] args = s.trim().split(" ");
            if (args.length != 2) {
                continue;
            }

            double val = 0;

            try {
                val = Double.parseDouble(args[1]);
            } catch (Exception e) {
            }

            int item = GetMat(args[0]);

            if (item > 0 && val != 0 && !map.containsKey(item)) {
                map.put(item, val);
            }
        }
    }

    private void loadpots(String node, Map<Integer, List<PotionEffect>> map) {
        map.clear();
        for (String s : getConfig().getStringList(node)) {
            String[] args = s.trim().split(" ");

            if (args.length < 2) {
                continue;
            }

            int item = GetMat(args[0]);

            List<PotionEffect> list = new ArrayList<PotionEffect>();

            for (int i = 1; i < args.length; i++) {
                PotionEffect val = getpot(args[i]);
                if (val != null) {
                    list.add(val);
                }

            }

            if (item > 0 && list.isEmpty() == false) {
                map.put(item, list);
            }

        }
    }

    @Override
    public void onDisable() {

        getLogger().log(Level.INFO, " v" + getDescription().getVersion() + " disabled.");
        Bukkit.getServer().getScheduler().cancelTasks(this);

    }

    boolean DenizenActive = false;

    @Override
    public void onEnable() {

        if (getServer().getPluginManager().getPlugin("Citizens") == null || getServer().getPluginManager().getPlugin("Citizens").isEnabled() == false) {
            getLogger().log(Level.SEVERE, "Citizens 2.0 not found or not enabled");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (getServer().getPluginManager().getPlugin("NameLayer") == null || getServer().getPluginManager().getPlugin("NameLayer").isEnabled() == false) {
            getLogger().log(Level.SEVERE, "Name Layer not found!");
            return;
        }

        this.gm = NameAPI.getGroupManager();

        CitizensAPI.getTraitFactory().registerTrait(TraitInfo.create(SentryTrait.class).withName("sentry"));

        this.getServer().getPluginManager().registerEvents(new SentryListener(this), this);
        getCommand("guards").setExecutor(new GuardCommands(this));

        this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            public void run() {
                //Unloaded chunk arrow cleanup
                while (arrows.size() > 200) {
                    Projectile a = arrows.remove();
                    if (a != null) {
                        a.remove();
                    }
                }
            }
        }, 40, 20 * 120);

        reloadMyConfig();
    }

    private void reloadMyConfig() {
        this.saveDefaultConfig();
        this.reloadConfig();
        loadmap("ArmorBuffs", ArmorBuffs);
        loadmap("StrengthBuffs", StrengthBuffs);
        loadmap("SpeedBuffs", SpeedBuffs);
        loadpots("WeaponEffects", WeaponEffects);
        loaditemlist("Helmets", Helmets);
        loaditemlist("Chestplates", Chestplates);
        loaditemlist("Leggings", Leggings);
        loaditemlist("Boots", Boots);
        archer = GetMat(getConfig().getString("AttackTypes.Archer", null));
        DieLikePlayers = getConfig().getBoolean("Server.DieLikePlayers", false);
        BodyguardsObeyProtection = getConfig().getBoolean("Server.BodyguardsObeyProtection", true);
        IgnoreListInvincibility = getConfig().getBoolean("Server.IgnoreListInvincibility", true);
        SentryEXP = getConfig().getInt("Server.ExpValue", 5);

    }

}
