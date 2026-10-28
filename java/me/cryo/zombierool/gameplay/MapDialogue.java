package me.cryo.zombierool.gameplay;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Shared speaker formatting for map-authored dialogue and voice subtitles. */
public final class MapDialogue {
    private MapDialogue(){}
    public static Component format(String name,String text){
        return Component.literal("<"+clean(name,80)+"> : "+clean(text,512)).withStyle(ChatFormatting.YELLOW);
    }
    private static String clean(String text,int limit){String value=text==null?"":text.replaceAll("[\\r\\n\\t]+"," ").replace("\u00a7","").trim();return value.substring(0,Math.min(limit,value.length()));}
    public static void send(ServerPlayer player,String name,String text){if(player!=null&&text!=null&&!text.isBlank())player.sendSystemMessage(format(name,text));}
}
