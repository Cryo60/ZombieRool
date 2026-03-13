package me.cryo.zombierool.block.system;

import com.mojang.blaze3d.systems.RenderSystem;
import io.netty.buffer.Unpooled;
import me.cryo.zombierool.GlobalSwitchState;
import me.cryo.zombierool.WaveManager;
import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.block.AbstractTechnicalBlock;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.SetUniversalSpawnerConfigPacket;
import me.cryo.zombierool.spawner.SpawnerRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
    public static final RegistryObject<Item> UNIVERSAL_SPAWNER_ITEM = ITEMS.register("universal_spawner", () -> new BlockItem(UNIVERSAL_SPAWNER_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<UniversalSpawnerBlockEntity>> UNIVERSAL_SPAWNER_BE = BLOCK_ENTITIES.register("universal_spawner", () -> BlockEntityType.Builder.of(UniversalSpawnerBlockEntity::new, UNIVERSAL_SPAWNER_BLOCK.get()).build(null));

    public static final RegistryObject<MenuType<UniversalSpawnerManagerMenu>> UNIVERSAL_SPAWNER_MENU = MENUS.register("universal_spawner_manager", () -> IForgeMenuType.create(UniversalSpawnerManagerMenu::new));

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
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
    }

    @SubscribeEvent
    public static void buildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(new ResourceLocation(ZombieroolMod.MODID, "zb_rct"))) {
            for (SpawnerMobType type : SpawnerMobType.values()) {
                ItemStack stack = new ItemStack(UNIVERSAL_SPAWNER_ITEM.get());
                CompoundTag blockStateTag = new CompoundTag();
                blockStateTag.putString("mob_type", type.getSerializedName());
                stack.getOrCreateTag().put("BlockStateTag", blockStateTag);

                CompoundTag beTag = new CompoundTag();
                beTag.putString("MobType", type.getSerializedName());
                BlockItem.setBlockEntityData(stack, UNIVERSAL_SPAWNER_BE.get(), beTag);

                stack.setHoverName(Component.literal("§9Spawner Universel (" + type.getSerializedName().toUpperCase() + ")"));
                event.accept(stack);
            }
        }
    }

    public enum SpawnerMobType implements StringRepresentable {
        ZOMBIE("zombie"), CRAWLER("crawler"), HELLHOUND("hellhound"), PLAYER("player");

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

        public UniversalSpawnerBlock() {
            super(BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.EMPTY).strength(-1, 3600000).noCollission().noOcclusion());
            this.registerDefaultState(this.stateDefinition.any().setValue(MOB_TYPE, SpawnerMobType.ZOMBIE).setValue(ACTIVE, true));
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            return Shapes.empty(); 
        }

        @Override
        public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
            return type == PathComputationType.LAND; 
        }

        @Override
        public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
            return true;
        }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(MOB_TYPE, ACTIVE); }

        @Override
        protected void addTechnicalTooltip(List<Component> tooltip) {
            tooltip.add(Component.literal(getTranslatedMessage("§9Spawner Universel", "§9Universal Spawner")));
            tooltip.add(Component.literal(getTranslatedMessage("§7Définit où les entités apparaîtront.", "§7Defines where entities will spawn.")));
            tooltip.add(Component.literal(getTranslatedMessage("§7Canaux et Zones configurables.", "§7Channels and Zones configurable.")));
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
            if (!level.isClientSide) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof UniversalSpawnerBlockEntity ube) {
                    ube.syncWithWorldConfig(false);
                }
            }
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

        public UniversalSpawnerBlockEntity(BlockPos pos, BlockState state) { super(UNIVERSAL_SPAWNER_BE.get(), pos, state); }

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
        }

        public List<Integer> getParsedChannels(String channelsStr) {
            if (channelsStr == null || channelsStr.isBlank()) return Collections.emptyList();
            return Arrays.stream(channelsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> {
                        try { return Integer.parseInt(s); }
                        catch (NumberFormatException e) { return -1; }
                    })
                    .filter(i -> i >= 0)
                    .collect(Collectors.toList());
        }

        public boolean isActive(Level level) {
            boolean hasPower = !requirePower || GlobalSwitchState.isActivated(level);
            List<Integer> starts = getParsedChannels(startChannels);
            boolean channelOk = starts.isEmpty() || starts.contains(0) || starts.stream().anyMatch(WaveManager.UNLOCKED_CHANNELS::contains);
            boolean zoneOk = !zone.isEmpty() && WaveManager.UNLOCKED_ZONES.contains(zone);
            boolean isStarted = channelOk || zoneOk;

            List<Integer> stops = getParsedChannels(stopChannels);
            boolean isStopped = !stops.isEmpty() && !stops.contains(0) && stops.stream().anyMatch(WaveManager.UNLOCKED_CHANNELS::contains);

            return hasPower && isStarted && !isStopped;
        }

        public SpawnerMobType getMobType() { return mobType; }
        public String getZone() { return zone; }
        public String getStartChannels() { return startChannels; }
        public String getStopChannels() { return stopChannels; }
        public boolean doesRequirePower() { return requirePower; }
        public int getSpawnWeight() { return spawnWeight; }

        public void setConfig(SpawnerMobType mobType, String zone, String startChannels, String stopChannels, boolean requirePower, int spawnWeight) {
            this.mobType = mobType;
            this.zone = zone;
            this.startChannels = startChannels;
            this.stopChannels = stopChannels;
            this.requirePower = requirePower;
            this.spawnWeight = Math.max(1, spawnWeight);
            syncWithWorldConfig(false);
            setChanged();
            if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }

        public void syncWithWorldConfig(boolean isRemoved) {
            if (level instanceof ServerLevel serverLevel) {
                WorldConfig config = WorldConfig.get(serverLevel);
                if (isRemoved || mobType != SpawnerMobType.PLAYER) {
                    config.removePlayerSpawnerPosition(worldPosition);
                }
                if (!isRemoved && mobType == SpawnerMobType.PLAYER) {
                    config.addPlayerSpawnerPosition(worldPosition);
                }
            }
        }

        @Override public void onLoad() { 
            super.onLoad(); 
            if (level != null && !level.isClientSide) {
                SpawnerRegistry.registerSpawner(level, this);
                syncWithWorldConfig(false);
            }
        }

        @Override public void setRemoved() { 
            super.setRemoved(); 
            if (level != null && !level.isClientSide) {
                SpawnerRegistry.unregisterSpawner(level, this);
                syncWithWorldConfig(true);
            }
        }

        @Override public void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            tag.putString("MobType", mobType.getSerializedName());
            tag.putString("Zone", zone);
            tag.putString("StartChannels", startChannels);
            tag.putString("StopChannels", stopChannels);
            tag.putBoolean("RequirePower", requirePower);
            tag.putInt("SpawnWeight", spawnWeight);
        }

        @Override public void load(CompoundTag tag) {
            super.load(tag);
            this.mobType = SpawnerMobType.fromString(tag.getString("MobType"));
            this.zone = tag.getString("Zone");
            this.startChannels = tag.contains("StartChannels") ? tag.getString("StartChannels") : String.valueOf(tag.getInt("StartChannel"));
            this.stopChannels = tag.contains("StopChannels") ? tag.getString("StopChannels") : String.valueOf(tag.getInt("StopChannel"));
            this.requirePower = tag.getBoolean("RequirePower");
            this.spawnWeight = tag.contains("SpawnWeight") ? tag.getInt("SpawnWeight") : 1;
        }

        @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
        @Override public CompoundTag getUpdateTag() { return this.saveWithoutMetadata(); }

        @Override public Component getDisplayName() { return Component.literal("Universal Spawner"); }
        @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
            return new UniversalSpawnerManagerMenu(id, inv, new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(this.worldPosition));
        }
    }

    public static class LegacySpawnerBE extends BlockEntity {
        public int canal = 0;
        public LegacySpawnerBE(BlockPos pos, BlockState state) {
            super(getLegacyType(state), pos, state);
        }
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
        public LegacySpawnerBlock(SpawnerMobType type) {
            super(BlockBehaviour.Properties.of().noOcclusion().noCollission());
            this.targetType = type;
        }

        @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new LegacySpawnerBE(pos, state); }

        @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return !level.isClientSide ? (lvl, pos, st, be) -> {
                if (be instanceof LegacySpawnerBE oldBe) {
                    int canal = oldBe.canal;
                    BlockState newState = UNIVERSAL_SPAWNER_BLOCK.get().defaultBlockState().setValue(UniversalSpawnerBlock.MOB_TYPE, targetType);
                    lvl.setBlock(pos, newState, 3);
                    if (lvl.getBlockEntity(pos) instanceof UniversalSpawnerBlockEntity newBe) {
                        newBe.setConfig(targetType, "", String.valueOf(canal), "0", false, 1);
                    }
                }
            } : null;
        }
    }

    public static class UniversalSpawnerManagerMenu extends AbstractContainerMenu {
        public final BlockPos pos;
        private final ContainerLevelAccess access;

        public UniversalSpawnerManagerMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
            super(UNIVERSAL_SPAWNER_MENU.get(), id);
            this.pos = extraData != null ? extraData.readBlockPos() : BlockPos.ZERO;
            this.access = ContainerLevelAccess.create(inv.player.level(), this.pos);
        }

        @Override public boolean stillValid(Player player) { return stillValid(this.access, player, player.level().getBlockState(pos).getBlock()); }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }

    @OnlyIn(Dist.CLIENT)
    public static class UniversalSpawnerManagerScreen extends AbstractContainerScreen<UniversalSpawnerManagerMenu> {
        private CycleButton<SpawnerMobType> typeButton;
        private EditBox zoneBox, startChannelBox, stopChannelBox, weightBox;
        private CycleButton<Boolean> powerButton;

        public UniversalSpawnerManagerScreen(UniversalSpawnerManagerMenu menu, Inventory inv, Component title) {
            super(menu, inv, title);
            this.imageWidth = 260;
            this.imageHeight = 210;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (this.zoneBox.isFocused() || this.startChannelBox.isFocused() || this.stopChannelBox.isFocused() || this.weightBox.isFocused()) {
                if (keyCode == 256) { 
                    this.onClose();
                    return true;
                }
                if (this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
                    return true; 
                }
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
                initialType = ube.getMobType();
                initialZone = ube.getZone();
                initialStart = ube.getStartChannels();
                initialStop = ube.getStopChannels();
                initialPower = ube.doesRequirePower();
                initialWeight = ube.getSpawnWeight();
            }

            int startX = (this.width - this.imageWidth) / 2;
            int startY = (this.height - this.imageHeight) / 2;
            int rightX = startX + 120;
            int y = startY + 30;
            int w = 120;
            int h = 20;
            int space = 26;

            this.typeButton = CycleButton.builder((SpawnerMobType type) -> Component.literal(type.name().toUpperCase()))
                    .withValues(Arrays.asList(SpawnerMobType.values()))
                    .withInitialValue(initialType)
                    .create(rightX, y, w, h, Component.empty(), (b, v) -> {});
            this.addRenderableWidget(typeButton);

            y += space; 
            this.zoneBox = new EditBox(this.font, rightX, y, w, h, Component.empty()); 
            this.zoneBox.setValue(initialZone); 
            this.zoneBox.setMaxLength(64);
            this.zoneBox.setFilter(s -> s.matches("[a-zA-Z_]*"));
            this.addRenderableWidget(zoneBox);

            y += space; 
            this.startChannelBox = new EditBox(this.font, rightX, y, w, h, Component.empty()); 
            this.startChannelBox.setValue(initialStart); 
            this.startChannelBox.setMaxLength(256);
            this.startChannelBox.setFilter(s -> s.matches("[0-9, ]*"));
            this.addRenderableWidget(startChannelBox);

            y += space; 
            this.stopChannelBox = new EditBox(this.font, rightX, y, w, h, Component.empty()); 
            this.stopChannelBox.setValue(initialStop); 
            this.stopChannelBox.setMaxLength(256);
            this.stopChannelBox.setFilter(s -> s.matches("[0-9, ]*"));
            this.addRenderableWidget(stopChannelBox);

            y += space; 
            this.powerButton = CycleButton.onOffBuilder(initialPower).create(rightX, y, w, h, Component.empty(), (b, v) -> {}); 
            this.addRenderableWidget(powerButton);

            y += space; 
            this.weightBox = new EditBox(this.font, rightX, y, w, h, Component.empty()); 
            this.weightBox.setValue(String.valueOf(initialWeight)); 
            this.weightBox.setFilter(s -> s.matches("[0-9]*"));
            this.addRenderableWidget(weightBox);

            int btnY = startY + this.imageHeight - 28;
            this.addRenderableWidget(Button.builder(Component.literal("§aSave Settings"), btn -> {
                try {
                    int weight = Math.max(1, Integer.parseInt(weightBox.getValue().isEmpty() ? "1" : weightBox.getValue()));
                    String cleanStart = formatChannels(startChannelBox.getValue());
                    String cleanStop = formatChannels(stopChannelBox.getValue());
                    
                    NetworkHandler.INSTANCE.sendToServer(new SetUniversalSpawnerConfigPacket(
                            this.menu.pos, typeButton.getValue(), zoneBox.getValue(), cleanStart, cleanStop, powerButton.getValue(), weight
                    ));
                    this.minecraft.setScreen(null);
                } catch (Exception ignored) {}
            }).bounds(startX + 15, btnY, 110, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("§cCancel"), btn -> {
                this.minecraft.setScreen(null);
            }).bounds(startX + 135, btnY, 110, 20).build());
        }

        private String formatChannels(String raw) {
            if (raw == null || raw.isBlank()) return "0";
            return Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> {
                        try { return String.valueOf(Integer.parseInt(s)); } 
                        catch (Exception e) { return null; }
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.joining(", "));
        }

        @Override 
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
            this.renderBackground(guiGraphics); 
            int startX = (this.width - this.imageWidth) / 2;
            int startY = (this.height - this.imageHeight) / 2;

            guiGraphics.fillGradient(startX, startY, startX + this.imageWidth, startY + this.imageHeight, 0xEE000000, 0xEE222222);
            guiGraphics.renderOutline(startX, startY, this.imageWidth, this.imageHeight, 0xFFAA00);
            
            guiGraphics.drawCenteredString(this.font, "§l" + this.title.getString(), startX + this.imageWidth / 2, startY + 10, 0xFFAA00);

            int textX = startX + 20;
            int y = startY + 36;
            int space = 26;

            guiGraphics.drawString(this.font, "Mob Type:", textX, y, 0xAAAAAA, false);
            guiGraphics.drawString(this.font, "Zone (Port):", textX, y += space, 0xAAAAAA, false);
            guiGraphics.drawString(this.font, "Start Channels:", textX, y += space, 0xAAAAAA, false);
            guiGraphics.drawString(this.font, "Stop Channels:", textX, y += space, 0xAAAAAA, false);
            guiGraphics.drawString(this.font, "Require Power:", textX, y += space, 0xAAAAAA, false);
            guiGraphics.drawString(this.font, "Spawn Weight:", textX, y += space, 0xAAAAAA, false);

            super.render(guiGraphics, mouseX, mouseY, partialTicks);
            this.renderTooltip(guiGraphics, mouseX, mouseY);
        }

        @Override 
        protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) { }

        @Override
        protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) { }
    }

    @Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientSetup {
        @SubscribeEvent
        public static void clientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                MenuScreens.register(UNIVERSAL_SPAWNER_MENU.get(), UniversalSpawnerManagerScreen::new);
                ItemBlockRenderTypes.setRenderLayer(UNIVERSAL_SPAWNER_BLOCK.get(), RenderType.translucent());
                ItemBlockRenderTypes.setRenderLayer(LEGACY_ZOMBIE_BLOCK.get(), RenderType.translucent());
                ItemBlockRenderTypes.setRenderLayer(LEGACY_CRAWLER_BLOCK.get(), RenderType.translucent());
                ItemBlockRenderTypes.setRenderLayer(LEGACY_DOG_BLOCK.get(), RenderType.translucent());
                ItemBlockRenderTypes.setRenderLayer(LEGACY_PLAYER_BLOCK.get(), RenderType.translucent());
            });
        }
    }
}