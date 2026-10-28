package me.cryo.zombierool.api;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import me.cryo.zombierool.gameplay.PortableEquipment;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Addon templates and data/<namespace>/buildable_presets/*.json. Templates never create map recipes automatically. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class BuildablePresets {
    private static final Map<String,CompoundTag> ADDONS=new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile Map<String,CompoundTag> data=Map.of();
    private BuildablePresets(){}
    /** Definition uses the same reward, mode, ticks, limit, presetPieces NBT fields as map recipes. */
    public static void register(ResourceLocation id,CompoundTag definition){validate(definition);ADDONS.put(id.toString(),definition.copy());}
    private static void validate(CompoundTag n){
        if(n.getString("name").isBlank()||ItemStack.of(n.getCompound("reward")).isEmpty()||n.getList("presetPieces",10).isEmpty())throw new IllegalArgumentException("A preset needs a name, reward and parts");
        if(n.getList("presetPieces",10).size()>32||!Set.of("item","block","deploy","event").contains(n.getString("mode")))throw new IllegalArgumentException("Invalid preset mode or part count");
        var ids=new HashSet<String>();for(var tag:n.getList("presetPieces",10)){var p=(CompoundTag)tag;if(!p.getString("part").matches("[a-z0-9_./:-]{1,64}")||!ids.add(p.getString("part"))||ItemStack.of(p.getCompound("visual")).isEmpty())throw new IllegalArgumentException("Invalid preset part");}
    }
    private static CompoundTag builtin(boolean turbine){
        var n=new CompoundTag();n.putString("name",turbine?"Turbine":"Zombie Shield");n.putString("mode","item");n.putInt("ticks",40);n.put("reward",new ItemStack(turbine?PortableEquipment.TURBINE.get():PortableEquipment.SHIELD.get()).save(new CompoundTag()));var list=new ListTag();
        for(var item:turbine?PortableEquipment.PARTS.subList(2,5):PortableEquipment.PARTS.subList(0,2)){var p=new CompoundTag();p.putString("part",item.getId().toString());p.put("visual",new ItemStack(item.get()).save(new CompoundTag()));list.add(p);}n.put("presetPieces",list);return n;
    }
    private static CompoundTag sentry(){var n=new CompoundTag();n.putString("name","Sentry Turret");n.putString("mode","item");n.putInt("ticks",60);n.put("reward",new ItemStack(PortableEquipment.SENTRY.get()).save(new CompoundTag()));var parts=new ListTag();for(var item:PortableEquipment.SENTRY_PARTS){var p=new CompoundTag();p.putString("part",item.getId().toString());p.put("visual",new ItemStack(item.get()).save(new CompoundTag()));parts.add(p);}n.put("presetPieces",parts);return n;}
    private static CompoundTag packAPunch(){
        var n=new CompoundTag();n.putString("name","Pack-a-Punch");n.putString("mode","block");n.putInt("ticks",80);n.putInt("limit",1);
        n.put("reward",new ItemStack(me.cryo.zombierool.block.system.PackAPunchSystem.PACK_A_PUNCH_ITEM.get()).save(new CompoundTag()));
        var parts=new ListTag();for(var item:PortableEquipment.PAP_PARTS){var p=new CompoundTag();p.putString("part",item.getId().toString());p.put("visual",new ItemStack(item.get()).save(new CompoundTag()));parts.add(p);}
        n.put("presetPieces",parts);return n;
    }
    public static Map<String,CompoundTag> all(){var out=new LinkedHashMap<String,CompoundTag>();out.put("zombie_shield",builtin(false));out.put("turbine",builtin(true));out.put("sentry_turret",sentry());out.put("pack_a_punch",packAPunch());ADDONS.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->out.put(e.getKey(),e.getValue().copy()));data.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->out.put(e.getKey(),e.getValue().copy()));return out;}
    public static ListTag catalog(){var out=new ListTag();all().forEach((id,n)->{var copy=n.copy();copy.putString("id",id);out.add(copy);});return out;}
    public static CompoundTag get(String id){var n=all().get(id);if(n==null)throw new IllegalArgumentException("Unknown buildable preset: "+id);return n.copy();}
    /** Map-owned templates never leak to another world's catalogue. */
    public static Map<String,CompoundTag> all(net.minecraft.server.level.ServerLevel level){
        var out=all();if(level==null)return out;
        var dir=me.cryo.zombierool.util.WorldZombieroolPaths.getZombieroolDir(level).toPath().resolve("gameplay/buildable_presets");
        if(java.nio.file.Files.isDirectory(dir))try(var stream=java.nio.file.Files.list(dir)){
            for(var file:stream.filter(p->p.getFileName().toString().endsWith(".json")&&!java.nio.file.Files.isSymbolicLink(p)).sorted().toList())try{
                var object=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();var n=new CompoundTag();
                n.putString("name",object.get("name").getAsString());n.putString("mode",object.has("mode")?object.get("mode").getAsString():"item");n.putInt("ticks",object.has("ticks")?object.get("ticks").getAsInt():40);n.putInt("limit",object.has("limit")?object.get("limit").getAsInt():0);
                n.put("reward",net.minecraftforge.common.crafting.CraftingHelper.getItemStack(object.getAsJsonObject("reward"),true).save(new CompoundTag()));var parts=new ListTag();
                for(var value:object.getAsJsonArray("parts")){var part=value.getAsJsonObject();var entry=new CompoundTag();entry.putString("part",part.has("part")?part.get("part").getAsString():part.get("id").getAsString());entry.put("visual",net.minecraftforge.common.crafting.CraftingHelper.getItemStack(part,true).save(new CompoundTag()));parts.add(entry);}n.put("presetPieces",parts);validate(n);out.put(file.getFileName().toString().replaceFirst("\\.json$",""),n);
            }catch(Exception error){org.slf4j.LoggerFactory.getLogger(BuildablePresets.class).error("Invalid map buildable preset {}",file,error);}
        }catch(java.io.IOException error){org.slf4j.LoggerFactory.getLogger(BuildablePresets.class).error("Cannot read map buildable presets",error);}return out;
    }
    public static ListTag catalog(net.minecraft.server.level.ServerLevel level){var rows=new ListTag();all(level).forEach((id,n)->{var copy=n.copy();copy.putString("id",id);rows.add(copy);});return rows;}
    public static CompoundTag get(net.minecraft.server.level.ServerLevel level,String id){var n=all(level).get(id);if(n==null)throw new IllegalArgumentException("Unknown buildable preset: "+id);return n.copy();}
    @SubscribeEvent public static void reload(net.minecraftforge.event.AddReloadListenerEvent e){e.addListener(new net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener(new com.google.gson.Gson(),"buildable_presets"){
        @Override protected void apply(Map<ResourceLocation,com.google.gson.JsonElement> files,net.minecraft.server.packs.resources.ResourceManager resources,net.minecraft.util.profiling.ProfilerFiller profiler){
            var parsed=new LinkedHashMap<String,CompoundTag>();files.forEach((id,json)->{try{
                var object=json.getAsJsonObject();var n=new CompoundTag();n.putString("name",object.get("name").getAsString());n.putString("mode",object.has("mode")?object.get("mode").getAsString():"item");n.putInt("ticks",object.has("ticks")?object.get("ticks").getAsInt():40);n.putInt("limit",object.has("limit")?object.get("limit").getAsInt():0);n.put("reward",net.minecraftforge.common.crafting.CraftingHelper.getItemStack(object.getAsJsonObject("reward"),true).save(new CompoundTag()));var pieces=new ListTag();
                for(var element:object.getAsJsonArray("parts")){var part=element.getAsJsonObject();var p=new CompoundTag();p.putString("part",part.get("id").getAsString());p.put("visual",net.minecraftforge.common.crafting.CraftingHelper.getItemStack(part,true).save(new CompoundTag()));pieces.add(p);}n.put("presetPieces",pieces);validate(n);parsed.put(id.toString(),n);
            }catch(RuntimeException error){org.slf4j.LoggerFactory.getLogger(BuildablePresets.class).error("Invalid buildable preset {}",id,error);}});data=Map.copyOf(parsed);
        }
    });}
}
