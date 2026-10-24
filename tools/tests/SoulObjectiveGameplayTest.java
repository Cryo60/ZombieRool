package local.hotfixtest;

import java.util.*;
import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.entity.*;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.init.*;
import me.cryo.zombierool.core.capability.ZombieCapabilitySystem;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** Real deaths, feeding ticks, redstone, persistence, chunk return and wave recharge. */
public final class SoulObjectiveGameplayTest {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;System.out.println("SOUL OBJECTIVE CHECK: "+message);}
    private static void tick(SoulChestSystem.Chest chest,int ticks)throws Exception{var m=chest.getClass().getDeclaredMethod("tick");m.setAccessible(true);for(int i=0;i<ticks;i++)m.invoke(chest);}
    private static ZombieEntity kill(ServerLevel l,ServerPlayer player,Vec3 p){var z=ZombieroolModEntities.ZOMBIE.get().create(l);z.setPos(p);z.setEmergenceTicks(0);z.getPersistentData().putBoolean("zombierool:no_gore",true);l.addFreshEntity(z);z.hurt(l.damageSources().playerAttack(player),10000);if(z.isAlive())throw new AssertionError("Fixture zombie survived");return z;}
    public static void run(ServerLevel level,ServerPlayer player)throws Exception{
        var game=WaveManager.class.getDeclaredField("gameRunning");game.setAccessible(true);game.setBoolean(null,true);
        for(int x=638;x<=658;x++)for(int z=-4;z<=4;z++)level.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);
        BlockPos chestPos=new BlockPos(640,80,0),dogPos=new BlockPos(648,81,0),logicPos=new BlockPos(655,80,0);
        level.setBlock(chestPos,MapAuthorSystem.SOUL_CHEST.get().defaultBlockState(),3);var chest=(SoulChestSystem.Chest)level.getBlockEntity(chestPos);chest.onLoad();
        level.setBlock(dogPos,MapAuthorSystem.CERBERUS.get().defaultBlockState(),3);var dog=(CerberusSystem.Head)level.getBlockEntity(dogPos);dog.onLoad();
        check(!chest.objectiveId.isBlank()&&!dog.objectiveId.isBlank()&&!chest.objectiveId.equals(dog.objectiveId),"Both placed objective kinds automatically receive different IDs");
        check(chest.objectiveId.matches("S[0-9]+")&&dog.objectiveId.matches("C[0-9]+"),"Automatic IDs are short letters and numbers without coordinates");
        String automatic=chest.objectiveId;chest.onLoad();check(chest.objectiveId.equals(automatic),"Loading the same objective preserves its automatic ID");
        var legacyConfig=chest.config();String legacyId="soul_640_80_0";legacyConfig.putString("objectiveId",legacyId);chest.configure(legacyConfig);chest.onLoad();
        check(chest.objectiveId.matches("S[0-9]+")&&!chest.objectiveId.equals(legacyId),"Old coordinate-based default IDs migrate to short IDs");
        chest.completed=true;SoulObjectiveRegistry.get(level).update(chest);check(SoulObjectiveRegistry.get(level).complete(legacyId),"Existing checkers keep resolving old coordinate IDs after migration");
        var migrated=SoulObjectiveRegistry.get(level).save(new CompoundTag());var registryLoad=SoulObjectiveRegistry.class.getDeclaredMethod("load",CompoundTag.class);registryLoad.setAccessible(true);
        check(((SoulObjectiveRegistry)registryLoad.invoke(null,migrated)).complete(legacyId),"Legacy ID aliases survive saved-data reloads");
        var c=chest.config();c.putString("objectiveId","chest_1");c.putInt("souls",1);c.putDouble("radius",2);chest.configure(c);
        c=dog.config();c.putString("objectiveId","dog_1");c.putInt("souls",2);c.putDouble("radius",8);c.putInt("chewTime",1);c.putInt("rewardChannel",8);dog.configure(c);WaveManager.lockChannel(8);
        var registry=SoulObjectiveRegistry.get(level);
        var lua=new me.cryo.zombierool.scripting.ZombieroolAPI(level);var globals=me.cryo.zombierool.shadow.luaj.vm2.lib.jse.JsePlatform.standardGlobals();globals.set("ZombieroolAPI",me.cryo.zombierool.shadow.luaj.vm2.lib.jse.CoerceJavaToLua.coerce(lua));
        globals.load("assert(ZombieroolAPI:getSoulObjective('dog_1').kind == 'cerberus', 'kind='..tostring(ZombieroolAPI:getSoulObjective('dog_1').kind)); assert(not ZombieroolAPI:isSoulObjectiveComplete('dog_1'), 'unfinished'); assert(ZombieroolAPI:configureSoulObjective('dog_1', {enabled=false}), 'configure disabled'); assert(ZombieroolAPI:getSoulObjective('dog_1').enabled == false, 'enabled='..tostring(ZombieroolAPI:getSoulObjective('dog_1').enabled)); assert(ZombieroolAPI:configureSoulObjective('dog_1', {enabled=true}))", "objective-api").call();
        check(dog.enabled&&dog.required==2&&dog.radius==8,"Actual Lua scripts query IDs and patch objective settings without erasing omitted fields");
        globals.load("assert(not ZombieroolAPI:configureSoulObjective('missing', {enabled=false})); assert(ZombieroolAPI:getEnemyStatus('invalid-uuid').alive == nil)","missing-api").call();
        check(lua.listSoulObjectives().length()>=2,"Lua objective enumeration includes both kinds");
        check(registry.unique("chest_1")&&registry.unique("dog_1"),"Chest and Cerberus IDs use the same registry");
        level.setBlock(logicPos,MapAuthorSystem.COMPLETION.get().defaultBlockState(),3);var logic=(SoulCompletionSystem.Checker)level.getBlockEntity(logicPos);logic.onLoad();
        c=logic.config();c.putString("objectiveIds","chest_1, dog_1");c.putString("enableChannels","27");c.putString("disableChannels","28");c.putInt("channelDuration",1);WaveManager.lockChannel(27);WaveManager.unlockChannel(28);logic.configure(c);check(!logic.getBlockState().getValue(BlockStateProperties.POWERED),"Checker stays OFF while the required objectives are unfinished");
        player.setPos(640.5,80,-1.5);var chestBody=kill(level,player,new Vec3(640.5,80,-.5));tick(chest,SoulOrbEntity.DURATION);
        check(chest.completed&&registry.complete("chest_1"),"A real nearby death completes and registers the chest ID");logic.tick();check(!logic.getBlockState().getValue(BlockStateProperties.POWERED),"A finished chest does not bypass the required Cerberus");chestBody.discard();
        player.setPos(648.5,80,-3.5);var first=kill(level,player,new Vec3(648.5,80,-2.5));
        check(dog.revealed&&dog.phase==1&&first.isCerberusFeeding()&&first.getCerberusHead().equals(dogPos),"First nearby kill reveals Cerberus and synchronizes the corpse's jaw attachment");
        check(CerberusSystem.jawAngle(2,0)<.02&&CerberusSystem.jawAngle(2,CerberusSystem.BITE_TICKS/2)>.6,"Chewing closes the jaws firmly and opens them fully for the next bite");
        Vec3 original=first.position();tick(dog,18);first.tick();check(first.position().distanceToSqr(original)>.1&&!first.isRemoved()&&first.isNoGravity(),"Actual dead zombie moves toward the mouth and survives corpse ticks during feeding");
        var ignored=kill(level,player,new Vec3(649,80,-2));check(!ignored.isCerberusFeeding()&&dog.collected==0,"Kills during corpse flight are ignored and not queued");ignored.discard();
        tick(dog,18);check(!first.isRemoved()&&first.isCerberusFeeding()&&dog.phase==2&&dog.collected==0,"The actual corpse stays visible in the mouth throughout chewing, without counting early");
        ignored=kill(level,player,new Vec3(649,80,-2));check(!ignored.isCerberusFeeding(),"Cerberus rejects another kill while chewing");ignored.discard();
        Vec3 mouthBefore=first.position();tick(dog,12);first.tick();check(!first.isRemoved()&&first.position().distanceToSqr(mouthBefore)>.001,"Chewing moves the retained corpse with the shaking head");
        tick(dog,8);check(first.isRemoved()&&dog.collected==1&&dog.canAccept()&&!dog.completed,"Only the end of chewing removes the corpse and reopens feeding");
        var second=kill(level,player,new Vec3(648.5,80,-2.5));tick(dog,CerberusSystem.FLIGHT_TICKS+20);
        check(second.isRemoved()&&dog.completed&&dog.collected==2&&registry.complete("dog_1"),"Second full meal completes the Cerberus ID");
        check(WaveManager.UNLOCKED_CHANNELS.contains(8),"Cerberus completion applies its configured reward channel");logic.tick();
        check(logic.getBlockState().getValue(BlockStateProperties.POWERED)&&logic.getBlockState().getSignal(level,logicPos,Direction.NORTH)==15,"Mixed completed IDs produce actual redstone strength 15");
        check(WaveManager.UNLOCKED_CHANNELS.contains(27)&&WaveManager.LOCKED_CHANNELS.contains(28),"Mixed objective completion enables and disables the configured spawner channels");
        var savedChecker=logic.saveWithoutMetadata();logic.setRemoved();logic.clearRemoved();logic.load(savedChecker);logic.onLoad();
        long clock=level.getGameTime();level.getServer().getWorldData().overworldData().setGameTime(clock+21);
        SpawnChannelActions.tick(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,()->false,level.getServer()));logic.tick();
        check(WaveManager.LOCKED_CHANNELS.contains(27)&&WaveManager.UNLOCKED_CHANNELS.contains(28)&&logic.getBlockState().getValue(BlockStateProperties.POWERED),"Temporary spawner actions restore after their duration without chunk reload restarting the timer");
        for(int i=0;i<5;i++)logic.tick();check(WaveManager.LOCKED_CHANNELS.contains(27)&&WaveManager.UNLOCKED_CHANNELS.contains(28),"Completed checker does not reapply expired channel actions every tick");
        c=logic.config();c.putInt("channelDuration",0);logic.configure(c);level.getServer().getWorldData().overworldData().setGameTime(clock+400);
        SpawnChannelActions.tick(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,()->false,level.getServer()));
        check(WaveManager.UNLOCKED_CHANNELS.contains(27)&&WaveManager.LOCKED_CHANNELS.contains(28),"Zero-duration completion actions persist for the match");
        level.getServer().getWorldData().overworldData().setGameTime(clock);
        var savedDog=dog.saveWithoutMetadata();var dogCopy=new CerberusSystem.Head(dogPos,dog.getBlockState());dogCopy.load(savedDog);check(dogCopy.completed&&dogCopy.objectiveId.equals("dog_1")&&dogCopy.chewTime==1,"Cerberus ID, settings and completion survive serialization");
        var savedRegistry=registry.save(new CompoundTag());var load=SoulObjectiveRegistry.class.getDeclaredMethod("load",CompoundTag.class);load.setAccessible(true);var recovered=(SoulObjectiveRegistry)load.invoke(null,savedRegistry);
        check(recovered.allComplete("chest_1; dog_1")&&!recovered.allComplete("")&&!recovered.allComplete("missing"),"Saved registry retains unloaded objectives; empty and missing IDs never pass");
        var duplicatePos=new BlockPos(656,80,0);level.setBlock(duplicatePos,MapAuthorSystem.SOUL_CHEST.get().defaultBlockState(),3);var duplicate=(SoulChestSystem.Chest)level.getBlockEntity(duplicatePos);duplicate.onLoad();c=duplicate.config();c.putString("objectiveId","dog_1");duplicate.configure(c);logic.tick();
        check(!registry.unique("dog_1")&&!logic.getBlockState().getValue(BlockStateProperties.POWERED),"Duplicate IDs across objective kinds invalidate redstone");
        level.setBlock(duplicatePos,Blocks.AIR.defaultBlockState(),3);logic.tick();check(level.getBlockState(duplicatePos).isAir()&&registry.unique("dog_1")&&logic.getBlockState().getValue(BlockStateProperties.POWERED),"Removing duplicate block removes its ID and restores the valid completed result");
        dog.setRemoved();SoulChestSystem.reset(level);check(!registry.allComplete("chest_1,dog_1")&&!logic.getBlockState().getValue(BlockStateProperties.POWERED),"New match clears all completions, including a previously unloaded Cerberus");
        dog.clearRemoved();dog.load(savedDog);dog.onLoad();check(!dog.completed&&dog.collected==0&&!dog.revealed,"Old chunk completion is reset when a Cerberus returns in a new match");
        var third=kill(level,player,new Vec3(648.5,80,-2.5));check(third.isCerberusFeeding(),"Reset Cerberus can feed again");level.setBlock(dogPos,Blocks.AIR.defaultBlockState(),3);
        check(!third.isCerberusFeeding()&&!third.isNoGravity()&&!registry.unique("dog_1"),"Breaking a feeding Cerberus releases the corpse and removes its ID");third.discard();
        var cap=player.getCapability(ZombieCapabilitySystem.Provider.PLAYER_DATA).orElseThrow(()->new AssertionError("capability"));cap.setTacticalType("zombierool:flash");cap.setEquipmentType("zombierool:claymore");cap.setTacticalCount(0);cap.setEquipmentCount(0);player.getPersistentData().remove("zr_equipment_charge_wave");
        EquipmentSystem.rechargeForWave(player,1);check(cap.getTacticalCount()==0&&cap.getEquipmentCount()==0,"Round 1 gives no additional recharge");
        EquipmentSystem.rechargeForWave(player,2);check(cap.getTacticalCount()==1&&cap.getEquipmentCount()==0,"Round 2 gives one tactical charge only");
        EquipmentSystem.rechargeForWave(player,2);check(cap.getTacticalCount()==1,"Repeated wave callback cannot duplicate charges");
        EquipmentSystem.rechargeForWave(player,3);check(cap.getTacticalCount()==1&&cap.getEquipmentCount()==0,"Odd round does not recharge");
        EquipmentSystem.rechargeForWave(player,4);check(cap.getTacticalCount()==2&&cap.getEquipmentCount()==1,"Round 4 gives one charge to each slot");
        EquipmentSystem.rechargeForWave(player,8);check(cap.getTacticalCount()==2&&cap.getEquipmentCount()==2,"Recharges never exceed the two-charge capacity");
        cap.setTacticalCount(0);cap.setEquipmentCount(0);player.getPersistentData().remove("zr_equipment_charge_wave");
        var current=WaveManager.class.getDeclaredField("currentWave");current.setAccessible(true);current.setInt(null,1);
        var regular=WaveManager.class.getDeclaredMethod("spawnNextWave",ServerLevel.class);regular.setAccessible(true);regular.invoke(null,level);check(cap.getTacticalCount()==1&&cap.getEquipmentCount()==0,"Actual regular wave start applies tactical recharge");
        current.setInt(null,3);var special=WaveManager.class.getDeclaredMethod("spawnSpecialWave",ServerLevel.class);special.setAccessible(true);special.invoke(null,level);check(cap.getTacticalCount()==2&&cap.getEquipmentCount()==1,"Actual special wave start applies both interval recharges");
        level.setBlock(chestPos,Blocks.AIR.defaultBlockState(),3);level.setBlock(logicPos,Blocks.AIR.defaultBlockState(),3);
        System.out.println("SOUL OBJECTIVE PASS: "+checks+" behavioral checks");
    }
}
