package me.cryo.zombierool.client;

import me.cryo.zombierool.core.system.WeaponSystem;

/** Per-weapon pose curves, in degrees for first person and radians for player bones. */
public final class MapWeaponAnimation {
    public static float value(WeaponSystem.Definition def,String channel,float wind,float strike,float fallback){
        if(def.animation==null)return fallback;var curve=def.animation.get(channel);if(curve==null||curve.length!=3)return fallback;
        for(float n:curve)if(!Float.isFinite(n)||Math.abs(n)>360)return fallback;
        return curve[0]+curve[1]*wind+curve[2]*strike;
    }
    public static boolean authored(WeaponSystem.Definition def){return def.animation!=null&&def.animation.containsKey("fpScale");}
    private MapWeaponAnimation(){}
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool",value=net.minecraftforge.api.distmarker.Dist.CLIENT,bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
    public static final class Models {
        @net.minecraftforge.eventbus.api.SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->{
            for(var item:net.minecraftforge.registries.ForgeRegistries.ITEMS.getValues())if(item instanceof WeaponSystem.BaseGunItem){
                if(item instanceof me.cryo.zombierool.item.MapBowItem){
                    net.minecraft.client.renderer.item.ItemProperties.register(item,new net.minecraft.resources.ResourceLocation("pulling"),(stack,level,entity,seed)->entity!=null&&entity.getUseItem()==stack?1:0);
                    net.minecraft.client.renderer.item.ItemProperties.register(item,new net.minecraft.resources.ResourceLocation("pull"),(stack,level,entity,seed)->entity!=null&&entity.getUseItem()==stack?(stack.getUseDuration()-entity.getUseItemRemainingTicks())/(float)Math.max(6,((WeaponSystem.BaseGunItem)item).getDefinition().draw_ticks):0);
                }
                net.minecraft.client.renderer.item.ItemProperties.register(item,new net.minecraft.resources.ResourceLocation("zombierool","loaded"),(stack,level,entity,seed)->!stack.getOrCreateTag().getBoolean(WeaponSystem.BaseGunItem.TAG_IS_RELOADING)&&((WeaponSystem.BaseGunItem)item).getAmmo(stack)>0?1:0);
            }
            WeaponSystem.Loader.LOADED_DEFINITIONS.keySet().removeIf(id->!me.cryo.zombierool.gameplay.MapWeaponPacks.available(id));
        });}
    }
}
