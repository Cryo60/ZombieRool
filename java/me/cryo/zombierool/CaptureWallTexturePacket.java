package me.cryo.zombierool.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import me.cryo.zombierool.block.system.MimicSystem;

import java.util.function.Supplier;

public class CaptureWallTexturePacket {
    private final BlockPos wallPos;
    private final ResourceLocation blockRL;
    private final Direction clickedFace;
    private final Vec3 hitVec;
    private final boolean inside;

    public CaptureWallTexturePacket(BlockPos wallPos, ResourceLocation blockRL, Direction clickedFace, Vec3 hitVec, boolean inside) {
        this.wallPos = wallPos;
        this.blockRL = blockRL;
        this.clickedFace = clickedFace;
        this.hitVec = hitVec;
        this.inside = inside;
    }

    public static void encode(CaptureWallTexturePacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.wallPos);
        buf.writeResourceLocation(pkt.blockRL);
        buf.writeEnum(pkt.clickedFace);
        buf.writeDouble(pkt.hitVec.x);
        buf.writeDouble(pkt.hitVec.y);
        buf.writeDouble(pkt.hitVec.z);
        buf.writeBoolean(pkt.inside);
    }

    public static CaptureWallTexturePacket decode(FriendlyByteBuf buf) {
        return new CaptureWallTexturePacket(
            buf.readBlockPos(),
            buf.readResourceLocation(),
            buf.readEnum(Direction.class),
            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
            buf.readBoolean()
        );
    }

    public static void handle(CaptureWallTexturePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            Level lvl = sender.getCommandSenderWorld();
            if (!(lvl instanceof ServerLevel server)) return;

            BlockEntity be = server.getBlockEntity(pkt.wallPos);
            if (!(be instanceof MimicSystem.IMimicContainer mimicContainer)) return;

            Block block = ForgeRegistries.BLOCKS.getValue(pkt.blockRL);
            if (block != null) {
                ItemStack stack = new ItemStack(block);
                BlockHitResult hitResult = new BlockHitResult(pkt.hitVec, pkt.clickedFace, pkt.wallPos, pkt.inside);
                BlockPlaceContext placeContext = new BlockPlaceContext(sender, InteractionHand.MAIN_HAND, stack, hitResult);
                
                BlockState placementState = block.getStateForPlacement(placeContext);
                if (placementState == null) placementState = block.defaultBlockState();

                mimicContainer.setMimic(placementState);
                if (be != null) {
                    be.setChanged();
                    server.sendBlockUpdated(
                        pkt.wallPos,
                        be.getBlockState(),
                        be.getBlockState(),
                        3
                    );
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}