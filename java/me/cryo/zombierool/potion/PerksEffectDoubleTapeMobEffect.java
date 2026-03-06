package me.cryo.zombierool.potion;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
public class PerksEffectDoubleTapeMobEffect extends MobEffect {
    public PerksEffectDoubleTapeMobEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFFF00);
    }
    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }
}