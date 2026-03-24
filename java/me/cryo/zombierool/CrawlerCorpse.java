package me.cryo.zombierool.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.client.model.ModelCrawler;
import me.cryo.zombierool.core.manager.DynamicResourceManager;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PlayMessages;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

public class CrawlerCorpse extends Mob {

    public static EntityType<CrawlerCorpse> TYPE;

    private static final EntityDataAccessor<String> CUSTOM_SKIN = SynchedEntityData.defineId(CrawlerCorpse.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> HALLOWEEN_SKIN = SynchedEntityData.defineId(CrawlerCorpse.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> ZR_SCALE = SynchedEntityData.defineId(CrawlerCorpse.class, EntityDataSerializers.FLOAT);

    private int deathTimer = 0;
    private static final int MAX_DEATH_TIMER = 45;

    public CrawlerCorpse(PlayMessages.SpawnEntity packet, Level world) {
        this(TYPE, world);
    }

    public CrawlerCorpse(EntityType<? extends Mob> type, Level world) {
        super(type, world);
        this.setNoAi(true);
        this.refreshDimensions();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(CUSTOM_SKIN, "");
        this.entityData.define(HALLOWEEN_SKIN, false);
        this.entityData.define(ZR_SCALE, 1.0f);
    }

    public String getCustomSkin() { return this.entityData.get(CUSTOM_SKIN); }
    public void setCustomSkin(String skinId) { this.entityData.set(CUSTOM_SKIN, skinId); }

    public boolean hasHalloweenSkin() { return this.entityData.get(HALLOWEEN_SKIN); }
    public void setHalloweenSkin(boolean halloween) { this.entityData.set(HALLOWEEN_SKIN, halloween); }

    public float getScale() { return this.entityData.get(ZR_SCALE); }
    public void setScale(float scale) { this.entityData.set(ZR_SCALE, scale); }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        this.setDeltaMovement(0, this.getDeltaMovement().y, 0);

        if (this.level().isClientSide) {
            if (this.tickCount % 3 == 0) {
                this.level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getRandomX(0.5), this.getY() + 0.2, this.getRandomZ(0.5), 0, 0.05, 0);
            }
        } else {
            deathTimer++;
            if (deathTimer >= MAX_DEATH_TIMER) {
                explode();
            }
        }
    }

    private void explode() {
        if (this.level() instanceof ServerLevel serverLevel) {
            CrawlerEntity.CrawlerGasManager.addGasCloud(serverLevel, this.position(), 240);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.5, this.getZ(), 20, 0.5, 0.5, 0.5, 0.05);
            serverLevel.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 0.5, this.getZ(), 20, 0.5, 0.5, 0.5, 0.05);
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.0f, 1.5f);
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.HOSTILE, 2.0f, 0.5f);
        }
        this.discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putString("CustomSkin", getCustomSkin());
        compound.putBoolean("HalloweenSkin", hasHalloweenSkin());
        compound.putFloat("zr_scale", getScale());
        compound.putInt("DeathTimer", deathTimer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        setCustomSkin(compound.getString("CustomSkin"));
        setHalloweenSkin(compound.getBoolean("HalloweenSkin"));
        if (compound.contains("zr_scale")) setScale(compound.getFloat("zr_scale"));
        deathTimer = compound.getInt("DeathTimer");
    }

    @Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class RegistryEvents {
        @SubscribeEvent
        public static void onRegisterEntities(RegisterEvent event) {
            if (event.getRegistryKey().equals(ForgeRegistries.Keys.ENTITY_TYPES)) {
                TYPE = EntityType.Builder.<CrawlerCorpse>of(CrawlerCorpse::new, MobCategory.MISC)
                        .setShouldReceiveVelocityUpdates(true)
                        .setTrackingRange(64)
                        .setUpdateInterval(3)
                        .setCustomClientFactory(CrawlerCorpse::new)
                        .sized(1f, 0.9f)
                        .build("crawler_corpse");
                event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> helper.register(new ResourceLocation(ZombieroolMod.MODID, "crawler_corpse"), TYPE));
            }
        }

        @SubscribeEvent
        public static void onRegisterAttributes(EntityAttributeCreationEvent event) {
            if (TYPE != null) {
                event.put(TYPE, Mob.createMobAttributes()
                    .add(Attributes.MAX_HEALTH, 10.0D)
                    .add(Attributes.MOVEMENT_SPEED, 0.0D)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D).build());
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    @Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientRegistryEvents {
        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(TYPE, CrawlerCorpseRenderer::new);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class CrawlerCorpseRenderer extends MobRenderer<CrawlerCorpse, ModelCrawler<CrawlerCorpse>> {
        public CrawlerCorpseRenderer(EntityRendererProvider.Context context) {
            super(context, new ModelCrawler<>(context.bakeLayer(ModelLayers.SPIDER)), 0.5f);
            this.addLayer(new EyesLayer<CrawlerCorpse, ModelCrawler<CrawlerCorpse>>(this) {
                @Override
                public void render(PoseStack pMatrixStack, MultiBufferSource pBuffer, int pPackedLight, CrawlerCorpse pLivingEntity, float pLimbSwing, float pLimbSwingAmount, float pPartialTicks, float pAgeInTicks, float pNetHeadYaw, float pHeadPitch) {
                    ResourceLocation customEye = DynamicResourceManager.getClientSkin("crawler_eyes", pLivingEntity.getCustomSkin());
                    if (customEye != null) {
                        RenderType renderType = RenderType.eyes(customEye);
                        com.mojang.blaze3d.vertex.VertexConsumer vertexconsumer = pBuffer.getBuffer(renderType);
                        this.getParentModel().renderToBuffer(pMatrixStack, vertexconsumer, 15728640, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
                    }
                }
                @Override
                public RenderType renderType() {
                    return RenderType.eyes(new ResourceLocation("minecraft:textures/entity/spider_eyes.png"));
                }
            });
        }

        @Override
        public ResourceLocation getTextureLocation(CrawlerCorpse entity) {
            String customSkin = entity.getCustomSkin();
            if (customSkin != null && !customSkin.isEmpty()) {
                ResourceLocation dyn = DynamicResourceManager.getClientSkin("crawler", customSkin);
                if (dyn != null) return dyn;
            }
            if (entity.hasHalloweenSkin()) {
                return new ResourceLocation("zombierool:textures/entities/halloween_crawler.png");
            }
            return new ResourceLocation("zombierool:textures/entities/crawler.png");
        }

        @Override
        protected void scale(CrawlerCorpse entity, PoseStack poseStack, float partialTickTime) {
            float s = entity.getScale();
            float progress = (entity.tickCount + partialTickTime) / 45.0f;
            if (progress > 1.0f) progress = 1.0f;
            
            float swell = 1.0f + (progress * 0.35f); 
            poseStack.scale(s * swell, s * swell, s * swell);
        }

        @Override
        protected void setupRotations(CrawlerCorpse entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks) {
            super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks);
            
            float progress = (entity.tickCount + partialTicks) / 45.0f;
            if (progress > 1.0f) progress = 1.0f;
            
            float shakeIntensity = progress * 5.0f; 
            float shakeX = Mth.sin(ageInTicks * 3.0f) * shakeIntensity;
            float shakeZ = Mth.cos(ageInTicks * 3.5f) * shakeIntensity;
            
            poseStack.mulPose(Axis.ZP.rotationDegrees(shakeZ));
            poseStack.mulPose(Axis.XP.rotationDegrees(shakeX));
        }
    }
}