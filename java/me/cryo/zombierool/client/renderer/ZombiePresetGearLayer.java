package me.cryo.zombierool.client.renderer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import me.cryo.zombierool.client.model.ModelZombieArmsFront;
import me.cryo.zombierool.config.EnemySkinPresets;
import me.cryo.zombierool.entity.ZombieEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Original accessory geometry follows the existing Minecraft head/body and gore visibility. */
public final class ZombiePresetGearLayer extends RenderLayer<ZombieEntity,ModelZombieArmsFront<ZombieEntity>> {
    private record Surface(boolean head,ResourceLocation texture,float[][] vertices){}
    private static final Map<ResourceLocation,List<Surface>> CACHE=new HashMap<>();
    private static final Map<String,net.minecraft.client.model.geom.ModelPart> HATS=new HashMap<>();
    private static net.minecraft.client.model.geom.ModelPart hat(String kind){
        var mesh=new net.minecraft.client.model.geom.builders.MeshDefinition();
        var cubes=net.minecraft.client.model.geom.builders.CubeListBuilder.create();
        switch(kind){
            case "steel", "mp" -> cubes.texOffs(0,0).addBox(-4.5f,-8.8f,-4.5f,9,2.5f,9).texOffs(0,0).addBox(-4,-9.7f,-4,8,1.5f,8).texOffs(0,0).addBox(-3.5f,-10.2f,-3.5f,7,1,7).texOffs(0,16).addBox(-5,-6.9f,-5,10,.6f,10);
            case "pickelhaube" -> cubes.texOffs(0,0).addBox(-4.5f,-10.5f,-4.5f,9,4,9).texOffs(0,16).addBox(-5,-7.2f,-5,10,1,10).texOffs(44,0).addBox(-1,-13,-1,2,3,2);
            case "crusader" -> cubes.texOffs(0,0).addBox(-4.5f,-10.5f,-4.5f,9,4,9).texOffs(0,16).addBox(-5,-7.2f,-5,10,1,10);
            case "officer", "guard" -> cubes.texOffs(0,0).addBox(-4.5f,-10,-4.5f,9,3,9).texOffs(0,16).addBox(-3.5f,-7.5f,-6,7,1,3);
            case "cowboy" -> cubes.texOffs(0,0).addBox(-4,-11,-4,8,4,8).texOffs(0,16).addBox(-6,-7.5f,-6,12,1,12);
            case "miner" -> cubes.texOffs(0,0).addBox(-4.5f,-10.5f,-4.5f,9,4,9).texOffs(0,16).addBox(-5,-7.2f,-5,10,1,10).texOffs(44,0).addBox(-1.5f,-9.5f,-5.5f,3,2,1);
            case "beanie" -> cubes.texOffs(0,0).addBox(-4.3f,-9.5f,-4.3f,9,3,9);
            default -> cubes.texOffs(0,0).addBox(-4.5f,-10,-4.5f,9,3,9).texOffs(0,16).addBox(-3.5f,-7.5f,-5.5f,7,1,2);
        }
        mesh.getRoot().addOrReplaceChild("hat",cubes,net.minecraft.client.model.geom.PartPose.ZERO);
        return net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh,64,64).bakeRoot();
    }

    public ZombiePresetGearLayer(ZombieRenderer renderer){super(renderer);}
    private static List<Surface> load(ResourceLocation id){
        var surfaces=new ArrayList<Surface>();
        try(var in=ZombiePresetGearLayer.class.getResourceAsStream("/assets/"+id.getNamespace()+"/"+id.getPath())){
            if(in==null)throw new IllegalStateException("Missing zombie gear: "+id);
            try(var reader=new InputStreamReader(new GZIPInputStream(in),StandardCharsets.UTF_8)){
                JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();
                for(var value:root.getAsJsonArray("surfaces")){
                    var row=value.getAsJsonObject();var data=row.getAsJsonArray("vertices");float[][] vertices=new float[data.size()][8];
                    for(int i=0;i<vertices.length;i++)for(int j=0;j<8;j++)vertices[i][j]=data.get(i).getAsJsonArray().get(j).getAsFloat();
                    surfaces.add(new Surface("head".equals(row.get("part").getAsString()),new ResourceLocation(row.get("texture").getAsString()),vertices));
                }
            }
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot load zombie gear: "+id,e);}
        return List.copyOf(surfaces);
    }
    @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,ZombieEntity entity,float swing,float amount,float partial,float age,float yaw,float pitch){
        if(entity.isInvisible()||me.cryo.zombierool.client.HalloweenManager.isHalloweenPeriod())return;
        var fitted=EnemySkinPresets.headgear(entity.getCustomSkin());
        if(fitted!=null){
            var head=getParentModel().head;
            if(head.visible&&(getParentModel().cranium==null||getParentModel().cranium.visible)){
                pose.pushPose();head.translateAndRotate(pose);
                String kind=fitted.get("kind").getAsString();
                HATS.computeIfAbsent(kind,ZombiePresetGearLayer::hat).render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(new ResourceLocation(fitted.get("texture").getAsString()))),light,OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
            return;
        }
        ResourceLocation mesh=EnemySkinPresets.gearMesh(entity.getCustomSkin());if(mesh==null)return;
        var model=getParentModel();
        for(var surface:CACHE.computeIfAbsent(mesh,ZombiePresetGearLayer::load)){
            var part=surface.head?model.head:model.body;
            if(!part.visible||surface.head&&model.cranium!=null&&!model.cranium.visible)continue;
            pose.pushPose();part.translateAndRotate(pose);var matrix=pose.last();
            var out=buffers.getBuffer(RenderType.entityCutoutNoCull(surface.texture));
            for(int i=0;i<surface.vertices.length;i+=3)for(int corner=0;corner<4;corner++){
                float[] v=surface.vertices[i+Math.min(corner,2)];
                out.vertex(matrix.pose(),v[0]/16,v[1]/16,v[2]/16).color(1f,1f,1f,1f).uv(v[3],v[4]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(matrix.normal(),v[5],v[6],v[7]).endVertex();
            }
            pose.popPose();
        }
    }
}
