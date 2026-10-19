package com.webapp.crazyshit;

import android.content.Context;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

/** Device-local submitted searches shared by the two search entry points. */
final class RecentSearchStore {
    static final String PREFS = "recent_searches";
    private static final int LIMIT = 10;
    private RecentSearchStore() { }

    static List<String> load(Context context) {
        ArrayList<String> result = new ArrayList<>();
        try {
            JSONArray values = new JSONArray(context.getSharedPreferences(PREFS, 0).getString("queries", "[]"));
            for (int i = 0; i < values.length() && result.size() < LIMIT; i++) {
                String query = values.optString(i, "").trim();
                if (query.length() >= 2 && query.length() <= 200
                        && result.stream().noneMatch(q -> q.equalsIgnoreCase(query))) result.add(query);
            }
        } catch (Exception ignored) { }
        return result;
    }

    static synchronized void remember(Context context, String value) {
        String query = value == null ? "" : value.trim();
        if (query.length() < 2 || query.length() > 200) return;
        List<String> values = load(context);
        values.removeIf(q -> q.equalsIgnoreCase(query));
        values.add(0, query);
        if (values.size() > LIMIT) values = new ArrayList<>(values.subList(0, LIMIT));
        save(context, values);
    }

    static synchronized void remove(Context context, String query) {
        List<String> values = load(context);
        values.removeIf(q -> q.equalsIgnoreCase(query));
        save(context, values);
    }

    static synchronized void clear(Context context) { save(context, new ArrayList<>()); }
    private static void save(Context context, List<String> values) {
        context.getSharedPreferences(PREFS, 0).edit().putString("queries", new JSONArray(values).toString()).apply();
    }
}
