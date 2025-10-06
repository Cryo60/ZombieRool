
package net.mcreator.zombierool.potion;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffect;

public class PerksEffectQuickReviveMobEffect extends MobEffect {
	public PerksEffectQuickReviveMobEffect() {
		super(MobEffectCategory.BENEFICIAL, -16711681);
	}

	@Override
	public String getDescriptionId() {
		return "effect.zombierool.perks_effect_quick_revive";
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
