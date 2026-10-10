package me.cryo.zombierool.block.system;

import java.util.*;
import me.cryo.zombierool.block.AbstractTechnicalBlock;
import me.cryo.zombierool.gameplay.TraversalMotion;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;

/** Directed traversal links. Markers are visible only to map builders. */
public final class TraversalSystem {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS,"zombierool");
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,"zombierool");
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"zombierool");
    public static final RegistryObject<Block> NODE = BLOCKS.register("traversal_node",TraversalBlock::new);
    public static final RegistryObject<Item> ITEM = ITEMS.register("traversal_node",()->new BlockItem(NODE.get(),new Item.Properties().stacksTo(1)));
    public static final RegistryObject<BlockEntityType<Anchor>> TYPE = ENTITIES.register("traversal_node",()->BlockEntityType.Builder.of(Anchor::new,NODE.get()).build(null));
    private static final Map<Level,Map<BlockPos,Anchor>> LOADED = new WeakHashMap<>();
    private TraversalSystem() {}
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey().location().equals(new ResourceLocation("zombierool","zb_rct")))e.accept(ITEM.get());});
    }
    public static void unload(Level level){LOADED.remove(level);}
    public static Anchor entry(Level level, BlockPos position) {return LOADED.getOrDefault(level,Map.of()).get(position);}
    public static boolean endpoint(BlockGetter level,BlockPos pos) {
        return level.getBlockEntity(pos) instanceof Anchor a && a.getBlockState().getValue(TraversalBlock.PHASE)!=1;
    }
    public static Anchor edge(Mob mob,Node node) {
        Anchor a=entry(mob.level(),new BlockPos(node.x,node.y,node.z));
        return a!=null && a.accepts(mob) && a.ready() ? a : null;
    }
    public static void approach(Mob mob) {
        if(!mob.isAlive() || mob.deathTime>0 || TraversalMotion.active(mob) || mob.isNoAi())return;
        BlockPos feet=mob.blockPosition();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
            Anchor a=entry(mob.level(),feet.offset(x,0,z));
            if(a==null || !a.accepts(mob) || !a.ready() || mob.position().distanceToSqr(a.start())>.65*.65)continue;
            Path path=mob.getNavigation().getPath();
            boolean planned=path!=null && !path.isDone() && path.getNextNodePos().equals(a.exit);
            LivingEntity target=mob.getTarget();
            boolean toward=target!=null && target.position().distanceToSqr(a.end())+1<target.position().distanceToSqr(a.start());
            if(planned || toward) {TraversalMotion.start(mob,a);return;}
        }
    }
    public static final class TraversalBlock extends AbstractTechnicalBlock implements EntityBlock {
        public static final IntegerProperty PHASE=IntegerProperty.create("phase",0,2);
        TraversalBlock(){super(Properties.of().strength(-1,3600000).noCollission().noOcclusion());registerDefaultState(stateDefinition.any().setValue(PHASE,0));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(PHASE);}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return shouldBeInvisible()?Shapes.empty():Block.box(3,0,3,13,4,13);}
        @Override protected VoxelShape getTechnicalCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Shapes.empty();}
        @Override public boolean isPathfindable(BlockState s,BlockGetter l,BlockPos p,PathComputationType t){return true;}
        @Override public BlockPathTypes getBlockPathType(BlockState s,BlockGetter l,BlockPos p,Mob m){return BlockPathTypes.OPEN;}
        @Override protected void addTechnicalTooltip(List<Component> list){list.add(Component.translatable("tooltip.zombierool.traversal"));}
        @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new Anchor(p,s);}
        @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity entity,ItemStack stack) {
            if(!(level instanceof ServerLevel server) || !(entity instanceof Player player))return;
            CompoundTag draft=stack.getOrCreateTag();
            String dimension=level.dimension().location().toString();
            BlockPos previous=BlockPos.of(draft.getLong("TraversalEntry"));
            Anchor root=draft.getString("TraversalDimension").equals(dimension)?entry(level,previous):null;
            int phase=draft.getInt("TraversalPhase");
            if(root==null || phase<1 || phase>2 || pos.distSqr(previous)>4096)phase=0;
            Anchor placed=(Anchor)level.getBlockEntity(pos);
            if(placed==null)return;
            if(phase==0){draft.putLong("TraversalEntry",pos.asLong());draft.putString("TraversalDimension",dimension);root=placed;}
            placed.root=root.getBlockPos();
            level.setBlock(pos,state.setValue(PHASE,phase),3);
            if(phase>0)LOADED.getOrDefault(level,new HashMap<>()).remove(pos);
            if(phase==1)root.apex=pos;
            if(phase==2)root.exit=pos;
            root.sync();placed.sync();
            draft.putInt("TraversalPhase",phase==2?0:phase+1);
            player.displayClientMessage(Component.translatable("message.zombierool.traversal.placed."+phase),true);
        }
        @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit) {
            if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
            if(!l.isClientSide && player instanceof ServerPlayer sp && sp.isCreative() && l.getBlockEntity(p) instanceof Anchor a) {
                Anchor root=entry(l,a.root);
                if(root!=null)TransportPackets.open(sp,root.getBlockPos(),"traversal",root.config());
            }
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
    }
    public static final class Anchor extends BlockEntity {
        public BlockPos root,apex,exit;
        public int duration=24;
        public boolean enabled=true,allMobs=false;
        public double clearance=.25;
        public Anchor(BlockPos p,BlockState s){super(TYPE.get(),p,s);root=p;}
        public Vec3 start(){return Vec3.atBottomCenterOf(worldPosition);}
        public Vec3 peak(){return Vec3.atBottomCenterOf(apex).add(0,clearance,0);}
        public Vec3 end(){return Vec3.atBottomCenterOf(exit);}
        public boolean accepts(Mob mob){return enabled && (allMobs || mob instanceof Monster || mob instanceof me.cryo.zombierool.entity.AbstractZombieRoolEntity)
                && (exit==null || !mob.level().getBlockCollisions(mob,mob.getBoundingBox().move(end().subtract(mob.position()))).iterator().hasNext());}
        public boolean ready(){
            if(!enabled || apex==null || exit==null || level==null || apex.getY()<Math.max(worldPosition.getY(),exit.getY()))return false;
            if(!level.hasChunkAt(apex)||!level.hasChunkAt(exit))return false;
            return level.getBlockEntity(apex) instanceof Anchor a && a.root.equals(worldPosition) && a.getBlockState().getValue(TraversalBlock.PHASE)==1
                    && level.getBlockEntity(exit) instanceof Anchor e && e.root.equals(worldPosition) && e.getBlockState().getValue(TraversalBlock.PHASE)==2
                    && !level.getBlockState(exit.below()).getCollisionShape(level,exit.below()).isEmpty();
        }
        public CompoundTag config(){CompoundTag n=new CompoundTag();n.putInt("duration",duration);n.putDouble("clearance",clearance);n.putBoolean("enabled",enabled);n.putBoolean("allMobs",allMobs);n.putString("route",apex==null||exit==null?"—":worldPosition.toShortString()+" → "+apex.toShortString()+" → "+exit.toShortString());return n;}
        public void configure(CompoundTag n){duration=Math.max(8,Math.min(200,n.getInt("duration")));clearance=Double.isFinite(n.getDouble("clearance"))?Math.max(0,Math.min(4,n.getDouble("clearance"))):.25;enabled=n.getBoolean("enabled");allMobs=n.getBoolean("allMobs");sync();}
        public void sync(){setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        @Override public void onLoad(){super.onLoad();if(level!=null && getBlockState().getValue(TraversalBlock.PHASE)==0)LOADED.computeIfAbsent(level,l->new HashMap<>()).put(worldPosition,this);}
        @Override public void setRemoved(){if(level!=null)LOADED.getOrDefault(level,new HashMap<>()).remove(worldPosition,this);super.setRemoved();}
        @Override protected void saveAdditional(CompoundTag n){super.saveAdditional(n);n.merge(config());n.putLong("root",root.asLong());if(apex!=null)n.putLong("apex",apex.asLong());if(exit!=null)n.putLong("exit",exit.asLong());}
        @Override public void load(CompoundTag n){super.load(n);root=n.contains("root")?BlockPos.of(n.getLong("root")):worldPosition;apex=n.contains("apex")?BlockPos.of(n.getLong("apex")):null;exit=n.contains("exit")?BlockPos.of(n.getLong("exit")):null;duration=n.contains("duration")?Math.max(8,Math.min(200,n.getInt("duration"))):24;clearance=n.contains("clearance")?n.getDouble("clearance"):.25;enabled=!n.contains("enabled")||n.getBoolean("enabled");allMobs=n.getBoolean("allMobs");}
        @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
        @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    }
}
