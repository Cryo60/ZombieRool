package me.cryo.zombierool.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.MenuProvider;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.ChatFormatting;
import me.cryo.zombierool.PerksManager;
import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.block.DerWunderfizzBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DerWunderfizzBlockEntity extends BlockEntity implements Container, MenuProvider {
    
    private NonNullList<ItemStack> items = NonNullList.withSize(0, ItemStack.EMPTY);
    
    public enum WunderfizzState {
        IDLE,
        ANIMATING,
        READY
    }

    private WunderfizzState state = WunderfizzState.IDLE;
    private int animationTicks = 0;
    private int readyTicks = 0;
    private String selectedPerkId = null;
    private String currentlyDisplayedPerk = "idle";
    private java.util.UUID buyerUUID = null;
    private String lastGivenPerkId = null; 

    private static final int FAST_PHASE_DURATION = 40;
    private static final int MEDIUM_PHASE_DURATION = 60;
    private static final int SLOW_PHASE_DURATION = 40;
    private static final int TOTAL_ANIMATION_DURATION = FAST_PHASE_DURATION + MEDIUM_PHASE_DURATION + SLOW_PHASE_DURATION;
    private static final int READY_DURATION = 200; 

    private Random random = new Random();
    private List<String> availablePerks = new ArrayList<>();

    public DerWunderfizzBlockEntity(BlockPos pos, BlockState blockState) {
        super(me.cryo.zombierool.init.ZombieroolModBlockEntities.DER_WUNDERFIZZ.get(), pos, blockState);
    }

    public DerWunderfizzBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide && this.level instanceof ServerLevel serverLevel) {
            WorldConfig config = WorldConfig.get(serverLevel);
            config.addWunderfizzPosition(this.worldPosition);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState blockState, DerWunderfizzBlockEntity entity) {
        BlockPos activePos = me.cryo.zombierool.handlers.KeyInputHandler.getActiveWunderfizzPosition();
        if (activePos != null && activePos.equals(pos)) {
            if (level.random.nextInt(4) == 0) {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, 
                    pos.getX() + 0.5 + (level.random.nextDouble() - 0.5), 
                    pos.getY() + 1.2 + (level.random.nextDouble() - 0.5), 
                    pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5), 
                    0, 0, 0);
            }
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState blockState, DerWunderfizzBlockEntity entity) {
        if (level.isClientSide) return;
        
        switch (entity.state) {
            case IDLE:
                break;
            case ANIMATING:
                entity.animationTicks++;
                
                if (entity.animationTicks % 30 == 0) {
                    level.playSound(null, pos, 
                        net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(
                            new net.minecraft.resources.ResourceLocation("zombierool:wunderfizz_loop")
                        ), 
                        SoundSource.BLOCKS, 1.0F, 1.0F);
                }

                String perkIdToDisplay = entity.calculateDisplayedPerk();
                if (perkIdToDisplay != null && !perkIdToDisplay.equals(entity.currentlyDisplayedPerk)) {
                    entity.currentlyDisplayedPerk = perkIdToDisplay;
                    entity.setChanged();
                    
                    if (blockState.hasProperty(DerWunderfizzBlock.PERK_TYPE)) {
                        BlockState newState = blockState.setValue(DerWunderfizzBlock.PERK_TYPE, DerWunderfizzBlock.WunderfizzPerkType.fromString(perkIdToDisplay));
                        level.setBlock(pos, newState, 3);
                    } else {
                        level.sendBlockUpdated(pos, blockState, blockState, 3);
                    }
                }
                
                if (entity.animationTicks >= TOTAL_ANIMATION_DURATION) {
                    entity.finishAnimation(level, pos, blockState);
                }
                break;
            case READY:
                entity.readyTicks++;
                if (entity.readyTicks >= READY_DURATION) {
                    entity.resetToIdleState();
                }
                break;
        }
    }

    private String calculateDisplayedPerk() {
        if (availablePerks.isEmpty()) {
            return "idle";
        }
        
        int perkCount = availablePerks.size();
        int localIndex = 0;
        
        if (animationTicks < FAST_PHASE_DURATION) {
            int changeInterval = 2;
            localIndex = (animationTicks / changeInterval) % perkCount;
        } else if (animationTicks < FAST_PHASE_DURATION + MEDIUM_PHASE_DURATION) {
            int changeInterval = 5;
            int adjustedTicks = animationTicks - FAST_PHASE_DURATION;
            localIndex = (adjustedTicks / changeInterval) % perkCount;
        } else {
            int changeInterval = 10;
            int adjustedTicks = animationTicks - FAST_PHASE_DURATION - MEDIUM_PHASE_DURATION;
            localIndex = (adjustedTicks / changeInterval) % perkCount;
        }
        
        if (animationTicks >= TOTAL_ANIMATION_DURATION - 10 && selectedPerkId != null) {
            return selectedPerkId;
        }
        
        return availablePerks.get(localIndex);
    }

    private void finishAnimation(Level level, BlockPos pos, BlockState blockState) {
        if (availablePerks.isEmpty() || selectedPerkId == null) {
            resetToIdleState();
            return;
        }
        
        level.playSound(null, pos, 
            net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(
                new net.minecraft.resources.ResourceLocation("zombierool:wunderfizz_end")
            ), 
            SoundSource.BLOCKS, 1.0F, 1.0F);

        this.state = WunderfizzState.READY;
        this.currentlyDisplayedPerk = selectedPerkId;
        readyTicks = 0;
        
        if (blockState.hasProperty(DerWunderfizzBlock.PERK_TYPE)) {
            BlockState newState = blockState.setValue(DerWunderfizzBlock.PERK_TYPE, DerWunderfizzBlock.WunderfizzPerkType.fromString(selectedPerkId));
            level.setBlock(pos, newState, 3);
        } else {
            level.sendBlockUpdated(pos, blockState, blockState, 3);
        }
        
        setChanged();
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            syncToClients(serverLevel);
        }
    }
    
    private void syncToClients(net.minecraft.server.level.ServerLevel level) {
        me.cryo.zombierool.network.S2CSyncWunderfizzStatePacket packet = 
            new me.cryo.zombierool.network.S2CSyncWunderfizzStatePacket(
                worldPosition, 
                state.name(), 
                selectedPerkId
            );
        me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(
            net.minecraftforge.network.PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(worldPosition)),
            packet
        );
    }

    public void startAnimation(Player buyer) {
        if (state != WunderfizzState.IDLE) return;
        
        this.buyerUUID = buyer.getUUID();
        availablePerks.clear();
        
        for (String perkId : PerksManager.ALL_PERKS.keySet()) {
            if (WorldConfig.get((ServerLevel)level).isRandomPerkDisabled(perkId)) continue;
            boolean hasPerk = PerksManager.hasPerk(buyer, perkId);
            boolean hitLimit = PerksManager.isPerkLimited(perkId, buyer) && PerksManager.getCurrentPerkPurchases(perkId, buyer) >= PerksManager.getPerkLimit(perkId, buyer);
            if (!hasPerk && !hitLimit) {
                availablePerks.add(perkId);
            }
        }
        
        if (availablePerks.isEmpty()) {
            buyer.displayClientMessage(Component.translatable("message.zombierool.wunderfizz.all_perks_owned").withStyle(ChatFormatting.RED), true);
            return;
        }
        
        if (availablePerks.size() > 1 && this.lastGivenPerkId != null) {
            availablePerks.remove(this.lastGivenPerkId);
        }

        selectedPerkId = availablePerks.get(random.nextInt(availablePerks.size()));
        this.lastGivenPerkId = selectedPerkId;
        state = WunderfizzState.ANIMATING;
        animationTicks = 0;
        currentlyDisplayedPerk = "idle";
        
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            syncToClients(serverLevel);
        }
    }

    public boolean collectDrink(Player player) {
        if (state != WunderfizzState.READY || selectedPerkId == null) {
            return false;
        }
        if (buyerUUID != null && !buyerUUID.equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.zombierool.wunderfizz.not_your_drink").withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true; 
    }

    public void resetAfterCollect() {
        resetToIdleState();
    }

    public void resetToIdleState() {
        this.state = WunderfizzState.IDLE;
        this.animationTicks = 0;
        this.readyTicks = 0;
        this.selectedPerkId = null;
        this.currentlyDisplayedPerk = "idle";
        this.buyerUUID = null;
        this.availablePerks.clear();
        
        setChanged();
        if (level != null) {
            BlockState currentState = level.getBlockState(worldPosition);
            if (currentState.hasProperty(DerWunderfizzBlock.PERK_TYPE)) {
                level.setBlock(worldPosition, currentState.setValue(DerWunderfizzBlock.PERK_TYPE, DerWunderfizzBlock.WunderfizzPerkType.IDLE), 3);
            } else {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    public String getSelectedPerkId() { return selectedPerkId; }
    public String getCurrentlyDisplayedPerk() { return currentlyDisplayedPerk; }
    public WunderfizzState getState() { return state; }
    public boolean isReady() { return state == WunderfizzState.READY; }

    public void setStateFromPacket(String stateName, String perkId) {
        try {
            this.state = WunderfizzState.valueOf(stateName);
            this.selectedPerkId = perkId;
            if (state == WunderfizzState.READY) {
                this.currentlyDisplayedPerk = perkId;
            }
        } catch (IllegalArgumentException e) {
            this.state = WunderfizzState.IDLE;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("state", state.name());
        tag.putInt("animationTicks", animationTicks);
        tag.putInt("readyTicks", readyTicks);
        tag.putString("currentlyDisplayedPerk", currentlyDisplayedPerk);
        if (selectedPerkId != null) {
            tag.putString("selectedPerkId", selectedPerkId);
        }
        if (buyerUUID != null) {
            tag.putUUID("buyerUUID", buyerUUID);
        }
        tag.putInt("availablePerksCount", availablePerks.size());
        for (int i = 0; i < availablePerks.size(); i++) {
            tag.putString("availablePerk_" + i, availablePerks.get(i));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("state")) {
            state = WunderfizzState.valueOf(tag.getString("state"));
        }
        animationTicks = tag.getInt("animationTicks");
        readyTicks = tag.getInt("readyTicks");
        if (tag.contains("currentlyDisplayedPerk")) {
            currentlyDisplayedPerk = tag.getString("currentlyDisplayedPerk");
        }
        if (tag.contains("selectedPerkId")) {
            selectedPerkId = tag.getString("selectedPerkId");
        }
        if (tag.contains("buyerUUID")) {
            buyerUUID = tag.getUUID("buyerUUID");
        }
        availablePerks.clear();
        int count = tag.getInt("availablePerksCount");
        for (int i = 0; i < count; i++) {
            if (tag.contains("availablePerk_" + i)) {
                availablePerks.add(tag.getString("availablePerk_" + i));
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag() { return this.saveWithFullMetadata(); }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void handleUpdateTag(CompoundTag tag) { load(tag); }

    @Override
    public int getContainerSize() { return items.size(); }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = ContainerHelper.removeItem(items, slot, amount);
        if (!stack.isEmpty()) setChanged();
        return stack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    public Component getDisplayName() { return Component.literal("Der Wunderfizz"); }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return null; }
}