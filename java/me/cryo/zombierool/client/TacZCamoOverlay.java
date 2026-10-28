package me.cryo.zombierool.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.*;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import java.util.*;

/** A surface pattern independent of the sub-pixel palette UVs used by many gun packs. */
public final class TacZCamoOverlay {
    private static final class CamoLayer extends RenderType {
        private CamoLayer(){super("zr_camo_unused",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,()->{},()->{});}
        private static final Map<ResourceLocation,RenderType> TYPES=new HashMap<>();
        static RenderType get(ResourceLocation texture){return TYPES.computeIfAbsent(texture,t->create("zr_camo_surface",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,true,true,CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER).setTextureState(new TextureStateShard(t,false,false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).setLayeringState(POLYGON_OFFSET_LAYERING).setWriteMaskState(COLOR_WRITE).createCompositeState(true)));}
    }
    private static final Map<BedrockModel,FittedBounds> FITTED=new WeakHashMap<>(),FITTED_MESHES=new WeakHashMap<>();
    private static final Map<com.tacz.guns.client.model.bedrock.BedrockPart,BindTransform> BIND_POSES=new WeakHashMap<>();
    private static final Map<com.tacz.guns.client.model.bedrock.BedrockPart,TacZCamoParts.Finish> FINISHES=new WeakHashMap<>();
    private record BindTransform(float x,float y,float z,float rx,float ry,float rz) {}
    private TacZCamoOverlay(){}
    static float patternScale(String id){return CamoPatterns.patternScale(id);}
    private static String patternId(ItemStack stack){
        if(!stack.hasTag())return "";
        String id=stack.getTag().getString("zr_camo").replace("camo_","");
        return id.isEmpty()&&me.cryo.zombierool.core.system.WeaponFacade.isPackAPunched(stack)?"_pap":id;
    }
    public static ResourceLocation pattern(ItemStack stack){
        return pattern(patternId(stack));
    }
    public record SpritePattern(NativeImage pixels,long version){}
    public static SpritePattern spritePattern(String id){var p=CamoPatterns.spritePattern(id);return p==null?null:new SpritePattern(p.pixels(),p.version());}
    public static ResourceLocation pattern(String id){return CamoPatterns.pattern(id);}
    public static void render(Object raw,PoseStack pose,ItemDisplayContext context,int light,int overlay){
        if(!net.minecraftforge.fml.ModList.get().isLoaded("tacz")||!(raw instanceof BedrockGunModel gun))return;
        BedrockModel model=gun;
        var stack=gun.getCurrentGunItem();var texture=pattern(stack);if(texture==null)return;
        var appearance=CamoAppearance.get(patternId(stack));renderSurface(model,texture,pose,light,overlay,(float)appearance.repeats,null,appearance);
        if(stack.hasTag()&&!stack.getTag().getString("zr_camo").isEmpty()&&(me.cryo.zombierool.core.system.WeaponFacade.isPackAPunched(stack)||stack.isEnchanted())){var upgraded=stack.copy();upgraded.getOrCreateTag().putString("zr_camo","_pap");var sheen=pattern(upgraded);if(sheen!=null)renderSurface(model,sheen,pose,net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,overlay);}
    }
    public static void renderMeshes(Object raw,PoseStack pose,int light,int overlay){
        if(!(raw instanceof BedrockGunModel gun))return;
        var stack=gun.getCurrentGunItem();var texture=pattern(stack);if(texture==null)return;
        var meshes=TacZMeshCamo.open(raw);if(meshes==null)return;
        var appearance=CamoAppearance.get(patternId(stack));renderSurface(gun,texture,pose,light,overlay,(float)appearance.repeats,meshes,appearance);
        if(stack.hasTag()&&!stack.getTag().getString("zr_camo").isEmpty()
            &&(me.cryo.zombierool.core.system.WeaponFacade.isPackAPunched(stack)||stack.isEnchanted())){
            var upgraded=stack.copy();upgraded.getOrCreateTag().putString("zr_camo","_pap");var sheen=pattern(upgraded);
            if(sheen!=null)renderSurface(gun,sheen,pose,net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,overlay,patternScale("_pap"),meshes);
        }
    }
    private static void renderSurface(BedrockModel model,ResourceLocation texture,PoseStack pose,int light,int overlay){
        renderSurface(model,texture,pose,light,overlay,patternScale("_pap"));
    }
    private static void renderSurface(BedrockModel model,ResourceLocation texture,PoseStack pose,int light,int overlay,float scale){
        renderSurface(model,texture,pose,light,overlay,scale,null);
    }
    private static void renderSurface(BedrockModel model,ResourceLocation texture,PoseStack pose,int light,int overlay,float scale,TacZMeshCamo.Pass meshes){
        renderSurface(model,texture,pose,light,overlay,scale,meshes,new CamoAppearance.Profile(scale));
    }
    private static void renderSurface(BedrockModel model,ResourceLocation texture,PoseStack pose,int light,int overlay,float scale,TacZMeshCamo.Pass meshes,CamoAppearance.Profile appearance){
        var mc=Minecraft.getInstance();var type=CamoLayer.get(texture);var buffers=mc.renderBuffers().bufferSource();
        var consumer=buffers.getBuffer(type);
        // Functional renderers contain the player's arms, attachments, flashes and
        // shells. Only repeat the gun's own mesh, without repeating those effects.
        var bind=new PoseStack();
        FittedBounds fitted=null;
        if(appearance.fitted){var cache=meshes==null?FITTED:FITTED_MESHES;fitted=cache.get(model);if(fitted==null){fitted=new FittedBounds();var rest=new PoseStack();for(var part:model.getShouldRender())collectBounds(part,rest,model,fitted,TacZCamoParts.Finish.PAINT,meshes);cache.put(model,fitted);}}
        pose.pushPose();for(var part:model.getShouldRender())renderPart(part,pose,bind,model,consumer,light,overlay,scale,TacZCamoParts.Finish.PAINT,meshes,fitted,appearance);pose.popPose();
        if(!com.tacz.guns.compat.oculus.OculusCompat.endBatch(buffers))buffers.endBatch(type);
    }
    private static void renderPart(com.tacz.guns.client.model.bedrock.BedrockPart part,PoseStack pose,VertexConsumer target,int light,int overlay){
        renderPart(part,pose,new PoseStack(),null,target,light,overlay,6f);
    }
    private static BindTransform bindTransform(com.tacz.guns.client.model.bedrock.BedrockPart part,BedrockModel model){
        return BIND_POSES.computeIfAbsent(part,key->{
            float x=part.x,y=part.y,z=part.z;
            var bone=model==null?null:model.getBone(part.name);
            if(bone!=null&&bone.getPivot()!=null){
                var parent=bone.getParent()==null?null:model.getBone(bone.getParent());
                var pivot=bone.getPivot();
                x=pivot.get(0);y=24-pivot.get(1);z=pivot.get(2);
                if(parent!=null&&parent.getPivot()!=null){var p=parent.getPivot();x-=p.get(0);y=p.get(1)-pivot.get(1);z-=p.get(2);}
            }
            return new BindTransform(x/16,y/16,z/16,part.getInitRotX(),part.getInitRotY(),part.getInitRotZ());
        });
    }
    private static void renderPart(com.tacz.guns.client.model.bedrock.BedrockPart part,PoseStack pose,PoseStack bind,BedrockModel model,VertexConsumer target,int light,int overlay,float scale){
        renderPart(part,pose,bind,model,target,light,overlay,scale,TacZCamoParts.Finish.PAINT,null);
    }
    private static void renderPart(com.tacz.guns.client.model.bedrock.BedrockPart part,PoseStack pose,PoseStack bind,BedrockModel model,VertexConsumer target,int light,int overlay,float scale,TacZCamoParts.Finish inherited,TacZMeshCamo.Pass meshes){
        renderPart(part,pose,bind,model,target,light,overlay,scale,inherited,meshes,null,new CamoAppearance.Profile(scale));
    }
    private static void renderPart(com.tacz.guns.client.model.bedrock.BedrockPart part,PoseStack pose,PoseStack bind,BedrockModel model,VertexConsumer target,int light,int overlay,float scale,TacZCamoParts.Finish inherited,TacZMeshCamo.Pass meshes,FittedBounds fitted,CamoAppearance.Profile appearance){
        if(!part.visible)return;
        if(meshes!=null&&meshes.excluded(part.name))return;
        if(part instanceof com.tacz.guns.client.model.FunctionalBedrockPart functional&&functional.functionalRenderer!=null&&functional.functionalRenderer.apply(part)!=null)return;
        if(part.xScale==0||part.yScale==0||part.zScale==0)return;
        int partLight=part.illuminated?net.minecraft.client.renderer.LightTexture.pack(15,15):light;
        pose.pushPose();part.translateAndRotateAndScale(pose);
        var rest=bindTransform(part,model);bind.pushPose();bind.translate(rest.x,rest.y,rest.z);
        bind.mulPose(com.mojang.math.Axis.ZP.rotation(rest.rz));bind.mulPose(com.mojang.math.Axis.YP.rotation(rest.ry));bind.mulPose(com.mojang.math.Axis.XP.rotation(rest.rx));
        // Restore each vertex to the model's bind pose: the pattern is continuous
        // across pieces, while remaining attached to each animated bone.
        // Do not skip the subtree: a named handguard may live under a barrel,
        // and an anonymous bolt child must inherit the bare-metal finish.
        var finish=finish(part,inherited);
        if(finish==TacZCamoParts.Finish.PAINT){
            var consumer=new SurfaceConsumer(target,pose.last(),bind.last(),scale,fitted,appearance);
            if(meshes==null)part.compile(pose.last(),consumer,partLight,overlay,1,1,1,1);
            else meshes.compile(part.name,pose.last(),consumer,partLight,overlay);
        }
        for(var child:part.children)renderPart(child,pose,bind,model,target,partLight,overlay,scale,finish,meshes,fitted,appearance);
        bind.popPose();pose.popPose();
    }
    private static TacZCamoParts.Finish finish(com.tacz.guns.client.model.bedrock.BedrockPart part,TacZCamoParts.Finish inherited){
        var cached=FINISHES.get(part);if(cached!=null)return cached;
        // BedrockPart.addChild does not populate getParent(); use actual traversal.
        var result=TacZCamoParts.resolve(part.name,part.illuminated,inherited);
        FINISHES.put(part,result);return result;
    }
    private static void collectBounds(com.tacz.guns.client.model.bedrock.BedrockPart part,PoseStack bind,BedrockModel model,FittedBounds bounds,TacZCamoParts.Finish inherited,TacZMeshCamo.Pass meshes){
        if(meshes!=null&&meshes.excluded(part.name))return;
        if(part instanceof com.tacz.guns.client.model.FunctionalBedrockPart functional&&functional.functionalRenderer!=null&&functional.functionalRenderer.apply(part)!=null)return;
        var rest=bindTransform(part,model);bind.pushPose();bind.translate(rest.x,rest.y,rest.z);
        bind.mulPose(com.mojang.math.Axis.ZP.rotation(rest.rz));bind.mulPose(com.mojang.math.Axis.YP.rotation(rest.ry));bind.mulPose(com.mojang.math.Axis.XP.rotation(rest.rx));
        var finish=finish(part,inherited);
        if(finish==TacZCamoParts.Finish.PAINT){if(meshes==null)part.compile(bind.last(),bounds,0,0,1,1,1,1);else meshes.compile(part.name,bind.last(),bounds,0,0);}
        for(var child:part.children)collectBounds(child,bind,model,bounds,finish,meshes);
        bind.popPose();
    }
    /** Whole-gun bind-space extents keep the illustration fixed during animations. */
    public static final class FittedBounds implements VertexConsumer {
        private final float[] min={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY},max={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        public VertexConsumer vertex(double x,double y,double z){float[] p={(float)x,(float)y,(float)z};for(int i=0;i<3;i++){min[i]=Math.min(min[i],p[i]);max[i]=Math.max(max[i],p[i]);}return this;}
        float coordinate(float value,int axis){float extent=max[axis]-min[axis];return !Float.isFinite(extent)||extent<.00001f?.5f:Math.max(0,Math.min(1,(value-min[axis])/extent));}
        public VertexConsumer color(int r,int g,int b,int a){return this;}
        public VertexConsumer uv(float u,float v){return this;}
        public VertexConsumer overlayCoords(int u,int v){return this;}
        public VertexConsumer uv2(int u,int v){return this;}
        public VertexConsumer normal(float x,float y,float z){return this;}
        public void endVertex(){}public void defaultColor(int r,int g,int b,int a){}public void unsetDefaultColor(){}
    }
    public static final class SurfaceConsumer implements VertexConsumer {
        private final VertexConsumer target;
        private final Matrix4f inverse;
        private final Matrix3f inverseNormal;
        private final float scale;
        private final FittedBounds fitted;private final CamoAppearance.Profile appearance;
        private final Vector3f position=new Vector3f(),normal=new Vector3f();
        private double x,y,z;
        private float nx,ny,nz;
        private int red=255,green=255,blue=255,alpha=255,overlayU,overlayV,lightU,lightV;
        public SurfaceConsumer(VertexConsumer target,PoseStack.Pose root){this(target,root,new PoseStack().last());}
        public SurfaceConsumer(VertexConsumer target,PoseStack.Pose animated,PoseStack.Pose bind){this(target,animated,bind,6f);}
        public SurfaceConsumer(VertexConsumer target,PoseStack.Pose animated,PoseStack.Pose bind,float scale){this(target,animated,bind,scale,null);}
        public SurfaceConsumer(VertexConsumer target,PoseStack.Pose animated,PoseStack.Pose bind,float scale,FittedBounds fitted){this(target,animated,bind,scale,fitted,new CamoAppearance.Profile(scale));}
        public SurfaceConsumer(VertexConsumer target,PoseStack.Pose animated,PoseStack.Pose bind,float scale,FittedBounds fitted,CamoAppearance.Profile appearance){this.appearance=appearance;this.fitted=fitted;this.target=target;this.scale=scale;inverse=new Matrix4f(bind.pose()).mul(new Matrix4f(animated.pose()).invert());inverseNormal=new Matrix3f(bind.normal()).mul(new Matrix3f(animated.normal()).invert());}
        public VertexConsumer vertex(double x,double y,double z){this.x=x;this.y=y;this.z=z;position.set((float)x,(float)y,(float)z).mulPosition(inverse);return this;}
        public VertexConsumer color(int r,int g,int b,int a){red=r;green=g;blue=b;alpha=a;return this;}
        public VertexConsumer uv(float u,float v){return this;}
        public VertexConsumer overlayCoords(int u,int v){overlayU=u;overlayV=v;return this;}
        public VertexConsumer uv2(int u,int v){lightU=u;lightV=v;return this;}
        public VertexConsumer normal(float x,float y,float z){nx=x;ny=y;nz=z;normal.set(x,y,z).mul(inverseNormal);return this;}
        public void endVertex(){
            float ax=Math.abs(normal.x),ay=Math.abs(normal.y),az=Math.abs(normal.z);
            float u,v;if(ax>=ay&&ax>=az){u=position.z;v=position.y;}else if(ay>=az){u=position.x;v=position.z;}else{u=position.x;v=position.y;}
            if(fitted!=null){u=fitted.coordinate(u,ax>=ay&&ax>=az?2:0);v=fitted.coordinate(v,ay>ax&&ay>=az?2:1);}else{u*=scale;v*=scale;}
            if(fitted!=null){u=(float)((u-.5)/appearance.artworkScale+.5);v=(float)((v-.5)/appearance.artworkScale+.5);}
            double angle=Math.toRadians(appearance.rotation);float ru=(float)((u-.5)*Math.cos(angle)-(v-.5)*Math.sin(angle)+.5+appearance.offsetU/(fitted!=null?appearance.artworkScale:1)),rv=(float)((u-.5)*Math.sin(angle)+(v-.5)*Math.cos(angle)+.5+appearance.offsetV/(fitted!=null?appearance.artworkScale:1));
            target.vertex(x,y,z).color(red,green,blue,(int)(alpha*appearance.opacity)).uv(ru,rv).overlayCoords(overlayU,overlayV).uv2(lightU,lightV).normal(nx,ny,nz).endVertex();
        }
        public void defaultColor(int r,int g,int b,int a){color(r,g,b,a);}
        public void unsetDefaultColor(){red=green=blue=alpha=255;}
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void reload(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event){
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->{BIND_POSES.clear();FINISHES.clear();FITTED.clear();FITTED_MESHES.clear();});
    }
}
