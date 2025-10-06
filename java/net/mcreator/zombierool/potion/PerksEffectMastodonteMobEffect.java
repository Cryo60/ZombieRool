package net.mcreator.zombierool.potion;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

public class PerksEffectMastodonteMobEffect extends MobEffect {
	public static final UUID MASTODONTE_UUID = UUID.fromString("6a29e4b6-a66c-4f7d-b8d4-5b4a7d3c0001");

	public PerksEffectMastodonteMobEffect() {
		super(MobEffectCategory.BENEFICIAL, 0xFF0000);
	}

	@Override
	public void applyEffectTick(LivingEntity entity, int amplifier) {
		if (entity instanceof Player player) {
			AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
			if (attr != null && attr.getModifier(MASTODONTE_UUID) == null) {
				attr.addTransientModifier(new AttributeModifier(MASTODONTE_UUID, "Juggernog bonus", 4.0, AttributeModifier.Operation.ADDITION));
				player.setHealth(player.getMaxHealth());
			}
		}
	}

	@Override
	public boolean isDurationEffectTick(int duration, int amplifier) {
		return true;
	}
}
