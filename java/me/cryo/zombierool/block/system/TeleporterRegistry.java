package me.cryo.zombierool.block.system;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Keeps destination IDs and Pack-a-Punch requirements visible across unloaded chunks. */
public final class TeleporterRegistry extends SavedData {
    public final Map<BlockPos,CompoundTag> pads=new HashMap<>();
    private final Map<String,Set<String>> conditions=new HashMap<>();
    private final Map<String,String> aliases=new HashMap<>();
    private int nextPad=1,nextSwitch=1;
    private static final List<String> SHARED=List.of("requiresPower","enabled","chargeTime","cost","cooldown","sharedCooldown","allPlayers","requiresLink","relink","linkTimeout","papLinks","requiredSwitches");
    public static TeleporterRegistry get(ServerLevel level){return level.getDataStorage().computeIfAbsent(TeleporterRegistry::load,TeleporterRegistry::new,"zombierool_teleporters");}
    private static TeleporterRegistry load(CompoundTag n){
        TeleporterRegistry data=new TeleporterRegistry();for(Tag tag:n.getList("pads",10)){CompoundTag p=(CompoundTag)tag;data.pads.put(BlockPos.of(p.getLong("pos")),p.getCompound("config").copy());}
        data.nextPad=Math.max(1,n.getInt("nextPad"));data.nextSwitch=Math.max(1,n.getInt("nextSwitch"));
        CompoundTag names=n.getCompound("aliases");for(String key:names.getAllKeys())data.aliases.put(key,names.getString(key));
        for(var entry:List.copyOf(data.pads.entrySet()))data.normalize(entry.getKey(),entry.getValue());
        data.pads.values().forEach(data::references);return data;
    }
    @Override public CompoundTag save(CompoundTag n){ListTag list=new ListTag();pads.forEach((pos,config)->{CompoundTag p=new CompoundTag();p.putLong("pos",pos.asLong());p.put("config",config.copy());list.add(p);});n.put("pads",list);n.putInt("nextPad",nextPad);n.putInt("nextSwitch",nextSwitch);CompoundTag names=new CompoundTag();aliases.forEach(names::putString);n.put("aliases",names);return n;}
    public String resolve(String id){String original=id;if(pads.values().stream().anyMatch(c->c.getString("id").equals(original)))return id;Set<String> seen=new HashSet<>();while(aliases.containsKey(id)&&seen.add(id))id=aliases.get(id);return id;}
    private String allocate(boolean isSwitch){String id;do{id=(isSwitch?"D":"T")+(isSwitch?nextSwitch++:nextPad++);}while(!named(id).isEmpty()||aliases.containsKey(id));return id;}
    public void references(CompoundTag c){for(String key:List.of("destination","returnId","condition","blockedCondition"))c.putString(key,resolve(c.getString(key)));String list=c.getString("secretIds");if(!list.isBlank())c.putString("secretIds",Arrays.stream(list.split(",")).map(String::trim).map(this::resolve).reduce((a,b)->a+", "+b).orElse(""));}
    public void normalize(BlockPos pos,CompoundTag c){
        String old=c.getString("id"),legacy="tp_"+pos.getX()+"_"+pos.getY()+"_"+pos.getZ();
        if(old.isBlank()||old.equals(legacy)){
            CompoundTag stored=pads.get(pos);String id=stored==null?"":stored.getString("id");
            if(id.isBlank()||id.equals(legacy))id=allocate(c.getString("role").equals("SWITCH"));
            c.putString("id",id);if(!old.isBlank())aliases.put(old,id);setDirty();
        }
        if(c.getString("role").equals("SWITCH")&&(c.getString("condition").isBlank()||c.getString("condition").equals("defcon_"+pos.asLong()))){String oldCondition=c.getString("condition"),condition="defcon"+c.getString("id");c.putString("condition",condition);if(!oldCondition.isBlank())aliases.put(oldCondition,condition);setDirty();}
        references(c);
    }
    public void update(TeleporterSystem.Pad pad){normalize(pad.getBlockPos(),pad.config);CompoundTag n=pad.config.copy();n.putBoolean("linked",pad.linked);n.putLong("pendingUntil",pad.pendingUntil);n.putLong("cooldownUntil",pad.cooldownUntil);pads.put(pad.getBlockPos(),n);setDirty();}
    private Set<String> destinations(CompoundTag c){Set<String> ids=new HashSet<>();for(String key:List.of("destination","returnId"))if(!c.getString(key).isBlank())ids.add(resolve(c.getString(key)));for(String id:c.getString("secretIds").split(","))if(!id.isBlank())ids.add(resolve(id.trim()));return ids;}
    private Set<BlockPos> connected(BlockPos root){
        Set<BlockPos> result=new HashSet<>();result.add(root);boolean changed;
        do{changed=false;for(var a:List.copyOf(result)){CompoundTag c=pads.get(a);if(c==null)continue;Set<String> links=destinations(c);for(var b:pads.entrySet()){
            CompoundTag other=b.getValue();boolean network=Set.of("DEFCON","DENIZEN").contains(c.getString("mode"))&&c.getString("mode").equals(other.getString("mode"))&&c.getString("channel").equals(other.getString("channel"));
            if((links.contains(other.getString("id"))||destinations(other).contains(c.getString("id"))||network)&&result.add(b.getKey()))changed=true;
        }}}while(changed);return result;
    }
    public void synchronize(ServerLevel level,TeleporterSystem.Pad source,CompoundTag previous){
        String oldId=previous.getString("id"),newId=source.config.getString("id");
        if(!oldId.isBlank()&&!oldId.equals(newId)&&pads.values().stream().noneMatch(c->c.getString("id").equals(oldId))){
            aliases.put(oldId,newId);pads.values().forEach(this::references);
            for(var pad:TeleporterSystem.loaded(level)){references(pad.config);pad.sync();}setDirty();
        }
        List<String> changed=SHARED.stream().filter(key->!Objects.equals(previous.get(key),source.config.get(key))).toList();
        if(changed.isEmpty())return;
        for(BlockPos pos:connected(source.getBlockPos()))if(!pos.equals(source.getBlockPos())){
            CompoundTag config=pads.get(pos);for(String key:changed)config.put(key,source.config.get(key).copy());
            if(changed.contains("requiresLink")){config.putLong("pendingUntil",0);config.putBoolean("linked",false);}
            if(level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof TeleporterSystem.Pad pad){
                for(String key:changed)pad.config.put(key,config.get(key).copy());
                if(changed.contains("requiresLink")){pad.pendingUntil=0;pad.linked=false;}pad.sync();
            }
        }setDirty();
    }
    public void remove(BlockPos p){pads.remove(p);setDirty();}
    public boolean condition(String channel,String key){return conditions.getOrDefault(channel,Set.of()).contains(resolve(key));}
    public void condition(String channel,String key,boolean active){Set<String> set=conditions.computeIfAbsent(channel,k->new HashSet<>());if(active)set.add(resolve(key));else set.remove(resolve(key));}
    public int conditionCount(String channel){return (int)conditions.getOrDefault(channel,Set.of()).stream().filter(key->key.startsWith("defcon")).count();}
    public void clearConditions(String channel){conditions.remove(channel);}
    public void reset(){conditions.clear();pads.values().forEach(c->{c.putBoolean("linked",false);c.putLong("pendingUntil",0);c.putLong("cooldownUntil",0);});setDirty();}
    public List<BlockPos> named(String id){String name=resolve(id);return pads.entrySet().stream().filter(e->e.getValue().getString("id").equals(name)).map(Map.Entry::getKey).sorted(Comparator.comparingLong(BlockPos::asLong)).toList();}
    public static boolean isSource(CompoundTag c){return c.getString("role").equals("SOURCE");}
    public boolean packUnlocked(){
        Map<String,Integer> required=new HashMap<>(),linked=new HashMap<>();
        pads.values().forEach(c->{if(c.getBoolean("enabled")&&isSource(c)){
            String channel=c.getString("channel");required.merge(channel,c.getInt("papLinks"),Math::max);
            if(c.getBoolean("linked")&&c.getInt("papLinks")>0)linked.merge(channel,1,Integer::sum);
        }});
        return required.entrySet().stream().allMatch(e->linked.getOrDefault(e.getKey(),0)>=e.getValue());
    }
}
