package me.cryo.zombierool.block.system;

import me.cryo.zombierool.gameplay.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;

/** Physical authoring blocks. Benches have no recipe setting; only part supports are linked. */
public final class BuildableBlocks {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"zombierool");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"zombierool");
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"zombierool");
    public static final RegistryObject<Block> BENCH=BLOCKS.register("buildable_workbench",Workbench::new);
    public static final RegistryObject<Block> PART=BLOCKS.register("buildable_part",PartBlock::new);
    public static final RegistryObject<Block> DECO=BLOCKS.register("decorative_component",PartBlock::new);
    public static final RegistryObject<Block> DESTINATION=BLOCKS.register("buildable_destination",PartBlock::new);
    public static final RegistryObject<BlockEntityType<PartSupport>> SUPPORT=ENTITIES.register("buildable_part",()->BlockEntityType.Builder.of(PartSupport::new,PART.get(),DESTINATION.get(),DECO.get()).build(null));
    static {ITEMS.register("decorative_component",()->new BlockItem(DECO.get(),new Item.Properties()));ITEMS.register("buildable_workbench",()->new BlockItem(BENCH.get(),new Item.Properties()));ITEMS.register("buildable_destination",()->new BlockItem(DESTINATION.get(),new Item.Properties()));ITEMS.register("buildable_part",()->new BlockItem(PART.get(),new Item.Properties()));}
    public static void register(IEventBus bus){
        BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey().location().toString().equals("zombierool:zb_rct")){e.accept(BENCH.get());e.accept(DECO.get());}});
    }
    private static InteractionResult use(Level level,BlockPos pos,Player player){
        if(player instanceof ServerPlayer p){
            if(p.isCreative()&&p.hasPermissions(2)&&!WaveManager.isGameRunning())BuildablePackets.openAt(p,pos,"");
            else BuildableSystem.interact(p,pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    private static void removed(BlockState state,Level l,BlockPos pos,BlockState next){
        if(state.getBlock()!=next.getBlock()&&l instanceof ServerLevel s&&!WaveManager.isGameRunning()&&!MatchWorldJournal.isRestoring()&&!BuildableSystem.get(s).active())BuildableSystem.get(s).unbind(pos);
    }
    public static BlockPos root(Level level, BlockPos pos) {
        var state=level.getBlockState(pos);
        return state.getBlock() instanceof Workbench&&state.getValue(Workbench.RIGHT)?pos.relative(state.getValue(Workbench.FACING).getCounterClockWise()):pos;
    }
    public static Vec3 center(Level level, BlockPos pos) {
        pos=root(level,pos);var state=level.getBlockState(pos);Vec3 out=Vec3.atCenterOf(pos);
        return state.getBlock() instanceof Workbench?out.add(Vec3.atLowerCornerOf(state.getValue(Workbench.FACING).getClockWise().getNormal()).scale(.5)):out;
    }
    /** Full block item models use NONE so Minecraft's tiny ground-item transform is not applied. */
    public record ComponentPose(double x,double y,double z,float size,net.minecraft.world.item.ItemDisplayContext context,float rx,float ry,float rz,float rw) {}
    public static ComponentPose componentPose(ItemStack stack,Direction face){
        return me.cryo.zombierool.api.BuildableVisuals.resolve(stack,face,defaultComponentPose(stack,face));
    }
    private static ComponentPose defaultComponentPose(ItemStack stack,Direction face){
        boolean block=stack.getItem() instanceof BlockItem,weapon=me.cryo.zombierool.core.system.WeaponFacade.isWeapon(stack);
        if(stack.is(me.cryo.zombierool.api.BuildableVisuals.UPRIGHT)){
            if(face.getAxis().isVertical()){
                String id=ForgeRegistries.ITEMS.getKey(stack.getItem()).getPath();double lift=switch(id){case "shield_car_door"->.15;case "shield_hand_truck"->.34;case "turbine_mannequin"->.22;case "turbine_fan"->.09;case "turbine_tail"->.21;default->.55;};
                if("turbine_tail".equals(id))return new ComponentPose(.5,face==Direction.UP?lift:1-lift,.5,1,ItemDisplayContext.NONE,0,0,face==Direction.UP?.70710677f:-.70710677f,.70710677f);
                return new ComponentPose(.5,face==Direction.UP?lift:1-lift,.5,1,ItemDisplayContext.NONE,face==Direction.UP?.70710677f:-.70710677f,0,0,.70710677f);
            }
            float yaw=(float)Math.toRadians(180-face.toYRot());return new ComponentPose(.5-face.getStepX()*.05,.5,.5-face.getStepZ()*.05,1,ItemDisplayContext.NONE,0,(float)Math.sin(yaw/2),0,(float)Math.cos(yaw/2));
        }
        float size=block?.8f:1f;var context=block?net.minecraft.world.item.ItemDisplayContext.NONE:weapon?net.minecraft.world.item.ItemDisplayContext.GROUND:net.minecraft.world.item.ItemDisplayContext.FIXED;
        if(face.getAxis().isVertical())return new ComponentPose(.5,block?.45:weapon?.12:.08,.5,size,context,block||weapon?0:.70710677f,0,0,block||weapon?1:.70710677f);
        float yaw=(float)Math.toRadians(block?180-face.toYRot():-face.toYRot());double offset=block?.075:.43;
        return new ComponentPose(.5-face.getStepX()*offset,.5,.5-face.getStepZ()*offset,size,context,0,(float)Math.sin(yaw/2),0,(float)Math.cos(yaw/2));
    }
    public static final class Workbench extends HorizontalDirectionalBlock {
        public static final BooleanProperty RIGHT=BooleanProperty.create("right");
        public Workbench(){super(Properties.of().strength(3).sound(SoundType.WOOD).noOcclusion());registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(RIGHT,false));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,RIGHT);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){
            var facing=c.getHorizontalDirection().getOpposite();
            return c.getLevel().getBlockState(c.getClickedPos().relative(facing.getClockWise())).canBeReplaced(c)?defaultBlockState().setValue(FACING,facing):null;
        }
        @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,LivingEntity owner,ItemStack stack){l.setBlock(p.relative(s.getValue(FACING).getClockWise()),s.setValue(RIGHT,true),3);}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Block.box(0,0,1,16,16,15);}
        @Override public BlockState updateShape(BlockState s,Direction direction,BlockState other,LevelAccessor l,BlockPos pos,BlockPos neighbor){
            Direction side=s.getValue(FACING).getClockWise();if(s.getValue(RIGHT))side=side.getOpposite();
            if(direction==side&&(!other.is(this)||other.getValue(RIGHT)==s.getValue(RIGHT)||other.getValue(FACING)!=s.getValue(FACING)))return Blocks.AIR.defaultBlockState();
            return super.updateShape(s,direction,other,l,pos,neighbor);
        }
        @Override public void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moving){
            super.onPlace(s,l,p,old,moving);if(!s.getValue(RIGHT)&&l instanceof ServerLevel server&&!WaveManager.isGameRunning()&&!BuildableSystem.get(server).active())BuildableSystem.get(server).anchor(server,p,"","");
        }
        @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){removed(s,l,p,next);super.onRemove(s,l,p,next,moving);}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){return BuildableBlocks.use(l,root(l,p),player);}
        @Override public BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
        @Override public BlockState mirror(BlockState s,Mirror m){return rotate(s,m.getRotation(s.getValue(FACING)));}
    }
    public static final class PartBlock extends BaseEntityBlock {
        public static final DirectionProperty FACE=BlockStateProperties.FACING;
        public static final BooleanProperty HAS_ITEM=BooleanProperty.create("has_item");
        public PartBlock(){super(Properties.of().strength(2).sound(SoundType.METAL).noOcclusion().noCollission());registerDefaultState(stateDefinition.any().setValue(FACE,Direction.UP).setValue(HAS_ITEM,false));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACE,HAS_ITEM);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACE,c.getClickedFace());}
        @Override public boolean canBeReplaced(BlockState s,net.minecraft.world.item.context.BlockPlaceContext c){return s.is(DESTINATION.get());}
        @Override public void appendHoverText(ItemStack stack,BlockGetter level,java.util.List<net.minecraft.network.chat.Component> lines,TooltipFlag flags){
            super.appendHoverText(stack,level,lines,flags);if(stack.is(DECO.get().asItem()))lines.add(net.minecraft.network.chat.Component.translatable("block.zombierool.decorative_component.tooltip").withStyle(net.minecraft.ChatFormatting.GRAY));if(stack.is(DESTINATION.get().asItem()))lines.add(net.minecraft.network.chat.Component.translatable("gui.zombierool.buildable.marker_tooltip").withStyle(net.minecraft.ChatFormatting.AQUA));
        }
        @Override public RenderShape getRenderShape(BlockState s){return s.is(DESTINATION.get())||s.is(DECO.get())&&s.getValue(HAS_ITEM)?RenderShape.INVISIBLE:RenderShape.MODEL;}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Block.box(1,0,1,15,12,15);}
        @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Shapes.empty();}
        @Override public boolean isPathfindable(BlockState s,BlockGetter l,BlockPos p,net.minecraft.world.level.pathfinder.PathComputationType type){return true;}
        @Override public net.minecraft.world.level.pathfinder.BlockPathTypes getBlockPathType(BlockState s,BlockGetter l,BlockPos p,net.minecraft.world.entity.Mob mob){return net.minecraft.world.level.pathfinder.BlockPathTypes.OPEN;}
        @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new PartSupport(p,s);}
        @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,LivingEntity owner,ItemStack stack){super.setPlacedBy(l,p,s,owner,stack);if(l.getBlockEntity(p) instanceof PartSupport support)support.attach();}
        @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){removed(s,l,p,next);super.onRemove(s,l,p,next,moving);}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
            if(s.is(DECO.get())){
                if(player instanceof ServerPlayer author&&author.isCreative()&&author.hasPermissions(2)&&!WaveManager.isGameRunning()&&l.getBlockEntity(p) instanceof PartSupport support){
                    ItemStack visual=player.getItemInHand(hand);
                    if(player.isShiftKeyDown())support.configure("","",ItemStack.EMPTY);
                    else if(!visual.isEmpty()&&!visual.is(DECO.get().asItem()))support.configure("","",visual);
                }
                return InteractionResult.sidedSuccess(l.isClientSide);
            }
            return BuildableBlocks.use(l,p,player);
        }
    }
    public static final class PartSupport extends BlockEntity {
        public String recipe="",part="";
        public ItemStack visual=ItemStack.EMPTY;
        public PartSupport(BlockPos p,BlockState s){super(SUPPORT.get(),p,s);}
        @Override public AABB getRenderBoundingBox(){return new AABB(worldPosition).inflate(4);}
        public void configure(String recipe,String part,ItemStack visual){
            this.recipe=recipe;this.part=part;this.visual=visual.copyWithCount(1);setChanged();
            syncBase();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
        }
        public void attach(){if(!getBlockState().is(DECO.get())&&level instanceof ServerLevel server&&!WaveManager.isGameRunning()&&!recipe.isEmpty()&&!part.isEmpty()&&!visual.isEmpty()){
            var data=BuildableSystem.get(server);if(!data.active()&&data.definitions().getCompound("recipes").contains(recipe)){if(getBlockState().is(DESTINATION.get()))data.destination(server,worldPosition,recipe);else data.configurePart(server,worldPosition,recipe,part,visual);}
        }}
        private void syncBase(){if(level instanceof ServerLevel server&&getBlockState().is(DECO.get())&&getBlockState().getValue(PartBlock.HAS_ITEM)!=!visual.isEmpty())server.setBlock(worldPosition,getBlockState().setValue(PartBlock.HAS_ITEM,!visual.isEmpty()),3);}
        @Override public void onLoad(){super.onLoad();attach();if(level instanceof ServerLevel server)server.getServer().tell(new net.minecraft.server.TickTask(server.getServer().getTickCount()+1,()->{if(!isRemoved())syncBase();}));}
        @Override protected void saveAdditional(CompoundTag n){super.saveAdditional(n);n.putString("recipe",recipe);n.putString("part",part);n.put("visual",visual.save(new CompoundTag()));}
        @Override public void load(CompoundTag n){super.load(n);recipe=n.getString("recipe");part=n.getString("part");visual=ItemStack.of(n.getCompound("visual"));}
        @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
        @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    }
}
