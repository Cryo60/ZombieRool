package me.cryo.zombierool.event;

import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.client.model.Modelmannequin;
import me.cryo.zombierool.client.model.Modelwolf;
import me.cryo.zombierool.client.particle.*;
import me.cryo.zombierool.client.render.ZombieMoonRenderer;
import me.cryo.zombierool.client.renderer.*;
import me.cryo.zombierool.init.ZombieroolModBlockEntities;
import me.cryo.zombierool.init.ZombieroolModEntities;
import me.cryo.zombierool.init.ZombieroolModParticleTypes;

import me.cryo.zombierool.block.system.DefenseDoorSystem;
import me.cryo.zombierool.block.system.DefenseWallSystem;
import me.cryo.zombierool.block.entity.TraitorBlockEntity;
import me.cryo.zombierool.block.entity.DerWunderfizzBlockEntity;
import me.cryo.zombierool.block.system.BlindBuySystem;
import me.cryo.zombierool.block.system.BuyWallWeaponSystem;
import me.cryo.zombierool.block.system.MysteryBoxSystem;
import me.cryo.zombierool.block.system.ObstacleDoorSystem;
import me.cryo.zombierool.block.system.PerksSystem;
import me.cryo.zombierool.block.system.UniversalSpawnerSystem;
import me.cryo.zombierool.block.system.PackAPunchSystem;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModBusSubscriber {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(BlindBuySystem.MENU.get(), BlindBuySystem.BlindBuyManagerScreen::new);
            ItemBlockRenderTypes.setRenderLayer(BlindBuySystem.BLOCK.get(), RenderType.cutout());

            MenuScreens.register(BuyWallWeaponSystem.MENU.get(), BuyWallWeaponSystem.WallWeaponManagerScreen::new);
            ItemBlockRenderTypes.setRenderLayer(BuyWallWeaponSystem.BLOCK.get(), RenderType.cutout());

            MenuScreens.register(ObstacleDoorSystem.MENU.get(), ObstacleDoorSystem.ObstacleDoorManagerScreen::new);
            ItemBlockRenderTypes.setRenderLayer(ObstacleDoorSystem.BLOCK.get(), RenderType.cutout());

            MenuScreens.register(PerksSystem.MENU.get(), PerksSystem.PerksInterfaceScreen::new);
            ItemBlockRenderTypes.setRenderLayer(PerksSystem.BLOCK.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(PerksSystem.LEGACY_PERKS_LOWER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(PerksSystem.LEGACY_PERKS_UPPER.get(), RenderType.cutout());

            MenuScreens.register(UniversalSpawnerSystem.UNIVERSAL_SPAWNER_MENU.get(), UniversalSpawnerSystem.UniversalSpawnerManagerScreen::new);
            ItemBlockRenderTypes.setRenderLayer(UniversalSpawnerSystem.UNIVERSAL_SPAWNER_BLOCK.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(UniversalSpawnerSystem.LEGACY_ZOMBIE_BLOCK.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(UniversalSpawnerSystem.LEGACY_CRAWLER_BLOCK.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(UniversalSpawnerSystem.LEGACY_DOG_BLOCK.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(UniversalSpawnerSystem.LEGACY_PLAYER_BLOCK.get(), RenderType.translucent());

            ItemBlockRenderTypes.setRenderLayer(MysteryBoxSystem.MYSTERY_BOX.get(), RenderType.cutout());
            
            // --- NEW ---
            ItemBlockRenderTypes.setRenderLayer(DefenseWallSystem.MAIN_BLOCK.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(DefenseWallSystem.DUMMY_BLOCK.get(), RenderType.cutout());
        });
    }

    @SubscribeEvent
    public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(new ResourceLocation("overworld"), new ZombieMoonRenderer());
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(Modelwolf.LAYER_LOCATION, Modelwolf::createBodyLayer);
        event.registerLayerDefinition(Modelmannequin.LAYER_LOCATION, Modelmannequin::createBodyLayer);
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer((BlockEntityType<TraitorBlockEntity>)(Object)ZombieroolModBlockEntities.TRAITOR.get(), TraitorBlockRenderer::new);
        event.registerBlockEntityRenderer(me.cryo.zombierool.init.ZombieroolModExtraBlockEntities.DEFENSE_DOOR.get(), DefenseDoorSystem.DefenseDoorRenderer::new);
        
        event.registerBlockEntityRenderer(DefenseWallSystem.BE.get(), DefenseWallSystem.DefenseWallRenderer::new);
        
        event.registerBlockEntityRenderer(BlindBuySystem.BE.get(), BlindBuySystem.BlindBuyCabinetRenderer::new);
        event.registerBlockEntityRenderer(BuyWallWeaponSystem.BE.get(), BuyWallWeaponSystem.BuyWallWeaponRenderer::new);
        event.registerBlockEntityRenderer(ObstacleDoorSystem.BE.get(), ObstacleDoorSystem.ObstacleDoorBlockRenderer::new);
        event.registerBlockEntityRenderer(MysteryBoxSystem.MYSTERY_BOX_BE.get(), MysteryBoxSystem.MysteryBoxRenderer::new);
        event.registerBlockEntityRenderer((BlockEntityType<DerWunderfizzBlockEntity>)(Object)ZombieroolModBlockEntities.DER_WUNDERFIZZ.get(), DerWunderfizzRenderer::new);
        event.registerBlockEntityRenderer(PackAPunchSystem.BE.get(), PackAPunchSystem.PackAPunchRenderer::new);
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ZombieroolModParticleTypes.INSTAKILL.get(), InstakillParticle::provider);
        event.registerSpriteSet(ZombieroolModParticleTypes.MAXAMMO.get(), MaxammoParticle::provider);
        event.registerSpriteSet(ZombieroolModParticleTypes.NUKE.get(), NukeParticle::provider);
        event.registerSpriteSet(ZombieroolModParticleTypes.DOUBLE_POINTS.get(), DoublePointsParticle::provider);
        event.registerSpriteSet(ZombieroolModParticleTypes.CARPENTER.get(), CarpenterParticle::provider);
        event.registerSpriteSet(ZombieroolModParticleTypes.GOLD_RUSH.get(), GoldRushParticle::provider);
        event.registerSpriteSet(ZombieroolModParticleTypes.ZOMBIE_BLOOD.get(), ZombieBloodParticle::provider);
        event.registerSpriteSet(ZombieroolModParticleTypes.WISH.get(), WishParticle::provider);
        event.registerSpriteSet(ZombieroolModParticleTypes.ON_THE_HOUSE.get(), OnTheHouseParticle::provider);
        event.registerSpriteSet(ZombieroolModParticleTypes.BLACK_CROW.get(), BlackCrowParticle::provider); 
    }
}