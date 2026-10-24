package local.skingalleryclient;
import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.core.system.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
public final class StarterAndTeleporterTest {
 private static int checks;
 private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("STARTER TELEPORT CHECK: "+label);}
 public static int run(ServerPlayer p)throws Exception{
  var l=p.serverLevel();var config=me.cryo.zombierool.config.WorldConfig.get(l);var saved=config.save(new CompoundTag());boolean prefer=p.getPersistentData().getBoolean("zr_prefer_zr_weapons");var original=p.position();var a=new BlockPos(850,65,0);var b=new BlockPos(860,65,0);
  try{
   var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("zombierool:glock"));check(item!=null&&item!=net.minecraft.world.item.Items.AIR,"Native Glock is available for starter selection");var nativeGun=new ItemStack(item);nativeGun.getOrCreateTag().putInt("ammo",1);nativeGun.setHoverName(net.minecraft.network.chat.Component.literal("Starter Glock"));config.setStarterStack(nativeGun);p.getPersistentData().putBoolean("zr_prefer_zr_weapons",false);
   var gun=WeaponFacade.createStarterStack(p,config);check(WeaponFacade.isTaczWeapon(gun)&&gun.getOrCreateTag().getString("GunId").contains("glock"),"Saved native Glock starter resolves to available TACZ mapping");check(gun.getHoverName().getString().equals("Starter Glock"),"Mapped starter preserves its custom name");
   p.getPersistentData().putBoolean("zr_prefer_zr_weapons",true);check(!WeaponFacade.isTaczWeapon(WeaponFacade.createStarterStack(p,config)),"Classic weapon preference still gives the native starter");p.getPersistentData().putBoolean("zr_prefer_zr_weapons",false);gun.getOrCreateTag().putInt("test_custom_tag",42);config.setStarterStack(gun);check(WeaponFacade.createStarterStack(p,config).getOrCreateTag().getInt("test_custom_tag")==42,"Explicit TACZ starter retains its saved custom data");
   for(var pos:java.util.List.of(a,b)){l.setBlock(pos.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);l.setBlock(pos,TeleporterSystem.PLATFORM.get().defaultBlockState(),2);var pad=(TeleporterSystem.Pad)l.getBlockEntity(pos);var n=TeleporterConfig.defaults(TeleporterConfig.Mode.DIRECT);n.putString("id",pos==a?"creative-from":"creative-to");n.putString("destination",pos==a?"creative-to":"creative-from");n.putBoolean("enabled",false);n.putInt("cost",1000000);n.putInt("unlockAfterSeconds",3600);pad.configure(n);pad.onLoad();}
   p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.teleportTo(l,850.5,65.126,.5,0,0);int points=me.cryo.zombierool.gameplay.PointManager.getScore(p);var channels=new java.util.HashSet<>(me.cryo.zombierool.gameplay.WaveManager.UNLOCKED_CHANNELS);
   me.cryo.zombierool.handlers.ServerInteractionHandler.handleInteraction(p,a,me.cryo.zombierool.network.InteractionType.TELEPORTER);
   check(p.getX()>859,"Creative F instantly follows Destination ID despite power, price and delay gates");check(points==me.cryo.zombierool.gameplay.PointManager.getScore(p)&&channels.equals(me.cryo.zombierool.gameplay.WaveManager.UNLOCKED_CHANNELS),"Creative preview does not charge points or change gameplay channels");
   var pad=(TeleporterSystem.Pad)l.getBlockEntity(a);pad.config.putBoolean("enabled",true);pad.config.putBoolean("requiresPower",false);pad.config.putInt("cost",0);check(((net.minecraft.network.chat.contents.TranslatableContents)pad.interactionHint(false,"F").getContents()).getKey().endsWith("use_free"),"Zero-cost teleporter uses a prompt without a price");pad.cooldownUntil=l.getGameTime()+100;check(pad.readyInSeconds()==5&&pad.failureMessage("message.zombierool.teleporter.cooldown").getString().contains("5"),"Unavailable teleporter reports remaining cooldown seconds");
  }finally{config.loadEditable(saved);p.getPersistentData().putBoolean("zr_prefer_zr_weapons",prefer);p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.teleportTo(l,original.x,original.y,original.z,0,0);for(var pos:java.util.List.of(a,b))l.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);}
  System.out.println("STARTER TELEPORT PASS: "+checks+" checks");return checks;
 }
}
