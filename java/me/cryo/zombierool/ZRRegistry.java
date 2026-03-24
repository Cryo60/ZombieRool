package me.cryo.zombierool.core.registry;

import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.item.BowieKnifeItem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ZRRegistry {
    public static final List<WeaponSystem.BaseGunItem> GUN_ITEMS = new ArrayList<>();
    private static ResourceKey<CreativeModeTab> TAB_KEY;

    public static Item ANIM_BOTTLE;
    public static Item ANIM_KNIFE;
    public static Item BOWIE_KNIFE;
    public static Item ANIM_GRENADE;
    public static Item ANIM_MOLOTOV;

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(ForgeRegistries.Keys.ITEMS)) {
            WeaponSystem.Loader.loadWeapons();

            event.register(ForgeRegistries.Keys.ITEMS, helper -> {
                for (var entry : WeaponSystem.Loader.LOADED_DEFINITIONS.entrySet()) {
                    WeaponSystem.BaseGunItem gunItem = WeaponSystem.Loader.createWeapon(entry.getValue());
                    ResourceLocation location = new ResourceLocation(ZombieroolMod.MODID, entry.getKey());
                    helper.register(location, gunItem);
                    GUN_ITEMS.add(gunItem);
                }

                ANIM_BOTTLE = new Item(new Item.Properties());
                ANIM_KNIFE = new Item(new Item.Properties());
                BOWIE_KNIFE = new BowieKnifeItem(new Item.Properties());
                ANIM_GRENADE = new Item(new Item.Properties());
                ANIM_MOLOTOV = new Item(new Item.Properties());

                helper.register(new ResourceLocation(ZombieroolMod.MODID, "anim_bottle"), ANIM_BOTTLE);
                helper.register(new ResourceLocation(ZombieroolMod.MODID, "anim_knife"), ANIM_KNIFE);
                helper.register(new ResourceLocation(ZombieroolMod.MODID, "bowie_knife"), BOWIE_KNIFE);
                helper.register(new ResourceLocation(ZombieroolMod.MODID, "anim_grenade"), ANIM_GRENADE);
                helper.register(new ResourceLocation(ZombieroolMod.MODID, "anim_molotov"), ANIM_MOLOTOV);
            });
        }

        if (event.getRegistryKey().equals(Registries.CREATIVE_MODE_TAB)) {
            TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation(ZombieroolMod.MODID, "zombie_arsenal"));

            event.register(Registries.CREATIVE_MODE_TAB, helper -> {
                CreativeModeTab tab = CreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.zombierool.zombie_arsenal"))
                        .icon(() -> !GUN_ITEMS.isEmpty() ? new ItemStack(GUN_ITEMS.get(0)) : new ItemStack(Items.IRON_AXE))
                        .withSearchBar() 
                        .build();
                helper.register(TAB_KEY, tab);
            });
        }
    }

    @SubscribeEvent
    public static void buildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == TAB_KEY) {
            for (Item item : GUN_ITEMS) {
                event.accept(item);
            }
            
            for (Item item : GUN_ITEMS) {
                if (item instanceof me.cryo.zombierool.api.IPackAPunchable papItem) {
                    ItemStack papStack = new ItemStack(item);
                    if (item instanceof me.cryo.zombierool.api.IReloadable reloadable) {
                        reloadable.initializeIfNeeded(papStack);
                    }
                    papItem.applyPackAPunch(papStack);
                    event.accept(papStack);
                }
            }
            
            event.accept(BOWIE_KNIFE);
            event.accept(me.cryo.zombierool.core.registry.ZRThrowableRegistry.GRENADE_ITEM.get());
            event.accept(me.cryo.zombierool.core.registry.ZRThrowableRegistry.MOLOTOV_ITEM.get());
        }
    }
}
