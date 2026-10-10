package me.cryo.zombierool.hotfix;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import me.cryo.zombierool.block.RestrictBlock;
import me.cryo.zombierool.block.system.UniversalSpawnerSystem.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
public final class HotfixGameplay {
 public static int soundOverloadScore(Object member,me.cryo.zombierool.shadow.luaj.vm2.Varargs arguments){
  try{
   var field=member.getClass().getDeclaredField("method");field.setAccessible(true);var method=(java.lang.reflect.Method)field.get(member);
   if(!java.util.Set.of("playGlobalSound","playSoundForPlayer","playDynamicSoundForPlayer","playSound").contains(method.getName())||!me.cryo.zombierool.scripting.ZombieroolAPI.class.isAssignableFrom(method.getDeclaringClass()))return -1;
   return Math.abs(arguments.narg()-method.getParameterCount())*10000;
  }catch(ReflectiveOperationException e){return -1;}
 }
 public static net.minecraft.resources.ResourceLocation resolveLegacySound(net.minecraft.resources.ResourceLocation sound){
  if(!sound.getNamespace().equals("zombierool"))return sound;
  int slash=sound.getPath().lastIndexOf('/');
  if(slash<0||net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.containsKey(sound))return sound;
  var legacy=new net.minecraft.resources.ResourceLocation(sound.getNamespace(),sound.getPath().substring(slash+1));
  return net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.containsKey(legacy)?legacy:sound;
 }
 public static me.cryo.zombierool.shadow.luaj.vm2.LuaValue loadScript(me.cryo.zombierool.shadow.luaj.vm2.Globals globals,String path){
  try(var reader=java.nio.file.Files.newBufferedReader(java.nio.file.Path.of(path),java.nio.charset.StandardCharsets.UTF_8)){return globals.load(reader,path);}
  catch(java.io.IOException e){throw new me.cryo.zombierool.shadow.luaj.vm2.LuaError(e);}
 }
 public static VoxelShape restrictCollision(CollisionContext context){
  Entity entity=context instanceof EntityCollisionContext c?c.getEntity():null;
  return entity==null||entity instanceof net.minecraft.world.entity.projectile.Projectile||entity.getClass().getName().equals("com.tacz.guns.entity.EntityKineticBullet")||entity instanceof net.minecraft.world.entity.player.Player p&&p.isCreative()?Shapes.empty():Shapes.block();
 }
 public static File[] sortScripts(File[] files){
  if(files!=null)Arrays.sort(files,Comparator.comparingInt((File f)->f.getName().equalsIgnoreCase("main.lua")?0:1).thenComparing(File::getName));
  return files;
 }
 public static SpawnerMobType initialSpawnType(UniversalSpawnerBlockEntity spawner){
  return spawner.getMobType()==SpawnerMobType.PLAYER && !spawner.isActive(spawner.getLevel()) ? SpawnerMobType.ZOMBIE : spawner.getMobType();
 }
 public static BlockHitResult clip(Level level,ClipContext original){
  return level.clip(new ClipContext(original.getFrom(),original.getTo(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,(Entity)null){
   @Override public VoxelShape getBlockShape(BlockState state,BlockGetter world,BlockPos pos){return state.getBlock() instanceof RestrictBlock?Shapes.empty():original.getBlockShape(state,world,pos);}
   @Override public VoxelShape getFluidShape(FluidState state,BlockGetter world,BlockPos pos){return original.getFluidShape(state,world,pos);}
  });
 }
}
