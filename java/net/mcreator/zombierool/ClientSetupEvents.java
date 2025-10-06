package net.mcreator.zombierool.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.player.Player; // NOUVEAU: Import pour Player

import net.mcreator.zombierool.block.ObstacleDoorBlock; // Assurez-vous d'importer votre ObstacleDoorBlock
import net.mcreator.zombierool.network.ObstacleDoorCopyBlockPacket;
import net.mcreator.zombierool.network.NetworkHandler; // Importez votre NetworkHandler

@Mod.EventBusSubscriber(modid = "zombierool", value = Dist.CLIENT)
public class ClientSetupEvents {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // NOUVEAU: Récupérer le joueur à partir de event.getEntity()
        Player player = (Player) event.getEntity();

        // S'assurer que c'est le joueur local qui interagit (côté client)
        // Utilise le 'player' casté
        if (event.getHand() == InteractionHand.MAIN_HAND && player.isCreative() && player.isSecondaryUseActive()) {
            Level level = event.getLevel();
            BlockPos clickedPos = event.getPos();
            
            // Vérifier si le bloc cliqué est un ObstacleDoorBlock
            if (level.getBlockState(clickedPos).getBlock() instanceof ObstacleDoorBlock) {
                ItemStack heldItem = player.getItemInHand(event.getHand()); // Utilise le 'player' casté
                
                // Vérifier si le joueur tient un BlockItem qui n'est PAS un ObstacleDoorBlock
                if (!heldItem.isEmpty() && heldItem.getItem() instanceof BlockItem blockItem) {
                    Block blockToCopy = blockItem.getBlock();
                    
                    if (!(blockToCopy instanceof ObstacleDoorBlock)) {
                        // Empêcher l'action par défaut du jeu
                        event.setCanceled(true); 
                        
                        // Envoyer le paquet au serveur
                        NetworkHandler.INSTANCE.sendToServer(new ObstacleDoorCopyBlockPacket(clickedPos, blockToCopy));
                        System.out.println("DEBUG CLIENT: Paquet ObstacleDoorCopyBlockPacket envoyé au serveur.");
                    } else {
                        System.out.println("DEBUG CLIENT: Le bloc tenu est un ObstacleDoorBlock. Action annulée côté client.");
                    }
                }
            }
        }
    }
}
