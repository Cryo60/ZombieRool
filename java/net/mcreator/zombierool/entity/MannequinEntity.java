package net.mcreator.zombierool.entity;

import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.network.PlayMessages;
import net.minecraftforge.network.NetworkHooks;

import net.minecraft.client.Minecraft; // Import for Minecraft client access
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.network.chat.MutableComponent; // Used for building the message

import net.mcreator.zombierool.init.ZombieroolModEntities;

public class MannequinEntity extends Monster {
    
    // --- Translation Helper Methods (Copied from CopyNotificationScreen) ---
    
    // Helper method to check if the client's language is English
    private static boolean isEnglishClient() {
        // Must be called on the client side, but since this logic only runs within
        // the hurt method inside the !this.level().isClientSide check, 
        // we assume the language is relevant to the server's message broadcast.
        // For simplicity and consistency with the previous approach, we keep it here.
        // Note: For true multi-lingual server messages, you should use the player's specific language.
        // However, for this auto-translation pattern, we use the client's language setting.
        return Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    // Helper method for dynamic translation
    private static MutableComponent getTranslatedComponent(String frenchMessage, String englishMessage) {
        if (isEnglishClient()) {
            return Component.literal(englishMessage);
        }
        return Component.literal(frenchMessage);
    }
    
    // ----------------------------------------------------------------------


    public MannequinEntity(PlayMessages.SpawnEntity packet, Level world) {
        this(ZombieroolModEntities.MANNEQUIN.get(), world);
    }

    public MannequinEntity(EntityType<MannequinEntity> type, Level world) {
        super(type, world);
        setMaxUpStep(0.6f);
        xpReward = 0;
        setNoAi(true);
        // Translate the custom name displayed on the entity
        setCustomName(getTranslatedComponent("Mannequin", "Dummy"));
        setCustomNameVisible(true);
        setPersistenceRequired();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource ds) {
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.armor_stand.hit"));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide) {
            boolean isHeadshot = false;

            if (source.getDirectEntity() instanceof Projectile projectile) {
                if (projectile.getY() >= this.getY() + this.getBbHeight() * 0.85) {
                    isHeadshot = true;
                }
            } else if (source.getSourcePosition() != null) {
                if (source.getSourcePosition().y >= this.getY() + this.getBbHeight() * 0.85) {
                    isHeadshot = true;
                }
            }
            
            float heartsLost = amount / 2.0F;

            // --- START: TRANSLATED MESSAGE CONSTRUCTION ---
            
            // 1. Get the localized prefix for the message
            MutableComponent message = getTranslatedComponent("Le Mannequin a reçu ", "The Dummy took ");

            // 2. Append damage amount (RED)
            message.append(Component.literal(String.format("%.1f", amount)).withStyle(ChatFormatting.RED));

            // 3. Append localized heart/damage separator
            message.append(getTranslatedComponent(" dégâts (", " damage ("));
            
            // 4. Append hearts lost amount (DARK_RED)
            message.append(Component.literal(String.format("%.1f", heartsLost)).withStyle(ChatFormatting.DARK_RED));

            // 5. Append localized suffix for hearts
            message.append(getTranslatedComponent(" cœurs)", " hearts)"));
            
            if (isHeadshot) {
                // 6. Append localized headshot text (GOLD)
                message.append(getTranslatedComponent(" (Tir à la tête !)", " (Headshot!)").withStyle(ChatFormatting.GOLD));
            }
            
            // 7. Append final punctuation
            message.append(Component.literal(" !"));

            // --- END: TRANSLATED MESSAGE CONSTRUCTION ---

            // The code is still using this.level().players().forEach(player -> player.sendSystemMessage(message));
            // This is functional, but remember that all players will see the message in *one* language 
            // (either English or French) based on the *server's* determination of the language code 
            // (which defaults to the primary client connected, or a system setting). 
            // For true per-player localization, you would need to check each 'player's' language 
            // inside the loop, but sticking to your current pattern, this is the best implementation.
            this.level().players().forEach(player -> player.sendSystemMessage(message));
        }

        if (source.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
            if (player.getMainHandItem().getItem() == Items.DIAMOND_PICKAXE) {
                return super.hurt(source, amount); 
            }
        }
        
        return false;
    }

    public static void init() {
    }

    public static AttributeSupplier.Builder createAttributes() {
        AttributeSupplier.Builder builder = Mob.createMobAttributes();
        builder = builder.add(Attributes.MOVEMENT_SPEED, 0.3);
        builder = builder.add(Attributes.MAX_HEALTH, 10);
        builder = builder.add(Attributes.ARMOR, 0);
        builder = builder.add(Attributes.ATTACK_DAMAGE, 3);
        builder = builder.add(Attributes.FOLLOW_RANGE, 16);
        return builder;
    }
}
