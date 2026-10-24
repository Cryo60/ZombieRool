package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.core.registry.ZRThrowableRegistry;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import java.util.UUID;

/** One synchronized physical device, with no inventory stack owned by its user. */
public final class EquipmentEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Integer> KIND=SynchedEntityData.defineId(EquipmentEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> LANDED=SynchedEntityData.defineId(EquipmentEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ARMED=SynchedEntityData.defineId(EquipmentEntity.class,EntityDataSerializers.BOOLEAN);
    private UUID attached;private Vec3 offset=Vec3.ZERO;
    private int age,fuse=-1,landAge=-1;
    public EquipmentEntity(EntityType<? extends EquipmentEntity> type,Level level){super(type,level);}
    public EquipmentEntity(Level level,LivingEntity owner,EquipmentSystem.Kind kind){super(ZRThrowableRegistry.EQUIPMENT_ENTITY.get(),owner,level);entityData.set(KIND,kind.ordinal());fuse=kind==EquipmentSystem.Kind.SEMTEX?40:kind.slot==2?30:-1;}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(KIND,0);entityData.define(LANDED,false);entityData.define(ARMED,false);}
    public EquipmentSystem.Kind kind(){return EquipmentSystem.Kind.values()[Math.floorMod(entityData.get(KIND),EquipmentSystem.Kind.values().length)];}
    public boolean landed(){return entityData.get(LANDED);}
    public boolean armed(){return landed()&&entityData.get(ARMED);}
    public UUID ownerId(){return getOwner()!=null?getOwner().getUUID():getPersistentData().hasUUID("zr_device_owner")?getPersistentData().getUUID("zr_device_owner"):null;}
    public int remainingFuse(){return fuse;}
    public void requestDetonation(){if(fuse<0)fuse=5;}
    public void land(){entityData.set(LANDED,true);entityData.set(ARMED,false);setNoGravity(true);setDeltaMovement(Vec3.ZERO);landAge=age;}
    @Override protected Item getDefaultItem(){return ZRThrowableRegistry.loadoutItem(kind());}
    @Override public boolean isPickable(){return false;}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @Override public void tick(){
        if(!landed())super.tick();else {xo=getX();yo=getY();zo=getZ();yRotO=getYRot();xRotO=getXRot();tickCount++;}
        if(!(level() instanceof ServerLevel level))return;
        age++;
        if(landed()&&age-landAge>=16)entityData.set(ARMED,true);
        if(getOwner()!=null)getPersistentData().putUUID("zr_device_owner",getOwner().getUUID());
        if(attached!=null){var victim=level.getEntity(attached);if(victim!=null&&!victim.isRemoved())setPos(victim.position().add(offset));else{attached=null;entityData.set(LANDED,false);setNoGravity(false);}}
        if(fuse>0&&--fuse==0){if(kind().slot==2)EquipmentEffects.burst(level,position(),kind(),ownerId());else explode();discard();return;}
        if(fuse<0&&armed()&&(kind()==EquipmentSystem.Kind.CLAYMORE||kind()==EquipmentSystem.Kind.BETTY)&&age%2==0){
            boolean found=level.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(kind()==EquipmentSystem.Kind.CLAYMORE?5:3),EquipmentEffects::enemy).stream().anyMatch(this::triggeredBy);
            if(found){EquipmentSystem.sound(level,position(),kind()==EquipmentSystem.Kind.CLAYMORE?"claymore_trigger":"mine_trigger",.65f);if(kind()==EquipmentSystem.Kind.BETTY){entityData.set(LANDED,false);setNoGravity(false);setDeltaMovement(0,.32,0);fuse=10;}else fuse=3;}
        }
        if(kind()==EquipmentSystem.Kind.SEMTEX&&age%8==0)EquipmentSystem.sound(level,position(),"semtex_tick",.35f);
        if(armed()&&age%12==0)level.sendParticles(ParticleTypes.ELECTRIC_SPARK,getX(),getY()+.12,getZ(),1,0,0,0,0);
        if(getY()<level.getMinBuildHeight()-16||age>24000)discard();
    }
    private boolean triggeredBy(LivingEntity e){
        Vec3 delta=e.position().subtract(position());if(Math.abs(delta.y)>1.7||!visible(e.getEyePosition()))return false;
        if(kind()==EquipmentSystem.Kind.BETTY)return delta.lengthSqr()<=9;
        Vec3 forward=Vec3.directionFromRotation(0,getYRot());return delta.horizontalDistanceSqr()<=25&&delta.normalize().dot(forward)>.45;
    }
    private boolean visible(Vec3 to){return level().clip(new ClipContext(position().add(0,.15,0),to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;}
    @Override protected void onHitBlock(BlockHitResult hit){
        if(level().isClientSide)return;
        if(kind()==EquipmentSystem.Kind.C4||kind()==EquipmentSystem.Kind.SEMTEX){setPos(hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.03)));setXRot(hit.getDirection().getAxis()==net.minecraft.core.Direction.Axis.Y?0:90);land();EquipmentSystem.sound((ServerLevel)level(),position(),kind()==EquipmentSystem.Kind.SEMTEX?"semtex_stick":"equipment_stick",.5f);}
        else{var motion=getDeltaMovement();switch(hit.getDirection().getAxis()){case X->motion=new Vec3(-motion.x,motion.y,motion.z);case Y->motion=new Vec3(motion.x,-motion.y,motion.z);case Z->motion=new Vec3(motion.x,motion.y,-motion.z);}setPos(hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.04)));setDeltaMovement(motion.scale(.3));if(getDeltaMovement().lengthSqr()<.025&&hit.getDirection()==net.minecraft.core.Direction.UP)land();}
    }
    @Override protected void onHitEntity(EntityHitResult hit){
        if(level().isClientSide||hit.getEntity()==getOwner())return;
        if(kind()==EquipmentSystem.Kind.SEMTEX){attached=hit.getEntity().getUUID();offset=hit.getLocation().subtract(hit.getEntity().position());setPos(hit.getLocation());land();EquipmentSystem.sound((ServerLevel)level(),position(),"semtex_stick",.5f);}
        else setDeltaMovement(getDeltaMovement().scale(-.25));
    }
    public void explode(){
        if(!(level() instanceof ServerLevel level)||isRemoved())return;
        Vec3 pos=position();float radius=kind()==EquipmentSystem.Kind.CLAYMORE?5:5.5f;
        PortableEquipment.blast(level,pos,radius);
        EquipmentSystem.sound(level,pos,kind()==EquipmentSystem.Kind.CLAYMORE?"claymore_explode":kind()==EquipmentSystem.Kind.SEMTEX?"semtex_explode":"equipment_explode",1);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,pos.x,pos.y+.15,pos.z,1,0,0,0,0);
        var source=getOwner() instanceof Player p?level.damageSources().playerAttack(p):level.damageSources().explosion(this,getOwner());
        for(var e:level.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(radius),EquipmentEffects::enemy)){
            double distance=e.position().distanceTo(pos);if(distance>radius||!visible(e.getEyePosition()))continue;
            if(kind()==EquipmentSystem.Kind.CLAYMORE&&e.position().subtract(pos).normalize().dot(Vec3.directionFromRotation(0,getYRot()))<.2)continue;
            e.getPersistentData().putBoolean("zombierool:explosive_damage",true);
            me.cryo.zombierool.core.manager.DamageManager.applyDamage(e,source,(float)(500*(1-.7*distance/radius)));
            if(!e.isAlive()&&getOwner() instanceof net.minecraft.server.level.ServerPlayer owner)me.cryo.zombierool.career.CareerManager.progressGameplay(owner,me.cryo.zombierool.career.CareerManager.ChallengeType.EQUIPMENT_KILLS,1);
        }
        if((kind()==EquipmentSystem.Kind.C4||kind()==EquipmentSystem.Kind.SEMTEX)&&getOwner() instanceof Player p&&p.distanceToSqr(pos)<radius*radius&&visible(p.getEyePosition()))FixedPlayerDamage.selfExplosion(p);
        me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnExplosion",ownerId()==null?"":ownerId().toString(),pos.x,pos.y,pos.z,(double)radius);
        discard();
    }
    @Override public void addAdditionalSaveData(CompoundTag n){super.addAdditionalSaveData(n);n.putInt("Kind",kind().ordinal());n.putInt("Age",age);n.putInt("Fuse",fuse);n.putInt("LandAge",landAge);n.putBoolean("Landed",landed());if(attached!=null){n.putUUID("Attached",attached);n.putDouble("OffsetX",offset.x);n.putDouble("OffsetY",offset.y);n.putDouble("OffsetZ",offset.z);}}
    @Override public void readAdditionalSaveData(CompoundTag n){super.readAdditionalSaveData(n);entityData.set(KIND,n.getInt("Kind"));age=n.getInt("Age");fuse=n.getInt("Fuse");landAge=n.getInt("LandAge");entityData.set(LANDED,n.getBoolean("Landed"));setNoGravity(landed());if(n.hasUUID("Attached")){attached=n.getUUID("Attached");offset=new Vec3(n.getDouble("OffsetX"),n.getDouble("OffsetY"),n.getDouble("OffsetZ"));}}
}
