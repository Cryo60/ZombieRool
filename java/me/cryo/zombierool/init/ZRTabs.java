package me.cryo.zombierool.init;

import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.core.registry.ZRBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ZRTabs {
    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.CREATIVE_MODE_TAB)) {
            event.register(Registries.CREATIVE_MODE_TAB, new net.minecraft.resources.ResourceLocation(ZombieroolMod.MODID, "zr_deco"), () -> CreativeModeTab.builder()
                .title(Component.translatable("item_group.zombierool.zr_deco"))
                .icon(() -> {
                    var item = ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(ZombieroolMod.MODID, "balsatic_stone"));
                    return item != null ? new ItemStack(item) : ItemStack.EMPTY;
                })
                .displayItems((parameters, tabData) -> {
                    ZRBlocks.ITEM_IDS.forEach(loc -> {
                        var item = ForgeRegistries.ITEMS.getValue(loc);
                        if (item != null) {
                            tabData.accept(item);
                        }
                    });
                }).withSearchBar().build());
        }
    }
}