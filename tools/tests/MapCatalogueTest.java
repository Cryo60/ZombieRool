import com.google.gson.*;
import me.cryo.zombierool.client.MapCatalogue;
import java.util.*;
public class MapCatalogueTest {
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);System.out.println("CATALOGUE DATA CHECK: "+name);}
 static MapCatalogue.Entry map(String id,String author,String version,int count,int stars){var j=new JsonObject();j.addProperty("id",id);j.addProperty("name",id);j.addProperty("author",author);j.addProperty("zr_version",version);var r=new JsonArray();for(int i=0;i<count;i++){var a=new JsonObject();a.addProperty("user_id","u"+i);a.addProperty("stars",stars);r.add(a);}j.add("reviews",r);return new MapCatalogue.Entry("community",j);}
 public static void main(String[] args){
  var one=map("one","a","26.7",1,5);var established=map("established","b","26.7",100,4);
  check(established.ratingScore()>one.ratingScore(),"Confidence-weighted ranking avoids promoting one five-star vote above an established good map");
  var old=map("old","c","1.6.0",100,5);var list=List.of(one,established,old);
  var featured=MapCatalogue.featured(list,"26.7",1792740000000L,3);
  check(!featured.contains(old)&&featured.size()==2,"Current maps take precedence over outdated maps in automatic featured selection");
  check(featured.equals(MapCatalogue.featured(list,"26.7",1792740000000L,3)),"Featured selection stays deterministic within a week");
  check(MapCatalogue.featured(List.of(old),"26.7",1792740000000L,3).equals(List.of(old)),"All-outdated catalogues still offer maps with their compatibility badges");
  check(MapCatalogue.compatibility("1.6.0","26.7")==-1&&MapCatalogue.compatibility("26.8","26.7")==1&&MapCatalogue.compatibility("26.7","26.7")==0&&MapCatalogue.compatibility("unknown","26.7")==-2,"Compatibility distinguishes outdated, current, newer and unknown versions");
  check(MapCatalogue.timestamp(new JsonPrimitive(1770000000L))==1770000000000L&&MapCatalogue.timestamp(new JsonPrimitive("2026-01-01"))>0,"Catalogue accepts seconds, milliseconds and calendar dates");
  var parsed=MapCatalogue.parse("official",JsonParser.parseString("{\"maps\":[{\"id\":\"visible\",\"name\":\"Nacht\",\"tags\":[\"survival\"]},{\"id\":\"hidden\",\"status\":\"pending\"},{\"id\":\"visible\"},{\"id\":\"../bad\"}]}").getAsJsonObject());
  check(parsed.size()==1&&parsed.get(0).matches("SURVIVAL")&&parsed.get(0).matches("nacht"),"Parsing excludes pending, duplicate and invalid maps; search matches titles and tags");
  var reviews=MapCatalogue.parseReviews(JsonParser.parseString("[{\"user_id\":\"a\",\"stars\":1},{\"user_id\":\"a\",\"stars\":5,\"comment\":\"hi §4there\"},{\"user_id\":\"b\",\"stars\":6}]"));
  check(reviews.size()==1&&reviews.get(0).stars()==5&&!reviews.get(0).comment().contains("§"),"Review parsing deduplicates accounts and rejects invalid stars and formatting codes");
  System.out.println("CATALOGUE DATA PASS");
 }
}
