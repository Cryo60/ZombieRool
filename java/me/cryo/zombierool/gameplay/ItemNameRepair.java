package me.cryo.zombierool.gameplay;
import com.google.gson.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.network.chat.Component;
/** Migrate legacy color-code text saved by earlier item grants. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class ItemNameRepair {
 private static final java.util.regex.Pattern LEGACY=java.util.regex.Pattern.compile("(?:\u00c3\u201a|\u00c2)*\u00a7[0-9a-fk-or]",java.util.regex.Pattern.CASE_INSENSITIVE);
 public static String clean(String text){
  for(int i=0;i<3&&(text.contains("Ã")||text.contains("Â")||text.contains("â€"));i++)try{
   var bytes=java.nio.charset.Charset.forName("windows-1252").newEncoder().onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text));
   String fixed=java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(bytes).toString();if(fixed.equals(text))break;text=fixed;
  }catch(java.nio.charset.CharacterCodingException ignored){break;}
  return LEGACY.matcher(text).replaceAll("");
 }
 private static void fix(JsonElement e){if(e.isJsonObject()){var o=e.getAsJsonObject();if(o.has("text")&&o.get("text").isJsonPrimitive())o.addProperty("text",clean(o.get("text").getAsString()));for(var row:o.entrySet())if(row.getValue().isJsonObject()||row.getValue().isJsonArray())fix(row.getValue());}else if(e.isJsonArray())for(var child:e.getAsJsonArray())fix(child);}
 public static boolean repair(net.minecraft.world.item.ItemStack stack){
  if(stack.hasTag()&&"mp7_cirrus".equals(stack.getTag().getString("zombierool:weapon_id"))){stack.getTag().putString("zombierool:weapon_id","mp7");stack.resetHoverName();}
  if(!stack.hasCustomHoverName())return false;var display=stack.getOrCreateTag().getCompound("display");String raw=display.getString("Name");if(!raw.contains("§")&&!raw.contains("Â")&&!raw.contains("Ã")&&!raw.contains("â€")&&!raw.contains("\\u00a7"))return false;
  try{var json=JsonParser.parseString(raw);if(json.isJsonPrimitive())json=new JsonPrimitive(clean(json.getAsString()));else fix(json);String cleaned=json.toString();if(raw.equals(cleaned))return false;var name=Component.Serializer.fromJson(cleaned);if(name==null)return false;stack.setHoverName(name);return true;}catch(RuntimeException ignored){return false;}
 }
 @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){if(e.phase!=TickEvent.Phase.END||e.player.level().isClientSide||e.player.tickCount%20!=0)return;for(var stack:e.player.getInventory().items)repair(stack);for(var stack:e.player.getInventory().armor)repair(stack);repair(e.player.getOffhandItem());}
}
