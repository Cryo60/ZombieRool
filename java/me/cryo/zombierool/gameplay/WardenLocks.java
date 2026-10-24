package me.cryo.zombierool.gameplay;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;
import java.util.*;

/** Physical map locks; the key is a team entitlement, never a death-droppable inventory item. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class WardenLocks extends SavedData {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"zombierool");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"zombierool");
    public static final RegistryObject<Block> LOCK=BLOCKS.register("warden_lock",LockBlock::new);
    public static final RegistryObject<Item> LOCK_ITEM=ITEMS.register("warden_lock",()->new BlockItem(LOCK.get(),new Item.Properties()));
    public static final RegistryObject<Item> KEY=ITEMS.register("warden_key",()->new Item(new Item.Properties().stacksTo(1)));
    private final Map<BlockPos,BlockPos> locks=new HashMap<>();
    private final Set<BlockPos> opened=new HashSet<>();
    private boolean key;
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);bus.addListener((net.minecraftforge.event.BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey().location().toString().equals("zombierool:zb_rct")){e.accept(LOCK_ITEM.get());e.accept(KEY.get());}});}
    public static WardenLocks get(ServerLevel level){return level.getDataStorage().computeIfAbsent(n->{
        var d=new WardenLocks();d.key=n.getBoolean("key");
        for(var t:n.getList("locks",10)){var a=(CompoundTag)t;var p=BlockPos.of(a.getLong("pos"));d.locks.put(p,BlockPos.of(a.getLong("target")));if(a.getBoolean("open"))d.opened.add(p);}return d;
    },WardenLocks::new,"zombierool_warden_locks");}
    private static WardenLocks team(ServerLevel level){return get(level.getServer().overworld());}
    public static boolean hasKey(ServerLevel level){return team(level).key;}
    @Override public CompoundTag save(CompoundTag n){n.putBoolean("key",key);var list=new ListTag();locks.forEach((p,target)->{var a=new CompoundTag();a.putLong("pos",p.asLong());a.putLong("target",target.asLong());a.putBoolean("open",opened.contains(p));list.add(a);});n.put("locks",list);return n;}
    private static BlockPos root(Level l,BlockPos p){var s=l.getBlockState(p);return s.getBlock() instanceof DoorBlock&&s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.UPPER?p.below():p;}
    private static boolean door(Level l,BlockPos p){var b=l.getBlockState(p).getBlock();return b instanceof DoorBlock||b instanceof me.cryo.zombierool.block.system.ObstacleDoorSystem.ObstacleDoorBlock;}
    private static Set<BlockPos> group(Level l,BlockPos pos){
        var result=new HashSet<BlockPos>();pos=root(l,pos);result.add(pos);
        var state=l.getBlockState(pos);
        if(state.getBlock() instanceof DoorBlock){
            var facing=state.getValue(DoorBlock.FACING);var hinge=state.getValue(DoorBlock.HINGE);var mate=pos.relative(hinge==DoorHingeSide.LEFT?facing.getClockWise():facing.getCounterClockWise());
            if(l.hasChunkAt(mate)){var other=l.getBlockState(mate);if(other.is(state.getBlock())&&other.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER&&other.getValue(DoorBlock.FACING)==facing&&other.getValue(DoorBlock.HINGE)!=hinge)result.add(mate);}
            return result;
        }
        if(!(state.getBlock() instanceof me.cryo.zombierool.block.system.ObstacleDoorSystem.ObstacleDoorBlock))return result;
        var queue=new ArrayDeque<BlockPos>();queue.add(pos);
        while(!queue.isEmpty()&&result.size()<4096){var p=queue.remove();for(var dir:Direction.values()){var next=p.relative(dir);if(l.hasChunkAt(next)&&l.getBlockState(next).getBlock() instanceof me.cryo.zombierool.block.system.ObstacleDoorSystem.ObstacleDoorBlock&&result.add(next))queue.add(next);}}
        return result;
    }
    /** True consumes this interaction; a paid door still needs its normal purchase afterwards. */
    public static boolean intercept(ServerPlayer p,BlockPos pos){
        var l=p.serverLevel();if(!door(l,pos))return false;
        var d=get(l);var group=group(l,pos);var matches=d.locks.entrySet().stream().filter(e->group.contains(e.getValue())&&!d.opened.contains(e.getKey())).map(Map.Entry::getKey).toList();
        if(matches.isEmpty())return false;
        if(me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(p.getUUID())||!hasKey(l)){p.displayClientMessage(Component.translatable("message.zombierool.warden.need_key"),true);return true;}
        for(var lock:matches)open(l,lock);
        p.displayClientMessage(Component.translatable("message.zombierool.warden.unlocked"),true);return true;
    }
    private static void open(ServerLevel l,BlockPos pos){var d=get(l);d.opened.add(pos);d.setDirty();var s=l.getBlockState(pos);if(s.is(LOCK.get())){PortableEquipmentPackets.lockDrop(l,pos,s);l.setBlock(pos,s.setValue(LockBlock.OPEN,true),3);}l.playSound(null,pos,net.minecraft.sounds.SoundEvents.CHAIN_BREAK,net.minecraft.sounds.SoundSource.BLOCKS,1,1);sync(l);}
    public static void reset(ServerLevel l){var d=get(l);d.key=false;d.opened.clear();d.setDirty();for(var p:d.locks.keySet())if(l.hasChunkAt(p)){var s=l.getBlockState(p);if(s.is(LOCK.get()))l.setBlock(p,s.setValue(LockBlock.OPEN,false),3);}sync(l);}
    public static Set<BlockPos> closedTargets(ServerLevel level){var data=get(level);var result=new HashSet<BlockPos>();for(var e:data.locks.entrySet())if(!data.opened.contains(e.getKey())){result.add(e.getKey());var p=e.getValue();if(level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4)!=null){for(var target:group(level,p)){result.add(target);if(level.getBlockState(target).getBlock() instanceof DoorBlock)result.add(target.above());}}else result.add(p);}return result;}
    public static void sync(ServerLevel level){PortableEquipmentPackets.syncLocks(level,closedTargets(level));}
    public static void drop(me.cryo.zombierool.entity.SpecialEnemyEntity boss){
        if(!(boss.level() instanceof ServerLevel l)||!WaveManager.isGameRunning()||!me.cryo.zombierool.config.SpecialEnemyConfig.get(l).getCompound("wardenKeyDrops").getBoolean(boss.kind())||hasKey(l))return;
        boolean needed=false;for(var world:l.getServer().getAllLevels()){var d=get(world);if(d.locks.keySet().stream().anyMatch(p->!d.opened.contains(p))){needed=true;break;}}
        if(needed){var item=new net.minecraft.world.entity.item.ItemEntity(l,boss.getX(),boss.getY()+.3,boss.getZ(),new ItemStack(KEY.get()));item.setDefaultPickUpDelay();item.getPersistentData().putBoolean("zr_boss_loot",true);l.addFreshEntity(item);}
    }
    @SubscribeEvent public static void pickup(net.minecraftforge.event.entity.player.EntityItemPickupEvent e){
        if(e.getItem().getItem().is(KEY.get()))e.setCanceled(true);
    }
    /** Same aim/range/visibility test on both sides; proximity alone never collects the key. */
    public static net.minecraft.world.entity.item.ItemEntity keyTarget(Player p){
        var eye=p.getEyePosition();var look=p.getViewVector(1);return p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,p.getBoundingBox().inflate(3),item->{
            if(!item.isAlive()||!item.getItem().is(KEY.get()))return false;var center=item.getBoundingBox().getCenter();var delta=center.subtract(eye);if(delta.lengthSqr()>9||look.dot(delta.normalize())<.85)return false;
            return p.level().clip(new ClipContext(eye,center,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS;
        }).stream().min(java.util.Comparator.comparingDouble(p::distanceToSqr)).orElse(null);
    }
    public static boolean collectLookedAt(ServerPlayer p){
        if(!p.isAlive()||p.isSpectator()||me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(p.getUUID())||BuildableSystem.busy(p)||LunarEquipment.busy(p)||hasKey(p.serverLevel()))return false;
        var item=keyTarget(p);if(item==null)return false;var d=team(p.serverLevel());d.key=true;d.setDirty();
        var drops=new ArrayList<net.minecraft.world.entity.item.ItemEntity>();for(var world:p.server.getAllLevels())for(var entity:world.getAllEntities())if(entity instanceof net.minecraft.world.entity.item.ItemEntity drop&&drop.getItem().is(KEY.get()))drops.add(drop);drops.forEach(net.minecraft.world.entity.Entity::discard);
        p.serverLevel().playSound(null,p.blockPosition(),me.cryo.zombierool.init.ZombieroolModSounds.BUY.get(),net.minecraft.sounds.SoundSource.PLAYERS,.7f,1);
        for(var player:p.server.getPlayerList().getPlayers()){PortableEquipmentPackets.sync(player);player.sendSystemMessage(Component.translatable("message.zombierool.warden.acquired"));}return true;
    }
    public static boolean unlockLookedAt(ServerPlayer p){
        if(!p.isAlive()||p.isSpectator()||me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(p.getUUID())||BuildableSystem.busy(p)||LunarEquipment.busy(p))return false;
        var hit=p.pick(3,1,false);if(!(hit instanceof BlockHitResult block)||hit.getType()!=HitResult.Type.BLOCK)return false;var pos=block.getBlockPos();var d=get(p.serverLevel());
        if(d.locks.containsKey(pos)&&!d.opened.contains(pos)){if(!hasKey(p.serverLevel()))return false;return intercept(p,d.locks.get(pos));}return intercept(p,pos);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void use(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock e){
        if(e.getEntity().isCreative())return;
        boolean locked=e.getLevel().isClientSide?me.cryo.zombierool.client.PortableEquipmentClient.locked(root(e.getLevel(),e.getPos())):e.getLevel() instanceof ServerLevel l&&closedTargets(l).contains(root(l,e.getPos()));
        if(locked){e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);}
    }
    @SubscribeEvent public static void protect(net.minecraftforge.event.level.BlockEvent.BreakEvent e){
        if(e.getPlayer().isCreative()||!(e.getLevel() instanceof ServerLevel l)||!door(l,e.getPos()))return;
        var group=group(l,e.getPos());var d=get(l);if(d.locks.entrySet().stream().anyMatch(a->group.contains(a.getValue())&&!d.opened.contains(a.getKey())))e.setCanceled(true);
    }
    @SubscribeEvent public static void chunk(net.minecraftforge.event.level.ChunkEvent.Load e){
        if(!(e.getLevel() instanceof ServerLevel level))return;
        var pos=e.getChunk().getPos();
        // ChunkEvent.Load may run before its loading future completes, on a worker.
        // Never ask ServerLevel for this chunk, or access SavedData, from that callback.
        level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount()+1,()->refreshChunk(level,pos)));
    }
    private static void refreshChunk(ServerLevel level,net.minecraft.world.level.ChunkPos pos){
        var chunk=level.getChunkSource().getChunkNow(pos.x,pos.z);if(chunk==null)return;
        var data=get(level);
        for(var p:List.copyOf(data.locks.keySet()))if(new net.minecraft.world.level.ChunkPos(p).equals(pos)){
            var state=chunk.getBlockState(p);
            if(state.is(LOCK.get())){
                var next=state.setValue(LockBlock.OPEN,data.opened.contains(p)).setValue(LockBlock.OFFSET,LockBlock.offset(level,p.relative(state.getValue(LockBlock.FACING).getOpposite()),state.getValue(LockBlock.FACING)));
                if(next!=state)level.setBlock(p,next,3);
            }else{data.locks.remove(p);data.opened.remove(p);data.setDirty();}
        }
        if(!data.locks.isEmpty())sync(level);
    }
    public static final class LockBlock extends DirectionalBlock {
        public static final BooleanProperty OPEN=BooleanProperty.create("open");
        public static final IntegerProperty OFFSET=IntegerProperty.create("offset",0,13);
        LockBlock(){super(Properties.of().strength(-1,3600000).noOcclusion().noCollission().noLootTable().sound(SoundType.CHAIN));registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(OPEN,false).setValue(OFFSET,0));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,OPEN,OFFSET);}
        private static int offset(BlockGetter l,BlockPos target,Direction face){var shape=l.getBlockState(target).getShape(l,target);if(shape.isEmpty()||!face.getAxis().isHorizontal())return 0;var box=shape.bounds();double value=face.getAxisDirection()==Direction.AxisDirection.NEGATIVE?box.min(face.getAxis()):1-box.max(face.getAxis());return Math.max(0,Math.min(13,(int)Math.round(value*16)));}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){var face=c.getClickedFace();var target=c.getClickedPos().relative(face.getOpposite());return face.getAxis().isHorizontal()&&door(c.getLevel(),target)?defaultBlockState().setValue(FACING,face).setValue(OFFSET,offset(c.getLevel(),target,face)):null;}
        @Override public BlockState updateShape(BlockState s,Direction dir,BlockState other,LevelAccessor l,BlockPos p,BlockPos neighbor){if(dir==s.getValue(FACING).getOpposite()){if(!(other.getBlock() instanceof DoorBlock)&&!(other.getBlock() instanceof me.cryo.zombierool.block.system.ObstacleDoorSystem.ObstacleDoorBlock))return Blocks.AIR.defaultBlockState();return s.setValue(OFFSET,offset(l,neighbor,s.getValue(FACING)));}return super.updateShape(s,dir,other,l,p,neighbor);}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){if(s.getValue(OPEN))return Shapes.empty();int o=s.getValue(OFFSET);return switch(s.getValue(FACING)){case NORTH->Block.box(4,3,13+o,12,15,16+o);case SOUTH->Block.box(4,3,-o,12,15,3-o);case EAST->Block.box(-o,3,4,3-o,15,12);case WEST->Block.box(13+o,3,4,16+o,15,12);default->Block.box(4,3,13,12,15,16);};}
        @Override public void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moving){super.onPlace(s,l,p,old,moving);if(old.getBlock()!=this&&l instanceof ServerLevel server){var d=get(server);d.locks.put(p.immutable(),root(l,p.relative(s.getValue(FACING).getOpposite())));d.setDirty();sync(server);}}
        @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){if(next.getBlock()!=this&&l instanceof ServerLevel server){var d=get(server);d.locks.remove(p);d.opened.remove(p);d.setDirty();sync(server);}super.onRemove(s,l,p,next,moving);}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand h,BlockHitResult hit){return InteractionResult.sidedSuccess(l.isClientSide);}
    }
}
