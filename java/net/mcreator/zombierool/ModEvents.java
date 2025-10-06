// Example: In your mod's 'events' package or similar.
// This is conceptual; your MCreator setup might have a specific place for event handlers.
package net.mcreator.zombierool.events;

import net.mcreator.zombierool.item.DeagleWeaponItem; // Import your Deagle item
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod; // Use your mod's main class name/ID

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE) // Replace "zombierool" with your mod ID
public class ModEvents {

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // Ensure the killer is a player
        if (event.getSource().getEntity() instanceof Player killer) {
            LivingEntity killedEntity = event.getEntity();
            ItemStack mainHandItem = killer.getMainHandItem();

            // Check if the item held is an instance of DeagleWeaponItem
            if (mainHandItem.getItem() instanceof DeagleWeaponItem deagleItem) {
                // Call the new method on your Deagle item instance
                deagleItem.onZombieKilled(mainHandItem, killer, killedEntity);
            }
        }
    }
}