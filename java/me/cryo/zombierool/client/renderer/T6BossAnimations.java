package me.cryo.zombierool.client.renderer;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Joint directions retargeted from local T6 xanim files onto our cuboid armature. */
public final class T6BossAnimations {
    private record Clip(float fps,float[][] frames){}
    private static final Map<String,Map<String,Clip>> CACHE=new HashMap<>();
    public static boolean apply(boolean panzer,String name,float age,boolean loop,int duration,ModelPart[] parts){
        String kind=panzer?"panzer":"brutus";
        var clips=CACHE.computeIfAbsent(kind,k->{
            try(var reader=new InputStreamReader(Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation("zombierool","zr_animation_data/"+k+"_minecraft_t6.json")).orElseThrow().open(),StandardCharsets.UTF_8)){
                var result=new HashMap<String,Clip>();
                for(var entry:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("clips").entrySet()){
                    var data=entry.getValue().getAsJsonObject();var raw=data.getAsJsonArray("frames");float[][] frames=new float[raw.size()][];
                    for(int f=0;f<frames.length;f++){var values=raw.get(f).getAsJsonArray();frames[f]=new float[values.size()];for(int i=0;i<values.size();i++)frames[f][i]=values.get(i).getAsFloat();}
                    result.put(entry.getKey(),new Clip(data.get("fps").getAsFloat(),frames));
                }return result;
            }catch(Exception failure){throw new IllegalStateException("Cannot load T6 boss animation "+k,failure);}
        });
        Clip clip=clips.get(name);if(clip==null)return false;
        float frame=Math.max(0,age)*clip.fps/20;if(!loop&&duration>0)frame=Math.max(0,age)*(clip.frames.length-1)/duration;frame=loop?frame%clip.frames.length:Math.min(frame,clip.frames.length-1);
        int a=(int)frame,b=loop?(a+1)%clip.frames.length:Math.min(a+1,clip.frames.length-1);float mix=frame-a;
        for(int i=0;i<parts.length;i++){
            var part=parts[i];part.xRot=angle(clip.frames[a][i*3],clip.frames[b][i*3],mix);part.yRot=angle(clip.frames[a][i*3+1],clip.frames[b][i*3+1],mix);part.zRot=angle(clip.frames[a][i*3+2],clip.frames[b][i*3+2],mix);
        }return true;
    }
    private static float angle(float a,float b,float mix){return a+Mth.wrapDegrees((b-a)*Mth.RAD_TO_DEG)*Mth.DEG_TO_RAD*mix;}
}
