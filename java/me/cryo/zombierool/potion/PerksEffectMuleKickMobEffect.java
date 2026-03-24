package me.cryo.zombierool.potion;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class PerksEffectMuleKickMobEffect extends MobEffect {
    public PerksEffectMuleKickMobEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x006400); 
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }
}