package me.cryo.zombierool.block.system;

import me.cryo.zombierool.block.AbstractTechnicalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.*;
import net.minecraftforge.registries.*;
import java.util.*;

/** Persistent dimension-local music volumes. Only the winning volume is sent to each listener. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class MusicVolume {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"zombierool");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"zombierool");
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"zombierool");
    public static final RegistryObject<Block> BLOCK=BLOCKS.register("music_volume",VolumeBlock::new);
    public static final RegistryObject<Item> ITEM=ITEMS.register("music_volume",()->new BlockItem(BLOCK.get(),new Item.Properties()));
    public static final RegistryObject<BlockEntityType<VolumeEntity>> ENTITY=ENTITIES.register("music_volume",()->BlockEntityType.Builder.of(VolumeEntity::new,BLOCK.get()).build(null));
    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new net.minecraft.resources.ResourceLocation("zombierool","music_volumes"),()->"1","1"::equals,"1"::equals);
    private record Message(String action,BlockPos pos,CompoundTag config){}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);
        CHANNEL.registerMessage(0,Message.class,(m,b)->{b.writeUtf(m.action,16);b.writeBlockPos(m.pos);b.writeNbt(m.config);},b->new Message(b.readUtf(16),b.readBlockPos(),b.readNbt()),(m,c)->{c.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->me.cryo.zombierool.client.MusicVolumeClient.receive(m.action,m.pos,m.config)));c.get().setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1,Save.class,(m,b)->{b.writeBlockPos(m.pos);b.writeNbt(m.config);},b->new Save(b.readBlockPos(),b.readNbt()),(m,c)->{var ctx=c.get();ctx.enqueueWork(()->{var p=ctx.getSender();if(p!=null&&p.isCreative()&&p.hasPermissions(2)&&p.distanceToSqr(m.pos.getCenter())<=64&&m.config!=null&&p.level().getBlockEntity(m.pos) instanceof VolumeEntity zone)zone.configure(m.config);});ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    private record Save(BlockPos pos,CompoundTag config){}
    public static void save(BlockPos pos,CompoundTag n){CHANNEL.sendToServer(new Save(pos,n));}
    private static void send(ServerPlayer p,String action,BlockPos pos,CompoundTag n){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Message(action,pos,n));}
    public static CompoundTag defaults(){var n=new CompoundTag();n.putString("name","Music volume");n.putString("id","");n.putString("preset","default");n.putBoolean("enabled",true);n.putInt("radiusX",8);n.putInt("radiusZ",8);n.putInt("height",8);n.putInt("priority",0);n.putInt("fadeTicks",40);n.putDouble("volume",1);return n;}
    public static CompoundTag sanitize(CompoundTag in){var n=defaults();String id=in.getString("id").trim();if(id.matches("[a-zA-Z0-9_:-]{1,64}"))n.putString("id",id);String name=in.getString("name");if(!name.isBlank())n.putString("name",name.substring(0,Math.min(64,name.length())));String preset=in.getString("preset").trim();if(preset.matches("[a-z0-9_][a-z0-9_./:-]{0,159}")&&!preset.contains(".."))n.putString("preset",preset);if(in.contains("enabled"))n.putBoolean("enabled",in.getBoolean("enabled"));for(String k:List.of("radiusX","radiusZ","height"))if(in.contains(k))n.putInt(k,Math.max(1,Math.min(256,in.getInt(k))));n.putInt("priority",Math.max(-1000,Math.min(1000,in.getInt("priority"))));if(in.contains("fadeTicks"))n.putInt("fadeTicks",Math.max(0,Math.min(1200,in.getInt("fadeTicks"))));double volume=in.contains("volume")?in.getDouble("volume"):1;n.putDouble("volume",Double.isFinite(volume)?Math.max(0,Math.min(4,volume)):1);return n;}
    public static AABB bounds(BlockPos pos,CompoundTag n){return new AABB(pos.getX()-n.getInt("radiusX"),pos.getY(),pos.getZ()-n.getInt("radiusZ"),pos.getX()+n.getInt("radiusX")+1,pos.getY()+n.getInt("height"),pos.getZ()+n.getInt("radiusZ")+1);}
    public static final class Data extends net.minecraft.world.level.saveddata.SavedData {
        private final Map<BlockPos,CompoundTag> zones=new TreeMap<>();
        private static final class Listener {Set<BlockPos> inside=new HashSet<>();Map<BlockPos,CompoundTag> previous=new HashMap<>();CompoundTag current=new CompoundTag();Transition transition;Set<BlockPos> exited=new HashSet<>();}
        private final Map<UUID,Listener> listeners=new HashMap<>();
        public record Transition(BlockPos pos,String id,boolean active,String preset){}
        public void forget(UUID player){listeners.remove(player);}
        public void reset(UUID player,AABB body){var state=new Listener();for(var e:zones.entrySet())if(bounds(e.getKey(),e.getValue()).intersects(body))state.inside.add(e.getKey());listeners.put(player,state);}
        public CompoundTag list(){var n=new CompoundTag();zones.forEach((p,c)->{var row=c.copy();row.putInt("x",p.getX());row.putInt("y",p.getY());row.putInt("z",p.getZ());n.put(c.getString("id"),row);});return n;}
        public BlockPos find(String id){return zones.entrySet().stream().filter(e->e.getValue().getString("id").equals(id)).map(Map.Entry::getKey).findFirst().orElse(null);}
        public boolean active(UUID player,String id){var state=listeners.get(player);return state!=null&&state.previous.containsKey(find(id));}
        public CompoundTag toggle(UUID player,BlockPos pos){if(!zones.containsKey(pos)||!zones.get(pos).getBoolean("enabled"))return null;var state=listeners.computeIfAbsent(player,k->new Listener());boolean active=!state.previous.containsKey(pos);if(!active)state.current=state.previous.remove(pos).copy();else{state.previous.put(pos,state.current.copy());state.current=zones.get(pos).copy();state.current.putLong("pos",pos.asLong());}state.transition=new Transition(pos,zones.get(pos).getString("id"),active,state.current.getString("preset"));return state.current.copy();}
        /** Trigger on outside-to-inside edges. Leaving never changes the latched selection. */
        public CompoundTag enter(UUID player,AABB body){var state=listeners.computeIfAbsent(player,k->new Listener());state.transition=null;var inside=new HashSet<BlockPos>();BlockPos winner=null;double size=Double.MAX_VALUE;int priority=Integer.MIN_VALUE;
            for(var entry:zones.entrySet()){var n=entry.getValue();var box=bounds(entry.getKey(),n);if(!n.getBoolean("enabled")||!box.intersects(body))continue;inside.add(entry.getKey());if(state.inside.contains(entry.getKey()))continue;double volume=box.getXsize()*box.getYsize()*box.getZsize();if(winner==null||n.getInt("priority")>priority||n.getInt("priority")==priority&&volume<size){winner=entry.getKey();size=volume;priority=n.getInt("priority");}}
            state.exited=new HashSet<>(state.inside);state.exited.removeAll(inside);state.inside=inside;
            if(winner!=null)return toggle(player,winner);
            if(!state.current.isEmpty()){var origin=BlockPos.of(state.current.getLong("pos"));if(!zones.containsKey(origin)||!zones.get(origin).getBoolean("enabled")){state.current=state.previous.getOrDefault(origin,new CompoundTag()).copy();state.previous.remove(origin);return state.current.copy();}}
            return null;
        }
        public static Data get(ServerLevel level){return level.getDataStorage().computeIfAbsent(Data::load,Data::new,"zombierool_music_volumes");}
        private static Data load(CompoundTag n){var data=new Data();for(var row:n.getList("zones",10)){var entry=(CompoundTag)row;data.put(BlockPos.of(entry.getLong("pos")),entry.getCompound("config"));}return data;}
        @Override public CompoundTag save(CompoundTag n){var rows=new ListTag();zones.forEach((pos,config)->{var row=new CompoundTag();row.putLong("pos",pos.asLong());row.put("config",config.copy());rows.add(row);});n.put("zones",rows);return n;}
        public void put(BlockPos pos,CompoundTag config){if(zones.size()>=1024&&!zones.containsKey(pos))throw new IllegalArgumentException("Maximum 1024 music volumes");var clean=sanitize(config);String id=clean.getString("id");if(id.isEmpty())id="music_"+pos.getX()+"_"+pos.getY()+"_"+pos.getZ();String wanted=id;boolean duplicate=zones.entrySet().stream().anyMatch(e->!e.getKey().equals(pos)&&e.getValue().getString("id").equals(wanted));if(duplicate)id="music_"+pos.getX()+"_"+pos.getY()+"_"+pos.getZ();clean.putString("id",id);zones.put(pos.immutable(),clean);setDirty();}
        public void remove(BlockPos pos){if(zones.remove(pos)!=null)setDirty();}
        public CompoundTag at(Vec3 point){BlockPos winner=null;CompoundTag config=null;double size=Double.MAX_VALUE;for(var entry:zones.entrySet()){var n=entry.getValue();var box=bounds(entry.getKey(),n);double volume=box.getXsize()*box.getYsize()*box.getZsize();if(n.getBoolean("enabled")&&box.contains(point)&&(config==null||n.getInt("priority")>config.getInt("priority")||n.getInt("priority")==config.getInt("priority")&&volume<size)){winner=entry.getKey();config=n;size=volume;}}if(config==null)return new CompoundTag();var result=config.copy();result.putLong("pos",winner.asLong());return result;}
    }
    private static boolean gameplay(ServerPlayer p){return me.cryo.zombierool.gameplay.WaveManager.isGameRunning()&&me.cryo.zombierool.gameplay.MapActorBehavior.eligible(p);}
    private static void notifyToggle(ServerPlayer p,Data data,CompoundTag selection){send(p,"play",BlockPos.ZERO,selection);var state=data.listeners.get(p.getUUID());var t=state==null?null:state.transition;if(t!=null&&gameplay(p)&&me.cryo.zombierool.scripting.LuaScriptManager.ready(p.serverLevel()))me.cryo.zombierool.scripting.LuaScriptManager.eventResult("OnMusicVolumeToggle",p.getUUID().toString(),t.id(),t.active(),t.preset().isEmpty()?basePreset(p):t.preset());}
    private static String basePreset(ServerPlayer p){String preset=me.cryo.zombierool.config.WorldConfig.get(p.serverLevel()).getMusicPreset();return preset==null||preset.isBlank()?"default":preset;}
    public static boolean toggle(ServerPlayer p,String id){if(!me.cryo.zombierool.gameplay.WaveManager.isGameRunning()||!me.cryo.zombierool.gameplay.MapActorBehavior.eligible(p))return false;var data=Data.get(p.serverLevel());var pos=data.find(id);if(pos==null)return false;var selection=data.toggle(p.getUUID(),pos);if(selection==null)return false;notifyToggle(p,data,selection);return true;}
    public static boolean enabled(ServerLevel level,String id,boolean enabled){var data=Data.get(level);var pos=data.find(id);if(pos==null)return false;var n=data.zones.get(pos).copy();n.putBoolean("enabled",enabled);if(level.getBlockEntity(pos) instanceof VolumeEntity be)be.configure(n);else data.put(pos,n);return true;}
    public static void reset(ServerPlayer p){for(var level:p.server.getAllLevels())Data.get(level).forget(p.getUUID());Data.get(p.serverLevel()).reset(p.getUUID(),p.getBoundingBox());var n=new CompoundTag();n.putString("preset",basePreset(p));send(p,"reset",BlockPos.ZERO,n);}
    public static void resetAll(ServerLevel level){for(var p:level.getServer().getPlayerList().getPlayers())reset(p);}
    @SubscribeEvent public static void death(net.minecraftforge.event.entity.living.LivingDeathEvent e){if(!e.isCanceled()&&e.getEntity() instanceof ServerPlayer p)reset(p);}
    @SubscribeEvent public static void respawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p)reset(p);}
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.level instanceof ServerLevel level)||level.players().isEmpty())return;
        var data=Data.get(level);
        for(var p:level.players()){
            if(!p.isAlive()||p.isSpectator()||!p.isCreative()&&(!gameplay(p)||me.cryo.zombierool.gameplay.WaveManager.isPausedByPlayer()))continue;
            var n=data.enter(p.getUUID(),p.getBoundingBox());var state=data.listeners.get(p.getUUID());var transition=state.transition;
            if(n!=null)notifyToggle(p,data,n);
            if(gameplay(p)&&me.cryo.zombierool.scripting.LuaScriptManager.ready(level)){
                for(var pos:new HashSet<>(state.exited)){var zone=data.zones.get(pos);if(zone!=null)me.cryo.zombierool.scripting.LuaScriptManager.eventResult("OnMusicVolumeExit",p.getUUID().toString(),zone.getString("id"));}
                if(transition!=null)me.cryo.zombierool.scripting.LuaScriptManager.eventResult("OnMusicVolumeEnter",p.getUUID().toString(),transition.id());
            }
        }
    }
    @SubscribeEvent public static void login(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)for(var level:p.server.getAllLevels())Data.get(level).forget(p.getUUID());}
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)for(var level:p.server.getAllLevels())Data.get(level).forget(p.getUUID());}
    @SubscribeEvent public static void dimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p){for(var level:p.server.getAllLevels())Data.get(level).forget(p.getUUID());send(p,"play",BlockPos.ZERO,new CompoundTag());}}
    public static final class VolumeBlock extends AbstractTechnicalBlock implements EntityBlock {
        public VolumeBlock(){super(Properties.of().strength(-1,3600000).noCollission().noOcclusion());}
        @Override protected void addTechnicalTooltip(List<net.minecraft.network.chat.Component> lines){lines.add(net.minecraft.network.chat.Component.translatable("tooltip.zombierool.music_volume"));}
        @Override protected VoxelShape getTechnicalCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Shapes.empty();}
        @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new VolumeEntity(p,s);}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(player instanceof ServerPlayer p&&p.isCreative()&&p.hasPermissions(2)&&l.getBlockEntity(pos) instanceof VolumeEntity zone)send(p,"open",pos,zone.config());return InteractionResult.sidedSuccess(l.isClientSide);}
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){if(!s.is(next.getBlock())&&l instanceof ServerLevel server)Data.get(server).remove(pos);super.onRemove(s,l,pos,next,moving);}
        @Override public ItemStack getCloneItemStack(BlockState state,HitResult hit,BlockGetter level,BlockPos pos,Player player){var stack=new ItemStack(ITEM.get());if(level.getBlockEntity(pos) instanceof VolumeEntity zone)stack.getOrCreateTag().put("BlockEntityTag",zone.saveWithoutMetadata());return stack;}
    }
    public static final class VolumeEntity extends BlockEntity {
        private CompoundTag config=defaults();
        public VolumeEntity(BlockPos pos,BlockState state){super(ENTITY.get(),pos,state);}
        public CompoundTag config(){return config.copy();}
        public void configure(CompoundTag in){config=sanitize(in);setChanged();apply();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        private void apply(){if(level instanceof ServerLevel server){var data=Data.get(server);data.put(worldPosition,config);config=data.zones.get(worldPosition).copy();}}
        @Override public void onLoad(){super.onLoad();apply();}
        @Override public void load(CompoundTag n){super.load(n);if(n.contains("MusicVolume",10))config=sanitize(n.getCompound("MusicVolume"));apply();}
        @Override protected void saveAdditional(CompoundTag n){super.saveAdditional(n);n.put("MusicVolume",config.copy());}
        @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
        @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
    }
    private MusicVolume(){}
}
