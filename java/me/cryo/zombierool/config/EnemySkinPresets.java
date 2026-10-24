package me.cryo.zombierool.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import me.cryo.zombierool.entity.AbstractZombieRoolEntity;
import me.cryo.zombierool.entity.ZombieEntity;
import me.cryo.zombierool.entity.HellhoundEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Built-in appearances use the existing synchronized/persisted skin ID, including detached limbs. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class EnemySkinPresets {
    private static final String PREFIX="@preset/";
    private static final JsonObject CATALOG=load();
    private static JsonObject load(){
        try(var in=EnemySkinPresets.class.getResourceAsStream("/assets/zombierool/skins/enemy_presets.json")){
            if(in==null)throw new IllegalStateException("Missing enemy appearance catalog");
            return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot read enemy appearances",e);}
    }
    public static List<String> zombies(){return CATALOG.getAsJsonObject("zombies").keySet().stream().filter(id->!"templars".equals(id)&&!id.startsWith("scourge_")).toList();}
    public static List<String> hounds(){return List.of("infernal","original");}
    public static String zombiePreset(String id){if(id!=null&&id.startsWith("scourge_"))return "scourge";return "templars".equals(id)?"origins":zombies().contains(id)?id:"nazi";}
    public static String houndPreset(String id){return List.of("t4","t5","t6").contains(id==null?"":id)?"infernal":"auto".equals(id)||hounds().contains(id)?id:"auto";}
    /** Index is one-based for Lua; zero asks for a random variant. */
    public static String appearance(String kind,String preset,int variant,net.minecraft.util.RandomSource random){
        boolean zombie="zombie".equals(kind);if(!zombie&&!"hellhound".equals(kind))return null;
        preset=zombie?zombiePreset(preset):houndPreset(preset);if("auto".equals(preset))preset="infernal";
        var rows=CATALOG.getAsJsonObject(zombie?"zombies":"hounds").getAsJsonObject(preset).getAsJsonArray("variants");
        if(variant<0||variant>rows.size())return null;
        return PREFIX+(zombie?"zombies/":"hounds/")+preset+"/"+(variant==0?randomVariant(rows,random,"scourge".equals(preset)):variant-1);
    }
    public static JsonObject catalog(){var result=CATALOG.deepCopy();result.getAsJsonObject("zombies").remove("templars");for(String alias:List.of("scourge_ghouls","scourge_zombies","scourge_skeletons"))result.getAsJsonObject("zombies").remove(alias);for(String legacy:List.of("t4","t5","t6"))result.getAsJsonObject("hounds").remove(legacy);return result;}
    public static String roll(ServerLevel level,String kind){
        var config=WorldConfig.get(level);String preset;
        if("zombie".equals(kind))preset=zombiePreset(config.getZombieSkinPreset());
        else if("hellhound".equals(kind)){
            preset=config.getHellhoundSkinPreset();
            if("auto".equals(preset))preset="original".equals(config.getZombieSkinPreset())?"original":"infernal";
        }else return "";
        if("original".equals(preset))return "";
        var rows=CATALOG.getAsJsonObject("zombie".equals(kind)?"zombies":"hounds").getAsJsonObject(preset).getAsJsonArray("variants");
        return PREFIX+("zombie".equals(kind)?"zombies/":"hounds/")+preset+"/"+randomVariant(rows,level.random,"scourge".equals(preset));
    }
    private static int randomVariant(JsonArray rows,net.minecraft.util.RandomSource random,boolean mixed){
        if(!mixed)return random.nextInt(rows.size());String model=List.of("scourge_ghoul","scourge_zombie","scourge_skeleton").get(random.nextInt(3));var choices=new java.util.ArrayList<Integer>();for(int i=0;i<rows.size();i++)if(rows.get(i).getAsJsonObject().get("model").getAsString().equals(model))choices.add(i);return choices.get(random.nextInt(choices.size()));
    }
    public static String blockyModel(String id){var row=variant(id);if(row!=null&&row.has("model"))return row.get("model").getAsString();if(id!=null){if(id.contains("scourge_ghouls"))return "scourge_ghoul";if(id.contains("scourge_zombies"))return "scourge_zombie";if(id.contains("scourge_skeletons"))return "scourge_skeleton";}return "";}
    public static String blockyTexture(String id){var row=variant(id);return row!=null&&row.has("blockyTexture")?row.get("blockyTexture").getAsString():"";}
    private static JsonObject variant(String id){
        if(id==null||!id.startsWith(PREFIX))return null;
        String[] p=id.substring(PREFIX.length()).split("/");if(p.length!=3)return null;
        try{
            var group=CATALOG.getAsJsonObject(p[0]);if(group==null)return null;
            var preset=group.getAsJsonObject(p[1]);if(preset==null||!preset.has("variants"))return null;
            var rows=preset.getAsJsonArray("variants");int index=Integer.parseInt(p[2]);
            return index>=0&&index<rows.size()?rows.get(index).getAsJsonObject():null;
        }catch(IllegalArgumentException|ClassCastException e){return null;}
    }
    public static ResourceLocation texture(String id){var v=variant(id);return v==null?null:new ResourceLocation("zombierool",v.get("texture").getAsString());}
    public static JsonObject headgear(String id){var v=variant(id);return v!=null&&v.has("headgear")?v.getAsJsonObject("headgear"):null;}
    public static ResourceLocation gearMesh(String id){var v=variant(id);return v!=null&&v.has("gear")?new ResourceLocation("zombierool",v.get("gear").getAsString()):null;}
    @SubscribeEvent public static void join(EntityJoinLevelEvent event){
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getEntity() instanceof AbstractZombieRoolEntity e)||e.hasAssignedAppearance()||!e.getCustomSkin().isEmpty())return;
        if(e instanceof ZombieEntity)e.assignRolledSkins("zombie");
        else if(e instanceof HellhoundEntity)e.assignRolledSkins("hellhound");
    }
    private EnemySkinPresets(){}
}
