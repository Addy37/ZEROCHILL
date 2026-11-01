package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.viewpager2.widget.ViewPager2;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Analytics v2: local counters and foreground-session summaries, with Eastern calendar periods.
 * Independent random period seeds expire locally; no account or installation identity is used.
 */
final class AnalyticsTracker {
    private static final ScheduledExecutorService NETWORK = Executors.newSingleThreadScheduledExecutor();
    private static final java.util.Map<String,Long> COUNTERS = new java.util.LinkedHashMap<>();
    private static final AnalyticsBatchQueue QUEUE=new AnalyticsBatchQueue((task,delay) -> {
        java.util.concurrent.ScheduledFuture<?> future=NETWORK.schedule(task,delay,TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    },summaries -> {
        JSONArray batch=new JSONArray();
        for(String summary:summaries) batch.put(new JSONObject(summary));
        return post(new JSONObject().put("summaries",batch));
    },5,60_000,30);
    private static Context application;
    private static int started;
    private static long segmentStart;
    private static java.util.concurrent.ScheduledFuture<?> periodic;
    private static final java.time.ZoneId EASTERN=java.time.ZoneId.of("America/New_York");
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private AnalyticsTracker() {
    }

    static void initialize(Context context) {
        if (!isConfigured() || !INITIALIZED.compareAndSet(false, true)) return;
        Context app = context.getApplicationContext();
        application=app;

    }

    static void onActivityCreated(Activity activity, Bundle state) {
        if (activity == null || state != null) return;
        String name = activity.getClass().getSimpleName();
        if (activity instanceof NativeMainActivity) attachMainPager(activity);
        else if ("FavoritesActivity".equals(name)) trackSection(activity, "favorites");
        else if ("DownloadedActivity".equals(name)) trackSection(activity, "downloads");
        else if ("SettingsActivity".equals(name)) trackSection(activity, "settings");
        else if ("ProfileActivity".equals(name)) trackSection(activity, "profile");
        else if (activity instanceof NativeFeedBrowserActivity) trackNativeBrowser(activity);
        else if (activity instanceof BunkrGalleryActivity) trackGalleryViewer(activity);
    }

    static void trackSection(Context context, String section) {
        if (section == null) return;
        track(context, "section", section.trim().toLowerCase(Locale.US));
    }

    // Kept as compatibility entry points. Normal provider usage and creator names never leave device.
    static void trackSource(Context context, String source) { }
    static boolean supportedSource(String source) {
        return java.util.Arrays.asList("crazyshit","efukt","fapzone","fapello","bunkr","wikifeet",
                "wikifeetx","kaotic","baddiehub","xmegadrive","onlyhaven").contains(source);
    }
    static void trackCreator(Context context, String ignored) { track(context,"creator_gallery","all"); }
    static void event(Context context,String metric) { track(context,metric,"all"); }
    static void failure(Context context,String metric,String category) {
        if(!AnalyticsV2Policy.allowed(metric,category)) category="unknown";
        track(context,metric,category);
    }
    static void provider(Context context,String url,String metric) {
        String source="unknown";
        if(BaddieHubRepository.isBaddieHubUrl(url)) source="baddiehub";
        else if(XMegaDriveRepository.isXMegaDriveUrl(url)) source="xmegadrive";
        else if(FapelloRepository.isFapelloUrl(url)) source="fapello";
        else if(BunkrRepository.isBunkrUrl(url)) source="bunkr";
        else if(OnlyHavenRepository.isOnlyHavenUrl(url)) source="onlyhaven";
        else if(EfuktRepository.isEfuktUrl(url)) source="efukt";
        else if(WebVideoSourceRepository.isKaoticUrl(url)) source="kaotic";
        else if(WikiFeetRepository.isWikiFeetUrl(url)) source="wikifeet";
        else if(url!=null && url.contains("crazyshit.com/")) source="crazyshit";
        failure(context,metric,source);
    }
    static void backendFailure(int status) { if(application!=null) track(application,"backend_error",AnalyticsV2Policy.backendCategory(status)); }
    static void onStarted(Activity activity) {
        if (!isConfigured()) return;
        if (started++ != 0) return;
        NETWORK.execute(() -> {
            if(segmentStart!=0) return;
            segmentStart=android.os.SystemClock.elapsedRealtime();
            count("sessions","all",1);
            android.content.SharedPreferences adoption=application.getSharedPreferences("analytics_v2_adoption",Context.MODE_PRIVATE);
            String previous=adoption.getString("version","");
            if(!previous.isEmpty() && !previous.equals(BuildConfig.VERSION_NAME)) count("update_adoption",BuildConfig.VERSION_NAME,1);
            adoption.edit().putString("version",BuildConfig.VERSION_NAME).apply();
            count("app_version",BuildConfig.VERSION_NAME,1);
            count("device_model",deviceModel(Build.MANUFACTURER,Build.MODEL),1);
            count("android_version",androidVersion(Build.VERSION.RELEASE),1);
            periodic=NETWORK.scheduleWithFixedDelay(() -> summarize(false),15,15,TimeUnit.MINUTES);
        });
    }
    static void onStopped(Activity activity) {
        if (!isConfigured() || started==0 || --started!=0 || activity.isChangingConfigurations()) return;
        NETWORK.execute(() -> { if(periodic!=null) periodic.cancel(false); summarize(true); });
    }
    private static void summarize(boolean ending) {
        try {
            long now=android.os.SystemClock.elapsedRealtime();
            if(segmentStart!=0) count("session_ms","all",Math.min(900_000,Math.max(0,now-segmentStart)));
            segmentStart=ending?0:now;
            if(COUNTERS.isEmpty() || application==null) return;
            JSONArray events=new JSONArray();
            for(java.util.Map.Entry<String,Long> entry:COUNTERS.entrySet()) {
                String[] key=entry.getKey().split("\n",2);
                events.put(new JSONObject().put("metric",key[0]).put("value",key[1]).put("count",entry.getValue()));
            }
            COUNTERS.clear();
            LocalDate day=LocalDate.now(EASTERN);
            JSONObject active=new JSONObject();
            String[] names={"day","week","month"};
            LocalDate[] periods={day,day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),day.withDayOfMonth(1)};
            android.content.SharedPreferences prefs=application.getSharedPreferences("analytics_v2_periods",Context.MODE_PRIVATE);
            for(int i=0;i<3;i++) {
                String period=periods[i].toString();
                String seed=prefs.getString(names[i]+"_seed","");
                if(!period.equals(prefs.getString(names[i]+"_period","")) || seed.isEmpty()) {
                    seed=java.util.UUID.randomUUID().toString();
                    prefs.edit().putString(names[i]+"_period",period).putString(names[i]+"_seed",seed).apply();
                }
                active.put(names[i]+"_key",anonymousKey(seed,names[i],periods[i]));
                active.put(names[i]+"_period",period);
            }
            JSONObject payload=new JSONObject().put("version",BuildConfig.VERSION_NAME).put("events",events).put("active",active);
            QUEUE.add(payload.toString());
            QUEUE.flushNow(); // Reuse the existing bounded retry/cooldown batching transport.

        } catch(Exception ignored) { /* Best effort. Never affects app operation. */ }
    }
    private static void count(String metric,String value,long amount) {
        if(!AnalyticsV2Policy.allowed(metric,value) || amount<=0 || COUNTERS.size()>=160 && !COUNTERS.containsKey(metric+"\n"+value)) return;
        String key=metric+"\n"+value;
        COUNTERS.put(key,Math.min(metric.equals("session_ms")?900_000:metric.equals("sessions")?1:10_000,COUNTERS.getOrDefault(key,0L)+amount));
    }

    private static void attachMainPager(Activity activity) {
        View root = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        if (root == null) {
            trackSection(activity, "home");
            return;
        }
        root.post(() -> {
            ViewPager2 pager = findPrimaryPager(root);
            if (pager == null) {
                trackSection(activity, "home");
                return;
            }
            trackPrimaryPage(activity, pager.getCurrentItem());
            pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    trackPrimaryPage(activity, position);
                }
            });
        });
    }

    private static ViewPager2 findPrimaryPager(View view) {
        if (view instanceof ViewPager2) return (ViewPager2) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            ViewPager2 found = findPrimaryPager(group.getChildAt(index));
            if (found != null) return found;
        }
        return null;
    }

    private static void trackPrimaryPage(Context context, int position) {
        if (position == MainPagerAdapter.PAGE_SERIES) trackSection(context, "collections");
        else if (position == MainPagerAdapter.PAGE_CHAOS) trackSection(context, "chaos");
        else if (position == MainPagerAdapter.PAGE_LIBRARY) trackSection(context, "library");
        else trackSection(context, "home");
    }

    private static void trackNativeBrowser(Activity activity) {
        Intent intent = activity.getIntent();
        if (intent == null) return;
        String creator = value(intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_BUNKR_CREATOR_QUERY));
        if (!creator.isEmpty()) {
            String title = value(intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_TITLE));
            trackCreator(activity, title.isEmpty() ? creator : title);
            return;
        }
        String source = value(intent.getStringExtra(NativeFeedBrowserActivity.EXTRA_SOURCE));
        if (!source.isEmpty()) trackSource(activity, source);
    }

    private static void trackGalleryViewer(Activity activity) {
        Intent intent = activity.getIntent();
        if (intent == null) return;
        String creator = value(intent.getStringExtra(BunkrGalleryActivity.EXTRA_CREATOR_QUERY));
        if (creator.isEmpty()) return;
        String title = value(intent.getStringExtra(BunkrGalleryActivity.EXTRA_TITLE));
        trackCreator(activity, title.isEmpty() ? creator : title);
    }

    private static void track(Context context, String metric, String value) {
        if (!isConfigured() || context==null) return;
        if(metric.equals("section")) {
            if(value.equals("chaos")) value="shittok";
            else if(value.equals("collections")) value="shows";
            else if(value.equals("home")) value="onlyfap";
            else if(value.equals("search") || value.equals("favorites") || value.equals("creator_gallery")) {metric=value;value="all";}
        }
        final String m=metric,v=value;
        if(!AnalyticsV2Policy.allowed(m,v)) return;
        NETWORK.execute(() -> count(m,v,1));
    }

    private static boolean isConfigured() {
        return !BuildConfig.ANALYTICS_V2_ENDPOINT.trim().isEmpty();
    }

    static String anonymousKey(String seed,String period,LocalDate periodStart) throws Exception {
        String input = "analytics-v2\n" + seed + '\n' + period + '\n' + periodStart;
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte part : digest) result.append(String.format(Locale.US, "%02x", part & 0xff));
        return result.toString();
    }

    private static int post(JSONObject payload) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(BuildConfig.ANALYTICS_V2_ENDPOINT).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(7_000);
            connection.setReadTimeout(10_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Cache-Control", "no-store");
            connection.setRequestProperty("X-CrazyShit-Analytics", "1");
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                InputStream error = connection.getErrorStream();
                if (error != null) error.close();
                return status;
            }
            InputStream input = connection.getInputStream();
            if (input != null) input.close();
            return status;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static String deviceModel(String manufacturer, String model) {
        String make = cleanDevicePart(manufacturer);
        String name = cleanDevicePart(model);
        if (name.isEmpty()) return make.isEmpty() ? "Unknown" : make;
        if (make.isEmpty() || name.regionMatches(true, 0, make, 0, make.length())
                && (name.length() == make.length() || " -_".indexOf(name.charAt(make.length())) >= 0)) {
            return name;
        }
        return (make + " " + name).substring(0, Math.min(80, make.length() + 1 + name.length())).trim();
    }

    static String androidVersion(String release) {
        String value = release == null ? "" : release.trim();
        return value.matches("[0-9]{1,2}(\\.[0-9]{1,2})?") ? value : "Unknown";
    }

    static String deviceManufacturer(String manufacturer) {
        String value = cleanDevicePart(manufacturer);
        return value.isEmpty() ? "Unknown" : value;
    }

    private static String cleanDevicePart(String raw) {
        if (raw == null) return "";
        String clean = raw.trim().replaceAll(" +", " ");
        if (clean.isEmpty() || clean.equalsIgnoreCase("unknown") || clean.length() > 80) {
            if (clean.length() > 80) clean = clean.substring(0, 80).trim();
            else return "";
        }
        return clean.matches("[\\p{L}\\p{N} ._+()/\\-]{1,80}") ? clean : "";
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
