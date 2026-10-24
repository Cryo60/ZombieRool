package me.cryo.zombierool.gameplay;

import com.google.gson.*;
import me.cryo.zombierool.block.system.MapDeviceSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

/** Sparse, shared world-space colliders. No invisible blocks, proxies, entities or terrain replacement. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool")
public final class VehicleDecorPhysics {
    private static final Map<String,List<AABB>> PROFILES=load();
    private static final Map<Level,World> WORLDS=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<net.minecraft.server.level.ServerPlayer,Vec3> SAFE=new WeakHashMap<>();
    private record Cell(BlockPos owner,VoxelShape shape){}
    public record Geometry(AABB bounds,List<AABB> boxes,List<AABB> movement){}
    private static final class World {long tick=Long.MIN_VALUE;final Map<BlockPos,Geometry> geometry=new HashMap<>();final Map<Long,List<Cell>> cells=new HashMap<>();final Map<BlockPos,Integer> keys=new HashMap<>();}
    private static Map<String,List<AABB>> load(){var map=new HashMap<String,List<AABB>>();try(var in=VehicleDecorPhysics.class.getClassLoader().getResourceAsStream("data/zombierool/scenery/vehicle_collision.json");var r=new java.io.InputStreamReader(java.util.Objects.requireNonNull(in),java.nio.charset.StandardCharsets.UTF_8)){
        for(var entry:JsonParser.parseReader(r).getAsJsonObject().entrySet()){var boxes=new ArrayList<AABB>();for(var row:entry.getValue().getAsJsonArray()){var a=row.getAsJsonArray();boxes.add(new AABB(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble(),a.get(3).getAsDouble(),a.get(4).getAsDouble(),a.get(5).getAsDouble()));}map.put(entry.getKey(),List.copyOf(boxes));}
    }catch(Exception e){throw new IllegalStateException("Missing vehicle collision profiles",e);}return Map.copyOf(map);}
    public static AABB localBounds(net.minecraft.nbt.CompoundTag config){AABB bounds=null;for(var box:PROFILES.getOrDefault(AmbientScenery.vehicleMesh(config),List.of()))bounds=bounds==null?box:bounds.minmax(box);return bounds==null?new AABB(0,0,0,1,1,1):bounds;}
    public static org.joml.Matrix4d transform(net.minecraft.nbt.CompoundTag n){var m=new org.joml.Matrix4d().rotateY(Math.toRadians(n.getDouble("yaw"))).rotateX(Math.toRadians(n.getDouble("pitch"))).rotateZ(Math.toRadians(n.getDouble("roll"))).scale(n.getDouble("size"));if(n.getString("variant").equals("crashed"))m.translate(0,.15,0).rotateZ(Math.toRadians(n.getString("vehicle").equals("helicopter")?33:17)).rotateX(Math.toRadians(-11));return m;}
    private static Geometry bake(MapDeviceSystem.Device d){var n=d.scenery;String key=AmbientScenery.vehicleMesh(n);var transform=transform(n);var boxes=new ArrayList<AABB>();AABB bounds=null;
        boolean rotated=Math.abs(n.getDouble("yaw")%90)>.001||n.getDouble("pitch")!=0||n.getDouble("roll")!=0||n.getString("variant").equals("crashed");
        for(var b:PROFILES.getOrDefault(key,List.of())){int steps=rotated?Math.max(1,(int)Math.ceil(b.getXsize()/.6)):1;for(int part=0;part<steps;part++){double x0=net.minecraft.util.Mth.lerp(part/(double)steps,b.minX,b.maxX),x1=net.minecraft.util.Mth.lerp((part+1)/(double)steps,b.minX,b.maxX);double[] lo={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},hi={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
            for(int i=0;i<8;i++){var p=transform.transformPosition(new org.joml.Vector3d((i&1)==0?x0:x1,(i&2)==0?b.minY:b.maxY,(i&4)==0?b.minZ:b.maxZ));lo[0]=Math.min(lo[0],p.x);lo[1]=Math.min(lo[1],p.y);lo[2]=Math.min(lo[2],p.z);hi[0]=Math.max(hi[0],p.x);hi[1]=Math.max(hi[1],p.y);hi[2]=Math.max(hi[2],p.z);}
            var world=new AABB(lo[0],lo[1],lo[2],hi[0],hi[1],hi[2]).move(d.getBlockPos().getX()+.5,d.getBlockPos().getY(),d.getBlockPos().getZ()+.5);boxes.add(world);bounds=bounds==null?world:bounds.minmax(world);
        }}
        if(bounds==null)bounds=new AABB(d.getBlockPos());
        // Movement must not inherit the mesh's 0.6-block ledges or hollow wheel/wing pockets.
        // Fill the horizontal silhouette from the base to one stable, unclimbable top plane.
        // Precise source bands remain available for selection and weapon rays.
        double bottom=Math.min(d.getBlockPos().getY(),bounds.minY),top=Math.max(bounds.maxY,bottom+2.0);
        var footprint=new HashMap<String,AABB>();for(var box:boxes){var solid=new AABB(box.minX,bottom,box.minZ,box.maxX,top,box.maxZ);String footprintKey=box.minX+":"+box.minZ+":"+box.maxX+":"+box.maxZ;footprint.putIfAbsent(footprintKey,solid);}
        return new Geometry(bounds,List.copyOf(boxes),List.copyOf(footprint.values()));
    }
    public static void invalidate(Level l){if(l!=null){var w=WORLDS.get(l);if(w!=null)w.tick=Long.MIN_VALUE;}}
    public static void unload(Level l){WORLDS.remove(l);}
    private static World world(Level l){var w=WORLDS.computeIfAbsent(l,k->new World());if(w.tick==l.getGameTime())return w;w.tick=l.getGameTime();var alive=new HashSet<BlockPos>();boolean changed=false;
        for(var d:MapDeviceSystem.devices(l))if(!d.isRemoved()&&d.kind()==MapDeviceSystem.Kind.VEHICLE&&d.enabled&&d.getBlockState().getValue(MapDeviceSystem.DeviceBlock.ACTIVE)){var pos=d.getBlockPos();alive.add(pos);int key=d.scenery.hashCode();if(!Objects.equals(w.keys.get(pos),key)){w.keys.put(pos,key);w.geometry.put(pos,bake(d));changed=true;}}
        if(w.geometry.keySet().removeIf(p->!alive.contains(p))){w.keys.keySet().retainAll(alive);changed=true;}
        if(changed){w.cells.clear();for(var entry:w.geometry.entrySet()){var cells=new HashMap<Long,VoxelShape>();for(var box:entry.getValue().movement){for(int x=net.minecraft.util.Mth.floor(box.minX);x<=net.minecraft.util.Mth.floor(box.maxX-1e-7);x++)for(int y=net.minecraft.util.Mth.floor(box.minY);y<=net.minecraft.util.Mth.floor(box.maxY-1e-7);y++)for(int z=net.minecraft.util.Mth.floor(box.minZ);z<=net.minecraft.util.Mth.floor(box.maxZ-1e-7);z++){var cell=new BlockPos(x,y,z);var clipped=box.intersect(new AABB(cell));var shape=Shapes.create(clipped.move(-x,-y,-z));cells.merge(cell.asLong(),shape,Shapes::or);}}
            for(var cell:cells.entrySet()){var pos=BlockPos.of(cell.getKey());w.cells.computeIfAbsent(cell.getKey(),k->new ArrayList<>()).add(new Cell(entry.getKey(),cell.getValue().move(pos.getX(),pos.getY(),pos.getZ())));}
        }}return w;
    }
    public static Level level(BlockGetter getter){return getter instanceof Level l?l:getter instanceof me.cryo.zombierool.mixins.VehicleNavigationRegionAccessor a?a.zr$vehicleLevel():null;}
    public static Geometry geometry(Level l,BlockPos p){return world(l).geometry.get(p);}
    public static List<VoxelShape> collisions(Level l,AABB area){var w=world(l);if(w.cells.isEmpty())return List.of();var out=new ArrayList<VoxelShape>();for(int x=net.minecraft.util.Mth.floor(area.minX);x<=net.minecraft.util.Mth.floor(area.maxX);x++)for(int y=net.minecraft.util.Mth.floor(area.minY);y<=net.minecraft.util.Mth.floor(area.maxY);y++)for(int z=net.minecraft.util.Mth.floor(area.minZ);z<=net.minecraft.util.Mth.floor(area.maxZ);z++)for(var c:w.cells.getOrDefault(BlockPos.asLong(x,y,z),List.of()))if(c.shape.bounds().intersects(area))out.add(c.shape);return out;}
    public static BlockHitResult pick(Level l,Vec3 from,Vec3 to,BlockHitResult original){var w=world(l);if(w.geometry.isEmpty())return original;double best=original.getType()==HitResult.Type.MISS?from.distanceToSqr(to)+1e-6:from.distanceToSqr(original.getLocation());BlockHitResult hit=original;
        for(var entry:w.geometry.entrySet()){var g=entry.getValue();if(!g.bounds.contains(from)&&g.bounds.clip(from,to).isEmpty())continue;for(var box:g.boxes){var h=Shapes.create(box).clip(from,to,BlockPos.ZERO);if(h!=null&&from.distanceToSqr(h.getLocation())<best){best=from.distanceToSqr(h.getLocation());hit=new BlockHitResult(h.getLocation(),h.getDirection(),entry.getKey(),h.isInside());}}}return hit;
    }
    public static boolean reachable(Level l,BlockPos pos,Vec3 eye,double reach){var g=geometry(l,pos);if(g==null)return false;for(var b:g.boxes)if((Math.pow(Math.max(0,Math.max(b.minX-eye.x,eye.x-b.maxX)),2)+Math.pow(Math.max(0,Math.max(b.minY-eye.y,eye.y-b.maxY)),2)+Math.pow(Math.max(0,Math.max(b.minZ-eye.z,eye.z-b.maxZ)),2))<=reach*reach)return true;return false;}
    /** Recover a player embedded by an edited/enabled vehicle or by an old saved position. */
    public static boolean recover(net.minecraft.server.level.ServerPlayer player){
        if(player.isSpectator()||!player.isAlive())return false;var l=player.serverLevel();var body=player.getBoundingBox().deflate(.045);
        if(collisions(l,body).isEmpty()){SAFE.put(player,player.position());return false;}
        var candidates=new ArrayList<Vec3>();var previous=SAFE.get(player);if(previous!=null&&previous.distanceToSqr(player.position())<.75*.75)candidates.add(previous);
        double half=player.getBbWidth()*.5+.025;for(var g:world(l).geometry.values())if(g.movement.stream().anyMatch(b->b.intersects(body))){
            double x=player.getX(),y=player.getY(),z=player.getZ();
            for(var box:g.movement)if(box.intersects(body)) {
                candidates.add(new Vec3(box.minX-half,y,z));candidates.add(new Vec3(box.maxX+half,y,z));
                candidates.add(new Vec3(x,y,box.minZ-half));candidates.add(new Vec3(x,y,box.maxZ+half));
            }
        }
        candidates.sort(Comparator.comparingDouble(player.position()::distanceToSqr));
        for(var at:candidates){double radius=player.getBbWidth()*.5;var standing=new AABB(at.x-radius,at.y,at.z-radius,at.x+radius,at.y+1.8,at.z+radius);if(!l.hasChunkAt(BlockPos.containing(at))||!l.noCollision(player,standing))continue;
            player.setSwimming(false);player.stopFallFlying();player.setPose(net.minecraft.world.entity.Pose.STANDING);player.setDeltaMovement(Vec3.ZERO);player.setPos(at.x,at.y,at.z);player.teleportTo(l,at.x,at.y,at.z,player.getYRot(),player.getXRot());SAFE.put(player,at);return true;
        }return false;
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void playerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event){if(event.phase==net.minecraftforge.event.TickEvent.Phase.END&&event.player instanceof net.minecraft.server.level.ServerPlayer p)recover(p);}
    private VehicleDecorPhysics(){}
}
