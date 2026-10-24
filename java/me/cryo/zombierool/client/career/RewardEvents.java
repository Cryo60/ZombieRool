package me.cryo.zombierool.client.career;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
public final class RewardEvents {
 private static final Path URL_FILE=Path.of(System.getProperty("user.home"),".zombierool","reward-events-url.txt");
 public record Event(String name,String code){}
 public static String url(){try{return Files.readString(URL_FILE).trim();}catch(Exception e){return "";}}
 public static void saveUrl(String url)throws Exception{if(!url.isBlank()&&!"https".equalsIgnoreCase(java.net.URI.create(url.trim()).getScheme()))throw new IllegalArgumentException("Use an HTTPS raw Gist URL");Files.createDirectories(URL_FILE.getParent());Files.writeString(URL_FILE,url.trim());}
 public static void fetch(java.util.function.BiConsumer<java.util.List<Event>,String> callback){java.util.concurrent.CompletableFuture.runAsync(()->{var events=new ArrayList<Event>();String message="";try{
  String url=url();if(url.isEmpty())throw new IllegalArgumentException("Paste the raw URL of your public event Gist");if(!me.cryo.zombierool.configuration.ZRClientConfig.allowNetworkRequests())throw new IllegalArgumentException("Online requests are disabled; signed codes still work offline");
  var client=java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(6)).followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build();var req=java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).timeout(java.time.Duration.ofSeconds(10)).GET().build();var response=client.send(req,java.net.http.HttpResponse.BodyHandlers.ofInputStream());String json;try(var stream=response.body()){if(response.statusCode()!=200)throw new IllegalArgumentException("Events unavailable (HTTP "+response.statusCode()+")");byte[] bytes=stream.readNBytes(256001);if(bytes.length>256000)throw new IllegalArgumentException("Event manifest too large");json=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);}
  var mc=net.minecraft.client.Minecraft.getInstance();byte[] key=RewardVoucher.publicKey();
  for(var item:JsonParser.parseString(json).getAsJsonArray()){if(events.size()>=100)break;var obj=item.getAsJsonObject();String code=obj.get("code").getAsString();try{RewardVoucher.verify(code,key,mc.getUser().getUuid(),System.currentTimeMillis()/1000);events.add(new Event(obj.get("name").getAsString(),code));}catch(Exception ignored){}}
  message=events.isEmpty()?"No active events":"Active events: "+events.size();
 }catch(Exception e){message=String.valueOf(e.getMessage());}var result=java.util.List.copyOf(events);String status=message;net.minecraft.client.Minecraft.getInstance().execute(()->callback.accept(result,status));});}
}
