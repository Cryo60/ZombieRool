package net.mcreator.zombierool.event;

import net.minecraftforge.event.entity.item.ItemTossEvent; // CHANGEMENT ICI
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.mcreator.zombierool.init.ZombieroolModMobEffects;
import net.mcreator.zombierool.PointManager;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Random;

@Mod.EventBusSubscriber
public class DropItemPointGainEvent {

    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public static void onItemTossed(ItemTossEvent event) { // CHANGEMENT ICI : méthode renommée
        ItemEntity tossedItem = event.getEntity();
        Player player = event.getPlayer(); // L'événement ItemTossEvent a directement le joueur !

        // Vérifie si le joueur a l'effet Vautour
        if (player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_VULTURE.get())) {
            // Génère un nombre aléatoire entre 10 et 30 (pour obtenir 100-300 points)
            int randomMultiplier = RANDOM.nextInt(21) + 10;
            int pointsToAward = randomMultiplier * 10;

            // Appelle la méthode modifyScore de PointManager pour ajouter les points
            PointManager.modifyScore(player, pointsToAward);

            // Jouer le son "buy"
            Level level = player.level();
            if (!level.isClientSide()) {
                SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "buy"));
                if (sound != null) {
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
                }
            }
        }
    }
}