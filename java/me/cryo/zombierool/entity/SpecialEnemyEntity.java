package me.cryo.zombierool.entity;

import me.cryo.zombierool.gameplay.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

/** Separate entity types keep minions out of the regular wave quota and scaling. */
public class SpecialEnemyEntity extends AbstractZombieRoolEntity {
    private static final net.minecraft.network.syncher.EntityDataAccessor<org.joml.Vector3f> PLAGUE_DIRECTION=net.minecraft.network.syncher.SynchedEntityData.defineId(SpecialEnemyEntity.class,net.minecraft.network.syncher.EntityDataSerializers.VECTOR3);
    public Vec3 plagueDirection(){var v=entityData.get(PLAGUE_DIRECTION);return new Vec3(v.x,v.y,v.z);}
    public void plagueVisual(Vec3 direction){var v=direction==null?Vec3.ZERO:direction;entityData.set(PLAGUE_DIRECTION,new org.joml.Vector3f((float)v.x,(float)v.y,(float)v.z));}
    private static final net.minecraft.network.syncher.EntityDataAccessor<org.joml.Vector3f> HOOK=net.minecraft.network.syncher.SynchedEntityData.defineId(SpecialEnemyEntity.class,net.minecraft.network.syncher.EntityDataSerializers.VECTOR3);
    public Vec3 hookOffset(){var v=entityData.get(HOOK);return new Vec3(v.x,v.y,v.z);}
    public void hookVisual(Vec3 world){var v=world==null?Vec3.ZERO:world.subtract(position());entityData.set(HOOK,new org.joml.Vector3f((float)v.x,(float)v.y,(float)v.z));}
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> CROUCHED=net.minecraft.network.syncher.SynchedEntityData.defineId(SpecialEnemyEntity.class,net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> BOSS_ANIMATION=net.minecraft.network.syncher.SynchedEntityData.defineId(SpecialEnemyEntity.class,net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Long> ANIMATION_START=net.minecraft.network.syncher.SynchedEntityData.defineId(SpecialEnemyEntity.class,net.minecraft.network.syncher.EntityDataSerializers.LONG);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> ANIMATION_LENGTH=net.minecraft.network.syncher.SynchedEntityData.defineId(SpecialEnemyEntity.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(HOOK,new org.joml.Vector3f());entityData.define(PLAGUE_DIRECTION,new org.joml.Vector3f());entityData.define(PROFILE,new net.minecraft.nbt.CompoundTag());entityData.define(ARMOR,0);entityData.define(HEAT_RADIUS,12f);entityData.define(CROUCHED,false);entityData.define(BOSS_ANIMATION,"idle");entityData.define(ANIMATION_START,0L);entityData.define(ANIMATION_LENGTH,0);}
    public boolean isBossCrouched(){return entityData.get(CROUCHED);}
    @Override public EntityDimensions getDimensions(Pose pose){
        var flight=TraversalMotion.flight(this);if(flight!=null&&flight.style==2&&CrawlerLeap.isCrawler(this))return EntityDimensions.fixed(1.4f*getScale(),2.1f*getScale());
        return isBossCrouched()?EntityDimensions.scalable(.75f,1.95f):super.getDimensions(pose);
    }
    @Override protected net.minecraft.world.phys.AABB makeBoundingBox(){var box=super.makeBoundingBox();var flight=TraversalMotion.flight(this);return flight!=null&&flight.style==2&&CrawlerLeap.isCrawler(this)?box.move(0,-1.05*getScale(),0):box;}

    @Override public void onSyncedDataUpdated(net.minecraft.network.syncher.EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(CROUCHED.equals(key))refreshDimensions();}
    private void updateBossClearance(){
        if(!isBoss()||isDescending())return;
        // Plan for two-block passages, and duck before the helmet touches a low ceiling.
        float height=isPanzer()?2.8f:2.65f;
        var overhead=new net.minecraft.world.phys.AABB(getX()-.8,getY()+1.96,getZ()-.8,getX()+.8,getY()+height,getZ()+.8);
        boolean crouch=level().getBlockCollisions(this,overhead).iterator().hasNext();
        if(crouch!=isBossCrouched())entityData.set(CROUCHED,crouch);
    }
    public void playBossAnimation(String name,int ticks){if((isBoss()||isEncounter())&&!level().isClientSide){entityData.set(BOSS_ANIMATION,name);entityData.set(ANIMATION_START,level().getGameTime());entityData.set(ANIMATION_LENGTH,ticks);}}
    public String bossAnimation(){return level().getGameTime()-entityData.get(ANIMATION_START)<entityData.get(ANIMATION_LENGTH)?entityData.get(BOSS_ANIMATION):"idle";}
    public int bossAnimationDuration(){return entityData.get(ANIMATION_LENGTH);}
    public float bossAnimationAge(float partial){return Math.max(0,level().getGameTime()-entityData.get(ANIMATION_START)+partial);}
    private static final net.minecraft.network.syncher.EntityDataAccessor<net.minecraft.nbt.CompoundTag> PROFILE=net.minecraft.network.syncher.SynchedEntityData.defineId(SpecialEnemyEntity.class,net.minecraft.network.syncher.EntityDataSerializers.COMPOUND_TAG);
    public String encounterTemplate(){return entityData.get(PROFILE).getString("template");}
    public net.minecraft.nbt.CompoundTag bossProfile(){return entityData.get(PROFILE).copy();}
    public void setBossProfile(net.minecraft.nbt.CompoundTag profile){entityData.set(PROFILE,profile.copy());if(encounterTemplate().equals("astronaut")){
        var tag=getPersistentData();if(!tag.contains("zr_astronaut_name")&&!level().isClientSide){var names=new java.util.ArrayList<>(java.util.List.of(ASTRONAUT_NAMES));if(level() instanceof ServerLevel server)for(var player:server.getServer().getPlayerList().getPlayers())names.add(player.getGameProfile().getName());tag.putString("zr_astronaut_name",names.get(random.nextInt(names.size())));}
        setCustomName(net.minecraft.network.chat.Component.literal(tag.contains("zr_astronaut_name")?tag.getString("zr_astronaut_name"):"Cosmonaut"));
    }else setCustomName(net.minecraft.network.chat.Component.translatable(profile.getString("name")));setCustomNameVisible(!encounterTemplate().equals("abomination"));addTag("zr_boss");}
    private static final String[] ASTRONAUT_NAMES=("xXSnip3rXx,xX_NoSc0pe_Xx,iTz_Skillz_97,II TrickShot II,oG x Reaper,zZ_DarkSnipez_Zz,xX-MLG-420-Xx,FaZeFan1337,OpTicFan360,xXxD4rkAngelxXx,XxN0obSlay3rxX,FR x LeSniper,xX_Prestige10,II NoSk1ll II,LeBgDu59,KevinDu93,Killerdu59,B3nJii_1996,TheChosenOne07,aVi3w_NoSc0pe,iTz x Legend,xX_T0mii_Xx,UnluckySniper69,xxDemonSlayerxx,Dark_Kevin_95,RoYaL x Ghost,SnIp3rDu974,II RaZoR II,MrQuickScope,MLG x Pancake,xX_HeadSh0t_Xx,RaGe_QuiT_360,Pr3stigeMaster,TehNoobSlayer,iiTz_Sm0k3,LeGaminDu77,St4rKiller92,Xx_DuTy_Xx,ProGamerDu62,Silent_Killer07,D4rkAssassinFR,NoSc0pe4Life,IDropShotU,MrPrestige10,xX_Z0mbie_Xx,TuMeReviveStp,iTz x Vortex,Re4per_1994,BrotherInArms9,II VENOM II,xX_Interv3ntion,xX_RayGun_Xx,TheRealNoobFR,LeRoiDuCut,Tr1ckSh0t_95,xXBlackOpsXx,iAmTheHost360,HostMigratePlz,LagSwitchOff,MW2Veteran09,PSN_Overkill,LeSniperDu13,NiNjA x Reflex,Z0mbieTrainz,xxLastStandxx,xX_M4GNUM_Xx,iiLegendary,GoLd x Falcon,TheSilentOne93,FR x Maverick,ReViVe_Me_Bro,OneShot_OneKill,CoD4Veteran07,xX_Clutch_360,Elite_Spartan117,UnKnOwN x Pro,II iCeMaN II,RaGe x Phoenix,TheNoScopeKid,LeBgDuLive,xX_MoonWalker_Xx,OneSmallStep_FR,ApolloKiller11,xX_NoOxyg3n_Xx,II LunarSniper II,MoonBase_Camper,Gr4vity_Off,xX_ToTheMoon_Xx,xX_SpaceMar1ne,DarkSid3OfMoon,LaLuneDu77,Astro_Kevin_93,CosmoSniper59,RichtofenFan,Dr_Richt0fen_FR,xX_Samantha_Xx,PackAPunch_Me,WunderWaffe_DG2,ThunderGun_Plz,xX_GershDev_Xx,xX_QuantumSn1p3r,ZombieBait_97,OxygenTank_Low,LeBgDeLaLune,xX_Ap0ll0_13_Xx,HoustonWeHaveLag,LagOnTheMoon,xX_Armstr0ng_Xx,BuzzAldr1n_FR,Gagarine_1961,MajorTomDu59,GroundControlFR,Lunatic_Kevin,BeamMeUp_Plz,SpaceCowboy93,AstroNoob_360,FreshMeat_92,Medic_Plz_Bro,MicSpamDu78,JeCamp_TuCamp,RageQuitDu94,Tonton_Kevin_95,MamieZombie_Xx,LePatronDu69,Tryhardeur_974,TaMereLaCampe,JeSuisUnBot,NoMicNoProblem,Hugo_x_Killer,Th0masD4rkFR,xX_Dylan_2k7_Xx,Enzo_ProPlayer,OnLeVoitPasVenir,xX_LunaTik_Xx,Biodome_Boy,MeteorShower_FR,Excavat0r_Pro,Tunnel11_Rat,NoAirNoProblem,xX_MoonBoot_Xx,Tyler_1994,BigBoss_Mike,Chris_the_Beast,Trevor2011,DeathStalker77,CodGod_Brandon,Brit_Sniper_UK,Chav_Killer_88,Bloody_Nora,Lil_Wayne_Fan,Mountain_Dew_Joe,Doritos_Sniper,HaloVeteran07,Sgt_Slaughter,Rambo_1987,Terminator_T800,Dark_Knight_11,Zombie_Hunter_8,Undead_Slayer_13,MoonMan_Dave,Spaceman_Spiff,Lt_Dan_Gaming,DerSniper_DE,Hans_Gamer_DE,Kartoffel_King,Fraulein_Killer,Blitzkrieg_Max,Bratwurst_Boy,ElChapo_Gamer,Pablito_Pro,LaMuerte_Roja,Jose_Sniper_MX,Tacos_N_Kills,El_Zombie_Loco,Senor_Camper,HueHueBR,Joao_BR_Pro,Zueira_Total,Brasileiro_Pro,BR_Sniper_2011,Il_Cecchino,Mario_Sniper_IT,Pizza_Killer_IT,Luigi_Camper,Mamma_Mia_Kill,Il_Boss_Napoli,Polski_Snajper,Kielbasa_Kill,Comrade_Sniper,Vodka_Warrior,Ivan_Zombie_RU,Ivan_The_Camper,Siberian_Wolf_7,Viking_Sven_SE,Odin_Sniper_NO,Hollandse_Pro,Tulip_Killer_NL,Sakura_Sniper,Ninja_Takeshi,Ryu_Hayabusa_X,Tokyo_Drift_JP,Samurai_Jack_93,Aussie_Mate_Pro,Kangaroo_Kid_AU,Maple_Sniper_CA,Poutine_Killer,Bogan_Slayer,Guest_20483,User_72910,Anonymous_7421,Noob_123456,asdfghjkl,EpicGamerMoment,ChuckNorris_Fan,NotAHacker_Pls,Trololo_Guy,Bro_Do_You_Even,MomGetTheCamera,Nikolai_Belinski,Tank_Dempsey_Fan,Takeo_Masaki,Gersh_Device_Pro,Lunar_Lander_Pro,NASA_Intern_2011,Moon_Cheese_Boy,II Hunter II,xX Sn1pz Xx,Ice Cold Jay,Deadly Mike 94,Big Bad Wolf 7,TheRealMVP,Sgt Pepper 360,Epic Fail Dave").split(",");
    public final CrawlerLeap crawlerLeap=new CrawlerLeap(this);
    public final EncounterAbilities encounter=new EncounterAbilities(this);
    @Override public boolean canAttack(LivingEntity target){return (encounter==null||encounter.mayTarget(target))&&super.canAttack(target);}
    @Override public void setTarget(LivingEntity target){if(target==null||encounter==null||encounter.mayTarget(target))super.setTarget(target);}
    public boolean isEncounter(){return EncounterAbilities.handles(this);}
    public boolean legacyBoss(){return isBoss()&&!isEncounter();}
    private int abilityCooldown=100,stunTicks;
    private float helmetDamage,coverDamage,coreDamage,explosiveDamage;
    private int helmetHits;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> ARMOR=net.minecraft.network.syncher.SynchedEntityData.defineId(SpecialEnemyEntity.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
    public boolean hasBossHelmet(){return (entityData.get(ARMOR)&1)==0;}
    public boolean hasCoreCover(){return (entityData.get(ARMOR)&2)==0;}
    public boolean hasPowerCore(){return (entityData.get(ARMOR)&4)==0;}
    private double setting(String key){return level() instanceof ServerLevel server?me.cryo.zombierool.config.SpecialEnemyConfig.get(server).getDouble(key):me.cryo.zombierool.config.SpecialEnemyConfig.defaults().getDouble(key);}
    private void breakArmor(int flag){if((entityData.get(ARMOR)&flag)!=0)return;if(flag==1&&!isPanzer())sound(me.cryo.zombierool.init.ZombieroolModSounds.BRUTUS_HELMET.get());entityData.set(ARMOR,entityData.get(ARMOR)|flag);if(level() instanceof ServerLevel)me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnBossArmorBroken",getUUID().toString(),kind(),flag==1?"helmet":flag==2?"reactor_cover":"reactor_core");releaseCapture();grabWindup=flameTicks=0;stunTicks=Math.max(stunTicks,flag==4?80:20);playBossAnimation("stagger",stunTicks);if(level() instanceof ServerLevel server)server.sendParticles(ParticleTypes.CRIT,getX(),getY()+getBbHeight()*.8,getZ(),24,.35,.25,.35,.3);}
    public boolean teleporterShock(){releaseCapture();grabWindup=flameTicks=0;stunTicks=60;playBossAnimation("stagger",60);return super.hurt(damageSources().generic(),getMaxHealth()*(float)setting("teleporterDamage"));}

    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> HEAT_RADIUS=net.minecraft.network.syncher.SynchedEntityData.defineId(SpecialEnemyEntity.class,net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    public float heatRadius(){return entityData.get(HEAT_RADIUS);}
    private boolean napalmAlert;
    private int napalmSpawnWave,napalmFuse=-1;
    private boolean napalmEnraged;
    public void markNapalmSpawnWave(int wave){napalmSpawnWave=wave;}
    public boolean isNapalmEnraged(){return napalmEnraged;}
    private java.util.UUID captured;
    private int pullTicks,holdTicks,grabWindup,flameTicks;
    private Vec3 shotImpact;
    private Entity shotSource;
    private long shotTick;
    public void recordShotImpact(Vec3 hit,Entity source){shotImpact=hit;shotSource=source;shotTick=level().getGameTime();}
    public Vec3 grappleOrigin(){return position().add(new Vec3(-.65,getBbHeight()*.6,.25).yRot(-getYRot()*(float)Math.PI/180));}
    public Vec3 flameOrigin(){return position().add(new Vec3(.65,getBbHeight()*.6,.25).yRot(-getYRot()*(float)Math.PI/180));}
    public Vec3 flameDirection(){var target=getTarget();return target!=null?target.getEyePosition().subtract(flameOrigin()).normalize():getLookAngle();}
    private Vec3 holdPosition,aimDirection;
    public boolean hasCapturedPlayer(){return captured!=null;}
    public boolean capture(net.minecraft.server.level.ServerPlayer player){
        if(me.cryo.zombierool.block.system.TeleporterTravel.bossProtected(player)||stunTicks>0||captured!=null||!isAlive()||!isPanzer()||player.getPersistentData().hasUUID("zr_panzer_owner"))return false;
        captured=player.getUUID();pullTicks=60;holdTicks=(int)setting("panzerHoldTicks");holdPosition=null;
        player.getPersistentData().putUUID("zr_panzer_owner",getUUID());
        me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->player),new me.cryo.zombierool.network.packet.S2CPanzerCapturePacket(true));me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnPanzerGrab",getUUID().toString(),player.getUUID().toString());return captured!=null;
    }
    public void releaseCapture(){
        if(captured!=null&&level() instanceof ServerLevel server){var player=server.getServer().getPlayerList().getPlayer(captured);if(player!=null){player.getPersistentData().remove("zr_panzer_owner");me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->player),new me.cryo.zombierool.network.packet.S2CPanzerCapturePacket(false));}}
        var previousCapture=captured;captured=null;holdPosition=null;if(previousCapture!=null&&!level().isClientSide)me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnPanzerRelease",getUUID().toString(),previousCapture.toString());
    }
    public void launchClaw(Vec3 direction){var claw=me.cryo.zombierool.init.ZombieroolModEntities.PANZER_CLAW.get().create(level());if(claw!=null){claw.launch(this,direction);level().addFreshEntity(claw);}}
    private boolean tickCapture(ServerLevel server){
        if(captured==null)return false;
        var player=server.getServer().getPlayerList().getPlayer(captured);
        if(player==null||me.cryo.zombierool.block.system.TeleporterTravel.bossProtected(player)||!player.isAlive()||player.isCreative()||player.isSpectator()||player.level()!=level()||me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(player.getUUID())||EquipmentEffects.active(this)||WonderWeaponEffects.frozen(this)||FiestaFreeze.frozen(this)){releaseCapture();return false;}
        getNavigation().stop();setDeltaMovement(0,getDeltaMovement().y,0);
        if(holdPosition==null){
            if(--pullTicks<=0||!hasLineOfSight(player)){releaseCapture();return true;}
            if(distanceToSqr(player)<=3.24){holdPosition=player.position();playBossAnimation("flame",60);sound(me.cryo.zombierool.init.ZombieroolModSounds.PANZER_FLAME.get());}
            else{var step=position().subtract(player.position()).normalize().scale(.55);if(!server.noCollision(player,player.getBoundingBox().move(step))){releaseCapture();return true;}var next=player.position().add(step);player.connection.teleport(next.x,next.y,next.z,player.getYRot(),player.getXRot());player.setDeltaMovement(Vec3.ZERO);return true;}
        }
        if(--holdTicks<=0){releaseCapture();return true;}
        if(player.position().distanceToSqr(holdPosition)>.0064)player.connection.teleport(holdPosition.x,holdPosition.y,holdPosition.z,player.getYRot(),player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        emitFlames(server,false);
        if(holdTicks%15==0)FixedPlayerDamage.panzerFlame(player,this,(float)setting("panzerFlameDamage"));
        return true;
    }
    private void shakeNearby(ServerLevel server,int duration,float strength,double radius){for(var player:server.players())if(player.isAlive()&&!player.isSpectator()&&distanceToSqr(player)<radius*radius){float weight=(float)(1-Math.sqrt(distanceToSqr(player))/radius);me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->player),new me.cryo.zombierool.network.packet.S2CScreenShakePacket(duration,strength*weight*(float)setting("screenShake"),me.cryo.zombierool.client.ScreenShakeHandler.ShakeType.IMPACT));}}
    private int descentTicks;
    private int descentDuration=48;
    private Vec3 landing;
    private double descentStartY;
    public void beginDescent(){
        landing=position();
        descentStartY=Math.max(getY()+48,level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,blockPosition().getX(),blockPosition().getZ())+24);
        descentTicks=descentDuration=48;noPhysics=true;setNoGravity(true);
        setPos(landing.x,descentStartY,landing.z);setDeltaMovement(Vec3.ZERO);playBossAnimation("spawn",48);sound(me.cryo.zombierool.init.ZombieroolModSounds.PANZER_BOOST.get());
    }
    public boolean isDescending(){return descentTicks>0;}
    public Vec3 spawnLanding(){return isDescending()&&landing!=null?landing:position();}
    public SpecialEnemyEntity(EntityType<? extends SpecialEnemyEntity> type,Level level){
        super(type,level);
        getPersistentData().putBoolean("zombierool:no_gore",true);
        if(isBoss())addTag("zr_boss");
    }
    public String kind(){var key=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(getType());return entityData.get(PROFILE).contains("id")?entityData.get(PROFILE).getString("id"):key==null?"skeleton_minion":key.getPath();}
    public boolean canPerformBossAction(){return isBoss()&&isAlive()&&!isNoAi()&&!isDescending()&&stunTicks==0&&!hasCapturedPlayer()&&grabWindup==0&&flameTicks==0&&!bossAnimation().equals("spawn")&&!bossAnimation().equals("land")&&!EquipmentEffects.active(this)&&!WonderWeaponEffects.frozen(this)&&!FiestaFreeze.frozen(this)&&!TraversalMotion.active(this);}
    public boolean isBoss(){return kind().equals("panzer")||kind().equals("brutus")||kind().equals("custom_mini_boss")||entityData.get(PROFILE).contains("id");}
    public boolean isPanzer(){return kind().equals("panzer")||encounterTemplate().equals("panzer");}
    public boolean isNapalm(){return kind().equals("napalm_minion");}
    public static AttributeSupplier.Builder attributes(double health,double speed,double damage){
        return net.minecraft.world.entity.monster.Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,health).add(Attributes.MOVEMENT_SPEED,speed).add(Attributes.ATTACK_DAMAGE,damage).add(Attributes.FOLLOW_RANGE,64).add(Attributes.KNOCKBACK_RESISTANCE,1);
    }
    public float uncappedBossHealth(){
        var health=getAttribute(Attributes.MAX_HEALTH);if(health==null)return -1;
        double base=health.getBaseValue();for(var mod:health.getModifiers(AttributeModifier.Operation.ADDITION))base+=mod.getAmount();
        double total=base;for(var mod:health.getModifiers(AttributeModifier.Operation.MULTIPLY_BASE))total+=base*mod.getAmount();
        for(var mod:health.getModifiers(AttributeModifier.Operation.MULTIPLY_TOTAL))total*=1+mod.getAmount();
        return (float)Math.max(1,Math.min(1_000_000,total));
    }
    @Override protected void registerGoals(){
        goalSelector.addGoal(0,new me.cryo.zombierool.gameplay.BossJumpLinkGoal(this));
        goalSelector.addGoal(1,new me.cryo.zombierool.gameplay.BrutusMachineLocks.LockGoal(this));
        goalSelector.addGoal(2,new MeleeAttackGoal(this,1,true){@Override public boolean canUse(){return (encounter==null||!encounter.avoidsMelee())&&super.canUse();}@Override public boolean canContinueToUse(){return (encounter==null||!encounter.avoidsMelee())&&super.canContinueToUse();}});
        goalSelector.addGoal(5,new LookAtPlayerGoal(this,Player.class,12));
        targetSelector.addGoal(1,new me.cryo.zombierool.hotfix.ReachablePlayerTargetGoal(this,Player.class,false,false));
    }
    @Override public void knockback(double strength,double x,double z){}
    @Override public void push(double x,double y,double z){}
    @Override public MobType getMobType(){return MobType.UNDEAD;}
    private void sound(net.minecraft.sounds.SoundEvent sound){level().playSound(null,blockPosition(),sound,net.minecraft.sounds.SoundSource.HOSTILE,.9f,1);}
    @Override protected void playStepSound(BlockPos pos,net.minecraft.world.level.block.state.BlockState state){if(EncounterAbilities.kind(this).equals("george_romero")){level().playSound(null,getX(),getY(),getZ(),me.cryo.zombierool.init.ZombieroolModSounds.encounter(isInWater()?"romero_water_step":"romero_step"),net.minecraft.sounds.SoundSource.HOSTILE,.5f,1);}else if(!isBoss())super.playStepSound(pos,state);}
    @Override protected net.minecraft.sounds.SoundEvent getAmbientSound(){if(isEncounter())return me.cryo.zombierool.gameplay.EncounterAudio.sound(this,"idle");return isNapalm()?me.cryo.zombierool.init.ZombieroolModSounds.NAPALM_IDLE.get():isBoss()?null:net.minecraft.sounds.SoundEvents.SKELETON_AMBIENT;}
    @Override protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source){return isEncounter()&&EncounterAudio.allowHurt(this)?me.cryo.zombierool.gameplay.EncounterAudio.sound(this,"hurt"):null;}
    @Override protected net.minecraft.sounds.SoundEvent getDeathSound(){return isEncounter()?me.cryo.zombierool.gameplay.EncounterAudio.sound(this,"death"):isPanzer()?me.cryo.zombierool.init.ZombieroolModSounds.PANZER_DEATH.get():isBoss()?me.cryo.zombierool.init.ZombieroolModSounds.BRUTUS_DEATH.get():isNapalm()?me.cryo.zombierool.init.ZombieroolModSounds.NAPALM_DEATH.get():net.minecraft.sounds.SoundEvents.SKELETON_DEATH;}
    @Override public boolean doHurtTarget(Entity target){if(isEncounter())return level() instanceof ServerLevel&&encounter.attack(target);if(isNapalm()){if(target instanceof Player&&!isInWater())armNapalm();return false;}if(isBoss()){if(stunTicks>0)return false;if((bossAnimation().equals("spawn")||bossAnimation().equals("land"))||isPanzer()&&(hasCapturedPlayer()||grabWindup>0||flameTicks>0))return false;playBossAnimation("melee",isPanzer()?31:28);sound(isPanzer()?me.cryo.zombierool.init.ZombieroolModSounds.PANZER_MELEE.get():me.cryo.zombierool.init.ZombieroolModSounds.BRUTUS_MELEE.get());}return super.doHurtTarget(target);}
    @Override protected boolean isCorpseTarget(){return EncounterAbilities.kind(this).equals("geist");}
    @Override protected void tickDeath(){
        if(EncounterAbilities.kind(this).equals("geist")){
            if(tickFeedingCorpse())return;
            int corpseAge=getPersistentData().getInt("zr_geist_corpse_age")+1;getPersistentData().putInt("zr_geist_corpse_age",corpseAge);
            if(getPersistentData().getBoolean("zr_geist_gas_pending")){
                if(corpseAge<45){settleCorpse();deathTime=Math.min(19,deathTime+1);return;}
                getPersistentData().remove("zr_geist_gas_pending");
                if(level() instanceof ServerLevel l){CrawlerEntity.CrawlerGasManager.addGasCloud(l,position(),240);l.sendParticles(ParticleTypes.LARGE_SMOKE,getX(),getY()+.4,getZ(),20,.4,.4,.4,.04);l.playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,net.minecraft.sounds.SoundSource.HOSTILE,.8f,1.5f);}
            }
            super.tickDeath();return;
        }
        deathTime++;
        if(isPanzer()&&level() instanceof ServerLevel server&&deathTime>=8&&deathTime<=40&&deathTime%8==0){
            double x=getX()+(random.nextDouble()-.5)*1.2,y=getY()+.3+random.nextDouble()*1.8,z=getZ()+(random.nextDouble()-.5)*1.2;
            server.sendParticles(ParticleTypes.EXPLOSION,x,y,z,1,0,0,0,0);server.sendParticles(ParticleTypes.SMOKE,x,y,z,12,.2,.3,.2,.05);
            server.playSound(null,x,y,z,net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,net.minecraft.sounds.SoundSource.HOSTILE,.8f,1.1f+random.nextFloat()*.3f);
        }
        int end=isEncounter()?switch(EncounterAbilities.kind(this)){case "george_romero"->134;case "geist"->60;case "abomination"->47;default->41;}:isBoss()?60:20;
        if(deathTime>=end&&!level().isClientSide)discard();
    }
    @Override public boolean fireImmune(){return isPanzer()||isNapalm();}
    @Override public boolean hurt(DamageSource source,float amount){
        if(fireImmune()&&source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE))return false;
        boolean head=false,reactor=false,leftArm=false;float armorDamage=0;
        if(legacyBoss()&&!source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)){
            Vec3 hit=null;Entity direct=source.getDirectEntity();
            if(shotTick==level().getGameTime()&&(direct==shotSource||source.getEntity()==shotSource))hit=shotImpact;
            if(hit==null&&direct instanceof net.minecraft.world.entity.projectile.Projectile p)hit=getBoundingBox().clip(new Vec3(p.xo,p.yo,p.zo),p.position().add(p.getDeltaMovement())).orElse(p.position());
            if(hit==null&&source.getEntity() instanceof Player player)hit=getBoundingBox().clip(player.getEyePosition(),player.getEyePosition().add(player.getLookAngle().scale(100))).orElse(null);
            head=hit!=null&&(hit.y-getY())/getBbHeight()>=.82;
            if(hit==null)head=getPersistentData().getBoolean(me.cryo.zombierool.core.manager.DamageManager.HEADSHOT_TAG);
            reactor=isPanzer()&&isReactorHit(hit);
            if(hit!=null){Vec3 local=hit.subtract(position()).yRot(yBodyRot*(float)Math.PI/180);leftArm=isPanzer()&&local.x<-.35&&(hit.y-getY())/getBbHeight()>.3;}
            armorDamage=amount*(float)setting("bodyDamage");
            amount*=head&&!hasBossHelmet()?1f:(float)setting("bodyDamage");
        }
        boolean accepted=super.hurt(source,amount);
        if(accepted&&isEncounter()&&isAlive())encounter.provoke();
        if(accepted&&legacyBoss()&&isAlive()&&!source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)){
            if(head&&hasBossHelmet()){
                if(isPanzer()){helmetDamage+=armorDamage;if(helmetDamage>=getMaxHealth()*.1f)breakArmor(1);}
                else if(++helmetHits>=5)breakArmor(1);
            }
            if(source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)){explosiveDamage+=armorDamage;if(hasBossHelmet()&&explosiveDamage>=getMaxHealth()*.1f)breakArmor(1);if(isPanzer()&&hasCapturedPlayer()&&explosiveDamage>=getMaxHealth()*.1f)releaseCapture();}
            if(reactor){if(hasCoreCover()){coverDamage+=armorDamage;if(coverDamage>=getMaxHealth()*.05f)breakArmor(2);}else if(hasPowerCore()){coreDamage+=armorDamage;if(coreDamage>=getMaxHealth()*.025f)breakArmor(4);}}
            if(leftArm&&source.getEntity() instanceof Player player&&player.getUUID().equals(captured)){releaseCapture();stunTicks=Math.max(stunTicks,20);}
        }

        if(accepted&&isNapalm()&&isAlive()&&source.getEntity() instanceof Player&&WaveManager.getCurrentWave()>napalmSpawnWave&&!napalmEnraged){napalmEnraged=true;getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(setting("napalmEnragedSpeed"));sound(me.cryo.zombierool.init.ZombieroolModSounds.NAPALM_CHARGE.get());}
        return accepted;
    }
    public boolean isWeakPoint(Vec3 hit){return hit!=null&&((hit.y-getY())/getBbHeight()>=.82||isPanzer()&&isReactorHit(hit));}
    private boolean isReactorHit(Vec3 hit){
        if(hit==null)return false;
        double height=(hit.y-getY())/getBbHeight();

        Vec3 local=hit.subtract(position()).yRot(yBodyRot*(float)Math.PI/180);
        return height>=.43&&height<=.64&&Math.abs(local.x)<=.29&&local.z>.2;
    }
    private void emitFlames(ServerLevel server,boolean damage){
        Vec3 origin=flameOrigin(),direction=flameDirection();
        Vec3 end=origin.add(direction.scale(8));
        var clip=server.clip(new net.minecraft.world.level.ClipContext(origin,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,this));
        double length=origin.distanceTo(clip.getLocation());
        for(double d=.3;d<length;d+=.45){Vec3 point=origin.add(direction.scale(d));double spread=.06+d*.07;server.sendParticles(ParticleTypes.FLAME,point.x,point.y,point.z,6,spread,spread,spread,.035);if(tickCount%3==0)server.sendParticles(ParticleTypes.SMOKE,point.x,point.y,point.z,2,spread,spread,spread,.025);}
        if(damage&&flameTicks==40||!damage&&holdTicks==(int)setting("panzerHoldTicks")-1)sound(me.cryo.zombierool.init.ZombieroolModSounds.PANZER_FLAME_LOOP.get());
        if(damage&&tickCount%10==0)for(var player:server.players()){
            if(!player.isAlive()||player.isCreative()||player.isSpectator()||me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(player.getUUID())||me.cryo.zombierool.block.system.TeleporterTravel.bossProtected(player))continue;
            Vec3 relative=player.getBoundingBox().getCenter().subtract(origin);double along=relative.dot(direction);
            if(along>=0&&along<=length&&relative.subtract(direction.scale(along)).length()<.6+along*.09&&hasLineOfSight(player))FixedPlayerDamage.panzerFlame(player,this,(float)setting("panzerFlameDamage"));
        }
    }
    @Override public void tick(){
        if(!level().isClientSide)updateBossClearance();
        if(stunTicks>0&&isAlive()){if(!WaveManager.isPausedByPlayer())stunTicks--;getNavigation().stop();setDeltaMovement(0,getDeltaMovement().y,0);super.tick();getNavigation().stop();setDeltaMovement(0,getDeltaMovement().y,0);return;}
        // Special enemies must not be recycled into the regular zombie queue.
        stuckTimer=0;
        if(!isBoss()&&!level().isClientSide&&getPersistentData().getBoolean("zr_special_spawner")&&!WaveManager.isGameRunning()){discard();return;}
        if(descentTicks>0 && level() instanceof ServerLevel server){
            getNavigation().stop();setDeltaMovement(Vec3.ZERO);
            if(WaveManager.isPausedByPlayer())return;
            super.tick();setDeltaMovement(Vec3.ZERO);
            double fraction=(descentDuration- --descentTicks)/(double)descentDuration;
            setPos(landing.x,descentStartY+(landing.y-descentStartY)*fraction*fraction,landing.z);
            fallDistance=0;
            if(descentTicks==0){playBossAnimation("land",28);server.sendParticles(ParticleTypes.EXPLOSION_EMITTER,getX(),getY()+.3,getZ(),1,0,0,0,0);shakeNearby(server,32,9f,32);noPhysics=false;setNoGravity(false);setOnGround(true);server.sendParticles(ParticleTypes.POOF,getX(),getY()+.1,getZ(),24,.7,.1,.7,.04);server.playSound(null,blockPosition(),me.cryo.zombierool.init.ZombieroolModSounds.PANZER_LAND.get(),net.minecraft.sounds.SoundSource.HOSTILE,2,1);}
            return;
        }
        super.tick();
        if(TraversalMotion.active(this))return;
        if(getEmergenceTicks()>0){getNavigation().stop();setDeltaMovement(Vec3.ZERO);return;}
        if(isBoss()&&(bossAnimation().equals("spawn")||bossAnimation().equals("land"))){getNavigation().stop();setDeltaMovement(0,getDeltaMovement().y,0);return;}
        if(!(level() instanceof ServerLevel server)||!isAlive()||WaveManager.isPausedByPlayer())return;
        if(isNapalm()&&onGround()&&getDeltaMovement().horizontalDistanceSqr()>.0001&&tickCount%18==0)sound(me.cryo.zombierool.init.ZombieroolModSounds.NAPALM_STEP.get());
        if(isNapalm()&&!isInWater()&&tickCount%5==0)server.sendParticles(ParticleTypes.FLAME,getX(),getY()+.8,getZ(),5,.25,.6,.25,.015);
        if(isNapalm()){
            entityData.set(HEAT_RADIUS,(float)setting("napalmWarningRadius"));tickNapalm(server);
            if(!isAlive())return;
            if(!isInWater()&&tickCount%40==0)server.playSound(null,blockPosition(),me.cryo.zombierool.init.ZombieroolModSounds.NAPALM_LOOP.get(),net.minecraft.sounds.SoundSource.HOSTILE,.5f,1);
            if(!isInWater()&&tickCount%10==0){boolean nearby=server.players().stream().anyMatch(p->p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(p.getUUID())&&distanceToSqr(p)<Math.pow(setting("napalmWarningRadius"),2)&&hasLineOfSight(p));if(nearby&&!napalmAlert)sound(me.cryo.zombierool.init.ZombieroolModSounds.NAPALM_CHARGE.get());napalmAlert=nearby;}
        }
        if(isEncounter()){encounter.tick(server);return;}
        if(!isBoss()||EquipmentEffects.active(this)||WonderWeaponEffects.frozen(this)||FiestaFreeze.frozen(this)){if(hasCapturedPlayer())releaseCapture();grabWindup=0;flameTicks=0;return;}
        if(onGround()&&getDeltaMovement().horizontalDistanceSqr()>.001&&tickCount%(isPanzer()?7:9)==0){shakeNearby(server,10,isPanzer()?2.8f:2.2f,isPanzer()?22:20);sound(isPanzer()?me.cryo.zombierool.init.ZombieroolModSounds.PANZER_STEP.get():me.cryo.zombierool.init.ZombieroolModSounds.BRUTUS_STEP.get());}
        if(getPersistentData().getBoolean("zr_boss_link")||getPersistentData().getBoolean("zr_boss_machine"))return;
        if(isPanzer()){
            if(tickCapture(server))return;
            if(flameTicks>0){getNavigation().stop();setDeltaMovement(0,getDeltaMovement().y,0);emitFlames(server,true);if(--flameTicks==0)sound(me.cryo.zombierool.init.ZombieroolModSounds.PANZER_FLAME_END.get());return;}
            if(grabWindup>0){getNavigation().stop();var prey=getTarget();if(prey!=null){double lead=Math.min(8,distanceTo(prey)/setting("clawSpeed"));aimDirection=prey.getEyePosition().add(prey.getDeltaMovement().scale(lead)).subtract(grappleOrigin()).normalize();}if(--grabWindup==0)launchClaw(aimDirection);return;}
        }
        if(--abilityCooldown>0)return;
        var target=getTarget();
        if(!(target instanceof Player player)||!player.isAlive()||player.isCreative()||player.isSpectator()||me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(player.getUUID())||!hasLineOfSight(player))return;
        double distance=distanceToSqr(player);
        if(isPanzer()){
            if(distance>9&&distance<256){
                aimDirection=player.getEyePosition().subtract(getEyePosition()).normalize();grabWindup=(int)setting("clawWindup");
                sound(me.cryo.zombierool.init.ZombieroolModSounds.PANZER_GRAB.get());playBossAnimation("grab",40);
                abilityCooldown=120;
            }else if(distance<=36){
                flameTicks=40;
                sound(me.cryo.zombierool.init.ZombieroolModSounds.PANZER_FLAME.get());
                playBossAnimation("flame",40);
                abilityCooldown=80;
            }
        }else if(distance<25){
            playBossAnimation("slam",30);shakeNearby(server,20,4f,20);
            server.sendParticles(ParticleTypes.EXPLOSION,getX(),getY()+.1,getZ(),8,1,.1,1,0);
            server.playSound(null,blockPosition(),me.cryo.zombierool.init.ZombieroolModSounds.BRUTUS_SLAM.get(),net.minecraft.sounds.SoundSource.HOSTILE,1,.7f);
            for(var p:server.players())if(p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(p.getUUID())&&distanceToSqr(p)<16&&hasLineOfSight(p)){
                p.hurt(damageSources().mobAttack(this),3);

            }
            abilityCooldown=120;
        }
    }
    private void armNapalm(){if(napalmFuse<0){napalmFuse=20;sound(me.cryo.zombierool.init.ZombieroolModSounds.NAPALM_ATTACK.get());}}
    private void tickNapalm(ServerLevel server){
        if(isInWater()){napalmFuse=-1;napalmAlert=false;return;}
        for(var player:server.players()){
            if(!player.isAlive()||player.isCreative()||player.isSpectator()||me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(player.getUUID())||!hasLineOfSight(player))continue;
            if(distanceToSqr(player)<=Math.pow(setting("napalmDetonationRadius"),2))armNapalm();
            if(distanceToSqr(player)<=9&&tickCount%20==0)player.hurt(damageSources().hotFloor(),1);
        }
        if(napalmFuse>=0){getNavigation().stop();setDeltaMovement(0,getDeltaMovement().y,0);server.sendParticles(ParticleTypes.FLAME,getX(),getY()+1,getZ(),10,.3,.5,.3,.04);if(--napalmFuse<=0){setHealth(0);die(damageSources().generic());}}
    }
    @Override public void die(DamageSource source){
        releaseCapture();
        boolean firstDeath=!dead;
        if(firstDeath&&EncounterAbilities.kind(this).equals("geist")&&level() instanceof ServerLevel l&&!CrawlerLeap.enabled(l)&&me.cryo.zombierool.config.WorldConfig.get(l).isCrawlerGasExplosion()){
            boolean explosive=source.is(net.minecraft.world.damagesource.DamageTypes.EXPLOSION)||getPersistentData().getBoolean("zombierool:explosive_damage");boolean melee=source.getDirectEntity() instanceof Player&&!getPersistentData().getBoolean(me.cryo.zombierool.core.manager.DamageManager.GUN_DAMAGE_TAG)&&!explosive;
            if(CrawlerGasPolicy.shouldExplode(explosive,melee,getPersistentData().getBoolean(me.cryo.zombierool.core.manager.DamageManager.HEADSHOT_TAG),random.nextFloat()))getPersistentData().putBoolean("zr_geist_gas_pending",true);
        }
        if(firstDeath&&isEncounter()){if(EncounterAbilities.kind(this).equals("pentagon_thief")){EncounterAudio.play(this,"thief_defeated",1);encounter.rewardCleanThiefKill();}if(EncounterAbilities.kind(this).equals("astronaut")&&level() instanceof ServerLevel server)encounter.astronautBurst(server,true);encounter.returnWeapons();}
        if(firstDeath&&isBoss())playBossAnimation("death",100);boolean explode=isNapalm()&&firstDeath;
        if(firstDeath&&isNapalm()&&level() instanceof ServerLevel server&&WaveManager.isGameRunning())NapalmSpawnRules.died(server);
        super.die(source);
        if(firstDeath&&isBoss())MiniBossLoot.killed(this,source);
        if(firstDeath&&!isBoss()&&level() instanceof ServerLevel)me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnMinionKilled",getUUID().toString(),kind());
        if(firstDeath&&kind().equals("skeleton_minion")&&level() instanceof ServerLevel skeletonLevel){for(int i=0;i<10;i++)GorePieceEntity.spawn(skeletonLevel,this,i==0?GorePieceEntity.BONE_SKULL:i<4?GorePieceEntity.BONE_RIB:GorePieceEntity.BONE,0,"");skeletonLevel.playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.SKELETON_DEATH,net.minecraft.sounds.SoundSource.HOSTILE,1,.8f);}
        if(explode && level() instanceof ServerLevel server){
            server.sendParticles(ParticleTypes.EXPLOSION_EMITTER,getX(),getY()+.6,getZ(),1,0,0,0,0);
            server.sendParticles(isInWater()?ParticleTypes.CLOUD:ParticleTypes.FLAME,getX(),getY()+.5,getZ(),100,1,.5,1,.12);
            server.playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,net.minecraft.sounds.SoundSource.HOSTILE,2,.8f);
            for(var player:server.players())if(player.isAlive()&&!player.isCreative()&&!player.isSpectator()&&!me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(player.getUUID())&&distanceToSqr(player)<25&&hasLineOfSight(player))FixedPlayerDamage.napalmExplosion(player,(float)(1-distanceTo(player)/5)*(isInWater()?.25f:1));
            var flames=me.cryo.zombierool.init.ZombieroolModEntities.NAPALM_FIRE.get().create(server);if(flames!=null&&!isInWater()){flames.setPos(position());server.addFreshEntity(flames);}

        }
    }
    @Override public void remove(RemovalReason reason){if(isEncounter()&&reason==RemovalReason.DISCARDED)encounter.returnWeapons();releaseCapture();super.remove(reason);}
    @Override public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag){super.addAdditionalSaveData(tag);tag.put("MiniBossProfile",bossProfile());tag.put("EncounterRuntime",encounter.save());tag.putInt("BossArmor",entityData.get(ARMOR));tag.putFloat("HelmetDamage",helmetDamage);tag.putFloat("CoverDamage",coverDamage);tag.putFloat("CoreDamage",coreDamage);tag.putFloat("ExplosiveDamage",explosiveDamage);tag.putInt("HelmetHits",helmetHits);tag.putInt("StunTicks",stunTicks);tag.putInt("NapalmSpawnWave",napalmSpawnWave);tag.putInt("NapalmFuse",napalmFuse);tag.putBoolean("NapalmEnraged",napalmEnraged);tag.putInt("AbilityCooldown",abilityCooldown);tag.putInt("DescentTicks",descentTicks);tag.putInt("DescentDuration",descentDuration);if(landing!=null){tag.putDouble("LandingX",landing.x);tag.putDouble("LandingY",landing.y);tag.putDouble("LandingZ",landing.z);tag.putDouble("DescentStartY",descentStartY);}}
    @Override public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag){super.readAdditionalSaveData(tag);encounter.load(tag.getCompound("EncounterRuntime"));if(tag.contains("MiniBossProfile",10)&&tag.getCompound("MiniBossProfile").contains("id"))setBossProfile(tag.getCompound("MiniBossProfile"));entityData.set(ARMOR,tag.getInt("BossArmor"));helmetDamage=tag.getFloat("HelmetDamage");coverDamage=tag.getFloat("CoverDamage");coreDamage=tag.getFloat("CoreDamage");explosiveDamage=tag.getFloat("ExplosiveDamage");helmetHits=tag.getInt("HelmetHits");stunTicks=tag.getInt("StunTicks");napalmSpawnWave=tag.contains("NapalmSpawnWave")?tag.getInt("NapalmSpawnWave"):WaveManager.getCurrentWave();napalmFuse=tag.contains("NapalmFuse")?tag.getInt("NapalmFuse"):-1;napalmEnraged=tag.getBoolean("NapalmEnraged");if(napalmEnraged)getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(setting("napalmEnragedSpeed"));abilityCooldown=tag.contains("AbilityCooldown")?Math.max(0,tag.getInt("AbilityCooldown")):100;descentDuration=tag.contains("DescentDuration")?Math.max(1,tag.getInt("DescentDuration")):40;descentTicks=Math.max(0,Math.min(descentDuration,tag.getInt("DescentTicks")));if(descentTicks>0){landing=new Vec3(tag.getDouble("LandingX"),tag.getDouble("LandingY"),tag.getDouble("LandingZ"));descentStartY=tag.getDouble("DescentStartY");noPhysics=true;setNoGravity(true);playBossAnimation("spawn",descentTicks);}}
}
