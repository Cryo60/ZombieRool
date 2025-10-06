package net.mcreator.zombierool;

import net.minecraft.world.entity.player.Player;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ZombieDamageHandler {

    @SubscribeEvent
    public static void onZombieDamaged(LivingHurtEvent event) {
        // La logique a été déplacée dans ArrowImpactHandler pour plus de précision.
        // On laisse cette classe vide pour éviter de compter les points en double.
        // Vous pouvez la supprimer si elle n'est plus utilisée pour autre chose.
    }
}
