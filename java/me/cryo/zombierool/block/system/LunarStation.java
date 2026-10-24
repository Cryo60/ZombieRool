package me.cryo.zombierool.block.system;

import me.cryo.zombierool.gameplay.LunarEquipment;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;

/** A physical, one by one by two dispenser. Either half grants the same secondary equipment. */
@Mod.EventBusSubscriber(modid="zombierool",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class LunarStation {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"zombierool");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"zombierool");
    private static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS=DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,"zombierool");
    static {for(String id:java.util.List.of("helmet_handle","helmet_seal","helmet_unseal","helmet_breath"))SOUNDS.register(id,()->net.minecraft.sounds.SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation("zombierool",id)));}
    public static final RegistryObject<Block> STATION=BLOCKS.register("lunar_helmet_station",StationBlock::new);
    public static final RegistryObject<Block> DIVING_STATION=BLOCKS.register("diving_helmet_station",()->new StationBlock(LunarEquipment.SuitStyle.DIVING));
    public static final RegistryObject<Block> HAZMAT_STATION=BLOCKS.register("hazmat_helmet_station",()->new StationBlock(LunarEquipment.SuitStyle.HAZMAT));
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"zombierool");
    public static final RegistryObject<Block> ZONE=BLOCKS.register("atmosphere_zone",AtmosphereZoneBlock::new);
    public static final RegistryObject<Item> ZONE_ITEM=ITEMS.register("atmosphere_zone",()->new BlockItem(ZONE.get(),new Item.Properties()));
    public static final RegistryObject<BlockEntityType<AtmosphereZoneBlock.Zone>> ZONE_ENTITY=ENTITIES.register("atmosphere_zone",()->BlockEntityType.Builder.of(AtmosphereZoneBlock.Zone::new,ZONE.get()).build(null));
    public static final RegistryObject<Item> STATION_ITEM=ITEMS.register("lunar_helmet_station",()->new BlockItem(STATION.get(),new Item.Properties()));
    public static final RegistryObject<Item> DIVING_STATION_ITEM=ITEMS.register("diving_helmet_station",()->new BlockItem(DIVING_STATION.get(),new Item.Properties()));
    public static final RegistryObject<Item> HAZMAT_STATION_ITEM=ITEMS.register("hazmat_helmet_station",()->new BlockItem(HAZMAT_STATION.get(),new Item.Properties()));
    public static final RegistryObject<Item> HELMET=ITEMS.register("lunar_helmet",()->new SuitItem(LunarEquipment.SuitStyle.LUNAR));
    public static final RegistryObject<Item> DIVING_HELMET=ITEMS.register("diving_helmet",()->new SuitItem(LunarEquipment.SuitStyle.DIVING));
    public static final RegistryObject<Item> HAZMAT_HELMET=ITEMS.register("hazmat_helmet",()->new SuitItem(LunarEquipment.SuitStyle.HAZMAT));
    public static Item helmet(LunarEquipment.SuitStyle style){return switch(style){case DIVING->DIVING_HELMET.get();case HAZMAT->HAZMAT_HELMET.get();default->HELMET.get();};}
    public static final class SuitItem extends Item {
        public final LunarEquipment.SuitStyle style;
        public SuitItem(LunarEquipment.SuitStyle style){super(new Item.Properties().stacksTo(1));this.style=style;}
        @Override public void inventoryTick(ItemStack stack,Level level,net.minecraft.world.entity.Entity entity,int slot,boolean selected){if(entity instanceof ServerPlayer p&&!stack.isEmpty()){if(LunarEquipment.acquire(p,style))LunarEquipment.setEquipped(p,true);stack.shrink(1);}}
        @Override public InteractionResultHolder<ItemStack> use(Level level,net.minecraft.world.entity.player.Player player,InteractionHand hand){
            if(player instanceof ServerPlayer p){LunarEquipment.acquire(p,style);LunarEquipment.toggle(p);}return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
        }
    }
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);SOUNDS.register(bus);ENTITIES.register(bus);LunarEquipment.register();LunarZonePackets.register();}
    @SubscribeEvent public static void creative(BuildCreativeModeTabContentsEvent e){if(e.getTabKey().location().equals(new net.minecraft.resources.ResourceLocation("zombierool","zb_rct"))){for(var item:java.util.List.of(STATION_ITEM,HELMET,DIVING_STATION_ITEM,DIVING_HELMET,HAZMAT_STATION_ITEM,HAZMAT_HELMET))e.accept(item.get());}}
    public static net.minecraft.network.chat.Component hint(net.minecraft.world.entity.player.Player player,StationBlock station,String key){
        boolean owned=LunarEquipment.data(player).getBoolean("owned")&&LunarEquipment.style(player)==station.style;
        return net.minecraft.network.chat.Component.translatable(owned?"message.zombierool.suit_owned":"message.zombierool.suit_take",key,net.minecraft.network.chat.Component.translatable("item.zombierool."+station.style.id+"_helmet"));
    }
    public static void interact(ServerPlayer p){interact(p,LunarEquipment.SuitStyle.LUNAR);}
    public static void interact(ServerPlayer p,LunarEquipment.SuitStyle style){
        if(!p.isAlive()||p.isSpectator()||me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(p.getUUID()))return;
        boolean acquired=LunarEquipment.acquire(p,style);p.displayClientMessage(net.minecraft.network.chat.Component.translatable(acquired?"message.zombierool.suit_acquired":"message.zombierool.suit_owned",net.minecraft.network.chat.Component.keybind("key.zombierool.lunar_helmet"),net.minecraft.network.chat.Component.translatable("item.zombierool."+style.id+"_helmet")),true);
        if(acquired)LunarEquipment.setEquipped(p,true);
    }
    public static final class StationBlock extends Block implements me.cryo.zombierool.block.WaterloggedElement {
        public final LunarEquipment.SuitStyle style;
        public static final EnumProperty<DoubleBlockHalf> HALF=BlockStateProperties.DOUBLE_BLOCK_HALF;
        public static final DirectionProperty FACING=HorizontalDirectionalBlock.FACING;
        private static final VoxelShape SHAPE=Block.box(1,0,1,15,16,15);
        public StationBlock(){this(LunarEquipment.SuitStyle.LUNAR);}
        public StationBlock(LunarEquipment.SuitStyle style){super(Properties.of().mapColor(MapColor.METAL).strength(3).sound(SoundType.METAL).noOcclusion());this.style=style;registerDefaultState(stateDefinition.any().setValue(HALF,DoubleBlockHalf.LOWER).setValue(FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(HALF,FACING);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){return c.getClickedPos().getY()<c.getLevel().getMaxBuildHeight()-1&&c.getLevel().getBlockState(c.getClickedPos().above()).canBeReplaced(c)?defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite()):null;}
        @Override public void setPlacedBy(Level l,BlockPos pos,BlockState s,LivingEntity owner,ItemStack stack){l.setBlock(pos.above(),s.setValue(HALF,DoubleBlockHalf.UPPER),3);}
        @Override public BlockState updateShape(BlockState s,Direction d,BlockState neighbor,LevelAccessor l,BlockPos p,BlockPos other){
            Direction counterpart=s.getValue(HALF)==DoubleBlockHalf.LOWER?Direction.UP:Direction.DOWN;
            if(d==counterpart&&(!neighbor.is(this)||neighbor.getValue(HALF)==s.getValue(HALF)))return Blocks.AIR.defaultBlockState();
            return super.updateShape(s,d,neighbor,l,p,other);
        }
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPE;}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,net.minecraft.world.entity.player.Player player,InteractionHand hand,BlockHitResult hit){
            if(player instanceof ServerPlayer p)interact(p,style);
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
    }
    private LunarStation(){}
}
