package me.lifelessnerd.purekitpvp.kitCommand;

import me.lifelessnerd.purekitpvp.PureKitPvP;
import me.lifelessnerd.purekitpvp.database.KitDatabase;
import me.lifelessnerd.purekitpvp.database.entities.PlayerKitPreferences;
import me.lifelessnerd.purekitpvp.files.KitConfig;
import me.lifelessnerd.purekitpvp.files.KitStatsConfig;
import me.lifelessnerd.purekitpvp.files.lang.LanguageConfig;
import me.lifelessnerd.purekitpvp.files.PlayerStatsConfig;
import me.lifelessnerd.purekitpvp.utils.ComponentUtils;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetKit implements TabExecutor, Listener {
    Plugin plugin;

    public static ArrayList<String> hasKit = new ArrayList<>();

    public GetKit(Plugin plugin){
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {

        Player player = e.getPlayer();
        if (!(player.getWorld().getName().equalsIgnoreCase(plugin.getConfig().getString("world")))){
            return;
        }

        hasKit.remove(player.getName());

    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player)){
            return false;
        }
        Player player = (Player) sender;
        String kitNameArg = args[0].substring(0, 1).toUpperCase() + args[0].substring(1);

        if (!(player.getWorld().getName().equalsIgnoreCase(plugin.getConfig().getString("world")))){
            player.sendMessage(LanguageConfig.lang.get("GENERIC_WRONG_WORLD").
                    replaceText(ComponentUtils.replaceConfig("%WORLD%",plugin.getConfig().getString("world"))));
            return true;
        }

        if (hasKit.contains(player.getName())){
            player.sendMessage(LanguageConfig.lang.get("KITS_ALREADY_SELECTED"));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(LanguageConfig.lang.get("GENERIC_WRONG_ARGS"));
            return false;
        }

        if (!(KitConfig.get().isSet("kits." + kitNameArg))){
            player.sendMessage(LanguageConfig.lang.get("KITS_DOES_NOT_EXIST"));
            return true;
        }

        if(!KitConfig.get().isSet("kits." + kitNameArg + ".permission")){
            player.sendMessage(LanguageConfig.lang.get("KITS_PERMISSION_NOT_DEFINED"));
            return true;
        }

        if (!(player.hasPermission(KitConfig.get().getString("kits." + kitNameArg + ".permission")))){ //IDEA lies
            player.sendMessage(LanguageConfig.lang.get("GENERIC_NO_PERMISSION"));
            return true;
        }

        //Okay, if all checks are passed, player may get the kit
        player.sendMessage(LanguageConfig.lang.get("KITS_KIT_GIVEN").replaceText(ComponentUtils.replaceConfig("%KIT%", kitNameArg)));

        FileConfiguration fileConfiguration = KitConfig.get();
        List<ItemStack> kitItems = (List<ItemStack>) fileConfiguration.get("kits." + kitNameArg + ".contents");

        //Remove any active potion effects that make PvP unfair
        for (PotionEffect effect : player.getActivePotionEffects())
            player.removePotionEffect(effect.getType());

        Map<Integer, Integer> playerPrefs = new HashMap<>();
        PureKitPvP pureKitPvP = (PureKitPvP) plugin;
        KitDatabase db = pureKitPvP.getKitDatabase();
        try {
            if(db.hasEntry(player, kitNameArg)){
                PlayerKitPreferences playerKitPreferences = db.getEntry(player, kitNameArg);
                playerPrefs = playerKitPreferences.getPreferences();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // Give items with index validation
        for (int index = 0; index < kitItems.size(); index++) {
            ItemStack item = kitItems.get(index);
            if (item == null) {
                item = new ItemStack(Material.AIR);
            }

            // Get the target slot index with validation
            int targetSlot = playerPrefs.getOrDefault(index, index);
            if (targetSlot < 0 || targetSlot > 40) {
                plugin.getLogger().warning("Invalid inventory slot index: " + targetSlot + " for player: " + player.getName());
                continue; // Skip invalid slots
            }

            // Set the item in the player's inventory
            player.getInventory().setItem(targetSlot, item);
        }


        hasKit.add(player.getName());

        //Add current kit to config
        PlayerStatsConfig.get().set(player.getName() + ".current_kit", kitNameArg);
        PlayerStatsConfig.save();
        PlayerStatsConfig.reload();

        //Tried to put this in a static class but it did not work so its here now
        if (!(KitStatsConfig.get().isSet(kitNameArg))){

            KitStatsConfig.get().set(kitNameArg, 1);

        } else {
            int value = KitStatsConfig.get().getInt(kitNameArg);
            int newValue = value + 1;
            KitStatsConfig.get().set(kitNameArg, newValue);
        }

        KitStatsConfig.save();
        KitStatsConfig.reload();

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (args.length == 1){
            List<String> autoComplete = new ArrayList<>();
            for(String key : KitConfig.get().getConfigurationSection("kits.").getKeys(false)){
                key = key.toLowerCase();
                autoComplete.add(key);
            };

            return autoComplete;
        }



        return new ArrayList<>();
    }
}
