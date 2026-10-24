package me.cryo.zombierool.integration;

import com.google.gson.*;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Immutable reload chosen before any ammunition changes, shared by server and animation. */
public final class TacZBoltReloadPlan {
    public record Step(String animation, long duration, int rounds) {}
    public final List<Step> steps;
    public final boolean empty;
    public final long duration;
    private static final JsonObject TIMINGS=load();
    private TacZBoltReloadPlan(List<Step> steps,boolean empty){this.steps=List.copyOf(steps);this.empty=empty;this.duration=steps.stream().mapToLong(Step::duration).sum();}
    private static JsonObject load(){
        try(var in=TacZBoltReloadPlan.class.getResourceAsStream("/data/zombierool/bolt_reload_timings.json")){
            if(in==null)throw new IOException("Missing bolt reload timings");
            return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    public static boolean supports(ItemStack stack){var gun=IGun.getIGunOrNull(stack);return gun!=null&&TIMINGS.has(gun.getGunId(stack).toString())&&me.cryo.zombierool.core.system.WeaponFacade.getDefinition(stack)!=null&&!stack.getOrCreateTag().getBoolean("zombierool:unmapped");}
    public static TacZBoltReloadPlan create(ItemStack stack){
        if(!supports(stack))return null;
        var gun=IGun.getIGunOrNull(stack);var t=TIMINGS.getAsJsonObject(gun.getGunId(stack).toString());
        int ammo=me.cryo.zombierool.core.system.WeaponFacade.getAmmo(stack),missing=Math.max(0,me.cryo.zombierool.core.system.WeaponFacade.getMaxAmmo(stack)-ammo);
        missing=Math.min(missing,me.cryo.zombierool.core.system.WeaponFacade.getReserve(stack));
        if(missing==0)return null;
        boolean empty=ammo==0;var steps=new ArrayList<Step>();
        // A clip is only used for a completely empty rifle. Partial magazines receive individual rounds.
        if(empty&&missing>=5&&t.has("reload_empty")) add(steps,t,"reload_empty",missing);
        else if(empty&&missing>=5&&t.has("reload_load_clip")){
            add(steps,t,t.has("reload_start_clip_empty")?"reload_start_clip_empty":"reload_start_clip",0);
            add(steps,t,"reload_load_clip",missing);add(steps,t,"reload_end_clip",0);
        }else{
            String start=empty&&t.has("reload_start_empty")?"reload_start_empty":ammo==1&&t.has("reload_start_1round")?"reload_start_1round":t.has("reload_start")?"reload_start":"reload_intro";
            add(steps,t,start,0);
            for(int i=0;i<missing;i++)add(steps,t,i==0&&empty&&t.has("reload_loop_1round")?"reload_loop_1round":"reload_loop",1);
            add(steps,t,"reload_end",0);
        }
        return new TacZBoltReloadPlan(steps,empty);
    }
    private static void add(List<Step> out,JsonObject t,String name,int rounds){if(!t.has(name))throw new IllegalStateException("Missing reload animation "+name);out.add(new Step(name,t.get(name).getAsLong(),rounds));}
}
