package me.cryo.zombierool.client;

import java.util.*;
import me.cryo.zombierool.core.system.WeaponImplementations.MeleeWeaponItem;
import me.cryo.zombierool.gameplay.MeleeMoves;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;

/** Local anticipation is reconciled once with the server; hits never replay a swing. */
@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class MeleeMotionClient {
    private record Motion(double start,int duration,int contact,int combo,boolean heavy,boolean predicted,String item) {}
    private static final Map<UUID,Motion> MOTIONS=new HashMap<>();
    private static double lastRequest=-1000,hitAt=-1000;
    private static boolean chargeSound;
    private static String item(AbstractClientPlayer p){return net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(p.getMainHandItem().getItem()).toString();}
    public static void attack(){
        var mc=Minecraft.getInstance();var p=mc.player;
        if(p==null||mc.screen!=null||p.isUsingItem()||p.isSpectator()||!p.isAlive()||me.cryo.zombierool.handlers.KeyInputHandler.isLocalPlayerDown||me.cryo.zombierool.gameplay.PortableEquipment.inHands(p)
            ||!(p.getMainHandItem().getItem() instanceof MeleeWeaponItem))return;
        double now=p.tickCount;var old=MOTIONS.get(p.getUUID());
        if(now-lastRequest<3)return;
        if(old!=null&&now<old.start+old.duration-3)return;
        lastRequest=now;
        NetworkHandler.INSTANCE.sendToServer(new C2SMeleeAttackPacket());
        if(old==null||now>=old.start+old.duration){
            var move=MeleeMoves.move(p.getMainHandItem(),p,false);
            int combo=old!=null&&old.item.equals(item(p))&&now<=old.start+old.duration+12?(old.combo+1)%3:0;
            MOTIONS.put(p.getUUID(),new Motion(now,move.duration(),move.contact(),combo,false,true,item(p)));
        }
    }
    public static void accept(S2CMeleeMotionPacket packet){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        var entity=mc.level.getPlayerByUUID(packet.player());if(!(entity instanceof AbstractClientPlayer p))return;
        if(packet.hit()){
            if(p==mc.player){hitAt=p.tickCount;ScreenShakeHandler.startShake(3,packet.heavy()?.3f:.12f,ScreenShakeHandler.ShakeType.IMPACT);}
            return;
        }
        var old=MOTIONS.get(packet.player());double start=p.tickCount;
        if(p==mc.player&&old!=null&&old.predicted&&old.item.equals(item(p))&&old.heavy==packet.heavy()&&start-old.start<=6)start=old.start;
        MOTIONS.put(packet.player(),new Motion(start,Math.max(4,packet.duration()),Math.max(1,packet.contact()),Math.floorMod(packet.combo(),3),packet.heavy(),false,item(p)));
    }
    /** x/y/z translation, x/y/z rotation in degrees, body twist in radians. */
    public static float[] pose(AbstractClientPlayer p,float partial){
        var s=MOTIONS.get(p.getUUID());float[] out=new float[7];
        if(s==null||!s.item.equals(item(p)))return out;
        double age=p.tickCount+partial-s.start;
        if(age<0||age>=s.duration)return out;
        boolean quick=MeleeMoves.quick(p.getMainHandItem()),two=MeleeMoves.twoHanded(p.getMainHandItem()),axe=MeleeMoves.kind(p.getMainHandItem()).equals("axe");
        return MeleeMotionCurve.sample((float)age,s.duration,s.contact,s.combo,s.heavy,quick,two,axe);
    }
    public static float charge(AbstractClientPlayer p,float partial){
        if(!p.isUsingItem()||p.getUsedItemHand()!=net.minecraft.world.InteractionHand.MAIN_HAND||!(p.getUseItem().getItem() instanceof MeleeWeaponItem)||MeleeMoves.kind(p.getUseItem()).equals("shield"))return 0;
        return Mth.clamp((p.getTicksUsingItem()+partial)/MeleeMoves.move(p.getUseItem(),p,true).charge(),0,1);
    }
    public static float recoil(AbstractClientPlayer p,float partial){float age=(float)(p.tickCount+partial-hitAt);return p==Minecraft.getInstance().player&&age>=0&&age<3?(float)Math.sin(age/3*Math.PI)*.025f:0;}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null){MOTIONS.clear();lastRequest=-1000;hitAt=-1000;return;}
        MOTIONS.entrySet().removeIf(row->{var p=mc.level.getPlayerByUUID(row.getKey());return p==null||p.tickCount>row.getValue().start+row.getValue().duration+20;});
        if(mc.screen!=null||mc.isPaused())return;
        if(mc.options.keyAttack.isDown())attack();
        float charge=charge(mc.player,0);
        if(charge>=1&&!chargeSound){mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.get(),.28f,1.4f);chargeSound=true;}
        if(charge==0)chargeSound=false;
    }
    @SubscribeEvent public static void hud(net.minecraftforge.client.event.RenderGuiEvent.Post e){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui||mc.screen!=null)return;
        float charge=charge(mc.player,e.getPartialTick());if(charge<=0)return;
        int x=mc.getWindow().getGuiScaledWidth()/2-15,y=mc.getWindow().getGuiScaledHeight()/2+16;
        e.getGuiGraphics().fill(x-1,y-1,x+31,y+3,0x99000000);
        e.getGuiGraphics().fill(x,y,x+Math.round(charge*30),y+2,charge>=1?0xffffd369:0xffdddddd);
    }
    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e){MOTIONS.clear();lastRequest=-1000;hitAt=-1000;chargeSound=false;}
    private MeleeMotionClient(){}
}
