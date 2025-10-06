package net.mcreator.zombierool.potion;


import net.minecraft.world.effect.MobEffectCategory;

import net.minecraft.world.effect.MobEffect;


public class PerksEffectPHDFlopperMobEffect extends MobEffect {

public PerksEffectPHDFlopperMobEffect() {

super(MobEffectCategory.BENEFICIAL, -65485);

}


@Override

public String getDescriptionId() {

return "effect.zombierool.perks_effect_phd_flopper";

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