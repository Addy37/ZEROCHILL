package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.Map;
import org.json.JSONObject;
import android.os.Parcelable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native search across site videos, collections, categories and the local Library. */
final class SearchPanel {
    private final Activity activity;
    private final Intent intent;
    private final Runnable dismiss;
    final boolean overlay;
    View view;
    EditText field() { return input; }
    private FrameLayout content;
    private FrameLayout resultLayer;
    private LinearLayout headerControls;
    private View clear;
    private ImageView searchIcon;
    private boolean closing;
    private android.animation.ValueAnimator morphAnimator;
    private GlassHeaderView header;
    private SearchGlassLayout shell;
    private LinearLayout resultsControls;
    private GlassHeaderView resultSurface, favoriteSurface;
    private android.animation.ValueAnimator resultsAnimator;
    private float morphProgress;
    private boolean resultShown;
    private ViewPageBackdrop emptyBackdrop;

    void setEmptyBackdrop(ViewPageBackdrop backdrop) {
        emptyBackdrop = backdrop;
        shell.setUnderlay(backdrop);
    }

    SearchPanel(Activity activity, Intent intent, boolean overlay, Runnable dismiss) {
        this.activity = activity; this.intent = intent; this.overlay = overlay; this.dismiss = dismiss;
    }
    public static final String EXTRA_QUERY = "query";
    private static final String EXTRA_SCOPE = "scope";
    private static final String SCOPE_BUNKR = "bunkr";

    private enum Filter {
        ALL,
        VIDEOS,
        EFUKT,
        BUNKR,
        COLLECTIONS,
        CATEGORIES,
        LIBRARY
    }

    private final CrazyShitRepository repository = new CrazyShitRepository();
    private final BrowseRepository browseRepository = new BrowseRepository();
    private final EfuktRepository efuktRepository = new EfuktRepository();
    private final BunkrRepository bunkrRepository = new BunkrRepository();
    private final ExecutorService io = Executors.newFixedThreadPool(8);

    private EditText input;
    private ZeroChillLoadingView progress;
    private TextView status;
    private RecyclerView recycler;
    private GlobalSearchAdapter adapter;
    private OnlyFapCreatorSearchAdapter onlyFapAdapter;
    private final List<TextView> filterViews = new ArrayList<>();

    private List<NativeContentItem> videos = new ArrayList<>();
    private List<NativeContentItem> crazyVideos = new ArrayList<>();
    private List<NativeContentItem> efuktVideos = new ArrayList<>();
    private List<NativeContentItem> series = new ArrayList<>();
    private List<NativeContentItem> efuktSeries = new ArrayList<>();
    private List<NativeContentItem> bunkrAlbums = new ArrayList<>();
    private List<NativeContentItem> fapzoneCreators = new ArrayList<>();
    private List<NativeContentItem> categories = new ArrayList<>();
    private List<NativeContentItem> library = new ArrayList<>();
    private Filter filter = Filter.ALL;
    private String activeQuery = "";
    private int generation;
    private boolean bunkrOnly;
    private CreatorSuggestionsController suggestions;
    private TextView searchState;
    private View filterBar;
    private final List<Future<?>> requests = new ArrayList<>();
    private final Map<Integer, String> errors = new LinkedHashMap<>();
    private List<NativeContentItem> crazySeries = new ArrayList<>();
    private int pendingSources;
    private boolean destroyed;
    private String snapshotId = ScreenSnapshotStore.newId();
    private Parcelable restoredScroll;
    private long searchStarted;
    private boolean firstResultsRecorded;
    private List<NativeContentItem> onlyFapLocal = new ArrayList<>();


    void create(Bundle state) {
        bunkrOnly = SCOPE_BUNKR.equals(intent.getStringExtra(EXTRA_SCOPE));
        buildUi();
        if (bunkrOnly) io.execute(() -> BundledCreatorIndex.get(activity.getApplicationContext()));


        if (state != null) {
            snapshotId = state.getString("snapshot", snapshotId);
            try { filter = Filter.valueOf(state.getString("filter", "ALL")); } catch (Exception ignored) { }
            refreshFilterStyles();
            input.setText(state.getString("typed", ""));
            input.setSelection(input.length());
            restoredScroll = state.getParcelable("scroll");
            activeQuery = state.getString("active", "");
            boolean showingSuggestions = state.getBoolean("suggestions", true);
            if (!showingSuggestions) { suggestions.hide(); input.clearFocus(); }
            if (!activeQuery.isEmpty()) {
                final String savedQuery = activeQuery;
                if (bunkrOnly) {
                    startOnlyFapSearch(savedQuery);
                } else {
                    final int token = ++generation;
                    io.execute(() -> {
                        JSONObject snapshot = ScreenSnapshotStore.read(activity, snapshotId);
                        activity.runOnUiThread(() -> {
                            if (destroyed || token != generation) return;
                            if (!restoreSnapshot(snapshot)) startGlobalSearch(savedQuery, false);
                        });
                    });
                }
            }
        } else {
            String supplied = intent.getStringExtra(EXTRA_QUERY);
            if (supplied != null && !supplied.trim().isEmpty()) {
                input.setText(supplied.trim());
                input.setSelection(input.length());
                runSearch();
            } else {
                input.requestFocus();

            }
        }
    }

    private void buildUi() {
        shell = new SearchGlassLayout(activity);
        shell.setClickable(true);
        shell.setOnClickListener(v -> dismiss.run());
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(shell, (v, insets) -> {
            androidx.core.graphics.Insets safe = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                    | androidx.core.view.WindowInsetsCompat.Type.displayCutout()
                    | androidx.core.view.WindowInsetsCompat.Type.ime());
            v.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        headerControls = new LinearLayout(activity);
        headerControls.setOrientation(LinearLayout.VERTICAL);
        headerControls.addView(buildSearchRow(), new LinearLayout.LayoutParams(-1, dp(56)));
        header = new GlassHeaderView(activity, headerControls, 28f);
        View favorites = searchAction("★", "Favorite creators", v ->
                activity.startActivity(new Intent(activity, CreatorsActivity.class)));
        favoriteSurface = new GlassHeaderView(activity, favorites, 24f);
        resultSurface = new GlassHeaderView(activity, new FrameLayout(activity), 24f, true);
        resultsControls = new LinearLayout(activity);
        resultsControls.setOrientation(LinearLayout.VERTICAL);
        resultsControls.setClipChildren(false);
        resultsControls.setClipToPadding(false);
        resultsControls.setOnClickListener(v -> {});
        shell.install(header, favoriteSurface, resultSurface, resultsControls);

        content = new FrameLayout(activity);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setClickable(true);
        resultsControls.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        resultLayer = new FrameLayout(activity);
        resultLayer.setClipChildren(false);
        resultLayer.setClipToPadding(false);
        content.addView(resultLayer, new FrameLayout.LayoutParams(-1, -1));
        recycler = new RecyclerView(activity);
        recycler.setLayoutManager(new LinearLayoutManager(activity));
        recycler.setBackgroundColor(Color.TRANSPARENT);
        recycler.setClipToPadding(false);
        recycler.setPadding(0, dp(68), 0, dp(4));
        recycler.setItemAnimator(null);
        if (bunkrOnly) {
            onlyFapAdapter = new OnlyFapCreatorSearchAdapter(this::openCreator);
            recycler.setAdapter(onlyFapAdapter);
        } else {
            adapter = new GlobalSearchAdapter(this::openResult);
            recycler.setAdapter(adapter);
        }
        FrameLayout.LayoutParams listParams = new FrameLayout.LayoutParams(-1, -1);
        listParams.topMargin = -dp(68);
        resultLayer.addView(recycler, listParams);
        recycler.getAdapter().registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override public void onChanged() { updateResultSize(); }
            @Override public void onItemRangeInserted(int start, int count) { updateResultSize(); }
            @Override public void onItemRangeRemoved(int start, int count) { updateResultSize(); }
        });
        status = BrowseUi.text(activity, "", 12, BrowseUi.MUTED);
        status.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        status.setPadding(dp(12), dp(14), dp(12), dp(8));
        status.setText(bunkrOnly ? "Search OnlyFap creators" : "Search creators or videos");
        resultLayer.addView(status, new FrameLayout.LayoutParams(-1, -2));
        progress = new ZeroChillLoadingView(activity, null, true);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        progressParams.topMargin = dp(12);
        resultLayer.addView(progress, progressParams);
        suggestionPanel = new LinearLayout(activity);
        suggestionPanel.setClipChildren(false);
        suggestionPanel.setClipToPadding(false);
        content.addView(suggestionPanel, new FrameLayout.LayoutParams(-1, -1));
        suggestions = new CreatorSuggestionsController(activity, input, suggestionPanel, this::openCreator);
        suggestions.setFloatingPresentation(this::updateResultSize);
        suggestions.setSearchAction(this::runSearch);
        if (!bunkrOnly) {
            filterBar = buildFilters();
            resultsControls.addView(filterBar, new LinearLayout.LayoutParams(-1, dp(44)));
        }
        searchState = BrowseUi.text(activity, "", 11, BrowseUi.MUTED);
        searchState.setPadding(dp(14), dp(4), dp(14), dp(8));
        searchState.setVisibility(View.GONE);
        searchState.setOnClickListener(v -> {
            if (activeQuery.isEmpty()) return;
            if (bunkrOnly) startOnlyFapSearch(activeQuery);
            else startGlobalSearch(activeQuery, false);
        });
        resultsControls.addView(searchState);
        suggestions.setVisibilityListener(showing -> {
            resultLayer.setVisibility(showing ? View.INVISIBLE : View.VISIBLE);
            if (filterBar != null) filterBar.setVisibility(showing ? View.GONE : View.VISIBLE);
            updateContentVisibility();
        });
        view = shell;
        updateContentVisibility();
    }

    private LinearLayout suggestionPanel;

    private void updateResultSize() {
        if (shell == null || recycler == null || suggestions == null) return;
        int count = suggestions.isShowing() ? suggestions.resultCount()
                : recycler.getAdapter().getItemCount();
        shell.setResultCount(!suggestions.isShowing() && count == 0 ? 1 : count);
    }

    private void updateContentVisibility() {
        if (content == null || input == null || resultsControls == null || closing) return;
        boolean show = input.length() > 0;
        updateResultSize();
        if (show) content.setVisibility(View.VISIBLE);
        if (show == resultShown) {
            if (!show) { content.setVisibility(View.INVISIBLE); resultSurface.setVisibility(View.INVISIBLE); resultsControls.setVisibility(View.INVISIBLE); resultSurface.setAlpha(0f); resultsControls.setAlpha(0f); }
            return;
        }
        resultShown = show;
        if (resultsAnimator != null) resultsAnimator.cancel();
        resultSurface.setVisibility(View.VISIBLE);
        resultsControls.setVisibility(View.VISIBLE);
        resultsAnimator = android.animation.ValueAnimator.ofFloat(resultSurface.getAlpha(), show ? 1f : 0f);
        resultsAnimator.setDuration(ZeroChillMotion.animationsEnabled(activity) ? 180L : 0L);
        resultsAnimator.addUpdateListener(a -> {
            float amount = (float) a.getAnimatedValue();
            resultSurface.setAlpha(amount); resultsControls.setAlpha(amount);
            float rise = dp(8) * (1f - amount);
            resultSurface.setTranslationY(rise); resultsControls.setTranslationY(rise);
        });
        resultsAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (!resultShown) { content.setVisibility(View.INVISIBLE); resultSurface.setVisibility(View.INVISIBLE); resultsControls.setVisibility(View.INVISIBLE); }
            }
        });
        resultsAnimator.start();
    }

    private View buildSearchRow() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));
        searchIcon = new ImageView(activity);
        searchIcon.setImageResource(R.drawable.ic_nav_search);
        searchIcon.setColorFilter(Color.WHITE);
        searchIcon.setPadding(dp(10), dp(10), dp(10), dp(10));
        searchIcon.setContentDescription("Close search");
        searchIcon.setClickable(true);
        searchIcon.setFocusable(true);
        searchIcon.setOnClickListener(v -> dismiss.run());
        row.addView(searchIcon, new LinearLayout.LayoutParams(dp(44), dp(48)));

        input = new ZeroChillEditText(activity);
        input.setHint(bunkrOnly ? "Search creators" : "Search creators or videos");
        input.setHintTextColor(ZeroChillUi.color(activity, R.color.zc_text_muted));
        input.setTextColor(ZeroChillUi.color(activity, R.color.zc_text_primary));
        input.setTextSize(16f);
        input.setSingleLine(true);
        input.setMinWidth(0);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setContentDescription(bunkrOnly
                ? "Search OnlyFap creators"
                : "Search creators, albums and videos");
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(dp(4), 0, dp(4), 0);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch();
                return true;
            }
            return false;
        });
        row.addView(input, new LinearLayout.LayoutParams(0, -1, 1f));
        clear = searchAction("×", "Clear search", v -> {
            input.setText(""); focus();
        });
        clear.setVisibility(View.GONE);
        row.addView(clear, new LinearLayout.LayoutParams(dp(44), dp(48)));
        input.addTextChangedListener(BrowseUi.onText(value -> {
            clear.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
            updateContentVisibility();
        }));
        return row;
    }

    private TextView searchAction(String label, String description, View.OnClickListener listener) {
        TextView action = BrowseUi.action(activity, label, description, listener);
        action.setBackgroundColor(Color.TRANSPARENT);
        action.setTextSize(22f);
        action.setPadding(0, 0, 0, 0);
        return action;
    }

    private View buildFilters() {
        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(4), dp(10), dp(4));
        scroll.addView(row, new HorizontalScrollView.LayoutParams(-2, -1));

        addFilter(row, "All", Filter.ALL);
        addFilter(row, "Videos", Filter.VIDEOS);
        addFilter(row, "EFukt", Filter.EFUKT);
        addFilter(row, "OnlyFap", Filter.BUNKR);
        addFilter(row, "Collections", Filter.COLLECTIONS);
        addFilter(row, "Categories", Filter.CATEGORIES);
        addFilter(row, "Library", Filter.LIBRARY);
        refreshFilterStyles();
        return scroll;
    }

    private void addFilter(LinearLayout row, String label, Filter value) {
        TextView chip = new TextView(activity);
        chip.setText(label);
        chip.setTag(value);
        chip.setTextSize(13.5f);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(15), 0, dp(15), 0);
        chip.setOnClickListener(v -> {
            haptic(v);
            filter = (Filter) v.getTag();
            refreshFilterStyles();
            renderResults();
            recycler.scrollToPosition(0);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(36));
        params.setMargins(dp(4), 0, dp(4), 0);
        row.addView(chip, params);
        filterViews.add(chip);
    }

    private void refreshFilterStyles() {
        for (TextView chip : filterViews) {
            boolean selected = chip.getTag() == filter;
            ZeroChillUi.styleChip(chip, selected);
        }
    }

    private void runSearch() {
        String query = input.getText().toString().trim();
        if (query.length() < 2) {
            input.setError("Type at least 2 characters");
            return;
        }
        BrowseUi.hideKeyboard(activity, input);
        input.clearFocus();
        suggestions.hide();
        if (bunkrOnly) {
            startOnlyFapSearch(query);
            return;
        }
        startGlobalSearch(query, true);
    }

    private void openCreator(NativeContentItem item) {
        BrowseUi.hideKeyboard(activity, input);
        activity.startActivity(NativeFeedBrowserActivity.createCreatorGallery(activity, item.title,
                item.searchQuery.isEmpty() ? item.title : item.searchQuery,
                NativeFeedBrowserActivity.creatorProfileHint(item),
                CreatorGalleryPreloader.sessionId(activity, item)));
    }

    private void startOnlyFapSearch(String query) {
        for (Future<?> request : requests) request.cancel(true);
        requests.clear();
        if (io instanceof java.util.concurrent.ThreadPoolExecutor) {
            ((java.util.concurrent.ThreadPoolExecutor) io).purge();
        }

        activeQuery = query == null ? "" : query.trim();
        final String requestedQuery = activeQuery;
        final int token = ++generation;
        pendingSources = 1;
        errors.clear();
        onlyFapLocal = new ArrayList<>();
        if (onlyFapAdapter != null) onlyFapAdapter.replace(onlyFapLocal);
        recycler.scrollToPosition(0);
        progress.setVisibility(View.VISIBLE);
        status.setVisibility(View.GONE);
        searchState.setVisibility(View.VISIBLE);
        searchState.setText("Finding saved creators · Checking sources…");
        searchState.setContentDescription(searchState.getText());

        requests.add(io.submit(() -> {
            BundledCreatorIndex index = BundledCreatorIndex.get(activity.getApplicationContext());
            List<NativeContentItem> bundled = index.matching(requestedQuery, 60);
            List<NativeContentItem> learned = CreatorCatalog.matching(
                    activity.getApplicationContext(), requestedQuery, false, 60);
            List<NativeContentItem> local = OnlyFapCreatorResults.merge(bundled, learned, 60);
            local.sort(java.util.Comparator.comparingInt(
                    (NativeContentItem item) -> index.rank(item, requestedQuery))
                    .thenComparing(item -> CreatorNameMatcher.normalized(item.title)));
            activity.runOnUiThread(() -> {
                if (destroyed || activity.isFinishing() || token != generation) return;
                onlyFapLocal = local;
                if (onlyFapAdapter != null) onlyFapAdapter.replace(local);
                if (!local.isEmpty()) progress.finish();
                status.setVisibility(View.GONE);
                searchState.setText(local.size() + " saved creators · Checking sources…");
            });
            List<NativeContentItem> result = null;
            try {
                result = new FapzoneCreatorSearchRepository().search(
                        activity,
                        requestedQuery,
                        40,
                        (partial, complete) -> {
                            if (complete || partial == null || partial.isEmpty()) return;
                            List<NativeContentItem> visible = OnlyFapCreatorResults.merge(
                                    onlyFapLocal, partial, 80);
                            activity.runOnUiThread(() -> {
                                if (destroyed || activity.isFinishing() || token != generation) return;
                                status.setVisibility(View.GONE);
                                if (onlyFapAdapter != null) onlyFapAdapter.replace(visible);
                                if (!visible.isEmpty()) progress.finish();
                                searchState.setText(visible.size() + " creators · Checking sources…");
                                searchState.setContentDescription(searchState.getText());
                            });
                        }
                );
            } catch (Exception ignored) {
            }

            List<NativeContentItem> creators = result;
            if (creators != null) CreatorCatalog.remember(activity.getApplicationContext(), creators);
            List<NativeContentItem> combined = creators == null ? null
                    : OnlyFapCreatorResults.merge(onlyFapLocal, creators, 80);
            activity.runOnUiThread(() -> {
                if (destroyed || activity.isFinishing() || token != generation) return;
                pendingSources = 0;
                progress.setVisibility(View.GONE);

                if (creators == null) {
                    searchState.setText("Live sources unavailable · Saved creators shown · Tap to retry");
                    searchState.setContentDescription(searchState.getText());
                    if (onlyFapLocal.isEmpty()) {
                        status.setText("Couldn't reach the creator sources right now.\nTap the message above to retry.");
                        status.setVisibility(View.VISIBLE);
                    }
                    return;
                }

                if (onlyFapAdapter != null) onlyFapAdapter.replace(combined);
                searchState.setText(combined.size() == 1
                        ? "1 creator"
                        : combined.size() + " creators");
                searchState.setContentDescription(searchState.getText());

                if (combined.isEmpty()) {
                    status.setText("No creator matches for “" + requestedQuery + "”\n\nTry a shorter or more general search.");
                    status.setVisibility(View.VISIBLE);
                } else {
                    status.setVisibility(View.GONE);
                    if (restoredScroll != null && recycler.getLayoutManager() != null) {
                        recycler.getLayoutManager().onRestoreInstanceState(restoredScroll);
                        restoredScroll = null;
                    }
                }
            });
        }));
    }

    private void startGlobalSearch(String query, boolean clear) {
        for (Future<?> request : requests) request.cancel(true);
        requests.clear();
        if (io instanceof java.util.concurrent.ThreadPoolExecutor)
            ((java.util.concurrent.ThreadPoolExecutor) io).purge();
        activeQuery = query;
        int token = ++generation;
        errors.clear();
        pendingSources = 8;
        searchStarted = android.os.SystemClock.elapsedRealtime();
        firstResultsRecorded = false;
        if (clear) {
            crazyVideos.clear(); efuktVideos.clear(); crazySeries.clear(); efuktSeries.clear();
            bunkrAlbums.clear(); fapzoneCreators.clear(); categories.clear(); library.clear();
            videos.clear(); series.clear();
            restoredScroll = null;
            recycler.scrollToPosition(0);
        }
        renderResults();
        source(token, 0, "CrazyShit videos", () -> repository.fetchFeed(activity, repository.searchUrl(query), 1));
        source(token, 1, "EFukt videos", () -> efuktRepository.search(activity, query));
        source(token, 2, "CrazyShit series", () -> matchCatalog(browseRepository.fetchSeries(activity), query));
        source(token, 3, "EFukt series", () -> matchCatalog(efuktRepository.fetchSeries(activity), query));
        source(token, 4, "OnlyFap albums", () -> bunkrRepository.searchAlbums(activity, query, 1));
        source(token, 5, "Categories", () -> matchCatalog(browseRepository.fetchCategories(activity), query));
        source(token, 6, "Your Library", () -> searchLibrary(query));
        source(token, 7, "OnlyFap creators", () ->
                new FapzoneCreatorSearchRepository().search(activity, query, 20));
    }

    private void source(int token, int id, String title, Callable<List<NativeContentItem>> fetch) {
        requests.add(io.submit(() -> {
            List<NativeContentItem> items = null;
            try { items = fetch.call(); } catch (Exception ignored) { }
            List<NativeContentItem> result = items;
            activity.runOnUiThread(() -> {
                if (destroyed || activity.isFinishing() || token != generation) return;
                pendingSources--;
                if (result == null) errors.put(id, title);
                else switch (id) {
                    case 0: crazyVideos = result; break;
                    case 1: efuktVideos = result; break;
                    case 2: crazySeries = result; break;
                    case 3: efuktSeries = result; break;
                    case 4: bunkrAlbums = result; break;
                    case 5: categories = result; break;
                    case 6: library = result; break;
                    case 7:
                        fapzoneCreators = result;
                        CreatorCatalog.remember(activity, result);
                        break;
                }
                videos = interleave(crazyVideos, efuktVideos);
                series = interleave(crazySeries, efuktSeries);
                renderResults();
                if (pendingSources == 0) saveSnapshot();
            });
        }));
    }

    private void saveSnapshot() {
        try {
            JSONObject data = new JSONObject().put("query", activeQuery);
            data.put("complete", pendingSources == 0);
            data.put("crazyVideos", ContentItemCodec.encodeList(crazyVideos, 100));
            data.put("efuktVideos", ContentItemCodec.encodeList(efuktVideos, 100));
            data.put("crazySeries", ContentItemCodec.encodeList(crazySeries, 100));
            data.put("efuktSeries", ContentItemCodec.encodeList(efuktSeries, 100));
            data.put("albums", ContentItemCodec.encodeList(bunkrAlbums, 100));
            data.put("creators", ContentItemCodec.encodeList(fapzoneCreators, 100));
            data.put("categories", ContentItemCodec.encodeList(categories, 100));
            data.put("library", ContentItemCodec.encodeList(library, 100));
            ScreenSnapshotStore.save(activity, snapshotId, data);
        } catch (Exception ignored) { }
    }

    private boolean restoreSnapshot(JSONObject data) {
        if (data == null || !activeQuery.equals(data.optString("query"))) return false;
        crazyVideos = ContentItemCodec.decodeList(data.optJSONArray("crazyVideos"), 100);
        efuktVideos = ContentItemCodec.decodeList(data.optJSONArray("efuktVideos"), 100);
        crazySeries = ContentItemCodec.decodeList(data.optJSONArray("crazySeries"), 100);
        efuktSeries = ContentItemCodec.decodeList(data.optJSONArray("efuktSeries"), 100);
        bunkrAlbums = ContentItemCodec.decodeList(data.optJSONArray("albums"), 100);
        fapzoneCreators = ContentItemCodec.decodeList(data.optJSONArray("creators"), 100);
        categories = ContentItemCodec.decodeList(data.optJSONArray("categories"), 100);
        library = ContentItemCodec.decodeList(data.optJSONArray("library"), 100);
        videos = interleave(crazyVideos, efuktVideos);
        series = interleave(crazySeries, efuktSeries);
        pendingSources = data.optBoolean("complete", false) ? 0 : pendingSources;
        renderResults();
        return data.optBoolean("complete", false);
    }

    private List<NativeContentItem> interleave(
            List<NativeContentItem> first,
            List<NativeContentItem> second
    ) {
        LinkedHashMap<String, NativeContentItem> result = new LinkedHashMap<>();
        int firstSize = first == null ? 0 : first.size();
        int secondSize = second == null ? 0 : second.size();
        int count = Math.max(firstSize, secondSize);
        for (int i = 0; i < count; i++) {
            if (i < firstSize) addUnique(result, first.get(i));
            if (i < secondSize) addUnique(result, second.get(i));
        }
        return new ArrayList<>(result.values());
    }

    private void addUnique(
            LinkedHashMap<String, NativeContentItem> result,
            NativeContentItem item
    ) {
        if (item == null || item.url == null || item.url.isEmpty()) return;
        NativeContentItem old = result.get(item.url);
        result.put(item.url, old == null ? item : old.merge(item));
    }

    private List<NativeContentItem> matchCatalog(List<NativeContentItem> source, String query) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        if (source == null) return result;
        String[] terms = normalized(query).split("\\s+");
        for (NativeContentItem item : source) {
            if (item == null) continue;
            String haystack = normalized(item.title + " " + item.url + " " + item.description);
            boolean match = true;
            for (String term : terms) {
                if (!term.isEmpty() && !haystack.contains(term)) {
                    match = false;
                    break;
                }
            }
            if (match) result.add(item);
        }
        return result;
    }

    private List<NativeContentItem> searchLibrary(String query) {
        LinkedHashMap<String, NativeContentItem> result = new LinkedHashMap<>();
        String needle = normalized(query);

        for (FavoriteStore.Item saved : FavoriteStore.load(activity)) {
            if (!normalized(saved.title).contains(needle)) continue;
            result.put(saved.url, new NativeContentItem(
                    NativeContentItem.KIND_MEDIA,
                    saved.title,
                    saved.url,
                    "",
                    "",
                    "",
                    ""
            ));
        }
        for (PlaybackHistoryStore.Item history : PlaybackHistoryStore.load(activity)) {
            if (!normalized(history.title).contains(needle)) continue;
            result.putIfAbsent(history.pageUrl, new NativeContentItem(
                    NativeContentItem.KIND_MEDIA,
                    history.title,
                    history.pageUrl,
                    "",
                    history.progressPercent() > 0 ? history.progressPercent() + "% watched" : "",
                    "",
                    ""
            ));
        }
        return new ArrayList<>(result.values());
    }

    private void renderResults() {
        if (activeQuery.isEmpty()) return;
        ArrayList<GlobalSearchAdapter.Entry> output = new ArrayList<>();
        if (filter == Filter.ALL || filter == Filter.VIDEOS) appendSection(output, "Videos  •  CrazyShit + EFukt", videos, GlobalSearchAdapter.SOURCE_REMOTE, 40);
        if (filter == Filter.EFUKT) appendSection(output, "EFukt Videos", efuktVideos, GlobalSearchAdapter.SOURCE_REMOTE, 40);
        if (filter == Filter.ALL || filter == Filter.BUNKR || filter == Filter.COLLECTIONS) {
            appendSection(output, "OnlyFap Creators", fapzoneCreators,
                    GlobalSearchAdapter.SOURCE_REMOTE, 20);
            appendSection(output, "OnlyFap Albums", bunkrAlbums, GlobalSearchAdapter.SOURCE_REMOTE, 30);
        }
        if (filter == Filter.ALL || filter == Filter.COLLECTIONS) appendSection(output, "Series", series, GlobalSearchAdapter.SOURCE_REMOTE, 20);
        if (filter == Filter.EFUKT) appendSection(output, "EFukt Series", efuktSeries, GlobalSearchAdapter.SOURCE_REMOTE, 20);
        if (filter == Filter.ALL || filter == Filter.CATEGORIES) appendSection(output, "Categories", categories, GlobalSearchAdapter.SOURCE_REMOTE, 20);
        if (filter == Filter.ALL || filter == Filter.LIBRARY) appendSection(output, "Your Library", library, GlobalSearchAdapter.SOURCE_LIBRARY, 30);
        if (!output.isEmpty() && !firstResultsRecorded && searchStarted > 0) {
            firstResultsRecorded = true;
            AppPerformance.record("Search first results", android.os.SystemClock.elapsedRealtime() - searchStarted);
        }
        adapter.replace(output);

        boolean waitingForFirstResult = output.isEmpty() && pendingSources > 0;
        if (waitingForFirstResult) {
            progress.setVisibility(View.VISIBLE);
        } else if (!output.isEmpty() && progress.getVisibility() == View.VISIBLE) {
            progress.finish();
        } else {
            progress.setVisibility(View.GONE);
        }
        String unavailable = unavailableForCurrentFilter();
        searchState.setVisibility(View.VISIBLE);
        searchState.setText(pendingSources > 0
                ? "Searching · " + pendingSources + " sources remaining"
                : unavailable.isEmpty() ? "Search complete"
                : "Unavailable: " + unavailable + " · Tap to retry");
        searchState.setContentDescription(searchState.getText());
        if (output.isEmpty()) {
            status.setText(pendingSources > 0 ? "" : !unavailable.isEmpty()
                    ? "Some sources could not be reached.\nTap the message above to retry."
                    : "No matches for “" + activeQuery + "”\n\nTry a shorter or more general search.");
            status.setVisibility(pendingSources > 0 ? View.GONE : View.VISIBLE);
        } else {
            status.setVisibility(View.GONE);
            if (restoredScroll != null) {
                recycler.getLayoutManager().onRestoreInstanceState(restoredScroll);
                restoredScroll = null;
            }
        }
    }

    private String unavailableForCurrentFilter() {
        ArrayList<String> unavailable = new ArrayList<>();
        for (Map.Entry<Integer, String> error : errors.entrySet()) {
            if (sourceVisibleForFilter(error.getKey())) unavailable.add(error.getValue());
        }
        return String.join(", ", unavailable);
    }

    private boolean sourceVisibleForFilter(int sourceId) {
        switch (filter) {
            case VIDEOS:
                return sourceId == 0 || sourceId == 1;
            case EFUKT:
                return sourceId == 1 || sourceId == 3;
            case BUNKR:
                return sourceId == 4 || sourceId == 7;
            case COLLECTIONS:
                return sourceId == 2 || sourceId == 3 || sourceId == 4 || sourceId == 7;
            case CATEGORIES:
                return sourceId == 5;
            case LIBRARY:
                return sourceId == 6;
            case ALL:
            default:
                return true;
        }
    }

    private void appendSection(
            List<GlobalSearchAdapter.Entry> out,
            String title,
            List<NativeContentItem> items,
            int source,
            int limit
    ) {
        if (items == null || items.isEmpty()) return;
        out.add(GlobalSearchAdapter.Entry.section(title + "  •  " + items.size()));
        int count = Math.min(limit, items.size());
        for (int i = 0; i < count; i++) {
            out.add(GlobalSearchAdapter.Entry.item(items.get(i), source));
        }
    }

    private void openResult(NativeContentItem item) {
        if (item == null || item.url == null || item.url.isEmpty()) return;
        haptic(recycler);
        if (item.isCreator()) {
            openCreator(item);
            return;
        }
        if (item.isSeries() || item.isCategory()) {
            String source = BunkrRepository.isAlbumUrl(item.url)
                    ? NativeFeedBrowserActivity.SOURCE_BUNKR
                    : EfuktRepository.isEfuktUrl(item.url)
                    ? NativeFeedBrowserActivity.SOURCE_EFUKT
                    : NativeFeedBrowserActivity.SOURCE_CRAZYSHIT;
            activity.startActivity(NativeFeedBrowserActivity.create(
                    activity, item.title, item.url, false, source
            ));
            return;
        }

        progress.setVisibility(View.VISIBLE);
        final int requestGeneration = generation;
        io.execute(() -> {
            CrazyShitRepository.StreamInfo stream = null;
            try {
                stream = PlayableSourceRouter.resolve(activity, item.url);
            } catch (Exception ignored) {
            }
            CrazyShitRepository.StreamInfo resolved = stream;
            activity.runOnUiThread(() -> {
                if (destroyed || activity.isFinishing() || requestGeneration != generation) return;
                progress.setVisibility(View.GONE);
                if (resolved == null || resolved.mediaUrl == null || resolved.mediaUrl.isEmpty()) {
                    ZeroChillToast.makeText(
                            activity,
                            "Couldn't resolve this video natively right now.",
                            ZeroChillToast.LENGTH_SHORT
                    ).show();
                    return;
                }
                Intent intent = new Intent(activity, VideoDetailActivity.class);
                intent.putExtra(PlayerActivity.EXTRA_MEDIA_URL, resolved.mediaUrl);
                intent.putExtra(PlayerActivity.EXTRA_PAGE_URL, resolved.pageUrl);
                intent.putExtra(PlayerActivity.EXTRA_TITLE,
                        item.title == null || item.title.trim().isEmpty() ? resolved.title : item.title);
                intent.putExtra(VideoDetailActivity.EXTRA_VIEWS, item.views);
                intent.putExtra(VideoDetailActivity.EXTRA_UPLOADER, item.uploader);
                intent.putExtra(VideoDetailActivity.EXTRA_COMMENTS, item.comments);
                intent.putExtra(VideoDetailActivity.EXTRA_MEDIA_REFERER, resolved.requestReferer);
                if (BunkrRepository.isBunkrUrl(item.url)) {
                    intent.putExtra(VideoDetailActivity.EXTRA_SOURCE, NativeFeedBrowserActivity.SOURCE_BUNKR);
                } else if (EfuktRepository.isEfuktUrl(item.url)) {
                    intent.putExtra(VideoDetailActivity.EXTRA_RELATED_FEED_URL, EfuktRepository.SERIES);
                    intent.putExtra(VideoDetailActivity.EXTRA_SOURCE, NativeFeedBrowserActivity.SOURCE_EFUKT);
                }
                try {
                    intent.putExtra(PlayerActivity.EXTRA_USER_AGENT, WebSettings.getDefaultUserAgent(activity));
                } catch (Exception ignored) {
                }
                try {
                    String cookies = CookieManager.getInstance().getCookie(resolved.mediaUrl);
                    if ((cookies == null || cookies.isEmpty()) && resolved.pageUrl != null) {
                        cookies = CookieManager.getInstance().getCookie(resolved.pageUrl);
                    }
                    if (cookies != null) intent.putExtra(PlayerActivity.EXTRA_COOKIES, cookies);
                } catch (Exception ignored) {
                }
                activity.startActivity(intent);
            });
        });
    }

    void focus() {
        input.requestFocus();
        input.post(() -> {
            if (!destroyed && input.isAttachedToWindow()) {
                ((android.view.inputmethod.InputMethodManager) activity.getSystemService(
                        Activity.INPUT_METHOD_SERVICE)).showSoftInput(input,
                        android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    boolean back() {
        androidx.core.view.WindowInsetsCompat insets =
                androidx.core.view.ViewCompat.getRootWindowInsets(view);
        if (insets != null && insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())) {
            BrowseUi.hideKeyboard(activity, input);
            input.clearFocus();
        } else {
            dismiss.run();
        }
        return true;
    }

    private void afterLayout(Runnable action) {
        if (view.getWidth() > 0 && view.getHeight() > 0) { action.run(); return; }
        view.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                view.getViewTreeObserver().removeOnPreDrawListener(this);
                action.run();
                return true;
            }
        });
    }

    void morph(View source, boolean expanding, Runnable finished) {
        if (closing && expanding) return;
        closing = !expanding;
        if (morphAnimator != null) { morphAnimator.removeAllListeners(); morphAnimator.cancel(); }
        if (!expanding) {
            BrowseUi.hideKeyboard(activity, input);
            if (resultsAnimator != null) { resultsAnimator.removeAllListeners(); resultsAnimator.cancel(); }
        }
        if (expanding && morphProgress == 0f) { header.setAlpha(0f); favoriteSurface.setAlpha(0f); }
        if (expanding) {
            // A newly attached overlay may not receive insets until the IME opens.
            // Seed it from the already-mounted control before measuring the morph.
            androidx.core.view.WindowInsetsCompat insets =
                    androidx.core.view.ViewCompat.getRootWindowInsets(source);
            if (insets != null) androidx.core.view.ViewCompat.dispatchApplyWindowInsets(view, insets);
        }
        afterLayout(() -> {
            if (destroyed) return;
            header.setAlpha(1f);
            int[] start = new int[2], root = new int[2];
            source.getLocationInWindow(start); view.getLocationInWindow(root);
            float targetX = shell.getPaddingLeft() + dp(14);
            float targetY = shell.getPaddingTop() + dp(8);
            float targetWidth = shell.getWidth() - shell.getPaddingLeft() - shell.getPaddingRight() - dp(88);
            float originX = start[0] - root[0], originY = start[1] - root[1];
            float originWidth = Math.max(dp(44), source.getWidth());
            float originHeight = Math.max(dp(48), source.getHeight());
            morphAnimator = android.animation.ValueAnimator.ofFloat(morphProgress, expanding ? 1f : 0f);
            morphAnimator.setDuration(ZeroChillMotion.animationsEnabled(activity)
                    ? Math.round(260L * Math.abs((expanding ? 1f : 0f) - morphProgress)) : 0L);
            morphAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
            morphAnimator.addUpdateListener(a -> {
                float amount = (float) a.getAnimatedValue(); morphProgress = amount;
                float width = originWidth + (targetWidth - originWidth) * amount;
                float height = originHeight + (dp(56) - originHeight) * amount;
                float left = originX + (targetX - originX) * amount;
                float top = originY + (targetY - originY) * amount;
                shell.setPillBounds(new android.graphics.RectF(left, top, left + width, top + height));
                searchIcon.setTranslationX(-dp(4) * (1f - amount));
                searchIcon.setTranslationY(-dp(4) * (1f - amount));
                input.setAlpha(amount); clear.setAlpha(amount);
                favoriteSurface.setAlpha(amount);
                if (!expanding) { resultsControls.setAlpha(amount); resultSurface.setAlpha(amount); }
            });
            morphAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) {
                    if (expanding && !closing) { shell.setPillBounds(null); focus(); }
                    finished.run();
                }
            });
            morphAnimator.start();
        });
    }

    private String normalized(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.US)
                .replace('-', ' ')
                .replace('_', ' ')
                .replaceAll("[^a-z0-9 ]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private void haptic(View view) {
        if (!activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE).getBoolean("haptics_enabled", true)) return;
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    void resume() {
        if (suggestions != null) suggestions.refreshLocal();
    }

    void save(Bundle state) {
        state.putString("typed", input.getText().toString());
        state.putString("active", activeQuery);
        state.putString("filter", filter.name());
        state.putString("snapshot", snapshotId);
        state.putBoolean("suggestions", suggestions.isShowing());
        state.putParcelable("scroll", recycler.getLayoutManager().onSaveInstanceState());
        if (!bunkrOnly) saveSnapshot();
    }

    void close() {
        destroyed = true;
        if (morphAnimator != null) { morphAnimator.removeAllListeners(); morphAnimator.cancel(); }
        if (resultsAnimator != null) { resultsAnimator.removeAllListeners(); resultsAnimator.cancel(); }
        generation++;
        if (suggestions != null) suggestions.close();
        if (adapter != null) adapter.close();
        io.shutdownNow();
    }
}
