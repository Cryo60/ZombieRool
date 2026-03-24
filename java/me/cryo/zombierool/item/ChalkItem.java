package me.cryo.zombierool.item;
import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.core.system.OverlaySystem;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CUpdateOverlayPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public class ChalkItem extends Item {
    public ChalkItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.zombierool.chalk.tooltip.1"));
        tooltip.add(Component.translatable("item.zombierool.chalk.tooltip.2"));
        tooltip.add(Component.translatable("item.zombierool.chalk.tooltip.3"));
        tooltip.add(Component.translatable("item.zombierool.chalk.tooltip.4"));
        tooltip.add(Component.translatable("item.zombierool.chalk.tooltip.5"));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Minecraft.getInstance().setScreen(new OverlaySystem.ChalkSelectionScreen(player.getItemInHand(hand), hand));
            });
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            if (level.isClientSide) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    Minecraft.getInstance().setScreen(new OverlaySystem.ChalkSelectionScreen(context.getItemInHand(), context.getHand()));
                });
            }
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        String tex = stack.getOrCreateTag().getString("chalk_texture");
        if (tex.isEmpty()) tex = "zombierool:textures/chalks/chalk_a.png"; 
        int rot = stack.getOrCreateTag().getInt("chalk_rotation");
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        level.playSound(null, pos, SoundEvents.CALCITE_PLACE, SoundSource.BLOCKS, 0.8f, 1.0f);
        String key = pos.getX() + "_" + pos.getY() + "_" + pos.getZ() + "_" + face.getName();
        if (level instanceof ServerLevel serverLevel) {
            WorldConfig config = WorldConfig.get(serverLevel);
            config.addMapOverlay(key, tex + ";" + rot);
        }
        S2CUpdateOverlayPacket packet = new S2CUpdateOverlayPacket(pos, face, tex, rot, true);
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(pos)), packet);
        return InteractionResult.SUCCESS;
    }
}