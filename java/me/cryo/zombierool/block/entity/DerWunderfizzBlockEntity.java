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
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

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
	                DerWunderfizzBlock.updatePerkType(level, pos, perkIdToDisplay);
	            }

	            if (entity.animationTicks >= TOTAL_ANIMATION_DURATION) {
	                entity.finishAnimation(level, pos, blockState);
	            }
	            break;

	        case READY:
	            entity.readyTicks++;
	            if (entity.readyTicks >= READY_DURATION) {
	                entity.resetToIdle(level, pos, blockState);
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
	        resetToIdle(level, pos, blockState);
	        return;
	    }

	    DerWunderfizzBlock.updatePerkType(level, pos, selectedPerkId);
	    
	    level.playSound(null, pos, 
	        net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(
	            new net.minecraft.resources.ResourceLocation("zombierool:wunderfizz_end")
	        ), 
	        SoundSource.BLOCKS, 1.0F, 1.0F);

	    this.state = WunderfizzState.READY;
	    this.currentlyDisplayedPerk = selectedPerkId;
	    readyTicks = 0;
	    
	    setChanged();
	    
	    if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
	        syncToClients(serverLevel);
	    }
	}

	private void syncToClients(net.minecraft.server.level.ServerLevel level) {
	    me.cryo.zombierool.network.SyncWunderfizzStatePacket packet = 
	        new me.cryo.zombierool.network.SyncWunderfizzStatePacket(
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
	        MobEffect effect = PerksManager.getEffectInstance(perkId);
	        if (effect != null && !buyer.hasEffect(effect) && !WorldConfig.get((ServerLevel)level).isRandomPerkDisabled(perkId)) {
	            availablePerks.add(perkId);
	        }
	    }

	    if (availablePerks.isEmpty()) {
	        buyer.displayClientMessage(Component.literal(
	            "§cYou already have all available perks!"
	        ), true);
	        return;
	    }

	    selectedPerkId = availablePerks.get(random.nextInt(availablePerks.size()));
	    
	    state = WunderfizzState.ANIMATING;
	    animationTicks = 0;
	    currentlyDisplayedPerk = "idle";
	    
	    setChanged();
	    
	    if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
	        syncToClients(serverLevel);
	    }
	}

	public boolean collectDrink(Player player) {
	    if (state != WunderfizzState.READY || selectedPerkId == null) {
	        return false;
	    }

	    if (buyerUUID != null && !buyerUUID.equals(player.getUUID())) {
	        player.displayClientMessage(Component.literal(
	            "§cThis drink is not for you!"
	        ), true);
	        return false;
	    }

	    MobEffect effect = PerksManager.getEffectInstance(selectedPerkId);
	    if (effect != null && player.hasEffect(effect)) {
	        player.displayClientMessage(Component.literal(
	            "§cYou already have this perk!"
	        ), true);
	        return false;
	    }

	    return true;
	}

	public void resetAfterCollect() {
	    this.state = WunderfizzState.IDLE;
	    this.animationTicks = 0;
	    this.readyTicks = 0;
	    this.selectedPerkId = null;
	    this.currentlyDisplayedPerk = "idle";
	    this.buyerUUID = null;
	    this.availablePerks.clear();
	    setChanged();
	}

	public String getSelectedPerkId() {
	    return selectedPerkId;
	}

	private void resetToIdle(Level level, BlockPos pos, BlockState blockState) {
	    this.state = WunderfizzState.IDLE;
	    this.animationTicks = 0;
	    this.readyTicks = 0;
	    this.selectedPerkId = null;
	    this.currentlyDisplayedPerk = "idle";
	    this.buyerUUID = null;
	    this.availablePerks.clear();
	    DerWunderfizzBlock.updatePerkType(level, pos, "idle");
	    setChanged();
	}

	public WunderfizzState getState() {
	    return state;
	}
	
	public boolean isReady() {
	    return state == WunderfizzState.READY;
	}

	public void setStateFromPacket(String stateName, String perkId) {
	    try {
	        this.state = WunderfizzState.valueOf(stateName);
	        this.selectedPerkId = perkId;
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
	    currentlyDisplayedPerk = tag.getString("currentlyDisplayedPerk");
	    
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
	public CompoundTag getUpdateTag() {
	    return this.saveWithFullMetadata();
	}

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

	@Override
	public void handleUpdateTag(CompoundTag tag) {
	    load(tag);
	}

	@Override
	public int getContainerSize() {
	    return items.size();
	}

	@Override
	public boolean isEmpty() {
	    for (ItemStack stack : items) {
	        if (!stack.isEmpty()) return false;
	    }
	    return true;
	}

	@Override
	public ItemStack getItem(int slot) {
	    return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
	    ItemStack stack = ContainerHelper.removeItem(items, slot, amount);
	    if (!stack.isEmpty()) setChanged();
	    return stack;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
	    return ContainerHelper.takeItem(items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
	    items.set(slot, stack);
	    if (stack.getCount() > getMaxStackSize()) {
	        stack.setCount(getMaxStackSize());
	    }
	    setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
	    return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
	    items.clear();
	    setChanged();
	}

	@Override
	public Component getDisplayName() {
	    return Component.literal("Der Wunderfizz");
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
	    return null;
	}
}