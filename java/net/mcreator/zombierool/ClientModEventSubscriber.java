package net.mcreator.zombierool.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.mcreator.zombierool.init.ZombieroolModBlockEntities;
import net.mcreator.zombierool.client.renderer.TraitorBlockRenderer;
import net.mcreator.zombierool.client.renderer.BuyWallWeaponRenderer;
// Nouveaux imports pour ObstacleDoorBlock
import net.mcreator.zombierool.client.renderer.ObstacleDoorBlockRenderer; // Import ajouté
import net.mcreator.zombierool.block.entity.ObstacleDoorBlockEntity; // Import ajouté

import net.mcreator.zombierool.block.entity.TraitorBlockEntity;
import net.mcreator.zombierool.block.entity.BuyWallWeaponBlockEntity;

@Mod.EventBusSubscriber(modid = "zombierool",
                        bus = Mod.EventBusSubscriber.Bus.MOD,
                        value = Dist.CLIENT)
public class ClientModEventSubscriber {

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Enregistrement du renderer pour TraitorBlockEntity
        BlockEntityType<TraitorBlockEntity> traitorType =
            (BlockEntityType<TraitorBlockEntity>)(Object)ZombieroolModBlockEntities.TRAITOR.get();
        event.registerBlockEntityRenderer(traitorType, TraitorBlockRenderer::new);

        // Enregistrement du renderer pour BuyWallWeaponBlockEntity
        BlockEntityType<BuyWallWeaponBlockEntity> buyWallType =
            (BlockEntityType<BuyWallWeaponBlockEntity>)(Object)ZombieroolModBlockEntities.BUY_WALL_WEAPON.get();
        event.registerBlockEntityRenderer(buyWallType, BuyWallWeaponRenderer::new);

        // NOUVEAU : Enregistrement du renderer pour ObstacleDoorBlockEntity
        BlockEntityType<ObstacleDoorBlockEntity> obstacleDoorType =
            (BlockEntityType<ObstacleDoorBlockEntity>)(Object)ZombieroolModBlockEntities.OBSTACLE_DOOR.get();
        event.registerBlockEntityRenderer(obstacleDoorType, ObstacleDoorBlockRenderer::new);
    }
}
