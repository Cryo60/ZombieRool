package net.mcreator.zombierool.potion;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.world.entity.player.Player;
// import net.minecraft.world.item.ItemStack; // Plus nécessaire si on retire la condition
// import net.mcreator.zombierool.api.ICustomWeapon; // Plus nécessaire si on retire la condition
import net.minecraftforge.common.MinecraftForge;

public class PerksEffectDoubleTapeMobEffect extends MobEffect {
    public PerksEffectDoubleTapeMobEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFFF00);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onAttack(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (!player.hasEffect(this)) return;

        // Condition supprimée pour affecter toutes les armes
        event.setAmount(event.getAmount() * 2f); // dégâts x2
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }
}