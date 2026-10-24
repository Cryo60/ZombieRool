package me.cryo.zombierool.client.render;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Source-derived voxel surfaces, shared by all placed decorations; no per-instance meshes. */
public final class VehicleDecorModels {
    private static final ResourceLocation TEXTURE=new ResourceLocation("zombierool","textures/scenery/military_materials.png");
    private record Face(int axis,int side,int color,float[] xyz){}
    private static final Map<String,List<Face>> CACHE=new HashMap<>();
    private static Object resourceManager;
    private static List<Face> mesh(String id){
        var manager=Minecraft.getInstance().getResourceManager();if(manager!=resourceManager){CACHE.clear();resourceManager=manager;}
        return CACHE.computeIfAbsent(id,key->{var faces=new ArrayList<Face>();try(var reader=new java.io.InputStreamReader(manager.open(new ResourceLocation("zombierool","scenery/vehicles/"+key+".json")),java.nio.charset.StandardCharsets.UTF_8)){
            var rows=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("faces");if(rows.size()>20000)throw new IllegalArgumentException("Vehicle surface budget exceeded");
            for(var row:rows){var a=row.getAsJsonArray();float[] xyz=new float[12];for(int i=0;i<12;i++)xyz[i]=a.get(i+3).getAsFloat();faces.add(new Face(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt(),xyz));}
        }catch(Exception ex){org.slf4j.LoggerFactory.getLogger("ZombieRool").warn("Cannot load decoration {}",key,ex);}return List.copyOf(faces);});
    }
    public static void draw(String id,PoseStack pose,MultiBufferSource buffers,int light,int section){
        var faces=mesh(id);var out=buffers.getBuffer(RenderType.entityCutout(TEXTURE));var p=pose.last();
        for(var f:faces){float cy=(f.xyz[1]+f.xyz[4]+f.xyz[7]+f.xyz[10])*.25f;if(section==1&&cy>=1.4f||section==2&&cy<1.4f)continue;
            float r=paint((f.color>>16)&255),g=paint((f.color>>8)&255),b=paint(f.color&255);
            for(int i=0;i<4;i++)out.vertex(p.pose(),f.xyz[i*3],f.xyz[i*3+1],f.xyz[i*3+2]).color(r,g,b,1).uv(i==1||i==2?.245f:.005f,i>=2?.495f:.005f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(p.normal(),f.axis==0?f.side:0,f.axis==1?f.side:0,f.axis==2?f.side:0).endVertex();
        }
    }
    private static float paint(int c){return (float)Math.pow(c/255f,.72)*1.15f;}
    static void vehicle(me.cryo.zombierool.block.system.MapDeviceSystem.Device d,PoseStack pose,MultiBufferSource b,int light){
        light=LevelRenderer.getLightColor(d.getLevel(),d.getBlockPos().above(4));var n=d.scenery;String state=n.getString("variant"),vehicle=n.getString("vehicle"),era=n.getString("era");
        pose.pushPose();pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float)n.getDouble("yaw")));pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees((float)n.getDouble("pitch")));pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float)n.getDouble("roll")));float scale=(float)n.getDouble("size");pose.scale(scale,scale,scale);
        if(state.equals("crashed")){pose.translate(0,.15,0);pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(vehicle.equals("helicopter")?33:17));pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-11));}
        draw(me.cryo.zombierool.gameplay.AmbientScenery.vehicleMesh(n),pose,b,light,0);pose.popPose();
    }
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool",bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class Reload {
        @net.minecraftforge.eventbus.api.SubscribeEvent public static void register(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event){event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->CACHE.clear());}
    }
    private VehicleDecorModels(){}
}
