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
    private static final EntityDataAccessor<Integer> VARIANT=SynchedEntityData.defineId(BloodDecalEntity.class,EntityDataSerializers.INT);
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
            if(!me.cryo.zombierool.gameplay.RemainsCleanup.allowBlood(level))break;
            BloodDecalEntity decal = new BloodDecalEntity(me.cryo.zombierool.init.ZombieroolModEntities.BLOOD_DECAL.get(), level);
            double ox = (level.random.nextDouble() - 0.5) * 1.35;
            double oz = (level.random.nextDouble() - 0.5) * 1.35;
            double ground=groundY(level,x+ox,y,z+oz);
            if(!Double.isFinite(ground))continue;
            decal.setPos(x+ox,ground,z+oz);
            decal.setYRot(level.random.nextFloat() * 360.0F);
            decal.entityData.set(SIZE,me.cryo.zombierool.util.BloodAppearance.size(level.random));
            decal.entityData.set(VARIANT,level.random.nextInt());
            level.addFreshEntity(decal);
        }
    }

    private static double groundY(ServerLevel level, double x, double y, double z) {
        var hit=me.cryo.zombierool.util.GoreCollision.clip(level,new net.minecraft.world.phys.Vec3(x,y+.05,z),new net.minecraft.world.phys.Vec3(x,y-2,z));
        return hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK?hit.getLocation().y:Double.NaN;
    }

    public float getSize() { return this.entityData.get(SIZE); }
    public int getVariant(){return entityData.get(VARIANT);}

    @Override
    protected void defineSynchedData() {
        this.entityData.define(SIZE, 1.0F);
        this.entityData.define(VARIANT,0);
    }

    @Override
    public void tick() {
        super.tick();

    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(SIZE, tag.getFloat("Size"));
        this.entityData.set(VARIANT,tag.getInt("Variant"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Size", this.getSize());
        tag.putInt("Variant",getVariant());
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
            // Collected and merged after block entities by BloodSurfaceRenderer.

        }

        @Override
        public ResourceLocation getTextureLocation(BloodDecalEntity entity) {
            return ZrBloodRenderType.texture(entity.getVariant());
        }
    }
}
