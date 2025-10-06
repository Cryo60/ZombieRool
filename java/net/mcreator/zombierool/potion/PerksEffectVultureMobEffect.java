package net.mcreator.zombierool.potion;


import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffect;


public class PerksEffectVultureMobEffect extends MobEffect {
    public PerksEffectVultureMobEffect() {
        super(MobEffectCategory.BENEFICIAL, -16711783);
    }

    @Override
    public String getDescriptionId() {
        return "effect.zombierool.perks_effect_vulture";
    }


    @Override
    public boolean isInstantenous() {
        return true;
    }


    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }
}