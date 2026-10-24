package me.cryo.zombierool.core.registry;

import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.item.throwable.Grenade;
import me.cryo.zombierool.item.throwable.Molotov;
import me.cryo.zombierool.item.throwable.Stielhandgranate;
import me.cryo.zombierool.item.throwable.MonkeyBomb;
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


    public static final RegistryObject<Item> C4_ITEM=selector("c4",me.cryo.zombierool.gameplay.EquipmentSystem.Kind.C4);
    public static final RegistryObject<Item> CLAYMORE_ITEM=selector("claymore",me.cryo.zombierool.gameplay.EquipmentSystem.Kind.CLAYMORE);
    public static final RegistryObject<Item> BETTY_ITEM=selector("bouncing_betty",me.cryo.zombierool.gameplay.EquipmentSystem.Kind.BETTY);
    public static final RegistryObject<Item> FLASH_ITEM=selector("flash",me.cryo.zombierool.gameplay.EquipmentSystem.Kind.FLASH);
    public static final RegistryObject<Item> PHEROMONE_ITEM=selector("pheromone",me.cryo.zombierool.gameplay.EquipmentSystem.Kind.PHEROMONE);
    public static final RegistryObject<Item> SEMTEX_ITEM=selector("semtex",me.cryo.zombierool.gameplay.EquipmentSystem.Kind.SEMTEX);
    public static final RegistryObject<Item> C4_DETONATOR=ITEMS.register("anim_c4_detonator",()->new Item(new Item.Properties()));
    private static RegistryObject<Item> selector(String name,me.cryo.zombierool.gameplay.EquipmentSystem.Kind kind){return ITEMS.register(name,()->new me.cryo.zombierool.gameplay.EquipmentSystem.LoadoutItem(kind));}
    public static Item loadoutItem(me.cryo.zombierool.gameplay.EquipmentSystem.Kind kind){return switch(kind){case C4->C4_ITEM.get();case CLAYMORE->CLAYMORE_ITEM.get();case BETTY->BETTY_ITEM.get();case FLASH->FLASH_ITEM.get();case PHEROMONE->PHEROMONE_ITEM.get();case SEMTEX->SEMTEX_ITEM.get();};}
    public static final RegistryObject<EntityType<me.cryo.zombierool.gameplay.EquipmentEntity>> EQUIPMENT_ENTITY=ENTITIES.register("equipment",()->EntityType.Builder.<me.cryo.zombierool.gameplay.EquipmentEntity>of(me.cryo.zombierool.gameplay.EquipmentEntity::new,MobCategory.MISC).sized(.3f,.2f).clientTrackingRange(8).updateInterval(1).build("equipment"));

    public static final RegistryObject<Item> GRENADE_ITEM = ITEMS.register("grenade", Grenade.GrenadeItem::new);
    public static final RegistryObject<Item> MOLOTOV_ITEM = ITEMS.register("molotov", Molotov.MolotovItem::new);
    public static final RegistryObject<Item> STIELHANDGRANATE_ITEM = ITEMS.register("stielhandgranate", Stielhandgranate.StielhandgranateItem::new);
    public static final RegistryObject<Item> MONKEY_BOMB_ITEM = ITEMS.register("monkey_bomb", MonkeyBomb.MonkeyBombItem::new);

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

    public static final RegistryObject<EntityType<Stielhandgranate.StielhandgranateEntity>> STIELHANDGRANATE_ENTITY = ENTITIES.register("stielhandgranate",
            () -> EntityType.Builder.<Stielhandgranate.StielhandgranateEntity>of(Stielhandgranate.StielhandgranateEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("stielhandgranate"));

    public static final RegistryObject<EntityType<MonkeyBomb.MonkeyBombEntity>> MONKEY_BOMB_ENTITY = ENTITIES.register("monkey_bomb",
            () -> EntityType.Builder.<MonkeyBomb.MonkeyBombEntity>of(MonkeyBomb.MonkeyBombEntity::new, MobCategory.MISC)
                    .sized(0.35f, 0.35f)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("monkey_bomb"));

    static {
        var bus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        ENTITIES.register(bus);
    }

    @Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientRenderers {
        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(EQUIPMENT_ENTITY.get(), me.cryo.zombierool.client.EquipmentRenderer::new);
            event.registerEntityRenderer(GRENADE_ENTITY.get(), ThrownItemRenderer::new);
            event.registerEntityRenderer(MOLOTOV_ENTITY.get(), ThrownItemRenderer::new);
            event.registerEntityRenderer(STIELHANDGRANATE_ENTITY.get(), ThrownItemRenderer::new);
            event.registerEntityRenderer(MONKEY_BOMB_ENTITY.get(), ThrownItemRenderer::new);
        }
    }
}
