package me.cryo.zombierool.core.registry;

import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.item.throwable.Grenade;
import me.cryo.zombierool.item.throwable.Molotov;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ZRThrowableRegistry {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ZombieroolMod.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ZombieroolMod.MODID);

    public static final RegistryObject<Item> GRENADE_ITEM = ITEMS.register("grenade", Grenade.GrenadeItem::new);
    public static final RegistryObject<Item> MOLOTOV_ITEM = ITEMS.register("molotov", Molotov.MolotovItem::new);

    public static final RegistryObject<EntityType<Grenade.GrenadeEntity>> GRENADE_ENTITY = ENTITIES.register("grenade",
            () -> EntityType.Builder.<Grenade.GrenadeEntity>of(Grenade.GrenadeEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("grenade"));

    public static final RegistryObject<EntityType<Molotov.MolotovEntity>> MOLOTOV_ENTITY = ENTITIES.register("molotov",
            () -> EntityType.Builder.<Molotov.MolotovEntity>of(Molotov.MolotovEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("molotov"));

    static {
        var bus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        ENTITIES.register(bus);
    }

    @Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientRenderers {
        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(GRENADE_ENTITY.get(), ThrownItemRenderer::new);
            event.registerEntityRenderer(MOLOTOV_ENTITY.get(), ThrownItemRenderer::new);
        }
    }
}