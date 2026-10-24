package me.cryo.zombierool.client;

import com.google.gson.*;
import java.time.*;
import java.util.*;
import org.apache.maven.artifact.versioning.ComparableVersion;

/** Catalogue metadata and deterministic discovery ranking, with no network or render dependencies. */
public final class MapCatalogue {
    private MapCatalogue() {}
    public record Review(String userId,String user,int stars,String comment,String date) {}
    public static final class Entry {
        public final String source,id,name,author,description,imageUrl,downloadUrl,gameVersion,zrVersion,version;
        public final List<String> tags,mods,gunpacks;
        public final long timestamp;
        public final int downloads;
        public List<Review> reviews;
        public int installed; // 0 absent, 1 installed, 2 update available
        public Entry(String source,JsonObject json){
            this.source=source;id=text(json,"id","");name=clean(text(json,"name",id));author=clean(text(json,"author","ZombieRool"));description=clean(text(json,"description",""));
            imageUrl=text(json,"image_url","");downloadUrl=text(json,"download_url","");gameVersion=text(json,"game_version","?");zrVersion=text(json,"zr_version","");version=text(json,"version","?");
            tags=strings(json,"tags");mods=strings(json,"mods");gunpacks=strings(json,"tacz_gunpacks");
            downloads=Math.max(0,integer(json,"downloads",0));timestamp=timestamp(json.get("timestamp"));reviews=parseReviews(json.get("reviews"));
        }
        public String key(){return source+":"+id;}
        public float average(){return reviews.isEmpty()?0:(float)reviews.stream().mapToInt(Review::stars).average().orElse(0);}
        public double ratingScore(){return (reviews.size()*average()+3*3.5)/(reviews.size()+3);}
        public boolean matches(String query){String q=query.toLowerCase(Locale.ROOT).trim();return (name+" "+author+" "+description+" "+String.join(" ",tags)).toLowerCase(Locale.ROOT).contains(q);}
    }
    public static List<Entry> parse(String source,JsonObject json){
        var result=new ArrayList<Entry>();var ids=new HashSet<String>();
        if(!json.has("maps")||!json.get("maps").isJsonArray())throw new IllegalArgumentException("Missing maps array");
        for(var element:json.getAsJsonArray("maps"))try{
            var obj=element.getAsJsonObject();String status=text(obj,"status","approved");
            if(!"approved".equals(status))continue;
            Entry entry=new Entry(source,obj);
            if(entry.id.matches("[a-zA-Z0-9_.-]{1,160}")&&ids.add(entry.id))result.add(entry);
            if(result.size()>=2000)break;
        }catch(RuntimeException ignored){}
        return result;
    }
    public static List<Review> parseReviews(JsonElement value){
        var result=new LinkedHashMap<String,Review>();
        if(value==null||!value.isJsonArray())return List.of();
        for(var element:value.getAsJsonArray())try{
            var r=element.getAsJsonObject();int stars=integer(r,"stars",0);if(stars<1||stars>5)continue;
            String id=text(r,"user_id","");if(id.isEmpty())continue;
            result.put(id,new Review(id,clean(text(r,"user","Player")),stars,clean(text(r,"comment","")),clean(text(r,"date",""))));
        }catch(RuntimeException ignored){}
        return List.copyOf(result.values());
    }
    private static String clean(String s){return s.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]|\u00a7.","");}
    private static int integer(JsonObject j,String key,int fallback){try{return j.get(key).getAsInt();}catch(RuntimeException e){return fallback;}}
    public static String text(JsonObject j,String key,String fallback){try{return j.get(key).getAsString();}catch(RuntimeException e){return fallback;}}
    private static List<String> strings(JsonObject j,String key){var list=new ArrayList<String>();if(j.has(key)&&j.get(key).isJsonArray())for(var e:j.getAsJsonArray(key))try{if(list.size()<64)list.add(e.getAsString());}catch(RuntimeException ignored){}return List.copyOf(list);}
    public static long timestamp(JsonElement value){
        if(value==null||value.isJsonNull())return 0;
        try{long t=value.getAsLong();return t<100000000000L?t*1000:t;}catch(RuntimeException ignored){}
        try{return Instant.parse(value.getAsString()).toEpochMilli();}catch(RuntimeException ignored){}
        try{return LocalDate.parse(value.getAsString()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();}catch(RuntimeException ignored){return 0;}
    }
    /** -2 unknown, -1 older, 0 current, 1 needs newer mod. */
    public static int compatibility(String target,String installed){
        if(target==null||!target.trim().matches("[0-9]+(?:\\.[0-9]+)*(?:[-+][a-zA-Z0-9.-]+)?"))return -2;
        return Integer.signum(new ComparableVersion(target.trim()).compareTo(new ComparableVersion(installed)));
    }
    public static List<Entry> featured(List<Entry> entries,String version,long now,int limit){
        var candidates=new ArrayList<>(entries);boolean current=candidates.stream().anyMatch(e->compatibility(e.zrVersion,version)==0);
        if(current)candidates.removeIf(e->compatibility(e.zrVersion,version)!=0);
        long week=now/(7L*86400000);
        candidates.sort(Comparator.<Entry>comparingDouble(e->discoveryScore(e,now,week)).reversed().thenComparing(Entry::key));
        var result=new ArrayList<Entry>();var authors=new HashSet<String>();
        for(var e:candidates)if(authors.add(e.author.toLowerCase(Locale.ROOT))){result.add(e);if(result.size()==limit)return result;}
        for(var e:candidates)if(!result.contains(e)){result.add(e);if(result.size()==limit)break;}
        return result;
    }
    private static double discoveryScore(Entry e,long now,long week){
        double age=e.timestamp==0?365:Math.max(0,(now-e.timestamp)/86400000.0);
        double rotate=new Random(31L*e.key().hashCode()+week).nextDouble();
        return e.ratingScore()+Math.log1p(e.downloads)*.13+Math.exp(-age/30)*.9+rotate*.65;
    }
}
