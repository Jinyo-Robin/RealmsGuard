package net.aufdemrand.sentry;

import java.util.List;
import net.aufdemrand.sentry.SentryInstance.Status;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.projectiles.ProjectileSource;

import net.citizensnpcs.api.CitizensAPI;

import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.api.trait.trait.Inventory;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.EntityType;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import vg.civcraft.mc.namelayer.GroupManager;
import vg.civcraft.mc.namelayer.group.Group;
import vg.civcraft.mc.namelayer.permission.GroupPermission;
import vg.civcraft.mc.namelayer.permission.PermissionType;

public class SentryListener implements Listener {

    public Sentry plugin;

    public SentryListener(Sentry sentry) {
        plugin = sentry;
    }

    @EventHandler
    public void kill(org.bukkit.event.entity.EntityDeathEvent event) {

        if (event.getEntity() == null) {
            return;
        }

        if (event.getEntity() instanceof Player && event.getEntity().hasMetadata("NPC") == false) {
            return;
        }

        Entity killer = event.getEntity().getKiller();
        if (killer == null) {
            EntityDamageEvent ev = event.getEntity().getLastDamageCause();
            if (ev != null && ev instanceof EntityDamageByEntityEvent) {
                killer = ((EntityDamageByEntityEvent) ev).getDamager();
                if (killer instanceof Projectile && ((Projectile) killer).getShooter() instanceof Entity) {
                    killer = (Entity) ((Projectile) killer).getShooter();
                }
            }
        }

        SentryInstance sentry = plugin.getSentry(killer);

        if (sentry != null && sentry.KillsDropInventory == false) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void despawn(net.citizensnpcs.api.event.NPCDespawnEvent event) {
        SentryInstance sentry = plugin.getSentry(event.getNPC());

        if (sentry != null && event.getReason() == net.citizensnpcs.api.event.DespawnReason.CHUNK_UNLOAD && sentry.guardEntity != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void EnvDamage(EntityDamageEvent event) {

        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }

        SentryInstance inst = plugin.getSentry(event.getEntity());
        if (inst == null) {
            return;
        }

        event.setCancelled(true);

        DamageCause cause = event.getCause();

        switch (cause) {
            case CONTACT:
            case DROWNING:
            case LAVA:
            case SUFFOCATION:
            case CUSTOM:
            case BLOCK_EXPLOSION:
            case VOID:
            case SUICIDE:
            case MAGIC:
                inst.onEnvironmentDamae(event);
                break;
            case LIGHTNING:
                inst.onEnvironmentDamae(event);
                break;
            case FIRE:
            case FIRE_TICK:
                inst.onEnvironmentDamae(event);
                break;
            case POISON:
                inst.onEnvironmentDamae(event);
                break;
            case FALL:
                break;
            default:
                break;
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {

        Entity entfrom = event.getDamager();
        Entity entto = event.getEntity();

        if (entfrom instanceof org.bukkit.entity.Projectile && entfrom instanceof Entity) {
            ProjectileSource source = ((Projectile) entfrom).getShooter();
            if (source instanceof Entity) {
                entfrom = (Entity) ((org.bukkit.entity.Projectile) entfrom).getShooter();
            }
        }

        SentryInstance from = plugin.getSentry(entfrom);
        SentryInstance to = plugin.getSentry(entto);

        plugin.debug("start: from: " + entfrom + " to " + entto + " cancelled " + event.isCancelled() + " damage " + event.getDamage() + " cause " + event.getCause());

        if (from != null) {

            if (event.getDamager() instanceof org.bukkit.entity.Projectile) {
                if (entto instanceof LivingEntity && from.isIgnored((LivingEntity) entto)) {
                    event.setCancelled(true);
                    event.getDamager().remove();
                    Projectile newProjectile = (Projectile) (entfrom.getWorld().spawnEntity(event.getDamager().getLocation().add(event.getDamager().getVelocity()), event.getDamager().getType()));
                    newProjectile.setVelocity(event.getDamager().getVelocity());
                    newProjectile.setShooter((LivingEntity) entfrom);
                    newProjectile.setTicksLived(event.getDamager().getTicksLived());
                    return;
                }
            }

            event.setDamage((double) from.getStrength());

            if (from.guardTarget == null || plugin.BodyguardsObeyProtection == false) {
                event.setCancelled(false);
            }

            if (to == null) {
                NPC n = CitizensAPI.getNPCRegistry().getNPC(entto);
                if (n != null) {
                    boolean derp = n.data().get(NPC.DEFAULT_PROTECTED_METADATA, true);
                    event.setCancelled(derp);
                }
            }

            if (entto == from.guardEntity) {
                event.setCancelled(true);
            }

            if (entfrom == entto) {
                event.setCancelled(true);
            }

            if (from.potionEffects != null && event.isCancelled() == false) {
                ((LivingEntity) entto).addPotionEffects(from.potionEffects);
            }

        }

        boolean ok = false;

        if (to != null) {
            if (entfrom == entto) {
                return;
            }

            if (to.guardTarget == null) {
                event.setCancelled(false);
            }

            if (entfrom == to.guardEntity) {
                event.setCancelled(true);
            }

            if (entfrom != null) {
                NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getNPC(entfrom);
                if (npc != null && npc.hasTrait(SentryTrait.class) && to.guardEntity != null) {
                    if (npc.getTrait(SentryTrait.class).getInstance().guardEntity == to.guardEntity) {
                        event.setCancelled(true);
                    }
                }
            }

            if (!event.isCancelled()) {
                ok = true;
                to.onDamage(event);
            }

            event.setCancelled(true);
        }

        if ((event.isCancelled() == false || ok) && entfrom != entto && event.getDamage() > 0) {
            for (NPC npc : CitizensAPI.getNPCRegistry()) {
                SentryInstance inst = plugin.getSentry(npc);

                if (inst == null || !npc.isSpawned() || npc.getEntity().getWorld() != entto.getWorld()) {
                    continue;
                }
                if (inst.guardEntity == entto) {
                    if (inst.Retaliate && entfrom instanceof LivingEntity) {
                        inst.setTarget((LivingEntity) entfrom, true);
                    }
                }

                if (inst.hasTargetType(16) && inst.sentryStatus == net.aufdemrand.sentry.SentryInstance.Status.isLOOKING && entfrom instanceof Player && CitizensAPI.getNPCRegistry().isNPC(entfrom) == false) {

                    if (npc.getEntity().getLocation().distance(entto.getLocation()) <= inst.sentryRange || npc.getEntity().getLocation().distance(entfrom.getLocation()) <= inst.sentryRange) {

                        if (inst.NightVision >= entfrom.getLocation().getBlock().getLightLevel() || inst.NightVision >= entto.getLocation().getBlock().getLightLevel()) {

                            if (inst.hasLOS(entfrom) || inst.hasLOS(entto)) {

                                if ((!(entto instanceof Player) && inst.containsTarget("event:pve"))
                                        || (entto instanceof Player && CitizensAPI.getNPCRegistry().isNPC(entto) == false && inst.containsTarget("event:pvp"))
                                        || (CitizensAPI.getNPCRegistry().isNPC(entto) == true && inst.containsTarget("event:pvnpc"))
                                        || (to != null && inst.containsTarget("event:pvsentry"))) {

                                    if (!inst.isIgnored((LivingEntity) entfrom)) {
                                        inst.setTarget((LivingEntity) entfrom, true);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return;
    }

    @EventHandler
    public void onNPCRightClick(net.citizensnpcs.api.event.NPCRightClickEvent event) {
        SentryInstance inst = plugin.getSentry(event.getNPC());
        if (inst == null) {
            return;
        }
        NPC ThisNPC = event.getNPC();
        String group = inst.getGroup();
        Player player = event.getClicker();

        Group g = GroupManager.getGroup(group);
        GroupPermission gPerm = this.plugin.getGM().getPermissionforGroup(g);
        GroupManager.PlayerType type = g.getPlayerType(player.getUniqueId());

        if (gPerm.isAccessible(type, PermissionType.ADMINS)) {
            if (player.getItemInHand().getType().equals(Material.STICK)) {
                player.sendMessage(ChatColor.GOLD + "------- Sentry Info for (" + ThisNPC.getId() + ") " + ThisNPC.getName() + "------");
                player.sendMessage(ChatColor.GREEN + inst.getStats());
                player.sendMessage(ChatColor.GREEN + "NameLayer Group: " + inst.getGroup());
                player.sendMessage(ChatColor.GOLD + "----- Target List -----");
                player.sendMessage(ChatColor.GREEN + "Targets: " + inst.validTargets.toString());
                player.sendMessage(ChatColor.GOLD + "----- Ignore List -----");
                player.sendMessage(ChatColor.GREEN + "Ignores: " + inst.ignoreTargets.toString());

            } else if (player.getItemInHand().getType().equals(Material.TORCH)) {
                player.sendMessage(inst.isCrew + "");
                if (inst.isCrew) {
                    if (inst.guardTarget != null) {
                        inst.setGuardTarget(null, false);
                        inst.ignoreLeash = false;
                        inst.Spawn = player.getLocation();
                        Util.chatGood(player, "No longer Guarding anyone");
                    } else {
                        inst.setGuardTarget(player.getName(), true);
                        inst.ignoreLeash = true;
                        Util.chatGood(player, "Now Guarding you");
                    }
                } else {
                    Util.chatBad(player, "This Guard type is not set to Crew");
                }

            } else if (checkFood(player.getItemInHand(), ThisNPC)) {
                if (inst.getHealth() == inst.sentryHealth) {
                    Util.chatGood(player, "This Guard is fully healed!");
                } else {
                    Util.chatGood(player, "This Guard has been healed a little");
                }

                if (player.getItemInHand().getAmount() > 1) {
                    player.getItemInHand().setAmount(player.getItemInHand().getAmount() - 1);
                } else {
                    player.setItemInHand(null);
                }
            } else {
                Util.chatBad(player, "Right Click with: Stick for Guard Stats, Torch to Guard you, Food to heal");
            }
        } else {
            Util.chatBad(player, "You don't have the right NameLayer permissions to interact with this guard");
        }
    }

    @EventHandler
    public void spawningGuard(PlayerInteractEvent evt) {
        if (evt.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Player player = evt.getPlayer();
            if ((!player.getItemInHand().getType().equals(Material.AIR)) || player.getItemInHand() != null) {
                if (player.getItemInHand().getType().equals(Material.PRISMARINE_SHARD)) {
                    ItemStack i = player.getItemInHand();
                    ItemMeta meta = i.getItemMeta();
                    if (meta.hasLore()) {
                        List<String> lore = meta.getLore();
                        if (lore.get(0).contains("Guard")) {
                            String guardName = meta.getDisplayName();
                            String sentryGroup = lore.get(2);
                            String guardType = lore.get(1);
                            NPC newGuard = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, guardName);
                            newGuard.addTrait(SentryTrait.class);
                            newGuard.addTrait(Equipment.class);
                            newGuard.addTrait(Inventory.class);

                            newGuard.data().setPersistent(NPC.PLAYER_SKIN_UUID_METADATA, "scarecr0w");
                            newGuard.data().setPersistent(NPC.PLAYER_SKIN_USE_LATEST, false);
                            newGuard.spawn(evt.getClickedBlock().getLocation().add(0, 1, 0));

                            SentryInstance inst = newGuard.getTrait(SentryTrait.class).getInstance();

                            inst.setGroup(sentryGroup);

                            String ignore = "NAMELAYER:" + sentryGroup.toUpperCase();
                            if (!inst.containsIgnore(ignore.toUpperCase())) {
                                inst.ignoreTargets.add(ignore.toUpperCase());
                            }
                            inst.processTargets();
                            inst.setTarget(null, false);
                            inst.sentryStatus = Status.isLOOKING;
                            inst.sentryRange = 40;
                            if (guardType.contains("Crew Sword") || guardType.contains("Crew Archer")) {
                                inst.setCrew();
                                player.sendMessage("Guard set to Crew");
                                player.sendMessage(inst.isCrew + "");
                            }

                            if (lore.get(1).contains("Diamond")) {
                                ItemStack helm = new ItemStack(Material.DIAMOND_HELMET, 1);
                                ItemStack chest = new ItemStack(Material.DIAMOND_CHESTPLATE, 1);
                                ItemStack leggings = new ItemStack(Material.DIAMOND_LEGGINGS, 1);
                                ItemStack boots = new ItemStack(Material.DIAMOND_BOOTS, 1);
                                ItemStack sword = new ItemStack(Material.DIAMOND_SWORD, 1);

                                this.plugin.equip(newGuard, helm);
                                this.plugin.equip(newGuard, chest);
                                this.plugin.equip(newGuard, leggings);
                                this.plugin.equip(newGuard, boots);
                                this.plugin.equip(newGuard, sword);

                            } else if (lore.get(1).contains("Iron")) {
                                ItemStack helm = new ItemStack(Material.IRON_HELMET, 1);
                                ItemStack chest = new ItemStack(Material.IRON_CHESTPLATE, 1);
                                ItemStack leggings = new ItemStack(Material.IRON_LEGGINGS, 1);
                                ItemStack boots = new ItemStack(Material.IRON_BOOTS, 1);
                                ItemStack sword = new ItemStack(Material.IRON_SWORD, 1);

                                this.plugin.equip(newGuard, helm);
                                this.plugin.equip(newGuard, chest);
                                this.plugin.equip(newGuard, leggings);
                                this.plugin.equip(newGuard, boots);
                                this.plugin.equip(newGuard, sword);
                                newGuard.getNavigator().getDefaultParameters().speedModifier(1.5F);
                            } else if (lore.get(1).contains("Archer")) {
                                ItemStack helm = new ItemStack(Material.LEATHER_HELMET, 1);
                                ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE, 1);
                                ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS, 1);
                                ItemStack boots = new ItemStack(Material.LEATHER_BOOTS, 1);
                                ItemStack bow = new ItemStack(Material.BOW, 1);

                                this.plugin.equip(newGuard, helm);
                                this.plugin.equip(newGuard, chest);
                                this.plugin.equip(newGuard, leggings);
                                this.plugin.equip(newGuard, boots);
                                this.plugin.equip(newGuard, bow);
                            } else if (lore.get(1).contains("Crew Sword")) {
                                ItemStack helm = new ItemStack(Material.IRON_HELMET, 1);
                                ItemStack chest = new ItemStack(Material.IRON_CHESTPLATE, 1);
                                ItemStack leggings = new ItemStack(Material.IRON_LEGGINGS, 1);
                                ItemStack boots = new ItemStack(Material.IRON_BOOTS, 1);
                                ItemStack sword = new ItemStack(Material.IRON_SWORD, 1);

                                this.plugin.equip(newGuard, helm);
                                this.plugin.equip(newGuard, chest);
                                this.plugin.equip(newGuard, leggings);
                                this.plugin.equip(newGuard, boots);
                                this.plugin.equip(newGuard, sword);

                            } else if (lore.get(1).contains("Crew Archer")) {
                                ItemStack helm = new ItemStack(Material.LEATHER_HELMET, 1);
                                ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE, 1);
                                ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS, 1);
                                ItemStack boots = new ItemStack(Material.LEATHER_BOOTS, 1);
                                ItemStack bow = new ItemStack(Material.BOW, 1);

                                this.plugin.equip(newGuard, helm);
                                this.plugin.equip(newGuard, chest);
                                this.plugin.equip(newGuard, leggings);
                                this.plugin.equip(newGuard, boots);
                                this.plugin.equip(newGuard, bow);

                                newGuard.getNavigator().getDefaultParameters().speedModifier(1.5F);
                            }
                            return;
                        }
                    }
                }
            }
            if (evt.getClickedBlock() != null) {
                Block clicked = evt.getClickedBlock();
                if (clicked.getType() == Material.SIGN_POST || clicked.getType() == Material.WALL_SIGN) {
                    if (clicked.getState() instanceof Sign) {
                        Sign sign = (Sign) clicked.getState();
                        String signType = sign.getLine(0);

                        //Ignoring Targets
                        if (signType.contains("NPC IGNORE")) {
                            handleIgnore(player, sign);
                        } //removing ignore Targets
                        else if (signType.contains("NPC NOIGNORE")) {
                            handleNoIgnore(player, sign);
                        } //Targetting List
                        else if (signType.contains("NPC TARGET")) {
                            handleTarget(player, sign);
                        } //remove from targetting list
                        else if (signType.contains("NPC NOTARGET")) {
                            handleNoTarget(player, sign);
                        }
                    }
                }
            }
        }
    }

    public void handleIgnore(Player player, Sign sign) {
        String type = sign.getLine(1);
        String object = sign.getLine(2);
        String guardGroup = sign.getLine(3);

        if (guardGroup.length() < 1) {
            Util.chatBad(player, "Guard Sign is missing Guard NameLayer Group");
            return;
        } else if (type.length() < 1) {
            Util.chatBad(player, "Guard Sign is missing Type of ignore: NameLayer, Entity, Player");
            return;
        } else if (object.length() < 1) {
            Util.chatBad(player, "Guard Sign is missing the Object of ignore: NameLayer group name, Entity Type, Player Name");
            return;
        } else {
            Group g = GroupManager.getGroup(guardGroup);
            if (g == null) {
                return;
            }
            GroupPermission gPerm = this.plugin.getGM().getPermissionforGroup(g);
            GroupManager.PlayerType permType = g.getPlayerType(player.getUniqueId());

            if (gPerm.isAccessible(permType, PermissionType.ADMINS)) {
                String ignore;
                if (type.equalsIgnoreCase("player")) {
                    ignore = "PLAYER:";
                } else if (type.equalsIgnoreCase("namelayer")) {
                    ignore = "NAMELAYER:";
                } else if (type.equalsIgnoreCase("entity")) {
                    ignore = "ENTITY:";
                } else {
                    Util.chatBad(player, "Guard Sign ignore type is not valid, must be: Namelayer, Player, or Entity");
                    return;
                }

                ignore = ignore + object.toUpperCase();

                List<Entity> inRange = player.getNearbyEntities(20, 20, 20);
                int i = 0;
                for (Entity entity : inRange) {
                    if (entity.hasMetadata("NPC")) {
                        SentryInstance inst = this.plugin.getSentry(entity);
                        if (inst != null) {
                            String entgroup = inst.getGroup();

                            if (entgroup.equalsIgnoreCase(guardGroup)) {

                                if (!inst.containsIgnore(ignore.toUpperCase())) {
                                    inst.ignoreTargets.add(ignore.toUpperCase());
                                    inst.processTargets();
                                    i++;
                                }
                            }
                        }
                    }
                }
                if (i > 0) {
                    Util.chatGood(player, i + " Guards have been set to ignore: " + ignore);
                } else {
                    Util.chatBad(player, "No Guards were found within range");
                }
            } else {
                Util.chatBad(player, "You are not Admin of NameLayer group " + guardGroup);
            }
        }

    }

    public void handleNoIgnore(Player player, Sign sign) {
        String type = sign.getLine(1);
        String object = sign.getLine(2);
        String guardGroup = sign.getLine(3);

        if (guardGroup.length() < 1) {
            Util.chatBad(player, "Guard Sign is missing Guard NameLayer Group");
            return;
        } else if (type.length() < 1) {
            Util.chatBad(player, "Guard Sign is missing Type of ignore: NameLayer, Entity, Player");
            return;
        } else if (object.length() < 1) {
            Util.chatBad(player, "Guard Sign is missing the Object of ignore: NameLayer group name, Entity Type, Player Name");
            return;
        } else {
            Group g = GroupManager.getGroup(guardGroup);
            if (g == null) {
                return;
            }
            GroupPermission gPerm = this.plugin.getGM().getPermissionforGroup(g);
            GroupManager.PlayerType permType = g.getPlayerType(player.getUniqueId());

            if (gPerm.isAccessible(permType, PermissionType.ADMINS)) {
                String ignore;
                if (type.equalsIgnoreCase("player")) {
                    ignore = "PLAYER:";
                } else if (type.equalsIgnoreCase("namelayer")) {
                    ignore = "NAMELAYER:";
                } else if (type.equalsIgnoreCase("entity")) {
                    ignore = "ENTITY:";
                } else {
                    Util.chatBad(player, "Guard Sign ignore type is not valid, must be: Namelayer, Player, or Entity");
                    return;
                }

                ignore = ignore + object.toUpperCase();

                List<Entity> inRange = player.getNearbyEntities(20, 20, 20);
                int i = 0;
                for (Entity entity : inRange) {
                    if (entity.hasMetadata("NPC")) {
                        SentryInstance inst = this.plugin.getSentry(entity);
                        if (inst != null) {
                            String entgroup = inst.getGroup();

                            if (entgroup.equalsIgnoreCase(guardGroup)) {

                                if (inst.containsIgnore(ignore.toUpperCase())) {
                                    inst.ignoreTargets.remove(ignore.toUpperCase());
                                    inst.processTargets();
                                    i++;
                                }
                            }
                        }
                    }
                }
                if (i > 0) {
                    Util.chatGood(player, i + " Guards have removed from ignore list: " + ignore);
                } else {
                    Util.chatBad(player, "No Guards were found within range");
                }
            } else {
                Util.chatBad(player, "You are not Admin of NameLayer group " + guardGroup);
            }
        }

    }

    public void handleTarget(Player player, Sign sign) {
        String type = sign.getLine(1);
        String object = sign.getLine(2);
        String guardGroup = sign.getLine(3);

        if (guardGroup.length() < 1) {
            Util.chatBad(player, "Guard Sign is missing Guard NameLayer Group");
            return;
        } else if (type.length() < 1) {
            Util.chatBad(player, "Guard Sign is missing Type of target: NameLayer, Entity, Player");
            return;
        } else if (object.length() < 1) {
            Util.chatBad(player, "Guard Sign is missing the Object of target: NameLayer group name, Entity Type, Player Name");
            return;
        } else {
            Group g = GroupManager.getGroup(guardGroup);
            if (g == null) {
                return;
            }
            GroupPermission gPerm = this.plugin.getGM().getPermissionforGroup(g);
            GroupManager.PlayerType permType = g.getPlayerType(player.getUniqueId());

            if (gPerm.isAccessible(permType, PermissionType.ADMINS)) {
                String target;
                if (type.equalsIgnoreCase("player")) {
                    target = "PLAYER:";
                } else if (type.equalsIgnoreCase("namelayer")) {
                    target = "NAMELAYER:";
                } else if (type.equalsIgnoreCase("entity")) {
                    target = "ENTITY:";
                } else {
                    Util.chatBad(player, "Guard Sign ignore type is not valid, must be: Namelayer, Player, or Entity");
                    return;
                }

                target = target + object.toUpperCase();

                List<Entity> inRange = player.getNearbyEntities(20, 20, 20);
                int i = 0;
                for (Entity entity : inRange) {
                    if (entity.hasMetadata("NPC")) {
                        SentryInstance inst = this.plugin.getSentry(entity);
                        if (inst != null) {
                            String entgroup = inst.getGroup();

                            if (entgroup.equalsIgnoreCase(guardGroup)) {

                                if (!inst.containsTarget(target.toUpperCase())) {
                                    inst.validTargets.add(target.toUpperCase());
                                    inst.processTargets();
                                    i++;
                                }
                            }
                        }
                    }
                }
                if (i > 0) {
                    Util.chatGood(player, i + " Guards have been set to target: " + target);
                } else {
                    Util.chatBad(player, "No Guards were found within range");
                }
            } else {
                Util.chatBad(player, "You are not Admin of NameLayer group " + guardGroup);
            }
        }

    }

    public void handleNoTarget(Player player, Sign sign) {
        String type = sign.getLine(1);
        String object = sign.getLine(2);
        String guardGroup = sign.getLine(3);

        if (guardGroup.length() < 1) {
            Util.chatBad(player, "Guard Sign is missing Guard NameLayer Group");
            return;
        } else if (type.length() < 1) {
            Util.chatBad(player, "Guard Sign is missing Type of target: NameLayer, Entity, Player");
            return;
        } else if (object.length() < 1) {
            Util.chatBad(player, "Guard Sign is missing the Object of target: NameLayer group name, Entity Type, Player Name");
            return;
        } else {
            Group g = GroupManager.getGroup(guardGroup);
            if (g == null) {
                return;
            }
            GroupPermission gPerm = this.plugin.getGM().getPermissionforGroup(g);
            GroupManager.PlayerType permType = g.getPlayerType(player.getUniqueId());

            if (gPerm.isAccessible(permType, PermissionType.ADMINS)) {
                String target;
                if (type.equalsIgnoreCase("player")) {
                    target = "PLAYER:";
                } else if (type.equalsIgnoreCase("namelayer")) {
                    target = "NAMELAYER:";
                } else if (type.equalsIgnoreCase("entity")) {
                    target = "ENTITY:";
                } else {
                    Util.chatBad(player, "Guard Sign ignore type is not valid, must be: Namelayer, Player, or Entity");
                    return;
                }

                target = target + object.toUpperCase();

                List<Entity> inRange = player.getNearbyEntities(20, 20, 20);
                int i = 0;
                for (Entity entity : inRange) {
                    if (entity.hasMetadata("NPC")) {
                        SentryInstance inst = this.plugin.getSentry(entity);
                        if (inst != null) {
                            String entgroup = inst.getGroup();

                            if (entgroup.equalsIgnoreCase(guardGroup)) {

                                if (inst.containsTarget(target.toUpperCase())) {
                                    inst.validTargets.remove(target.toUpperCase());
                                    inst.processTargets();
                                    i++;
                                }
                            }
                        }
                    }
                }
                if (i > 0) {
                    Util.chatGood(player, i + " Guards have removed from target list: " + target);
                } else {
                    Util.chatBad(player, "No Guards were found within range");
                }
            } else {
                Util.chatBad(player, "You are not Admin of NameLayer group " + guardGroup);
            }
        }

    }

    private boolean checkFood(ItemStack food, NPC myNPC) {

        SentryInstance inst = plugin.getSentry(myNPC);
        if (inst == null) {
            return false;
        } else {
            Double health = inst.getHealth();
            if (food.getType().equals(Material.COOKED_BEEF)) {
                Double add = this.plugin.getConfig().getDouble("DefaultHealing.CookedBeef");
                inst.setHealth(health + add);
                return true;
            } else if (food.getType().equals(Material.GRILLED_PORK)) {
                Double add = this.plugin.getConfig().getDouble("DefaultHealing.CookedPork");
                inst.setHealth(health + add);
                return true;
            } else if (food.getType().equals(Material.COOKED_MUTTON)) {
                Double add = this.plugin.getConfig().getDouble("DefaultHealing.CookedMutton");
                inst.setHealth(health + add);
                return true;
            } else if (food.getType().equals(Material.COOKED_CHICKEN)) {
                Double add = this.plugin.getConfig().getDouble("DefaultHealing.CookedChicken");
                inst.setHealth(health + add);
                return true;
            } else if (food.getType().equals(Material.BREAD)) {
                Double add = this.plugin.getConfig().getDouble("DefaultHealing.Bread");
                inst.setHealth(health + add);
                return true;
            } else if (food.getType().equals(Material.COOKED_FISH)) {
                Double add = this.plugin.getConfig().getDouble("DefaultHealing.CookedFish");
                inst.setHealth(health + add);
                return true;
            } else if (food.getType().equals(Material.MUSHROOM_SOUP)) {
                Double add = this.plugin.getConfig().getDouble("DefaultHealing.MushroomStew");
                inst.setHealth(health + add);
                return true;
            } else if (food.getType().equals(Material.APPLE)) {
                Double add = this.plugin.getConfig().getDouble("DefaultHealing.Apple");
                inst.setHealth(health + add);
                return true;
            } else if (food.getType().equals(Material.MELON)) {
                Double add = this.plugin.getConfig().getDouble("DefaultHealing.MelonSlice");
                inst.setHealth(health + add);
                return true;
            } else if (food.getType().equals(Material.PUMPKIN_PIE)) {
                Double add = this.plugin.getConfig().getDouble("DefaultHealing.PumpkinPie");
                inst.setHealth(health + add);
                return true;
            } else {
                return false;
            }
        }
    }
}
