package me.cryo.zombierool.gameplay;

import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.util.zip.*;
import net.minecraft.server.level.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.*;

/** Definitions and visuals belong to the save. Registry identities remain stable for saved stacks. */
public final class MapWeaponPacks {
    public static final Set<String> RESERVED=Set.of("fire_wand","shadow_wand","longbow","scarlet_crossbow","scarlet_sword","scarlet_axe","scarlet_halberd","scarlet_shield");
    private static final Set<String> active=new HashSet<>();
    private static byte[] resources=new byte[0];
    private static final int LIMIT=32*1024*1024,CHUNK=512*1024;
    private static final net.minecraftforge.registries.DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS=net.minecraftforge.registries.DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS,"zombierool");
    public static void registerSounds(net.minecraftforge.eventbus.api.IEventBus bus){for(var id:List.of("map_powder_fire","map_powder_load","map_bow_release","map_bow_pull"))SOUNDS.register(id,()->net.minecraft.sounds.SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool",id)));SOUNDS.register(bus);}
    private record Part(int index,int total,byte[] bytes){}
    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("zombierool","map_weapon_packs"),()->"1","1"::equals,"1"::equals);
    public static void register(){CHANNEL.registerMessage(0,Part.class,(m,b)->{b.writeInt(m.index);b.writeInt(m.total);b.writeByteArray(m.bytes);},b->new Part(b.readInt(),b.readInt(),b.readByteArray(CHUNK)),(m,c)->{c.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->me.cryo.zombierool.client.MapWeaponPackClient.part(m.index,m.total,m.bytes)));c.get().setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));}
    public static boolean available(String id){return MapArmor.available(id)&&(!(RESERVED.contains(id)||id.startsWith("stratholme_"))||active.contains(id));}
    public static void clear(){active.clear();}
    public static void activate(String id){if(RESERVED.contains(id)||id.startsWith("stratholme_"))active.add(id);}
    public static List<Path> definitions(ServerLevel level){
        clear();MapArmor.clear();resources=new byte[0];Path root=me.cryo.zombierool.util.WorldZombieroolPaths.resolve(me.cryo.zombierool.util.WorldZombieroolPaths.getZombieroolDir(level),"weapon_packs").toPath();var defs=new ArrayList<Path>();var entries=new TreeMap<String,byte[]>();long size=0;
        try{
            if(!Files.isDirectory(root))return defs;
            try(var directories=Files.list(root)){
                for(var pack:directories.filter(Files::isDirectory).sorted().toList()){
                    if(Files.isSymbolicLink(pack)||!Files.isRegularFile(pack.resolve("pack.json")))continue;
                    var config=com.google.gson.JsonParser.parseString(Files.readString(pack.resolve("pack.json"))).getAsJsonObject();
                    if(config.has("enabled")&&!config.get("enabled").getAsBoolean())continue;
                    MapArmor.load(config);
                    Path weapons=pack.resolve("weapons");
                    if(Files.isDirectory(weapons))try(var files=Files.list(weapons)){defs.addAll(files.filter(p->Files.isRegularFile(p)&&!Files.isSymbolicLink(p)&&p.toString().endsWith(".json")).sorted().toList());}
                    Path assets=pack.resolve("assets");if(!Files.isDirectory(assets))continue;
                    try(var files=Files.walk(assets,10)){
                        for(var file:files.filter(Files::isRegularFile).sorted().toList()){
                            String name=pack.relativize(file).toString().replace('\\','/');long bytes=Files.size(file);
                            if(Files.isSymbolicLink(file)||!file.toRealPath().startsWith(pack.toRealPath())||!name.matches("assets/[a-z0-9_.-]+/[a-z0-9_./-]+")||!(name.endsWith(".json")||name.endsWith(".png")||name.endsWith(".ogg"))||bytes>4*1024*1024||size+bytes>LIMIT)throw new IOException("Invalid or oversized map pack asset: "+name);
                            if(entries.putIfAbsent(name,Files.readAllBytes(file))!=null)throw new IOException("Duplicate map pack asset: "+name);size+=bytes;
                        }
                    }
                }
            }
            if(!entries.isEmpty()){
                var out=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(out)){
                    zip.putNextEntry(new ZipEntry("pack.mcmeta"));zip.write("{\"pack\":{\"pack_format\":15,\"description\":\"Map weapon pack\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
                    for(var e:entries.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();}
                }resources=out.toByteArray();if(resources.length>LIMIT)throw new IOException("Map weapon resource pack exceeds 32 MB");
            }
        }catch(Exception e){clear();MapArmor.clear();resources=new byte[0];defs.clear();me.cryo.zombierool.ZombieroolMod.LOGGER.error("Cannot load map weapon packs",e);}
        return defs;
    }
    public static void sync(ServerPlayer p){int count=(resources.length+CHUNK-1)/CHUNK;var target=PacketDistributor.PLAYER.with(()->p);CHANNEL.send(target,new Part(-1,resources.length,new byte[0]));for(int i=0;i<count;i++)CHANNEL.send(target,new Part(i,resources.length,Arrays.copyOfRange(resources,i*CHUNK,Math.min(resources.length,(i+1)*CHUNK))));CHANNEL.send(target,new Part(-2,resources.length,new byte[0]));}
    private MapWeaponPacks(){}
}
