package me.cryo.zombierool.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import me.cryo.zombierool.block.system.MapDeviceSystem;
import me.cryo.zombierool.client.render.AmbientSceneryRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Color-only effects must blend after custom sky/shader composition, against world depth. */
@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class AmbientSceneryEffects {
    private static ShaderInstance alphaShader,glowShader;
    private static final java.util.Map<String,RenderType> materials=new java.util.HashMap<>();
    private static final class EffectType extends RenderType {
        private EffectType(){super("zr_scenery_unused",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,()->{},()->{});}
        static RenderType make(ResourceLocation texture,boolean glow){return create("zr_scenery_effect",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,4096,false,true,CompositeState.builder().setShaderState(new ShaderStateShard(()->glow?glowShader:alphaShader)).setTextureState(new TextureStateShard(texture,false,false)).setTransparencyState(glow?ADDITIVE_TRANSPARENCY:TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));}
    }
    public static RenderType material(ResourceLocation texture,boolean glow){return materials.computeIfAbsent(texture+"|"+glow,k->EffectType.make(texture,glow));}
    public static boolean hasPending(){return !pending.isEmpty();}
    @Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void shaders(RegisterShadersEvent e)throws java.io.IOException{
            e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation("zombierool","scenery_effect"),DefaultVertexFormat.NEW_ENTITY),s->alphaShader=s);
            e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation("zombierool","scenery_glow"),DefaultVertexFormat.NEW_ENTITY),s->glowShader=s);
        }
    }
    private record Draw(MapDeviceSystem.Device device,float partial){}
    private static final java.util.Map<BlockPos,Draw> pending=new java.util.LinkedHashMap<>();
    private static final MultiBufferSource.BufferSource buffers=MultiBufferSource.immediate(new BufferBuilder(65536));
    private static final Matrix4f projection=new Matrix4f(),view=new Matrix4f();
    private static boolean captured;
    private static float fogStart,fogEnd;
    @SubscribeEvent public static void begin(TickEvent.RenderTickEvent e){if(e.phase==TickEvent.Phase.START){pending.clear();captured=false;}}
    public static void enqueue(MapDeviceSystem.Device d,float partial){
        if(!d.getBlockState().getValue(MapDeviceSystem.DeviceBlock.ACTIVE))return;
        pending.put(d.getBlockPos(),new Draw(d,partial));fogStart=RenderSystem.getShaderFogStart();fogEnd=RenderSystem.getShaderFogEnd();
    }
    @SubscribeEvent public static void capture(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||pending.isEmpty())return;
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera();projection.set(e.getProjectionMatrix());
        view.rotationX((float)Math.toRadians(camera.getXRot())).rotateY((float)Math.toRadians(camera.getYRot()+180));captured=true;
    }
    @SubscribeEvent(priority=EventPriority.LOW) public static void draw(RenderGuiEvent.Pre e){drawWorld();}
    public static void drawWorld(){
        var mc=Minecraft.getInstance();if(!captured||pending.isEmpty()||mc.level==null||alphaShader==null||glowShader==null)return;captured=false;
        var previous=RenderSystem.getShader();int[] textures=new int[4];for(int i=0;i<4;i++)textures[i]=RenderSystem.getShaderTexture(i);
        float oldStart=RenderSystem.getShaderFogStart(),oldEnd=RenderSystem.getShaderFogEnd();
        var stack=RenderSystem.getModelViewStack();RenderSystem.backupProjectionMatrix();stack.pushPose();stack.setIdentity();stack.mulPoseMatrix(view);RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,VertexSorting.DISTANCE_TO_ORIGIN);
        try(var state=new MapAtmosphereClient.RenderState()){
            mc.getMainRenderTarget().bindWrite(false);mc.gameRenderer.lightTexture().turnOnLightLayer();
            try{RenderSystem.enableDepthTest();RenderSystem.setShaderColor(1,1,1,1);RenderSystem.setShaderFogStart(fogStart);RenderSystem.setShaderFogEnd(fogEnd);
                prepareDepth();
                var camera=mc.gameRenderer.getMainCamera().getPosition();
                for(var row:pending.values()){var d=row.device;if(d.isRemoved()||d.getLevel()!=mc.level)continue;var p=new PoseStack();p.translate(d.getBlockPos().getX()-camera.x,d.getBlockPos().getY()-camera.y,d.getBlockPos().getZ()-camera.z);AmbientSceneryRenderer.renderEffects(d,row.partial,p,buffers);}
                buffers.endBatch();
            }finally{mc.gameRenderer.lightTexture().turnOffLightLayer();}
        }finally{pending.clear();RenderSystem.setShaderFogStart(oldStart);RenderSystem.setShaderFogEnd(oldEnd);stack.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.restoreProjectionMatrix();RenderSystem.setShader(()->previous);for(int i=0;i<4;i++)RenderSystem.setShaderTexture(i,textures[i]);}
    }
    static void prepareDepth(){
        if(alphaShader==null||glowShader==null)return;
        var mc=Minecraft.getInstance();
                RenderSystem.setShaderTexture(1,MapAtmosphereClient.sceneryWorldDepth());RenderSystem.setShaderTexture(3,MapAtmosphereClient.sceneryHandDepth());
                alphaShader.safeGetUniform("ScreenSize").set((float)mc.getMainRenderTarget().width,(float)mc.getMainRenderTarget().height);glowShader.safeGetUniform("ScreenSize").set((float)mc.getMainRenderTarget().width,(float)mc.getMainRenderTarget().height);
    }
    private AmbientSceneryEffects(){}
}
