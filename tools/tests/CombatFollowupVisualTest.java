package local.atmospherevisual;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.item.*;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.client.*;
import me.cryo.zombierool.block.system.*;
import net.minecraft.resources.ResourceLocation;
@Mod("atmospherevisual")
public final class CombatFollowupVisualTest {
 java.util.Set<String> reloadTracks=new java.util.HashSet<>();
 int age,checks;boolean requested,done;volatile Throwable failure;
 public CombatFollowupVisualTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
 void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("COMBAT FOLLOWUP CHECK: "+text);}
 void shot(String name)throws Exception{var mc=Minecraft.getInstance();try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output"),name+".png"));}}
 void setup(net.minecraft.server.level.ServerPlayer player)throws Exception{
  var l=player.serverLevel();player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.getInventory().clearContent();
  for(var pos:BlockPos.betweenClosed(88,79,88,112,86,111))l.setBlock(pos,pos.getY()==79?Blocks.SMOOTH_STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
  player.teleportTo(l,100,80,90,0,6);me.cryo.zombierool.config.WorldConfig.get(l).setDayNightMode("night");l.setDayTime(18000);
  var box=MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState().setValue(MysteryBoxSystem.MysteryBoxBlock.OPEN,true);l.setBlock(new BlockPos(100,80,99),box,3);
  int i=0;for(String id:new java.util.TreeSet<>(BonusVisuals.TYPES)){
   var bonus=me.cryo.zombierool.bonuses.BonusManager.ALL_BONUSES.get(id);check(bonus!=null,"Original bonus definition retained: "+id);me.cryo.zombierool.bonuses.BonusManager.spawnDebugBonus(bonus,l,new Vec3(94+(i%5)*2.6,80,96+(i/5)*3));i++;
  }
  // Test real barricade tracing and a solid wall, without changing collisions.
  var at=new BlockPos(108,81,93);l.setBlock(at,me.cryo.zombierool.core.registry.ZRBlocks.DEFENSE_DOOR.get().defaultBlockState(),3);Vec3 from=new Vec3(108.5,81.5,91),to=new Vec3(108.5,81.5,96);
  check(WeaponEffectTrace.clip(l,from,to,player).getType()==HitResult.Type.MISS,"Flame trace crosses closed defense doors");l.setBlock(at,Blocks.STONE.defaultBlockState(),3);check(WeaponEffectTrace.clip(l,from,to,player).getType()==HitResult.Type.BLOCK,"Flame trace still stops at solid walls");
  var z=new me.cryo.zombierool.entity.ZombieEntity(me.cryo.zombierool.init.ZombieroolModEntities.ZOMBIE.get(),l);z.setPos(110,80,99);InfernoZombie.ordinaryBurn(z,1);z.getPersistentData().putLong("zr_ordinary_burn_until",l.getGameTime()-10);z.setRemainingFireTicks(100);check(InfernoZombie.ordinaryBurning(z),"Weapon fire stays ordinary until its last burn tick");var attack=new net.minecraftforge.event.entity.living.LivingAttackEvent(z,l.damageSources().onFire(),1);InfernoZombie.attack(attack);check(!z.isInferno(),"Flamethrower residual burning cannot create explosive sprinters");
  var fake=net.minecraftforge.common.util.FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"ReloadProbe"));fake.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);fake.getPersistentData().putBoolean("zr_prefer_zr_weapons",false);
  for(boolean upgraded:new boolean[]{false,true}){
   var smg=WeaponFacade.createWeaponStack("mp7",upgraded,fake);if(!WeaponFacade.isTaczWeapon(smg))throw new AssertionError("Actual MP7 pack required");
   var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(smg);fake.getInventory().selected=0;fake.getInventory().setItem(0,smg);var index=com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gun.getGunId(smg)).orElseThrow();
   check(gun.getGunId(smg).toString().equals("lrl:mp7_cirrus"),"The installed Cirrus pack is the mapped MP7, PAP="+upgraded);
   check(gun.getFireMode(smg)==com.tacz.guns.api.item.gun.FireMode.AUTO,"MP7 uses automatic fire, PAP="+upgraded);
   check(index.getGunData().getShootInterval(fake,com.tacz.guns.api.item.gun.FireMode.AUTO,smg)==50,"MP7 mapped interval remains 50ms, PAP="+upgraded);
   var d=com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(fake).getDataHolder();d.initialData();d.currentGunItem=fake::getMainHandItem;d.drawTimestamp=System.currentTimeMillis()-10000;d.shootTimestamp=-10000;d.lastShootTimestamp=-10000;
   var draw=new com.tacz.guns.entity.shooter.LivingEntityDrawGun(fake,d);var shoot=new com.tacz.guns.entity.shooter.LivingEntityShoot(fake,d,draw);int before=WeaponFacade.getAmmo(smg),successful=0;
   for(int shot=0;shot<12;shot++){d.shootTimestamp-=60;d.lastShootTimestamp-=60;var result=shoot.shoot(()->0f,()->0f,System.currentTimeMillis()-d.baseTimestamp);if(result==com.tacz.guns.api.entity.ShootResult.SUCCESS)successful++;System.out.println("MP7 SHOT "+shot+" "+result);}
   check(successful==12,"MP7 accepts twelve successive shots at its mapped interval, PAP="+upgraded);

  }
  check(!me.cryo.zombierool.core.system.WeaponSystem.Loader.LOADED_DEFINITIONS.containsKey("mp7_cirrus"),"Cirrus has no separate ZombieRool definition");
  for(String rifle:new String[]{"kar98k","arisaka","mosinnagant","springfield"})for(int initial:new int[]{0,1,4}){
   var kar=WeaponFacade.createWeaponStack(rifle,false,fake);check(me.cryo.zombierool.integration.TacZBoltReloadPlan.supports(kar),"Actual rifle profile loaded: "+rifle);fake.getInventory().setItem(0,kar);fake.getInventory().selected=0;WeaponFacade.setAmmo(kar,initial);WeaponFacade.setReserve(kar,50);int total=initial+50;
   var d=com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(fake).getDataHolder();d.initialData();d.currentGunItem=fake::getMainHandItem;d.drawTimestamp=System.currentTimeMillis()-10000;d.shootTimestamp=-10000;
   var draw=new com.tacz.guns.entity.shooter.LivingEntityDrawGun(fake,d);var reload=new com.tacz.guns.entity.shooter.LivingEntityReload(fake,d,draw,new com.tacz.guns.entity.shooter.LivingEntityShoot(fake,d,draw));check(((com.tacz.guns.api.item.gun.AbstractGunItem)kar.getItem()).canReload(fake,kar),"Manual capacity allows reloading without moving a round to the chamber: "+initial);var beforeTag=kar.getTag().copy();d.isBolting=true;reload.reload();check(kar.getTag().equals(beforeTag)&&d.reloadTimestamp==-1,"Rejected reload during bolt does not change ammunition: "+initial);d.isBolting=false;reload.reload();int jumps=0,prior=WeaponFacade.getAmmo(kar);
   for(int step=0;step<400&&d.reloadTimestamp!=-1;step++){d.reloadTimestamp-=50;reload.tickReloadState();int current=WeaponFacade.getAmmo(kar);if(current!=prior){jumps++;check(current<=5,"Native reload stays within configured capacity: "+rifle+" from "+initial);}prior=current;}
   check(WeaponFacade.getAmmo(kar)==5,"Mapped rifle completes reload: "+rifle+" from "+initial);check(WeaponFacade.getAmmo(kar)+WeaponFacade.getReserve(kar)==total,"Mapped Kar conserves reserve ammunition");
  }
  var legacy=new ItemStack(net.minecraft.world.item.Items.STICK);legacy.setHoverName(net.minecraft.network.chat.Component.literal("Â§aMP7"));check(ItemNameRepair.repair(legacy)&&legacy.getHoverName().getString().equals("MP7"),"Old saved name corruption is removed");
  check(ItemNameRepair.clean("Â§aÃ‰pÃ©e").equals("Épée")&&ItemNameRepair.clean("Épée du héros").equals("Épée du héros"),"UTF-8 repair preserves correct accents and fixes encoded names");
  for(var item:net.minecraftforge.registries.ForgeRegistries.ITEMS)if(item instanceof me.cryo.zombierool.core.system.WeaponSystem.BaseGunItem){String name=new ItemStack(item).getHoverName().getString();check(!name.contains("Â§")&&!name.contains("§"),"Weapon name has structured formatting: "+net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item));}
  var carbine=WeaponFacade.createWeaponStack("m1carbine",false,fake);if(WeaponFacade.isTaczWeapon(carbine))check(com.tacz.guns.api.item.IGun.getIGunOrNull(carbine).getFireMode(carbine)==com.tacz.guns.api.item.gun.FireMode.SEMI,"M1 Carbine stays semi automatic");
  for(var entry:com.tacz.guns.api.TimelessAPI.getAllCommonGunIndex()){
   if(entry.getValue().getGunData().getBolt()!=com.tacz.guns.resource.pojo.data.gun.Bolt.MANUAL_ACTION)continue;
   if(!entry.getKey().getNamespace().equals("tacz"))continue; // Actual mapped pack rifles are covered above.
   var stack=WeaponFacade.createUnmappedTaczWeaponStack(entry.getKey(),false);var gun=(com.tacz.guns.api.item.gun.AbstractGunItem)stack.getItem();fake.getInventory().selected=0;fake.getInventory().setItem(0,stack);WeaponFacade.setReserve(stack,100);
   int cap=WeaponFacade.getMaxAmmo(stack);
   for(int ammo:new int[]{0,Math.max(1,cap-1)})for(boolean chamber:new boolean[]{false,true}){
    if(ammo==0&&chamber)continue;gun.setBulletInBarrel(stack,chamber);gun.setCurrentAmmoCount(stack,Math.max(0,ammo-(chamber?1:0)));
    System.out.println("BOLT DIAGNOSTIC: "+entry.getKey()+" total="+ammo+" chamber="+chamber+" capacity="+cap+" canReload="+gun.canReload(fake,stack));
    var d=com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(fake).getDataHolder();d.initialData();d.currentGunItem=fake::getMainHandItem;d.drawTimestamp=System.currentTimeMillis()-10000;d.shootTimestamp=-10000;
    var draw=new com.tacz.guns.entity.shooter.LivingEntityDrawGun(fake,d);var reload=new com.tacz.guns.entity.shooter.LivingEntityReload(fake,d,draw,new com.tacz.guns.entity.shooter.LivingEntityShoot(fake,d,draw));reload.reload();
    int initialTotal=WeaponFacade.getAmmo(stack)+WeaponFacade.getReserve(stack);
    for(int step=0;step<300&&d.reloadTimestamp!=-1;step++){d.reloadTimestamp-=50;reload.tickReloadState();}
    check(WeaponFacade.getAmmo(stack)+WeaponFacade.getReserve(stack)==initialTotal,"Manual reload conserves ammunition: "+entry.getKey());System.out.println("BOLT AMMO: "+WeaponFacade.getAmmo(stack)+" / "+cap);check(WeaponFacade.getAmmo(stack)==cap,"Full manual reload: "+entry.getKey()+" from "+ammo+" chamber="+chamber);
   }
  }
 }
 void tick(TickEvent.ClientTickEvent e){
  if(done||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
  try{
   if(failure!=null)throw new AssertionError(failure);
   if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"AtmosphereFixture");return;}
   if(mc.level==null||mc.player==null)return;age++;
   if(age==20){MapAtmosphereClient.receive("preview",MapAtmosphere.preset("nuketown_zombies"));mc.getSingleplayerServer().execute(()->{try{setup(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable t){failure=t;}});}
   if(age==50){for(String id:BonusVisuals.TYPES){var model=mc.getModelManager().getModel(new ResourceLocation("zombierool","bonus/"+id));check(model!=mc.getModelManager().getMissingModel(),"3D bonus model baked: "+id);for(var q:model.getQuads(null,null,net.minecraft.util.RandomSource.create(1)))check(!q.getSprite().contents().name().getPath().contains("missingno"),"Bonus texture stitched: "+id);}shot("bonuses");}
   if(age==51){
    me.cryo.zombierool.init.KeyBindings.REPAIR_AND_PURCHASE_KEY.setDown(false);
   }
   if(age==52){
    var stack=WeaponFacade.createUnmappedTaczWeaponStack(new ResourceLocation("tacz:kar98"),false);mc.player.getInventory().setItem(mc.player.getInventory().selected,stack);
    Class<?> cl=TacZAutoReload.class;for(var pair:java.util.Map.of("loading",(Object)stack,"heldTrigger",true,"slot",mc.player.getInventory().selected,"gunId","tacz:kar98","expires",mc.player.tickCount+100).entrySet()){var f=cl.getDeclaredField(pair.getKey());f.setAccessible(true);f.set(null,pair.getValue());}
    mc.options.keyAttack.setDown(true);check(TacZAutoReload.blocksHeldShot(),"An empty-gun automatic reload blocks the held trigger");mc.player.getInventory().setItem(mc.player.getInventory().selected,stack.copy());check(TacZAutoReload.blocksHeldShot(),"Reload protection survives synchronized stack replacement");mc.options.keyAttack.setDown(false);check(!TacZAutoReload.blocksHeldShot(),"Releasing and pressing again permits deliberate reload interruption");
   }
   if(age==55){mc.options.hideGui=true;mc.player.setXRot(-28);mc.player.setYRot(0);}
   if(age==65)shot("f1-sky");
   if(age==70){mc.options.hideGui=false;var data=me.cryo.zombierool.client.career.LocalCareerManager.getData();if(data.activeChallenges.isEmpty())data.activeChallenges.put("fixture",new me.cryo.zombierool.client.career.LocalCareerManager.ChallengeDef(me.cryo.zombierool.client.career.LocalCareerManager.ChallengeType.KILLS,100,100));mc.options.keyPlayerList.setDown(true);}
   if(age==80)shot("tab-challenges");
   if(age==81){mc.options.keyPlayerList.setDown(false);WaveManager.setClientGameRunning(true);mc.gameMode.setLocalMode(net.minecraft.world.level.GameType.SURVIVAL);var flame=new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("zombierool:flamethrower")));((me.cryo.zombierool.core.system.WeaponSystem.BaseGunItem)flame.getItem()).initializeIfNeeded(flame);((me.cryo.zombierool.core.system.WeaponSystem.BaseGunItem)flame.getItem()).setOverheat(flame,650);mc.player.getInventory().setItem(mc.player.getInventory().selected,flame);}
   if(age==84)shot("raised-heat-bar");
   if(age>=85){
    int slot=(age-85)/360,phase=(age-85)%360;
    String[] rifles={"springfield","kar98k","arisaka","mosinnagant"};
    if(slot>=rifles.length){done=true;System.out.println("ATMOSPHERE VISUAL PASS: "+checks+" combat follow-up checks");mc.stop();return;}
    String rifle=rifles[slot];
    if(phase==0){reloadTracks.clear();mc.player.getInventory().selected=0;mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.getPersistentData().putBoolean("zr_prefer_zr_weapons",false);var gun=WeaponFacade.createWeaponStack(rifle,false,p);WeaponFacade.setAmmo(gun,0);WeaponFacade.setReserve(gun,40);p.getInventory().selected=0;p.getInventory().setItem(0,gun);var d=com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(p).getDataHolder();d.initialData();d.currentGunItem=p::getMainHandItem;d.drawTimestamp=System.currentTimeMillis()-10000;d.shootTimestamp=System.currentTimeMillis();});}
    com.tacz.guns.api.TimelessAPI.getGunDisplay(mc.player.getMainHandItem()).ifPresent(display->{var machine=display.getAnimationStateMachine();if(machine!=null){var controller=machine.getAnimationController();if(controller.getUpdatingTrackArray()!=null)for(var track:controller.getUpdatingTrackArray()){var runner=controller.getAnimation(track);if(runner!=null&&runner.isRunning()&&runner.getAnimation().name.startsWith("reload"))reloadTracks.add(runner.getAnimation().name);}}});
    if(phase==170){System.out.println("RIFLE EMPTY "+rifle+" "+reloadTracks);check(reloadTracks.contains("reload_clip")||reloadTracks.contains("reload_empty_clip")||reloadTracks.contains("reload_empty"),"Full reload animation: "+rifle);check(!reloadTracks.contains("reload_loop"),"Empty rifle does not use individual rounds: "+rifle);check(WeaponFacade.getAmmo(mc.player.getMainHandItem())==5,"Empty rifle loaded five rounds: "+rifle);reloadTracks.clear();mc.getSingleplayerServer().execute(()->WeaponFacade.setAmmo(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).getMainHandItem(),4));}
    if(phase==180){check(WeaponFacade.getAmmo(mc.player.getMainHandItem())==4,"Partial fixture has four rounds before R: "+rifle);System.out.println("PARTIAL INPUT "+rifle+" "+mc.player.getMainHandItem().getTag());com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player).reload();}
    if(phase==340){System.out.println("RIFLE PARTIAL "+rifle+" "+reloadTracks);check(reloadTracks.contains("reload_loop")&&reloadTracks.contains("reload_end")&&!reloadTracks.contains("reload_empty")&&!reloadTracks.contains("reload_load_clip"),"Partial reload uses individual rounds: "+rifle);check(WeaponFacade.getAmmo(mc.player.getMainHandItem())==5,"Partial rifle loaded five rounds: "+rifle);}
   }
  }catch(Throwable t){t.printStackTrace();done=true;System.out.println("ATMOSPHERE VISUAL FAIL: "+t);mc.stop();}
 }
}
