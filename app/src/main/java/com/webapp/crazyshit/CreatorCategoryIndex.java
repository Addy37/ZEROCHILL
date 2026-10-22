package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Only records category membership established by a provider category response. */
final class CreatorCategoryIndex {
    private static final String PREFS = "onlyhaven_confirmed_categories_v1";
    private CreatorCategoryIndex() { }

    static synchronized void remember(Context context, ContentPreference category,
                                      List<OnlyHavenRepository.Creator> creators) {
        if (context == null || category == ContentPreference.ALL || creators == null) return;
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, 0);
        SharedPreferences.Editor edit = prefs.edit();
        for (OnlyHavenRepository.Creator creator : creators) {
            if (creator == null || creator.url.isEmpty()) continue;
            java.util.Set<String> modes = new HashSet<>(prefs.getStringSet(creator.url,
                    java.util.Collections.emptySet()));
            modes.add(category.value);
            edit.putStringSet(creator.url, modes);
        }
        edit.apply();
    }

    static boolean includes(Context context, ContentPreference category, NativeContentItem item) {
        if (category == ContentPreference.ALL) return true;
        if (context == null || item == null || !OnlyHavenRepository.isOnlyHavenUrl(item.url)) return false;
        return context.getApplicationContext().getSharedPreferences(PREFS, 0)
                .getStringSet(item.url, java.util.Collections.emptySet()).contains(category.value);
    }

    static List<NativeContentItem> filter(Context context, ContentPreference category,
                                          List<NativeContentItem> items) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        if (items != null) for (NativeContentItem item : items)
            if (includes(context, category, item)) result.add(item);
        return result;
    }
}
