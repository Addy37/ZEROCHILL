package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Isolated, opt-in native provider test surface. Never alters the shared feed preference. */
public final class PawchiveBrowseActivity extends Activity {
    private final PawchiveRepository repository = new PawchiveRepository();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Future<?> task;
    private int generation;
    private boolean busy;
    private int offset;
    private boolean hasMore;
    private String query = "";
    private PawchiveRepository.Creator creator;
    private PawchiveRepository.Post post;
    private List<PawchiveRepository.Creator> catalog;
    private final Set<String> seen = new HashSet<>();
    private LinearLayout rows;
    private TextView heading, status, more;
    private EditText search;
    private ScrollView scroll;
    private int accent;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        accent = AppearanceTheme.accent(this);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = action("‹", 26);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> goBack());
        bar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        heading = text("Pawchive", 20, Color.WHITE);
        heading.setTypeface(null, Typeface.BOLD);
        bar.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        TextView refresh = action("↻", 24);
        refresh.setContentDescription("Refresh");
        refresh.setOnClickListener(v -> {
            if (creator == null && post == null) catalog = null;
            restart();
        });
        bar.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(bar);
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xff999da3);
        search.setHint("Search creators");
        search.setTextSize(15);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(200)});
        search.setOnEditorActionListener((v, id, event) -> {
            if (id == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                submitSearch(); return true;
            }
            return false;
        });
        searchBar.addView(search, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView submit = action("Search", 14);
        submit.setOnClickListener(v -> submitSearch());
        searchBar.addView(submit, new LinearLayout.LayoutParams(dp(82), dp(48)));
        root.addView(searchBar);
        status = text("", 14, 0xffb6bbc4);
        status.setPadding(dp(8), dp(10), dp(8), dp(10));
        root.addView(status);
        scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        more = action("Load more", 15);
        more.setOnClickListener(v -> load());
        root.addView(more, new LinearLayout.LayoutParams(-1, dp(52)));
        setContentView(root);
        restart();
    }

    private void submitSearch() {
        if (post != null) return;
        String value = search.getText().toString().trim();
        if (creator != null && !value.isEmpty() && value.length() < 3) {
            status.setText("Use at least three characters to search posts.");
            return;
        }
        query = value;
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
        restart();
    }

    private void restart() {
        generation++;
        if (task != null) task.cancel(true);
        busy = false;
        offset = 0;
        hasMore = true;
        seen.clear();
        rows.removeAllViews();
        scroll.scrollTo(0, 0);
        load();
    }

    private void load() {
        if (busy || !hasMore || isFinishing()) return;
        busy = true;
        more.setVisibility(View.GONE);
        status.setText("Loading Pawchive…");
        final int request = generation, page = offset;
        final String term = query;
        final PawchiveRepository.Creator selectedCreator = creator;
        final PawchiveRepository.Post selectedPost = post;
        final List<PawchiveRepository.Creator> cached = catalog;
        task = io.submit(() -> {
            try {
                if (selectedPost != null) {
                    List<PawchiveRepository.Media> media = repository.postMedia(selectedPost);
                    publish(request, () -> {
                        for (PawchiveRepository.Media item : media) addMedia(item, selectedPost);
                        finishPage(false, media.isEmpty() ? "No supported public media in this post." : "");
                    });
                } else if (selectedCreator != null) {
                    PawchiveRepository.PostPage result = repository.creatorPosts(
                            selectedCreator.service, selectedCreator.id, term, page);
                    publish(request, () -> {
                        int count = 0;
                        for (PawchiveRepository.Post item : result.posts) {
                            if (seen.add(item.key())) { addPost(item); count++; }
                        }
                        offset = page + PawchiveRepository.PAGE_SIZE;
                        finishPage(result.hasMore && count > 0,
                                rows.getChildCount() == 0 ? "No posts found." : "");
                    });
                } else {
                    List<PawchiveRepository.Creator> all = cached == null ? repository.listCreators() : cached;
                    List<PawchiveRepository.Creator> result = PawchiveRepository.creatorShelf(
                            all, term, page, PawchiveRepository.PAGE_SIZE);
                    publish(request, () -> {
                        catalog = all;
                        for (PawchiveRepository.Creator item : result) addCreator(item);
                        offset = page + result.size();
                        finishPage(result.size() == PawchiveRepository.PAGE_SIZE,
                                rows.getChildCount() == 0 ? "No matching creators." : "");
                    });
                }
            } catch (Exception error) {
                publish(request, () -> {
                    busy = false;
                    status.setText("Pawchive could not load. " +
                            (error.getMessage() == null ? "" : error.getMessage()));
                    more.setText("Retry");
                    more.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void publish(int request, Runnable work) {
        runOnUiThread(() -> {
            if (request == generation && !isFinishing() && !isDestroyed()) work.run();
        });
    }

    private void finishPage(boolean next, String message) {
        busy = false;
        hasMore = next;
        status.setText(message);
        more.setText("Load more");
        more.setVisibility(next ? View.VISIBLE : View.GONE);
    }

    private void addCreator(PawchiveRepository.Creator item) {
        addRow(item.name, item.service, () -> {
            creator = item;
            post = null;
            heading.setText(item.name);
            query = "";
            search.setText("");
            search.setHint("Search posts");
            restart();
        });
    }

    private void addPost(PawchiveRepository.Post item) {
        addRow(item.title.isEmpty() ? "Post " + item.id : item.title, item.service, () -> {
            post = item;
            heading.setText(item.title.isEmpty() ? "Post" : item.title);
            search.setVisibility(View.GONE);
            restart();
        });
    }

    private void addMedia(PawchiveRepository.Media item, PawchiveRepository.Post owner) {
        addRow(item.name, item.video ? "Play video" : "View image", () -> {
            if (item.video) {
                Intent intent = new Intent(this, PlayerActivity.class);
                intent.putExtra(PlayerActivity.EXTRA_MEDIA_URL, item.url);
                intent.putExtra(PlayerActivity.EXTRA_PAGE_URL, owner.postUrl());
                intent.putExtra(PlayerActivity.EXTRA_TITLE, item.name);
                startActivity(intent);
            } else {
                Dialog viewer = new Dialog(this);
                viewer.requestWindowFeature(Window.FEATURE_NO_TITLE);
                ImageView image = new ImageView(this);
                image.setBackgroundColor(Color.BLACK);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setContentDescription("Image preview. Tap to close.");
                image.setOnClickListener(v -> viewer.dismiss());
                viewer.setContentView(image);
                viewer.setOnDismissListener(v -> Glide.with(this).clear(image));
                viewer.show();
                if (viewer.getWindow() != null) {
                    viewer.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
                    viewer.getWindow().setLayout(-1, -1);
                }
                Glide.with(this).load(item.url)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_dialog_alert)
                        .fitCenter().into(image);
            }
        });
    }

    private void addRow(String title, String detail, Runnable clicked) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setPadding(dp(14), dp(12), dp(14), dp(12));
        cell.setMinimumHeight(dp(72));
        cell.setBackground(ZeroChillUi.rounded(this, 0xff121518, 0x24ffffff, R.dimen.zc_radius_medium));
        TextView name = text(title, 16, Color.WHITE);
        name.setMaxLines(3);
        cell.addView(name);
        TextView subtitle = text(detail, 13, 0xffaab1bb);
        cell.addView(subtitle);
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setOnClickListener(v -> clicked.run());
        ZeroChillMotion.installPressFeedback(cell);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(6);
        rows.addView(cell, params);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        return view;
    }

    private TextView action(String value, int size) {
        TextView view = text(value, size, accent);
        view.setGravity(Gravity.CENTER);
        view.setFocusable(true);
        view.setClickable(true);
        ZeroChillMotion.installPressFeedback(view);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void goBack() {
        if (post != null) {
            post = null;
            heading.setText(creator.name);
            search.setVisibility(View.VISIBLE);
            restart();
        } else if (creator != null) {
            creator = null;
            heading.setText("Pawchive");
            query = "";
            search.setText("");
            search.setHint("Search creators");
            restart();
        } else {
            finish();
        }
    }

    @Override public void onBackPressed() { goBack(); }

    @Override protected void onDestroy() {
        generation++;
        if (task != null) task.cancel(true);
        io.shutdownNow();
        super.onDestroy();
    }
}
