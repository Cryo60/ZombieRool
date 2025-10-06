package net.mcreator.zombierool.potion;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity; // Correct import
import net.minecraft.world.entity.ai.attributes.AttributeMap; // Correct import

import java.util.UUID;

public class PerksEffectSpeedColaMobEffect extends MobEffect {
    private static final UUID SPEED_COLA_MS_UUID = UUID.fromString("6a29e4b6-a66c-4f7d-b8d4-5b4a7d3c0b1f");

    public PerksEffectSpeedColaMobEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x00AAFF);

        // Define the attribute modifier in the constructor.
        // We set the VALUE to the smallest common increment (0.05D)
        // and use MULTIPLY_BASE.
        // This means actual effect is: base_speed * (1 + value * (amplifier + 1))
        this.addAttributeModifier(
            Attributes.MOVEMENT_SPEED,
            SPEED_COLA_MS_UUID.toString(),
            0.05D, // Base value for the modifier itself
            AttributeModifier.Operation.MULTIPLY_BASE
        );
    }

    // No need to override applyAttributesModifiers or removeAttributesModifiers
    // as MobEffect handles this automatically based on the constructor's addAttributeModifier.

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }
}