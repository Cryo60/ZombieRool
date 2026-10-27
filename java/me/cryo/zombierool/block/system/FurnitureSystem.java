package me.cryo.zombierool.block.system;

import java.util.*;
import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.registries.*;

/** Baked furniture is also a plain mimic state: no entity or inventory required. */
public final class FurnitureSystem {
    public static final List<String> MODELS=List.of("wall_barricade_planks","sofa","sofa_corner","armchair","coffee_table","desk","drawer_desk","office_chair","filing_cabinet","nightstand","bed_head","bed_foot","locker","workbench","tool_cabinet","industrial_shelf","pallet","control_console","metal_table","dumpster","wheelie_bin","wastebasket","hospital_bed_head","hospital_bed_foot","medical_cart","medical_cabinet","iv_stand","hotel_dresser","suitcase_rack","reception_counter","restaurant_table","dining_chair","booth_seat","service_counter","cash_register","pizza_oven","display_fridge","chandelier","industrial_light","ceiling_fan","wall_painting","wall_poster","wall_lamp","bed","hospital_bed","wooden_barrel","metal_barrel","brazier","military_crate","field_radio","weather_station","trench_wall","duckboard","ammo_stack","bunker_vent","prison_bunk","prison_sink","barbed_wire_reel","sandbag_fortification","reinforced_crate","plague_crate","burning_barrel","wow_tavern_table","wow_tavern_bench","wow_tavern_chair","wow_weapon_rack","wow_supply_sacks","wow_alchemy_table","wow_smithing_anvil","wow_forge_bellows","wow_gravestone","wow_runestone","wow_plague_cauldron","wow_bone_pile","wow_soul_lantern","flag_france","wall_standard_france","flag_united_kingdom","wall_standard_united_kingdom","flag_germany_historical","wall_standard_germany_historical","flag_soviet_union","wall_standard_soviet_union","flag_japan","wall_standard_japan","flag_usa","wall_standard_usa","flag_russia","wall_standard_russia","flag_germany","wall_standard_germany","flag_cuba","wall_standard_cuba","flag_lordaeron","wall_standard_lordaeron","flag_scourge","wall_standard_scourge","flag_stormwind","wall_standard_stormwind","flag_orgrimmar","wall_standard_orgrimmar","flag_burning_legion","wall_standard_burning_legion","flag_scarlet_crusade","wall_standard_scarlet_crusade","flag_forsaken","wall_standard_forsaken","flag_japan_empire","wall_standard_japan_empire");
    public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"zombierool");
    public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"zombierool");
    public static final Map<String,RegistryObject<Block>> ALL=new LinkedHashMap<>();
    static {for(String id:MODELS){RegistryObject<Block> block=BLOCKS.register("furniture_"+id,()->id.equals("wall_barricade_planks")?new BarricadePlankBlock():id.equals("brazier")?new BrazierBlock():LargeFurnitureBlock.SIZES.containsKey(id)?new LargeFurnitureBlock(id):List.of("bed","hospital_bed","iv_stand").contains(id)?new ExtendedFurnitureBlock(id):(id.equals("pallet")||id.equals("duckboard"))?new PalletBlock(id):id.startsWith("sofa")?new SofaBlock(id):new FurnitureBlock(id));ALL.put(id,block);ITEMS.register("furniture_"+id,()->new BlockItem(block.get(),new Item.Properties()));}}
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);}
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool",bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class ClientLayers {
        @net.minecraftforge.eventbus.api.SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event){event.enqueueWork(()->{net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(ALL.get("brazier").get(),net.minecraft.client.renderer.RenderType.cutout());net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(ALL.get("burning_barrel").get(),net.minecraft.client.renderer.RenderType.cutout());});}
    }
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool",bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
    public static final class Inventory {
        @net.minecraftforge.eventbus.api.SubscribeEvent public static void content(net.minecraftforge.event.BuildCreativeModeTabContentsEvent e){if(e.getTabKey().location().equals(new net.minecraft.resources.ResourceLocation("zombierool:zb_rct")))for(var item:ITEMS.getEntries())e.accept(item.get());}
    }
    private static final Map<String,List<AABB>> BOXES=load();
    private static Map<String,List<AABB>> load(){try(var in=FurnitureSystem.class.getClassLoader().getResourceAsStream("data/zombierool/furniture/shapes.json");var reader=new java.io.InputStreamReader(Objects.requireNonNull(in),java.nio.charset.StandardCharsets.UTF_8)){
        var map=new HashMap<String,List<AABB>>();for(var entry:JsonParser.parseReader(reader).getAsJsonObject().entrySet()){var list=new ArrayList<AABB>();for(var row:entry.getValue().getAsJsonArray()){var b=row.getAsJsonArray();list.add(new AABB(b.get(0).getAsDouble()/16,b.get(1).getAsDouble()/16,b.get(2).getAsDouble()/16,b.get(3).getAsDouble()/16,b.get(4).getAsDouble()/16,b.get(5).getAsDouble()/16));}map.put(entry.getKey(),List.copyOf(list));}return Map.copyOf(map);
    }catch(Exception e){throw new IllegalStateException("Missing furniture collision definitions",e);}}
    public static List<AABB> boxes(String id){return BOXES.get(id);}
    public static final class BarricadePlankBlock extends FurnitureBlock {
        public static final IntegerProperty VARIANT=IntegerProperty.create("variant",0,9);
        public BarricadePlankBlock(){super("wall_barricade_planks");registerDefaultState(defaultBlockState().setValue(VARIANT,0));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){super.createBlockStateDefinition(b);b.add(VARIANT);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext context){return super.getStateForPlacement(context).setValue(VARIANT,context.getLevel().random.nextInt(10));}
        @Override public net.minecraft.world.InteractionResult use(BlockState state,Level level,BlockPos pos,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,BlockHitResult hit){
            if(!player.isCreative())return net.minecraft.world.InteractionResult.PASS;
            if(!level.isClientSide)level.setBlock(pos,state.cycle(VARIANT),3);
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
    }
    public static class FurnitureBlock extends HorizontalDirectionalBlock {
        public final String model;
        protected final Map<Direction,VoxelShape> shapes=new EnumMap<>(Direction.class);
        public FurnitureBlock(String id){super(Properties.of().strength(-1,3600000).noOcclusion().lightLevel(state->id.equals("brazier")?(state.getValue(BrazierBlock.FIRE)==0?0:12):List.of("chandelier","industrial_light","wall_lamp","brazier","burning_barrel","wow_soul_lantern").contains(id)?12:0).sound(id.equals("sofa")||id.equals("armchair")||id.startsWith("bed")?SoundType.WOOL:SoundType.WOOD));model=id;
            registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));
            for(var facing:Direction.Plane.HORIZONTAL){VoxelShape result=Shapes.empty();int turns=facing==Direction.NORTH?0:facing==Direction.EAST?1:facing==Direction.SOUTH?2:3;
                for(var original:BOXES.get(id)){AABB b=original;for(int i=0;i<turns;i++)b=new AABB(1-b.maxZ,b.minY,b.minX,1-b.minZ,b.maxY,b.maxX);result=Shapes.or(result,Shapes.create(b));}shapes.put(facing,result.optimize());}
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext context){
            Direction facing=(model.startsWith("wall_")&&context.getClickedFace().getAxis().isHorizontal())?context.getClickedFace():context.getHorizontalDirection().getOpposite();
            return defaultBlockState().setValue(FACING,facing);
        }
        @Override public BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
        @Override public BlockState mirror(BlockState s,Mirror m){return rotate(s,m.getRotation(s.getValue(FACING)));}
        @Override public VoxelShape getShape(BlockState s,BlockGetter level,BlockPos pos,CollisionContext context){return shapes.get(s.getValue(FACING));}
        @Override public boolean isPathfindable(BlockState s,BlockGetter l,BlockPos p,net.minecraft.world.level.pathfinder.PathComputationType type){return false;}
        public VoxelShape wholeShape(BlockState state){return getShape(state,null,BlockPos.ZERO,CollisionContext.empty());}
        @Override public void animateTick(BlockState state,Level level,BlockPos pos,net.minecraft.util.RandomSource random){
            if((model.equals("plague_crate")||model.equals("wow_plague_cauldron"))&&random.nextInt(5)==0)level.addParticle(me.cryo.zombierool.init.ZombieroolModParticleTypes.TOXIC_SMOKE.get(),pos.getX()+.2+random.nextDouble()*.6,pos.getY()+(model.equals("wow_plague_cauldron")?.75:.89),pos.getZ()+.2+random.nextDouble()*.6,(random.nextDouble()-.5)*.006,.012,(random.nextDouble()-.5)*.006);
            if(model.equals("brazier")||model.equals("burning_barrel")){double top=model.equals("burning_barrel")?1:.6;for(int i=0;i<2;i++)level.addParticle(net.minecraft.core.particles.ParticleTypes.FLAME,pos.getX()+.3+random.nextDouble()*.4,pos.getY()+top+random.nextDouble()*.3,pos.getZ()+.3+random.nextDouble()*.4,0,.015,0);if(random.nextInt(4)==0)level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,pos.getX()+.5,pos.getY()+top+.45,pos.getZ()+.5,0,.025,0);}
        }
        /** Centre of the largest real face, so wall weapons do not hover in empty table space. */
        public Vec3 weaponMount(BlockState state,Direction face){
            var boxes=getShape(state,null,BlockPos.ZERO,CollisionContext.empty()).toAabbs();var box=boxes.stream().max(Comparator.comparingDouble(b->b.getYsize()*(face.getAxis()==Direction.Axis.X?b.getZsize():b.getXsize()))).orElseThrow();var center=box.getCenter();
            return switch(face){case NORTH->new Vec3(center.x,center.y,box.minZ-.006);case SOUTH->new Vec3(center.x,center.y,box.maxZ+.006);case WEST->new Vec3(box.minX-.006,center.y,center.z);case EAST->new Vec3(box.maxX+.006,center.y,center.z);default->center;};
        }
    }
    public static final class BrazierBlock extends FurnitureBlock {
        public static final IntegerProperty FIRE=IntegerProperty.create("fire",0,2);
        BrazierBlock(){super("brazier");registerDefaultState(defaultBlockState().setValue(FIRE,1));}
        @Override public int getLightEmission(BlockState s,BlockGetter level,BlockPos pos){return s.getValue(FIRE)==0?0:12;}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){super.createBlockStateDefinition(b);b.add(FIRE);}
        @Override public void animateTick(BlockState state,Level level,BlockPos pos,net.minecraft.util.RandomSource random){if(state.getValue(FIRE)==0)return;var particle=state.getValue(FIRE)==2?net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME:net.minecraft.core.particles.ParticleTypes.FLAME;for(int i=0;i<2;i++)level.addParticle(particle,pos.getX()+.3+random.nextDouble()*.4,pos.getY()+.6+random.nextDouble()*.3,pos.getZ()+.3+random.nextDouble()*.4,0,.015,0);}
    }
    public static final class ExtendedFurnitureBlock extends FurnitureBlock {
        public static final BooleanProperty EXTENSION=BooleanProperty.create("extension");
        private static final ThreadLocal<Boolean> REMOVING=ThreadLocal.withInitial(()->false);
        private final Map<Direction,VoxelShape> extensions=new EnumMap<>(Direction.class);
        ExtendedFurnitureBlock(String id){super(id);registerDefaultState(defaultBlockState().setValue(EXTENSION,false));for(var facing:Direction.Plane.HORIZONTAL){var shape=Shapes.empty();int turns=facing==Direction.NORTH?0:facing==Direction.EAST?1:facing==Direction.SOUTH?2:3;for(var original:BOXES.get(id+"_extension")){var b=original;for(int i=0;i<turns;i++)b=new AABB(1-b.maxZ,b.minY,b.minX,1-b.minZ,b.maxY,b.maxX);shape=Shapes.or(shape,Shapes.create(b));}extensions.put(facing,shape.optimize());}}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){super.createBlockStateDefinition(b);b.add(EXTENSION);}
        public Direction extensionDirection(BlockState state){return model.equals("iv_stand")?Direction.UP:state.getValue(FACING).getOpposite();}
        @Override public BlockState getStateForPlacement(BlockPlaceContext context){var state=super.getStateForPlacement(context);return context.getLevel().getBlockState(context.getClickedPos().relative(extensionDirection(state))).isAir()?state:null;}
        @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(!level.isClientSide)level.setBlock(pos.relative(extensionDirection(state)),state.setValue(EXTENSION,true),3);}
        @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(state.getBlock()!=next.getBlock()&&!REMOVING.get()){REMOVING.set(true);try{var other=pos.relative(state.getValue(EXTENSION)?extensionDirection(state).getOpposite():extensionDirection(state));var partner=level.getBlockState(other);if(partner.is(this)&&partner.getValue(FACING)==state.getValue(FACING)&&partner.getValue(EXTENSION)!=state.getValue(EXTENSION))level.removeBlock(other,false);}finally{REMOVING.remove();}}super.onRemove(state,level,pos,next,moving);}
        @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return state.getValue(EXTENSION)?extensions.get(state.getValue(FACING)):super.getShape(state,level,pos,context);}
    }
    public static final class PalletBlock extends FurnitureBlock {
        public static final IntegerProperty LAYERS=IntegerProperty.create("layers",1,4);
        private final Map<Integer,VoxelShape> stacked=new HashMap<>();
        PalletBlock(String id){super(id);registerDefaultState(defaultBlockState().setValue(LAYERS,1));for(var face:Direction.Plane.HORIZONTAL)for(int layers=1;layers<=4;layers++){VoxelShape shape=Shapes.empty();for(int i=0;i<layers;i++)shape=Shapes.or(shape,shapes.get(face).move(0,i*.25,0));stacked.put(face.get2DDataValue()*4+layers,shape.optimize());}}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){super.createBlockStateDefinition(b);b.add(LAYERS);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext context){var current=context.getLevel().getBlockState(context.getClickedPos());return current.is(this)?current.setValue(LAYERS,Math.min(4,current.getValue(LAYERS)+1)):super.getStateForPlacement(context);}
        @Override public boolean canBeReplaced(BlockState state,BlockPlaceContext context){return !context.isSecondaryUseActive()&&context.getClickedFace()==Direction.UP&&context.getItemInHand().is(asItem())&&state.getValue(LAYERS)<4;}
        @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return stacked.get(state.getValue(FACING).get2DDataValue()*4+state.getValue(LAYERS));}
    }
    public static final class SofaBlock extends FurnitureBlock {
        public static final BooleanProperty LEFT=BooleanProperty.create("left"),RIGHT=BooleanProperty.create("right");
        private final Map<Integer,VoxelShape> connectedShapes=new HashMap<>();
        SofaBlock(String id){super(id);registerDefaultState(defaultBlockState().setValue(LEFT,false).setValue(RIGHT,false));for(var facing:Direction.Plane.HORIZONTAL)for(int mask=0;mask<4;mask++){
            var rows=BOXES.get(id+"_connected_"+mask);VoxelShape shape=Shapes.empty();int turns=facing==Direction.NORTH?0:facing==Direction.EAST?1:facing==Direction.SOUTH?2:3;
            for(var original:rows){var b=original;for(int i=0;i<turns;i++)b=new AABB(1-b.maxZ,b.minY,b.minX,1-b.minZ,b.maxY,b.maxX);shape=Shapes.or(shape,Shapes.create(b));}connectedShapes.put(facing.get2DDataValue()*4+mask,shape.optimize());}}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){super.createBlockStateDefinition(b);b.add(LEFT,RIGHT);}
        public BlockState connect(BlockState state,BlockGetter level,BlockPos pos){var facing=state.getValue(FACING);var left=model.equals("sofa_corner")?facing:facing.getCounterClockWise();return state.setValue(LEFT,isSofa(level,pos.relative(left))).setValue(RIGHT,isSofa(level,pos.relative(facing.getClockWise())));}
        private boolean isSofa(BlockGetter level,BlockPos pos){var state=level.getBlockState(pos);if(state.getBlock() instanceof SofaBlock)return true;return level.getBlockEntity(pos) instanceof MimicSystem.IMimicContainer mimic&&mimic.getMimic()!=null&&mimic.getMimic().getBlock() instanceof SofaBlock;}
        @Override public BlockState getStateForPlacement(BlockPlaceContext context){return connect(super.getStateForPlacement(context),context.getLevel(),context.getClickedPos());}
        @Override public BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other){return connect(state,level,pos);}
        @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){int mask=(state.getValue(LEFT)?1:0)|(state.getValue(RIGHT)?2:0);return connectedShapes.get(state.getValue(FACING).get2DDataValue()*4+mask);}
        @Override public BlockState mirror(BlockState state,Mirror mirror){var result=super.mirror(state,mirror);return mirror==Mirror.NONE?result:result.setValue(LEFT,state.getValue(RIGHT)).setValue(RIGHT,state.getValue(LEFT));}
    }
    private FurnitureSystem(){}
}
