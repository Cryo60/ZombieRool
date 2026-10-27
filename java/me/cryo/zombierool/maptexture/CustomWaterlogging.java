package me.cryo.zombierool.maptexture;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.*;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
/** Sparse, world-owned fluid identities: no multiplication of every vanilla block state. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool")
public final class CustomWaterlogging extends net.minecraft.world.level.saveddata.SavedData {
 private final Map<Long,Integer> slots=new HashMap<>();
 private static final Map<ServerLevel,CustomWaterlogging> DATA=new WeakHashMap<>();
 private static final Map<ServerLevel,Map<Long,Integer>> original=new WeakHashMap<>();
 public static final Map<String,Map<Long,Integer>> CLIENT=new java.util.concurrent.ConcurrentHashMap<>();
 public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new net.minecraft.resources.ResourceLocation("zombierool","map_waterlogged"),()->"1","1"::equals,"1"::equals);
 public record Update(String dimension,CompoundTag slots,boolean full){}
 @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool",bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
 public static final class Setup {
  @net.minecraftforge.eventbus.api.SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e){e.enqueueWork(()->CHANNEL.messageBuilder(Update.class,0,NetworkDirection.PLAY_TO_CLIENT).encoder((p,b)->{b.writeUtf(p.dimension);b.writeNbt(p.slots);b.writeBoolean(p.full);}).decoder(b->new Update(b.readUtf(),b.readNbt(),b.readBoolean())).consumerNetworkThread((p,c)->{c.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->Client.receive(p)));c.get().setPacketHandled(true);}).add());}
 }
 public static CustomWaterlogging get(ServerLevel level){return DATA.computeIfAbsent(level,l->l.getDataStorage().computeIfAbsent(CustomWaterlogging::load,CustomWaterlogging::new,"zombierool_custom_waterlogged"));}
 private static CustomWaterlogging load(CompoundTag n){var d=new CustomWaterlogging();var values=n.getCompound("slots");for(var k:values.getAllKeys())try{int slot=values.getInt(k);if(slot>=0&&slot<MapTextures.SLOTS)d.slots.put(Long.parseLong(k),slot);}catch(NumberFormatException ignored){}return d;}
 @Override public CompoundTag save(CompoundTag n){n.put("slots",tag(slots));return n;}
 private static CompoundTag tag(Map<Long,Integer> values){var n=new CompoundTag();values.forEach((pos,slot)->n.putInt(""+pos,slot));return n;}
 public static int fluidSlot(Fluid fluid){for(int i=0;i<MapTextures.SLOTS;i++)if(MapTextureWater.SOURCE[i]!=null&&MapTextureWater.SOURCE[i].isPresent()&&(fluid==MapTextureWater.SOURCE[i].get()||fluid==MapTextureWater.FLOW[i].get()))return i;return -1;}
 public static int slot(BlockGetter level,BlockPos pos){if(level instanceof ServerLevel server)return get(server).slots.getOrDefault(pos.asLong(),-1);return net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()?Client.slot(level,pos):-1;}
 public static void put(LevelAccessor level,BlockPos pos,int slot){
  if(!(level instanceof ServerLevel server))return;var d=get(server);int old=d.slots.getOrDefault(pos.asLong(),-1);if(old==slot)return;
  if(me.cryo.zombierool.gameplay.WaveManager.isGameRunning()&&!me.cryo.zombierool.gameplay.MatchWorldJournal.isRestoring())original.computeIfAbsent(server,k->new HashMap<>()).putIfAbsent(pos.asLong(),old);
  if(slot<0)d.slots.remove(pos.asLong());else{if(d.slots.size()>=16384&&!d.slots.containsKey(pos.asLong()))throw new IllegalStateException("Custom waterlogged block limit");d.slots.put(pos.asLong(),slot);}d.setDirty();
  var n=new CompoundTag();n.putInt(""+pos.asLong(),slot);var packet=new Update(server.dimension().location().toString(),n,false);for(var p:server.players())CHANNEL.send(PacketDistributor.PLAYER.with(()->p),packet);
 }
 public static void restore(ServerLevel level,BlockPos pos){var values=original.get(level);if(values!=null&&values.containsKey(pos.asLong()))put(level,pos,values.remove(pos.asLong()));}
 public static BlockState placement(Level level,BlockPos pos,BlockState next){
  int stored=slot(level,pos);var before=level.getBlockState(pos);
  if(stored>=0&&next.is(net.minecraft.world.level.block.Blocks.WATER)&&before.hasProperty(BlockStateProperties.WATERLOGGED)&&before.getValue(BlockStateProperties.WATERLOGGED))return MapTextureWater.BLOCKS[stored].get().defaultBlockState();
  if(next.hasProperty(BlockStateProperties.WATERLOGGED)&&!before.is(next.getBlock())){
   int incoming=fluidSlot(level.getFluidState(pos).getType());
   if(incoming>=0){put(level,pos,incoming);return next.setValue(BlockStateProperties.WATERLOGGED,true);}
  }
  return next;
 }
 public static void changed(Level level,BlockPos pos){
  var state=level.getBlockState(pos);int slot=slot(level,pos);
  if(slot>=0&&(!state.hasProperty(BlockStateProperties.WATERLOGGED)||!state.getValue(BlockStateProperties.WATERLOGGED)))put(level,pos,-1);
  if(level instanceof ServerLevel server&&!get(server).slots.isEmpty())for(var at:BlockPos.betweenClosed(pos.offset(-1,-1,-1),pos.offset(1,1,1))){int water=slot(level,at);if(water>=0)level.scheduleTick(at.immutable(),MapTextureWater.SOURCE[water].get(),5);}
 }
 @net.minecraftforge.eventbus.api.SubscribeEvent public static void login(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Update(p.serverLevel().dimension().location().toString(),tag(get(p.serverLevel()).slots),true));}
 @net.minecraftforge.eventbus.api.SubscribeEvent public static void unload(net.minecraftforge.event.level.LevelEvent.Unload e){if(e.getLevel() instanceof ServerLevel l){original.remove(l);DATA.remove(l);}}
 @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
 public static final class Client {
  public static int slot(BlockGetter level,BlockPos pos){var mc=net.minecraft.client.Minecraft.getInstance();if(mc.level==null)return -1;return CLIENT.getOrDefault(mc.level.dimension().location().toString(),Map.of()).getOrDefault(pos.asLong(),-1);}
  static void receive(Update p){if(p.slots==null||p.slots.size()>16384)return;var map=CLIENT.computeIfAbsent(p.dimension,k->new java.util.concurrent.ConcurrentHashMap<>());if(p.full)map.clear();var level=net.minecraft.client.Minecraft.getInstance().level;for(var k:p.slots.getAllKeys())try{long value=Long.parseLong(k);int slot=p.slots.getInt(k);if(slot<0)map.remove(value);else if(slot<MapTextures.SLOTS)map.put(value,slot);if(level!=null&&level.dimension().location().toString().equals(p.dimension)){var pos=BlockPos.of(value);level.sendBlockUpdated(pos,level.getBlockState(pos),level.getBlockState(pos),3);}}catch(NumberFormatException ignored){}}
 }
}
