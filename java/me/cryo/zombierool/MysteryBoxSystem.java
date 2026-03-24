package me.cryo.zombierool.block.system;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import me.cryo.zombierool.MysteryBoxManager;
import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.init.ZombieroolModSounds;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.S2CSyncMysteryBoxStatePacket;
import me.cryo.zombierool.scripting.LuaScriptManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Matrix4f;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class MysteryBoxSystem {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ZombieroolMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ZombieroolMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ZombieroolMod.MODID);

    public static final RegistryObject<Block> MYSTERY_BOX = BLOCKS.register("mystery_box", MysteryBoxBlock::new);
    public static final RegistryObject<Item> MYSTERY_BOX_ITEM = ITEMS.register("mystery_box", () -> new BlockItem(MYSTERY_BOX.get(), new Item.Properties()));
    public static final RegistryObject<Item> TEDDY_BEAR_ITEM = ITEMS.register("teddy_bear", () -> new Item(new Item.Properties()));

    public static final RegistryObject<BlockEntityType<MysteryBoxBlockEntity>> MYSTERY_BOX_BE = BLOCK_ENTITIES.register("mystery_box", () -> BlockEntityType.Builder.of(MysteryBoxBlockEntity::new, MYSTERY_BOX.get()).build(null));

    static {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }

    @SubscribeEvent
    public static void buildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(new ResourceLocation(ZombieroolMod.MODID, "zb_rct"))) {
            event.accept(MYSTERY_BOX_ITEM.get());
            event.accept(TEDDY_BEAR_ITEM.get());
        }
    }

    public static class MysteryBoxBlock extends HorizontalDirectionalBlock implements EntityBlock {
        public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
        public static final BooleanProperty PART = BooleanProperty.create("part");
        public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
        public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

        public MysteryBoxBlock() {
            super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BROWN)
                .ignitedByLava()
                .instrument(NoteBlockInstrument.BASS)
                .sound(SoundType.WOOD)
                .strength(-1, 3600000)
                .isRedstoneConductor((bs, br, bp) -> false)
                .pushReaction(PushReaction.BLOCK)
                .lightLevel(state -> 10)
                .noOcclusion()
            );
            this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, false).setValue(OPEN, false).setValue(ACTIVE, true));
        }

        @Override
        public void appendHoverText(ItemStack itemstack, BlockGetter world, List<Component> list, TooltipFlag flag) {
            super.appendHoverText(itemstack, world, list, flag);
            list.add(Component.translatable("block.zombierool.mystery_box.tooltip.1").withStyle(ChatFormatting.BLUE));
            list.add(Component.translatable("block.zombierool.mystery_box.tooltip.2").withStyle(ChatFormatting.GRAY));
            list.add(Component.translatable("block.zombierool.mystery_box.tooltip.3").withStyle(ChatFormatting.GRAY));
        }

        @Override
        public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) { return 15; }

        @Override
        public VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) { return Shapes.block(); }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) { return Shapes.block(); }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.block(); }

        @Override
        public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.block(); }

        @Override
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }

        @Override
        public RenderShape getRenderShape(BlockState state) {
            if (state.getValue(PART)) {
                return RenderShape.INVISIBLE;
            }
            return RenderShape.MODEL;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, PART, OPEN, ACTIVE);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            Level world = context.getLevel();
            BlockPos mainPos = context.getClickedPos();
            Direction facing = context.getHorizontalDirection().getOpposite();
            BlockPos otherPartPos = MysteryBoxManager.getOtherPartPos(mainPos, facing);

            if (!world.getBlockState(otherPartPos).canBeReplaced() || !world.getBlockState(mainPos).canBeReplaced()) {
                return null;
            }

            if (!world.isClientSide()) {
                world.setBlock(mainPos, this.defaultBlockState().setValue(FACING, facing).setValue(PART, false).setValue(OPEN, false).setValue(ACTIVE, true), 3);
                world.setBlock(otherPartPos, this.defaultBlockState().setValue(FACING, facing).setValue(PART, true).setValue(OPEN, false).setValue(ACTIVE, true), 3);

                if (world instanceof ServerLevel serverWorld) {
                    WorldConfig config = WorldConfig.get(serverWorld);
                    config.addMysteryBoxPosition(mainPos.immutable());
                    config.addMysteryBoxPosition(otherPartPos.immutable());
                }

                return this.defaultBlockState().setValue(FACING, facing).setValue(PART, false).setValue(OPEN, false).setValue(ACTIVE, true);
            }

            return this.defaultBlockState().setValue(FACING, facing).setValue(PART, false).setValue(OPEN, false).setValue(ACTIVE, true);
        }

        @Override
        public void onRemove(BlockState state, Level worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
            if (state.getBlock() != newState.getBlock() && !isMoving) {
                Direction facing = state.getValue(FACING);
                BlockPos otherPartPos;

                if (state.getValue(PART)) {
                    otherPartPos = MysteryBoxManager.getOppositeOtherPartPos(pos, facing);
                } else {
                    otherPartPos = MysteryBoxManager.getOtherPartPos(pos, facing);
                }

                if (!worldIn.isClientSide() && worldIn instanceof ServerLevel serverWorld) {
                    WorldConfig config = WorldConfig.get(serverWorld);
                    config.removeMysteryBoxPosition(pos.immutable());
                    if (worldIn.getBlockState(otherPartPos).is(this)) {
                        config.removeMysteryBoxPosition(otherPartPos.immutable());
                    }
                }

                if (worldIn.getBlockState(otherPartPos).is(this) && !worldIn.isClientSide()) {
                    worldIn.setBlockAndUpdate(otherPartPos, Blocks.AIR.defaultBlockState());
                }
            }
            super.onRemove(state, worldIn, pos, newState, isMoving);
        }

        @Override
        public BlockState rotate(BlockState state, Rotation rot) { return state.setValue(FACING, rot.rotate(state.getValue(FACING))); }

        @Override
        public BlockState mirror(BlockState state, Mirror mirrorIn) { return state.setValue(FACING, mirrorIn.mirror(state.getValue(FACING))); }

        @Override
        public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
            if (state.getValue(PART)) return Collections.emptyList();
            return Collections.singletonList(new ItemStack(this));
        }

        @Nullable
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            if (!state.getValue(PART)) return new MysteryBoxBlockEntity(pos, state);
            return null;
        }

        @Nullable
        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (!state.getValue(PART) && type == MYSTERY_BOX_BE.get()) {
                return level.isClientSide() ? (lvl, pos, st, be) -> MysteryBoxBlockEntity.clientTick(lvl, pos, st, (MysteryBoxBlockEntity)be)
                        : (lvl, pos, st, be) -> MysteryBoxBlockEntity.tick(lvl, pos, st, (MysteryBoxBlockEntity)be);
            }
            return null;
        }
    }

    public static class MysteryBoxBlockEntity extends BlockEntity {
        private int boxState = 0;
        private int timer = 0;
        private ItemStack finalWeapon = ItemStack.EMPTY;
        private boolean isTeddy = false;
        private UUID buyerUUID = null;
        private boolean usedIngot = false;
        private int purchaseCost = 950;

        public MysteryBoxBlockEntity(BlockPos pos, BlockState state) {
            super(MYSTERY_BOX_BE.get(), pos, state);
        }

        @Override
        public net.minecraft.world.phys.AABB getRenderBoundingBox() {
            return new net.minecraft.world.phys.AABB(worldPosition).expandTowards(0, 512, 0).inflate(10);
        }

        @Override
        public void onLoad() {
            super.onLoad();
            if (this.level != null && !this.level.isClientSide && this.level instanceof ServerLevel serverLevel) {
                WorldConfig config = WorldConfig.get(serverLevel);
                config.addMysteryBoxPosition(this.worldPosition);
            }
        }

        public void startCycling(Player player, ItemStack weapon, boolean isTeddy, boolean useIngot, int cost) {
            this.boxState = 1;
            this.timer = 100;
            this.finalWeapon = weapon;
            this.isTeddy = isTeddy;
            this.buyerUUID = player.getUUID();
            this.usedIngot = useIngot;
            this.purchaseCost = cost;
            level.setBlock(worldPosition, getBlockState().setValue(MysteryBoxBlock.OPEN, true), 3);
            BlockPos otherPart = MysteryBoxManager.getOtherPartPos(worldPosition, getBlockState().getValue(MysteryBoxBlock.FACING));
            if (level.getBlockState(otherPart).is(MYSTERY_BOX.get())) {
                level.setBlock(otherPart, level.getBlockState(otherPart).setValue(MysteryBoxBlock.OPEN, true), 3);
            }
            level.playSound(null, worldPosition, ZombieroolModSounds.MYSTERY_BOX_JINGLE.get(), SoundSource.BLOCKS, 1f, 1f);
            sync();
        }

        public void collectWeapon(Player player) {
            if (boxState == 2 && !isTeddy) {
                if (buyerUUID != null && !buyerUUID.equals(player.getUUID())) {
                    return;
                }
                if (!finalWeapon.isEmpty()) {
                    WeaponFacade.grantWeaponToPlayer((net.minecraft.server.level.ServerPlayer) player, finalWeapon);
                    LuaScriptManager.callEvent("OnMysteryBoxUsed", player.getUUID().toString(), WeaponFacade.getWeaponId(finalWeapon));
                }
                resetToIdle(level, worldPosition, getBlockState());
            }
        }

        private void resetToIdle(Level level, BlockPos pos, BlockState state) {
            this.boxState = 0;
            this.timer = 0;
            this.finalWeapon = ItemStack.EMPTY;
            this.isTeddy = false;
            this.buyerUUID = null;
            BlockState currentState = level.getBlockState(pos);
            if (currentState.hasProperty(MysteryBoxBlock.OPEN)) {
                level.setBlock(pos, currentState.setValue(MysteryBoxBlock.OPEN, false), 3);
            }
            BlockPos otherPart = MysteryBoxManager.getOtherPartPos(pos, currentState.getValue(MysteryBoxBlock.FACING));
            BlockState otherState = level.getBlockState(otherPart);
            if (otherState.is(MYSTERY_BOX.get()) && otherState.hasProperty(MysteryBoxBlock.OPEN)) {
                level.setBlock(otherPart, otherState.setValue(MysteryBoxBlock.OPEN, false), 3);
            }
            sync();
        }

        public static void tick(Level level, BlockPos pos, BlockState state, MysteryBoxBlockEntity entity) {
            if (level.isClientSide) return;
            if (!state.getValue(MysteryBoxBlock.ACTIVE)) return;
            if (entity.boxState == 1) {
                entity.timer--;
                if (entity.timer <= 0) {
                    entity.boxState = 2;
                    entity.timer = entity.isTeddy ? 60 : 240;
                    if (entity.isTeddy) {
                        level.playSound(null, pos, ZombieroolModSounds.MYSTERY_BOX_BYBYE.get(), SoundSource.BLOCKS, 1f, 1f);
                    } else if (entity.finalWeapon != null && !entity.finalWeapon.isEmpty()) {
                        String weaponId = me.cryo.zombierool.core.system.WeaponFacade.getWeaponId(entity.finalWeapon);
                        if (weaponId.equals("raygun") || weaponId.equals("zombierool:raygun") || weaponId.equals("raygunmarkii") || weaponId.equals("zombierool:raygunmarkii")) {
                            level.playSound(null, pos, me.cryo.zombierool.init.ZombieroolModSounds.MUS_RAYGUN_STINGER.get(), net.minecraft.sounds.SoundSource.RECORDS, 2.0f, 1.0f);
                        }
                    }
                    entity.sync();
                }
            } else if (entity.boxState == 2) {
                entity.timer--;
                if (entity.timer <= 0) {
                    if (entity.isTeddy) {
                        MysteryBoxManager.get((ServerLevel)level).moveMysteryBox((ServerLevel)level, entity.usedIngot, entity.purchaseCost, entity.buyerUUID);
                        entity.resetToIdle(level, pos, state);
                    } else {
                        entity.resetToIdle(level, pos, state);
                    }
                }
            }
        }

        public static void clientTick(Level level, BlockPos pos, BlockState state, MysteryBoxBlockEntity entity) {
            if (!state.getValue(MysteryBoxBlock.ACTIVE)) return;
            if (entity.boxState > 0) {
                if (entity.timer > 0) {
                    entity.timer--;
                }
            }
        }

        public void syncStateFromClient(int state, int timer, ItemStack finalWeapon, boolean isTeddy) {
            this.boxState = state;
            this.timer = timer;
            this.finalWeapon = finalWeapon;
            this.isTeddy = isTeddy;
        }

        private void sync() {
            if (level instanceof ServerLevel serverLevel) {
                S2CSyncMysteryBoxStatePacket packet = new S2CSyncMysteryBoxStatePacket(worldPosition, boxState, timer, finalWeapon, isTeddy);
                NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> serverLevel.getChunkAt(worldPosition)), packet);
            }
            setChanged();
        }

        public int getBoxState() { return boxState; }
        public int getTimer() { return timer; }
        public ItemStack getFinalWeapon() { return finalWeapon; }
        public boolean isTeddy() { return isTeddy; }

        @Override
        public void load(CompoundTag pTag) {
            super.load(pTag);
            this.boxState = pTag.getInt("BoxState");
            this.timer = pTag.getInt("Timer");
            this.isTeddy = pTag.getBoolean("IsTeddy");
            this.usedIngot = pTag.getBoolean("UsedIngot");
            this.purchaseCost = pTag.getInt("PurchaseCost");
            if (pTag.hasUUID("BuyerUUID")) this.buyerUUID = pTag.getUUID("BuyerUUID");
            if (pTag.contains("FinalWeapon")) this.finalWeapon = ItemStack.of(pTag.getCompound("FinalWeapon"));
        }

        @Override
        protected void saveAdditional(CompoundTag pTag) {
            super.saveAdditional(pTag);
            pTag.putInt("BoxState", this.boxState);
            pTag.putInt("Timer", this.timer);
            pTag.putBoolean("IsTeddy", this.isTeddy);
            pTag.putBoolean("UsedIngot", this.usedIngot);
            pTag.putInt("PurchaseCost", this.purchaseCost);
            if (this.buyerUUID != null) pTag.putUUID("BuyerUUID", this.buyerUUID);
            if (!this.finalWeapon.isEmpty()) pTag.put("FinalWeapon", this.finalWeapon.save(new CompoundTag()));
        }

        @Override
        public ClientboundBlockEntityDataPacket getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }

        @Override
        public CompoundTag getUpdateTag() {
            return saveWithoutMetadata();
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class MysteryBoxRenderer implements BlockEntityRenderer<MysteryBoxBlockEntity> {
        private static final List<ItemStack> CACHED_WEAPONS = new ArrayList<>();
        private static final ResourceLocation BEAM_LOCATION = new ResourceLocation("minecraft", "textures/entity/beacon_beam.png");

        public MysteryBoxRenderer(BlockEntityRendererProvider.Context context) {}

        public static void clearCache() {
            CACHED_WEAPONS.clear();
        }

        private void populateCache(Player player) {
            CACHED_WEAPONS.clear();
            for (WeaponSystem.Definition def : WeaponSystem.Loader.LOADED_DEFINITIONS.values()) {
                ItemStack stack = WeaponFacade.createWeaponStack(def.id, false, player);
                if (stack != null && !stack.isEmpty()) {
                    CACHED_WEAPONS.add(stack);
                }
            }
            boolean preferZr = player != null && player.getPersistentData().getBoolean("zr_prefer_zr_weapons");
            if (!preferZr) {
                for (net.minecraft.resources.ResourceLocation unmappedId : WeaponFacade.getUnmappedTaczGuns()) {
                    if (!me.cryo.zombierool.integration.TacZIntegration.isTaczGunAvailable(unmappedId.toString())) {
                        continue;
                    }
                    ItemStack wep = WeaponFacade.createUnmappedTaczWeaponStack(unmappedId, false);
                    if (wep != null && !wep.isEmpty()) {
                        CACHED_WEAPONS.add(wep);
                    }
                }
            }
            if (CACHED_WEAPONS.isEmpty()) {
                CACHED_WEAPONS.add(new ItemStack(net.minecraft.world.item.Items.WOODEN_SWORD));
            }
        }

        @Override
        public void render(MysteryBoxBlockEntity entity, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
            if (!entity.getBlockState().getValue(MysteryBoxBlock.ACTIVE)) return;
            int state = entity.getBoxState();
            Direction facing = entity.getBlockState().getValue(MysteryBoxBlock.FACING);
            BlockPos pos = entity.getBlockPos();
            BlockPos otherPos = MysteryBoxManager.getOtherPartPos(pos, facing);
            double cx = (otherPos.getX() - pos.getX()) / 2.0 + 0.5;
            double cy = 0.6;
            double cz = (otherPos.getZ() - pos.getZ()) / 2.0 + 0.5;

            if (state == 0) {
                Player player = Minecraft.getInstance().player;
                if (player != null) {
                    Vec3 boxCenter = new Vec3(pos.getX() + cx, pos.getY() + cy, pos.getZ() + cz);
                    Vec3 eyePos = player.getEyePosition(partialTicks);
                    Vec3 lookVec = player.getViewVector(partialTicks);
                    Vec3 toBox = boxCenter.subtract(eyePos).normalize();
                    double dot = lookVec.dot(toBox);
                    float alpha = 1.0f;
                    if (dot > 0.95) {
                        alpha = 0.0f;
                    } else if (dot > 0.85) {
                        alpha = (float) (1.0 - ((dot - 0.85) * 10.0));
                    }
                    if (alpha > 0.05f) {
                        renderBeaconBeam(poseStack, buffer, partialTicks, entity.getLevel().getGameTime(), alpha, cx, cy, cz);
                    }
                }
                return;
            }

            poseStack.pushPose();
            poseStack.translate(cx, cy, cz);
            float yOffset = 0.0f;
            float maxOffset = 0.6f;
            int timer = entity.getTimer();
            ItemStack toRender = ItemStack.EMPTY;

            if (state == 1) {
                float progress = (100 - timer) / 20.0f;
                yOffset = Math.min(maxOffset, progress * maxOffset);
                if (CACHED_WEAPONS.isEmpty()) populateCache(Minecraft.getInstance().player);
                if (!CACHED_WEAPONS.isEmpty()) {
                    int index = (int) ((entity.getLevel().getGameTime() / 4) % CACHED_WEAPONS.size());
                    toRender = CACHED_WEAPONS.get(index);
                }
            } else if (state == 2) {
                if (entity.isTeddy()) {
                    toRender = new ItemStack(TEDDY_BEAR_ITEM.get());
                    if (timer < 20) {
                        yOffset = maxOffset + (20 - timer) / 10.0f;
                    } else {
                        yOffset = maxOffset;
                    }
                } else {
                    toRender = entity.getFinalWeapon();
                    if (timer < 20) {
                        yOffset = maxOffset * (timer / 20.0f);
                    } else {
                        yOffset = maxOffset;
                    }
                }
            }

            poseStack.translate(0, yOffset, 0);
            float rotation = (entity.getLevel().getGameTime() + partialTicks) * 5.0f;
            poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
            poseStack.scale(0.8f, 0.8f, 0.8f);
            Minecraft.getInstance().getItemRenderer().renderStatic(
                toRender, ItemDisplayContext.GROUND, combinedLight, combinedOverlay, poseStack, buffer, entity.getLevel(), 0
            );
            poseStack.popPose();
        }

        private void renderBeaconBeam(PoseStack poseStack, MultiBufferSource buffer, float partialTicks, long gameTime, float alpha, double cx, double cy, double cz) {
            if (buffer instanceof MultiBufferSource.BufferSource bs) {
                bs.endBatch();
            }
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, BEAM_LOCATION);

            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder builder = tesselator.getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

            poseStack.pushPose();
            poseStack.translate(cx, cy - 0.5, cz);
            float time = (float)gameTime + partialTicks;
            float angle = time * 2.0f;
            poseStack.mulPose(Axis.YP.rotationDegrees(angle));

            Matrix4f matrix = poseStack.last().pose();
            float width = 0.15f;
            float height = 512.0f;
            int r = 200, g = 200, b = 255;
            int a = (int)(alpha * 150);

            for (int i = 0; i < 4; i++) {
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
                builder.vertex(matrix, -width, 0, 0).uv(0, 0).color(r, g, b, a).endVertex();
                builder.vertex(matrix, width, 0, 0).uv(1, 0).color(r, g, b, a).endVertex();
                builder.vertex(matrix, width, height, 0).uv(1, height).color(r, g, b, 0).endVertex();
                builder.vertex(matrix, -width, height, 0).uv(0, height).color(r, g, b, 0).endVertex();
            }
            tesselator.end();
            poseStack.popPose();

            RenderSystem.setShaderFogStart(me.cryo.zombierool.client.ClientEnvironmentEffects.fogNearPlane);
            RenderSystem.setShaderFogEnd(me.cryo.zombierool.client.ClientEnvironmentEffects.fogFarPlane);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }

    @OnlyIn(Dist.CLIENT)
    @Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class MysteryBoxPlacementPreview {
        @SubscribeEvent
        public static void onRenderLevelStage(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            Level level = mc.level;

            if (player == null || level == null) return;
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() != MYSTERY_BOX.get().asItem()) return;

            HitResult hitResult = mc.hitResult;
            if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) return;

            BlockHitResult blockHitResult = (BlockHitResult) hitResult;
            BlockPos clickedPos = blockHitResult.getBlockPos();
            Direction clickedFace = blockHitResult.getDirection();
            BlockPos mainPos = clickedPos.relative(clickedFace);

            Direction facing = player.getDirection().getOpposite();
            BlockPos otherPartPos = MysteryBoxManager.getOtherPartPos(mainPos, facing);

            if (!level.getBlockState(mainPos).canBeReplaced() || !level.getBlockState(otherPartPos).canBeReplaced()) return;

            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();
            double camX = mc.gameRenderer.getMainCamera().getPosition().x;
            double camY = mc.gameRenderer.getMainCamera().getPosition().y;
            double camZ = mc.gameRenderer.getMainCamera().getPosition().z;
            poseStack.translate(-camX, -camY, -camZ);

            renderGhostBlock(poseStack, level, mainPos,
                    MYSTERY_BOX.get().defaultBlockState()
                            .setValue(MysteryBoxBlock.FACING, facing)
                            .setValue(MysteryBoxBlock.PART, false)
                            .setValue(MysteryBoxBlock.ACTIVE, true),
                    0.5f);

            renderGhostBlock(poseStack, level, otherPartPos,
                    MYSTERY_BOX.get().defaultBlockState()
                            .setValue(MysteryBoxBlock.FACING, facing)
                            .setValue(MysteryBoxBlock.PART, true)
                            .setValue(MysteryBoxBlock.ACTIVE, true),
                    0.5f);

            poseStack.popPose();
        }

        private static void renderGhostBlock(PoseStack poseStack, Level level, BlockPos pos, BlockState state, float alpha) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            RenderType renderType = RenderType.translucent();
            renderType.setupRenderState();
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferBuilder = tesselator.getBuilder();
            bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            VoxelShape shape = state.getShape(level, pos);
            if (shape.isEmpty()) shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) shape = Shapes.block();

            Matrix4f matrix = poseStack.last().pose();
            int r = 255; int g = 255; int b = 255;
            int a = (int) (alpha * 255);

            shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
                double dx1 = x1 + pos.getX(); double dy1 = y1 + pos.getY(); double dz1 = z1 + pos.getZ();
                double dx2 = x2 + pos.getX(); double dy2 = y2 + pos.getY(); double dz2 = z2 + pos.getZ();

                bufferBuilder.vertex(matrix, (float) dx1, (float) dy2, (float) dz2).color(r, g, b, a).endVertex();
                bufferBuilder.vertex(matrix, (float) dx2, (float) dy2, (float) dz2).color(r, g, b, a).endVertex();
                bufferBuilder.vertex(matrix, (float) dx2, (float) dy2, (float) dz1).color(r, g, b, a).endVertex();
                bufferBuilder.vertex(matrix, (float) dx1, (float) dy2, (float) dz1).color(r, g, b, a).endVertex();

                bufferBuilder.vertex(matrix, (float) dx1, (float) dy1, (float) dz1).color(r, g, b, a).endVertex();
                bufferBuilder.vertex(matrix, (float) dx2, (float) dy1, (float) dz1).color(r, g, b, a).endVertex();
                bufferBuilder.vertex(matrix, (float) dx2, (float) dy1, (float) dz2).color(r, g, b, a).endVertex();
                bufferBuilder.vertex(matrix, (float) dx1, (float) dy1, (float) dz2).color(r, g, b, a).endVertex();

                bufferBuilder.vertex(matrix, (float) dx2, (float) dy2, (float) dz1).color(r, g, b, a).endVertex();
                bufferBuilder.vertex(matrix, (float) dx1, (float) dy2, (float) dz1).color(r, g, b, a).endVertex();
                bufferBuilder.vertex(matrix, (float) dx1, (float) dy1, (float) dz1).color(r, g, b, a).endVertex();
                bufferBuilder.vertex(matrix, (float) dx2, (float) dy1, (float) dz1).color(r, g, b, a).endVertex();

                bufferBuilder.vertex(matrix, (float) dx1, (float) dy2, (float) dz2).color(r, g, b, a).endVertex();
                bufferBuilder.vertex(matrix, (float) dx2, (float) dy2, (float) dz2).color(r, g, b, a).endVertex();
                bufferBuilder.vertex(matrix, (float) dx2, (float) dy1, (float) dz2).color(r, g, b, a).endVertex();
                bufferBuilder.vertex(matrix, (float) dx1, (float) dy1, (float) dz2).color(r, g, b, a).endVertex();

                bufferBuilder.vertex(matrix, (float) dx1, (float) dy2, (float) dz1).color(r, g, b, a).endVertex();
                bufferBuilder.vertex(matrix, (float) dx1, (float) dy2, (float) dz2).color(r, g, b, a).endVertex();
                bufferBuilder.vertex(matrix, (float) dx1, (float) dy1, (float) dz2).color(r, g, b, a).endVertex();
                bufferBuilder.vertex(matrix, (float) dx1, (float) dy1, (float) dz1).color(r, g, b, a).endVertex();

                bufferBuilder.vertex(matrix, (float) dx2, (float) dy2, (float) dz2).color(r, g, b, a).endVertex();
                bufferBuilder.vertex(matrix, (float) dx2, (float) dy2, (float) dz1).color(r, g, b, a).endVertex();
                bufferBuilder.vertex(matrix, (float) dx2, (float) dy1, (float) dz1).color(r, g, b, a).endVertex();
                bufferBuilder.vertex(matrix, (float) dx2, (float) dy1, (float) dz2).color(r, g, b, a).endVertex();
            });

            tesselator.end();
            renderType.clearRenderState();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }
}