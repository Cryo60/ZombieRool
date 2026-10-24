package local.hotfixtest;

import me.cryo.zombierool.block.*;
import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.config.*;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.init.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

public final class Oct14GameplayTest {
    private static int checks;
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("OCT14 CHECK: "+text);}
    public static void run(ServerLevel level,ServerPlayer player)throws Exception {
        BlockPos p=new BlockPos(1264,80,0);for(int x=-8;x<=24;x++)for(int y=-2;y<=4;y++)for(int z=-3;z<=3;z++)level.setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        boolean global=GlobalSwitchState.isActivated(level);long time=level.getGameTime();float health=player.getHealth(),absorption=player.getAbsorptionAmount();
        var effects=new java.util.ArrayList<>(player.getActiveEffects());int points=PointManager.getScore(player);
        var activator=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("zombierool","alpha_activator"));
        var receptor=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("zombierool","alpha_receptor"));
        check(activator instanceof AbstractActivatorBlock && receptor instanceof AbstractReceptorBlock,"Real registered activator and receptor are used");
        var zombie=ZombieroolModEntities.ZOMBIE.get().create(level);
        try {
            var unloaded=new BlockPos(131072,80,131072);
            check(level.getChunkSource().getChunkNow(unloaded.getX()>>4,unloaded.getZ()>>4)==null,"The serialization probe starts outside loaded chunks");
            var detached=new UniversalSpawnerSystem.UniversalSpawnerBlockEntity(unloaded,UniversalSpawnerSystem.UNIVERSAL_SPAWNER_BLOCK.get().defaultBlockState());detached.setLevel(level);detached.saveWithFullMetadata();
            check(level.getChunkSource().getChunkNow(unloaded.getX()>>4,unloaded.getZ()>>4)==null,"Saving spawner power configuration cannot reload an unloading chunk");
            level.setBlock(p,UniversalSpawnerSystem.UNIVERSAL_SPAWNER_BLOCK.get().defaultBlockState(),3);
            // Validate every input face and every combination; global activators do not count as local input.
            for(Direction direction:Direction.values())for(var mode:java.util.List.of(BlockPowerSettings.Mode.GLOBAL,BlockPowerSettings.Mode.LOCAL,BlockPowerSettings.Mode.EITHER,BlockPowerSettings.Mode.BOTH)) {
                BlockPowerSettings.setMode(level,p,mode);
                for(int g=0;g<2;g++)for(int l=0;l<2;l++) {
                    GlobalSwitchState.setActivated(level,g==1);level.setBlock(p.relative(direction),l==1?Blocks.REDSTONE_BLOCK.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
                    boolean expected=switch(mode){case GLOBAL->g==1;case LOCAL->l==1;case EITHER->g==1||l==1;case BOTH->g==1&&l==1;default->false;};
                    check(BlockPowerSettings.required(level,p,true,false)==expected,"Power "+mode+" "+direction+" global="+g+" local="+l);
                }
                level.removeBlock(p.relative(direction),false);
            }
            BlockPowerSettings.setMode(level,p,BlockPowerSettings.Mode.BOTH);GlobalSwitchState.setActivated(level,true);
            level.setBlock(p.east(),ZombieroolModBlocks.ACTIVATOR.get().defaultBlockState(),3);
            check(!BlockPowerSettings.required(level,p,true,false),"A global activator alone cannot satisfy the two-source AND mode");
            level.removeBlock(p.east(),false);level.setBlock(p.west(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
            check(BlockPowerSettings.required(level,p,true,false),"A global source plus independent local redstone satisfies AND");
            var persisted=BlockPowerSettings.get(level).save(new CompoundTag());check(persisted.getList("blocks",10).size()>0,"Per-block source choices are persisted in world SavedData");
            var configured=(UniversalSpawnerSystem.UniversalSpawnerBlockEntity)level.getBlockEntity(p);
            check(configured.getUpdateTag().getString("PowerMode").equals("BOTH"),"The spawner synchronizes its chosen source to the client configuration widget");
            BlockPowerSettings.setMode(level,p,BlockPowerSettings.Mode.DEFAULT);level.removeBlock(p.west(),false);
            BlockPos inA=p.offset(5,0,0),inB=p.offset(9,0,0),out=p.offset(15,0,0);
            level.setBlock(inA,activator.defaultBlockState(),3);level.setBlock(inB,activator.defaultBlockState(),3);level.setBlock(out,receptor.defaultBlockState(),3);
            for(Direction direction:Direction.values()) {
                level.setBlock(inA.relative(direction),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
                check(MultiReceptorState.isActivated(level,"alpha"),"Activator accepts input on "+direction);
                var state=level.getBlockState(out);
                for(Direction output:Direction.values())check(state.getSignal(level,out,output)==15&&state.getDirectSignal(level,out,output)==15,"Receptor weak and strong output on "+output);
                BlockPos conductor=out.relative(direction);level.setBlock(conductor,Blocks.STONE.defaultBlockState(),3);
                check(level.hasNeighborSignal(out.relative(direction,2)),"Receptor powers a solid conducting neighbor on "+direction);
                level.removeBlock(conductor,false);level.removeBlock(inA.relative(direction),false);
                check(!MultiReceptorState.isActivated(level,"alpha"),"Channel falls after input disappears on "+direction);
            }
            level.setBlock(inA.above(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);level.setBlock(inB.above(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
            level.removeBlock(inB.above(),false);check(MultiReceptorState.isActivated(level,"alpha"),"An unpowered activator cannot cancel another active input on the same channel");
            level.removeBlock(inA,false);check(!MultiReceptorState.isActivated(level,"alpha"),"Removing the last active source clears the channel");
            check(!receptor.defaultBlockState().getValue(SignalTechnicalBlock.SURVIVAL_VISIBLE),"Signal markers default to hidden in survival");
            var before=level.getBlockState(inB);level.setBlock(inB,before.setValue(SignalTechnicalBlock.SURVIVAL_VISIBLE,true),3);
            check(level.getBlockState(inB).getValue(SignalTechnicalBlock.SURVIVAL_VISIBLE),"Changing visibility retains the signal block and persists a block state option");
            feedbackCircuit(level,p,inB,out);
            // Test the real damage/event path with indirect player-owned projectiles and a deterministic random stream.
            player.removeAllEffects();player.addEffect(new MobEffectInstance(ZombieroolModMobEffects.PERKS_EFFECT_BLOOD_RAGE.get(),10000));
            zombie.setPos(p.getX(),80,8);zombie.setEmergenceTicks(0);zombie.setNoAi(true);zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(2000);level.addFreshEntity(zombie);
            var arrow=new net.minecraft.world.entity.projectile.Arrow(level,player);
            var source=new net.minecraft.world.damagesource.DamageSource(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.ARROW),arrow);
            player.getRandom().setSeed(314159);int procs=0;
            for(int i=0;i<200;i++) {
                level.getServer().getWorldData().overworldData().setGameTime(time+20L*i);player.setHealth(player.getMaxHealth());player.setAbsorptionAmount(0);zombie.setHealth(2000);zombie.invulnerableTime=0;
                zombie.hurt(source,20);
                if(player.getAbsorptionAmount()>0)procs++;
            }
            check(procs>=25&&procs<=60,"Blood Rage procs visibly for indirect gun-style damage ("+procs+"/200 hits)");
            player.setHealth(player.getMaxHealth()/2);player.setAbsorptionAmount(0);level.getServer().getWorldData().overworldData().setGameTime(time+4020);
            for(int seed=0;seed<100000;seed++){player.getRandom().setSeed(seed);if(player.getRandom().nextFloat()<.1){player.getRandom().setSeed(seed);break;}}
            float damaged=player.getHealth();zombie.setHealth(2000);zombie.invulnerableTime=0;zombie.hurt(source,20);
            check(player.getHealth()>damaged,"Blood Rage restores actual player health");
            float restored=player.getHealth();zombie.invulnerableTime=0;zombie.hurt(source,20);check(player.getHealth()==restored,"Multiple pellets cannot bypass Blood Rage's one-second cooldown");
        } finally {
            zombie.discard();BlockPowerSettings.setMode(level,p,BlockPowerSettings.Mode.DEFAULT);
            for(int x=-8;x<=24;x++)for(int y=-2;y<=4;y++)for(int z=-3;z<=3;z++)level.removeBlock(p.offset(x,y,z),false);
            player.removeAllEffects();for(var effect:effects)player.addEffect(effect);player.setHealth(health);player.setAbsorptionAmount(absorption);PointManager.setScore(player,points);level.getServer().getWorldData().overworldData().setGameTime(time);GlobalSwitchState.setActivated(level,global);
        }
        System.out.println("OCT14 GAMEPLAY PASS: "+checks+" checks");
    }
    private static void feedbackCircuit(ServerLevel level,BlockPos p,BlockPos input,BlockPos output){
        for(Direction d:Direction.values())level.setBlock(output.relative(d),Blocks.REDSTONE_LAMP.defaultBlockState(),3);
        level.setBlock(input.east(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
        for(Direction d:Direction.values())check(level.getBlockState(output.relative(d)).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT),"Actual Alpha receptor lights a lamp on "+d);
        check(level.getBlockState(output).getValue(AbstractReceptorBlock.POWERED),"Receptor power is present in the synchronized block state");
        MultiReceptorState.reset(level);check(!MultiReceptorState.isActivated(level,"alpha"),"Match reset drops the named outputs");
        MultiReceptorState.refreshInputs(level);check(MultiReceptorState.isActivated(level,"alpha"),"A still-powered activator restores its channel after a match reset");
        level.removeBlock(input.east(),false);
        for(Direction d:Direction.values()){var pos=output.relative(d);level.getBlockState(pos).tick(level,pos,level.random);check(!level.getBlockState(pos).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT),"Actual Alpha receptor extinguishes a lamp on "+d);level.removeBlock(pos,false);}
        var soul=p.offset(21,0,0);level.setBlock(soul,MapAuthorSystem.SOUL_CHEST.get().defaultBlockState(),3);
        var chest=(SoulChestSystem.Chest)level.getBlockEntity(soul);chest.objectiveId="oct14_power_probe";chest.completed=true;SoulObjectiveRegistry.get(level).update(chest);
        level.setBlock(input.east(),MapAuthorSystem.COMPLETION.get().defaultBlockState(),3);
        var checker=(SoulCompletionSystem.Checker)level.getBlockEntity(input.east());var config=checker.config();config.putString("objectiveIds",chest.objectiveId);checker.configure(config);checker.tick();
        check(MultiReceptorState.isActivated(level,"alpha")&&level.getBlockState(output).getValue(AbstractReceptorBlock.POWERED),"A genuinely completed soul checker powers the Alpha activator and receptor");
        var entry=p.offset(3,0,0);var exit=p.offset(6,0,0);level.setBlock(entry,TraversalSystem.ZIPLINE.get().defaultBlockState(),3);level.setBlock(exit,TraversalSystem.ZIPLINE.get().defaultBlockState().setValue(TraversalSystem.TraversalBlock.PHASE,2),3);
        var zip=(TraversalSystem.Anchor)level.getBlockEntity(entry);zip.exit=exit;var other=(TraversalSystem.Anchor)level.getBlockEntity(exit);other.root=entry;
        check(level.getBlockState(entry.above()).getValue(TraversalSystem.TraversalBlock.UPPER)&&!level.getBlockState(entry.above()).canBeReplaced(),"Zipline reserves its second block instead of allowing overlapping placement");
        BlockPowerSettings.setMode(level,entry,BlockPowerSettings.Mode.LOCAL);
        for(var endpoint:java.util.List.of(entry,exit))for(int height=0;height<2;height++)for(Direction d:Direction.values()){
            var pos=endpoint.above(height).relative(d);if(pos.equals(entry)||pos.equals(entry.above())||pos.equals(exit)||pos.equals(exit.above()))continue;
            level.setBlock(pos,Blocks.REDSTONE_BLOCK.defaultBlockState(),3);check(BlockPowerSettings.required(level,exit,true,false),"Zipline local power at "+(endpoint.equals(entry)?"entry":"exit")+" height="+height+" face="+d);level.removeBlock(pos,false);
        }
        BlockPowerSettings.setMode(level,entry,BlockPowerSettings.Mode.DEFAULT);level.removeBlock(entry,false);level.removeBlock(exit,false);
        check(level.getBlockState(entry.above()).isAir()&&level.getBlockState(exit.above()).isAir(),"Removing a zipline removes its reserved upper parts");
        level.removeBlock(input.east(),false);level.removeBlock(soul,false);
        var receivers=new java.util.ArrayList<net.minecraft.world.level.block.Block>();
        receivers.add(UniversalSpawnerSystem.UNIVERSAL_SPAWNER_BLOCK.get());receivers.add(PerksSystem.BLOCK.get());receivers.add(PackAPunchSystem.PACK_A_PUNCH.get());receivers.add(ZombieroolModBlocks.DER_WUNDERFIZZ.get());receivers.add(TeleporterSystem.PLATFORM.get());
        receivers.addAll(MapDeviceSystem.TYPES.values().stream().map(net.minecraftforge.registries.RegistryObject::get).toList());
        var machine=p.offset(3,0,0);
        for(var block:receivers){
            level.setBlock(machine,block.defaultBlockState(),3);BlockPowerSettings.setMode(level,machine,BlockPowerSettings.Mode.LOCAL);
            for(Direction d:Direction.values()){
                level.setBlock(machine.relative(d),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
                boolean powered=BlockPowerSettings.required(level,machine,true,false);
                var be=level.getBlockEntity(machine);
                if(be instanceof TeleporterSystem.Pad pad)powered=pad.hasPower();else if(be instanceof MapDeviceSystem.Device device)powered=device.hasPower();else if(be instanceof PerksSystem.PerksAColaBlockEntity)powered=MapPower.syncPerk(level,machine);else if(be instanceof PackAPunchSystem.PackAPunchBlockEntity){MapPower.syncFlag(level,machine,PackAPunchSystem.PackAPunchBlock.POWERED);powered=level.getBlockState(machine).getValue(PackAPunchSystem.PackAPunchBlock.POWERED);}
                check(powered,"Actual receiver "+net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block)+" powered on "+d);
                level.removeBlock(machine.relative(d),false);
                check(!BlockPowerSettings.required(level,machine,true,false),"Receiver loses local power on "+d);
            }
            BlockPowerSettings.setMode(level,machine,BlockPowerSettings.Mode.DEFAULT);level.removeBlock(machine,false);
        }
    }
}
