package me.cryo.zombierool.client;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool",bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class MapActorCatalogue {
    private static boolean geist,thief;
    public static boolean replaces(String path){return geist&&path.equals("crawler_spawn_egg")||thief&&path.equals("hellhound_spawn_egg");}
    private static CompoundTag names=new CompoundTag(),nameKeys=new CompoundTag();
    public static void accept(CompoundTag value,CompoundTag keys,CompoundTag variants){geist=variants.getString("crawlerVariant").equals("geist");thief=variants.getString("specialWaveEnemy").equals("pentagon_thief");names=value.copy();nameKeys=keys.copy();var mc=net.minecraft.client.Minecraft.getInstance();if(mc.level!=null){var parameters=new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(mc.level.enabledFeatures(),mc.player!=null&&mc.player.canUseGameMasterBlocks(),mc.level.registryAccess());me.cryo.zombierool.init.ZombieroolModTabs.ZB_RCT.get().buildContents(parameters);}}
    public static int eggColor(net.minecraft.world.item.ItemStack stack,int layer){String id=stack.hasTag()?stack.getTag().getString("MapActor"):"";float hue=(float)Math.floorMod(id.hashCode()*0x9E3779B9,360)/360f;return net.minecraft.util.Mth.hsvToRgb((hue+(layer==0?0:.47f))%1,layer==0?.65f:.8f,layer==0?.85f:.38f);}
    @SubscribeEvent public static void colors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event){event.register((stack,layer)->eggColor(stack,layer),me.cryo.zombierool.block.system.UniversalSpawnerSystem.ACTOR_EGG_ITEM.get());}
    @SubscribeEvent public static void creative(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event){
        if(event.getTabKey()!=me.cryo.zombierool.init.ZombieroolModTabs.ZB_RCT.getKey())return;
        // Native eggs already cover these four identities. Expose every other built-in
        // enemy once, including bosses, even on maps without custom actor definitions.
        for(String id:me.cryo.zombierool.gameplay.MapActorTemplates.BUILTINS){
            if(java.util.Set.of("builtin_zombie","builtin_crawler","builtin_hellhound","builtin_white_knight").contains(id))continue;
            var profile=me.cryo.zombierool.gameplay.MapActorTemplates.builtin(id);
            event.accept(me.cryo.zombierool.item.MapActorSpawnEggItem.egg(id,profile.getString("name"),profile.getString("nameKey")));
        }
        for(String id:new java.util.TreeSet<>(names.getAllKeys()))if(!me.cryo.zombierool.gameplay.MapActorTemplates.BUILTINS.contains(id))event.accept(me.cryo.zombierool.item.MapActorSpawnEggItem.egg(id,names.getString(id),nameKeys.getString(id)));
    }
}
