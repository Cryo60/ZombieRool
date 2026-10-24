package local.skingalleryfixture;
import com.google.gson.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.network.chat.Component;
import me.cryo.zombierool.entity.*;
import me.cryo.zombierool.init.ZombieroolModEntities;
import java.nio.file.*;
@Mod("skingalleryfixture") @Mod.EventBusSubscriber(modid="skingalleryfixture")
public class SkinGalleryWorldBuilder {
 private static boolean built;private static int age,count;private static MinecraftServer server;
 @SubscribeEvent public static void start(ServerStartedEvent event){server=event.getServer();server.execute(()->{try{build();built=true;}catch(Throwable e){e.printStackTrace();server.halt(false);}});}
 private static void build()throws Exception{
  var level=server.overworld();server.getWorldData().setGameType(net.minecraft.world.level.GameType.CREATIVE);server.setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
  var rules=level.getGameRules();for(var rule:java.util.List.of(GameRules.RULE_DOMOBSPAWNING,GameRules.RULE_DAYLIGHT,GameRules.RULE_WEATHER_CYCLE,GameRules.RULE_MOBGRIEFING))rules.getRule(rule).set(false,server);
  rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true,server);level.setDayTime(6000);level.setDefaultSpawnPos(new BlockPos(18,65,-5),0);
  me.cryo.zombierool.config.WorldConfig.get(level).setHellhoundFireVariant(false);
  var config=new net.minecraft.nbt.CompoundTag();config.putString("mapId","zr_skin_gallery");config.putBoolean("spookyAmbience",false);me.cryo.zombierool.config.WorldConfig.get(level).loadEditable(config);
  var catalog=me.cryo.zombierool.config.EnemySkinPresets.catalog();
  var names=JsonParser.parseReader(new java.io.InputStreamReader(me.cryo.zombierool.config.EnemySkinPresets.class.getResourceAsStream("/assets/zombierool/lang/fr_fr.json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
  int rows=catalog.getAsJsonObject("zombies").size()+1;
  for(int cx=-1;cx<=4;cx++)for(int cz=-1;cz<=rows*7/16;cz++)level.setChunkForced(cx,cz,true);
  for(int x=-4;x<=50;x++)for(int z=-9;z<=rows*7;z++){
   level.setBlock(new BlockPos(x,64,z),((z%7==0)?Blocks.POLISHED_DEEPSLATE:Blocks.SMOOTH_STONE).defaultBlockState(),3);
   if(x==-4||x==50)level.setBlock(new BlockPos(x,65,z),Blocks.IRON_BARS.defaultBlockState(),3);
  }
  var layout=new JsonArray();int row=0;
  for(var entry:catalog.getAsJsonObject("zombies").entrySet()){
   String preset=entry.getKey(),title=names.get("config.zombierool.value."+preset).getAsString();var variants=entry.getValue().getAsJsonObject().getAsJsonArray("variants");
   sign(level,new BlockPos(-1,65,row*7+2),title,"Zombies",variants.size()+" variantes","Approchez-vous");
   for(int i=0;i<variants.size();i++){
    int x=3+i*6,z=row*7+3;level.setBlock(new BlockPos(x,64,z),Blocks.SEA_LANTERN.defaultBlockState(),3);
    var entity=ZombieroolModEntities.ZOMBIE.get().create(level);String id="@preset/zombies/"+preset+"/"+i;prepare(entity,x+.5,65,z+.5,title+" "+(i+1),id);if(!level.addFreshEntity(entity))throw new IllegalStateException("Gallery entity rejected: "+entity.getCustomSkin());
    var record=new JsonObject();record.addProperty("skin",id);record.addProperty("x",x+.5);record.addProperty("y",65);record.addProperty("z",z+.5);record.addProperty("name",title+" "+(i+1));layout.add(record);count++;
   }row++;
  }
  int i=0;for(var entry:catalog.getAsJsonObject("hounds").entrySet())for(int j=0;j<entry.getValue().getAsJsonObject().getAsJsonArray("variants").size();j++){
   int x=3+i++*6,z=row*7+3;level.setBlock(new BlockPos(x,64,z),Blocks.SEA_LANTERN.defaultBlockState(),3);var entity=ZombieroolModEntities.HELLHOUND.get().create(level);prepare(entity,x+.5,65,z+.5,"Chien "+entry.getKey()+" "+(j+1),"@preset/hounds/"+entry.getKey()+"/"+j);if(!level.addFreshEntity(entity))throw new IllegalStateException("Gallery entity rejected: "+entity.getCustomSkin());count++;
  }
  sign(level,new BlockPos(-1,65,row*7+2),"Chiens infernaux","Toutes variantes","Classique / Infernal","");
  sign(level,new BlockPos(15,65,-7),"GALERIE DES SKINS","66 zombies / 2 chiens","Creatif - sans combat","Chaque ligne = preset");
  sign(level,new BlockPos(20,65,-7),"COMMANDES UTILES","/time set night","/time set day","F3+T : textures");
  var station=me.cryo.zombierool.block.system.LunarStation.STATION.get();var stationPos=new BlockPos(18,65,-3);var stationState=station.defaultBlockState();level.setBlock(stationPos,stationState,3);station.setPlacedBy(level,stationPos,stationState,null,net.minecraft.world.item.ItemStack.EMPTY);
  for(int x=51;x<=75;x++)for(int z=-8;z<=9;z++)level.setBlock(new BlockPos(x,64,z),(x>=55?Blocks.END_STONE:Blocks.SMOOTH_STONE).defaultBlockState(),3);
  var environment=me.cryo.zombierool.gameplay.LunarEnvironment.get(level);environment.put("lunar_demo",new net.minecraft.world.phys.AABB(55,64,-8,75,90,10),.167,true);
  environment.put("pressurized_room",new net.minecraft.world.phys.AABB(58,65,-4,65,70,4),1,false);
  for(int x=57;x<=65;x++)for(int z=-5;z<=4;z++)for(int y=65;y<=70;y++)if(y==70||x==57||x==65||z==-5||z==4)if(!(x==57&&z>=-1&&z<=1&&y<=67))level.setBlock(new BlockPos(x,y,z),Blocks.GLASS.defaultBlockState(),3);
  sign(level,new BlockPos(52,65,-3),"TEST LUNAIRE","A partir de x=55","Vide / faible gravite","Tester en survie");
  sign(level,new BlockPos(58,65,-3),"SALLE PRESSURISEE","Air et gravite Terre","H : mettre / retirer","casque independant");
  sign(level,new BlockPos(21,65,-7),"STATION LUNAIRE","F : recuperer casque","H : mettre / retirer","Test lunaire a droite");
  Files.writeString(Path.of("D:/Dev/ZombieRool/build/zombie-presets/gallery-layout.json"),new GsonBuilder().setPrettyPrinting().create().toJson(layout),java.nio.charset.StandardCharsets.UTF_8);
 }
 private static void prepare(AbstractZombieRoolEntity e,double x,double y,double z,String name,String skin){e.setCustomSkin(skin);e.setCustomEyeSkin("");e.setPos(x,y,z);e.setYRot(180);e.setYHeadRot(180);e.setYBodyRot(180);e.setNoAi(true);e.setNoGravity(true);e.setInvulnerable(true);e.setSilent(true);e.setPersistenceRequired();e.setCustomName(Component.literal(name));e.setCustomNameVisible(true);e.getPersistentData().putBoolean("zr_skin_gallery",true);e.getPersistentData().putBoolean("zr_debug_spawn",true);}
 private static void sign(net.minecraft.server.level.ServerLevel level,BlockPos p,String a,String b,String c,String d){level.setBlock(p,Blocks.OAK_SIGN.defaultBlockState(),3);if(level.getBlockEntity(p) instanceof SignBlockEntity s){var text=new SignText();String[] lines={a,b,c,d};for(int k=0;k<4;k++)text=text.setMessage(k,Component.literal(lines[k]));s.setText(text,true);s.setText(text,false);s.setChanged();}}
 @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){if(!built||event.phase!=TickEvent.Phase.END)return;if(++age==100){System.out.println("SKIN GALLERY WORLD PASS: "+count+" persistent exhibits");server.halt(false);}}
}
