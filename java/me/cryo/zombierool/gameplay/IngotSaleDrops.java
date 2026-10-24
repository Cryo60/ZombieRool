package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.entity.ZombieEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** A console request belongs to its player and is consumed by one real zombie kill. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool")
public final class IngotSaleDrops {
    private static final String PENDING="zr_next_ingot_sale";
    private static final String TEAM_REWARDS="zr_pending_team_ingots";
    private IngotSaleDrops(){}
    public static void queue(Player player){player.getPersistentData().putBoolean(PENDING,true);}
    public static boolean consume(Player player,LivingEntity victim){
        if(player.level().isClientSide||!(victim instanceof ZombieEntity)||!player.getPersistentData().getBoolean(PENDING))return false;
        player.getPersistentData().remove(PENDING);return true;
    }
    public static void grant(Player player){
        var reward=new net.minecraft.world.item.ItemStack(me.cryo.zombierool.init.ZombieroolModItems.INGOT_SALE.get());
        if(player.getInventory().add(reward))return;
        // Mark it before joining the world so survival-item restrictions cannot delete it.
        var drop=new net.minecraft.world.entity.item.ItemEntity(player.level(),player.getX(),player.getY()+.3,player.getZ(),reward);
        drop.getPersistentData().putUUID("zr_drop_owner",player.getUUID());drop.setDefaultPickUpDelay();
        player.level().addFreshEntity(drop);
    }
    /** Downed/spectating players receive the reward after their loadout is restored. */
    public static void grantTeamReward(net.minecraft.server.level.ServerPlayer player){
        if(!player.isAlive()||player.isSpectator()||me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(player.getUUID())){
            var data=player.getPersistentData();data.putInt(TEAM_REWARDS,data.getInt(TEAM_REWARDS)+1);return;
        }
        grant(player);player.getInventory().setChanged();player.containerMenu.broadcastChanges();
    }
    public static void clearTeamRewards(Player player){player.getPersistentData().remove(TEAM_REWARDS);}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.PlayerTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||!(event.player instanceof net.minecraft.server.level.ServerPlayer player))return;
        var data=player.getPersistentData();int count=data.getInt(TEAM_REWARDS);if(count<=0)return;
        if(!WaveManager.isGameRunning()){clearTeamRewards(player);return;}
        if(!player.isAlive()||player.isSpectator()||me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(player.getUUID()))return;
        clearTeamRewards(player);for(int i=0;i<count;i++)grant(player);player.getInventory().setChanged();player.containerMenu.broadcastChanges();
    }
}
