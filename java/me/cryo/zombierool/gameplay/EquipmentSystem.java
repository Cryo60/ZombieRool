package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.core.capability.ZombieCapabilitySystem;
import me.cryo.zombierool.core.registry.ZRThrowableRegistry;
import me.cryo.zombierool.config.WorldConfig;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CSyncThirdPersonAnimPacket;
import me.cryo.zombierool.player.PlayerDownManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import net.minecraft.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Server-owned virtual loadout. Creative items are selectors, never carried during play. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class EquipmentSystem {
    public enum Kind {
        C4("c4",1), CLAYMORE("claymore",1), BETTY("bouncing_betty",1),
        PHEROMONE("pheromone",2), FLASH("flash",2), SEMTEX("semtex",3);
        public final String id;public final int slot;
        Kind(String id,int slot){this.id=id;this.slot=slot;}
        public static Kind of(String id){for(var k:values())if(id.equals(k.id)||id.equals("zombierool:"+k.id))return k;return null;}
    }
    public static class LoadoutItem extends Item {
        private final Kind kind;
        public LoadoutItem(Kind kind){super(new Properties().stacksTo(1));this.kind=kind;}
        public Kind kind(){return kind;}
        @Override public void inventoryTick(ItemStack stack,Level level,net.minecraft.world.entity.Entity entity,int slot,boolean selected){
            if(entity instanceof ServerPlayer p&&!p.isCreative()){equip(p,kind);stack.setCount(0);p.inventoryMenu.broadcastChanges();}
        }
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            var stack=player.getItemInHand(hand);
            if(player instanceof ServerPlayer sp){equip(sp,kind);if(!player.isCreative())stack.shrink(1);}
            return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
        }
        @Override public void appendHoverText(ItemStack s,Level l,List<Component> lines,TooltipFlag flag){
            lines.add(Component.translatable("tooltip.zombierool.virtual_equipment"));
        }
    }
    private record Hold(Kind kind,long since){}
    private record Action(Kind kind,long at,Vec3 pos,float yaw){}
    private static final Map<UUID,Hold> HELD=new HashMap<>();
    private static final Map<UUID,Action> ACTIONS=new HashMap<>();
    private static final Map<UUID,Long> COOLDOWNS=new HashMap<>();
    private EquipmentSystem(){}
    public static boolean valid(Player p){return p.isAlive()&&!p.isSpectator()&&!PlayerDownManager.isPlayerDown(p.getUUID())&&!TeleporterPlayerLock.active(p)&&!LunarEquipment.busy(p)&&!BuildableSystem.busy(p);}
    public static boolean busy(UUID id){return HELD.containsKey(id)||ACTIONS.containsKey(id);}
    public static boolean equipped(Player p,Kind k){return p.getCapability(ZombieCapabilitySystem.Provider.PLAYER_DATA).map(c->Kind.of(k.slot==1?c.getEquipmentType():k.slot==2?c.getTacticalType():c.getLethalType())==k).orElse(false);}
    public static void equip(ServerPlayer p,Kind k){
        if(HELD.remove(p.getUUID())!=null)animate(p,"equipment_cancel",6);
        p.getCapability(ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(c->{
            switch(k.slot){case 1-> {c.setEquipmentType("zombierool:"+k.id);c.setEquipmentCount(2);}case 2->{c.setTacticalType("zombierool:"+k.id);c.setTacticalCount(2);}default->{c.setLethalType("zombierool:"+k.id);c.setLethalCount(5);}}
            c.sync(p);
        });
        p.sendSystemMessage(Component.translatable("message.zombierool.equipment_equipped",new ItemStack(ZRThrowableRegistry.loadoutItem(k)).getHoverName()));
    }
    public static void refill(ZombieCapabilitySystem.IData c){c.setEquipmentCount(Kind.of(c.getEquipmentType())==null?0:2);c.setTacticalCount(Kind.of(c.getTacticalType())==null?0:2);}
    public static void onWaveStart(ServerLevel level,int wave){for(ServerPlayer p:level.getServer().getPlayerList().getPlayers())if(p.level()==level)rechargeForWave(p,wave);}
    public static void rechargeForWave(ServerPlayer p,int wave){
        var data=p.getPersistentData();if(wave<=1||data.getInt("zr_equipment_charge_wave")>=wave)return;data.putInt("zr_equipment_charge_wave",wave);
        p.getCapability(ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(c->{
            boolean changed=false;
            if(wave%2==0&&Kind.of(c.getTacticalType())!=null&&c.getTacticalCount()<2){c.setTacticalCount(c.getTacticalCount()+1);changed=true;}
            if(wave%4==0&&Kind.of(c.getEquipmentType())!=null&&c.getEquipmentCount()<2){c.setEquipmentCount(c.getEquipmentCount()+1);changed=true;}
            if(changed)c.sync(p);
        });
    }
    public static void resetLoadout(ServerPlayer p,ZombieCapabilitySystem.IData c){
        LunarEquipment.revoke(p);
        p.getPersistentData().remove("zr_vril_camouflage_until");p.getPersistentData().remove("zr_vril_damage_until");p.getPersistentData().remove("zr_player_flash_until");
        p.getPersistentData().remove("zr_equipment_charge_wave");
        cancel(p);me.cryo.zombierool.handlers.LethalWeaponManager.cancel(p);var config=WorldConfig.get(p.serverLevel());
        c.setEquipmentType(config.getStartingEquipment());c.setTacticalType(config.getStartingTactical());refill(c);
    }
    public static void cancel(ServerPlayer p){if(HELD.remove(p.getUUID())!=null)animate(p,"equipment_cancel",6);ACTIONS.remove(p.getUUID());COOLDOWNS.remove(p.getUUID());}
    public static void input(ServerPlayer p,int slot,int state){
        if(state==0){cancel(p);return;}
        if(!valid(p)){cancel(p);return;}
        long now=p.level().getGameTime();var id=p.getUUID();
        if(state==1){
            if(busy(id)||now<COOLDOWNS.getOrDefault(id,0L)||me.cryo.zombierool.handlers.LethalWeaponManager.isCooking(id))return;
            p.getCapability(ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(c->{
                var k=Kind.of(slot==1?c.getEquipmentType():c.getTacticalType());
                if(k==null||k.slot!=slot)return;
                if(k==Kind.C4&&detonateOwned(p)){animate(p,"c4_detonate",12);COOLDOWNS.put(id,now+15);return;}
                if((slot==1?c.getEquipmentCount():c.getTacticalCount())<=0)return;
                HELD.put(id,new Hold(k,now));animate(p,k.id+"_hold",12000);sound(p.serverLevel(),p.position(),k==Kind.CLAYMORE?"claymore_draw":"equipment_draw",.5f);
            });
        }else if(state==2){
            var hold=HELD.get(id);if(hold==null||hold.kind.slot!=slot)return;
            HELD.remove(id);release(p,hold.kind);
        }
    }
    public static void releaseSemtex(ServerPlayer p){if(valid(p))release(p,Kind.SEMTEX);}
    private static void release(ServerPlayer p,Kind k){
        Vec3 destination=null;
        if(k==Kind.CLAYMORE||k==Kind.BETTY){
            Vec3 eye=p.getEyePosition(),ahead=p.position().add(p.getLookAngle().x*1.4,1.0,p.getLookAngle().z*1.4);
            var hit=p.level().clip(new net.minecraft.world.level.ClipContext(eye,ahead.add(0,-3,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
            if(hit.getType()!=HitResult.Type.BLOCK||hit.getDirection()!=net.minecraft.core.Direction.UP||eye.distanceToSqr(hit.getLocation())>16){animate(p,"equipment_cancel",6);return;}
            destination=hit.getLocation().add(0,.02,0);
        }
        Vec3 placement=destination;
        p.getCapability(ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(c->{
            int count=k.slot==1?c.getEquipmentCount():k.slot==2?c.getTacticalCount():c.getLethalCount();if(count<=0)return;
            if(k.slot==1)c.setEquipmentCount(count-1);else if(k.slot==2)c.setTacticalCount(count-1);else c.setLethalCount(count-1);
            c.sync(p);int delay=placement==null?5:8;
            ACTIONS.put(p.getUUID(),new Action(k,p.level().getGameTime()+delay,placement,p.getYRot()));
            COOLDOWNS.put(p.getUUID(),p.level().getGameTime()+20);animate(p,k.id+(placement==null?"_throw":"_place"),18);
        });
    }
    public static boolean detonateOwned(ServerPlayer p){
        boolean found=false;
        for(var e:p.serverLevel().getAllEntities())if(e instanceof EquipmentEntity device&&device.kind()==Kind.C4&&p.getUUID().equals(device.ownerId())){device.requestDetonation();found=true;}
        if(found)sound(p.serverLevel(),p.position(),"c4_click",.5f);return found;
    }
    public static void animate(ServerPlayer p,String name,int ticks){NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),new S2CSyncThirdPersonAnimPacket(p.getUUID(),name,ticks));}
    public static void sound(ServerLevel level,Vec3 pos,String id,float volume){
        var sound=ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool",id));
        if(sound!=null)level.playSound(null,pos.x,pos.y,pos.z,sound,SoundSource.PLAYERS,volume,1);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        HELD.entrySet().removeIf(e->{var p=event.getServer().getPlayerList().getPlayer(e.getKey());if(p!=null&&valid(p))return false;if(p!=null)animate(p,"equipment_cancel",6);return true;});
        var ready=new ArrayList<UUID>();
        ACTIONS.forEach((id,a)->{var p=event.getServer().getPlayerList().getPlayer(id);if(p==null||!valid(p)||p.level().getGameTime()>=a.at)ready.add(id);});
        for(var id:ready){var action=ACTIONS.remove(id);var p=event.getServer().getPlayerList().getPlayer(id);if(p==null||!valid(p))continue;
            var device=new EquipmentEntity(p.serverLevel(),p,action.kind);device.setYRot(action.yaw);
            if(action.pos!=null){device.setPos(action.pos);device.land();sound(p.serverLevel(),action.pos,action.kind==Kind.CLAYMORE?"claymore_place":"equipment_place",.65f);}
            else {device.setPos(p.getEyePosition().add(0,-.15,0));device.setDeltaMovement(p.getLookAngle().scale(action.kind==Kind.C4?.8:1.1));sound(p.serverLevel(),p.position(),action.kind==Kind.SEMTEX?"semtex_throw":"equipment_throw",.5f);}
            if(p.serverLevel().addFreshEntity(device))me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnEquipmentUsed",p.getUUID().toString(),action.kind.id,device.getX(),device.getY(),device.getZ());
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p)cancel(p);}
}
