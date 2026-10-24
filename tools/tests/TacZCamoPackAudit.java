import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import me.cryo.zombierool.client.TacZCamoParts;
import static me.cryo.zombierool.client.TacZCamoParts.Finish.*;

/** Runs the production finish classifier on the actual hierarchy of every extracted model. */
public final class TacZCamoPackAudit {
    static TacZCamoParts.Finish finish(String name,Map<String,JsonObject> bones,Set<String> visiting){
        if(!visiting.add(name))throw new AssertionError("Cyclic bone hierarchy: "+name);
        var b=bones.get(name);var parent=b.get("parent");
        var inherited=parent==null||parent.isJsonNull()||!bones.containsKey(parent.getAsString())?PAINT:finish(parent.getAsString(),bones,visiting);
        visiting.remove(name);
        return TacZCamoParts.resolve(name,false,inherited);
    }
    public static void main(String[] args)throws Exception{
        var packs=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();
        int total=0;var unresolved=new ArrayList<String>();
        for(var raw:packs){var pack=raw.getAsJsonObject();int paint=0,metal=0,protectedCount=0,models=0,mixed=0;
            if(!pack.get("status").getAsString().equals("inspected"))throw new AssertionError("Missing pack");
            for(var rawModel:pack.getAsJsonArray("models")){
                var model=rawModel.getAsJsonObject();var bones=new HashMap<String,JsonObject>();
                for(var rawBone:model.getAsJsonArray("bones")){var b=rawBone.getAsJsonObject();bones.put(b.get("name").getAsString(),b);}
                int p=0,m=0;
                for(var bone:bones.values()){
                    if(bone.get("cubes").getAsInt()==0&&!bone.get("mesh").getAsBoolean())continue;
                    var f=finish(bone.get("name").getAsString(),bones,new HashSet<>());
                    switch(f){case PAINT->{paint++;p++;}case METAL->{metal++;m++;}case PROTECTED->protectedCount++;}
                }
                if(p>0&&m>0)mixed++;
                else unresolved.add(pack.get("id").getAsString()+"\t"+model.get("path").getAsString()+"\tpaint="+p+" metal="+m);
                models++;
            }
            total+=models;
            System.out.printf(Locale.ROOT,"%s: %d models, %d mixed; %d paint / %d metal / %d protected bones%n",
                pack.get("id").getAsString(),models,mixed,paint,metal,protectedCount);
            if(models>0&&(paint==0||metal==0))throw new AssertionError("No selective coverage: "+pack.get("id"));
        }
        Files.write(Path.of(args[0]+".unmixed.txt"),unresolved);
        System.out.println("PASS: "+packs.size()+" catalog entries inspected, "+total+" model hierarchies classified (not a visual validation)");
    }
}
