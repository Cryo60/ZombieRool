package me.cryo.zombierool.gameplay;

import java.util.*;
import me.cryo.zombierool.config.*;
import me.cryo.zombierool.bonuses.BonusManager;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.entity.AbstractZombieRoolEntity;
import me.cryo.zombierool.spawner.SpawnerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.*;
import net.minecraftforge.registries.ForgeRegistries;

/** Commands are operator-only via the secret-console packet; world changes mark the run as cheated. */
public final class DebugConsole {
    public static final List<String> HELP = List.of("game","status","god","noclip","heal","spawn","enemies","despawn","bonus","bonus_next","perk","perks","power","signal","inspect","tp","pap","ammo","points_set","debug_reset");
    private static String text(String key,Object... args){return Component.translatable("gui.zombierool.console.debug."+key,args).getString();}
    private static boolean toggle(String[] args,boolean old){if(args.length==1)return !old;if(args.length!=2)throw new IllegalArgumentException("on / off");return bool(args[1]);}
    private static boolean bool(String s){return switch(s.toLowerCase(Locale.ROOT)){case "on","true","1"->true;case "off","false","0"->false;default->throw new IllegalArgumentException("on / off");};}
    public static boolean enemy(Entity e){return e instanceof AbstractZombieRoolEntity;}
    public static Mob lastEnemy(net.minecraft.server.level.ServerLevel level){
        Mob last=null;
        for(var entity:level.getAllEntities())if(entity instanceof Mob mob&&enemy(mob)&&mob.isAlive()){
            if(last==null||WaveManager.isCountedEnemy(mob)||!WaveManager.isCountedEnemy(last))last=mob;
        }
        return last;
    }
    public static String execute(ServerPlayer player,String command){
        String[] a=command.trim().split("\\s+");String verb=a[0].toLowerCase(Locale.ROOT);
        var level=player.serverLevel();
        if(!HELP.contains(verb)&&!verb.equals("godmode")&&!verb.equals("godmod"))return null;
        if(!Set.of("status","enemies","inspect").contains(verb))WaveManager.setCheatsUsed(true);
        switch(verb){
            case "god","godmode","godmod" -> {boolean enabled=toggle(a,DebugPlayerControls.god(player));DebugPlayerControls.setGod(player,enabled);return text("toggle", "god", enabled?"ON":"OFF");}
            case "noclip" -> {boolean enabled=toggle(a,DebugPlayerControls.noclip(player));DebugPlayerControls.setNoclip(player,enabled);return text("toggle","noclip",enabled?"ON":"OFF");}
            case "debug_reset" -> {DebugPlayerControls.reset(player);MultiReceptorState.clearDebugOverrides(level);MapPower.refresh(level);return text("reset");}
            case "game" -> {
                if(a.length!=2)throw new IllegalArgumentException("game start / stop / restart / pause / resume");
                switch(a[1]){
                    case "start"->WaveManager.startGame(level);
                    case "stop"->{WaveManager.endGame(level,null);if(player.isSpectator()){player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);player.setHealth(player.getMaxHealth());}}
                    case "restart"->WaveManager.restartLevel(level);
                    case "pause"->{if(!WaveManager.isServerGameRunning())return text("start_required");WaveManager.setGamePaused(true);}
                    case "resume"->WaveManager.setGamePaused(false);
                    default->throw new IllegalArgumentException("game start / stop / restart / pause / resume");
                }
                // startGame resets this flag; a console-started match is still a debug run.
                if(WaveManager.isServerGameRunning())WaveManager.setCheatsUsed(true);
                return text("game",a[1]);
            }
            case "status" -> {return text("status",WaveManager.isServerGameRunning(),WaveManager.getCurrentWave(),WaveManager.remainingEnemies(),SpawnerRegistry.getSpawners(level).size(),GlobalSwitchState.isActivated(level),WorldConfig.get(level).getMaxPerks(),DebugPlayerControls.god(player),DebugPlayerControls.noclip(player));}
            case "heal" -> {player.setHealth(player.getMaxHealth());player.clearFire();player.fallDistance=0;return text("healed");}
            case "points_set" -> {if(a.length!=2)throw new IllegalArgumentException("points_set <amount>");int value=Integer.parseInt(a[1]);if(value<0)throw new IllegalArgumentException("amount >= 0");PointManager.setScore(player,value);return text("points",value);}
            case "enemies" -> {
                Map<String,Integer> counts=new TreeMap<>();for(var e:level.getAllEntities())if(enemy(e)&&e.isAlive())counts.merge(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).toString(),1,Integer::sum);
                return text("enemies",counts);
            }
            case "despawn" -> {
                if(a.length>2)throw new IllegalArgumentException("despawn [type / all]");String type=a.length==1?"all":a[1];int count=0;
                var targets=new ArrayList<Entity>();for(var e:level.getAllEntities())if(enemy(e)&&(type.equals("all")||matches(e,type)))targets.add(e);
                for(var e:targets){e.discard();count++;}return text("removed",count);
            }
            case "spawn" -> {
                if(a.length!=2&&a.length!=3&&a.length!=5&&a.length!=6)throw new IllegalArgumentException("spawn <entity-id> [1..50] [x y z]");int count=a.length==3||a.length==6?Integer.parseInt(a[2]):1;
                if(count<1||count>50)throw new IllegalArgumentException("count 1..50");
                var id=ResourceLocation.tryParse(a[1].contains(":")?a[1]:"zombierool:"+a[1]);
                if(id==null||!ForgeRegistries.ENTITY_TYPES.containsKey(id))throw new IllegalArgumentException("Unknown entity: "+a[1]);
                var type=ForgeRegistries.ENTITY_TYPES.getValue(id);Vec3 position=a.length>=5?coordinates(player,a,a.length-3):target(player).getLocation();int added=0;
                for(int i=0;i<count;i++){
                    var entity=type.create(level);if(!(entity instanceof Mob mob))throw new IllegalArgumentException("Use a living mob type");
                    mob.moveTo(position.x+(i%5)*.7,position.y+.1,position.z+(i/5)*.7,player.getYRot()+180,0);
                    mob.getPersistentData().putBoolean("zr_debug_spawn",true);
                    mob.finalizeSpawn(level,level.getCurrentDifficultyAt(mob.blockPosition()),MobSpawnType.COMMAND,null,null);
                    if(level.addFreshEntity(mob))added++;
                }return text("spawned",added,id);
            }
            case "bonus_next" -> {
                if(a.length>2)throw new IllegalArgumentException("bonus_next [id|random|clear]");String id=a.length==1?"random":a[1];
                if(id.equalsIgnoreCase("clear")||id.equalsIgnoreCase("cancel")){QueuedBonusDrops.clear(player);return text("bonus_next_clear");}
                QueuedBonusDrops.queue(player,id);return text("bonus_next",id);
            }
            case "bonus" -> {
                if(a.length>=2&&a[1].equalsIgnoreCase("next")){return execute(player,"bonus_next"+(a.length>2?" "+a[2]:""));}
                if(a.length!=2&&a.length!=5)throw new IllegalArgumentException("bonus <id|random> [x y z]");
                var bonus=QueuedBonusDrops.resolve(player,a[1]);if(bonus==null)throw new IllegalArgumentException("Unknown bonus: "+a[1]);
                Vec3 at=a.length==5?coordinates(player,a,2):player.position();BonusManager.spawnDebugBonus(bonus,level,at);return text("bonus",bonus.id);
            }
            case "perk" -> {
                if(a.length!=3||!Set.of("give","remove").contains(a[1]))throw new IllegalArgumentException("perk give / remove <id>");
                var perk=PerksManager.ALL_PERKS.get(a[2]);if(perk==null||perk.getAssociatedEffect()==null)throw new IllegalArgumentException("Unknown perk: "+a[2]);
                if(a[1].equals("give"))perk.applyEffect(player);else player.removeEffect(perk.getAssociatedEffect());return text("perk",a[1],a[2]);
            }
            case "perks" -> {
                if(a.length==2&&a[1].equals("clear")){for(var perk:PerksManager.ALL_PERKS.values())if(perk.getAssociatedEffect()!=null)player.removeEffect(perk.getAssociatedEffect());return text("perk","clear","all");}
                if(a.length==3&&a[1].equals("limit")){int limit=Integer.parseInt(a[2]);if(limit<-1||limit>256)throw new IllegalArgumentException("limit -1..256");WorldConfig.get(level).setMaxPerks(limit);for(var p:level.players())DebugPlayerControls.sync(p);return text("limit",limit);}
                throw new IllegalArgumentException("perks clear / limit <-1..256>");
            }
            case "power" -> {
                if(a.length==2&&(a[1].equals("on")||a[1].equals("off"))){
                    boolean on=bool(a[1]);for(var pos:WorldConfig.get(level).getPowerSwitchPositions())if(level.hasChunkAt(pos)){
                        var block=level.getBlockState(pos);if(block.getBlock() instanceof me.cryo.zombierool.block.PowerSwitchBlock)level.setBlock(pos,block.setValue(me.cryo.zombierool.block.PowerSwitchBlock.POWERED,on),3);
                    }
                    GlobalSwitchState.setActivated(level,on);MapPower.refresh(level);return text("toggle","power",a[1]);
                }
                if(a.length==2&&a[1].equals("reset")){MapPower.resetSignals(level);MapPower.refresh(level);return text("power_reset");}
                if(a.length==3&&a[1].equals("source")){
                    var hit=target(player);if(hit.getType()!=HitResult.Type.BLOCK||!BlockPowerSettings.configurable(level,hit.getBlockPos()))return text("no_device");
                    var mode=BlockPowerSettings.Mode.valueOf(a[2].toUpperCase(Locale.ROOT));BlockPowerSettings.setMode(level,hit.getBlockPos(),mode);return text("source",hit.getBlockPos().toShortString(),mode);
                }throw new IllegalArgumentException("power on / off / reset / source <default|global|local|either|both>");
            }
            case "signal" -> {if(a.length!=3)throw new IllegalArgumentException("signal <channel> on / off / auto");String channel=a[1].toLowerCase(Locale.ROOT);if(java.util.Arrays.stream(me.cryo.zombierool.block.SignalChannel.values()).noneMatch(c->c.id().equals(channel)))throw new IllegalArgumentException("Unknown signal channel");MultiReceptorState.debugOverride(level,channel,a[2].equals("auto")?null:bool(a[2]));MultiReceptorState.notifyReceptors(level,channel);MapPower.refresh(level);return text("toggle",channel,a[2]);}
            case "inspect" -> {
                var hit=target(player);if(hit.getType()!=HitResult.Type.BLOCK)return text("no_device");var pos=hit.getBlockPos();var root=BlockPowerSettings.root(level,pos);var be=level.getBlockEntity(root);
                return text("inspect",pos.toShortString(),level.getBlockState(pos),root.toShortString(),BlockPowerSettings.mode(level,root),BlockPowerSettings.localSignal(level,root),be==null?"{}":be.saveWithFullMetadata());
            }
            case "tp" -> {if(a.length!=4)throw new IllegalArgumentException("tp <x> <y> <z>");Vec3 parsed=coordinates(player,a,1);double x=parsed.x,y=parsed.y,z=parsed.z;if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>29999984||Math.abs(z)>29999984||y<level.getMinBuildHeight()-64||y>level.getMaxBuildHeight()+256)throw new IllegalArgumentException("Invalid coordinates");player.teleportTo(x,y,z);return text("teleported");}
            case "pap" -> {var item=player.getMainHandItem();if(!WeaponFacade.isWeapon(item))return text("no_weapon");WeaponFacade.applyPackAPunch(item);return text("pap");}
            case "ammo" -> {var item=player.getMainHandItem();if(!WeaponFacade.isWeapon(item))return text("no_weapon");WeaponFacade.setAmmo(item,WeaponFacade.getMaxAmmo(item));WeaponFacade.setReserve(item,WeaponFacade.getMaxReserve(item));player.inventoryMenu.broadcastChanges();return text("ammo");}
        }
        return null;
    }
    private static boolean matches(Entity e,String type){var id=ForgeRegistries.ENTITY_TYPES.getKey(e.getType());return id!=null&&(id.toString().equals(type)||id.getPath().equals(type));}
    private static BlockHitResult target(ServerPlayer player){var from=player.getEyePosition();return player.serverLevel().clip(new net.minecraft.world.level.ClipContext(from,from.add(player.getLookAngle().scale(12)),net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,player));}
    private DebugConsole(){}
    private static Vec3 coordinates(ServerPlayer player,String[] args,int first){
        try{return net.minecraft.commands.arguments.coordinates.Vec3Argument.vec3(false).parse(new com.mojang.brigadier.StringReader(String.join(" ",Arrays.copyOfRange(args,first,first+3)))).getPosition(player.createCommandSourceStack());}
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new IllegalArgumentException(e.getRawMessage().getString());}
    }

}
