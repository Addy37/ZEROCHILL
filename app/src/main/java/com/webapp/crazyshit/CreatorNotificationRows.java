package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Expansion is view state only. Persisted read state remains in UpdateInboxStore. */
final class CreatorNotificationRows {
    private final Set<String> expanded = new HashSet<>();
    private List<UpdateInboxStore.Entry> groups = new ArrayList<>();

    List<UpdateInboxStore.Entry> replace(List<UpdateInboxStore.Entry> entries) {
        groups = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
        return rows();
    }
    boolean isExpanded(UpdateInboxStore.Entry entry) { return expanded.contains(key(entry)); }
    List<UpdateInboxStore.Entry> toggle(UpdateInboxStore.Entry entry) {
        String key = key(entry);
        if (!expanded.remove(key)) expanded.add(key);
        return rows();
    }
    ArrayList<String> expandedKeys() { return new ArrayList<>(expanded); }
    void restore(List<String> keys) { expanded.clear(); if (keys != null) expanded.addAll(keys); }
    private String key(UpdateInboxStore.Entry entry) {
        return "creator:" + CreatorNameMatcher.normalized(entry.creatorName);
    }
    private List<UpdateInboxStore.Entry> rows() {
        ArrayList<UpdateInboxStore.Entry> result = new ArrayList<>();
        for (UpdateInboxStore.Entry parent : groups) {
            result.add(parent);
            if (!UpdateInboxStore.CATEGORY_ONLYFAP.equals(parent.category) || !isExpanded(parent)) continue;
            for (UpdateInboxStore.CreatorItem item : parent.children) {
                UpdateInboxStore.Entry child = new UpdateInboxStore.Entry();
                child.id = parent.id + ":child:" + item.id;
                child.category = "creator_item";
                child.sourceKey = item.sourceKey;
                child.sourceLabel = item.sourceKey;
                child.title = item.item.isImage() ? "Photo" : item.item.isVideo() ? "Video" : "Content";
                child.subtitle = item.sourceKey + (item.read ? " · Read" : " · Unread");
                child.timestamp = item.timestamp;
                child.read = item.read;
                child.items.add(item.item);
                child.creatorItem = item;
                child.creatorParent = parent;
                result.add(child);
            }
        }
        return result;
    }
}
