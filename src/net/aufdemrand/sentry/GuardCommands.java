/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.aufdemrand.sentry;

import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import vg.civcraft.mc.namelayer.GroupManager;
import vg.civcraft.mc.namelayer.group.Group;
import vg.civcraft.mc.namelayer.permission.GroupPermission;
import vg.civcraft.mc.namelayer.permission.PermissionType;

/**
 *
 * @author James
 */
public class GuardCommands implements CommandExecutor {

    private Sentry plugin;
    
    public GuardCommands(Sentry plugin)
    {
        this.plugin = plugin; 
    } 

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        
        if(!(sender instanceof Player))
        {
            return false;
        }
        else
        {
            Player player = (Player) sender;
            
            //using /guards list <group>
            //List the guards in the Area you Control
            if(args.length == 2 && args[0].equalsIgnoreCase("list")) {
                String group = args[1];
                
                Group g = GroupManager.getGroup(group);
                if(g == null)
                {
                    Util.chatBad(player, "Group does not exist");
                    return true;
                }
                GroupPermission gPerm = this.plugin.getGM().getPermissionforGroup(g);
                GroupManager.PlayerType type = g.getPlayerType(player.getUniqueId());
                
                if (gPerm.isAccessible(type, PermissionType.ADMINS))
                {
                    List<Entity> inRange = player.getNearbyEntities(20, 20, 20);
                    int i = 0;
                    for (Entity entity : inRange) {
                        if (entity.hasMetadata("NPC")) {
                            SentryInstance inst = this.plugin.getSentry(entity);
                            if (inst != null) {
                                String entgroup = inst.getGroup();
                                if (entgroup.equalsIgnoreCase(group)) {
                                    i++;                                    
                                }
                            }
                        }
                    }
                    if(i == 0)
                    {
                        Util.chatBad(player, "You control no guards in this area");
                        return true;
                    }
                    else
                    {
                        Util.chatGood(player, "You control " + i + " guards in this area");
                        return true;
                    }
                    
                }
                else
                {
                    Util.chatBad(player, "You do not have the right NameLayer permissions for group: " + ChatColor.GREEN + group);
                    return true;
                }
                
            }
            
            
            //using /guards warn <group> warning would go here for those who break it and stuff
            //sets the warning message of the Guards in the area
            if (args.length >= 3 && args[0].equalsIgnoreCase("warn")) {
                String group = args[1];
                
                String arg = "";
                for (int i = 2; i < args.length; i++) {
                    arg += " " + args[i];
                }
                arg = arg.trim();

                String warningMsg = arg.replaceAll("\"$", "").replaceAll("^\"", "").replaceAll("'$", "").replaceAll("^'", "");
                
                Group g = GroupManager.getGroup(group);
                if(g == null)
                {
                    Util.chatBad(player, "Group does not exist");
                    return true;
                }
                GroupPermission gPerm = this.plugin.getGM().getPermissionforGroup(g);
                GroupManager.PlayerType type = g.getPlayerType(player.getUniqueId());
                if (gPerm.isAccessible(type, PermissionType.ADMINS))
                {
                    List<Entity> inRange = player.getNearbyEntities(20, 20, 20);
                    int i = 0;
                    for (Entity entity : inRange) {
                        if (entity.hasMetadata("NPC")) {
                            SentryInstance inst = this.plugin.getSentry(entity);
                            if (inst != null) {
                                String entgroup = inst.getGroup();

                                if (entgroup.equalsIgnoreCase(group)) {
                                    inst.WarningMessage = warningMsg;
                                    i++;                                    
                                }
                            }
                        }
                    }
                    Util.chatGood(player, i + " Guards warning message set to: '" + ChatColor.translateAlternateColorCodes('&',warningMsg) + "'");
                    return true;
                }
                else
                {
                    Util.chatBad(player, "You do not have the right NameLayer permissions for group: " + ChatColor.GREEN + group);
                    return true;
                } 
            }
            
            
            //using /guards greet <group> greeting message would go here for those who enter
            //Sets the greeting message of the guards in the area
            if (args.length >= 3 && args[0].equalsIgnoreCase("greet"))  {
                String group = args[1];
                
                String arg = "";
                for (int i = 2; i < args.length; i++) {
                    arg += " " + args[i];
                }
                arg = arg.trim();

                String greetingMsg = arg.replaceAll("\"$", "").replaceAll("^\"", "").replaceAll("'$", "").replaceAll("^'", "");
                
                Group g = GroupManager.getGroup(group);
                if(g == null)
                {
                    Util.chatBad(player, "Group does not exist");
                    return true;
                }
                GroupPermission gPerm = this.plugin.getGM().getPermissionforGroup(g);
                GroupManager.PlayerType type = g.getPlayerType(player.getUniqueId());
                if (gPerm.isAccessible(type, PermissionType.ADMINS))
                {
                    List<Entity> inRange = player.getNearbyEntities(20, 20, 20);
                    int i = 0;
                    for (Entity entity : inRange) {
                        if (entity.hasMetadata("NPC")) {
                            SentryInstance inst = this.plugin.getSentry(entity);
                            if (inst != null) {
                                String entgroup = inst.getGroup();

                                if (entgroup.equalsIgnoreCase(group)) {
                                    inst.GreetingMessage = greetingMsg;
                                    i++;                                    
                                }
                            }
                        }
                    }
                    Util.chatGood(player, i + " Guards greeting message set to: '" + ChatColor.translateAlternateColorCodes('&',greetingMsg) + "'");
                    return true;
                }
                else
                {
                    Util.chatBad(player, "You do not have the right NameLayer permissions for group: " + ChatColor.GREEN + group);
                    return true;
                } 
            }            
            
            
            
            //using /Guards set group <group>
            //Sets the guard item in hand to specific group
            if(args.length == 3 && args[0].equalsIgnoreCase("set") && args[1].equalsIgnoreCase("group"))
            {
                String group = args[2];
                
                Group g = GroupManager.getGroup(group);
                if(g == null)
                {
                    Util.chatBad(player, "Group does not exist");
                    return true;
                }
                GroupPermission gPerm = this.plugin.getGM().getPermissionforGroup(g);
                GroupManager.PlayerType type = g.getPlayerType(player.getUniqueId());
                if(gPerm.isAccessible(type, PermissionType.ADMINS))
                {
                    if(player.getItemInHand().getType().equals(Material.PRISMARINE_SHARD))
                    {
                        ItemStack item = player.getItemInHand();
                        if(item.hasItemMeta())
                        {
                            ItemMeta meta = item.getItemMeta();
                            if (meta.hasLore())
                            {
                                List<String> lore = meta.getLore();
                                if(lore.get(0).contains("Guard"))
                                {
                                    lore.set(2, group);
                                    meta.setLore(lore);
                                    item.setItemMeta(meta);
                                    player.setItemInHand(item);
                                    player.updateInventory();
                                    Util.chatGood(player, "Guard group changed to: " + ChatColor.GREEN + group);
                                    return true;
                                }
                                else
                                {
                                    Util.chatBad(player, "You are currently not holding a Guard Item");
                                    return true;
                                }
                            }
                        }
                    }
                    else
                    {
                        Util.chatBad(player, "You are currently not holding a Guard Item");
                        return true;
                    }
                }
                else
                {
                    Util.chatBad(player, "You do not have the right NameLayer permissions for group: " + ChatColor.GREEN + group);
                    return true;
                }
            }     
        }
        return false;
    }
}
