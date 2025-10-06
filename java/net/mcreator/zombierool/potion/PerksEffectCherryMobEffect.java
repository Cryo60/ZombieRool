package net.mcreator.zombierool.potion;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffect;

public class PerksEffectCherryMobEffect extends MobEffect {
    public PerksEffectCherryMobEffect() {
        super(MobEffectCategory.BENEFICIAL, -6749953);
    }

    @Override
    public String getDescriptionId() {
        return "effect.zombierool.perks_effect_cherry";
    }

    @Override
    public boolean isInstantenous() {
        return false; // L'effet n'est plus instantané
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return false; // Cet effet sera déclenché manuellement au rechargement
    }
}