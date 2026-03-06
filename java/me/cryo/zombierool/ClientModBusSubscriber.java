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
import me.cryo.zombierool.block.entity.TraitorBlockEntity;
import me.cryo.zombierool.block.entity.BuyWallWeaponBlockEntity;
import me.cryo.zombierool.block.entity.ObstacleDoorBlockEntity;
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
        event.registerEntityRenderer(ZombieroolModEntities.ZOMBIE.get(), ZombieRenderer::new);
        event.registerEntityRenderer(ZombieroolModEntities.HELLHOUND.get(), HellhoundRenderer::new);
        event.registerEntityRenderer(ZombieroolModEntities.CRAWLER.get(), CrawlerRenderer::new);
        event.registerEntityRenderer(ZombieroolModEntities.WHITE_KNIGHT.get(), WhiteKnightRenderer::new);
        event.registerEntityRenderer(ZombieroolModEntities.DUMMY.get(), DummyRenderer::new);

        event.registerBlockEntityRenderer((BlockEntityType<TraitorBlockEntity>)(Object)ZombieroolModBlockEntities.TRAITOR.get(), TraitorBlockRenderer::new);
        event.registerBlockEntityRenderer((BlockEntityType<BuyWallWeaponBlockEntity>)(Object)ZombieroolModBlockEntities.BUY_WALL_WEAPON.get(), BuyWallWeaponRenderer::new);
        event.registerBlockEntityRenderer((BlockEntityType<ObstacleDoorBlockEntity>)(Object)ZombieroolModBlockEntities.OBSTACLE_DOOR.get(), ObstacleDoorBlockRenderer::new);
        event.registerBlockEntityRenderer(me.cryo.zombierool.init.ZombieroolModExtraBlockEntities.DEFENSE_DOOR.get(), DefenseDoorSystem.DefenseDoorRenderer::new);
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
        event.registerSpriteSet(ZombieroolModParticleTypes.BLACK_CROW.get(), BlackCrowParticle::provider); // ICI AUSSI
    }
}