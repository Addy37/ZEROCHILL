package com.webapp.crazyshit;

/** Finite categories only; callers must never pass exception text, URLs, titles or social content. */
final class AnalyticsV2Policy {
    static boolean allowed(String metric,String value) {
        if(metric==null || value==null) return false;
        switch(metric) {
            case "section": return java.util.Arrays.asList("shittok","shows","onlyfap","library").contains(value);
            case "provider_failure": case "provider_missing":
                return java.util.Arrays.asList("crazyshit","efukt","fapzone","fapello","bunkr","wikifeet","wikifeetx","kaotic","baddiehub","xmegadrive","onlyhaven","unknown").contains(value);
            case "playback_failure": return java.util.Arrays.asList("network","decoder","source","unknown").contains(value);
            case "backend_error": case "account_error":
                return java.util.Arrays.asList("auth","permission","rate_limit","network","server","invalid","unknown").contains(value);
            case "app_version": case "update_adoption": return value.matches("[A-Za-z0-9._+\\-]{1,40}");
            case "device_model": return value.matches("[\\p{L}\\p{N} ._+()/\\-]{1,80}");
            case "android_version": return value.matches("(?:[0-9]{1,2}(\\.[0-9]{1,2})?|Unknown)");
            case "sessions": case "session_ms": case "shittok_videos": case "buffering":
            case "search": case "favorites": case "creator_gallery": case "update_prompt":
            case "account_signin": case "comments": case "likes": case "dms": return value.equals("all");
            default: return false;
        }
    }
    static boolean adoptedUpdate(String previous,String current,long firstInstall,long lastUpdate) {
        return previous.isEmpty() ? firstInstall>0 && lastUpdate>firstInstall : !previous.equals(current);
    }
    static String playbackCategory(int code) {
        if(code>=2000 && code<3000) return "network";
        if(code>=3000 && code<4000) return "source";
        if(code>=4000 && code<5000) return "decoder";
        return "unknown";
    }
    static String backendCategory(int status) {
        if(status==401) return "auth";
        if(status==403) return "permission";
        if(status==429) return "rate_limit";
        if(status>=500) return "server";
        if(status>=400) return "invalid";
        return "network";
    }
}
