// [main\java\me\cryo\zombierool\block\system\UniversalSpawnerSystem.java]
package me.cryo.zombierool.block.system;

import io.netty.buffer.Unpooled;
import me.cryo.zombierool.config.GlobalSwitchState;
import me.cryo.zombierool.gameplay.WaveManager;
import me.cryo.zombierool.config.WorldConfig;
import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.block.AbstractTechnicalBlock;
import me.cryo.zombierool.client.gui.UnifiedConfigScreen;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.C2SSetUniversalSpawnerConfigPacket;
import me.cryo.zombierool.spawner.SpawnerRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class UniversalSpawnerSystem {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ZombieroolMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ZombieroolMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ZombieroolMod.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, ZombieroolMod.MODID);

    public static final RegistryObject<Block> UNIVERSAL_SPAWNER_BLOCK = BLOCKS.register("universal_spawner", UniversalSpawnerBlock::new);
    public static final RegistryObject<Block> ACTOR_MARKER_BLOCK = BLOCKS.register("map_actor_marker", me.cryo.zombierool.block.MapActorMarkerBlock::new);
    public static final RegistryObject<Item> ACTOR_MARKER_ITEM = ITEMS.register("map_actor_marker", () -> new BlockItem(ACTOR_MARKER_BLOCK.get(),new Item.Properties()));
    public static final RegistryObject<Item> ACTOR_EGG_ITEM = ITEMS.register("map_actor_spawn_egg",me.cryo.zombierool.item.MapActorSpawnEggItem::new);
    public static final RegistryObject<BlockEntityType<me.cryo.zombierool.block.MapActorMarkerBlock.MarkerData>> ACTOR_MARKER_BE=BLOCK_ENTITIES.register("map_actor_marker",()->BlockEntityType.Builder.of(me.cryo.zombierool.block.MapActorMarkerBlock.MarkerData::new,ACTOR_MARKER_BLOCK.get()).build(null));
    public static final RegistryObject<Item> UNIVERSAL_SPAWNER_ITEM = ITEMS.register("universal_spawner", () -> new BlockItem(UNIVERSAL_SPAWNER_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Block> MINI_BOSS_SPAWNER_BLOCK = BLOCKS.register("mini_boss_spawner", () -> new UniversalSpawnerBlock(1));
    public static final RegistryObject<Block> MINION_SPAWNER_BLOCK = BLOCKS.register("minion_spawner", () -> new UniversalSpawnerBlock(2));
    public static final RegistryObject<Item> MINI_BOSS_SPAWNER_ITEM = ITEMS.register("mini_boss_spawner", () -> new BlockItem(MINI_BOSS_SPAWNER_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> MINION_SPAWNER_ITEM = ITEMS.register("minion_spawner", () -> new BlockItem(MINION_SPAWNER_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<UniversalSpawnerBlockEntity>> UNIVERSAL_SPAWNER_BE = BLOCK_ENTITIES.register("universal_spawner", () -> BlockEntityType.Builder.of(UniversalSpawnerBlockEntity::new, UNIVERSAL_SPAWNER_BLOCK.get(), MINI_BOSS_SPAWNER_BLOCK.get(), MINION_SPAWNER_BLOCK.get()).build(null));
    public static final RegistryObject<MenuType<UniversalSpawnerManagerMenu>> UNIVERSAL_SPAWNER_MENU = MENUS.register("sys_universal_spawner_manager", () -> IForgeMenuType.create(UniversalSpawnerManagerMenu::new));

    public static final RegistryObject<Block> LEGACY_ZOMBIE_BLOCK = BLOCKS.register("spawner_zombie", () -> new LegacySpawnerBlock(SpawnerMobType.ZOMBIE));
    public static final RegistryObject<Block> LEGACY_CRAWLER_BLOCK = BLOCKS.register("spawner_crawler", () -> new LegacySpawnerBlock(SpawnerMobType.CRAWLER));
    public static final RegistryObject<Block> LEGACY_DOG_BLOCK = BLOCKS.register("spawner_dog", () -> new LegacySpawnerBlock(SpawnerMobType.HELLHOUND));
    public static final RegistryObject<Block> LEGACY_PLAYER_BLOCK = BLOCKS.register("player_spawner", () -> new LegacySpawnerBlock(SpawnerMobType.PLAYER));

    public static final RegistryObject<BlockEntityType<LegacySpawnerBE>> LEGACY_ZOMBIE_BE = BLOCK_ENTITIES.register("spawner_zombie", () -> BlockEntityType.Builder.of(LegacySpawnerBE::new, LEGACY_ZOMBIE_BLOCK.get()).build(null));
    public static final RegistryObject<BlockEntityType<LegacySpawnerBE>> LEGACY_CRAWLER_BE = BLOCK_ENTITIES.register("spawner_crawler", () -> BlockEntityType.Builder.of(LegacySpawnerBE::new, LEGACY_CRAWLER_BLOCK.get()).build(null));
    public static final RegistryObject<BlockEntityType<LegacySpawnerBE>> LEGACY_DOG_BE = BLOCK_ENTITIES.register("spawner_dog", () -> BlockEntityType.Builder.of(LegacySpawnerBE::new, LEGACY_DOG_BLOCK.get()).build(null));
    public static final RegistryObject<BlockEntityType<LegacySpawnerBE>> LEGACY_PLAYER_BE = BLOCK_ENTITIES.register("player_spawner", () -> BlockEntityType.Builder.of(LegacySpawnerBE::new, LEGACY_PLAYER_BLOCK.get()).build(null));

    static {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); MENUS.register(bus);
    }

    @SubscribeEvent
    public static void buildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(new ResourceLocation(ZombieroolMod.MODID, "zb_rct"))) {
            event.accept(MINI_BOSS_SPAWNER_ITEM.get());
            event.accept(ACTOR_MARKER_ITEM.get());
            event.accept(ACTOR_EGG_ITEM.get());
            event.accept(MINION_SPAWNER_ITEM.get());
            for (SpawnerMobType type : SpawnerMobType.values()) {
                if(type.special())continue;
                ItemStack stack = new ItemStack(UNIVERSAL_SPAWNER_ITEM.get());
                CompoundTag blockStateTag = new CompoundTag();
                blockStateTag.putString("mob_type", type.getSerializedName());
                stack.getOrCreateTag().put("BlockStateTag", blockStateTag);
                CompoundTag beTag = new CompoundTag();
                beTag.putString("MobType", type.getSerializedName());
                BlockItem.setBlockEntityData(stack, UNIVERSAL_SPAWNER_BE.get(), beTag);
                Component typeName = Component.translatable("mob.zombierool." + type.getSerializedName());
                stack.setHoverName(Component.translatable("block.zombierool.universal_spawner.type", typeName).withStyle(ChatFormatting.BLUE));
                event.accept(stack);
            }
        }
    }

    public enum SpawnerMobType implements StringRepresentable {
        ZOMBIE("zombie"), CRAWLER("crawler"), HELLHOUND("hellhound"), PLAYER("player"), PANZER("panzer"), BRUTUS("brutus"), NAPALM_MINION("napalm_minion"), SKELETON_MINION("skeleton_minion");
        public boolean boss(){return this==PANZER||this==BRUTUS;}
        public boolean special(){return boss()||this==NAPALM_MINION||this==SKELETON_MINION;}
        private final String name;
        SpawnerMobType(String name) { this.name = name; }
        @Override public String getSerializedName() { return this.name; }
        public static SpawnerMobType fromString(String name) {
            for (SpawnerMobType type : values()) if (type.name.equals(name)) return type;
            return ZOMBIE;
        }
    }

    public static class UniversalSpawnerBlock extends AbstractTechnicalBlock implements EntityBlock {
        public static final EnumProperty<SpawnerMobType> MOB_TYPE = EnumProperty.create("mob_type", SpawnerMobType.class);
        public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

        public final int family;
        public List<SpawnerMobType> allowedTypes(){return family==1?List.of(SpawnerMobType.PANZER,SpawnerMobType.BRUTUS):family==2?List.of(SpawnerMobType.NAPALM_MINION,SpawnerMobType.SKELETON_MINION):Arrays.asList(SpawnerMobType.values());}
        public UniversalSpawnerBlock(){this(0);}
        public UniversalSpawnerBlock(int family) {
            super(BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.EMPTY).strength(-1, 3600000).noCollission().noOcclusion());
            this.family=family;
            this.registerDefaultState(this.stateDefinition.any().setValue(MOB_TYPE, family==1?SpawnerMobType.PANZER:family==2?SpawnerMobType.NAPALM_MINION:SpawnerMobType.ZOMBIE).setValue(ACTIVE, true));
        }

        @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
        @Override public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) { return type == PathComputationType.LAND; }
        @Override public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) { return true; }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(MOB_TYPE, ACTIVE); }

        @Override protected void addTechnicalTooltip(List<Component> tooltip) {
            tooltip.add(Component.translatable("block.zombierool.universal_spawner.tooltip.1").withStyle(ChatFormatting.BLUE));
            tooltip.add(Component.translatable("block.zombierool.universal_spawner.tooltip.2").withStyle(ChatFormatting.GRAY));
        }

        @Override
        public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (!world.isClientSide && player.isCreative()) {
                if (world.getBlockEntity(pos) instanceof MenuProvider provider) {
                    NetworkHooks.openScreen((ServerPlayer) player, provider, pos);
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof UniversalSpawnerBlockEntity ube) {
                ube.syncWithWorldConfig(false); SpawnerRegistry.registerSpawner(level,ube);ube.tick();
            }
        }

        @Override
        public ItemStack getCloneItemStack(BlockGetter level,BlockPos pos,BlockState state) {
            ItemStack stack=new ItemStack(this);
            if(level.getBlockEntity(pos) instanceof UniversalSpawnerBlockEntity be){
                stack.getOrCreateTag().put("BlockEntityTag",be.saveWithoutMetadata());
                CompoundTag blockState=new CompoundTag();blockState.putString("mob_type",be.getMobType().getSerializedName());
                stack.getOrCreateTag().put("BlockStateTag",blockState);
            }
            return stack;
        }
        @Override public ItemStack getCloneItemStack(BlockState state,net.minecraft.world.phys.HitResult target,net.minecraft.world.level.BlockGetter level,BlockPos pos,Player player){return getCloneItemStack(level,pos,state);}

        @Override
        public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
            super.onPlace(state, world, pos, oldState, isMoving);
            if (!world.isClientSide() && world instanceof ServerLevel serverLevel) {
                WorldConfig.get(serverLevel).addPathPosition(pos.immutable(), serverLevel);
            }
        }

        @Override
        public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
            if (!world.isClientSide() && world instanceof ServerLevel serverLevel && state.getBlock() != newState.getBlock()) {
                WorldConfig.get(serverLevel).removePathPosition(pos.immutable(), serverLevel);
            }
            super.onRemove(state, world, pos, newState, isMoving);
        }

        @Override
        public BlockPathTypes getBlockPathType(BlockState state, BlockGetter world, BlockPos pos, @Nullable net.minecraft.world.entity.Mob mob) {
            return BlockPathTypes.OPEN;
        }

        @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new UniversalSpawnerBlockEntity(pos, state); }

        @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return level.isClientSide ? null : (lvl, pos, st, be) -> { if (be instanceof UniversalSpawnerBlockEntity spawner) spawner.tick(); };
        }
    }

    public static class UniversalSpawnerBlockEntity extends BlockEntity implements MenuProvider {
        private SpawnerMobType mobType = SpawnerMobType.ZOMBIE;
        private String zone = "";
        private String startChannels = "0";
        private String stopChannels = "0";
        private boolean requirePower = false;
        private int spawnWeight = 1;
        private boolean loadedSpecialSettings;
        private CompoundTag specialSettings=defaultSpecialConfig();
        private me.cryo.zombierool.gameplay.BlockPowerSettings.Mode clientPowerMode=me.cryo.zombierool.gameplay.BlockPowerSettings.Mode.DEFAULT;
        public me.cryo.zombierool.gameplay.BlockPowerSettings.Mode getPowerMode(){return level instanceof ServerLevel server?me.cryo.zombierool.gameplay.BlockPowerSettings.storedMode(server,worldPosition):clientPowerMode;}
        public int lastBossWave=-1;
        public long nextMinionTick;
        public long specialSession;
        public void resetSpecialRuntime(){lastBossWave=-1;nextMinionTick=0;setChanged();}
        public static CompoundTag defaultSpecialConfig(){var tag=new CompoundTag();tag.putInt("FirstWave",8);tag.putInt("WaveInterval",8);tag.putInt("Radius",12);tag.putInt("Cooldown",30);tag.putInt("MaxAlive",1);tag.putInt("Batch",1);tag.putBoolean("UseRadius",false);return tag;}
        public CompoundTag specialConfig(){if(mobType.special()&&level instanceof ServerLevel server){var result=specialSettings.copy();result.merge(me.cryo.zombierool.config.SpecialEnemyConfig.spawnerSettings(server,mobType));return result;}return specialSettings.copy();}
        public void applyMapBossSettings(CompoundTag settings){
            if(!mobType.special())return;
            if(!mobType.boss()){if(level instanceof ServerLevel server)specialSettings.merge(me.cryo.zombierool.config.SpecialEnemyConfig.spawnerSettings(server,mobType));setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);return;}
            mobType=settings.getString("type").equals("brutus")?SpawnerMobType.BRUTUS:SpawnerMobType.PANZER;
            specialSettings.putInt("FirstWave",settings.getInt("firstWave"));specialSettings.putInt("WaveInterval",settings.getInt("interval"));
            setChanged();
            if(level instanceof ServerLevel){var state=getBlockState();if(state.hasProperty(UniversalSpawnerBlock.MOB_TYPE))level.setBlock(worldPosition,state.setValue(UniversalSpawnerBlock.MOB_TYPE,mobType),3);level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        }
        public void setSpecialConfig(CompoundTag tag){
            if(tag==null)return;
            if(mobType.special()&&!mobType.boss()&&level instanceof ServerLevel server){var changes=new CompoundTag();String prefix=mobType==SpawnerMobType.NAPALM_MINION?"napalm":"skeleton";for(String key:java.util.List.of("FirstWave","Radius","Cooldown","MaxAlive","Batch","UseRadius"))if(tag.contains(key))changes.put(prefix+key,tag.get(key).copy());me.cryo.zombierool.config.SpecialEnemyConfig.update(server,changes);return;}
            if(mobType.boss()&&level instanceof ServerLevel server){var changes=new CompoundTag();if(tag.contains("FirstWave",3))changes.putInt("firstWave",tag.getInt("FirstWave"));if(tag.contains("WaveInterval",3))changes.putInt("interval",tag.getInt("WaveInterval"));me.cryo.zombierool.config.SpecialEnemyConfig.update(server,changes);return;}
            if(tag.contains("UseRadius",1))specialSettings.putBoolean("UseRadius",tag.getBoolean("UseRadius"));
            String[] keys={"FirstWave","WaveInterval","Radius","Cooldown","MaxAlive","Batch"};
            int[] maximum={10000,10000,128,3600,64,16};
            for(int i=0;i<keys.length;i++)if(tag.contains(keys[i],3))specialSettings.putInt(keys[i],Math.max(1,Math.min(maximum[i],tag.getInt(keys[i]))));
            setChanged();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
        }

        public UniversalSpawnerBlockEntity(BlockPos pos, BlockState state) { super(UNIVERSAL_SPAWNER_BE.get(), pos, state); mobType=state.getValue(UniversalSpawnerBlock.MOB_TYPE); if(mobType==SpawnerMobType.NAPALM_MINION)specialSettings.putInt("FirstWave",5); if(mobType==SpawnerMobType.SKELETON_MINION){specialSettings.putBoolean("UseRadius",true);specialSettings.putInt("Batch",3);specialSettings.putInt("Cooldown",8);specialSettings.putInt("MaxAlive",12);} }

        public void tick() {
            if (level == null || level.isClientSide) return;
            boolean currentlyActive = isActive(level);
            BlockState currentState = getBlockState();
            boolean stateChanged = false;

            if (currentState.getValue(UniversalSpawnerBlock.ACTIVE) != currentlyActive) {
                currentState = currentState.setValue(UniversalSpawnerBlock.ACTIVE, currentlyActive);
                stateChanged = true;
            }
            if (currentState.getValue(UniversalSpawnerBlock.MOB_TYPE) != mobType) {
                currentState = currentState.setValue(UniversalSpawnerBlock.MOB_TYPE, mobType);
                stateChanged = true;
            }
            if (stateChanged) level.setBlock(worldPosition, currentState, 3);
            me.cryo.zombierool.gameplay.SpecialEnemySpawns.tick(this);
        }

        public List<Integer> getParsedChannels(String channelsStr) {
            if (channelsStr == null || channelsStr.isBlank()) {
                return new ArrayList<>(Arrays.asList(0));
            }
            List<Integer> list = Arrays.stream(channelsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try { return Integer.parseInt(s); } catch (Exception e) { return -1; }
                })
                .filter(i -> i >= 0)
                .collect(Collectors.toList());
            if (list.isEmpty()) {
                list.add(0);
            }
            return list;
        }

        public boolean isActive(Level level) {
            boolean hasPower = me.cryo.zombierool.gameplay.BlockPowerSettings.required(level,worldPosition,requirePower,GlobalSwitchState.isActivated(level));
            if (!hasPower) return false;

            List<Integer> starts = getParsedChannels(startChannels);
            boolean isLocked = starts.stream().anyMatch(WaveManager.LOCKED_CHANNELS::contains);
            if (isLocked) return false;

            boolean isStarted = false;

            if (this.mobType == SpawnerMobType.PLAYER) {
                isStarted = starts.contains(0) || starts.stream().anyMatch(c -> c > 0 && WaveManager.UNLOCKED_CHANNELS.contains(c));
            } else {
                boolean isInitialSpawn = false;
                if (starts.contains(0)) {
                    if (zone == null || zone.trim().isEmpty()) {
                        isInitialSpawn = true;
                    } else {
                        String[] myZones = zone.split(",");
                        for (String z : myZones) {
                            if (WaveManager.UNLOCKED_ZONES.contains(z.trim().toUpperCase(java.util.Locale.ROOT))) {
                                isInitialSpawn = true;
                                break;
                            }
                        }
                    }
                }

                boolean isUnlockedViaDoor = starts.stream().anyMatch(c -> c > 0 && WaveManager.UNLOCKED_CHANNELS.contains(c));

                isStarted = isInitialSpawn || isUnlockedViaDoor;
            }

            if (!isStarted) return false;

            List<Integer> stops = getParsedChannels(stopChannels);
            boolean isStopped = !stops.isEmpty() && !stops.contains(0) && stops.stream().anyMatch(WaveManager.UNLOCKED_CHANNELS::contains);
            return !isStopped;
        }

        public SpawnerMobType getMobType() { return mobType; }
        public String getZone() { return zone; }
        public String getStartChannels() { return startChannels; }
        public String getStopChannels() { return stopChannels; }
        public boolean doesRequirePower() { return requirePower; }
        public int getSpawnWeight() { return spawnWeight; }

        public void setConfig(SpawnerMobType mobType, String zone, String startChannels, String stopChannels, boolean requirePower, int spawnWeight) {
            if(getBlockState().getBlock() instanceof UniversalSpawnerBlock block && block.family!=0 && !block.allowedTypes().contains(mobType))return;
            if(this.mobType!=mobType){
                if(specialSettings.getInt("FirstWave")== (this.mobType==SpawnerMobType.NAPALM_MINION?5:8))specialSettings.putInt("FirstWave",mobType==SpawnerMobType.NAPALM_MINION?5:8);
                specialSettings.putBoolean("UseRadius",mobType==SpawnerMobType.SKELETON_MINION);
                if(specialSettings.getInt("Cooldown")== (this.mobType==SpawnerMobType.SKELETON_MINION?8:30))specialSettings.putInt("Cooldown",mobType==SpawnerMobType.SKELETON_MINION?8:30);
                if(specialSettings.getInt("MaxAlive")== (this.mobType==SpawnerMobType.SKELETON_MINION?12:1))specialSettings.putInt("MaxAlive",mobType==SpawnerMobType.SKELETON_MINION?12:1);
                int oldDefault=this.mobType==SpawnerMobType.SKELETON_MINION?3:1;if(specialSettings.getInt("Batch")==oldDefault)specialSettings.putInt("Batch",mobType==SpawnerMobType.SKELETON_MINION?3:1);}
            this.mobType = mobType; this.zone = zone; this.startChannels = startChannels; this.stopChannels = stopChannels;
            this.requirePower = requirePower; this.spawnWeight = Math.max(1, spawnWeight);
            if(mobType.special()&&level instanceof ServerLevel server)applyMapBossSettings(me.cryo.zombierool.config.SpecialEnemyConfig.get(server));
            syncWithWorldConfig(false); setChanged();
            if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }

        public void syncWithWorldConfig(boolean isRemoved) {
            if (level instanceof ServerLevel serverLevel) {
                WorldConfig config = WorldConfig.get(serverLevel);
                if (isRemoved || mobType != SpawnerMobType.PLAYER) config.removePlayerSpawnerPosition(worldPosition);
                if (!isRemoved && mobType == SpawnerMobType.PLAYER) config.addPlayerSpawnerPosition(worldPosition);
            }
        }

        @Override public void onLoad() { super.onLoad(); if (level instanceof ServerLevel server) { SpawnerRegistry.registerSpawner(level, this);if(mobType.special()){if(mobType.boss()&&loadedSpecialSettings)me.cryo.zombierool.config.SpecialEnemyConfig.adoptLegacySpawner(server,mobType,specialSettings);applyMapBossSettings(me.cryo.zombierool.config.SpecialEnemyConfig.get(server));} syncWithWorldConfig(false); } }
        @Override public void setRemoved() { super.setRemoved(); if (level != null && !level.isClientSide) { SpawnerRegistry.unregisterSpawner(level, this); syncWithWorldConfig(true); } }

        @Override public void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            tag.putString("MobType", mobType.getSerializedName()); tag.putString("Zone", zone);
            tag.putString("StartChannels", startChannels); tag.putString("StopChannels", stopChannels);
            tag.putBoolean("RequirePower", requirePower); tag.putInt("SpawnWeight", spawnWeight);
            tag.putString("PowerMode",getPowerMode().name());
            tag.put("SpecialSettings",specialSettings.copy());tag.putInt("SpecialSettingsVersion",4);
            tag.putInt("LastBossWave",lastBossWave);tag.putLong("NextMinionTick",nextMinionTick);tag.putLong("SpecialSession",specialSession);
        }

        @Override public void load(CompoundTag tag) {
            super.load(tag);
            try{clientPowerMode=me.cryo.zombierool.gameplay.BlockPowerSettings.Mode.valueOf(tag.getString("PowerMode"));}catch(IllegalArgumentException ignored){clientPowerMode=me.cryo.zombierool.gameplay.BlockPowerSettings.Mode.DEFAULT;}
            if(tag.contains("MobType"))this.mobType = SpawnerMobType.fromString(tag.getString("MobType"));
            if(getBlockState().getBlock() instanceof UniversalSpawnerBlock block && block.family!=0 && !block.allowedTypes().contains(mobType))mobType=block.allowedTypes().get(0); this.zone = tag.getString("Zone");
            this.startChannels = tag.contains("StartChannels") ? tag.getString("StartChannels") : String.valueOf(tag.getInt("StartChannel"));
            this.stopChannels = tag.contains("StopChannels") ? tag.getString("StopChannels") : String.valueOf(tag.getInt("StopChannel"));
            this.requirePower = tag.getBoolean("RequirePower"); this.spawnWeight = tag.contains("SpawnWeight") ? tag.getInt("SpawnWeight") : 1;
            loadedSpecialSettings=true;specialSettings=defaultSpecialConfig();if(mobType==SpawnerMobType.NAPALM_MINION)specialSettings.putInt("FirstWave",5);if(mobType==SpawnerMobType.SKELETON_MINION){specialSettings.putBoolean("UseRadius",true);specialSettings.putInt("Batch",3);specialSettings.putInt("Cooldown",8);specialSettings.putInt("MaxAlive",12);}
            lastBossWave=tag.contains("LastBossWave")?tag.getInt("LastBossWave"):-1;nextMinionTick=tag.getLong("NextMinionTick");specialSession=tag.getLong("SpecialSession");
            if(tag.contains("SpecialSettings",10)){
                var settings=tag.getCompound("SpecialSettings");if(settings.contains("UseRadius",1))specialSettings.putBoolean("UseRadius",settings.getBoolean("UseRadius"));String[] keys={"FirstWave","WaveInterval","Radius","Cooldown","MaxAlive","Batch"};int[] max={10000,10000,128,3600,64,16};
                for(int i=0;i<keys.length;i++)if(settings.contains(keys[i],3))specialSettings.putInt(keys[i],Math.max(1,Math.min(max[i],settings.getInt(keys[i]))));
            }
            if(tag.getInt("SpecialSettingsVersion")<4&&mobType==SpawnerMobType.NAPALM_MINION&&specialSettings.getInt("FirstWave")==8)specialSettings.putInt("FirstWave",5);
            if(!tag.contains("SpecialSettingsVersion")&&mobType==SpawnerMobType.NAPALM_MINION&&specialSettings.getInt("Batch")==3)specialSettings.putInt("Batch",1);
            if(tag.getInt("SpecialSettingsVersion")<2&&mobType==SpawnerMobType.NAPALM_MINION){if(specialSettings.getInt("Cooldown")==8)specialSettings.putInt("Cooldown",30);if(specialSettings.getInt("MaxAlive")==12)specialSettings.putInt("MaxAlive",1);}
            if(level!=null&&!level.isClientSide){SpawnerRegistry.registerSpawner(level,this);syncWithWorldConfig(false);setChanged();}
        }

        @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
        @Override public CompoundTag getUpdateTag() { return this.saveWithoutMetadata(); }
        @Override public Component getDisplayName() { return Component.translatable(getBlockState().getBlock().getDescriptionId()); }
        @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) { return new UniversalSpawnerManagerMenu(id, inv, new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(this.worldPosition)); }
    }

    public static class LegacySpawnerBE extends BlockEntity {
        public int canal = 0;
        public LegacySpawnerBE(BlockPos pos, BlockState state) { super(getLegacyType(state), pos, state); }
        private static BlockEntityType<?> getLegacyType(BlockState state) {
            if (state.is(LEGACY_CRAWLER_BLOCK.get())) return LEGACY_CRAWLER_BE.get();
            if (state.is(LEGACY_DOG_BLOCK.get())) return LEGACY_DOG_BE.get();
            if (state.is(LEGACY_PLAYER_BLOCK.get())) return LEGACY_PLAYER_BE.get();
            return LEGACY_ZOMBIE_BE.get();
        }
        @Override public void load(CompoundTag tag) { super.load(tag); this.canal = tag.getInt("SpawnerCanal"); }
    }

    public static class LegacySpawnerBlock extends Block implements EntityBlock {
        private final SpawnerMobType targetType;
        public LegacySpawnerBlock(SpawnerMobType type) { super(BlockBehaviour.Properties.of().noOcclusion().noCollission()); this.targetType = type; }
        @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new LegacySpawnerBE(pos, state); }
        @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return !level.isClientSide ? (lvl, pos, st, be) -> {
                if (be instanceof LegacySpawnerBE oldBe) {
                    BlockState newState = UNIVERSAL_SPAWNER_BLOCK.get().defaultBlockState().setValue(UniversalSpawnerBlock.MOB_TYPE, targetType);
                    lvl.setBlock(pos, newState, 3);
                    if (lvl.getBlockEntity(pos) instanceof UniversalSpawnerBlockEntity newBe) newBe.setConfig(targetType, "", String.valueOf(oldBe.canal), "0", false, 1);
                }
            } : null;
        }
    }

    public static class UniversalSpawnerManagerMenu extends AbstractContainerMenu {
        public final BlockPos pos;
        public UniversalSpawnerManagerMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
            super(UNIVERSAL_SPAWNER_MENU.get(), id);
            this.pos = extraData != null ? extraData.readBlockPos() : BlockPos.ZERO;
        }
        @Override public boolean stillValid(Player player) { return player.isCreative()&&player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))<=64&&player.level().getBlockEntity(pos) instanceof UniversalSpawnerBlockEntity; }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }

    @OnlyIn(Dist.CLIENT)
    public static class UniversalSpawnerManagerScreen extends UnifiedConfigScreen<UniversalSpawnerManagerMenu> {
        private CycleButton<SpawnerMobType> typeButton;
        private EditBox zoneBox, startChannelBox, stopChannelBox, weightBox;
        private CycleButton<Boolean> powerButton;
        private CycleButton<me.cryo.zombierool.gameplay.BlockPowerSettings.Mode> sourceButton;
        public UniversalSpawnerManagerScreen(UniversalSpawnerManagerMenu menu, Inventory inv, Component title) {
            super(menu, inv, title);
            this.imageHeight=240;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (this.zoneBox.isFocused() || this.startChannelBox.isFocused() || this.stopChannelBox.isFocused() || this.weightBox.isFocused()) {
                if (keyCode == 256) { this.onClose(); return true; }
                if (this.minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        protected void init() {
            super.init();
            BlockEntity be = this.minecraft.level.getBlockEntity(this.menu.pos);
            SpawnerMobType initialType = SpawnerMobType.ZOMBIE;
            String initialZone = "", initialStart = "0", initialStop = "0";
            int initialWeight = 1;
            boolean initialPower = false;

            if (be instanceof UniversalSpawnerBlockEntity ube) {
                initialType = ube.getMobType(); initialZone = ube.getZone(); initialStart = ube.getStartChannels();
                initialStop = ube.getStopChannels(); initialPower = ube.doesRequirePower(); initialWeight = ube.getSpawnWeight();
            }

            int startX = (this.width - this.imageWidth) / 2;
            int startY = (this.height - this.imageHeight) / 2;
            int rightX = startX + 120;
            int y = startY + 30;
            int w = 120;
            int h = 20;
            int space = 26;

            this.typeButton = CycleButton.builder((SpawnerMobType type) -> Component.translatable("mob.zombierool."+type.getSerializedName()))
                    .withValues(initialType.boss()?java.util.List.of(initialType):be!=null && be.getBlockState().getBlock() instanceof UniversalSpawnerBlock block?block.allowedTypes():Arrays.asList(SpawnerMobType.values())).withInitialValue(initialType).create(rightX, y, w, h, Component.empty(), (b, v) -> {});
            if(initialType.boss()){typeButton.active=false;typeButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("config.zombierool.enemies.type.tooltip")));}
            this.addRenderableWidget(typeButton); y += space;

            this.zoneBox = new EditBox(this.font, rightX, y, w, h, Component.empty());
            this.zoneBox.setValue(initialZone); this.zoneBox.setMaxLength(64);
            this.addRenderableWidget(zoneBox); y += space;

            this.startChannelBox = new EditBox(this.font, rightX, y, w, h, Component.empty());
            this.startChannelBox.setValue(initialStart); this.startChannelBox.setMaxLength(256);
            this.addRenderableWidget(startChannelBox); y += space;

            this.stopChannelBox = new EditBox(this.font, rightX, y, w, h, Component.empty());
            this.stopChannelBox.setValue(initialStop); this.stopChannelBox.setMaxLength(256);
            this.addRenderableWidget(stopChannelBox); y += space;

            this.powerButton = CycleButton.onOffBuilder(initialPower).create(rightX, y, w, h, Component.empty(), (b, v) -> {});
            this.addRenderableWidget(powerButton); y += space;

            this.weightBox = new EditBox(this.font, rightX, y, w, h, Component.empty());
            this.weightBox.setValue(String.valueOf(initialWeight));
            this.addRenderableWidget(weightBox);
            var source=be instanceof UniversalSpawnerBlockEntity spawner?spawner.getPowerMode():me.cryo.zombierool.gameplay.BlockPowerSettings.Mode.DEFAULT;
            sourceButton=CycleButton.builder((me.cryo.zombierool.gameplay.BlockPowerSettings.Mode mode)->Component.translatable("gui.zombierool.block_power."+mode.name().toLowerCase(java.util.Locale.ROOT))).withValues(me.cryo.zombierool.gameplay.BlockPowerSettings.Mode.values()).withInitialValue(source).create(rightX,y+space,w,h,Component.empty(),(button,value)->{});
            sourceButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.zombierool.block_power.help")));addRenderableWidget(sourceButton);
            int btnY = startY + this.imageHeight - 28;
            this.addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.spawner.save").withStyle(ChatFormatting.GREEN), btn -> {
                try {
                    int weight = Math.max(1, Integer.parseInt(weightBox.getValue().isEmpty() ? "1" : weightBox.getValue()));
                    CompoundTag special=new CompoundTag();
                    special.putString("PowerMode",sourceButton.getValue().name());
                    NetworkHandler.INSTANCE.sendToServer(new C2SSetUniversalSpawnerConfigPacket(
                            this.menu.pos, typeButton.getValue(), zoneBox.getValue(), startChannelBox.getValue(), stopChannelBox.getValue(), powerButton.getValue(), weight, special
                    ));
                    this.minecraft.setScreen(null);
                } catch (Exception ignored) {}
            }).bounds(startX + 15, btnY, 110, 20).build());

            this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel").withStyle(ChatFormatting.RED), btn -> this.minecraft.setScreen(null))
                    .bounds(startX + 135, btnY, 110, 20).build());
        }

        @Override
        protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
            super.renderLabels(g, mouseX, mouseY);
            int textX = 20;
            int y = 36;
            int space = 26;
            for(String key:List.of("type","zone","start","stop","power","weight")){g.drawString(this.font,Component.translatable("gui.zombierool.spawner."+key),textX,y,0xAAAAAA,false);y+=space;}
            g.drawString(this.font,Component.translatable("gui.zombierool.block_power.source"),textX,y,0xAAAAAA,false);
        }
    }
}
