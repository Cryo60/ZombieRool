package me.cryo.zombierool.client;

import me.cryo.zombierool.block.system.AtmosphereZoneBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class AtmosphereZoneFeedback {
    private static final List<net.minecraft.world.level.block.entity.BlockEntity> ZONES=new ArrayList<>();
    private static final org.joml.Matrix4f projection=new org.joml.Matrix4f(),view=new org.joml.Matrix4f();
    private static final MultiBufferSource.BufferSource outlineBuffers=MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(4096));
    private static boolean captured;
    public static boolean needsDepth(){return editing();}
    private static boolean editing(){var mc=Minecraft.getInstance();if(!visible()||mc.level==null)return false;
        var main=mc.player.getMainHandItem();var off=mc.player.getOffhandItem();
        boolean holding=main.getItem() instanceof net.minecraft.world.item.BlockItem a&&(a.getBlock() instanceof AtmosphereZoneBlock||a.getBlock() instanceof me.cryo.zombierool.block.system.MusicVolume.VolumeBlock)||off.getItem() instanceof net.minecraft.world.item.BlockItem b&&(b.getBlock() instanceof AtmosphereZoneBlock||b.getBlock() instanceof me.cryo.zombierool.block.system.MusicVolume.VolumeBlock);
        return holding||mc.hitResult instanceof BlockHitResult hit&&(mc.level.getBlockEntity(hit.getBlockPos()) instanceof AtmosphereZoneBlock.Zone||mc.level.getBlockEntity(hit.getBlockPos()) instanceof me.cryo.zombierool.block.system.MusicVolume.VolumeEntity);
    }
    private static long lastScan=Long.MIN_VALUE;private static net.minecraft.client.multiplayer.ClientLevel owner;
    private static boolean visible(){var mc=Minecraft.getInstance();return mc.player!=null&&mc.player.isCreative()&&!TechnicalBlockClient.shouldBeInvisible();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){ZONES.clear();owner=null;lastScan=Long.MIN_VALUE;}
    @SubscribeEvent public static void capture(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;captured=false;if(!editing())return;
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera();projection.set(e.getProjectionMatrix());view.rotationX((float)Math.toRadians(camera.getXRot())).rotateY((float)Math.toRadians(camera.getYRot()+180));captured=true;
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void render(RenderGuiEvent.Pre e){
        if(!captured||!editing())return;captured=false;var mc=Minecraft.getInstance();
        if(owner!=mc.level||lastScan==Long.MIN_VALUE||mc.level.getGameTime()-lastScan>=10){owner=mc.level;lastScan=owner.getGameTime();ZONES.clear();int cx=mc.player.chunkPosition().x,cz=mc.player.chunkPosition().z;
            for(int x=cx-4;x<=cx+4;x++)for(int z=cz-4;z<=cz+4;z++){var chunk=owner.getChunkSource().getChunk(x,z,false);if(chunk!=null)for(var be:chunk.getBlockEntities().values())if(be instanceof AtmosphereZoneBlock.Zone||be instanceof me.cryo.zombierool.block.system.MusicVolume.VolumeEntity)ZONES.add(be);}
        }
        var camera=mc.gameRenderer.getMainCamera().getPosition();var pose=new com.mojang.blaze3d.vertex.PoseStack();pose.translate(-camera.x,-camera.y,-camera.z);
        var previous=com.mojang.blaze3d.systems.RenderSystem.getShader();int[] previousTextures=new int[4];for(int i=0;i<4;i++)previousTextures[i]=com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(i);var stack=com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();com.mojang.blaze3d.systems.RenderSystem.backupProjectionMatrix();stack.pushPose();stack.setIdentity();stack.mulPoseMatrix(view);com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(projection,com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
        try(var state=new MapAtmosphereClient.RenderState()){
        mc.getMainRenderTarget().bindWrite(false);com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1);
        AmbientSceneryEffects.prepareDepth();
        var lines=outlineBuffers.getBuffer(AmbientSceneryEffects.material(new ResourceLocation("zombierool","textures/scenery/tracer_white.png"),false));
        for(var zone:ZONES){if(zone.isRemoved())continue;var config=zone instanceof AtmosphereZoneBlock.Zone atmosphere?atmosphere.config():((me.cryo.zombierool.block.system.MusicVolume.VolumeEntity)zone).config();boolean vacuum=config.getBoolean("vacuum"),enabled=config.getBoolean("enabled");float r=!enabled?.5f:vacuum?1f:.2f,g=!enabled?.5f:vacuum?.45f:1f,b=!enabled?.5f:vacuum?.15f:.8f;
            if(zone instanceof me.cryo.zombierool.block.system.MusicVolume.VolumeEntity){r=.65f;g=.35f;b=1;}
            box(pose,lines,(zone instanceof AtmosphereZoneBlock.Zone atmosphere?atmosphere.bounds():me.cryo.zombierool.block.system.MusicVolume.bounds(zone.getBlockPos(),config)).inflate(.004),camera,r,g,b,.8f);box(pose,lines,new net.minecraft.world.phys.AABB(zone.getBlockPos()).inflate(.02),camera,r,g,b,1f);
        }
        outlineBuffers.endBatch();
        }finally{stack.popPose();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();com.mojang.blaze3d.systems.RenderSystem.restoreProjectionMatrix();com.mojang.blaze3d.systems.RenderSystem.setShader(()->previous);for(int i=0;i<4;i++)com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(i,previousTextures[i]);}
    }
    private static void box(com.mojang.blaze3d.vertex.PoseStack p,com.mojang.blaze3d.vertex.VertexConsumer out,net.minecraft.world.phys.AABB b,net.minecraft.world.phys.Vec3 camera,float r,float g,float blue,float alpha){
        var points=new net.minecraft.world.phys.Vec3[8];for(int i=0;i<8;i++)points[i]=new net.minecraft.world.phys.Vec3((i&1)==0?b.minX:b.maxX,(i&2)==0?b.minY:b.maxY,(i&4)==0?b.minZ:b.maxZ);
        for(int i=0;i<8;i++)for(int bit:new int[]{1,2,4})if((i&bit)==0){var a=points[i];var z=points[i|bit];var toward=a.add(z).scale(.5).subtract(camera);var side=z.subtract(a).normalize().cross(toward.normalize());if(side.lengthSqr()<.0001)continue;side=side.normalize().scale(Math.max(.012,toward.length()*.0008));
            for(var point:new net.minecraft.world.phys.Vec3[]{a.add(side),a.subtract(side),z.subtract(side),z.add(side)})out.vertex(p.last().pose(),(float)point.x,(float)point.y,(float)point.z).color(r,g,blue,alpha).uv(.5f,.5f).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(15728880).normal(0,1,0).endVertex();
        }
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e){
        var mc=Minecraft.getInstance();if(!visible()||mc.options.hideGui||!(mc.hitResult instanceof BlockHitResult hit)||mc.level==null)return;
        if(mc.level.getBlockEntity(hit.getBlockPos()) instanceof me.cryo.zombierool.block.system.MusicVolume.VolumeEntity music){var n=music.config();e.getGuiGraphics().drawCenteredString(mc.font,Component.literal(n.getString("name")+" | "+n.getString("preset")+" | priority "+n.getInt("priority")),mc.getWindow().getGuiScaledWidth()/2,mc.getWindow().getGuiScaledHeight()-72,0xffc69fff);}
        if(mc.level.getBlockEntity(hit.getBlockPos()) instanceof AtmosphereZoneBlock.Zone zone){var n=zone.config();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
            e.getGuiGraphics().drawCenteredString(mc.font,Component.translatable("hud.zombierool.zone_feedback",n.getInt("radiusX")*2+1,n.getInt("height"),n.getInt("radiusZ")*2+1,n.getDouble("gravity"),Component.translatable(n.getBoolean("vacuum")?"hud.zombierool.zone_vacuum":"hud.zombierool.zone_air")),w/2,h-72,n.getBoolean("enabled")?0xffe8c885:0xffaaaaaa);
            e.getGuiGraphics().drawCenteredString(mc.font,Component.translatable(n.getBoolean("enabled")?"hud.zombierool.zone_active":"hud.zombierool.zone_inactive"),w/2,h-60,0xffd7e5ee);
        }
    }
    private AtmosphereZoneFeedback(){}
}
