package net.mcreator.zombierool.potion;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffect;

public class PerksEffectBloodRageMobEffect extends MobEffect {
    public PerksEffectBloodRageMobEffect() {
        // Beneficial effect, color -13434880 (a shade of red)
        super(MobEffectCategory.BENEFICIAL, -13434880);
    }

    @Override
    public String getDescriptionId() {
        return "effect.zombierool.perks_effect_blood_rage";
    }

    @Override
    public boolean isInstantenous() {
        // This effect is not instant; it will persist on the player.
        // Set to false as the regeneration/absorption is event-driven.
        return false;
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // This effect doesn't need to do anything on every tick.
        // The logic for regeneration/absorption will be in a separate event handler.
        return false;
    }
}