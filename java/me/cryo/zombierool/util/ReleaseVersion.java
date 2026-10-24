package me.cryo.zombierool.util;

import java.util.regex.Pattern;

/** Release tags historically include both the Minecraft version and mod version. */
public final class ReleaseVersion {
    private static final Pattern VERSION=Pattern.compile("^(\\d+(?:\\.\\d+)+)(?:[-_](.*))?$");
    private ReleaseVersion() {}
    public static String normalize(String value) {
        return value.trim().replaceFirst("(?i)^zombierool[-_ v]*","")
                .replaceFirst("^1\\.20\\.1[-_]","").replaceFirst("(?i)^v(?=\\d)","");
    }
    public static boolean newer(String available,String installed) {
        var a=VERSION.matcher(normalize(available));var b=VERSION.matcher(normalize(installed));
        if(!a.matches() || !b.matches())return false;
        String[] left=a.group(1).split("\\."),right=b.group(1).split("\\.");
        for(int i=0;i<Math.max(left.length,right.length);i++) {
            int compare=new java.math.BigInteger(i<left.length?left[i]:"0").compareTo(new java.math.BigInteger(i<right.length?right[i]:"0"));
            if(compare!=0)return compare>0;
        }
        String aq=a.group(2)==null?"":a.group(2).toLowerCase(java.util.Locale.ROOT),bq=b.group(2)==null?"":b.group(2).toLowerCase(java.util.Locale.ROOT);
        if(aq.equals(bq))return false;
        if(aq.matches("hotfix\\d+") || bq.matches("hotfix\\d+")) {
            int ah=aq.matches("hotfix\\d+")?Integer.parseInt(aq.substring(6)):0;
            int bh=bq.matches("hotfix\\d+")?Integer.parseInt(bq.substring(6)):0;
            if(ah!=bh)return ah>bh;
        }
        return aq.isEmpty() && !bq.isEmpty();
    }
}
