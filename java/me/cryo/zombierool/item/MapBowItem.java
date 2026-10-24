package me.cryo.zombierool.item;

import me.cryo.zombierool.core.system.WeaponSystem;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.*;

/** Draw and release are timed by the server, using ordinary ZR arrows and ammunition. */
public final class MapBowItem extends FantasyRangedWeaponItem {
    public MapBowItem(WeaponSystem.Definition def){super(def);}
    @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.BOW;}
    @Override public int getUseDuration(ItemStack stack){return 72000;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);initializeIfNeeded(stack);
        if(hand!=InteractionHand.MAIN_HAND||stack.getOrCreateTag().getBoolean(TAG_IS_RELOADING)||player.getCooldowns().isOnCooldown(this)||getAmmo(stack)<=0||!me.cryo.zombierool.gameplay.MapWeaponPacks.available(getDefinition().id.replace("zombierool:","")))return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);if(!level.isClientSide)playSound(level,player,getDefinition().sounds.draw);return InteractionResultHolder.consume(stack);
    }
    @Override public void releaseUsing(ItemStack stack,Level level,LivingEntity entity,int remaining){
        if(level.isClientSide||!(entity instanceof Player player)||player.getMainHandItem()!=stack)return;
        int held=getUseDuration(stack)-remaining;if(held<6)return;
        float draw=Math.min(1,held/(float)Math.max(6,getDefinition().draw_ticks));super.tryShoot(stack,player,draw,false);
    }
    /** Shoot packets cannot bypass drawing the bow. */
    @Override public void tryShoot(ItemStack stack,Player player,float charge,boolean left){}
}
