package me.cryo.zombierool.gameplay;

import java.util.*;
import me.cryo.zombierool.bonuses.BonusManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Operator requests are consumed by one credited hostile kill, independently of ordinary drop chances. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class QueuedBonusDrops {
    private static final String KEY="zr_next_bonus";
    public static BonusManager.Bonus resolve(Player player,String id){
        if(!id.equalsIgnoreCase("random"))return BonusManager.getBonus(id.toLowerCase(Locale.ROOT));
        var bonus=BonusManager.getRandomBonus(player);if(bonus!=null)return bonus;
        var all=new ArrayList<>(BonusManager.ALL_BONUSES.values());return all.isEmpty()?null:all.get(player==null?new Random().nextInt(all.size()):player.getRandom().nextInt(all.size()));
    }
    public static void queue(Player player,String id){
        if(!id.equalsIgnoreCase("random")&&BonusManager.getBonus(id.toLowerCase(Locale.ROOT))==null)throw new IllegalArgumentException("Unknown bonus: "+id);
        player.getPersistentData().putString(KEY,id.toLowerCase(Locale.ROOT));
    }
    public static String pending(Player player){return player.getPersistentData().getString(KEY);}
    public static void clear(Player player){player.getPersistentData().remove(KEY);}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void death(LivingDeathEvent event){
        if(event.getEntity().level().isClientSide||!(event.getSource().getEntity() instanceof ServerPlayer player)
            ||!(event.getEntity() instanceof net.minecraft.world.entity.monster.Monster)
            ||event.getEntity().getPersistentData().getBoolean("zr_forced_bonus_drop"))return;
        String id=pending(player);if(id.isEmpty())return;var bonus=resolve(player,id);if(bonus==null)return;
        clear(player);event.getEntity().getPersistentData().putBoolean("zr_forced_bonus_drop",true);
        BonusManager.spawnDebugBonus(bonus,player.serverLevel(),event.getEntity().position().add(0,.5,0));
    }
    private QueuedBonusDrops(){}
}
