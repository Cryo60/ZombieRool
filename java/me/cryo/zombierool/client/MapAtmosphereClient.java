package me.cryo.zombierool.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;
import me.cryo.zombierool.gameplay.MapAtmosphere;
import java.util.*;

/** Composite the finalized world image before the HUD; shader-pack programs remain untouched. */
@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class MapAtmosphereClient {
    private static final Map<String,ResourceLocation> worldSkies=new TreeMap<>();
    private static final Map<String,byte[][]> transfers=new HashMap<>();
    public static java.util.Set<String> skyIds(){return java.util.Set.copyOf(worldSkies.keySet());}
    public static void texture(String id,int index,int count,byte[] data){
        if(!id.startsWith("zombierool:world_sky/")||ResourceLocation.tryParse(id)==null||count<1||count>32||index<0||index>=count||data.length>256*1024)return;
        if(!transfers.containsKey(id)&&transfers.size()>=16)return;var parts=transfers.computeIfAbsent(id,k->new byte[count][]);if(parts.length!=count)return;parts[index]=data;
        for(var part:parts)if(part==null)return;transfers.remove(id);
        try(var state=new TextureUploadState()){var out=new java.io.ByteArrayOutputStream();for(var part:parts)out.write(part);byte[] bytes=out.toByteArray();if(bytes.length<24)return;var header=java.nio.ByteBuffer.wrap(bytes);int w=header.getInt(16),h=header.getInt(20);if(w<1||h<1||w>4096||h>4096||(long)w*h>8_388_608)return;
            var image=com.mojang.blaze3d.platform.NativeImage.read(new java.io.ByteArrayInputStream(bytes));var location=new ResourceLocation(id);var texture=new net.minecraft.client.renderer.texture.DynamicTexture(image);texture.setFilter(true,false);Minecraft.getInstance().getTextureManager().register(location,texture);worldSkies.put(id,location);
        }catch(Exception error){me.cryo.zombierool.ZombieroolMod.LOGGER.warn("Invalid map skybox {}",id);}
    }
    private static ResourceLocation soundId(String id){var file=DynamicSoundLoader.getDynamicSoundFile(id);return file==null?new ResourceLocation(id):new ResourceLocation("zombierool","dynamic_"+file.getName().replace(".ogg",""));}
    private static CompoundTag settings=MapAtmosphere.defaults();
    private static final Map<String,Ambient> loops=new HashMap<>();
    private static final List<Ambient> sounds=new ArrayList<>();
    private record Pending(CompoundTag source,long time){}
    private static final List<Pending> pending=new ArrayList<>();
    private static ShaderInstance shader,bloodShader;
    private static String checkedSky;
    private static net.minecraft.server.packs.resources.ResourceManager checkedResources;
    private static boolean checkedSkyExists;
    private static void invalidateSkyLookup(){checkedSky=null;checkedResources=null;}
    /** Resource packs may live on disk. Never probe them once per blood particle. */
    private static boolean skyAvailable(ResourceLocation sky){
        String name=sky.toString();if(worldSkies.containsKey(name))return true;
        var resources=Minecraft.getInstance().getResourceManager();
        if(!name.equals(checkedSky)||resources!=checkedResources){checkedSky=name;checkedResources=resources;checkedSkyExists=resources.getResource(sky).isPresent();}
        return checkedSkyExists;
    }
    @Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class SkyResourceReload {
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event){
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)resources->invalidateSkyLookup());
        }
    }
    private static RenderTarget scene,depth,handDepth;
    private static final Matrix4f invProjection=new Matrix4f(),invView=new Matrix4f();
    private static boolean captured,handCaptured;
    private static final Matrix4f worldProjection=new Matrix4f(),worldModelView=new Matrix4f();
    private static long flashStart;
    private static float flash;
    public static CompoundTag settings(){return settings.copy();}
    public static int playing(){return sounds.size();}
    public static void receive(String action,CompoundTag n){
        if(n==null)return;
        if(action.equals("import_ok")||action.equals("import_error")){me.cryo.zombierool.client.gui.MapAtmosphereScreen.importResult(action.equals("import_ok"),n);return;}
        if(action.equals("save_ok")||action.equals("save_error")){me.cryo.zombierool.client.gui.MapAtmosphereScreen.saveResult(action.equals("save_ok"));return;}
        if(action.equals("open")){me.cryo.zombierool.client.gui.MapAtmosphereScreen.open(n);return;}
        if(action.equals("cue")){n=n.copy();n.putBoolean("loop",false);if(!settings.getBoolean("enabled"))return;var mc=Minecraft.getInstance();if(mc.player==null)return;
            double speed=n.getDouble("soundSpeed"),distance=n.getBoolean("global")?0:mc.player.getEyePosition().distanceTo(new Vec3(n.getDouble("x"),n.getDouble("y"),n.getDouble("z")));if(!n.getBoolean("global")&&distance>=n.getDouble("radius"))return;
            flashStart=mc.level.getGameTime();flash=(float)n.getDouble("flash")*gain(n,distance,false);if(pending.size()<64)pending.add(new Pending(n.copy(),mc.level.getGameTime()+(speed>0?(long)(20*distance/speed):0)));return;}

        var incoming=MapAtmosphere.sanitize(n);if(!incoming.getList("skyObjects",10).equals(settings.getList("skyObjects",10)))SkyObjectsRenderer.clear();if(!incoming.getString("presetId").equals(settings.getString("presetId"))){pending.clear();flash=0;sounds.forEach(sound->sound.retiring=true);loops.clear();}settings=incoming;var wanted=new HashMap<String,CompoundTag>();if(settings.getBoolean("enabled"))for(Tag row:settings.getList("sources",10)){var source=(CompoundTag)row;if(source.getBoolean("enabled")&&source.getBoolean("loop"))wanted.put(source.getString("id"),source);}
        loops.entrySet().removeIf(e->{if(wanted.containsKey(e.getKey())&&e.getValue().config.equals(wanted.get(e.getKey())))return false;e.getValue().retiring=true;return true;});
        for(var e:wanted.entrySet())if(!loops.containsKey(e.getKey())){var sound=play(e.getValue());if(sound!=null)loops.put(e.getKey(),sound);}
        if(!settings.getBoolean("enabled")){pending.clear();sounds.forEach(sound->sound.retiring=true);}
    }
    private static Ambient play(CompoundTag n){if(sounds.size()>=64||n.getString("sound").startsWith("zombierool:custom/")&&!DynamicSoundLoader.hasDynamicSound(n.getString("sound")))return null;var sound=new Ambient(n);sounds.add(sound);Minecraft.getInstance().getSoundManager().play(sound);return sound;}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.level==null)return;
        if(mc.level.getGameTime()%40==0&&settings.getBoolean("enabled")){loops.entrySet().removeIf(row->row.getValue().isStopped()||!sounds.contains(row.getValue()));for(Tag row:settings.getList("sources",10)){var source=(CompoundTag)row;String id=source.getString("id");if(source.getBoolean("enabled")&&source.getBoolean("loop")&&!loops.containsKey(id)){var sound=play(source);if(sound!=null)loops.put(id,sound);}}}
        pending.removeIf(p->{if(mc.level.getGameTime()<p.time)return false;play(p.source);return true;});sounds.removeIf(s->s.isStopped()||mc.level.getGameTime()-s.started>60&&!mc.getSoundManager().isActive(s));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){var mc=Minecraft.getInstance();for(var sound:sounds)mc.getSoundManager().stop(sound);sounds.clear();loops.clear();pending.clear();settings=MapAtmosphere.defaults();SkyObjectsRenderer.clear();for(var id:worldSkies.values())mc.getTextureManager().release(id);worldSkies.clear();invalidateSkyLookup();transfers.clear();captured=false;flash=0;if(scene!=null){scene.destroyBuffers();scene=null;}if(depth!=null){depth.destroyBuffers();depth=null;}if(handDepth!=null){handDepth.destroyBuffers();handDepth=null;}}
    /** Smooth finite-radius attenuation, including indoor obstruction; independent of vanilla's 16-block cap. */
    public static float gain(CompoundTag n,double distance,boolean obstructed){double near=n.getDouble("near"),radius=Math.max(near+1,n.getDouble("radius"));double t=Math.max(0,Math.min(1,(distance-near)/(radius-near)));double g=(1-t)*(1-t);return (float)(g*(obstructed?n.getDouble("occlusion"):1));}
    private static final class Ambient extends AbstractTickableSoundInstance {
        final CompoundTag config;final boolean loop;final long started;int age,retireAge;boolean retiring,blocked;
        Ambient(CompoundTag n){super(SoundEvent.createVariableRangeEvent(soundId(n.getString("sound"))),SoundSource.AMBIENT,n.contains("_seed")?RandomSource.create(n.getLong("_seed")):RandomSource.create());config=n.copy();started=Minecraft.getInstance().level==null?0:Minecraft.getInstance().level.getGameTime();loop=n.getBoolean("loop");looping=loop;x=n.getDouble("x");y=n.getDouble("y");z=n.getDouble("z");relative=n.getBoolean("global");if(relative){x=0;y=0;z=0;}attenuation=SoundInstance.Attenuation.NONE;pitch=(float)Math.max(.25,n.getDouble("pitch")+(random.nextDouble()*2-1)*n.getDouble("pitchVariation"));volume=.001f;}
        @Override public boolean canStartSilent(){return true;}
        @Override public void tick(){var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null){stop();return;}age++;if(retiring)retireAge++;int fadeOut=Math.max(1,(int)config.getDouble("fadeOut"));if(retiring&&retireAge>=fadeOut){stop();return;}
            if(!relative&&age%10==1){var eye=mc.gameRenderer.getMainCamera().getPosition();blocked=mc.level.clip(new net.minecraft.world.level.ClipContext(eye,new Vec3(x,y,z),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player)).getType()==net.minecraft.world.phys.HitResult.Type.BLOCK;}
            double distance=relative?0:mc.gameRenderer.getMainCamera().getPosition().distanceTo(new Vec3(x,y,z));float target=(float)(config.getDouble("volume")*settings.getDouble("ambientVolume"))*gain(config,distance,blocked);float fade=(float)Math.min(1,age/Math.max(1,config.getDouble("fadeIn")))*(retiring?1-retireAge/(float)fadeOut:1);volume+=(target*fade-volume)*.2f;
        }
    }
    /** Allocate stencil before world rendering, so the first scoped weapon cannot recreate the main FBO mid-frame. */
    public static void prepareFrame(){
        var mc=Minecraft.getInstance();AmbientParticles.prepareFrame(mc.level!=null&&deferredBlood());if(mc.level==null||!settings.getBoolean("enabled"))return;
        var main=mc.getMainRenderTarget();if(!main.isStencilEnabled())try(var state=new RenderState()){main.enableStencil();main.bindWrite(true);}
    }
    /** Preserve the depth produced by the hand pass before a shader pack resolves/clears it. */
    public static void captureHand(){
        if(!captured||handDepth==null)return;
        int draw=GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);if(draw==0)return;
        try(var state=new RenderState()){
            // Scoped weapons can enable stencil during the hand pass, after the world capture.
            if(Minecraft.getInstance().getMainRenderTarget().isStencilEnabled()&&!handDepth.isStencilEnabled())handDepth.enableStencil();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,handDepth.frameBufferId);
            GL30.glBlitFramebuffer(0,0,handDepth.width,handDepth.height,0,0,handDepth.width,handDepth.height,GL30.GL_DEPTH_BUFFER_BIT,GL30.GL_NEAREST);
        }finally{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);}
        handCaptured=true;
    }
    static int sceneryWorldDepth(){return depth==null?-1:depth.getDepthTextureId();}
    static int sceneryHandDepth(){return handCaptured?handDepth.getDepthTextureId():sceneryWorldDepth();}
    /** Restore actual GL bindings as well as RenderSystem's shader inputs. Other renderers cache bindings. */
    static final class RenderState implements AutoCloseable {
        final int active=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        final int program=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM);
        final int[] textures=new int[4];
        final boolean depthTest=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST),depthWrite=org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK),blend=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND),cull=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE);
        final int srcRgb=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB),dstRgb=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB),srcAlpha=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA),dstAlpha=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        final float[] color=RenderSystem.getShaderColor().clone();
        RenderState(){for(int i=0;i<4;i++){int unit=org.lwjgl.opengl.GL13.GL_TEXTURE0+i;RenderSystem.activeTexture(unit);org.lwjgl.opengl.GL13.glActiveTexture(unit);textures[i]=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);}RenderSystem.activeTexture(active);org.lwjgl.opengl.GL13.glActiveTexture(active);}
        public void close(){for(int i=0;i<4;i++){int unit=org.lwjgl.opengl.GL13.GL_TEXTURE0+i;RenderSystem.activeTexture(unit);org.lwjgl.opengl.GL13.glActiveTexture(unit);org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,textures[i]);RenderSystem.bindTexture(textures[i]);}RenderSystem.activeTexture(active);org.lwjgl.opengl.GL13.glActiveTexture(active);com.mojang.blaze3d.platform.GlStateManager._glUseProgram(program);if(depthTest)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();RenderSystem.depthMask(depthWrite);if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);}
    }
    private static void targets(int w,int h){if(scene==null){scene=new TextureTarget(w,h,false,Minecraft.ON_OSX);depth=new TextureTarget(w,h,true,Minecraft.ON_OSX);handDepth=new TextureTarget(w,h,true,Minecraft.ON_OSX);}else if(scene.width!=w||scene.height!=h){scene.resize(w,h,Minecraft.ON_OSX);depth.resize(w,h,Minecraft.ON_OSX);handDepth.resize(w,h,Minecraft.ON_OSX);}}
    public static boolean deferredBlood(){return ClientEnvironmentEffects.shaderPackActive()&&deferredAmbient();}
    public static boolean deferredAmbient(){
        if(shader==null||bloodShader==null||!settings.getBoolean("enabled")||!settings.getBoolean("skyEnabled")||ClientEnvironmentEffects.hasMapFog())return false;
        if(settings.getBoolean("vanillaSky"))return !settings.getList("skyObjects",10).isEmpty();
        var id=new ResourceLocation(settings.getString("skyTexture"));return skyAvailable(id);
    }
    /** Put the custom sky behind translucent geometry, before its colors blend with the background. */
    @SubscribeEvent public static void skyBackground(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_SKY||shader==null||!settings.getBoolean("enabled")||!settings.getBoolean("skyEnabled")||ClientEnvironmentEffects.hasMapFog()||ClientEnvironmentEffects.shaderPackActive())return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        if(settings.getBoolean("vanillaSky")){var camera=mc.gameRenderer.getMainCamera();var view=new Matrix4f().rotationX((float)Math.toRadians(camera.getXRot())).rotateY((float)Math.toRadians(camera.getYRot()+180f));renderSkyObjects(e.getProjectionMatrix(),view,-1,-1);return;}
        var sky=new ResourceLocation(settings.getString("skyTexture"));
        if(!skyAvailable(sky))return;
        var state=new RenderState();var previousShader=RenderSystem.getShader();int[] previousTextures=new int[4];for(int i=0;i<4;i++)previousTextures[i]=RenderSystem.getShaderTexture(i);
        var projection=new Matrix4f(e.getProjectionMatrix()).invert();var view=new Matrix4f(RenderSystem.getInverseViewRotationMatrix());
        var stack=RenderSystem.getModelViewStack();RenderSystem.backupProjectionMatrix();stack.pushPose();stack.setIdentity();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(new Matrix4f(),VertexSorting.ORTHOGRAPHIC_Z);
        try{
            // Keep the active world framebuffer, including the framebuffer owned by a shader pack.
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.setShader(()->shader);RenderSystem.setShaderTexture(3,sky);
            shader.safeGetUniform("SkyPass").set(1f);shader.safeGetUniform("InvProjMat").set(projection);shader.safeGetUniform("InvViewRot").set(view);
            shader.safeGetUniform("SkyLayout").set(settings.getString("skyLayout").equals("panorama")?1f:0f);shader.safeGetUniform("SkyCoordinates").set(settings.getString("skyCoordinates").equals("z_up")?1f:0f);
            shader.safeGetUniform("SkyParams").set((float)Math.toRadians(settings.getDouble("skyYaw")),(float)Math.toRadians(settings.getDouble("skyPitch")),(float)settings.getDouble("skyBrightness"));
            var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION);b.vertex(-1,-1,0).endVertex();b.vertex(1,-1,0).endVertex();b.vertex(1,1,0).endVertex();b.vertex(-1,1,0).endVertex();Tesselator.getInstance().end();
        }finally{
            stack.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.restoreProjectionMatrix();RenderSystem.setShader(()->previousShader);for(int i=0;i<4;i++)RenderSystem.setShaderTexture(i,previousTextures[i]);state.close();
        }
        var camera=mc.gameRenderer.getMainCamera();var skyView=new Matrix4f().rotationX((float)Math.toRadians(camera.getXRot())).rotateY((float)Math.toRadians(camera.getYRot()+180f));
        renderSkyObjects(e.getProjectionMatrix(),skyView,-1,-1);
    }
    private static void renderSkyObjects(Matrix4f projection,Matrix4f view,int worldDepth,int heldDepth){
        if(!settings.getBoolean("enabled")||!settings.getBoolean("skyEnabled")||ClientEnvironmentEffects.hasMapFog())return;
        var previous=RenderSystem.getShader();int[] textures=new int[4];for(int i=0;i<4;i++)textures[i]=RenderSystem.getShaderTexture(i);
        try(var state=new RenderState()){SkyObjectsRenderer.render(settings,projection,view,worldDepth,heldDepth);}
        finally{RenderSystem.setShader(()->previous);for(int i=0;i<4;i++)RenderSystem.setShaderTexture(i,textures[i]);}
    }
    @SubscribeEvent public static void capture(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;captured=false;handCaptured=false;if(!AmbientSceneryEffects.hasPending()&&!AtmosphereZoneFeedback.needsDepth()&&(!settings.getBoolean("enabled")||!gradingNeeded()&&!deferredBlood()))return;var mc=Minecraft.getInstance();var main=mc.getMainRenderTarget();try(var state=new RenderState()){targets(main.width,main.height);if(main.isStencilEnabled()){if(!depth.isStencilEnabled())depth.enableStencil();if(!handDepth.isStencilEnabled())handDepth.enableStencil();}depth.copyDepthFrom(main);main.bindWrite(false);}
        // AFTER_LEVEL pose stacks may be replaced by Oculus. Rebuild the world camera view,
        // using Minecraft's +180 degree yaw convention rather than the later GUI matrix.
        worldProjection.set(e.getProjectionMatrix());worldModelView.rotationX((float)Math.toRadians(mc.gameRenderer.getMainCamera().getXRot())).rotateY((float)Math.toRadians(mc.gameRenderer.getMainCamera().getYRot()+180f));invProjection.set(e.getProjectionMatrix()).invert();invView.set(RenderSystem.getInverseViewRotationMatrix());captured=true;
    }
    @SubscribeEvent public static void vignette(RenderGuiOverlayEvent.Pre e){if(settings.getBoolean("enabled")&&e.getOverlay().id().equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.VIGNETTE.id()))e.setCanceled(true);}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void gui(RenderGuiEvent.Pre event){composite();}
    /** RenderTick END is emitted even when F1 skips Forge's GUI render events. */
    @SubscribeEvent public static void hiddenHud(net.minecraftforge.event.TickEvent.RenderTickEvent e){
        var mc=Minecraft.getInstance();
        if(e.phase==net.minecraftforge.event.TickEvent.Phase.END&&mc.options.hideGui&&mc.level!=null&&mc.screen==null){composite();AmbientSceneryEffects.drawWorld();}
    }
    public static void composite(){
        var mc=Minecraft.getInstance();if(!captured||shader==null||mc.level==null||!settings.getBoolean("enabled"))return;
        if(deferredBlood()){compositePass(true);renderSkyObjects(worldProjection,worldModelView,depth.getDepthTextureId(),handCaptured?handDepth.getDepthTextureId():depth.getDepthTextureId());renderBloodAfterShader(true);}
        if(gradingNeeded())compositePass(false);
    }
    private static boolean gradingNeeded(){
        if(settings.getDouble("exposure")!=0||settings.getDouble("vignette")!=0||settings.getDouble("dirt")!=0||settings.getDouble("grain")!=0)return true;
        for(String key:new String[]{"red","green","blue","contrast","gamma","saturation"})if(settings.getDouble(key)!=1)return true;
        var level=Minecraft.getInstance().level;return flash>0&&level!=null&&level.getGameTime()-flashStart<8;
    }
    private static void renderBloodAfterShader(boolean includeBlood){
        if(!AmbientParticles.hasDeferred()&&(!includeBlood||!me.cryo.zombierool.client.particle.BloodStainParticle.hasDeferred()))return;
        var mc=Minecraft.getInstance();var state=new RenderState();var previous=RenderSystem.getShader();int[] tex=new int[4];for(int i=0;i<4;i++)tex[i]=RenderSystem.getShaderTexture(i);
        var stack=RenderSystem.getModelViewStack();RenderSystem.backupProjectionMatrix();stack.pushPose();stack.setIdentity();stack.mulPoseMatrix(worldModelView);RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(worldProjection,VertexSorting.DISTANCE_TO_ORIGIN);
        try{
            mc.gameRenderer.lightTexture().turnOnLightLayer();RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.setShader(()->bloodShader);RenderSystem.setShaderTexture(0,net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.setShaderTexture(1,depth.getDepthTextureId());RenderSystem.setShaderTexture(3,handCaptured?handDepth.getDepthTextureId():depth.getDepthTextureId());bloodShader.safeGetUniform("ScreenSize").set((float)mc.getMainRenderTarget().width,(float)mc.getMainRenderTarget().height);
            var out=Tesselator.getInstance().getBuilder();out.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.PARTICLE);
            AmbientParticles.renderDeferred(out,mc.gameRenderer.getMainCamera(),mc.getFrameTime());if(includeBlood)me.cryo.zombierool.client.particle.BloodStainParticle.renderDeferred(out,mc.gameRenderer.getMainCamera(),mc.getFrameTime());Tesselator.getInstance().end();
        }finally{stack.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.restoreProjectionMatrix();RenderSystem.setShader(()->previous);for(int i=0;i<4;i++)RenderSystem.setShaderTexture(i,tex[i]);state.close();}
    }
    private static void compositePass(boolean skyStage){
        var mc=Minecraft.getInstance();if(!captured||shader==null||mc.level==null||!settings.getBoolean("enabled"))return;var main=mc.getMainRenderTarget();
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,scene.frameBufferId);GL30.glBlitFramebuffer(0,0,main.width,main.height,0,0,main.width,main.height,GL30.GL_COLOR_BUFFER_BIT,GL30.GL_NEAREST);main.bindWrite(false);
        var state=new RenderState();var previousShader=RenderSystem.getShader();int[] previousTextures=new int[4];for(int i=0;i<4;i++)previousTextures[i]=RenderSystem.getShaderTexture(i);
        var stack=RenderSystem.getModelViewStack();RenderSystem.backupProjectionMatrix();stack.pushPose();stack.setIdentity();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(new Matrix4f(),VertexSorting.ORTHOGRAPHIC_Z);RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);
        try{
            RenderSystem.setShader(()->shader);RenderSystem.setShaderTexture(0,scene.getColorTextureId());RenderSystem.setShaderTexture(1,depth.getDepthTextureId());RenderSystem.setShaderTexture(2,handCaptured?handDepth.getDepthTextureId():main.getDepthTextureId());
            var sky=new ResourceLocation(settings.getString("skyTexture"));boolean exists=skyAvailable(sky);RenderSystem.setShaderTexture(3,sky);
            shader.safeGetUniform("SkyPass").set(0f);shader.safeGetUniform("InvProjMat").set(invProjection);shader.safeGetUniform("InvViewRot").set(invView);shader.safeGetUniform("SkyEnabled").set(skyStage&&!settings.getBoolean("vanillaSky")?1f:0f);shader.safeGetUniform("SkyLayout").set(settings.getString("skyLayout").equals("panorama")?1f:0f);
            shader.safeGetUniform("SkyCoordinates").set(settings.getString("skyCoordinates").equals("z_up")?1f:0f);
            shader.safeGetUniform("SkyParams").set((float)Math.toRadians(settings.getDouble("skyYaw")),(float)Math.toRadians(settings.getDouble("skyPitch")),(float)settings.getDouble("skyBrightness"));shader.safeGetUniform("Tint").set((float)settings.getDouble("red"),(float)settings.getDouble("green"),(float)settings.getDouble("blue"));
            shader.safeGetUniform("Grade").set((float)settings.getDouble("exposure"),(float)settings.getDouble("contrast"),(float)settings.getDouble("saturation"),(float)settings.getDouble("gamma"));shader.safeGetUniform("ScreenEffects").set((float)settings.getDouble("vignette"),(float)settings.getDouble("vignetteSoftness"),(float)settings.getDouble("dirt"),(float)settings.getDouble("grain"));
            shader.safeGetUniform("Time").set(mc.level.getGameTime()/20f);shader.safeGetUniform("Flash").set(flash*Math.max(0,1-(mc.level.getGameTime()-flashStart)/8f));
            if(skyStage){shader.safeGetUniform("Tint").set(1f,1f,1f);shader.safeGetUniform("Grade").set(0f,1f,1f,1f);shader.safeGetUniform("ScreenEffects").set(0f,1f,0f,0f);shader.safeGetUniform("Flash").set(0f);}
            var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION);b.vertex(-1,-1,0).endVertex();b.vertex(1,-1,0).endVertex();b.vertex(1,1,0).endVertex();b.vertex(-1,1,0).endVertex();Tesselator.getInstance().end();
        }finally{RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);stack.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.restoreProjectionMatrix();RenderSystem.setShader(()->previousShader);for(int i=0;i<4;i++)RenderSystem.setShaderTexture(i,previousTextures[i]);state.close();}
    }
    @Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Shaders {
        @SubscribeEvent public static void register(RegisterShadersEvent e)throws java.io.IOException{e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation("zombierool","map_atmosphere"),DefaultVertexFormat.POSITION),s->shader=s);e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation("zombierool","blood_late"),DefaultVertexFormat.PARTICLE),s->bloodShader=s);e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation("zombierool","sky_objects"),DefaultVertexFormat.POSITION_TEX_COLOR),s->SkyObjectsRenderer.shader=s);}
    }
}
