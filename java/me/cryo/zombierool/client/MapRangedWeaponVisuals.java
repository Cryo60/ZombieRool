package me.cryo.zombierool.client;

import com.mojang.math.Axis;
import me.cryo.zombierool.core.system.WeaponSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.*;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;

/** The pack supplies pose curves; this controller only evaluates reload and recoil phases. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class MapRangedWeaponVisuals {
    static void attach(net.minecraft.client.player.AbstractClientPlayer player){
        var data=dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess.getPlayerAssociatedData(player);
        var id=new net.minecraft.resources.ResourceLocation("zombierool","map_ranged_weapon");
        if(data.get(id)==null){var pose=new Pose(player);data.set(id,pose);dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess.getPlayerAnimLayer(player).addAnimLayer(1110,pose);}
    }
    public static boolean accepts(ItemStack stack){if(!(stack.getItem() instanceof WeaponSystem.BaseGunItem gun)||gun instanceof me.cryo.zombierool.core.system.WeaponImplementations.MeleeWeaponItem||gun instanceof me.cryo.zombierool.item.MapBowItem)return false;String id=gun.getDefinition().id.replace("zombierool:","");return me.cryo.zombierool.gameplay.MapWeaponPacks.RESERVED.contains(id)||id.startsWith("stratholme_");}
    private static float reload(ItemStack stack,WeaponSystem.BaseGunItem gun,net.minecraft.world.entity.player.Player player){
        if(!stack.getOrCreateTag().getBoolean(WeaponSystem.BaseGunItem.TAG_IS_RELOADING))return 0;
        float total=gun.getDefinition().ammo.reload_time*(gun.isPackAPunched(stack)?gun.getDefinition().pap.reload_speed_mult:1);
        if(player!=null&&player.hasEffect(me.cryo.zombierool.init.ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get()))total*=.5f;
        float progress=net.minecraft.util.Mth.clamp(1-stack.getOrCreateTag().getInt(WeaponSystem.BaseGunItem.TAG_RELOAD_TIMER)/Math.max(1,total),0,1);
        return (float)Math.sin(progress*Math.PI);
    }
    private static float recoil(ItemStack stack,net.minecraft.world.entity.player.Player player,float partial){if(!stack.getOrCreateTag().contains(WeaponSystem.BaseGunItem.TAG_LAST_FIRE))return 0;float elapsed=player.level().getGameTime()-stack.getOrCreateTag().getLong(WeaponSystem.BaseGunItem.TAG_LAST_FIRE)+partial;return Math.max(0,1-elapsed/8);}
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST,receiveCanceled=true)
    public static void hand(net.minecraftforge.client.event.RenderHandEvent e){
        var mc=Minecraft.getInstance();if(mc.player==null||!accepts(mc.player.getMainHandItem())||me.cryo.zombierool.gameplay.PortableEquipment.inHands(mc.player))return;
        var stack=mc.player.getMainHandItem();var gun=(WeaponSystem.BaseGunItem)stack.getItem();var def=gun.getDefinition();boolean wand=def.weapon_kind.equals("wand");
        if(e.getHand()==net.minecraft.world.InteractionHand.OFF_HAND){if(!wand)e.setCanceled(true);return;}
        e.setCanceled(true);var pose=e.getPoseStack();var buffers=mc.renderBuffers().bufferSource();int side=mc.player.getMainArm()==net.minecraft.world.entity.HumanoidArm.RIGHT?1:-1;
        float load=reload(stack,gun,mc.player),kick=recoil(stack,mc.player,e.getPartialTick());pose.pushPose();pose.translate(side*.46,-.46-e.getEquipProgress()*.45,-.86);
        pose.translate(side*MapWeaponAnimation.value(def,"fpX",load,kick,0),MapWeaponAnimation.value(def,"fpY",load,kick,-.1f*load),MapWeaponAnimation.value(def,"fpZ",load,kick,.1f*load+.07f*kick));
        pose.mulPose(Axis.XP.rotationDegrees(MapWeaponAnimation.value(def,"fpRx",load,kick,45*load-8*kick)));
        pose.mulPose(Axis.YP.rotationDegrees(side*MapWeaponAnimation.value(def,"fpRy",load,kick,-25*load)));
        pose.mulPose(Axis.ZP.rotationDegrees(side*MapWeaponAnimation.value(def,"fpRz",load,kick,20*load)));
        float scale=net.minecraft.util.Mth.clamp(MapWeaponAnimation.value(def,"fpScale",load,kick,.82f),.1f,3);pose.scale(scale,scale,scale);
        mc.getItemRenderer().renderStatic(stack,ItemDisplayContext.NONE,e.getPackedLight(),net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,pose,buffers,mc.level,0);
        pose.pushPose();pose.translate(0,wand?-.23:-.34,wand?0:.28);pose.mulPose(Axis.XP.rotationDegrees(wand?0:-20));ScarletWeaponClient.grip().render(pose,buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(mc.player.getSkinTextureLocation())),e.getPackedLight(),net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);pose.popPose();
        if(!wand){pose.translate(0,-.17+.10*load,-.20+.20*load);pose.mulPose(Axis.XP.rotationDegrees(-60+20*load));pose.mulPose(Axis.ZP.rotationDegrees(-side*12));ScarletWeaponClient.grip().render(pose,buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(mc.player.getSkinTextureLocation())),e.getPackedLight(),net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);}
        pose.popPose();buffers.endBatch();
    }
    private static final class Pose implements IAnimation {
        private final net.minecraft.client.player.AbstractClientPlayer player;Pose(net.minecraft.client.player.AbstractClientPlayer p){player=p;}
        public boolean isActive(){return accepts(player.getMainHandItem())&&!ThirdPersonAnimHandler.isAnimationPlaying(player.getUUID())&&!me.cryo.zombierool.gameplay.PortableEquipment.inHands(player);}
        public void setupAnim(float delta){}
        public Vec3f get3DTransform(String bone,TransformType type,float delta,Vec3f original){
            if(type!=TransformType.ROTATION||!accepts(player.getMainHandItem()))return original;
            var stack=player.getMainHandItem();var gun=(WeaponSystem.BaseGunItem)stack.getItem();var def=gun.getDefinition();float load=reload(stack,gun,player),kick=recoil(stack,player,delta);boolean right=player.getMainArm()==net.minecraft.world.entity.HumanoidArm.RIGHT;float side=right?1:-1;
            if(bone.equals(right?"rightArm":"leftArm"))return new Vec3f(MapWeaponAnimation.value(def,"tpX",load,kick,-1.35f+.65f*load),side*MapWeaponAnimation.value(def,"tpY",load,kick,-.15f),side*MapWeaponAnimation.value(def,"tpZ",load,kick,-.05f));
            if(!def.weapon_kind.equals("wand")&&bone.equals(right?"leftArm":"rightArm"))return new Vec3f(-1.35f+.65f*load,side*(.55f-.25f*load),side*.05f);
            return original;
        }
    }
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool",value=net.minecraftforge.api.distmarker.Dist.CLIENT,bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
    public static final class Models {
        @net.minecraftforge.eventbus.api.SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(new net.minecraft.resources.ResourceLocation("zombierool","map_ranged_weapon"),1110,Pose::new));}
    }
    private MapRangedWeaponVisuals(){}
}
