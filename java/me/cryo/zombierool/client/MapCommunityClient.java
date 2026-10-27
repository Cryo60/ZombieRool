package me.cryo.zombierool.client;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import me.cryo.zombierool.configuration.ZRClientConfig;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Fixed community endpoints; verified Minecraft identity and short-lived, memory-only review session. */
public final class MapCommunityClient {
    public static final String SERVICE="https://zombierool-community.onrender.com";
    private static final ExecutorService IO=Executors.newFixedThreadPool(4,r->{Thread t=new Thread(r,"ZR-MapCatalogue");t.setDaemon(true);return t;});
    private static String token="",account="";
    private static long expires;
    private MapCommunityClient(){}
    public record Catalogue(List<MapCatalogue.Entry> entries,boolean cached){}
    public static <T> CompletableFuture<T> async(Callable<T> task){return CompletableFuture.supplyAsync(()->{try{return task.call();}catch(Exception e){throw new CompletionException(e);}},IO);}
    private static void allowed() throws IOException {if(!ZRClientConfig.allowNetworkRequests())throw new IOException("network_disabled");}
    private static void source(String source){if(!Set.of("official","community").contains(source))throw new IllegalArgumentException("source");}
    public static Catalogue load(String source) throws IOException {
        source(source);Path cache=Minecraft.getInstance().gameDirectory.toPath().resolve("config/zombierool/catalogue/"+source+".json");
        try{
            allowed();String repo=source.equals("official")?"zombierool-maps":"zombierool-community-hub";
            var json=request("GET","https://raw.githubusercontent.com/Cryo60/"+repo+"/main/maps.json",null,"");
            var entries=MapCatalogue.parse(source,json);
            try{Files.createDirectories(cache.getParent());Files.writeString(cache,json.toString(),StandardCharsets.UTF_8);}catch(IOException ignored){}
            return new Catalogue(entries,false);
        }catch(Exception error){
            try{if(Files.size(cache)<=4*1024*1024)return new Catalogue(MapCatalogue.parse(source,JsonParser.parseString(Files.readString(cache,StandardCharsets.UTF_8)).getAsJsonObject()),true);}catch(Exception ignored){}
            throw new IOException(error instanceof IOException?error.getMessage():"service_unavailable");
        }
    }
    public static List<MapCatalogue.Review> reviews(MapCatalogue.Entry map) throws IOException {
        return MapCatalogue.parseReviews(request("GET",path(map)+"/reviews",null,"").get("reviews"));
    }
    private static String path(MapCatalogue.Entry map){source(map.source);if(!map.id.matches("[a-zA-Z0-9_.-]{1,160}"))throw new IllegalArgumentException("map");return SERVICE+"/api/minecraft/maps/"+map.source+"/"+map.id;}
    public static synchronized List<MapCatalogue.Review> submit(MapCatalogue.Entry map,int stars,String comment) throws Exception {
        allowed();if(stars<1||stars>5||comment.length()>1000)throw new IOException("invalid_review");
        var mc=Minecraft.getInstance();String player=mc.getUser().getUuid();
        if(!account.equals(player)||System.currentTimeMillis()+5000>=expires){
            var body=new JsonObject();body.addProperty("username",mc.getUser().getName());
            var challenge=request("POST",SERVICE+"/api/minecraft/auth/challenge",body,"");
            allowed();
            try{mc.getMinecraftSessionService().joinServer(mc.getUser().getGameProfile(),mc.getUser().getAccessToken(),challenge.get("serverId").getAsString());}
            catch(com.mojang.authlib.exceptions.AuthenticationException e){throw new IOException("account_verification_failed");}
            var verify=new JsonObject();verify.addProperty("challenge",challenge.get("challenge").getAsString());
            var session=request("POST",SERVICE+"/api/minecraft/auth/verify",verify,"");
            token=session.get("token").getAsString();expires=session.get("expires").getAsLong();account=player;
        }
        var body=new JsonObject();body.addProperty("stars",stars);body.addProperty("comment",comment);
        try{return MapCatalogue.parseReviews(request("POST",path(map)+"/review",body,token).get("reviews"));}
        catch(IOException e){if("session_expired".equals(e.getMessage())){token="";expires=0;}throw e;}
    }
    public static JsonObject request(String method,String url,JsonObject body,String bearer) throws IOException {
        allowed();var conn=(HttpURLConnection)URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(10000);conn.setReadTimeout(20000);conn.setRequestMethod(method);conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Accept","application/json");conn.setRequestProperty("User-Agent","ZombieRool-MapCatalogue");
        if(!bearer.isEmpty())conn.setRequestProperty("Authorization","Bearer "+bearer);
        try{
            if(body!=null){conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json; charset=utf-8");try(var out=conn.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int status=conn.getResponseCode();InputStream input=status>=400?conn.getErrorStream():conn.getInputStream();
            JsonObject result;
            try(input){result=input==null?new JsonObject():JsonParser.parseString(new String(bounded(input,4*1024*1024),StandardCharsets.UTF_8)).getAsJsonObject();}
            catch(RuntimeException e){throw new IOException(status==404?"service_update_required":"service_unavailable");}
            if(status<200||status>=300)throw new IOException(MapCatalogue.text(result,"error",status==404?"service_update_required":"service_unavailable"));
            return result;
        }finally{conn.disconnect();}
    }
    public static byte[] image(String url) throws IOException {
        allowed();if(!validMapLink(url))throw new IOException("image_url");var conn=(HttpURLConnection)URI.create(url).toURL().openConnection();conn.setConnectTimeout(5000);conn.setReadTimeout(10000);
        try(var in=conn.getInputStream()){return bounded(in,CatalogueImageDecoder.MAX_BYTES);}finally{conn.disconnect();}
    }
    private static byte[] bounded(InputStream in,int max) throws IOException {byte[] bytes=in.readNBytes(max+1);if(bytes.length>max)throw new IOException("response_too_large");return bytes;}
    public static boolean validMapLink(String value){try{var uri=URI.create(value);return ("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme()))&&uri.getHost()!=null&&uri.getUserInfo()==null;}catch(Exception e){return false;}}
    public static String ownId(){return "minecraft:"+Minecraft.getInstance().getUser().getUuid().replace("-","").toLowerCase(Locale.ROOT);}
    public static String error(Throwable error){while(error.getCause()!=null)error=error.getCause();String message=error.getMessage();return Set.of("network_disabled","account_verification_failed","session_expired","content_blocked","rate_limited","banned","invalid_review","service_update_required","map_not_found").contains(message==null?"":message)?message:"service_unavailable";}
}
