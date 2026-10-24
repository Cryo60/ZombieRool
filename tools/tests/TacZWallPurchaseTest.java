package local.hotfixtest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import me.cryo.zombierool.block.system.BuyWallWeaponSystem;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.integration.TacZIntegration;
import me.cryo.zombierool.gameplay.PointManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Real mapped TacZ mod and actual purchase handler, with New World's saved M40A3 template. */
@Mod("hotfixtests")
public final class TacZWallPurchaseTest {
    public static class ScreamerProbe extends me.cryo.zombierool.scripting.ZombieroolAPI {
        public int screamers,sounds;
        public ScreamerProbe(net.minecraft.server.level.ServerLevel level){super(level);}
        @Override public void triggerScopeScreamer(String uuid){screamers++;}
        @Override public void playDynamicSoundForPlayer(String uuid,String id,float volume,float pitch){if(id.equals("zombierool:sfx/misc/screaming")&&Math.abs(volume-.22f)<.001&&pitch==1)sounds++;}
    }
    public TacZWallPurchaseTest(){MinecraftForge.EVENT_BUS.addListener(this::run);}
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("TACZ CHECK: "+message);}
    private void run(ServerStartedEvent event){
        try {
            var level=event.getServer().overworld();
            var player=FakePlayerFactory.get(level,new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000040"),"TacZWallProbe"));
            player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();player.getPersistentData().putBoolean("zr_prefer_zr_weapons",false);
            check(TacZIntegration.isTaczGunAvailable("tacz:ai_awp"),"The M40A3's actual TacZ gun is available in the loaded gun pack");
            BlockPos pos=new BlockPos(0,80,0);level.setBlock(pos,BuyWallWeaponSystem.BLOCK.get().defaultBlockState(),3);
            var wall=(BuyWallWeaponSystem.BuyWallWeaponBlockEntity)level.getBlockEntity(pos);
            ItemStack nativeGun=new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("zombierool:m40a3")));
            var tag=nativeGun.getOrCreateTag();tag.putInt("Reserve",42);tag.putInt("Ammo",7);tag.putInt("AmmoLeft",7);tag.putBoolean("EquippedPrev",false);
            wall.setItem(0,nativeGun);wall.setPrice(100);PointManager.setScore(player,1000);
            var handler=me.cryo.zombierool.handlers.ServerInteractionHandler.class.getDeclaredMethod("handleWallWeapon",net.minecraft.server.level.ServerPlayer.class,net.minecraft.server.level.ServerLevel.class,BlockPos.class);handler.setAccessible(true);
            handler.invoke(null,player,level,pos);
            var bought=player.getInventory().getItem(1);
            check(WeaponFacade.isTaczWeapon(bought)&&bought.getOrCreateTag().getString("GunId").equals("tacz:ai_awp"),"Buying New World's tagged M40A3 gives the mapped TacZ gun");
            var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(bought);
            check(gun.hasBulletInBarrel(bought)&&WeaponFacade.getAmmo(bought)==WeaponFacade.getMaxAmmo(bought)&&gun.getCurrentAmmoCount(bought)==WeaponFacade.getMaxAmmo(bought)-1,"Bolt-action purchases include the chamber within total capacity");
            check(WeaponFacade.getCareerWeaponId(bought).equals("m40a3"),"Mapped TacZ sniper uses the M40A3 career identity");
            me.cryo.zombierool.career.ServerCareerManager.setEquippedCamos(player,java.util.Map.of("m40a3","camo_gold"));
            check(WeaponFacade.createWeaponStack("m40a3",false,player).getOrCreateTag().getString("zr_camo").equals("camo_gold"),"Newly purchased TacZ weapons inherit the equipped career camo");
            check(bought.getOrCreateTag().getString("zr_camo").equals("camo_gold"),"Equipping a camo updates existing mapped TacZ weapons");
            check(PointManager.getScore(player)==900,"First wall purchase charges the configured weapon price");
            WeaponFacade.setAmmo(bought,0);WeaponFacade.setReserve(bought,0);handler.invoke(null,player,level,pos);
            check(PointManager.getScore(player)==850&&WeaponFacade.getAmmo(bought)>0&&WeaponFacade.getReserve(bought)>0,"Buying again refills the existing TacZ gun at half price");
            check(gun.hasBulletInBarrel(bought),"Wall ammunition refills retain a loaded chamber");
            check(ForgeRegistries.ITEMS.getKey(wall.getItem(0).getItem()).toString().equals("zombierool:m40a3")&&wall.getItem(0).getTag().getInt("Ammo")==7,"Resolution leaves the saved wall template unchanged");
            player.getInventory().clearContent();player.getPersistentData().putBoolean("zr_prefer_zr_weapons",true);handler.invoke(null,player,level,pos);
            check(!WeaponFacade.isTaczWeapon(player.getInventory().getItem(1))&&player.getInventory().getItem(1).getItem()==nativeGun.getItem(),"Explicit classic-weapon preference still gives the native M40A3");
            var configured=bought.copy();configured.getOrCreateTag().putString("AttachmentSCOPE","tacz:scope_mk5hd");
            var preserved=WeaponFacade.resolveGrantedWeapon(configured,player);
            check(WeaponFacade.isTaczWeapon(preserved)&&preserved.getTag().getString("AttachmentSCOPE").equals("tacz:scope_mk5hd"),"Explicitly configured TacZ weapons keep their gun and attachments");
            attachmentTest(player,level,wall,handler,pos);
            capacityTests(player);
            multiMappingTests(player,level,wall,handler,pos);
            glassBulletTests(player,level);
            unmappedCareerTest(player);
            reloadReplacementTest(player,level,wall,handler,pos);
            pauseTest(player);
            scopeTests(level,player,bought);
            System.out.println("TACZ WALL PASS");
        } catch(Throwable error){error.printStackTrace();}
        event.getServer().halt(false);
    }
    private void glassBulletTests(net.minecraft.server.level.ServerPlayer player,net.minecraft.server.level.ServerLevel level){
        var pos=new BlockPos(100,80,30);level.getChunkAt(pos);
        for(int x=-1;x<=1;x++)for(int z=-3;z<=4;z++)for(int y=0;y<3;y++)level.setBlock(pos.offset(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
        var door=me.cryo.zombierool.core.registry.ZRBlocks.GLASS_DEFENSE_DOOR.get();
        var bullet=new com.tacz.guns.entity.EntityKineticBullet(com.tacz.guns.entity.EntityKineticBullet.TYPE,level);bullet.setOwner(player);
        var from=net.minecraft.world.phys.Vec3.atBottomCenterOf(pos).add(0,.6,-2);var to=from.add(0,0,5);
        var ray=new net.minecraft.world.level.ClipContext(from,to,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,bullet);
        try{
            for(int stage=0;stage<=5;stage++){
                var state=door.defaultBlockState().setValue(me.cryo.zombierool.block.system.GlassDefenseDoorBlock.BREACHED,true).setValue(me.cryo.zombierool.block.system.DefenseDoorSystem.DefenseDoorBlock.STAGE,stage);
                level.setBlock(pos,state,2);
                var hit=com.tacz.guns.util.block.BlockRayTrace.rayTraceBlocks(level,ray);
                check(hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS&&hit.getLocation().distanceToSqr(to)<.001,"Real TacZ block trace crosses rebuilt glass-door barricades at stage "+stage);
                check(!state.getCollisionShape(level,pos,net.minecraft.world.phys.shapes.CollisionContext.of(player)).isEmpty(),"Glass-door barricade still blocks a player's body at stage "+stage);
                me.cryo.zombierool.block.system.GlassDefenseDoorBlock.damage(level,pos);
                check(level.getBlockState(pos).getValue(me.cryo.zombierool.block.system.DefenseDoorSystem.DefenseDoorBlock.STAGE)==stage,"Bullets cannot destroy rebuilt glass-door planks at stage "+stage);
            }
            level.setBlock(pos,door.defaultBlockState(),2);
            check(com.tacz.guns.util.block.BlockRayTrace.rayTraceBlocks(level,ray).getType()==net.minecraft.world.phys.HitResult.Type.BLOCK,"Unbroken glass still intercepts a TacZ bullet before becoming a repairable barricade");
        }finally{level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);bullet.discard();}
    }
    private void multiMappingTests(net.minecraft.server.level.ServerPlayer player,net.minecraft.server.level.ServerLevel level,BuyWallWeaponSystem.BuyWallWeaponBlockEntity wall,java.lang.reflect.Method handler,BlockPos pos)throws Exception {
        player.getPersistentData().remove("zr_prefer_zr_weapons");
        for(var pair:java.util.Map.of("m1911","tacz:m1911","m16a4","tacz:m16a4","kar98k","tacz:kar98","ak47","tacz:ak47","mp5","tacz:hk_mp5a5","p90","tacz:p90").entrySet()){
            var def=me.cryo.zombierool.core.system.WeaponSystem.Loader.LOADED_DEFINITIONS.get(pair.getKey());
            var primary=me.cryo.zombierool.integration.TacZMappings.choose(def,id->true);
            check(primary==def.tacz,"Preferred mapping wins when both packs are installed: "+pair.getKey());
            var stack=WeaponFacade.createWeaponStack(pair.getKey(),false,player);
            check(WeaponFacade.isTaczWeapon(stack)&&WeaponFacade.getWeaponId(stack).equals(pair.getValue()),"Missing custom pack falls back to the real default-pack gun: "+pair.getKey());
            check(WeaponFacade.getDefinition(stack)==def&&WeaponFacade.getCareerWeaponId(stack).equals(pair.getKey()),"Fallback retains ZombieRool stats and career identity: "+pair.getKey());
            stack.getTag().remove("zombierool:weapon_id");
            check(WeaponFacade.getDefinition(stack)==def,"Fallback gun is recognized even without saved ZR identity: "+pair.getKey());
            check(WeaponFacade.getAmmo(stack)==WeaponFacade.getMaxAmmo(stack)&&WeaponFacade.getReserve(stack)==WeaponFacade.getMaxReserve(stack),"Fallback purchase fills magazine, chamber and reserve: "+pair.getKey());
            check(!WeaponFacade.getUnmappedTaczGuns().contains(new ResourceLocation(pair.getValue())),"Career does not duplicate a mapped fallback: "+pair.getKey());
        }
        var barrett=WeaponFacade.createWeaponStack("barret",false,player);
        check(WeaponFacade.getWeaponId(barrett).equals("tacz:m107")&&new ResourceLocation("tacz:scope_mk5hd").equals(com.tacz.guns.api.item.IGun.getIGunOrNull(barrett).getAttachmentId(barrett,com.tacz.guns.api.item.attachment.AttachmentType.SCOPE)),"Barrett fallback equips an actual default-pack scope");
        var olympia=WeaponFacade.createWeaponStack("olympia",false,player);
        check(!WeaponFacade.isTaczWeapon(olympia),"Olympia keeps its unique ZR model when the 725 pack is unavailable");
        check(!me.cryo.zombierool.integration.TacZMappings.mappedIds(me.cryo.zombierool.core.system.WeaponSystem.Loader.LOADED_DEFINITIONS.get("olympia")).contains("tacz:db_short"),"Olympia no longer maps to the duplicate Double Barrel family");
        var def=me.cryo.zombierool.core.system.WeaponSystem.Loader.LOADED_DEFINITIONS.get("m1911");
        check(me.cryo.zombierool.integration.TacZMappings.choose(def,id->false)==null,"No available pack returns to the classic ZombieRool weapon");
        var mapping=me.cryo.zombierool.integration.TacZMappings.choose(def);
        String oldPap=mapping.pap_gun_id;var oldAttachments=mapping.attachments;
        try{
            mapping.pap_gun_id="missingpack:unavailable_upgrade";
            var stack=WeaponFacade.createWeaponStack("m1911",true,player);
            check(WeaponFacade.getWeaponId(stack).equals("tacz:m1911")&&WeaponFacade.isPackAPunched(stack)&&WeaponFacade.getDefinition(stack)==def,"Fallback PaP stays in its own pack when its upgraded model is missing");
            var json=new com.google.gson.Gson().toJson(def);
            var decoded=new com.google.gson.Gson().fromJson(json,me.cryo.zombierool.core.system.WeaponSystem.Definition.class);
            check(me.cryo.zombierool.integration.TacZMappings.forGun(decoded,mapping.pap_gun_id)!=null&&me.cryo.zombierool.integration.TacZMappings.variants(decoded).size()==me.cryo.zombierool.integration.TacZMappings.variants(def).size(),"Map configuration and client payload preserve ordered variants and PaP identity");
            mapping.attachments=java.util.Map.of("scope","missingpack:unavailable_scope");
            stack=WeaponFacade.createWeaponStack("m1911",false,player);
            check(!stack.getTag().contains("AttachmentSCOPE"),"Fallback cannot receive an unavailable attachment from another pack");
        }finally{mapping.pap_gun_id=oldPap;mapping.attachments=oldAttachments;}
        player.getInventory().clearContent();player.getInventory().selected=1;PointManager.setScore(player,1000);
        wall.setItem(0,new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("zombierool:m1911"))));handler.invoke(null,player,level,pos);
        var bought=player.getInventory().getItem(1);
        check(WeaponFacade.getWeaponId(bought).equals("tacz:m1911")&&WeaponFacade.getCareerWeaponId(bought).equals("m1911"),"Saved classic wall template buys the available TacZ fallback");
        player.getPersistentData().putBoolean("zr_prefer_zr_weapons",true);
        var classic=WeaponFacade.createWeaponStack("m1911",false,player);
        check(!WeaponFacade.isTaczWeapon(classic)&&classic.getItem()==ForgeRegistries.ITEMS.getValue(new ResourceLocation("zombierool:m1911")),"Classic weapon preference still overrides every TacZ alternative");
        player.getPersistentData().remove("zr_prefer_zr_weapons");player.getInventory().clearContent();
    }
    private void reloadReplacementTest(net.minecraft.server.level.ServerPlayer player,net.minecraft.server.level.ServerLevel level,BuyWallWeaponSystem.BuyWallWeaponBlockEntity wall,java.lang.reflect.Method handler,BlockPos pos)throws Exception {
        player.getInventory().clearContent();player.getInventory().selected=1;
        var rifle=WeaponFacade.createUnmappedTaczWeaponStack(new ResourceLocation("tacz:m16a1"),false);
        var shotgun=WeaponFacade.createUnmappedTaczWeaponStack(new ResourceLocation("tacz:m1014"),false);
        check(!rifle.isEmpty()&&!shotgun.isEmpty(),"Crash regression uses the actual M16A1 and M1014 gun scripts");
        player.getInventory().setItem(1,rifle);player.getInventory().setItem(2,WeaponFacade.createUnmappedTaczWeaponStack(new ResourceLocation("tacz:ai_awp"),false));
        var d=com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(player).getDataHolder();d.initialData();
        d.currentGunItem=player::getMainHandItem;d.drawTimestamp=System.currentTimeMillis()-10000;d.shootTimestamp=-10000;
        var draw=new com.tacz.guns.entity.shooter.LivingEntityDrawGun(player,d);
        var reload=new com.tacz.guns.entity.shooter.LivingEntityReload(player,d,draw,new com.tacz.guns.entity.shooter.LivingEntityShoot(player,d,draw));
        WeaponFacade.setAmmo(rifle,0);com.tacz.guns.api.item.IGun.getIGunOrNull(rifle).setBulletInBarrel(rifle,false);
        reload.reload();
        check(d.reloadTimestamp!=-1&&d.reloadStateType.isReloading(),"M16 reload is active immediately before the wall purchase");
        reload.tickReloadState();
        wall.setItem(0,shotgun);PointManager.setScore(player,1000);handler.invoke(null,player,level,pos);
        var bought=player.getMainHandItem();
        check(bought!=rifle&&WeaponFacade.getWeaponId(bought).equals("tacz:m1014"),"Full inventory wall purchase replaces the actively reloading M16 with the M1014");
        var state=reload.tickReloadState();
        check(!state.getStateType().isReloading()&&d.reloadTimestamp==-1&&!d.reloadStateType.isReloading(),"Weapon replacement ends the old reload before calling the M1014 script");
        check(WeaponFacade.getAmmo(bought)==WeaponFacade.getMaxAmmo(bought)&&com.tacz.guns.api.item.IGun.getIGunOrNull(bought).hasBulletInBarrel(bought),"Replacing a reloading gun leaves the purchased shotgun fully loaded");
        WeaponFacade.setAmmo(bought,0);com.tacz.guns.api.item.IGun.getIGunOrNull(bought).setBulletInBarrel(bought,false);
        reload.reload();
        check(d.reloadTimestamp!=-1&&d.scriptData!=null&&!d.scriptData.isnil()&&d.scriptData.get("interrupted_time").toint()==-1,"A fresh M1014 reload initializes its own Lua cache");
        check(reload.tickReloadState().getStateType().isReloading(),"Normal shotgun reload still runs after the interrupted wall purchase");
        d.reloadTimestamp-=30000;reload.tickReloadState();
        // The shotgun enters its ending animation after feeding the last shells.
        if(d.reloadTimestamp!=-1){d.reloadTimestamp-=5000;reload.tickReloadState();}
        check(d.reloadTimestamp==-1&&WeaponFacade.getAmmo(bought)>0,"Normal M1014 reload completes and actually feeds ammunition");
        WeaponFacade.setAmmo(bought,0);com.tacz.guns.api.item.IGun.getIGunOrNull(bought).setBulletInBarrel(bought,false);
        reload.reload();reload.cancelReload();
        check(d.scriptData.get("interrupted_time").tolong()>=0,"Cancelling an unchanged shotgun still invokes its own Lua interruption handler");
        d.reloadTimestamp-=30000;reload.tickReloadState();
        WeaponFacade.setAmmo(bought,0);reload.reload();
        player.getInventory().setItem(1,bought.copy());reload.cancelReload();
        check(d.reloadTimestamp==-1&&!d.reloadStateType.isReloading(),"Cancellation before the next tick also stops a reload on a replaced stack with the same GunId");
        player.getInventory().setItem(1,rifle);reload.reload();
        check(d.reloadTimestamp!=-1,"M16 can start a new reload after replacing the shotgun");
        player.getInventory().setItem(1,bought);reload.reload();
        check(d.reloadTimestamp!=-1&&d.scriptData.get("interrupted_time").toint()==-1&&reload.tickReloadState().getStateType().isReloading(),"Reload immediately after switching initializes the replacement shotgun before its first tick");
        bought.getOrCreateTag().putString("GunId","tacz:m16a1");
        check(!reload.tickReloadState().getStateType().isReloading()&&d.reloadTimestamp==-1,"Changing the gun definition in place cannot reuse another gun's reload script");
        bought.getOrCreateTag().putString("GunId","tacz:m1014");
        player.getInventory().setItem(1,rifle);reload.reload();
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(me.cryo.zombierool.init.ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get(),100,0));
        long timestamp=d.reloadTimestamp;reload.tickReloadState();
        check(d.reloadTimestamp==timestamp-50,"Speed Cola still accelerates a valid TacZ reload by one extra tick");
        reload.tickReloadState();
        check(d.reloadTimestamp==timestamp-50,"Repeated reload queries during one server tick do not multiply Speed Cola's bonus");
        player.removeEffect(me.cryo.zombierool.init.ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get());
        player.getInventory().setItem(1,ItemStack.EMPTY);
        check(!reload.tickReloadState().getStateType().isReloading()&&d.reloadTimestamp==-1,"Removing the reloading gun clears the action safely");
        player.getInventory().clearContent();d.initialData();
    }
    private void unmappedCareerTest(net.minecraft.server.level.ServerPlayer player){
        var unmapped=WeaponFacade.getUnmappedTaczGuns();
        check(!unmapped.isEmpty(),"The real TacZ pack contains unmapped guns for the career fixture");
        var catalog=WeaponFacade.getCareerWeaponIds();
        check(unmapped.stream().allMatch(id->catalog.contains(id.toString()))&&catalog.contains("m40a3")&&!catalog.contains("tacz:ai_awp"),"Career lists every unmapped TacZ gun while mapped guns keep their ZombieRool entry");
        var id=unmapped.get(0);var stack=WeaponFacade.createCareerPreview(id.toString(),player);
        check(!stack.isEmpty()&&WeaponFacade.getCareerWeaponId(stack).equals(id.toString())&&stack.getTag().getString("GunId").equals(id.toString()),"Unmapped career entries create the matching TacZ preview and independent career identity");
        player.getInventory().setItem(0,stack);
        me.cryo.zombierool.career.ServerCareerManager.setEquippedCamos(player,java.util.Map.of(id.toString(),"camo_woodland","m40a3","camo_gold"));
        check(stack.getTag().getString("zr_camo").equals("camo_woodland"),"Equipping an unmapped gun's camo updates the held TacZ stack");
        var granted=WeaponFacade.resolveGrantedWeapon(WeaponFacade.createUnmappedTaczWeaponStack(id,false),player);
        check(granted.getTag().getString("zr_camo").equals("camo_woodland")&&WeaponFacade.createWeaponStack("m40a3",false,player).getTag().getString("zr_camo").equals("camo_gold"),"Unmapped purchases preserve their equipped camo without changing the mapped sniper's camo");
        player.getInventory().clearContent();
    }
    private void attachmentTest(net.minecraft.server.level.ServerPlayer player,net.minecraft.server.level.ServerLevel level,BuyWallWeaponSystem.BuyWallWeaponBlockEntity wall,java.lang.reflect.Method handler,BlockPos pos)throws Exception {
        var rifle=WeaponFacade.createUnmappedTaczWeaponStack(new ResourceLocation("tacz:m4a1"),false);
        player.getInventory().clearContent();player.getInventory().selected=1;player.getInventory().setItem(1,rifle);
        var operator=com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(player);operator.initialData();
        var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(rifle);gun.setBulletInBarrel(rifle,false);gun.setCurrentAmmoCount(rifle,1);
        var mag=new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz:attachment")));mag.getOrCreateTag().putString("AttachmentId","tacz:extended_mag_3");
        check(TacZIntegration.equipWallAttachment(player,mag),"Actual M4A1 accepts a tier-three magazine wall purchase");
        check(gun.hasBulletInBarrel(rifle)&&WeaponFacade.getAmmo(rifle)==WeaponFacade.getMaxAmmo(rifle)&&WeaponFacade.getMaxAmmo(rifle)>30,"Magazine upgrade fills the larger total capacity immediately");
        check(operator.getCacheProperty()!=null,"Magazine installation immediately rebuilds the actual TacZ ballistic cache");
        for(String attachmentId:java.util.List.of("tacz:sight_coyote","tacz:extended_mag_2")){
            var attachment=new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz:attachment")));attachment.getOrCreateTag().putString("AttachmentId",attachmentId);
            wall.setItem(0,attachment);wall.setPrice(25);PointManager.setScore(player,1000);handler.invoke(null,player,level,pos);
            check(TacZIntegration.hasEquippedAttachment(rifle,attachment)&&PointManager.getScore(player)==975,"The real wall handler equips and charges "+attachmentId);
            check(operator.getCacheProperty()!=null,"Ballistic cache remains valid after wall purchase of "+attachmentId);
            check(WeaponFacade.getWallPurchaseId(attachment).equals("attachment:"+attachmentId),"Chalk identifies the exact installed accessory "+attachmentId);
            var other=attachment.copy();other.getOrCreateTag().putString("AttachmentId",attachmentId.equals("tacz:sight_coyote")?"tacz:sight_552":"tacz:extended_mag_3");
            check(!TacZIntegration.hasEquippedAttachment(rifle,other)&&!WeaponFacade.getWallPurchaseId(other).equals(WeaponFacade.getWallPurchaseId(attachment)),"A different accessory cannot fill this wall's chalk");
            player.setPos(.5,80,10.5);player.xOld=player.xo=player.getX();player.yOld=player.yo=player.getY();player.zOld=player.zo=player.getZ();player.setYRot(-90);player.setXRot(0);gun.setFireMode(rifle,com.tacz.guns.api.item.gun.FireMode.SEMI);
            var d=operator.getDataHolder();d.baseTimestamp=System.currentTimeMillis()-50000;d.drawTimestamp=System.currentTimeMillis()-10000;d.shootTimestamp=-10000;
            int ammo=gun.getCurrentAmmoCount(rifle);
            var before=new java.util.HashSet<Integer>();for(var entity:level.getAllEntities())if(entity instanceof com.tacz.guns.entity.EntityKineticBullet)before.add(entity.getId());
            var target=me.cryo.zombierool.init.ZombieroolModEntities.ZOMBIE.get().create(level);target.setPos(6.5,80,10.5);level.addFreshEntity(target);target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);target.setEmergenceTicks(0);target.getPersistentData().putBoolean("zombierool:no_gore",true);
            var result=operator.shoot(player::getXRot,player::getYRot,System.currentTimeMillis()-d.baseTimestamp);
            var bullets=new java.util.ArrayList<com.tacz.guns.entity.EntityKineticBullet>();for(var entity:level.getAllEntities())if(entity instanceof com.tacz.guns.entity.EntityKineticBullet b&&!before.contains(b.getId()))bullets.add(b);
            check(result==com.tacz.guns.api.entity.ShootResult.SUCCESS&&gun.getCurrentAmmoCount(rifle)==ammo-1&&!bullets.isEmpty(),"A shot immediately after "+attachmentId+" consumes a round and creates a real projectile");
            var origin=bullets.get(0).position();target.setPos(origin.add(bullets.get(0).getDeltaMovement().normalize().scale(6)).add(0,-player.getEyeHeight(),0));
            target.xOld=target.xo=target.getX();target.yOld=target.yo=target.getY();target.zOld=target.zo=target.getZ();
            level.getChunk(target.blockPosition().getX()>>4,target.blockPosition().getZ()>>4);float health=target.getHealth();
            for(var block:BlockPos.betweenClosed(BlockPos.containing(origin).offset(-2,-2,-2),target.blockPosition().offset(2,3,2)))level.setBlock(block,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            for(var bullet:bullets)for(int tick=0;tick<10&&!bullet.isRemoved();tick++)bullet.tick();
            check(target.getHealth()<health,"The projectile after "+attachmentId+" actually damages a ZombieRool enemy");target.discard();for(var bullet:bullets)bullet.discard();
        }
    }
    private void capacityTests(net.minecraft.server.level.ServerPlayer player){
        player.getPersistentData().putBoolean("zr_prefer_zr_weapons",false);
        int count=0;
        for(var def:me.cryo.zombierool.core.system.WeaponSystem.Loader.LOADED_DEFINITIONS.values()){
            if(def.tacz==null||def.tacz.gun_id==null||!TacZIntegration.isTaczGunAvailable(def.tacz.gun_id))continue;
            var stack=WeaponFacade.createWeaponStack(def.id,false,player);
            if(!WeaponFacade.isTaczWeapon(stack))continue;
            // Some maps define default extended attachments. Compare without them.
            stack.getOrCreateTag().remove("AttachmentEXTENDED_MAG");TacZIntegration.fillMagazineAndChamber(stack);
            check(WeaponFacade.getMaxAmmo(stack)==def.ammo.clip_size&&WeaponFacade.getAmmo(stack)==def.ammo.clip_size,"Mapped total capacity matches ZombieRool: "+def.id);
            var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);int magazine=gun.getCurrentAmmoCount(stack);
            check(magazine+(gun.hasBulletInBarrel(stack)?1:0)==def.ammo.clip_size,"Magazine plus chamber fits capacity: "+def.id);
            WeaponFacade.setAmmo(stack,0);check(WeaponFacade.getAmmo(stack)==0&&!gun.hasBulletInBarrel(stack),"Emptying removes the chamber too: "+def.id);
            WeaponFacade.setAmmo(stack,def.ammo.clip_size);check(WeaponFacade.getAmmo(stack)==def.ammo.clip_size,"Full refill restores total capacity: "+def.id);
            var upgraded=WeaponFacade.createWeaponStack(def.id,true,player);
            check(WeaponFacade.getMaxAmmo(upgraded)==def.ammo.clip_size+def.pap.clip_bonus&&WeaponFacade.getAmmo(upgraded)==WeaponFacade.getMaxAmmo(upgraded),"Pack-a-Punch capacity includes chamber and no unsolicited magazine upgrade: "+def.id);
            count++;
        }
        check(count>10,"Capacity checks cover all available mapped guns, including M1014");
        var shotgun=WeaponFacade.createWeaponStack("m1014",false,player);
        check(WeaponFacade.getAmmo(shotgun)==6&&com.tacz.guns.api.item.IGun.getIGunOrNull(shotgun).getCurrentAmmoCount(shotgun)==5,"Mapped M1014 has five magazine shells plus one chamber shell");
        var mag=new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz:attachment")));mag.getOrCreateTag().putString("AttachmentId","tacz:extended_mag_3");
        var rifle=WeaponFacade.createUnmappedTaczWeaponStack(new ResourceLocation("tacz:m4a1"),false);player.getInventory().setItem(1,rifle);player.getInventory().selected=1;
        int before=WeaponFacade.getMaxReserve(rifle);WeaponFacade.setReserve(rifle,before-10);
        check(TacZIntegration.equipWallAttachment(player,mag),"Reserve test uses an M4A1 that accepts extended magazines in the real pack");
        check(WeaponFacade.getMaxReserve(rifle)>before&&WeaponFacade.getReserve(rifle)==WeaponFacade.getMaxReserve(rifle)-10,"Extended magazine adds reserve capacity and the extra ammunition while preserving prior spent reserve");
        player.getInventory().clearContent();
    }
    private void pauseTest(net.minecraft.server.level.ServerPlayer player){
        var d=com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(player).getDataHolder();
        long originalBase=d.baseTimestamp;d.baseTimestamp=System.currentTimeMillis()-10000;
        d.reloadTimestamp=1000;d.boltTimestamp=-1;d.shootTimestamp=400;long base=d.baseTimestamp;
        TacZIntegration.compensatePause(player,5000);
        check(d.reloadTimestamp==6000&&d.boltTimestamp==-1&&d.shootTimestamp==5400&&d.baseTimestamp==base,"TacZ pause shifts active clocks and preserves inactive action sentinels and the synchronized base clock");
        d.reloadTimestamp=-1;d.shootTimestamp=-1;d.baseTimestamp=originalBase;
    }
    private void scopeTests(net.minecraft.server.level.ServerLevel level,net.minecraft.server.level.ServerPlayer player,ItemStack sniper)throws Exception {
        var manager=me.cryo.zombierool.core.manager.LookTriggerManager.class;
        var globalsField=me.cryo.zombierool.scripting.LuaScriptManager.class.getDeclaredField("globals");globalsField.setAccessible(true);
        var probe=new ScreamerProbe(level);
        var globals=me.cryo.zombierool.shadow.luaj.vm2.lib.jse.JsePlatform.standardGlobals();
        globals.set("ZombieroolAPI",me.cryo.zombierool.scripting.LegacySoundBindings.wrap(probe));
        String prototype=null;
        try(var jar=new java.util.zip.ZipFile("D:/Dev/ZombieRool/build/libs/zombierool-26.7-dev.jar");var zip=new java.util.zip.ZipInputStream(jar.getInputStream(jar.getEntry("assets/zombierool/maps/zr_prototype.zip")))){
            for(java.util.zip.ZipEntry entry;(entry=zip.getNextEntry())!=null;)if(entry.getName().equals("zr_scripts/main.lua")){prototype=new String(zip.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);break;}
        }
        check(prototype!=null,"The test reads Prototype's actual packaged Lua script");
        globals.load(prototype,"packaged-prototype").call();globalsField.set(null,globals);
        me.cryo.zombierool.core.manager.LookTriggerManager.clearAll();
        globals.get("OnGameStart").call();
        player.getInventory().selected=1;player.getInventory().setItem(1,sniper.copy());player.setPos(30,-55,-20);aimAt(player,new net.minecraft.world.phys.Vec3(30,-54,-9));
        var playersField=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");playersField.setAccessible(true);
        @SuppressWarnings("unchecked") var players=(java.util.List<net.minecraft.server.level.ServerPlayer>)playersField.get(level.getServer().getPlayerList());boolean added=!players.contains(player);if(added)players.add(player);
        try {
            player.getPersistentData().putBoolean("zr_is_scoping",true);
            com.tacz.guns.entity.sync.ModSyncedEntityData.IS_AIMING_KEY.setValue(player,false);
            for(int i=0;i<55;i++)me.cryo.zombierool.core.manager.LookTriggerManager.tick(level);
            check(probe.screamers==0,"A stale ZombieRool scope flag cannot trigger Prototype while TacZ is not aiming");
            player.getPersistentData().putBoolean("zr_is_scoping",false);
            com.tacz.guns.entity.sync.ModSyncedEntityData.IS_AIMING_KEY.setValue(player,true);
            check(me.cryo.zombierool.core.manager.LookTriggerManager.isScoping(player),"Actual TacZ ADS counts as scoped without the legacy client scope flag");
            for(int i=0;i<49;i++)me.cryo.zombierool.core.manager.LookTriggerManager.tick(level);
            check(probe.screamers==0,"Prototype requires the full 2.5 seconds of scoped looking");
            me.cryo.zombierool.core.manager.LookTriggerManager.tick(level);
            check(probe.screamers==1&&probe.sounds==1,"Prototype's real look trigger invokes both the screamer and its audible sound with a TacZ sniper");
            for(int i=0;i<100;i++)me.cryo.zombierool.core.manager.LookTriggerManager.tick(level);
            check(probe.screamers==1,"Prototype removes its one-shot trigger safely after the screamer");
            globals.get("OnGameStart").call();
            var restored=level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,new net.minecraft.world.phys.AABB(29,-56,-10,32,-52,-7));
            check(restored.size()==1&&restored.get(0).getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(net.minecraft.world.item.Items.PLAYER_HEAD),"Prototype restart recreates the consumed armor stand with its original head");
            globals.get("OnGameStart").call();
            check(level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,new net.minecraft.world.phys.AABB(29,-56,-10,32,-52,-7)).size()==1,"Repeated Prototype starts keep one target rather than duplicate armor stands");
            var nativeGun=new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("zombierool:m40a3")));player.getInventory().setItem(1,nativeGun);player.getPersistentData().putBoolean("zr_is_scoping",true);
            check(me.cryo.zombierool.core.manager.LookTriggerManager.isScoping(player),"Classic ZombieRool sniper scope remains supported");
            player.getInventory().setItem(1,new ItemStack(net.minecraft.world.item.Items.STICK));
            check(!me.cryo.zombierool.core.manager.LookTriggerManager.isScoping(player),"Switching away from a sniper clears scoped trigger eligibility");
            player.getInventory().setItem(1,sniper.copy());
            globals=me.cryo.zombierool.shadow.luaj.vm2.lib.jse.JsePlatform.standardGlobals();globals.set("ZombieroolAPI",me.cryo.zombierool.scripting.LegacySoundBindings.wrap(probe));globalsField.set(null,globals);
            globals.load(java.nio.file.Files.readString(java.nio.file.Path.of("D:/Dev/ZombieRool/run/saves/New World/zr_scripts/zz_tacz_screamer_test.lua")),"new-world-tacz-test").call();
            me.cryo.zombierool.core.manager.LookTriggerManager.clearAll();globals.get("OnGameStart").call();
            var targets=level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,new net.minecraft.world.phys.AABB(-35,-54,39,-32,-49,42)).stream().filter(s->s.getTags().contains("zr_script_look_target")).toList();
            check(targets.size()==1&&targets.get(0).isNoGravity()&&targets.get(0).hasCustomName(),"New World's script creates its named floating-safe pumpkin target");
            player.setPos(-33.5,-53,29);aimAt(player,new net.minecraft.world.phys.Vec3(-33.5,-51.6,40.5));
            int before=probe.screamers;for(int i=0;i<50;i++)me.cryo.zombierool.core.manager.LookTriggerManager.tick(level);
            check(probe.screamers==before+1&&probe.sounds==2,"New World's actual test script triggers the image and Prototype sound through TacZ ADS");
            globals.get("OnGameStart").call();
            check(level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,new net.minecraft.world.phys.AABB(-35,-54,39,-32,-49,42)).stream().filter(s->s.getTags().contains("zr_script_look_target")).count()==1,"Restarting the local test replaces its target without duplicates");
        } finally {if(added)players.remove(player);me.cryo.zombierool.core.manager.LookTriggerManager.clearAll();}
    }
    private void aimAt(net.minecraft.server.level.ServerPlayer player,net.minecraft.world.phys.Vec3 target){
        var delta=target.subtract(player.getEyePosition());player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));player.setYHeadRot(player.getYRot());player.setXRot((float)Math.toDegrees(Math.atan2(-delta.y,Math.hypot(delta.x,delta.z))));
    }
}
