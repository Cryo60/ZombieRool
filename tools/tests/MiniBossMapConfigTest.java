package local.hotfixtest;

import me.cryo.zombierool.config.*;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.entity.SpecialEnemyEntity;
import me.cryo.zombierool.block.system.UniversalSpawnerSystem.*;
import me.cryo.zombierool.init.ZombieroolModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;

public final class MiniBossMapConfigTest {
    private static int count;
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);count++;System.out.println("MAP ENEMIES CHECK: "+label);}
    public static void run(ServerLevel level,ServerPlayer player)throws Exception{
        var config=WorldConfig.get(level);var old=config.getSpecialEnemies();var running=WaveManager.class.getDeclaredField("gameRunning");running.setAccessible(true);var previous=running.get(null);
        BlockPos first=new BlockPos(1480,80,0),second=first.offset(4,0,0),minion=first.offset(8,0,0);
        try{
            config.setSpecialEnemies(SpecialEnemyConfig.defaults());running.set(null,true);MiniBossLoot.reset(level);
            level.getChunkAt(first);level.setBlock(first,me.cryo.zombierool.block.system.UniversalSpawnerSystem.MINI_BOSS_SPAWNER_BLOCK.get().defaultBlockState(),3);level.setBlock(second,me.cryo.zombierool.block.system.UniversalSpawnerSystem.MINI_BOSS_SPAWNER_BLOCK.get().defaultBlockState(),3);
            var a=(UniversalSpawnerBlockEntity)level.getBlockEntity(first);var b=(UniversalSpawnerBlockEntity)level.getBlockEntity(second);a.onLoad();b.onLoad();
            var typeChange=new CompoundTag();typeChange.putString("type","brutus");SpecialEnemyConfig.update(level,typeChange);a.setConfig(SpawnerMobType.BRUTUS,"","0","0",false,1);check(a.getMobType()==SpawnerMobType.BRUTUS&&b.getMobType()==SpawnerMobType.BRUTUS,"Changing boss type updates every loaded spawner");
            var legacy=b.saveWithoutMetadata();legacy.putString("MobType","panzer");b.load(legacy);b.onLoad();check(b.getMobType()==SpawnerMobType.BRUTUS,"A reloaded chunk adopts map type instead of its stale stored type");
            level.setBlock(minion,me.cryo.zombierool.block.system.UniversalSpawnerSystem.MINION_SPAWNER_BLOCK.get().defaultBlockState(),3);var m=(UniversalSpawnerBlockEntity)level.getBlockEntity(minion);m.onLoad();var patch=new CompoundTag();patch.putInt("napalmFirstWave",11);patch.putInt("napalmRadius",23);patch.putBoolean("napalmUseRadius",true);SpecialEnemyConfig.update(level,patch);check(m.specialConfig().getInt("FirstWave")==11&&m.specialConfig().getInt("Radius")==23&&m.specialConfig().getBoolean("UseRadius"),"Map config controls minion spawn rules and activation radius");
            var saved=new CompoundTag();config.saveEditable(saved);var loaded=new WorldConfig();loaded.loadEditable(saved);check(loaded.getSpecialEnemies().equals(config.getSpecialEnemies()),"Boss and minion settings survive map configuration serialization");
            var lua=new me.cryo.zombierool.scripting.ZombieroolAPI(level);var globals=me.cryo.zombierool.shadow.luaj.vm2.lib.jse.JsePlatform.standardGlobals();globals.set("ZombieroolAPI",me.cryo.zombierool.scripting.LegacySoundBindings.wrap(lua));
            globals.load("assert(ZombieroolAPI:setMiniBossType('panzer')); ZombieroolAPI:configureSpecialEnemies({panzerHealth=480,brutusHealth=360,bodyDamage=0.25,napalmFirstWave=3,skeletonBatch=6}); assert(ZombieroolAPI:getSpecialEnemiesConfig().napalmFirstWave==3); assert(ZombieroolAPI:getMapConfig().specialEnemies.skeletonBatch==6); assert(ZombieroolAPI:getSpecialSpawner(1480,80,0).type=='panzer'); assert(#ZombieroolAPI:listSpecialSpawners()>=3)","map-enemies-api").call();
            check(a.getMobType()==SpawnerMobType.PANZER&&b.getMobType()==SpawnerMobType.PANZER&&m.specialConfig().getInt("FirstWave")==3,"Actual LuaJ scripts configure global enemy rules and enumerate spawners");
            check(MiniBossLoot.tables(level).contains("zombierool_test:boss_first")&&MiniBossLoot.tables(level).contains("zombierool_test:boss_recurring"),"Loot picker lists server-loaded datapack tables");
            patch=new CompoundTag();patch.putString("firstLoot","zombierool_test:boss_first");patch.putString("recurringLoot","zombierool_test:boss_recurring");SpecialEnemyConfig.update(level,patch);
            var managerField=ServerLevel.class.getDeclaredField("entityManager");managerField.setAccessible(true);var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<net.minecraft.world.entity.Entity>)managerField.get(level);manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(first),FullChunkStatus.ENTITY_TICKING);
            kill(level,first);check(items(level,first,Items.APPLE)==1&&items(level,first,Items.BONE)==0&&MiniBossLoot.kills(level)==1,"First kill rolls only its first-kill table");
            kill(level,first);check(items(level,first,Items.APPLE)==1&&items(level,first,Items.BONE)==2&&MiniBossLoot.kills(level)==2,"Later kill rolls the recurring table without repeating first loot");
            MiniBossLoot.reset(level);kill(level,first);check(items(level,first,Items.APPLE)==2&&MiniBossLoot.kills(level)==1,"Starting a new match restores first-kill eligibility");
            MiniBossLoot.reset(level);patch=new CompoundTag();patch.putBoolean("recurringOnFirst",true);SpecialEnemyConfig.update(level,patch);kill(level,first);check(items(level,first,Items.BONE)==4,"Optional first kill also rolls the recurring table");
            patch=new CompoundTag();patch.putString("firstLoot","");patch.putString("recurringLoot","");SpecialEnemyConfig.update(level,patch);
            for(var type:java.util.List.of(ZombieroolModEntities.PANZER.get(),ZombieroolModEntities.BRUTUS.get())){
                var boss=type.create(level);boss.getPersistentData().putBoolean("zr_special_spawner",true);boss.setPos(first.getX(),80,0);int bullets=0;
                while(boss.isAlive()&&bullets<500){boss.invulnerableTime=0;boss.recordShotImpact(boss.position().add(0,boss.getBbHeight()*.9,0),player);boss.hurt(player.damageSources().playerAttack(player),7);bullets++;}
                check(!boss.isAlive()&&bullets<=(boss.isPanzer()?100:60),"Unupgraded seven-damage gun kills "+boss.kind()+" through its helmet/faceplate in "+bullets+" shots");
            }
            var invalid=SpecialEnemyConfig.defaults();invalid.putDouble("panzerHealth",Double.NaN);invalid.putDouble("bodyDamage",-1);invalid.putString("firstLoot","invalid table!");var safe=SpecialEnemyConfig.sanitize(invalid);check(safe.getDouble("panzerHealth")==480&&safe.getDouble("bodyDamage")==.05&&safe.getString("firstLoot").isEmpty(),"Invalid settings are clamped and malformed table IDs rejected");
            check(net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.containsKey(new net.minecraft.resources.ResourceLocation("zombierool:brutus_helmet")),"Original T6 Brutus helmet cue is registered");
            var stack=new net.minecraft.world.item.ItemStack(Items.DIAMOND,3);stack.getOrCreateTag().putString("QuestKey","warden");stack.setHoverName(net.minecraft.network.chat.Component.literal("Custom quest reward"));
            patch=new CompoundTag();patch.put("firstLootStack",stack.save(new CompoundTag()));patch.putBoolean("recurringOnFirst",false);SpecialEnemyConfig.update(level,patch);MiniBossLoot.reset(level);kill(level,first);
            var rewards=level.getEntitiesOfClass(ItemEntity.class,new net.minecraft.world.phys.AABB(first).inflate(4));
            check(rewards.stream().anyMatch(e->net.minecraft.world.item.ItemStack.matches(e.getItem(),stack)),"Item-slot first reward preserves count, custom name and all quest NBT");
            globals.load("assert(ZombieroolAPI:setMiniBossLootItem(false,'minecraft:emerald',2,'{QuestStage:4}')); assert(ZombieroolAPI:getSpecialEnemiesConfig().recurringLootStack.tag.QuestStage==4); assert(ZombieroolAPI:getSpecialEnemiesConfig().recurringLootStack.Count==2)","loot-item-api").call();
            check(ItemStackNBT(config.getSpecialEnemies().getCompound("recurringLootStack")),"Lua configures an item reward with custom NBT");
            globals.load("assert(ZombieroolAPI:setMiniBossLootItem(true,'minecraft:apple',1,'')); assert(ZombieroolAPI:getSpecialEnemiesConfig().firstLootStack.tag==nil)","replace-loot-item-api").call();
            check(!net.minecraft.world.item.ItemStack.of(config.getSpecialEnemies().getCompound("firstLootStack")).hasTag(),"Replacing a reward removes the previous item's quest NBT and custom name");
            var cleanAddon=new CompoundTag();config.saveEditable(cleanAddon);cleanAddon.getCompound("addonConfig").remove("test_addon:extra_enemy");config.loadEditable(cleanAddon);
            var extension=new CompoundTag();extension.putBoolean("enabled",true);extension.putInt("spawnInterval",7);MapConfigExtensions.register(new net.minecraft.resources.ResourceLocation("test_addon:extra_enemy"),"enemies",extension);
            var addonSaved=new CompoundTag();config.saveEditable(addonSaved);check(addonSaved.getCompound("addonConfig").getCompound("test_addon:extra_enemy").getInt("spawnInterval")==7,"New addon schema injects defaults into persisted map settings");
            var extensionChange=new CompoundTag();extensionChange.putInt("spawnInterval",11);addonSaved.getCompound("addonConfig").put("test_addon:extra_enemy",extensionChange);config.loadEditable(addonSaved);var roundtrip=new CompoundTag();config.saveEditable(roundtrip);
            check(roundtrip.getCompound("addonConfig").getCompound("test_addon:extra_enemy").getInt("spawnInterval")==11,"Addon map values survive loading and saving");
            var wire=new CompoundTag();me.cryo.zombierool.network.packet.S2COpenConfigMenuPacket.addLootTables(wire,level);check(wire.getCompound("mapConfigSchemas").getCompound("test_addon:extra_enemy").getString("category").equals("enemies"),"Server transmits dynamic addon metadata to map menu");
            var visualBoss=ZombieroolModEntities.PANZER.get().create(level);visualBoss.getPersistentData().putBoolean("zr_special_spawner",true);visualBoss.moveTo(first.getX()+.5,80,.5,0,0);level.addFreshEntity(visualBoss);player.setPos(first.getX()+.5,80,1.5);float beforeHp=player.getHealth();var beforeBlock=level.getBlockState(first.below());visualBoss.setHealth(0);visualBoss.die(level.damageSources().generic());var death=SpecialEnemyEntity.class.getDeclaredMethod("tickDeath");death.setAccessible(true);for(int t=0;t<60;t++)death.invoke(visualBoss);
            check(player.getHealth()==beforeHp&&level.getBlockState(first.below()).equals(beforeBlock)&&visualBoss.isRemoved(),"Five Panzer death blasts leave nearby player health and blocks unchanged");
            System.out.println("MAP ENEMIES PASS: "+count+" assertions");
        }finally{config.setSpecialEnemies(old);SpecialEnemyConfig.syncSpawners(level);running.set(null,previous);MiniBossLoot.reset(level);for(var pos:java.util.List.of(first,second,minion))level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);for(var entity:level.getEntitiesOfClass(ItemEntity.class,new net.minecraft.world.phys.AABB(first).inflate(4)))entity.discard();}
    }
    private static boolean ItemStackNBT(CompoundTag tag){var stack=net.minecraft.world.item.ItemStack.of(tag);return stack.is(Items.EMERALD)&&stack.getCount()==2&&stack.getTag().getInt("QuestStage")==4;}
    private static void kill(ServerLevel level,BlockPos pos){var boss=ZombieroolModEntities.BRUTUS.get().create(level);boss.getPersistentData().putBoolean("zr_special_spawner",true);boss.moveTo(pos.getX()+.5,80,.5,0,0);level.addFreshEntity(boss);boss.setHealth(0);boss.die(level.damageSources().generic());boss.discard();}
    private static int items(ServerLevel level,BlockPos pos,net.minecraft.world.item.Item item){return level.getEntitiesOfClass(ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(4)).stream().filter(e->e.getItem().is(item)).mapToInt(e->e.getItem().getCount()).sum();}
}
