package me.cryo.zombierool.client;

import me.cryo.zombierool.entity.SpecialEnemyEntity;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class NapalmMinionOverlay {
    public static float intensity(net.minecraft.world.entity.player.Player player){
        if(player==null||!player.isAlive()||player.isCreative()||player.isSpectator())return 0;
        double heat=0;
        for(var mob:player.level().getEntitiesOfClass(SpecialEnemyEntity.class,player.getBoundingBox().inflate(32),e->e.isNapalm()&&e.isAlive()&&!e.isInWater()))if(player.hasLineOfSight(mob))heat=Math.max(heat,1-player.distanceTo(mob)/mob.heatRadius());
        for(var fire:player.level().getEntitiesOfClass(me.cryo.zombierool.entity.NapalmFireEntity.class,player.getBoundingBox().inflate(4)))if(player.position().subtract(fire.position()).horizontalDistanceSqr()<=16&&Math.abs(player.getY()-fire.getY())<=1.2)heat=Math.max(heat,.83);
        return (float)Math.sqrt(Math.max(0,heat));
    }
    @SubscribeEvent public static void render(net.minecraftforge.client.event.RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();float heat=intensity(mc.player);if(heat<=0)return;
        renderHeat(event.getGuiGraphics(),heat,mc.level.getGameTime()+mc.getFrameTime());
    }
    public static void renderHeat(net.minecraft.client.gui.GuiGraphics gui,float heat,float time){
        var mc=Minecraft.getInstance();renderHeat(gui,heat,time,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
    }
    public static void renderHeat(net.minecraft.client.gui.GuiGraphics gui,float heat,float time,int w,int h){
        gui.pose().pushPose();gui.pose().translate(0,0,550);
        int band=Math.max(24,(int)(h*.32f)),alpha=(int)(heat*180);int edge=(alpha<<24)|0xEA5010;
        gui.fillGradient(0,0,w,band,edge,0x00EA5010);gui.fillGradient(0,h-band,w,h,0x00EA5010,edge);
        var flame=net.minecraft.client.resources.model.ModelBakery.FIRE_1.sprite();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();gui.setColor(1,1,1,Math.min(.95f,.25f+heat*.7f));
        int width=Math.max(48,w/7);
        for(int x=-width/2;x<w;x+=width*3/4){int height=(int)(band*(.8+.25*Math.sin(x*.04+time*.12))*(.35+heat));gui.blit(x,h-height,0,width,height,flame);}
        for(int y=0;y<h;y+=width){gui.blit(-width/2,y,0,width,(int)(width*1.4),flame);gui.blit(w-width/2,y,0,width,(int)(width*1.4),flame);}
        gui.setColor(1,1,1,1);gui.pose().popPose();
    }
}
