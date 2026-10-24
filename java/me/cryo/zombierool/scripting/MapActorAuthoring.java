package me.cryo.zombierool.scripting;

import me.cryo.zombierool.gameplay.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import java.nio.file.*;

/** Uses the existing operator-only Lua workbench transport and conflict checks. */
public final class MapActorAuthoring {
    public static CompoundTag request(ServerPlayer player,String action,String name,String code,String version) throws Exception {
        var out=new CompoundTag();out.putString("action",action);out.putString("name",name);var level=player.serverLevel();var data=MapActors.get(level);
        switch(action) {
            case "actor_list" -> {var list=new ListTag();for(String id:data.catalogueIds())list.add(StringTag.valueOf(id));out.put("files",list);out.put("names",data.catalogue().getCompound("names"));out.put("nameKeys",data.catalogue().getCompound("nameKeys"));var defaults=new CompoundTag();for(String id:data.catalogueIds()){var profile=data.profile(id);for(String key:java.util.List.of("boxes","bones","animations","armorBoxes","appearances"))profile.remove(key);defaults.put(id,profile);}out.put("defaults",defaults);}
            case "actor_egg" -> {var profile=data.profile(name);if(profile.isEmpty())throw new IllegalArgumentException("Unknown profile");if(!player.isCreative())throw new IllegalArgumentException("Creative mode required");var stack=me.cryo.zombierool.item.MapActorSpawnEggItem.egg(name,profile.getString("name"),profile.getString("nameKey"));if(!player.getInventory().add(stack))player.drop(stack,false);out.putString("message","Spawn egg added to your inventory");}
            case "actor_import" -> out.putString("message","Imported "+data.importFiles(level)+" map profiles");
            case "actor_read" -> {var p=data.profile(name);if(p.isEmpty())throw new IllegalArgumentException("Unknown profile");String json=MapActors.json(p);out.putString("code",json);out.putString("version",LuaWorkbench.hash(json));}
            case "actor_save" -> {
                MapActors.id(name);var old=data.profile(name);
                if(!old.isEmpty()&&!LuaWorkbench.hash(MapActors.json(old)).equals(version))throw new IllegalArgumentException("Profile changed: reopen it before saving");
                if(old.isEmpty()&&!version.isEmpty())throw new IllegalArgumentException("Profile removed: reopen it");
                data.put(level,name,MapActors.parse(code));String json=MapActors.json(data.profile(name));out.putString("code",json);out.putString("version",LuaWorkbench.hash(json));out.putString("message","Map profile saved");
            }
            case "actor_script" -> {
                String callback=data.profile(name).getString("callback");MapActors.id(callback);Path file=LuaWorkbench.file(player,callback+".lua");
                Files.writeString(file,MapActorTemplates.script(callback),StandardOpenOption.CREATE_NEW);out.putString("message","Created zombierool/scripts/"+callback+".lua. Open Lua editor, then Reload.");
            }
            case "actor_spawn" -> {
                if(!player.isCreative())throw new IllegalArgumentException("Creative mode required for preview");
                var at=player.position().add(player.getLookAngle().multiply(3,0,3));String id=data.spawn(level,name,at,"");if(id.isEmpty())throw new IllegalArgumentException("No safe spawn space");out.putString("message","Preview spawned: "+id);
            }
            case "actor_marker_read","actor_marker_save" -> {
                var pos=BlockPos.of(Long.parseLong(name));
                if(!level.hasChunkAt(pos)||!level.getBlockState(pos).is(me.cryo.zombierool.block.system.UniversalSpawnerSystem.ACTOR_MARKER_BLOCK.get()))throw new IllegalArgumentException("Marker no longer exists");
                var old=data.marker(pos);String current=old.isEmpty()?"":MapActors.json(old);
                if(action.equals("actor_marker_save")){
                    if(!LuaWorkbench.hash(current).equals(version))throw new IllegalArgumentException("Marker changed: reopen it");
                    data.marker(pos,MapActors.parse(code));if(level.getBlockEntity(pos) instanceof me.cryo.zombierool.block.MapActorMarkerBlock.MarkerData be)be.configure(data.marker(pos));out.putString("message","Marker saved");
                }
                var n=data.marker(pos);String actual=n.isEmpty()?"":MapActors.json(n);out.putString("code",n.isEmpty()?MapActors.json(MapActorTemplates.marker()):actual);out.putString("version",LuaWorkbench.hash(actual));
            }
            default -> throw new IllegalArgumentException("Unknown actor action");
        }
        if(action.equals("actor_save")||action.equals("actor_import"))for(var online:level.players())me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->online),new LuaWorkbenchPackets.Response(data.catalogue()));
        out.putBoolean("success",true);return out;
    }
    private MapActorAuthoring(){}
}
