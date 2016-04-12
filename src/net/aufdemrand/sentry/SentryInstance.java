package net.aufdemrand.sentry;

import java.text.DecimalFormat;
import java.util.*;

import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Owner;
import net.citizensnpcs.util.PlayerAnimation;

//Version Specifics
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EntityPotion;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketPlayOutAnimation;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
/////////////////////////

import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.EntityEffect;
import org.bukkit.Location;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

public class SentryInstance {

    public enum Status {

        isDEAD, isDYING, isHOSTILE, isLOOKING, isRETALIATING, isSTUCK, isWWAITING
    }

    Sentry plugin;
    public SentryTrait myTrait;
    public NPC myNPC = null;
    public boolean loaded = false;

    private Set<Player> _myDamamgers = new HashSet<Player>();

    private Location _projTargetLostLoc;

    public Integer Armor = 0;
    public double sentryHealth = 20;
    public Integer NightVision = 16;
    public Integer sentryRange = 10;
    public int FollowDistance = 16;
    public float sentrySpeed = (float) 1.0;

    public boolean isCrew;
    public boolean ignoreLeash;
    public String sentryGroup;

    public Double AttackRateSeconds = 2.0;
    public int attackRate = 15;
    public long timeSinceAttack = 0;
    public Double HealRate = 0.0;
    Packet healanim = null;

    public boolean KillsDropInventory = true;
    public boolean DropInventory = false;
    public boolean Targetable = true;
    public boolean Invincible = false;
    public boolean IgnoreLOS;

    public int epcount = 0;
    private GiveUpStuckAction giveup = new GiveUpStuckAction(this);

    public String GreetingMessage = "&a<NPC> says: Welcome, <PLAYER>!";
    public String WarningMessage = "&a<NPC> says: Halt! Come no further!";
    public Integer WarningRange = 10;
    private Map<Player, Long> Warnings = new HashMap<Player, Long>();

    public LivingEntity guardEntity = null;
    public String guardTarget = null;

    public List<String> ignoreTargets = new ArrayList<String>();
    public List<String> validTargets = new ArrayList<String>();
    public Set<String> _ignoreTargets = new HashSet<String>();
    public Set<String> _validTargets = new HashSet<String>();

    public LivingEntity meleeTarget;
    public LivingEntity projectileTarget;

    private Class<? extends Projectile> myProjectile;

    Long isRespawnable = System.currentTimeMillis();
    private long oktoFire = System.currentTimeMillis();
    private long oktoheal = System.currentTimeMillis();
    private long oktoreasses = System.currentTimeMillis();
    private long okToTakedamage = 0;

    public List<PotionEffect> potionEffects = null;
    ItemStack potiontype = null;

    Random r = new Random();
    public Integer RespawnDelaySeconds = 10;
    public Boolean Retaliate = true;

    public Status sentryStatus = Status.isDYING;
    public Double sentryWeight = 1.0;
    public Location Spawn = null;
    public Integer Strength = 1;

    private Integer taskID = null;

    public SentryInstance(Sentry plugin) {
        this.plugin = plugin;
        isRespawnable = System.currentTimeMillis();
    }

    public void cancelRunnable() {
        if (taskID != null) {
            plugin.getServer().getScheduler().cancelTask(taskID);
        }
    }

    public boolean hasTargetType(int type) {
        return (this.targets & type) == type;
    }

    public boolean hasIgnoreType(int type) {
        return (this.ignores & type) == type;
    }

    public boolean isIgnored(LivingEntity aTarget) {
        //cheak ignores

        if (aTarget == this.guardEntity) {
            return true;
        }

        if (ignores == 0) {
            return false;
        }

        if (hasIgnoreType(all)) {
            return true;
        }

        if (aTarget instanceof Player && !net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(aTarget)) {

            if (hasIgnoreType(players)) {
                return true;
            } else {
                String name = ((Player) aTarget).getName();
                UUID uuid = ((Player) aTarget).getUniqueId();
                List<String> groups = this.plugin.getGM().getAllGroupNames(uuid);

                if (this.hasIgnoreType(namedplayers) && containsIgnore("PLAYER:" + name)) {
                    return true;
                }

                if (this.hasIgnoreType(namelayer)) {
                    for (String group : groups) {
                        if (containsIgnore("NAMELAYER:" + group.toUpperCase())) {
                            return true;
                        }
                    }
                }

                if (this.hasIgnoreType(owner) && name.equalsIgnoreCase(myNPC.getTrait(Owner.class).getOwner())) {
                    return true;
                }

            }
        } else if (net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(aTarget)) {

            if (this.hasIgnoreType(npcs)) {
                return true;
            }

            NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getNPC(aTarget);

            if (npc != null) {

                String name = npc.getName();

                if (this.hasIgnoreType(namednpcs) && this.containsIgnore("NPC:" + name)) {
                    return true;
                }

                SentryInstance inst = this.plugin.getSentry(npc);
                if (inst != null) {
                    String group = inst.getGroup();
                    if (this.hasIgnoreType(namelayer)) {
                        if (containsIgnore("NAMELAYER:" + group.toUpperCase())) {
                            return true;
                        }
                    }
                }

            }
        } else if (aTarget instanceof Monster && hasIgnoreType(monsters)) {
            return true;
        } else if (aTarget instanceof LivingEntity && hasIgnoreType(namedentities)) {
            if (this.containsIgnore("ENTITY:" + aTarget.getType())) {
                return true;
            }
        }

        //not ignored, ok!
        return false;
    }

    public boolean isTarget(LivingEntity aTarget) {

        if (targets == 0 || targets == events) {
            return false;
        }

        if (this.hasTargetType(all)) {
            return true;
        }

        //Check if target
        if (aTarget instanceof Player && !net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(aTarget)) {

            if (this.hasTargetType(players)) {
                return true;
            } else {
                String name = ((Player) aTarget).getName();
                UUID uuid = ((Player) aTarget).getUniqueId();
                List<String> groups = this.plugin.getGM().getAllGroupNames(uuid);

                if (this.hasTargetType(namelayer)) {
                    for (String group : groups) {
                        if (containsTarget("NAMELAYER:" + group.toUpperCase())) {
                            return true;
                        }
                    }
                }

                if (hasTargetType(namedplayers) && this.containsTarget("PLAYER:" + name)) {
                    return true;
                }

                if (this.containsTarget("ENTITY:OWNER") && name.equalsIgnoreCase(myNPC.getTrait(Owner.class).getOwner())) {
                    return true;
                }

            }
        } else if (net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(aTarget)) {

            if (this.hasTargetType(npcs)) {
                return true;
            }

            NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getNPC(aTarget);

            String name = npc.getName();

            if (this.hasTargetType(namednpcs) && containsTarget("NPC:" + name)) {
                return true;
            }

            SentryInstance inst = this.plugin.getSentry(npc);
            if (inst != null) {
                String group = inst.getGroup();
                if (this.hasTargetType(namelayer)) {
                    if (containsTarget("NAMELAYER:" + group.toUpperCase())) {
                        return true;
                    }
                }
            }

        } else if (aTarget instanceof Monster && this.hasTargetType(monsters)) {
            return true;
        } else if (aTarget instanceof LivingEntity && hasTargetType(namedentities)) {
            if (this.containsTarget("ENTITY:" + aTarget.getType())) {
                return true;
            }
        }
        return false;

    }

    public boolean containsIgnore(String theTarget) {
        return _ignoreTargets.contains(theTarget.toUpperCase());
    }

    public boolean containsTarget(String theTarget) {
        return _validTargets.contains(theTarget.toUpperCase());

    }

    public void deactivate() {
        plugin.getServer().getScheduler().cancelTask(taskID);
    }

    public void die(boolean runscripts, org.bukkit.event.entity.EntityDamageEvent.DamageCause cause) {
        if (sentryStatus == Status.isDYING || sentryStatus == Status.isDEAD || getMyEntity() instanceof LivingEntity == false) {
            return;
        }

        sentryStatus = Status.isDYING;

        setTarget(null, false);
        //		myNPC.getTrait(Waypoints.class).getCurrentProvider().setPaused(true);

        sentryStatus = Status.isDEAD;

        if (this.DropInventory) {
            getMyEntity().getLocation().getWorld().spawn(getMyEntity().getLocation(), ExperienceOrb.class).setExperience(plugin.SentryEXP);
        }

        List<ItemStack> items = new java.util.LinkedList<ItemStack>();

        if (getMyEntity() instanceof HumanEntity) {
            //get drop inventory.
            for (ItemStack is : ((HumanEntity) getMyEntity()).getInventory().getArmorContents()) {
                if (is.getTypeId() > 0) {
                    items.add(is);
                }
            }

            ItemStack is = ((HumanEntity) getMyEntity()).getInventory().getItemInHand();
            if (is.getTypeId() > 0) {
                items.add(is);
            }

            ((HumanEntity) getMyEntity()).getInventory().clear();
            ((HumanEntity) getMyEntity()).getInventory().setArmorContents(null);
            ((HumanEntity) getMyEntity()).getInventory().setItemInHand(null);
        }

        if (items.isEmpty()) {
            getMyEntity().playEffect(EntityEffect.DEATH);
        } else {
            getMyEntity().playEffect(EntityEffect.HURT);
        }

        if (!DropInventory) {
            items.clear();
        }

        for (ItemStack is : items) {
            getMyEntity().getWorld().dropItemNaturally(getMyEntity().getLocation(), is);
        }

        if (plugin.DieLikePlayers) {
            //die!

            ((LivingEntity) getMyEntity()).setHealth(0);

        } else {
            org.bukkit.event.entity.EntityDeathEvent ed = new org.bukkit.event.entity.EntityDeathEvent((LivingEntity) getMyEntity(), items);

            plugin.getServer().getPluginManager().callEvent(ed);
            //citizens will despawn it.

        }

        if (RespawnDelaySeconds == -1) {
            cancelRunnable();

            myNPC.destroy();
            return;
        } else {
            isRespawnable = System.currentTimeMillis() + RespawnDelaySeconds * 1000;
        }
    }

    private void faceEntity(Entity from, Entity at) {

        if (from.getWorld() != at.getWorld()) {
            return;
        }
        Location loc = from.getLocation();

        double xDiff = at.getLocation().getX() - loc.getX();
        double yDiff = at.getLocation().getY() - loc.getY();
        double zDiff = at.getLocation().getZ() - loc.getZ();

        double distanceXZ = Math.sqrt(xDiff * xDiff + zDiff * zDiff);
        double distanceY = Math.sqrt(distanceXZ * distanceXZ + yDiff * yDiff);

        double yaw = (Math.acos(xDiff / distanceXZ) * 180 / Math.PI);
        double pitch = (Math.acos(yDiff / distanceY) * 180 / Math.PI) - 90;
        if (zDiff < 0.0) {
            yaw = yaw + (Math.abs(180 - yaw) * 2);
        }

        net.citizensnpcs.util.NMS.look((LivingEntity) from, (float) yaw - 90, (float) pitch);

    }

    private void faceForward() {
        net.citizensnpcs.util.NMS.look(getMyEntity(), getMyEntity().getLocation().getYaw(), 0);
    }

    private void faceAlignWithVehicle() {
        org.bukkit.entity.Entity v = getMyEntity().getVehicle();
        net.citizensnpcs.util.NMS.look((LivingEntity) getMyEntity(), v.getLocation().getYaw(), 0);
    }

    public LivingEntity findTarget(Integer Range) {
        Range += WarningRange;
        List<Entity> EntitiesWithinRange = getMyEntity().getNearbyEntities(Range, Range, Range);
        LivingEntity theTarget = null;
        Double distanceToBeat = 99999.0;

        // plugin.getServer().broadcastMessage("Targets scanned : " +
        // EntitiesWithinRange.toString());
        for (Entity aTarget : EntitiesWithinRange) {
            if (!(aTarget instanceof LivingEntity)) {
                continue;
            }

            // find closest target
            if (!isIgnored((LivingEntity) aTarget) && isTarget((LivingEntity) aTarget)) {

                // can i see it?
                // too dark?
                double ll = aTarget.getLocation().getBlock().getLightLevel();
                // sneaking cut light in half
                if (aTarget instanceof Player) {
                    if (((Player) aTarget).isSneaking()) {
                        ll /= 2;
                    }
                }

                // too dark?
                if (ll >= (16 - this.NightVision)) {

                    double dist = aTarget.getLocation().distance(getMyEntity().getLocation());

                    if (hasLOS(aTarget)) {

                        if (WarningRange > 0 && sentryStatus == Status.isLOOKING && aTarget instanceof Player && dist > (Range - WarningRange) && !net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(aTarget) & !(WarningMessage.isEmpty())) {

                            if (Warnings.containsKey(aTarget) && System.currentTimeMillis() < Warnings.get(aTarget) + 60 * 1000) {
                                //already warned u in last 30 seconds.
                            } else {
                                ((Player) aTarget).sendMessage(getWarningMessage((Player) aTarget));
                                if (!getNavigator().isNavigating()) {
                                    faceEntity(getMyEntity(), aTarget);
                                }
                                Warnings.put((Player) aTarget, System.currentTimeMillis());
                            }

                        } else if (dist < distanceToBeat) {
                            // now find closes mob
                            distanceToBeat = dist;
                            theTarget = (LivingEntity) aTarget;
                        }
                    }

                }

            } else {
                //not a target

                if (WarningRange > 0 && sentryStatus == Status.isLOOKING && aTarget instanceof Player && !net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(aTarget) && !(GreetingMessage.isEmpty())) {
                    boolean LOS = getMyEntity().hasLineOfSight(aTarget);
                    if (LOS) {
                        if (Warnings.containsKey(aTarget) && System.currentTimeMillis() < Warnings.get(aTarget) + 60 * 1000) {
                            //already greeted u in last 30 seconds.
                        } else {
                            ((Player) aTarget).sendMessage(getGreetingMEssage((Player) aTarget));
                            faceEntity(getMyEntity(), aTarget);
                            Warnings.put((Player) aTarget, System.currentTimeMillis());
                        }
                    }
                }

            }

        }

        if (theTarget != null) {
            // plugin.getServer().broadcastMessage("Targeting: " +
            // theTarget.toString());
            return theTarget;
        }

        return null;
    }

    public void Draw(boolean on) {
        ((CraftLivingEntity) (getMyEntity())).getHandle().b(on); // TODO: 1.8 UPDATE - IS THIS CORRECT?
    }

    public void Fire(LivingEntity theEntity) {

        double v = 34;
        double g = 20;

        Effect effect = null;

        boolean ballistics = true;

        effect = Effect.BOW_FIRE;

        Location loc = Util.getFireSource(getMyEntity(), theEntity);

        Location targetsHeart = theEntity.getLocation();
        targetsHeart = targetsHeart.add(0, .33, 0);

        Vector test = targetsHeart.clone().subtract(loc).toVector();

        Double elev = test.getY();

        Double testAngle = Util.launchAngle(loc, targetsHeart, v, elev, g);

        if (testAngle == null) {
            setTarget(null, false);
            return;
        }

        Double hangtime = Util.hangtime(testAngle, v, elev, g);
        Vector targetVelocity = theEntity.getLocation().subtract(_projTargetLostLoc).toVector();
        targetVelocity.multiply(1);
        Location to = Util.leadLocation(targetsHeart, targetVelocity, hangtime);

        Vector victor = to.clone().subtract(loc).toVector();

        Double dist = Math.sqrt(Math.pow(victor.getX(), 2) + Math.pow(victor.getZ(), 2));
        elev = victor.getY();
        if (dist == 0) {
            return;
        }

        if (!hasLOS(theEntity)) {

            setTarget(null, false);

            return;
        }

        if (ballistics) {
            Double launchAngle = Util.launchAngle(loc, to, v, elev, g);
            if (launchAngle == null) {
                setTarget(null, false);
                return;

            }

            victor.setY(Math.tan(launchAngle) * dist);
            Vector noise = Vector.getRandom();
            victor = Util.normalizeVector(victor);

            noise = noise.multiply(1 / 10.0);

            if (myProjectile == Arrow.class || myProjectile == org.bukkit.entity.ThrownPotion.class) {
                v = v + (1.188 * Math.pow(hangtime, 2));
            } else {
                v = v + (.5 * Math.pow(hangtime, 2));
            }

            v = v + (r.nextDouble() - .8) / 2;

            victor = victor.multiply(v / 20.0);

        } else {
            if (dist > sentryRange) {
                setTarget(null, false);
                return;

            }
        }

        Projectile theArrow = getMyEntity().getWorld().spawn(loc, myProjectile);

        plugin.arrows.add(theArrow);
        theArrow.setShooter(getMyEntity());
        theArrow.setVelocity(victor);

        if (effect != null) {
            getMyEntity().getWorld().playEffect(getMyEntity().getLocation(), effect, null);
        }

        Draw(false);

    }

    public int getArmor() {

        double mod = 0;
        if (getMyEntity() instanceof Player) {
            for (ItemStack is : ((Player) getMyEntity()).getInventory().getArmorContents()) {
                if (plugin.ArmorBuffs.containsKey(is.getTypeId())) {
                    mod += plugin.ArmorBuffs.get(is.getTypeId());
                }
            }
        }

        return (int) (Armor + mod);
    }

    String getGreetingMEssage(Player player) {
        String str = GreetingMessage.replace("<NPC>", myNPC.getName()).replace("<PLAYER>", player.getName());
        return ChatColor.translateAlternateColorCodes('&', str);
    }

    public LivingEntity getGuardTarget() {
        return this.guardEntity;
    }

    public String getGroup() {
        if (myNPC == null) {
            return "none";
        }
        if (getMyEntity() == null) {
            return "none";
        }
        return sentryGroup;
    }

    public void setCrew() {
        if (isCrew == true) {
            isCrew = false;
        } else {
            isCrew = true;
        }
    }

    public void setGroup(String group) {
        if (myNPC == null) {
            return;
        }
        if (getMyEntity() == null) {
            return;
        }
        sentryGroup = group;
    }

    public double getHealth() {
        if (myNPC == null) {
            return 0;
        }
        if (getMyEntity() == null) {
            return 0;
        }
        return ((CraftLivingEntity) getMyEntity()).getHealth();
    }

    public float getSpeed() {
        if (myNPC.isSpawned() == false) {
            return sentrySpeed;
        }
        double mod = 0;
        if (getMyEntity() instanceof Player) {
            for (ItemStack is : ((Player) getMyEntity()).getInventory().getArmorContents()) {
                if (plugin.SpeedBuffs.containsKey(is.getTypeId())) {
                    mod += plugin.SpeedBuffs.get(is.getTypeId());
                }
            }
        }
        return (float) (sentrySpeed + mod) * (this.getMyEntity().isInsideVehicle() ? 2 : 1);
    }

    public String getStats() {
        DecimalFormat df = new DecimalFormat("#.0");
        double h = getHealth();

        return ChatColor.RED + "[HP]:" + ChatColor.WHITE + h + "/" + sentryHealth + ChatColor.RED + " [AP]:" + ChatColor.WHITE + getArmor()
                + ChatColor.RED + " [STR]:" + ChatColor.WHITE + getStrength() + ChatColor.RED + " [SPD]:" + ChatColor.WHITE + df.format(getSpeed())
                + ChatColor.RED + " [RNG]:" + ChatColor.WHITE + sentryRange + ChatColor.RED + " [ATK]:" + ChatColor.WHITE + AttackRateSeconds + ChatColor.RED + " [VIS]:" + ChatColor.WHITE + NightVision
                + ChatColor.RED + " [HEAL]:" + ChatColor.WHITE + HealRate + ChatColor.RED + " [WARN]:" + ChatColor.WHITE + WarningRange + ChatColor.RED + " [FOL]:" + ChatColor.WHITE + Math.sqrt(FollowDistance);

    }

    public int getStrength() {
        double mod = 0;

        if (getMyEntity() instanceof Player) {
            if (plugin.StrengthBuffs.containsKey(((Player) getMyEntity()).getInventory().getItemInHand().getTypeId())) {
                mod += plugin.StrengthBuffs.get(((Player) getMyEntity()).getInventory().getItemInHand().getTypeId());
            }
        }

        return (int) (Strength + mod);
    }

    String getWarningMessage(Player player) {
        String str = WarningMessage.replace("<NPC>", myNPC.getName()).replace("<PLAYER>", player.getName());
        return ChatColor.translateAlternateColorCodes('&', str);

    }

    public void initialize() {

		// plugin.getServer().broadcastMessage("NPC " + npc.getName() +
        // " INITIALIZING!");
        // check for illegal values
        if (sentryWeight <= 0) {
            sentryWeight = 1.0;
        }
        if (AttackRateSeconds > 30) {
            AttackRateSeconds = 30.0;
        }

        if (sentryHealth < 0) {
            sentryHealth = 0;
        }

        if (sentryRange < 1) {
            sentryRange = 1;
        }
        if (sentryRange > 200) {
            sentryRange = 200;
        }

        if (sentryWeight <= 0) {
            sentryWeight = 1.0;
        }

        if (RespawnDelaySeconds < -1) {
            RespawnDelaySeconds = -1;
        }

        if (Spawn == null) {
            Spawn = getMyEntity().getLocation();
        }

        //disable citizens respawning. Cause Sentry doesnt always raise EntityDeath
        myNPC.data().set("respawn-delay", -1);

        setHealth(sentryHealth);

        _myDamamgers.clear();

        this.sentryStatus = Status.isLOOKING;
        faceForward();

        healanim = new PacketPlayOutAnimation(((CraftEntity) getMyEntity()).getHandle(), 6);

        //	Packet derp = new net.minecraft.server.Packet15Place();
        if (guardTarget == null) {
            myNPC.teleport(Spawn, TeleportCause.PLUGIN); //it should be there... but maybe not if the position was saved elsewhere.
        }

        float pf = myNPC.getNavigator().getDefaultParameters().range();

        if (pf < sentryRange + 5) {
            pf = sentryRange + 5;
        }

        myNPC.data().set(NPC.DEFAULT_PROTECTED_METADATA, false);
        myNPC.data().set(NPC.TARGETABLE_METADATA, this.Targetable);

        myNPC.getNavigator().getDefaultParameters().range(pf);
        myNPC.getNavigator().getDefaultParameters().stationaryTicks(5 * 20);
        myNPC.getNavigator().getDefaultParameters().useNewPathfinder(false);

        processTargets();

        if (taskID == null) {
            taskID = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new SentryLogic(), 40 + this.myNPC.getId(), 1L);

        }
    }

    public void onDamage(EntityDamageByEntityEvent event) {

        if (sentryStatus == Status.isDYING) {
            return;
        }

        if (myNPC == null || !myNPC.isSpawned()) {
            // \\how did you get here?
            return;
        }

        if (guardTarget != null && guardEntity == null) {
            return; //dont take damage when bodyguard target isnt around.
        }
        if (System.currentTimeMillis() < okToTakedamage + 500) {
            return;
        }
        okToTakedamage = System.currentTimeMillis();

        event.getEntity().setLastDamageCause(event);

        NPC npc = myNPC;

        LivingEntity attacker = null;

        double finaldamage = event.getDamage();

        // Find the attacker
        if (event.getDamager() instanceof Projectile) {
            if (((Projectile) event.getDamager()).getShooter() instanceof LivingEntity) {
                attacker = (LivingEntity) ((Projectile) event.getDamager()).getShooter();
            }
        } else if (event.getDamager() instanceof LivingEntity) {
            attacker = (LivingEntity) event.getDamager();
        }

        if (Invincible) {
            return;
        }

        if (plugin.IgnoreListInvincibility) {
            if (isIgnored(attacker)) {
                return;
            }
        }

        // can i kill it? lets go kill it.
        if (attacker != null) {
            if (this.Retaliate) {
                if (!(event.getDamager() instanceof Projectile) || (net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getNPC(attacker) == null)) {
                    // only retaliate to players or non-projectlies. Prevents stray sentry arrows from causing retaliation.

                    setTarget(attacker, true);

                }
            }
        }

        int arm = getArmor();

        if (finaldamage > 0) {

            if (attacker != null) {
                // knockback
                npc.getEntity().setVelocity(attacker.getLocation().getDirection().multiply(1.0 / (sentryWeight + (arm / 5))));
            }

            // Apply armor
            finaldamage -= arm;

            // there was damamge before armor.
            if (finaldamage <= 0) {
                npc.getEntity().getWorld().playEffect(npc.getEntity().getLocation(), org.bukkit.Effect.ZOMBIE_CHEW_IRON_DOOR, 1);
            }
        }

        if (finaldamage > 0) {
            npc.getEntity().playEffect(EntityEffect.HURT);

            // is he dead?
            if (getHealth() - finaldamage <= 0) {

                //set the killer
                if (event.getDamager() instanceof HumanEntity) {
                    ((CraftLivingEntity) getMyEntity()).getHandle().killer = (EntityHuman) ((CraftLivingEntity) event.getDamager()).getHandle();
                }

                die(true, event.getCause());

            } else {
                getMyEntity().damage(finaldamage);
            }
        }
    }

    Random R = new Random();

    public void onEnvironmentDamae(EntityDamageEvent event) {

        if (sentryStatus == Status.isDYING) {
            return;
        }

        if (!myNPC.isSpawned() || Invincible) {
            return;
        }

        if (guardTarget != null && guardEntity == null) {
            return; //dont take damage when bodyguard target isnt around.
        }
        if (System.currentTimeMillis() < okToTakedamage + 500) {
            return;
        }
        okToTakedamage = System.currentTimeMillis();

        getMyEntity().setLastDamageCause(event);

        double finaldamage = event.getDamage();

        if (event.getCause() == DamageCause.CONTACT || event.getCause() == DamageCause.BLOCK_EXPLOSION) {
            finaldamage -= getArmor();
        }

        if (finaldamage > 0) {
            getMyEntity().playEffect(EntityEffect.HURT);

            if (event.getCause() == DamageCause.FIRE) {
                if (!getNavigator().isNavigating()) {
                    getNavigator().setTarget(getMyEntity().getLocation().add(R.nextInt(2) - 1, 0, R.nextInt(2) - 1));
                }
            }

            if (getHealth() - finaldamage <= 0) {

                die(true, event.getCause());

                // plugin.getServer().broadcastMessage("Dead!");
            } else {
                getMyEntity().damage(finaldamage);

            }
        }

    }

    @EventHandler
    public void onRightClick(NPCRightClickEvent event) {

    }

    final int all = 1;
    final int players = 2;
    final int npcs = 4;
    final int monsters = 8;
    final int events = 16;
    final int namedentities = 32;
    final int namedplayers = 64;
    final int namednpcs = 128;
    final int namelayer = 256;
    final int owner = 4096;
    final int clans = 8192;
    final int townyenemies = 16384;
    final int factionenemies = 16384 * 2;
    final int mcTeams = 16384 * 4;

    private int targets = 0;
    private int ignores = 0;

    public void processTargets() {
        try {

            targets = 0;
            ignores = 0;
            _ignoreTargets.clear();
            _validTargets.clear();

            for (String t : validTargets) {
                if (t.contains("ENTITY:ALL")) {
                    targets |= all;
                } else if (t.contains("ENTITY:MONSTER")) {
                    targets |= monsters;
                } else if (t.contains("ENTITY:PLAYER")) {
                    targets |= players;
                } else if (t.contains("ENTITY:NPC")) {
                    targets |= npcs;
                } else {
                    _validTargets.add(t);
                    if (t.contains("NPC:")) {
                        targets |= namednpcs;
                    } else if (t.contains("EVENT:")) {
                        targets |= events;
                    } else if (t.contains("PLAYER:")) {
                        targets |= namedplayers;
                    } else if (t.contains("ENTITY:")) {
                        targets |= namedentities;
                    } else if (t.contains("NAMELAYER:")) {
                        targets |= namelayer;
                    }
                }
            }
            for (String t : ignoreTargets) {
                if (t.contains("ENTITY:ALL")) {
                    ignores |= all;
                } else if (t.contains("ENTITY:MONSTER")) {
                    ignores |= monsters;
                } else if (t.contains("ENTITY:PLAYER")) {
                    ignores |= players;
                } else if (t.contains("ENTITY:NPC")) {
                    ignores |= npcs;
                } else if (t.contains("ENTITY:OWNER")) {
                    ignores |= owner;
                } else {
                    _ignoreTargets.add(t);
                    if (t.contains("NPC:")) {
                        ignores |= namednpcs;
                    } else if (t.contains("PLAYER:")) {
                        ignores |= namedplayers;
                    } else if (t.contains("ENTITY:")) {
                        ignores |= namedentities;
                    } else if (t.contains("NAMELAYER:")) {
                        ignores |= namelayer;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private class SentryLogic implements Runnable {

        @Override
        public void run() {
            timeSinceAttack += 1;
            // plugin.getServer().broadcastMessage("tick " + (myNPC ==null) +
            if (getMyEntity() == null) {
                sentryStatus = Status.isDEAD; // incase it dies in a way im not handling.....
            }
            if (UpdateWeapon()) {
                //ranged
                if (meleeTarget != null) {
                    plugin.debug(myNPC.getName() + " Switched to ranged");
                    LivingEntity derp = meleeTarget;
                    boolean ret = sentryStatus == Status.isRETALIATING;
                    setTarget(null, false);
                    setTarget(derp, ret);
                }
            } else {
                //melee
                if (projectileTarget != null) {
                    plugin.debug(myNPC.getName() + " Switched to melee");
                    boolean ret = sentryStatus == Status.isRETALIATING;
                    LivingEntity derp = projectileTarget;
                    setTarget(null, false);
                    setTarget(derp, ret);
                }
            }

            if (myNPC.isSpawned() && ignoreLeash == false) {
                if (myNPC.getEntity().getLocation().distance(Spawn) > 80) {
                    myNPC.getEntity().teleport(Spawn);
                }
            }

            if (sentryStatus != Status.isDEAD && HealRate > 0) {
                if (System.currentTimeMillis() > oktoheal) {
                    if (getHealth() < sentryHealth && sentryStatus != Status.isDEAD && sentryStatus != Status.isDYING) {
                        double heal = 1;
                        if (HealRate < 0.5) {
                            heal = (0.5 / HealRate);
                        }

                        setHealth(getHealth() + heal);

                        if (healanim != null) {
                            net.citizensnpcs.util.NMS.sendPacketsNearby(null, getMyEntity().getLocation(), healanim);
                        }

                        if (getHealth() >= sentryHealth) {
                            _myDamamgers.clear(); //healed to full, forget attackers
                        }
                    }
                    oktoheal = (long) (System.currentTimeMillis() + HealRate * 1000);
                }

            }

            if (sentryStatus == Status.isDEAD && System.currentTimeMillis() > isRespawnable && RespawnDelaySeconds > 0 & Spawn.getWorld().isChunkLoaded(Spawn.getBlockX() >> 4, Spawn.getBlockZ() >> 4)) {
                // Respawn

                plugin.debug("respawning" + myNPC.getName());
                if (guardEntity == null) {
                    myNPC.spawn(Spawn.clone());
                    //	myNPC.teleport(Spawn,org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                } else {
                    myNPC.spawn(guardEntity.getLocation().add(2, 0, 2));
                    //	myNPC.teleport(guardEntity.getLocation().add(2, 0, 2),org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                }
                return;
            } else if ((sentryStatus == Status.isHOSTILE || sentryStatus == Status.isRETALIATING) && myNPC.isSpawned()) {

                if (!isMyChunkLoaded()) {
                    setTarget(null, false);
                    return;
                }

                if (targets > 0 && sentryStatus == Status.isHOSTILE && System.currentTimeMillis() > oktoreasses) {
                    LivingEntity target = findTarget(sentryRange);
                    setTarget(target, false);
                    oktoreasses = System.currentTimeMillis() + 3000;
                }

                if (projectileTarget != null && !projectileTarget.isDead() && projectileTarget.getWorld() == getMyEntity().getLocation().getWorld()) {
                    if (_projTargetLostLoc == null) {
                        _projTargetLostLoc = projectileTarget.getLocation();
                    }

                    if (!getNavigator().isNavigating()) {
                        faceEntity(getMyEntity(), projectileTarget);
                    }

                    Draw(true);

                    if (System.currentTimeMillis() > oktoFire) {
                        // Fire!
                        oktoFire = (long) (System.currentTimeMillis() + AttackRateSeconds * 1000.0);
                        Fire(projectileTarget);
                    }
                    if (projectileTarget != null) {
                        _projTargetLostLoc = projectileTarget.getLocation();
                    }

                    return; // keep at it
                } else if (meleeTarget != null && !meleeTarget.isDead()) {

                    //Attacking Stuff ZANTID
                    if (meleeTarget.getWorld() == getMyEntity().getLocation().getWorld()) {
                        double dist = meleeTarget.getLocation().distance(getMyEntity().getLocation());
                        double distsq = meleeTarget.getLocation().distanceSquared(getMyEntity().getLocation());
                        if (distsq < 3 * 3) {
                            if (timeSinceAttack > attackRate) {
                                punch(meleeTarget);
                                timeSinceAttack = 0;
                            }
                        }
                        //block if in range
                        Draw(dist < 3);
                        // Did it get away?
                        if (dist > sentryRange) {
                            // it got away...
                            setTarget(null, false);
                        }
                    } else {
                        setTarget(null, false);
                    }

                } else {
                    // target died or null
                    setTarget(null, false);
                }

            } else if (sentryStatus == Status.isLOOKING && myNPC.isSpawned()) {

                if (getMyEntity().isInsideVehicle() == true) {
                    faceAlignWithVehicle(); //sync the rider with the vehicle.
                }

                if (guardEntity instanceof Player) {
                    if (((Player) guardEntity).isOnline() == false) {
                        guardEntity = null;
                    }
                }

                if (guardTarget != null && guardEntity == null) {
                    // daddy? where are u?
                    setGuardTarget(guardTarget, false);
                }

                if (guardTarget != null && guardEntity == null) {
                    // daddy? where are u?
                    setGuardTarget(guardTarget, true);
                }

                if (guardEntity != null) {

                    Location npcLoc = getMyEntity().getLocation();

                    if (guardEntity.getLocation().getWorld() != npcLoc.getWorld() || !isMyChunkLoaded()) {
                        if (Util.CanWarp(guardEntity, myNPC)) {
                            myNPC.despawn();
                            myNPC.spawn((guardEntity.getLocation().add(1, 0, 1)));
                        } else {
                            ((Player) guardEntity).sendMessage(myNPC.getName() + " cannot follow you to " + guardEntity.getWorld().getName());
                            guardEntity = null;
                        }

                    } else {
                        double dist = npcLoc.distanceSquared(guardEntity.getLocation());
                        plugin.debug(myNPC.getName() + dist + getNavigator().isNavigating() + " " + getNavigator().getEntityTarget() + " ");
                        if (dist > 1024) {
                            myNPC.teleport(guardEntity.getLocation().add(1, 0, 1), TeleportCause.PLUGIN);
                        } else if (dist > FollowDistance && !getNavigator().isNavigating()) {
                            getNavigator().setTarget((Entity) guardEntity, false);
                            getNavigator().getLocalParameters().stationaryTicks(3 * 20);
                        } else if (dist < FollowDistance && getNavigator().isNavigating()) {
                            getNavigator().cancelNavigation();
                        }
                    }
                }

                LivingEntity target = null;

                if (targets > 0) {
                    target = findTarget(sentryRange);
                }

                if (target != null) {
                    oktoreasses = System.currentTimeMillis() + 3000;
                    setTarget(target, false);
                }

                if (target == null && ignoreLeash == false) {
                    if (Spawn.distance(myNPC.getEntity().getLocation()) > 20) {
                        getNavigator().setTarget(Spawn);
                    } else if (Spawn.distance(myNPC.getEntity().getLocation()) < 10) {
                        getNavigator().cancelNavigation();
                    }
                }

            }

        }
    }

    public void punch(LivingEntity entity) {
        faceEntity(getMyEntity(), entity);
        swingWeapon();
        entity.damage(Strength, getMyEntity());

    }

    public void swingWeapon() {
        if (myNPC.isSpawned() && getMyEntity() instanceof Player) {
            PlayerAnimation.ARM_SWING.play((Player) getMyEntity());
        }
    }

    private boolean isMyChunkLoaded() {
        if (getMyEntity() == null) {
            return false;
        }
        Location npcLoc = getMyEntity().getLocation();
        return npcLoc.getWorld().isChunkLoaded(npcLoc.getBlockX() >> 4, npcLoc.getBlockZ() >> 4);
    }

    public boolean setGuardTarget(String name, boolean forcePlayer) {

        if (myNPC == null) {
            return false;
        }

        if (name == null) {
            guardEntity = null;
            guardTarget = null;
            setTarget(null, false);// clear active hostile target
            return true;
        }

        if (!forcePlayer) {

            List<Entity> EntitiesWithinRange = getMyEntity().getNearbyEntities(sentryRange, sentryRange, sentryRange);

            for (Entity aTarget : EntitiesWithinRange) {

                if (aTarget instanceof Player) {
                    //chesk for players
                    if (((Player) aTarget).getName().equals(name)) {
                        guardEntity = (LivingEntity) aTarget;
                        guardTarget = ((Player) aTarget).getName();
                        setTarget(null, false); // clear active hostile target
                        return true;
                    }
                } else if (aTarget instanceof LivingEntity) {
                    //check for named mobs.
                    String ename = ((LivingEntity) aTarget).getCustomName();
                    if (ename != null && ename.equals(name)) {
                        guardEntity = (LivingEntity) aTarget;
                        guardTarget = ename;
                        setTarget(null, false); // clear active hostile target
                        return true;
                    }
                }

            }
        } else {

            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (player.getName().equals(name)) {
                    guardEntity = (LivingEntity) player;
                    guardTarget = player.getName();
                    setTarget(null, false); // clear active hostile target
                    return true;
                }

            }

        }

        return false;

    }

    public void setHealth(double health) {
        if (myNPC == null) {
            return;
        }
        if (getMyEntity() == null) {
            return;
        }
        if (((CraftLivingEntity) getMyEntity()).getMaxHealth() != sentryHealth) {
            getMyEntity().setMaxHealth(sentryHealth);
        }
        if (health > sentryHealth) {
            health = sentryHealth;
        }

        getMyEntity().setHealth(health);
    }

    public boolean UpdateWeapon() {
        int weapon = 0;

        ItemStack is = null;

        if (getMyEntity() instanceof HumanEntity) {
            is = ((HumanEntity) getMyEntity()).getInventory().getItemInHand();
            weapon = is.getTypeId();
        }

        potionEffects = plugin.WeaponEffects.get(weapon);

        myProjectile = null;

        if (weapon == plugin.archer || getMyEntity() instanceof org.bukkit.entity.Skeleton) {
            myProjectile = org.bukkit.entity.Arrow.class;
        } else {
            return false; //melee
        }

        return true; //ranged
    }

    public void setTarget(LivingEntity theEntity, boolean isretaliation) {

        if (getMyEntity() == null) {
            return;
        }

        if (theEntity == getMyEntity()) {
            return;
        }
        if (guardTarget != null && guardEntity == null) {
            theEntity = null;
        }
        if (theEntity == null) {
            plugin.debug(myNPC.getName() + "- Set Target Null");

            sentryStatus = Status.isLOOKING;
            projectileTarget = null;
            meleeTarget = null;
            _projTargetLostLoc = null;
        }

        if (myNPC == null) {
            return;
        }
        if (!myNPC.isSpawned()) {
            return;
        }

        if (theEntity == null) {

            Draw(false);

            if (guardEntity != null) {

                getGoalController().setPaused(true);

                if (getNavigator().getEntityTarget() == null || (getNavigator().getEntityTarget() != null && getNavigator().getEntityTarget().getTarget() != guardEntity)) {

                    if (guardEntity.getLocation().getWorld() != getMyEntity().getLocation().getWorld()) {
                        myNPC.despawn();
                        myNPC.spawn((guardEntity.getLocation().add(1, 0, 1)));
                        return;
                    }

                    getNavigator().setTarget((Entity) guardEntity, false);

                    getNavigator().getLocalParameters().stationaryTicks(3 * 20);
                }
            } else {

                getNavigator().cancelNavigation();

                faceForward();

                if (getGoalController().isPaused()) {
                    getGoalController().setPaused(false);
                }
            }
            return;
        }

        if (theEntity == guardEntity) {
            return;
        }
        if (isretaliation) {
            sentryStatus = Status.isRETALIATING;
        } else {
            sentryStatus = Status.isHOSTILE;
        }

        if (!getNavigator().isNavigating()) {
            faceEntity(getMyEntity(), theEntity);
        }

        if (UpdateWeapon()) {

            plugin.debug(myNPC.getName() + "- Set Target projectile");
            projectileTarget = theEntity;
            meleeTarget = null;
        } else {

            plugin.debug(myNPC.getName() + "- Set Target melee");
            meleeTarget = theEntity;
            projectileTarget = null;
            if (getNavigator().getEntityTarget() != null && getNavigator().getEntityTarget().getTarget() == theEntity) {
                return;
            }
            if (!getGoalController().isPaused()) {
                getGoalController().setPaused(true);
            }

            getNavigator().setTarget((Entity) theEntity, true);
            getNavigator().getLocalParameters().speedModifier(getSpeed());
            getNavigator().getLocalParameters().stuckAction(giveup);
            getNavigator().getLocalParameters().stationaryTicks(5 * 20);
        }
    }

    protected net.citizensnpcs.api.ai.Navigator getNavigator() {
        return myNPC.getNavigator();
    }

    protected net.citizensnpcs.api.ai.GoalController getGoalController() {
        return myNPC.getDefaultGoalController();
    }

    public boolean hasLOS(Entity other) {
        if (!myNPC.isSpawned()) {
            return false;
        }
        if (IgnoreLOS) {
            return true;
        }
        return getMyEntity().hasLineOfSight(other);
    }

    public LivingEntity getMyEntity() {
        if (myNPC == null) {
            return null;
        }
        if (myNPC.getEntity() == null) {
            return null;
        }
        if (myNPC.getEntity().isDead()) {
            return null;
        }
        if (!(myNPC.getEntity() instanceof LivingEntity)) {
            plugin.getServer().getLogger().info("Sentry " + myNPC.getName() + " is not a living entity! Errors inbound....");
            return null;
        }
        return (LivingEntity) myNPC.getEntity();
    }

}
