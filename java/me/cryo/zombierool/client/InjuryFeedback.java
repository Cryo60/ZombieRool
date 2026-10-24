package me.cryo.zombierool.client;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cryo.zombierool.gameplay.WaveManager;
import me.cryo.zombierool.init.ZombieroolModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Health feedback is independent of the optional numeric health display. */
@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class InjuryFeedback {
    private static final ResourceLocation BLOOD=new ResourceLocation("zombierool","textures/screens/blood_screen.png");
    private static net.minecraft.client.player.LocalPlayer owner;
    private static float previousHealth,previousAbsorption,intensity,impact;
    private static int previousHurt,lastImpact=-100;
    private static volatile float critical;
    private static Loop heartbeat,ringing,breathing;
    private InjuryFeedback(){}
    public static boolean active(){var p=Minecraft.getInstance().player;return p!=null&&p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&WaveManager.isGameRunning();}
    public static float severity(float health,float maximum){return Mth.clamp((1-health/Math.max(1,maximum))/.85f,0,1);}
    public static int audioStep(){return active()?Mth.clamp((int)Math.ceil(critical*4),0,4):0;}
    public static void reset(){if(heartbeat!=null)heartbeat.finish();if(ringing!=null)ringing.finish();if(breathing!=null)breathing.finish();heartbeat=null;ringing=null;breathing=null;owner=null;intensity=impact=critical=0;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){reset();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        if(!active()){reset();return;}
        var mc=Minecraft.getInstance();var p=mc.player;
        if(owner!=p){reset();owner=p;previousHealth=p.getHealth();previousAbsorption=p.getAbsorptionAmount();previousHurt=p.hurtTime;lastImpact=-100;}
        if((p.getHealth()<previousHealth-.001f||p.getAbsorptionAmount()<previousAbsorption-.001f||p.hurtTime>previousHurt)&&p.tickCount-lastImpact>2){lastImpact=p.tickCount;impact=1;ScreenShakeHandler.startShake(8,1.15f,ScreenShakeHandler.ShakeType.IMPACT);}
        previousHealth=p.getHealth();previousAbsorption=p.getAbsorptionAmount();previousHurt=p.hurtTime;impact=Math.max(0,impact-.075f);
        float target=severity(p.getHealth(),p.getMaxHealth());intensity=Mth.lerp(target>intensity?.4f:.15f,intensity,target);
        if(intensity<.002f)intensity=0;
        critical=Mth.clamp((.5f-p.getHealth()/Math.max(1,p.getMaxHealth()))/.4f,0,1);
        if(critical>.02f&&mc.options.getSoundSourceVolume(SoundSource.MASTER)>0){
            if(heartbeat==null||heartbeat.isStopped()){heartbeat=new Loop(ZombieroolModSounds.INJURY_HEARTBEAT.get(),0);mc.getSoundManager().play(heartbeat);}
            if(ringing==null||ringing.isStopped()){ringing=new Loop(ZombieroolModSounds.INJURY_RINGING.get(),1);mc.getSoundManager().play(ringing);}
            if(critical>.6f){if(breathing==null||breathing.isStopped()){breathing=new Loop(ZombieroolModSounds.INJURY_BREATHING.get(),2);mc.getSoundManager().play(breathing);}}else if(breathing!=null){breathing.finish();breathing=null;}
        }else{if(breathing!=null)breathing.finish();breathing=null;if(heartbeat!=null)heartbeat.finish();if(ringing!=null)ringing.finish();heartbeat=null;ringing=null;}
    }
    @SubscribeEvent public static void draw(RenderGuiEvent.Pre event){
        if(!active()||intensity<=.002f)return;
        var mc=Minecraft.getInstance();var g=event.getGuiGraphics();int w=event.getWindow().getGuiScaledWidth(),h=event.getWindow().getGuiScaledHeight();
        float pulse=.96f+.04f*(float)Math.sin(mc.player.tickCount*.2f);
        g.fill(0,0,w,h,((int)(intensity*27)<<24));
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        try{g.setColor(.68f,.43f,.43f,Math.min(.84f,(intensity*.64f+impact*.17f)*pulse));g.blit(BLOOD,0,0,0,0,w,h,w,h);}
        finally{g.setColor(1,1,1,1);RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();}
    }
    private static final class Loop extends AbstractTickableSoundInstance {
        final int kind;
        Loop(SoundEvent event,int kind){super(event,SoundSource.MASTER,RandomSource.create());this.kind=kind;relative=true;attenuation=Attenuation.NONE;looping=true;delay=0;updateVolume();}
        void updateVolume(){volume=(kind==0?.28f:kind==1?.10f:.18f)*critical;pitch=kind==0?1+critical*.12f:1;}
        void finish(){stop();}
        @Override public void tick(){if(!active()||critical<=.02f)stop();else updateVolume();}
    }
}
