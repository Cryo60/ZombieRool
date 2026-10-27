package me.cryo.zombierool.gameplay;

import java.util.*;
import me.cryo.zombierool.core.system.WeaponImplementations.MeleeWeaponItem;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.core.manager.DamageManager;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CMeleeMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.sounds.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** Server-owned swings. A miss still has recovery; a hit is resolved once, at contact. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class MeleeCombat {
    private static final Map<UUID, Swing> SWINGS=new HashMap<>();
    private static final UUID SHRED=UUID.fromString("3b3f97b0-18f2-4cc6-8f2a-1dfc39cf2430");
    private static final UUID STAGGER=UUID.fromString("34d8d5c3-7521-4dd7-9d60-c0d9f4caa790");
    private static final class Swing {
        ItemStack stack; long start,until; int combo; boolean heavy,contact,buffered;
        MeleeMoves.Move move;
    }
    public static boolean allowed(ServerPlayer p) {
        return p.isAlive()&&!p.isSpectator()&&!me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(p.getUUID())
            &&!WaveManager.isPausedByPlayer()&&!TeleporterPlayerLock.active(p)&&!LunarEquipment.busy(p)
            &&!PortableEquipment.inHands(p)&&!BuildableSystem.busy(p)
            &&!me.cryo.zombierool.handlers.LethalWeaponManager.isCooking(p.getUUID());
    }
    public static boolean canCharge(ServerPlayer p){
        Swing swing=SWINGS.get(p.getUUID());
        return allowed(p)&&(swing==null||p.level().getGameTime()>=swing.until);
    }
    public static boolean request(ServerPlayer p,boolean heavy) {
        if(!allowed(p)||p.isUsingItem()||!(p.getMainHandItem().getItem() instanceof MeleeWeaponItem weapon))return false;
        ItemStack stack=p.getMainHandItem();
        if(!MapWeaponPacks.available(weapon.getDefinition().id.replace("zombierool:","")))return false;
        weapon.initializeIfNeeded(stack);
        if(weapon.hasDurability()&&weapon.getDurability(stack)<=0)return false;
        long now=p.level().getGameTime();Swing old=SWINGS.get(p.getUUID());
        if(old!=null&&now<old.until){
            // One buffered light input only; neither packet spam nor weapon swapping skips recovery.
            if(!heavy&&old.stack==stack&&old.until-now<=4)old.buffered=true;
            return false;
        }
        Swing s=new Swing();s.stack=stack;s.start=now;s.heavy=heavy;s.move=MeleeMoves.move(stack,p,heavy);
        s.combo=!heavy&&old!=null&&old.stack==stack&&now-old.until<=12?(old.combo+1)%3:0;
        s.until=now+s.move.duration();SWINGS.put(p.getUUID(),s);
        var tag=stack.getOrCreateTag();tag.putInt("zr_combo",s.combo);tag.putLong(WeaponSystem.BaseGunItem.TAG_LAST_FIRE,now);
        p.resetAttackStrengthTicker();p.swing(net.minecraft.world.InteractionHand.MAIN_HAND,true);
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),new S2CMeleeMotionPacket(p.getUUID(),s.move.duration(),s.move.contact(),s.combo,heavy,false));
        weapon.playMeleeSwing(p,stack,heavy);
        return true;
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if(event.phase==net.minecraftforge.event.TickEvent.Phase.END&&event.player instanceof ServerPlayer p)advance(p);
    }
    public static void advance(ServerPlayer p) {
        Swing s=SWINGS.get(p.getUUID());if(s==null)return;
        long now=p.level().getGameTime();
        if(!allowed(p)||p.getMainHandItem()!=s.stack||s.stack.isEmpty()){
            s.contact=true;s.buffered=false;
            if(now>=s.until)SWINGS.remove(p.getUUID());return;
        }
        if(!s.contact&&now>=s.start+s.move.contact()){
            s.contact=true;
            if(!p.isUsingItem())impact(p,s);
        }
        if(now>=s.until&&s.buffered){s.buffered=false;request(p,false);}
        else if(now>s.until+12)SWINGS.remove(p.getUUID());
    }
    private static Vec3 nearest(AABB b,Vec3 p) {
        return new Vec3(net.minecraft.util.Mth.clamp(p.x,b.minX,b.maxX),net.minecraft.util.Mth.clamp(p.y,b.minY,b.maxY),net.minecraft.util.Mth.clamp(p.z,b.minZ,b.maxZ));
    }
    /** Box-distance handles large enemies and small rats without aim assistance through walls. */
    public static boolean inArc(Player p,LivingEntity target,double reach,double cone) {
        Vec3 eye=p.getEyePosition(),point=nearest(target.getBoundingBox(),eye),delta=point.subtract(eye);
        return delta.lengthSqr()<=reach*reach&&(delta.lengthSqr()<.04||delta.normalize().dot(p.getLookAngle())>=cone)
            &&MeleeTrace.clear(p.level(),eye,point);
    }
    private static boolean enemy(Player p,LivingEntity e) {
        return e!=p&&!(e instanceof Player)&&e.isAlive()&&!e.isSpectator()&&(e instanceof net.minecraft.world.entity.monster.Enemy||MapActors.owns(e)||e instanceof me.cryo.zombierool.entity.DummyEntity);
    }
    private static void impact(ServerPlayer p,Swing s) {
        var level=p.serverLevel();var weapon=(MeleeWeaponItem)s.stack.getItem();var move=s.move;
        Vec3 eye=p.getEyePosition(),look=p.getLookAngle();
        var targets=level.getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(move.reach()),e->enemy(p,e)&&inArc(p,e,move.reach(),move.cone()));
        // Prefer the crosshair, then proximity. Cleave remains a forward arc, never a rear sphere.
        targets.sort(Comparator.<LivingEntity>comparingDouble(e->e.getBoundingBox().inflate(.12).clip(eye,eye.add(look.scale(move.reach()))).isPresent()?0:1).thenComparingDouble(p::distanceToSqr));
        float damage=weapon.getWeaponDamage(s.stack)*(s.heavy?1.8f:s.combo==2?1.3f:1);
        if(p.hasEffect(me.cryo.zombierool.init.ZombieroolModMobEffects.PERKS_EFFECT_DOUBLE_TAP.get()))damage*=2;
        int hits=0;LivingEntity primary=null;
        int limit=AshbringerCombat.matches(s.stack)?1:move.targets();
        for(var target:targets){
            if(hits>=limit)break;
            float amount=damage*(hits==0?1:move.secondary());
            if(MeleeMoves.quick(s.stack)&&target.getLookAngle().dot(p.position().subtract(target.position()).normalize())<-.35)amount*=1.75f;
            target.getPersistentData().remove(DamageManager.GUN_DAMAGE_TAG);target.getPersistentData().remove(DamageManager.HEADSHOT_TAG);target.getPersistentData().remove("zombierool:explosive_damage");
            if(me.cryo.zombierool.bonuses.BonusManager.isInstaKillActive(p)&&!EquipmentEffects.instantKillImmune(target))amount=100000;
            double verticalBefore=target.getDeltaMovement().y;
            if(!AshbringerCombat.hit(p,s.stack,target,amount))continue;
            var after=target.getDeltaMovement();target.setDeltaMovement(after.x,verticalBefore,after.z);
            if(primary==null)primary=target;hits++;
            // Resistance keeps bosses planted; ordinary enemies yield space to committed strikes.
            if(target.isAlive())pushHorizontal(p,target,s.heavy?.65:s.combo==2?.35:.22);
            if(target.isAlive()&&(s.heavy||s.combo==2)&&!EquipmentEffects.instantKillImmune(target)){
                var speed=target.getAttribute(Attributes.MOVEMENT_SPEED);
                if(speed!=null){speed.removeModifier(STAGGER);speed.addTransientModifier(new AttributeModifier(STAGGER,"Melee recovery",-.45,AttributeModifier.Operation.MULTIPLY_TOTAL));target.getPersistentData().putLong("zr_melee_stagger_until",level.getGameTime()+(s.heavy?8:5));}
            }
            if(MeleeMoves.kind(s.stack).equals("axe")&&target.isAlive()){
                var armor=target.getAttribute(Attributes.ARMOR);
                if(armor!=null){armor.removeModifier(SHRED);armor.addTransientModifier(new AttributeModifier(SHRED,"Scarlet axe armor break",-4,AttributeModifier.Operation.ADDITION));target.getPersistentData().putLong("scarlet_shred_until",level.getGameTime()+100);}
            }
        }
        if(primary!=null){
            if(AshbringerCombat.matches(s.stack))AshbringerCombat.cleave(p,s.stack,primary,damage,s.heavy);
            level.sendParticles(s.heavy?ParticleTypes.CRIT:ParticleTypes.SWEEP_ATTACK,primary.getX(),primary.getY()+primary.getBbHeight()*.55,primary.getZ(),s.heavy?7:1,.15,.15,.15,.02);
            level.playSound(null,primary.blockPosition(),s.heavy?SoundEvents.PLAYER_ATTACK_CRIT:SoundEvents.PLAYER_ATTACK_STRONG,SoundSource.PLAYERS,.65f,s.heavy?.8f:1.15f);
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(()->p),new S2CMeleeMotionPacket(p.getUUID(),s.move.duration(),s.move.contact(),s.combo,s.heavy,true));
            if(weapon.hasDurability()&&!p.isCreative()){
                weapon.setDurability(s.stack,Math.max(0,weapon.getDurability(s.stack)-(s.heavy?2:1)));
                if(weapon.getDurability(s.stack)==0){level.playSound(null,p.blockPosition(),SoundEvents.ITEM_BREAK,SoundSource.PLAYERS,1,1);s.stack.shrink(1);}
            }
        }else{
            var block=CompatibilityFixes.clip(level,new ClipContext(eye,eye.add(look.scale(move.reach())),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
            if(block.getType()==HitResult.Type.BLOCK){
                var at=block.getBlockPos();
                if(MeleeMoves.quick(s.stack))me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnBlockKnifed",p.getUUID().toString(),at.getX(),at.getY(),at.getZ());
                if(level.getBlockState(at).getBlock() instanceof me.cryo.zombierool.block.system.GlassDefenseDoorBlock)me.cryo.zombierool.block.system.GlassDefenseDoorBlock.damage(level,at);
                level.playSound(null,at,SoundEvents.PLAYER_ATTACK_NODAMAGE,SoundSource.PLAYERS,.45f,.85f);
            }
        }
        Vec3 at=primary==null?eye.add(look.scale(move.reach())):primary.getBoundingBox().getCenter();
        me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnMeleeStrike",p.getUUID().toString(),at.x,at.y,at.z);
    }
    @SubscribeEvent public static void attack(net.minecraftforge.event.entity.player.AttackEntityEvent e) {
        if(e.getEntity().getMainHandItem().getItem() instanceof MeleeWeaponItem){e.setCanceled(true);if(e.getEntity() instanceof ServerPlayer p)request(p,false);}
    }
    /** Ground-level push: do not reuse vanilla knockback, which adds vertical velocity. */
    public static void pushHorizontal(Player player,LivingEntity target,double strength){
        if(EquipmentEffects.boss(target)||target instanceof me.cryo.zombierool.entity.SpecialEnemyEntity special&&special.isBoss()||MapActors.owns(target)&&MapKnifeRules.profile(target).getBoolean("boss"))return;
        var direction=target.position().subtract(player.position()).multiply(1,0,1);
        if(direction.lengthSqr()<.0001)direction=player.getLookAngle().multiply(1,0,1);
        direction=direction.normalize();var old=target.getDeltaMovement();
        var horizontal=new Vec3(old.x*.35+direction.x*strength,0,old.z*.35+direction.z*strength);
        if(horizontal.lengthSqr()>.85*.85)horizontal=horizontal.normalize().scale(.85);
        target.setDeltaMovement(horizontal.x,old.y,horizontal.z);target.hurtMarked=true;
    }
    @SubscribeEvent public static void recover(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e){
        var target=e.getEntity();if(target.level().isClientSide)return;
        var data=target.getPersistentData();
        if(data.contains("zr_melee_stagger_until")&&data.getLong("zr_melee_stagger_until")<=target.level().getGameTime()){
            var speed=target.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.removeModifier(STAGGER);data.remove("zr_melee_stagger_until");
        }
    }
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){SWINGS.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void respawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent e){SWINGS.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void dimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e){SWINGS.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e){SWINGS.clear();}
    private MeleeCombat(){}
}
