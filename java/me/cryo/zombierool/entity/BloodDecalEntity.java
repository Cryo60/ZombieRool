package me.cryo.zombierool.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.configuration.ZRClientConfig;
import me.cryo.zombierool.client.ZrBloodRenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PlayMessages;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.joml.Matrix4f;

public class BloodDecalEntity extends Entity {
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(BloodDecalEntity.class, EntityDataSerializers.FLOAT);
    private static final ResourceLocation TEXTURE = new ResourceLocation("zombierool", "textures/entities/gore_blood.png");

    public BloodDecalEntity(PlayMessages.SpawnEntity packet, Level level) {
        this(me.cryo.zombierool.init.ZombieroolModEntities.BLOOD_DECAL.get(), level);
    }

    public BloodDecalEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static void pool(ServerLevel level, double x, double y, double z, int count) {
        for (int i = 0; i < count; i++) {
            BloodDecalEntity decal = new BloodDecalEntity(me.cryo.zombierool.init.ZombieroolModEntities.BLOOD_DECAL.get(), level);
            double ox = (level.random.nextDouble() - 0.5) * 1.35;
            double oz = (level.random.nextDouble() - 0.5) * 1.35;
            decal.setPos(x + ox, groundY(level, x + ox, y, z + oz) + 0.02 + i * 0.0015, z + oz);
            decal.setYRot(level.random.nextFloat() * 360.0F);
            decal.entityData.set(SIZE, 0.55F + level.random.nextFloat() * 1.05F);
            level.addFreshEntity(decal);
        }
    }

    private static double groundY(ServerLevel level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            pos = pos.below();
            state = level.getBlockState(pos);
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return y + 0.02;
        return pos.getY() + shape.max(Direction.Axis.Y);
    }

    public float getSize() { return this.entityData.get(SIZE); }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(SIZE, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount > 1200) this.discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(SIZE, tag.getFloat("Size"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Size", this.getSize());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @OnlyIn(Dist.CLIENT)
    public static class BloodDecalRenderer extends EntityRenderer<BloodDecalEntity> {
        public BloodDecalRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public void render(BloodDecalEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffer, int light) {
            if (me.cryo.zombierool.client.HalloweenManager.isHalloweenPeriod() || ZRClientConfig.isGoreReduced()) return;
            pose.pushPose();
            pose.translate(0.0, 0.008 + (entity.getId() & 5) * 0.0012, 0.0);
            pose.mulPose(Axis.YP.rotationDegrees(entity.getYRot()));
            float size = entity.getSize();
            pose.scale(size, 1.0F, size);
            VertexConsumer vc = buffer.getBuffer(ZrBloodRenderType.DECAL);
            Matrix4f matrix = pose.last().pose();
            int overlay = OverlayTexture.NO_OVERLAY;
            vc.vertex(matrix, -0.5F, 0.0F, -0.5F).color(85, 12, 18, 240).uv(0.0F, 0.0F).overlayCoords(overlay).uv2(light).normal(0.0F, 1.0F, 0.0F).endVertex();
            vc.vertex(matrix, -0.5F, 0.0F, 0.5F).color(85, 12, 18, 240).uv(0.0F, 1.0F).overlayCoords(overlay).uv2(light).normal(0.0F, 1.0F, 0.0F).endVertex();
            vc.vertex(matrix, 0.5F, 0.0F, 0.5F).color(85, 12, 18, 240).uv(1.0F, 1.0F).overlayCoords(overlay).uv2(light).normal(0.0F, 1.0F, 0.0F).endVertex();
            vc.vertex(matrix, 0.5F, 0.0F, -0.5F).color(85, 12, 18, 240).uv(1.0F, 0.0F).overlayCoords(overlay).uv2(light).normal(0.0F, 1.0F, 0.0F).endVertex();
            pose.popPose();
        }

        @Override
        public ResourceLocation getTextureLocation(BloodDecalEntity entity) {
            return TEXTURE;
        }
    }
}
