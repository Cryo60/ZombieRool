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
    public enum Kind { TRAVERSAL, JUMP, ZIPLINE, WALL }
    public static final RegistryObject<Block> JUMP = BLOCKS.register("jump_link",()->new TraversalBlock(Kind.JUMP));
    public static final RegistryObject<Block> ZIPLINE = BLOCKS.register("zipline",()->new TraversalBlock(Kind.ZIPLINE));
    public static final RegistryObject<Block> WALL = BLOCKS.register("crawler_wall_link",()->new TraversalBlock(Kind.WALL));
    public static final RegistryObject<Item> JUMP_ITEM = ITEMS.register("jump_link",()->new BlockItem(JUMP.get(),new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> ZIPLINE_ITEM = ITEMS.register("zipline",()->new BlockItem(ZIPLINE.get(),new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WALL_ITEM = ITEMS.register("crawler_wall_link",()->new BlockItem(WALL.get(),new Item.Properties()));
    public static final RegistryObject<BlockEntityType<Anchor>> TYPE = ENTITIES.register("traversal_node",()->BlockEntityType.Builder.of(Anchor::new,NODE.get(),JUMP.get(),ZIPLINE.get(),WALL.get()).build(null));
    private static final Map<Level,Map<BlockPos,Anchor>> LOADED = new WeakHashMap<>();
    private static final Map<Level,Long> REVISIONS=new WeakHashMap<>();
    private static final Map<Level,Index> INDEXES=new WeakHashMap<>();
    private static final Map<Level,Set<BlockPos>> REMOVING=new WeakHashMap<>();
    private static final class Index {
        final long revision;
        final List<Anchor> roots;
        final Map<BlockPos,List<Route>> edges=new HashMap<>();
        Index(Level level){
            revision=TraversalSystem.revision(level);roots=List.copyOf(LOADED.getOrDefault(level,Map.of()).values());
            for(Anchor a:roots){
                if(a.isRemoved())continue;
                if(a.kind()==Kind.WALL){
                    for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)add(a.getBlockPos().offset(x,0,z),new Route(a,false));
                }else{
                    add(a.getBlockPos(),new Route(a,false));
                    if(a.exit!=null)add(a.exit,new Route(a,true));
                }
            }
        }
        void add(BlockPos pos,Route route){edges.computeIfAbsent(pos,k->new ArrayList<>()).add(route);}
    }
    public static long revision(Level level){return REVISIONS.getOrDefault(level,0L);}
    public static void blockChanged(Level level,BlockPos pos){
        // Terrain edits are rare compared with node queries. Never rescan a wall
        // per node, but invalidate immediately for commands, Lua and normal edits.
        var roots=LOADED.get(level);if(roots==null)return;
        boolean changed=false;
        for(Anchor a:roots.values())if(a.kind()==Kind.WALL){
            BlockPos p=a.getBlockPos();
            if(Math.abs(p.getX()-pos.getX())+Math.abs(p.getZ()-pos.getZ())<=1&&pos.getY()<=p.getY()&&pos.getY()>=p.getY()-129){a.lastWallCheck=Long.MIN_VALUE;changed=true;}
        }
        if(changed)invalidate(level);
    }
    private static void invalidate(Level level){REVISIONS.merge(level,1L,Long::sum);INDEXES.remove(level);}
    private static void remember(Level level,BlockPos pos,Anchor a){
        if(LOADED.computeIfAbsent(level,l->new HashMap<>()).put(pos,a)!=a)invalidate(level);
    }
    private static void forget(Level level,BlockPos pos,Anchor a){
        var map=LOADED.get(level);if(map!=null&&(a==null?map.remove(pos)!=null:map.remove(pos,a)))invalidate(level);
    }
    private static Index index(Level level){return INDEXES.computeIfAbsent(level,Index::new);}
    private static void breakLinked(Level level,BlockPos pos){
        if(level.isClientSide||!(level.getBlockEntity(pos) instanceof Anchor part))return;
        BlockPos rootPos=part.root;var pending=REMOVING.computeIfAbsent(level,l->new HashSet<>());
        if(!pending.add(rootPos))return;
        try{
            if(!level.hasChunkAt(rootPos)&&rootPos.distSqr(pos)<=65536)((ServerLevel)level).getChunkAt(rootPos);
            Anchor root=part.configurationRoot();if(root==null)return;
            for(BlockPos other:new BlockPos[]{rootPos,root.apex,root.exit}){
                if(other==null||other.equals(pos)||other.distSqr(rootPos)>65536)continue;
                if(!level.hasChunkAt(other))((ServerLevel)level).getChunkAt(other);
                if(level.getBlockEntity(other) instanceof Anchor a&&a.root.equals(rootPos)&&a.kind()==root.kind())level.destroyBlock(other,false);
            }
        }finally{pending.remove(rootPos);if(pending.isEmpty())REMOVING.remove(level);}
    }
    private TraversalSystem() {}
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey().location().equals(new ResourceLocation("zombierool","zb_rct"))){e.accept(ITEM.get());e.accept(JUMP_ITEM.get());e.accept(ZIPLINE_ITEM.get());e.accept(WALL_ITEM.get());}});
    }
    public static void unload(Level level){LOADED.remove(level);INDEXES.remove(level);REVISIONS.remove(level);REMOVING.remove(level);}
    public static Anchor entry(Level level, BlockPos position) {
        Anchor cached=LOADED.getOrDefault(level,Map.of()).get(position);
        if(cached!=null&&!cached.isRemoved())return cached;
        // A restored chunk can keep its block entities packed until the first lookup.
        // Pathfinding must not depend on a later Forge onLoad callback or a builder's click.
        if(level.hasChunkAt(position)&&level.getBlockEntity(position) instanceof Anchor a&&a.getBlockState().getValue(TraversalBlock.PHASE)==0){
            remember(level,position,a);return a;
        }
        return null;
    }
    public static BlockPos lower(Level level,BlockPos pos){var state=level.getBlockState(pos);return state.getBlock() instanceof TraversalBlock b&&b.kind==Kind.ZIPLINE&&state.getValue(TraversalBlock.UPPER)?pos.below():pos;}
    public static boolean endpoint(BlockGetter level,BlockPos pos) {
        if(!(level.getBlockEntity(pos) instanceof Anchor a))return false;
        Anchor primary=a.configurationRoot();
        if(primary!=null&&a.getLevel()!=null)remember(a.getLevel(),primary.getBlockPos(),primary);
        return a.getBlockState().getValue(TraversalBlock.PHASE)!=1;
    }
    public record Route(Anchor anchor, boolean reverse) {
        public Vec3 start(){return reverse?anchor.end():anchor.start();}
        public Vec3 end(){return reverse?anchor.start():anchor.end();}
        public Vec3 peak(){if(anchor.kind()==Kind.WALL)return start().add(anchor.wallFace.getStepX(),0,anchor.wallFace.getStepZ());return anchor.kind()==Kind.TRAVERSAL?anchor.peak():start().add(end()).scale(.5).add(0,anchor.kind()==Kind.JUMP?anchor.clearance:0,0);}
        public BlockPos destination(){return reverse?anchor.getBlockPos():anchor.exit;}
    }
    public static Collection<Anchor> loaded(Level level){return index(level).roots;}
    public static Route edge(Mob mob,Node node) {
        if(mob.getNavigation() instanceof me.cryo.zombierool.gameplay.RouteNavigation navigation&&navigation.walkingOnly())return null;
        BlockPos pos=new BlockPos(node.x,node.y,node.z);
        var candidates=index(mob.level()).edges.getOrDefault(pos,List.of());
        // Unpack newly loaded roots only when the cell actually contains a marker.
        if(candidates.isEmpty()&&mob.level().getBlockState(pos).getBlock() instanceof TraversalBlock){
            Anchor a=entry(mob.level(),pos);
            if(a!=null&&a.ready()&&a.accepts(mob)){var route=new Route(a,false);if(me.cryo.zombierool.gameplay.BossTraversalHistory.allows(mob,route))return route;}
        }
        for(Route route:candidates){
            Anchor root=route.anchor();
            if(!me.cryo.zombierool.gameplay.BossTraversalHistory.allows(mob,route)||root.isRemoved()||route.reverse()&&!root.bidirectional||!root.ready()||!root.accepts(mob,route.reverse()))continue;
            if(root.kind()==Kind.WALL&&( !(me.cryo.zombierool.gameplay.CrawlerLeap.isCrawler(mob))
                    ||new Vec3(node.x+.5,node.y,node.z+.5).distanceToSqr(root.start())>1.3*1.3))continue;
            return route;
        }
        return null;
    }
    public static void approach(Mob mob) {
        if(mob instanceof me.cryo.zombierool.entity.SpecialEnemyEntity boss&&boss.isBoss()&&mob.getPersistentData().getBoolean("zr_boss_machine"))return;
        if(!mob.isAlive() || mob.deathTime>0 || TraversalMotion.active(mob) || mob.isNoAi() || me.cryo.zombierool.gameplay.EquipmentEffects.active(mob)||me.cryo.zombierool.gameplay.WonderWeaponEffects.frozen(mob))return;
        if(me.cryo.zombierool.gameplay.CrawlerLeap.isCrawler(mob)){
            for(Anchor anchor:nearbyWalls(mob)){
                if(anchor.kind()!=Kind.WALL||Math.abs(mob.getY()-anchor.getBlockPos().getY())>1||!anchor.ready()||!anchor.accepts(mob))continue;
                Route wall=new Route(anchor,false);
                Vec3 offset=mob.position().subtract(wall.start());
                if(offset.x*offset.x+offset.z*offset.z>1.8*1.8)continue;
                Path path=mob.getNavigation().getPath();
                boolean planned=plansRoute(mob,path,wall);
                // Catch the first unsupported step even if vanilla has skipped the roof node.
                // The player's proximity/elevation must not turn a wall descent into a fall.
                boolean falling=mob.getTarget()!=null&&!mob.onGround()&&mob.getDeltaMovement().y<0&&mob.getY()<anchor.getBlockPos().getY();
                if(!planned&&!falling)continue;
                if(mob.level().clip(new ClipContext(mob.position().add(0,.3,0),wall.start().add(0,.3,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mob)).getType()!=HitResult.Type.MISS)continue;
                TraversalMotion.start(mob,wall);return;
            }
        }
        Path path=mob.getNavigation().getPath();
        if(path==null||path.isDone())return;
        for(int i=Math.max(0,path.getNextNodeIndex()-1);i<Math.min(path.getNodeCount()-1,path.getNextNodeIndex()+3);i++){
            Route route=edge(mob,path.getNode(i));
            if(route==null||route.anchor().kind()==Kind.WALL||!path.getNode(i+1).asBlockPos().equals(route.destination()))continue;
            double reach=route.anchor().kind()==Kind.ZIPLINE?1.8:.8;
            if(mob.position().distanceToSqr(route.start())>reach*reach)continue;
            if(mob.getTarget() instanceof net.minecraft.world.entity.player.Player player
                &&mob.getNavigation() instanceof me.cryo.zombierool.gameplay.RouteNavigation navigation&&navigation.preferNearbyWalk(player))return;
            if(mob.level().clip(new ClipContext(mob.position().add(0,.6,0),route.start().add(0,.6,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mob)).getType()!=HitResult.Type.MISS)continue;
            TraversalMotion.start(mob,route);return;
        }
    }
    private static Collection<Anchor> nearbyWalls(Mob mob){
        BlockPos feet=mob.blockPosition();var nearby=new ArrayList<Anchor>();
        for(int dy=-1;dy<=1;dy++)for(Route route:index(mob.level()).edges.getOrDefault(feet.offset(0,dy,0),List.of()))
            if(route.anchor().kind()==Kind.WALL&&!nearby.contains(route.anchor()))nearby.add(route.anchor());
        return nearby;
    }
    private static boolean plansRoute(Mob mob,Path path,Route route){
        if(path==null||path.isDone())return false;
        for(int i=Math.max(0,path.getNextNodeIndex()-1);i<Math.min(path.getNodeCount()-1,path.getNextNodeIndex()+3);i++){
            Route edge=edge(mob,path.getNode(i));
            if(edge!=null&&edge.anchor()==route.anchor()&&edge.reverse()==route.reverse()&&path.getNode(i+1).asBlockPos().equals(route.destination()))return true;
        }
        return false;
    }
    public static boolean boardingZipline(Mob mob){
        Path path=mob.getNavigation().getPath();if(path==null||path.isDone())return false;
        for(int i=Math.max(0,path.getNextNodeIndex()-1);i<Math.min(path.getNodeCount()-1,path.getNextNodeIndex()+3);i++){
            Route route=edge(mob,path.getNode(i));
            if(route!=null&&route.anchor().kind()==Kind.ZIPLINE&&path.getNode(i+1).asBlockPos().equals(route.destination())&&mob.position().distanceToSqr(route.start())<9)return true;
        }
        return false;
    }
    public static final class TraversalBlock extends AbstractTechnicalBlock implements EntityBlock {
        public static final IntegerProperty PHASE=IntegerProperty.create("phase",0,2);
        public static final net.minecraft.world.level.block.state.properties.BooleanProperty UPPER=net.minecraft.world.level.block.state.properties.BooleanProperty.create("upper");
        public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING=HorizontalDirectionalBlock.FACING;
        public final Kind kind;
        TraversalBlock(){this(Kind.TRAVERSAL);}
        TraversalBlock(Kind kind){super(Properties.of().strength(-1,3600000).noCollission().noOcclusion());this.kind=kind;registerDefaultState(stateDefinition.any().setValue(PHASE,0).setValue(FACING,net.minecraft.core.Direction.NORTH).setValue(UPPER,false));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(PHASE,FACING,UPPER);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext context){if(kind==Kind.ZIPLINE&&(context.getClickedPos().getY()+1>=context.getLevel().getMaxBuildHeight()||!context.getLevel().getBlockState(context.getClickedPos().above()).canBeReplaced(context)))return null;return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
        @Override public BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
        @Override public BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return kind==Kind.ZIPLINE?Block.box(1,0,1,15,16,15):shouldBeInvisible()?Shapes.empty():Block.box(3,0,3,13,12,13);}
        @Override protected VoxelShape getTechnicalCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Shapes.empty();}
        @Override public RenderShape getRenderShape(BlockState state){return kind==Kind.ZIPLINE?(state.getValue(UPPER)?RenderShape.INVISIBLE:RenderShape.MODEL):super.getRenderShape(state);}
        @Override public boolean isPathfindable(BlockState s,BlockGetter l,BlockPos p,PathComputationType t){return true;}
        @Override public BlockPathTypes getBlockPathType(BlockState s,BlockGetter l,BlockPos p,Mob m){return BlockPathTypes.OPEN;}
        @Override protected void addTechnicalTooltip(List<Component> list){list.add(Component.translatable("tooltip.zombierool."+kind.name().toLowerCase(Locale.ROOT)));}
        @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return s.getValue(UPPER)?null:new Anchor(p,s);}
        public void ensureUpper(Level level,BlockPos pos,BlockState state){if(kind==Kind.ZIPLINE&&!state.getValue(UPPER)&&pos.getY()+1<level.getMaxBuildHeight()&&level.getBlockState(pos).getBlock()==this&&level.getBlockState(pos.above()).canBeReplaced())level.setBlock(pos.above(),state.setValue(UPPER,true),3);}
        @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){super.onPlace(state,level,pos,old,moving);if(!level.isClientSide&&old.getBlock()!=this)ensureUpper(level,pos,state);}
        @Override public List<ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder builder){return state.getValue(UPPER)?List.of():super.getDrops(state,builder);}
        @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity entity,ItemStack stack) {
            if(!(level instanceof ServerLevel server) || !(entity instanceof Player player))return;
            CompoundTag draft=stack.getOrCreateTag();
            String dimension=level.dimension().location().toString();
            BlockPos previous=BlockPos.of(draft.getLong("TraversalEntry"));
            Anchor root=draft.getString("TraversalDimension").equals(dimension)?entry(level,previous):null;
            int phase=draft.getInt("TraversalPhase");
            if(root==null || root.kind()!=kind || phase<1 || phase>2 || pos.distSqr(previous)>65536 || kind==Kind.WALL)phase=0;
            Anchor placed=(Anchor)level.getBlockEntity(pos);
            if(placed==null)return;
            if(phase==0){if(kind==Kind.JUMP)placed.clearance=1.5;if(kind==Kind.ZIPLINE)placed.duration=60;draft.putLong("TraversalEntry",pos.asLong());draft.putString("TraversalDimension",dimension);root=placed;}
            placed.root=root.getBlockPos();
            if(kind==Kind.WALL){placed.findWallExit();placed.sync();return;}
            if(kind!=Kind.TRAVERSAL&&phase==1)phase=2;
            level.setBlock(pos,state.setValue(PHASE,phase),3);
            if(phase>0)forget(level,pos,null);
            if(phase==1)root.apex=pos;
            if(phase==2)root.exit=pos;
            root.sync();placed.sync();
            draft.putInt("TraversalPhase",phase==2?0:phase+1);
            player.getInventory().setChanged();((ServerPlayer)player).containerMenu.broadcastChanges();
            player.displayClientMessage(Component.translatable("message.zombierool.traversal.placed."+phase),true);
        }
        @Override public void onRemove(BlockState old,Level level,BlockPos pos,BlockState next,boolean moving){
            if(old.getBlock()!=next.getBlock()){
                if(kind==Kind.ZIPLINE){var other=old.getValue(UPPER)?pos.below():pos.above();var partner=level.getBlockState(other);if(partner.getBlock()==this&&partner.getValue(UPPER)!=old.getValue(UPPER))level.removeBlock(other,false);}
                if(!old.getValue(UPPER))breakLinked(level,pos);
            }
            super.onRemove(old,level,pos,next,moving);
        }
        @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit) {
            if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
            p=lower(l,p);
            if(!l.isClientSide && player instanceof ServerPlayer sp && sp.isCreative() && l.getBlockEntity(p) instanceof Anchor a) {
                if(a.root.distSqr(p)<=256*256&&!l.hasChunkAt(a.root))sp.serverLevel().getChunkAt(a.root);
                Anchor root=a.configurationRoot();
                // Keep the interacted endpoint in the packet: permission/distance checks apply there.
                if(root!=null)TransportPackets.open(sp,p,root.kind().name().toLowerCase(Locale.ROOT),root.config());
            }
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
    }
    public static final class Anchor extends BlockEntity {
        public BlockPos root,apex,exit;
        public int duration=24;
        public boolean enabled=true,allMobs=false,allowPlayers=true,bidirectional=false,requiresPower=false;
        public int cost=0;
        public boolean autoDuration=true;
        public net.minecraft.core.Direction wallFace=net.minecraft.core.Direction.NORTH;
        public double clearance=.25;
        public Anchor(BlockPos p,BlockState s){super(TYPE.get(),p,s);root=p;}
        public Kind kind(){return ((TraversalBlock)getBlockState().getBlock()).kind;}
        private long lastWallCheck=Long.MIN_VALUE;
        private boolean wallValid;
        public boolean findWallExit(){
            if(level==null)return false;
            long now=level.getGameTime();
            if(lastWallCheck!=Long.MIN_VALUE&&now>=lastWallCheck&&now-lastWallCheck<20)return wallValid;
            lastWallCheck=now;wallValid=false;
            BlockPos best=null;net.minecraft.core.Direction bestFace=null;
            // Test all walls independently: the player's placement direction is irrelevant.
            for(var face:net.minecraft.core.Direction.Plane.HORIZONTAL){
                // At a roof opening the wall starts BELOW the marker, not beside its top.
                BlockPos wall;
                for(int y=worldPosition.getY()-1;y>=Math.max(level.getMinBuildHeight()+1,worldPosition.getY()-128);y--){
                    BlockPos feet=new BlockPos(worldPosition.getX(),y,worldPosition.getZ());wall=feet.relative(face);
                    if(!level.hasChunkAt(feet)||!level.hasChunkAt(wall)||level.getBlockState(wall).getCollisionShape(level,wall).isEmpty())break;
                    if(!level.getBlockState(feet).getCollisionShape(level,feet).isEmpty())break;
                    if(!level.getBlockState(feet.below()).getCollisionShape(level,feet.below()).isEmpty()){
                        if(best==null||feet.getY()<best.getY()||feet.getY()==best.getY()&&face==wallFace){best=feet;bestFace=face;}
                        break;
                    }
                }
            }
            if(best==null)return false;
            int ticks=autoDuration?Math.max(20,(int)Math.ceil((worldPosition.getY()-best.getY())/.075)):duration;
            boolean changed=!best.equals(exit)||wallFace!=bestFace||duration!=ticks;
            exit=best;wallFace=bestFace;duration=ticks;wallValid=true;if(changed&&!level.isClientSide)sync();return true;
        }
        private Vec3 wallOffset(){return Vec3.ZERO;}
        public Vec3 start(){return Vec3.atBottomCenterOf(worldPosition).add(wallOffset());}
        public Vec3 peak(){return Vec3.atBottomCenterOf(apex).add(0,clearance,0);}
        public Vec3 end(){return Vec3.atBottomCenterOf(exit).add(wallOffset());}
        public boolean accepts(Mob mob){return accepts(mob,false);}
        public boolean accepts(Mob mob,boolean reverse){return enabled && (kind()!=Kind.ZIPLINE||me.cryo.zombierool.gameplay.BlockPowerSettings.required(mob.level(),worldPosition,requiresPower,me.cryo.zombierool.config.GlobalSwitchState.isActivated(mob.level())||me.cryo.zombierool.gameplay.MapPower.receives(mob.level(),worldPosition))) && (kind()!=Kind.WALL||me.cryo.zombierool.gameplay.CrawlerLeap.isCrawler(mob)) && (allMobs || mob instanceof Monster || (ForgeRegistries.ENTITY_TYPES.getKey(mob.getType())!=null&&ForgeRegistries.ENTITY_TYPES.getKey(mob.getType()).getNamespace().equals("zombierool")))
                && (exit==null || !mob.level().getBlockCollisions(mob,mob.getBoundingBox().move((reverse?start():end()).subtract(mob.position()))).iterator().hasNext());}
        public boolean ready(){
            if(!enabled||level==null)return false;
            if(kind()==Kind.WALL)return findWallExit();
            if(exit==null || kind()==Kind.TRAVERSAL&&(apex==null||apex.getY()<Math.max(worldPosition.getY(),exit.getY())))return false;
            if(kind()!=Kind.TRAVERSAL)return level.hasChunkAt(exit)&&level.getBlockEntity(exit) instanceof Anchor e&&e.root.equals(worldPosition)&&e.kind()==kind()&&e.getBlockState().getValue(TraversalBlock.PHASE)==2&&!level.getBlockState(exit.below()).getCollisionShape(level,exit.below()).isEmpty();
            if(!level.hasChunkAt(apex)||!level.hasChunkAt(exit))return false;
            return level.getBlockEntity(apex) instanceof Anchor a && a.root.equals(worldPosition) && a.getBlockState().getValue(TraversalBlock.PHASE)==1
                    && level.getBlockEntity(exit) instanceof Anchor e && e.root.equals(worldPosition) && e.getBlockState().getValue(TraversalBlock.PHASE)==2
                    && !level.getBlockState(exit.below()).getCollisionShape(level,exit.below()).isEmpty();
        }
        public Anchor configurationRoot(){
            if(root.equals(worldPosition))return this;
            if(level==null||!level.hasChunkAt(root)||!(level.getBlockEntity(root) instanceof Anchor a)||a.kind()!=kind()||!a.root.equals(root))return null;
            return worldPosition.equals(a.exit)||worldPosition.equals(a.apex)?a:null;
        }
        public CompoundTag config(){Anchor primary=configurationRoot();if(primary!=null&&primary!=this)return primary.config();if(kind()==Kind.WALL)findWallExit();CompoundTag n=new CompoundTag();n.putInt("duration",duration);n.putBoolean("autoDuration",autoDuration);n.putInt("cost",cost);n.putDouble("clearance",clearance);n.putBoolean("enabled",enabled);n.putBoolean("allMobs",allMobs);n.putBoolean("allowPlayers",allowPlayers);n.putBoolean("bidirectional",bidirectional);n.putBoolean("requiresPower",requiresPower);n.putString("wallFace",wallFace.getName());n.putString("route",exit==null?"â€”":worldPosition.toShortString()+" → "+(apex==null?"":apex.toShortString()+" → ")+exit.toShortString());return n;}
        public void configure(CompoundTag n){Anchor primary=configurationRoot();if(primary==null)return;if(primary!=this){primary.configure(n);return;}if(kind()==Kind.WALL&&n.getInt("duration")!=duration)autoDuration=false;duration=Math.max(8,Math.min(kind()==Kind.WALL?2400:400,n.getInt("duration")));clearance=Double.isFinite(n.getDouble("clearance"))?Math.max(0,Math.min(16,n.getDouble("clearance"))):.25;enabled=n.getBoolean("enabled");allMobs=n.getBoolean("allMobs");allowPlayers=!n.contains("allowPlayers")||n.getBoolean("allowPlayers");bidirectional=n.getBoolean("bidirectional");requiresPower=n.getBoolean("requiresPower");cost=Math.max(0,Math.min(1000000,n.getInt("cost")));if(kind()==Kind.WALL){lastWallCheck=Long.MIN_VALUE;findWallExit();}sync();}
        private void syncLocal(){setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        public void sync(){
            if(level!=null)invalidate(level);syncLocal();if(level==null||!root.equals(worldPosition))return;
            for(BlockPos pos:new BlockPos[]{apex,exit})if(pos!=null&&level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof Anchor a&&a.root.equals(root)&&a.kind()==kind()){
                a.duration=duration;a.autoDuration=autoDuration;a.clearance=clearance;a.enabled=enabled;a.allMobs=allMobs;a.allowPlayers=allowPlayers;a.bidirectional=bidirectional;a.requiresPower=requiresPower;a.cost=cost;a.wallFace=wallFace;a.syncLocal();
            }
        }
        @Override public void onLoad(){super.onLoad();if(level!=null && getBlockState().getValue(TraversalBlock.PHASE)==0)remember(level,worldPosition,this);if(level instanceof ServerLevel server&&kind()==Kind.ZIPLINE)server.getServer().tell(new net.minecraft.server.TickTask(server.getServer().getTickCount()+1,()->{if(!isRemoved()&&server.hasChunkAt(worldPosition)&&getBlockState().getBlock() instanceof TraversalBlock block)block.ensureUpper(server,worldPosition,getBlockState());}));}
        @Override public void setRemoved(){if(level!=null)forget(level,worldPosition,this);super.setRemoved();}
        @Override protected void saveAdditional(CompoundTag n){super.saveAdditional(n);n.merge(config());n.putLong("root",root.asLong());if(apex!=null)n.putLong("apex",apex.asLong());if(exit!=null)n.putLong("exit",exit.asLong());}
        @Override public void load(CompoundTag n){super.load(n);lastWallCheck=Long.MIN_VALUE;if(level!=null)invalidate(level);root=n.contains("root")?BlockPos.of(n.getLong("root")):worldPosition;apex=n.contains("apex")?BlockPos.of(n.getLong("apex")):null;exit=n.contains("exit")?BlockPos.of(n.getLong("exit")):null;autoDuration=!n.contains("autoDuration")||n.getBoolean("autoDuration");duration=n.contains("duration")?Math.max(8,Math.min(kind()==Kind.WALL?2400:400,n.getInt("duration"))):24;clearance=n.contains("clearance")?n.getDouble("clearance"):.25;enabled=!n.contains("enabled")||n.getBoolean("enabled");allMobs=n.getBoolean("allMobs");allowPlayers=!n.contains("allowPlayers")||n.getBoolean("allowPlayers");bidirectional=n.getBoolean("bidirectional");requiresPower=n.getBoolean("requiresPower");cost=Math.max(0,n.getInt("cost"));var face=net.minecraft.core.Direction.byName(n.getString("wallFace"));if(face!=null&&face.getAxis().isHorizontal())wallFace=face;}
        @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
        @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    }
}
