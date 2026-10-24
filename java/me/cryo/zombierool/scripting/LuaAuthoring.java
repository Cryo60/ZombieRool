package me.cryo.zombierool.scripting;
import java.util.*;
/** Pure authoring model: guided rules generate ordinary, editable Lua. */
public final class LuaAuthoring {
 public record Event(String label,String function,String args,String help,boolean player,boolean wave,boolean kill){}
 public record Action(String label,String method,String parameter,String defaultValue,String help,boolean player){}
 public record Step(int action,String value){}
 public static final List<Event> EVENTS=List.of(
  new Event("Game start","OnGameStart","","Runs once when the match starts.",false,false,false),
  new Event("Wave start","OnWaveStart","wave","Runs at the start of each round. wave is the round number.",false,true,false),
  new Event("Zombie killed","OnZombieKill","playerUUID, mobType, isHeadshot","After a kill: player UUID, enemy type and headshot flag.",true,false,true),
  new Event("Block interaction","OnBlockInteract","playerUUID, x, y, z","When a player interacts with a block.",true,false,false),
  new Event("Power activated","OnPowerActivated","","When the power is activated.",false,false,false),
  new Event("Thief steals a weapon","OnThiefWeaponStolen","enemyUUID, playerUUID","After a held weapon is stolen. The full stack is retained by the Thief.",true,false,false),
  new Event("Thief returns a weapon","OnThiefWeaponReturned","enemyUUID, playerUUID","After the stolen stack is restored to its owner.",true,false,false),
  new Event("Thief escapes","OnThiefEscaped","enemyUUID","The Thief has escaped and the special round can finish.",false,false,false),
  new Event("Romero rage","OnRomeroRageChanged","enemyUUID, enraged","Called only when calm/rage changes.",false,false,false),
  new Event("Astronaut grabs","OnAstronautGrab","enemyUUID, playerUUID","At the start of the headbutt windup.",true,false,false),
  new Event("Astronaut teleports","OnAstronautTeleport","enemyUUID, playerUUID, x, y, z","Only emitted when a safe destination is found.",true,false,false),
  new Event("Abomination hook","OnAbominationHook","enemyUUID, playerUUID, pullDistance","A physical hook hits a player.",true,false,false),
  new Event("Geist leap","OnGeistLeap","enemyUUID, playerUUID","The telegraphed leap locks its direction.",true,false,false),
  new Event("Romero ground slam","OnRomeroGroundSlam","enemyUUID","At the electrical impact of his projector.",false,false,false),
  new Event("Romero zombie buff","OnRomeroZombieBuff","enemyUUID, kind, count","kind is electric or speed; count is affected zombies.",false,false,false),
  new Event("Crawler leap","OnCrawlerLeap","enemyUUID, playerUUID","Nova or Geist starts a dodgeable leap windup.",true,false,false),
  new Event("Crawler leap impact","OnCrawlerLeapHit","enemyUUID, playerUUID","Actual bounding-box contact during a crawler leap.",true,false,false),
  new Event("Music volume toggled","OnMusicVolumeToggle","playerUUID, volumeId, active, preset","On entry or Lua toggle: active is true for activation and false for restoration.",true,false,false),
  new Event("Music volume entered","OnMusicVolumeEnter","playerUUID, volumeId","Physical entry only; leaving does not change the music.",true,false,false),
  new Event("Music volume exited","OnMusicVolumeExit","playerUUID, volumeId","Physical exit only.",true,false,false),
  new Event("Hidden enemy spawned","OnHiddenEnemySpawned","playerUUID, enemyUUID, profileId, x, y, z","Only after a successful hidden spawn on map path blocks.",true,false,false));
 public static final List<Action> ACTIONS=List.of(
  new Action("Message to everyone","broadcastMessage","Message","The room is pressurized.","Shows a chat message to every player.",false),
  new Action("Unlock channel","unlockChannel","Channel","1","Unlocks the selected logic channel.",false),
  new Action("Pressurize a zone","configureAtmosphereZone","Zone ID","pressure_lab","Makes an existing zone breathable. Use its exact ID.",false),
  new Action("Give points","addPoints","Points","500","Gives points to the player from this event.",true),
  new Action("Give weapon","giveWeapon","Weapon ID","glock","Gives the weapon to the player from this event.",true),
  new Action("Voice event","playVoiceEvent","Voice event","level_start","Plays a voice event for the player from this event.",true),
  new Action("Enable zombie swimming","setZombieSwimming","Enabled (true/false)","true","Active ou desactive la nage des zombies.",false),
  new Action("Finish Outnumbered","finishIntroPhase","No parameter","","Ends Outnumbered and resumes normal rounds.",false),
  new Action("Global sound","playGlobalSound","Sound ID","minecraft:block.note_block.pling","Plays a registered sound for every player.",false));
 public static String quote(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\"";}
 public static String generate(int eventIndex,int condition,String conditionValue,double delay,List<Step> steps,String id){
  if(!id.matches("[A-Za-z0-9_]{1,80}"))throw new IllegalArgumentException("Invalid rule ID");Event event=EVENTS.get(eventIndex);if(steps.isEmpty())throw new IllegalArgumentException("Add at least one action.");if(!Double.isFinite(delay)||delay<0||delay>3600)throw new IllegalArgumentException("Delay must be between 0 and 3600 seconds.");
  String guard="";if(condition==1){if(!event.wave)throw new IllegalArgumentException("The round condition requires Wave start.");guard="wave >= "+integer(conditionValue,1,100000);}if(condition==2){if(!event.kill)throw new IllegalArgumentException("Headshot requires Zombie killed.");guard="isHeadshot == true";}if(condition==3){if(!event.kill)throw new IllegalArgumentException("Enemy type requires Zombie killed.");guard="mobType == "+quote(conditionValue);}
  StringBuilder actions=new StringBuilder();for(Step step:steps){Action action=ACTIONS.get(step.action);if(action.player&&!event.player)throw new IllegalArgumentException(action.label+" requires an event with a player.");String value=step.value;String args=switch(step.action){case 0,8->quote(value);case 1->""+integer(value,0,100000);case 2->quote(value)+", { vacuum = false }";case 3->"playerUUID, "+integer(value,1,1000000);case 4,5->"playerUUID, "+quote(value);case 6->{if(!value.equals("true")&&!value.equals("false"))throw new IllegalArgumentException("Enabled must be true or false.");yield value;}case 7->"";default->throw new IllegalArgumentException("Unknown action");};actions.append("    ZombieroolAPI:").append(action.method).append('(').append(args).append(")\n");}
  String body=actions.toString();if(delay>0)body="    ZombieroolAPI:registerTimer("+quote("rule_"+id)+", "+Math.round(delay*20)+", function()\n"+body.indent(4)+"    end)\n";if(!guard.isEmpty())body="    if "+guard+" then\n"+body.indent(4)+"    end\n";
  String previous="zr_previous_"+id;return "\n-- Guided rule: "+event.label+"\nlocal "+previous+" = "+event.function+"\nfunction "+event.function+"("+event.args+")\n    if type("+previous+") == \"function\" then "+previous+"("+event.args+") end\n"+body+"end\n";
 }
 private static int integer(String value,int min,int max){try{int n=Integer.parseInt(value);if(n>=min&&n<=max)return n;}catch(Exception ignored){}throw new IllegalArgumentException("Expected an integer between "+min+" a "+max);}
}
