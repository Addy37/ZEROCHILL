package com.webapp.crazyshit;

import android.app.Activity;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.util.*;

final class MediaCollectionPicker {
    private MediaCollectionPicker() { }

    static void show(Activity activity, NativeContentItem item, String galleryKey, Runnable changed) {
        if (item == null || activity.isFinishing() || activity.isDestroyed()) return;
        Set<String> selected = new LinkedHashSet<>();
        for (MediaCollectionStore.Group group : MediaCollectionStore.load(activity))
            if (group.urls.contains(item.url)) selected.add(group.id);
        showSelection(activity, item, galleryKey, selected, changed);
    }

    private static void showSelection(Activity activity, NativeContentItem item, String galleryKey,
            Set<String> selected, Runnable changed) {
        List<MediaCollectionStore.Group> groups = MediaCollectionStore.load(activity);
        if (groups.isEmpty()) {
            name(activity, "New collection", "", value -> {
                selected.add(MediaCollectionStore.create(activity, value));
                showSelection(activity, item, galleryKey, selected, changed);
            });
            return;
        }
        String[] labels = new String[groups.size()];
        boolean[] checked = new boolean[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            labels[i] = groups.get(i).name;
            checked[i] = selected.contains(groups.get(i).id);
        }
        new ZeroChillDialog.Builder(activity).setTitle("Save to collections")
                .setMultiChoiceItems(labels, checked, (dialog, index, value) -> {
                    String id = groups.get(index).id;
                    if (value) selected.add(id); else selected.remove(id);
                })
                .setNeutralButton("+ New collection", (dialog, which) ->
                        name(activity, "New collection", "", value -> {
                            selected.add(MediaCollectionStore.create(activity, value));
                            showSelection(activity, item, galleryKey, selected, changed);
                        }))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Done", (dialog, which) -> {
                    try {
                        MediaCollectionStore.assign(activity, item, galleryKey, selected);
                        if (changed != null) changed.run();
                    } catch (Exception error) { failure(activity); }
                }).show();
    }

    interface NameAction { void accept(String name) throws Exception; }

    static void name(Activity activity, String title, String initial, NameAction action) {
        EditText input = new ZeroChillEditText(activity);
        input.setSingleLine(true);
        input.setHint("Collection name");
        input.setText(initial);
        input.setSelection(input.length());
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(MediaCollectionStore.MAX_NAME)});
        LinearLayout container = new LinearLayout(activity);
        int padding = BrowseUi.dp(activity, 20);
        container.setPadding(padding, 0, padding, 0);
        container.addView(input, new LinearLayout.LayoutParams(-1, -2));
        android.app.AlertDialog dialog = new ZeroChillDialog.Builder(activity).setTitle(title)
                .setView(container).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) { input.setError("Enter a name"); return; }
                    try { action.accept(value); dialog.dismiss(); }
                    catch (Exception error) { input.setError("Couldn't save collection"); }
                }));
        dialog.show();
    }

    static void failure(Activity activity) {
        ZeroChillToast.makeText(activity, "Couldn't update collections.", ZeroChillToast.LENGTH_SHORT).show();
    }
}
