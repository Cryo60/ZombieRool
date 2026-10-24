package local.hotfixtest;

import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.gameplay.EquipmentSystem.Kind;
import me.cryo.zombierool.core.capability.ZombieCapabilitySystem;
import me.cryo.zombierool.core.registry.ZRThrowableRegistry;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.init.*;
import me.cryo.zombierool.config.WorldConfig;
import net.minecraft.server.level.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import java.util.*;

public final class EquipmentGameplayTest {
    private static final class DamageProbe extends net.minecraftforge.common.util.FakePlayer {
        DamageProbe(ServerLevel level){super(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"EquipmentDamageProbe"));}
        @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source){return false;}
    }
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;System.out.println("EQUIPMENT CHECK: "+message);}
    private static me.cryo.zombierool.entity.ZombieEntity zombie(ServerLevel l,Vec3 p){var z=ZombieroolModEntities.ZOMBIE.get().create(l);z.setPos(p);z.setEmergenceTicks(0);z.getPersistentData().remove("zr_emerge_no_ai");z.setNoAi(false);z.setOnGround(true);z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);z.setHealth(1024);z.getPersistentData().putBoolean("zombierool:no_gore",true);l.addFreshEntity(z);return z;}
    private static EquipmentEntity device(ServerLevel l,ServerPlayer owner,Kind k,Vec3 p){var e=new EquipmentEntity(l,owner,k);e.setPos(p);l.addFreshEntity(e);return e;}
    private static long advance(ServerLevel l,int ticks){long old=l.getGameTime();l.getServer().getWorldData().overworldData().setGameTime(old+ticks);return old;}
    private static void restore(ServerLevel l,long old){l.getServer().getWorldData().overworldData().setGameTime(old);}
    public static void run(ServerLevel level,ServerPlayer a,ServerPlayer b)throws Exception{
        for(var sound:List.of("claymore_draw","claymore_place","claymore_trigger","claymore_explode","semtex_draw","semtex_throw","semtex_stick","semtex_tick","semtex_explode"))check(net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.containsKey(new net.minecraft.resources.ResourceLocation("zombierool",sound)),"Registered T5 sound "+sound);
        a.setGameMode(GameType.ADVENTURE);b.setGameMode(GameType.ADVENTURE);a.setHealth(a.getMaxHealth());b.setHealth(b.getMaxHealth());
        for(int x=190;x<230;x++)for(int z=90;z<111;z++){level.setBlock(new BlockPos(x,78,z),ZombieroolModBlocks.PATH.get().defaultBlockState(),3);level.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);for(int y=80;y<86;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        a.setPos(200.5,80,100.5);a.setYRot(0);a.setXRot(40);b.setPos(220.5,80,100.5);
        var cap=a.getCapability(ZombieCapabilitySystem.Provider.PLAYER_DATA).orElseThrow(()->new AssertionError("player capability"));
        for(var k:Kind.values()){
            var item=ZRThrowableRegistry.loadoutItem(k);check(item instanceof EquipmentSystem.LoadoutItem,"Registered virtual "+k.id);
            a.getInventory().clearContent();WeaponFacade.grantWeaponToPlayer(a,new ItemStack(item));
            check(a.getInventory().isEmpty(),k.id+" purchase does not occupy the inventory");
            check(k.slot==1?cap.getEquipmentCount()==2:k.slot==2?cap.getTacticalCount()==2:cap.getLethalCount()==5,k.id+" fills the appropriate independent slot");
        }
        cap.setEquipmentType("zombierool:claymore");cap.setEquipmentCount(2);cap.setTacticalType("zombierool:flash");cap.setTacticalCount(1);
        var saved=cap.serializeNBT();var copied=new ZombieCapabilitySystem.Impl();copied.deserializeNBT(saved);check(copied.getEquipmentType().equals("zombierool:claymore")&&copied.getEquipmentCount()==2&&copied.getTacticalType().equals("zombierool:flash")&&copied.getTacticalCount()==1,"Independent equipment and tactical slots survive persistence");
        var cloned=new ZombieCapabilitySystem.Impl();cloned.copyFrom(cap);check(cloned.getEquipmentCount()==2&&cloned.getTacticalCount()==1,"Respawn capability copy preserves all slots");
        var legacy=new ZombieCapabilitySystem.Impl();legacy.deserializeNBT(new CompoundTag());check(legacy.getEquipmentCount()==0&&legacy.getTacticalCount()==0,"Old player data does not invent equipment");
        cap.setLethalType("zombierool:grenade");cap.setLethalCount(2);
        me.cryo.zombierool.handlers.LethalWeaponManager.handleStateChange(a,1);me.cryo.zombierool.handlers.LethalWeaponManager.handleStateChange(a,2);
        var grenades=level.getEntitiesOfClass(me.cryo.zombierool.item.throwable.Grenade.GrenadeEntity.class,new AABB(a.position(),a.position()).inflate(4));
        check(cap.getLethalCount()==1&&grenades.isEmpty(),"Classic grenade release consumes once and waits for the throwing arm");
        long throwingTime=advance(level,4);me.cryo.zombierool.handlers.LethalWeaponManager.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->false,level.getServer()));
        check(level.getEntitiesOfClass(me.cryo.zombierool.item.throwable.Grenade.GrenadeEntity.class,new AABB(a.position(),a.position()).inflate(4)).isEmpty(),"Grenade stays in the animated hand for four ticks");
        advance(level,1);me.cryo.zombierool.handlers.LethalWeaponManager.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->false,level.getServer()));
        grenades=level.getEntitiesOfClass(me.cryo.zombierool.item.throwable.Grenade.GrenadeEntity.class,new AABB(a.position(),a.position()).inflate(4));
        check(grenades.size()==1&&grenades.get(0).getFuse()==95,"Physical grenade leaves at tick five, with fuse time preserved");grenades.forEach(Entity::discard);restore(level,throwingTime);me.cryo.zombierool.handlers.LethalWeaponManager.cancel(a);
        cap.setEquipmentCount(0);cap.setTacticalCount(0);cap.setLethalCount(0);
        var actionsField=me.cryo.zombierool.bonuses.BonusManager.class.getDeclaredField("ACTIONS");actionsField.setAccessible(true);
        @SuppressWarnings("unchecked") var actions=(Map<String,me.cryo.zombierool.bonuses.BonusManager.BonusAction>)actionsField.get(null);
        actions.get("max_ammo").apply(a,level,a.position(),null);
        check(cap.getEquipmentCount()==2&&cap.getTacticalCount()==2&&cap.getLethalCount()==5,"Actual Max Ammo pickup restores equipment, tactical and lethal charges");
        var wallPos=new BlockPos(198,80,100);level.setBlock(wallPos,me.cryo.zombierool.block.system.BuyWallWeaponSystem.BLOCK.get().defaultBlockState(),3);
        var wall=(me.cryo.zombierool.block.system.BuyWallWeaponSystem.BuyWallWeaponBlockEntity)level.getBlockEntity(wallPos);wall.setPrice(100);
        var buy=me.cryo.zombierool.handlers.ServerInteractionHandler.class.getDeclaredMethod("handleWallWeapon",ServerPlayer.class,ServerLevel.class,BlockPos.class);buy.setAccessible(true);
        for(var k:Kind.values()){
            EquipmentSystem.equip(a,k);wall.setItem(0,new ItemStack(ZRThrowableRegistry.loadoutItem(k)));
            if(k.slot==1)cap.setEquipmentCount(0);else if(k.slot==2)cap.setTacticalCount(0);else cap.setLethalCount(0);
            PointManager.setScore(a,1000);buy.invoke(null,a,level,wallPos);
            check(PointManager.getScore(a)==1000&&(k.slot==1?cap.getEquipmentCount()==0:k.slot==2?cap.getTacticalCount()==0:cap.getLethalCount()==0),"Wall cannot sell more ammunition for equipped "+k.id);
        }
        cap.setEquipmentType("zombierool:claymore");cap.setEquipmentCount(2);cap.setTacticalType("zombierool:flash");cap.setTacticalCount(1);
        EquipmentSystem.input(a,1,1);check(EquipmentSystem.busy(a.getUUID())&&cap.getEquipmentCount()==2,"Holding Claymore neither places nor consumes it");
        EquipmentSystem.input(a,0,0);check(!EquipmentSystem.busy(a.getUUID())&&cap.getEquipmentCount()==2,"Canceling a held mine consumes no charge");
        EquipmentSystem.input(a,1,1);EquipmentSystem.input(a,1,2);check(cap.getEquipmentCount()==1,"Releasing Claymore reserves exactly one charge");
        long old=advance(level,10);EquipmentSystem.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->false,level.getServer()));restore(level,old);
        var placed=new ArrayList<EquipmentEntity>();for(var e:level.getAllEntities())if(e instanceof EquipmentEntity d)placed.add(d);
        check(placed.stream().anyMatch(d->d.kind()==Kind.CLAYMORE&&d.landed()),"Release animation places a physical Claymore on the floor");placed.forEach(Entity::discard);EquipmentSystem.cancel(a);
        var c4=device(level,a,Kind.C4,new Vec3(204,80,100));c4.land();for(int i=0;i<16;i++)c4.tick();
        var foreign=device(level,b,Kind.C4,new Vec3(207,80,100));foreign.land();for(int i=0;i<16;i++)foreign.tick();
        check(EquipmentSystem.detonateOwned(a),"Second C4 action finds the owner's armed charge");
        for(int i=0;i<4;i++)c4.tick();check(!c4.isRemoved(),"C4 waits for the detonator button press in the animation");c4.tick();check(c4.isRemoved()&&!foreign.isRemoved(),"C4 detonation leaves another player's charge intact");foreign.discard();
        var semtex=device(level,a,Kind.SEMTEX,new Vec3(209,81,100));
        var victim=zombie(level,new Vec3(210,80,100));
        var hit=EquipmentEntity.class.getDeclaredMethod("onHitEntity",EntityHitResult.class);hit.setAccessible(true);hit.invoke(semtex,new EntityHitResult(victim,victim.position().add(0,1,0)));
        victim.setPos(211,80,100);semtex.tick();check(semtex.landed()&&Math.abs(semtex.getX()-211)<.01,"Semtex follows a moving zombie after sticking");
        for(int i=1;i<39;i++)semtex.tick();check(!semtex.isRemoved()&&semtex.remainingFuse()==1,"Semtex remains live before exactly two seconds");semtex.tick();check(semtex.isRemoved(),"Semtex explodes at tick 40");victim.discard();
        var blockSemtex=device(level,a,Kind.SEMTEX,new Vec3(209,82,103));
        var blockHit=EquipmentEntity.class.getDeclaredMethod("onHitBlock",BlockHitResult.class);blockHit.setAccessible(true);blockHit.invoke(blockSemtex,new BlockHitResult(new Vec3(209,80,103),Direction.UP,new BlockPos(209,79,103),false));
        var start=blockSemtex.position();blockSemtex.tick();check(blockSemtex.landed()&&blockSemtex.position().distanceToSqr(start)<.0001,"Semtex remains fixed on its block");blockSemtex.discard();
        var mine=device(level,a,Kind.CLAYMORE,new Vec3(200,80,100));mine.land();mine.setYRot(0);a.setPos(200,80,102);float health=a.getHealth();
        for(int i=0;i<18;i++)mine.tick();check(mine.remainingFuse()==-1,"Players do not trigger Claymore");
        var tracked=mine.getEntityData().packDirty();var remote=new EquipmentEntity(ZRThrowableRegistry.EQUIPMENT_ENTITY.get(),level);remote.getEntityData().assignValues(tracked);
        check(remote.armed(),"Tracked Claymore arming is visible to a client entity without its private server age");
        var behind=zombie(level,new Vec3(200,80,98));mine.tick();mine.tick();check(mine.remainingFuse()==-1,"Claymore rejects an enemy behind it");
        var front=zombie(level,new Vec3(200,80,102));for(int i=0;i<5;i++)mine.tick();check(mine.isRemoved()&&front.getHealth()<1024&&a.getHealth()==health&&behind.getHealth()==1024,"Claymore damages front enemies only, never players or enemies behind it");front.discard();behind.discard();
        var betty=device(level,a,Kind.BETTY,new Vec3(206,80,103));betty.land();var target=zombie(level,new Vec3(207,80,103));for(int i=0;i<18;i++)betty.tick();double y=betty.getY();check(betty.remainingFuse()>0&&!betty.landed(),"Bouncing Betty launches before exploding");for(int i=0;i<4;i++)betty.tick();check(betty.getY()>y,"Betty explosive charge rises above the floor");for(int i=0;i<12&&!betty.isRemoved();i++)betty.tick();check(betty.isRemoved()&&target.getHealth()<1024,"Betty's airborne charge detonates against nearby enemies");target.discard();
        var blind=zombie(level,new Vec3(210,80,100));blind.setTarget(a);EquipmentEffects.apply(blind,Kind.FLASH);check(EquipmentEffects.active(blind)&&EquipmentEffects.flashed(blind)&&blind.getTarget()==null,"Flash clears pursuit and activates blindness");blind.setTarget(a);check(blind.getTarget()==null,"Target reassignment cannot override Flash");
        old=advance(level,79);check(EquipmentEffects.flashed(blind),"Blindness lasts through tick 79");advance(level,1);check(!EquipmentEffects.active(blind),"Blindness ends at four seconds");restore(level,old);blind.discard();
        var angry=zombie(level,new Vec3(210,80,103));var other=zombie(level,new Vec3(211,80,103));EquipmentEffects.apply(angry,Kind.PHEROMONE);angry.setTarget(a);check(angry.getTarget()==null,"Pheromone prevents pursuit of players");angry.goalSelector.tick();check(angry.getTarget()==other,"Pheromone selects another zombie to attack");check(other.getHealth()<1024,"Pheromone zombie actually attacks another zombie");old=advance(level,99);check(EquipmentEffects.active(angry),"Pheromone lasts through tick 99");advance(level,1);check(!EquipmentEffects.active(angry),"Pheromone expires after five seconds");restore(level,old);angry.discard();other.discard();
        var boss=zombie(level,new Vec3(217,80,103));boss.addTag("zr_boss");double speed=boss.getAttributeValue(Attributes.MOVEMENT_SPEED);EquipmentEffects.apply(boss,Kind.PHEROMONE);check(!EquipmentEffects.active(boss),"Pheromone does not affect bosses");EquipmentEffects.apply(boss,Kind.FLASH);check(!EquipmentEffects.active(boss)&&boss.getAttributeValue(Attributes.MOVEMENT_SPEED)<speed,"Flash slows bosses without blinding them");old=advance(level,80);EquipmentEffects.expireSlow(boss);check(Math.abs(boss.getAttributeValue(Attributes.MOVEMENT_SPEED)-speed)<.00001,"Boss movement speed returns after four seconds");restore(level,old);boss.discard();
        var config=WorldConfig.get(level);var snapshot=new CompoundTag();config.saveEditable(snapshot);config.setStartingEquipment("zombierool:bouncing_betty");config.setStartingTactical("zombierool:pheromone");EquipmentSystem.resetLoadout(a,cap);check(cap.getEquipmentType().equals("zombierool:bouncing_betty")&&cap.getEquipmentCount()==2&&cap.getTacticalType().equals("zombierool:pheromone")&&cap.getTacticalCount()==2,"New match loads map-configured equipment and tactical slots");config.loadEditable(snapshot);
        var cleanup=device(level,a,Kind.C4,new Vec3(220,80,100));DroppedItemCleanup.clear(level);check(cleanup.isRemoved(),"Match reset removes deployed devices");
        a.setPos(205,80,105);a.setYRot(90);a.setYHeadRot(90);a.setXRot(0);EquipmentEffects.burst(level,new Vec3(204,80.2,105),Kind.FLASH,a.getUUID());
        check(a.getPersistentData().getLong("zr_player_flash_until")==level.getGameTime()+80,"Own Flash sends a four-second player blindness effect");
        a.getPersistentData().remove("zr_player_flash_until");a.setYRot(-90);a.setYHeadRot(-90);
        EquipmentEffects.burst(level,new Vec3(204,80.2,105),Kind.FLASH,a.getUUID());
        check(!a.getPersistentData().contains("zr_player_flash_until"),"Turning away before detonation prevents own Flash blindness");
        a.setYRot(90);a.setYHeadRot(90);
        a.getPersistentData().remove("zr_player_flash_until");for(int wallY=80;wallY<84;wallY++)level.setBlock(new BlockPos(204,wallY,105),Blocks.STONE.defaultBlockState(),3);
        EquipmentEffects.burst(level,new Vec3(203,80.2,105),Kind.FLASH,a.getUUID());check(!a.getPersistentData().contains("zr_player_flash_until"),"A solid wall shields the player from own Flash");
        for(int wallY=80;wallY<84;wallY++)level.setBlock(new BlockPos(204,wallY,105),Blocks.AIR.defaultBlockState(),3);
        var damagePlayer=new DamageProbe(level);level.addNewPlayer(damagePlayer);damagePlayer.setGameMode(GameType.SURVIVAL);damagePlayer.setInvulnerable(false);damagePlayer.getAbilities().invulnerable=false;
        var spawnShield=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");spawnShield.setAccessible(true);spawnShield.setInt(damagePlayer,0);damagePlayer.setPos(205,80,105);damagePlayer.setHealth(damagePlayer.getMaxHealth());
        var self=device(level,damagePlayer,Kind.SEMTEX,new Vec3(201,80.2,105));self.land();float before=damagePlayer.getHealth();self.explode();
        check(damagePlayer.getHealth()<before,"Own Semtex damages its owner four blocks away, beyond the former three-block limit");
        damagePlayer.getPersistentData().remove("zr_last_self_exp");damagePlayer.addEffect(new net.minecraft.world.effect.MobEffectInstance(ZombieroolModMobEffects.PERKS_EFFECT_PHD_FLOPPER.get(),100,0));before=damagePlayer.getHealth();self=device(level,damagePlayer,Kind.SEMTEX,new Vec3(204,80.2,105));self.land();self.explode();
        check(damagePlayer.getHealth()==before,"PhD retains self-explosion immunity for Semtex");damagePlayer.discard();
        var gun=WeaponFacade.createWeaponStack("zombierool:m1014",false,a);check(!gun.isEmpty()&&WeaponFacade.getDefinition(gun)!=null&&WeaponFacade.getDefinition(gun).tacz.gun_id.equals("tacz:m1014"),"Native M1014 definition and TacZ mapping exist");
        System.out.println("EQUIPMENT GAMEPLAY PASS: "+checks+" assertions");
    }
}
