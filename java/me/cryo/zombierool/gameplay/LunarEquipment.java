package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.config.MapConfigExtensions;
import me.cryo.zombierool.config.WorldConfig;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CSyncThirdPersonAnimPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.UUID;
import java.util.Optional;
import java.util.function.Supplier;

/** Secondary equipment: never replaces armor, an offhand item, or a tactical. Server owns every transition. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class LunarEquipment {
    /** Appearance only: every suit shares the same equipment slot and oxygen rules. */
    public enum SuitStyle {
        LUNAR("lunar"), DIVING("diving"), HAZMAT("hazmat");
        public final String id;
        SuitStyle(String id){this.id=id;}
        public static SuitStyle parse(String id){for(var style:values())if(style.id.equals(id))return style;return LUNAR;}
    }
    public static SuitStyle style(Player player){return SuitStyle.parse(data(player).getString("style"));}
    public static final String KEY="zr_lunar";
    public static final int DURATION=34, REMOVE_DURATION=21, SEAL_TICK=30, UNSEAL_TICK=4;
    private static final UUID GRAVITY_ID=UUID.fromString("8a801a78-788d-4465-9590-293563738f18");
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("zombierool","lunar"),()->"1","1"::equals,"1"::equals);
    public static void register() {
        EnemyWaterMovement.register();
        IntroPhase.register();
        CompoundTag defaults=new CompoundTag();defaults.putDouble("gravity",1);defaults.putBoolean("vacuum",false);defaults.putInt("oxygenSeconds",5);defaults.putString("startingSuit","none");
        MapConfigExtensions.register(new ResourceLocation("zombierool","lunar"),"general",defaults);
        CHANNEL.registerMessage(0,Toggle.class,(m,b)->{},b->new Toggle(),(m,c)->{var context=c.get();context.enqueueWork(()->{if(context.getSender()!=null)toggle(context.getSender());});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1,State.class,State::encode,State::decode,State::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2,Environment.class,(m,b)->b.writeNbt(m.data),b->new Environment(b.readNbt()),(m,c)->{c.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->me.cryo.zombierool.client.LunarEquipmentClient.environment(m.data)));c.get().setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public record Toggle() {}
    public record Environment(CompoundTag data){}
    public static void sendEnvironment(ServerPlayer p,CompoundTag data){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Environment(data));}
    public static boolean busy(net.minecraft.world.entity.Entity e){return e instanceof Player p&&data(p).getBoolean("changing");}
    public record State(UUID player,CompoundTag data) {
        static void encode(State msg,FriendlyByteBuf buf){buf.writeUUID(msg.player);buf.writeNbt(msg.data);}
        static State decode(FriendlyByteBuf buf){var id=buf.readUUID();var data=buf.readNbt();return new State(id,data==null?new CompoundTag():data);}
        static void handle(State msg,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->me.cryo.zombierool.client.LunarEquipmentClient.receive(msg.player,msg.data)));ctx.get().setPacketHandled(true);}
    }
    public static void requestToggle(){CHANNEL.sendToServer(new Toggle());}
    public static CompoundTag data(Player p){var root=p.getPersistentData();if(!root.contains(KEY,10))root.put(KEY,new CompoundTag());return root.getCompound(KEY);}
    public static boolean equipped(Player p){return data(p).getBoolean("equipped");}
    public static void applyStartingSuit(ServerPlayer player,WorldConfig config){
        revoke(player);String id=config.getAddonConfig().getCompound("zombierool:lunar").getString("startingSuit");
        if(!java.util.Set.of("lunar","diving","hazmat").contains(id))return;
        acquire(player,SuitStyle.parse(id));var state=data(player);state.putBoolean("equipped",true);state.putBoolean("target",true);sync(player);
    }
    public static boolean protectedFromVacuum(Player p){var n=data(p);return n.getBoolean("equipped")||(n.getBoolean("owned")&&n.getBoolean("changing")&&n.getBoolean("target"));}
    public static boolean acquire(ServerPlayer p){return acquire(p,SuitStyle.LUNAR);}
    public static boolean acquire(ServerPlayer p,SuitStyle style){
        var n=data(p);
        if(n.getBoolean("owned")&&style(p)==style)return false;
        n.putString("style",style.id);n.putBoolean("owned",true);n.putBoolean("equipped",false);
        n.putBoolean("changing",false);n.putBoolean("target",false);n.putInt("elapsed",0);sync(p);return true;
    }
    public static boolean toggle(ServerPlayer p){return setEquipped(p,!data(p).getBoolean("equipped"));}
    public static boolean setEquipped(ServerPlayer p,boolean target){var n=data(p);
        if(!p.isAlive()||p.isSpectator()||me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(p.getUUID())||!n.getBoolean("owned")||n.getBoolean("changing")||n.getBoolean("equipped")==target)return false;
        if(PortableEquipment.inHands(p))PortableEquipment.toggle(p);n.putBoolean("changing",true);n.putBoolean("target",target);n.putInt("elapsed",0);p.stopUsingItem();
        EquipmentSystem.cancel(p);me.cryo.zombierool.handlers.LethalWeaponManager.cancel(p);
        for(var stack:p.getHandSlots())if(stack.getItem() instanceof me.cryo.zombierool.core.system.WeaponSystem.BaseGunItem gun){
            var tag=stack.getOrCreateTag();if(tag.getBoolean(gun.TAG_IS_RELOADING))p.getCooldowns().removeCooldown(gun);tag.putBoolean(gun.TAG_IS_RELOADING,false);tag.putInt(gun.TAG_RELOAD_TIMER,0);tag.putInt(gun.TAG_BURST_SHOTS_LEFT,0);tag.putInt(gun.TAG_BURST_DELAY,0);
        }
        me.cryo.zombierool.integration.TacZIntegration.stopTeleporterActions(p);
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),new S2CSyncThirdPersonAnimPacket(p.getUUID(),target?"helmet_equip":"helmet_remove",target?DURATION:REMOVE_DURATION));
        sound(p,"helmet_handle",.7f);sync(p);return true;
    }
    public static void revoke(ServerPlayer p){var n=data(p);n.putBoolean("owned",false);n.putBoolean("equipped",false);n.putBoolean("changing",false);n.putBoolean("target",false);n.putInt("elapsed",0);sync(p);}
    private static void sound(ServerPlayer p,String id,float volume){p.serverLevel().playSound(null,p.blockPosition(),net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool",id)),SoundSource.PLAYERS,volume,1);}
    private static void sync(ServerPlayer p){CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),new State(p.getUUID(),data(p).copy()));}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){revoke(p);LunarPhysics.forget(p);LunarPhysics.sync(p);}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){revoke(p);LunarPhysics.forget(p);}}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void death(net.minecraftforge.event.entity.living.LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p)revoke(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p){data(p).putBoolean("changing",false);sync(p);LunarPhysics.forget(p);LunarPhysics.sync(p);}}
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking e){if(e.getEntity() instanceof ServerPlayer observer&&e.getTarget() instanceof ServerPlayer p)CHANNEL.send(PacketDistributor.PLAYER.with(()->observer),new State(p.getUUID(),data(p).copy()));}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){var n=new CompoundTag();e.getEntity().getPersistentData().put(KEY,n);}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p))return;
        var n=data(p);boolean dirty=false;if(p.tickCount%20==0)LunarPhysics.sync(p);
        if(n.getBoolean("changing")){
            if(!p.isAlive()||p.isSpectator()||me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(p.getUUID())){n.putBoolean("changing",false);dirty=true;}
            else {int elapsed=n.getInt("elapsed")+1;n.putInt("elapsed",elapsed);boolean target=n.getBoolean("target");
                if(elapsed==(target?SEAL_TICK:UNSEAL_TICK)){n.putBoolean("equipped",target);sound(p,target?"helmet_seal":"helmet_unseal",.8f);dirty=true;
                    me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnHelmetChanged",p.getUUID().toString(),target);}
                if(elapsed>=(target?DURATION:REMOVE_DURATION)){n.putBoolean("changing",false);dirty=true;}
            }
        }
        var settings=WorldConfig.get(p.serverLevel()).getAddonConfig().getCompound("zombierool:lunar");
        var conditions=LunarEnvironment.get(p.serverLevel()).at(p.getX(),p.getEyeY(),p.getZ(),new LunarEnvironment.Conditions(LunarEnvironment.gravity(settings.getDouble("gravity")),settings.getBoolean("vacuum")));
        boolean exempt=p.isCreative()||p.isSpectator();double gravity=exempt?1:conditions.gravity();
        applyGravity(p,gravity);
        if(gravity<1&&!p.onGround())n.putBoolean("lunarFall",true);
        if(gravity>=1&&n.getBoolean("lunarFall")){
            // Start a normal fall when crossing out of low gravity. The helmet only seals air.
            if(n.getDouble("gravity")<1&&!p.onGround())p.fallDistance=0;
            n.putBoolean("lunarFall",false);
        }else if(p.onGround()&&n.getBoolean("lunarFall")){p.fallDistance=0;n.putBoolean("lunarFall",false);}
        int capacity=Math.max(1,Math.min(300,settings.getInt("oxygenSeconds")))*20;
        int air=n.contains("air")?n.getInt("air"):capacity;
        boolean underwater=p.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
        boolean waterBreathing=p.hasEffect(net.minecraft.world.effect.MobEffects.WATER_BREATHING)||p.hasEffect(net.minecraft.world.effect.MobEffects.CONDUIT_POWER);
        boolean needsOxygen=(conditions.vacuum()||(underwater&&!waterBreathing))&&!exempt;
        boolean suffocating=needsOxygen&&!protectedFromVacuum(p)&&p.isAlive();
        air=suffocating?Math.max(0,air-1):Math.min(capacity,air+4);
        if(n.getBoolean("underwater")!=underwater||n.getBoolean("needsOxygen")!=needsOxygen||n.getBoolean("vacuum")!=conditions.vacuum())dirty=true;
        n.putInt("air",air);n.putInt("capacity",capacity);n.putBoolean("vacuum",conditions.vacuum());n.putBoolean("underwater",underwater);n.putBoolean("needsOxygen",needsOxygen);
        // A single reserve drives both environments. Vanilla must not apply a second drowning clock.
        p.setAirSupply(Math.round(p.getMaxAirSupply()*air/(float)capacity));
        if(n.getDouble("gravity")!=gravity)dirty=true;n.putDouble("gravity",gravity);
        if(suffocating&&air==0&&p.tickCount%20==0)p.hurt(p.damageSources().drown(),2);
        if(equipped(p)&&p.isAlive()&&p.tickCount%80==0)sound(p,"helmet_breath",.25f);
        if(dirty||p.tickCount%(suffocating?5:20)==0)sync(p);
    }
    public static void applyGravity(net.minecraft.world.entity.LivingEntity p,double gravity){
        var attribute=p.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
        if(attribute!=null){var modifier=attribute.getModifier(GRAVITY_ID);double amount=gravity-1;
            if(modifier!=null&&(Math.abs(modifier.getAmount()-amount)>1e-8||amount==0))attribute.removeModifier(GRAVITY_ID);
            if(amount!=0&&attribute.getModifier(GRAVITY_ID)==null)attribute.addTransientModifier(new AttributeModifier(GRAVITY_ID,"ZombieRool lunar gravity",amount,AttributeModifier.Operation.MULTIPLY_TOTAL));}
    }
    public static double jumpScale(double gravity){return gravity>=1?1:Math.sqrt(Math.max(.02,gravity)/Math.max(.35,gravity));}
    @SubscribeEvent public static void jump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent e){if(e.getEntity() instanceof Player p){double gravity=data(p).contains("gravity")?data(p).getDouble("gravity"):1;if(gravity<1){var v=p.getDeltaMovement();p.setDeltaMovement(v.x,v.y*jumpScale(gravity),v.z);data(p).putBoolean("lunarFall",true);}}}
    @SubscribeEvent public static void fall(LivingFallEvent e){if(e.getEntity() instanceof Player p){double gravity=LunarPhysics.gravityAt(p);if(gravity<1){e.setDamageMultiplier(0);p.fallDistance=0;}}}
    private LunarEquipment(){}
}
