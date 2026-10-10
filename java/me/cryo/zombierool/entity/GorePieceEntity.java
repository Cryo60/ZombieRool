package me.cryo.zombierool.entity;

import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.client.model.ZombieLimbModel;
import me.cryo.zombierool.client.renderer.ZombieRenderer;
import me.cryo.zombierool.configuration.ZRClientConfig;
import me.cryo.zombierool.core.manager.DynamicResourceManager;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PlayMessages;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

public class GorePieceEntity extends Entity {
    public static final byte RIGHT_UPPER = 0;
    public static final byte RIGHT_FORE = 1;
    public static final byte RIGHT_HAND = 2;
    public static final byte LEFT_UPPER = 3;
    public static final byte LEFT_FORE = 4;
    public static final byte LEFT_HAND = 5;
    public static final byte TORSO = 6;
    public static final byte SKULL = 7;
    public static final byte JAW = 8;
    public static final byte MEAT = 9;

    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(GorePieceEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(GorePieceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> CUSTOM_SKIN = SynchedEntityData.defineId(GorePieceEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> STUCK = SynchedEntityData.defineId(GorePieceEntity.class, EntityDataSerializers.BOOLEAN);

    private static final ResourceLocation FLESH = new ResourceLocation("zombierool", "textures/entities/gore_flesh.png");

    public GorePieceEntity(PlayMessages.SpawnEntity packet, Level level) {
        this(me.cryo.zombierool.init.ZombieroolModEntities.GORE_PIECE.get(), level);
    }

    public GorePieceEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    public static void spawn(ServerLevel level, LivingEntity from, byte kind, int skin, String customSkin) {
        GorePieceEntity piece = new GorePieceEntity(me.cryo.zombierool.init.ZombieroolModEntities.GORE_PIECE.get(), level);
        piece.setPos(from.getX(), from.getY() + from.getBbHeight() * 0.62, from.getZ());
        piece.entityData.set(KIND, kind);
        piece.entityData.set(SKIN, skin);
        piece.entityData.set(CUSTOM_SKIN, customSkin == null ? "" : customSkin);
        double yaw = level.random.nextDouble() * Math.PI * 2.0;
        // Start outside the source hitbox so corpse collision cannot trap the piece.
        double radius = from.getBbWidth() * 0.5 + piece.getBbWidth() + 0.05;
        piece.setPos(from.getX() + Math.cos(yaw) * radius, piece.getY(), from.getZ() + Math.sin(yaw) * radius);
        double speed = 0.35 + level.random.nextDouble() * 0.45;
        piece.setDeltaMovement(Math.cos(yaw) * speed, 0.35 + level.random.nextDouble() * 0.55, Math.sin(yaw) * speed);
        piece.setYRot(level.random.nextFloat() * 360.0F);
        level.addFreshEntity(piece);
    }

    public byte getKind() { return this.entityData.get(KIND); }
    public int getSkinIndex() { return this.entityData.get(SKIN); }
    public String getCustomSkin() { return this.entityData.get(CUSTOM_SKIN); }
    public boolean isStuck() { return this.entityData.get(STUCK); }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(KIND, MEAT);
        this.entityData.define(SKIN, 0);
        this.entityData.define(CUSTOM_SKIN, "");
        this.entityData.define(STUCK, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isStuck()) {
            this.setDeltaMovement(Vec3.ZERO);
            if (!this.level().isClientSide && this.tickCount > 900) this.discard();
            return;
        }
        this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.05, 0.0));
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
        if (!this.level().isClientSide && this.onGround()) {
            this.entityData.set(STUCK, true);
            this.setDeltaMovement(Vec3.ZERO);
        } else if (!this.level().isClientSide && (this.tickCount > 80 || this.getY() < this.level().getMinBuildHeight() - 8)) {
            this.discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(KIND, tag.getByte("Kind"));
        this.entityData.set(SKIN, tag.getInt("Skin"));
        this.entityData.set(CUSTOM_SKIN, tag.getString("CustomSkin"));
        this.entityData.set(STUCK, tag.getBoolean("Stuck"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putByte("Kind", this.getKind());
        tag.putInt("Skin", this.getSkinIndex());
        tag.putString("CustomSkin", this.getCustomSkin());
        tag.putBoolean("Stuck", this.isStuck());
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
    public static class GorePieceRenderer extends EntityRenderer<GorePieceEntity> {
        private final ModelPart root;

        public GorePieceRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.root = context.bakeLayer(ZombieLimbModel.GIB_LAYER);
        }

        @Override
        public void render(GorePieceEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffer, int light) {
            if (ZRClientConfig.isGoreReduced()) return;
            if(me.cryo.zombierool.client.HalloweenManager.isHalloweenPeriod()){
                pose.pushPose();pose.translate(0,.10,0);pose.mulPose(Axis.YP.rotationDegrees((entity.tickCount+partial)*17));
                me.cryo.zombierool.client.CandyModel.render(pose,buffer,entity.getId());pose.popPose();return;
            }
            ModelPart part = this.root.getChild(partName(entity.getKind()));
            pose.pushPose();
            float spin = (entity.tickCount + partial) * (entity.isStuck() ? 0.0F : 17.0F);
            pose.translate(0.0, entity.isStuck() ? 0.04 : 0.12, 0.0);
            if (!entity.isStuck()) {
                pose.mulPose(Axis.YP.rotationDegrees(spin * 1.3F));
                pose.mulPose(Axis.XP.rotationDegrees(spin));
            } else {
                pose.mulPose(Axis.YP.rotationDegrees(entity.getYRot()));
                pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            }
            pose.scale(-0.75F, -0.75F, 0.75F);
            part.render(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity))), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }

        @Override
        public ResourceLocation getTextureLocation(GorePieceEntity entity) {
            if (entity.getKind() == MEAT) return FLESH;
            String custom = entity.getCustomSkin();
            if (custom != null && !custom.isEmpty()) {
                ResourceLocation dyn = DynamicResourceManager.getClientSkin("zombie", custom);
                if (dyn != null) return dyn;
            }
            return ZombieRenderer.skin(entity.getSkinIndex());
        }

        private static String partName(byte kind) {
            return switch (kind) {
                case RIGHT_UPPER -> "right_upper";
                case RIGHT_FORE -> "right_fore";
                case RIGHT_HAND -> "right_hand";
                case LEFT_UPPER -> "left_upper";
                case LEFT_FORE -> "left_fore";
                case LEFT_HAND -> "left_hand";
                case TORSO -> "torso";
                case SKULL -> "skull";
                case JAW -> "jaw";
                default -> "meat";
            };
        }
    }
}
