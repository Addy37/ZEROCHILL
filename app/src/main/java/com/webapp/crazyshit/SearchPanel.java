package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Native search scoped to one content area at a time. */
final class SearchPanel {
    static final List<String> SCOPE_LABELS = Arrays.asList(
            "Shows", "OnlyFap", "Bunkr", "XMegaDrive", "BaddieHub", "Library");
    private static final long ONLYFAP_DEBOUNCE_MS = 280L;
    private static final long XMEGA_DEBOUNCE_MS = 900L;
    private static final long REMOTE_DEBOUNCE_MS = 420L;
    private static final long LIBRARY_DEBOUNCE_MS = 160L;

    enum Scope {
        SHOWS, ONLYFAP, BUNKR_ALBUMS, XMEGADRIVE, BADDIEHUB, LIBRARY
    }

    /** Browser work is allowed only for a real XMegaDrive query. */
    static boolean runsXMegaDrive(Scope scope, String query) {
        return scope == Scope.XMEGADRIVE && query != null && query.trim().length() >= 2;
    }

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
    private GlassHeaderView resultSurface, favoriteSurface, scopeSurface;
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

    private final CrazyShitRepository repository = new CrazyShitRepository();
    private final EfuktRepository efuktRepository = new EfuktRepository();
    private final WebVideoSourceRepository webVideoRepository = new WebVideoSourceRepository();
    private final XMegaDriveRepository xMegaDriveRepository = new XMegaDriveRepository();
    private final BunkrRepository bunkrRepository = new BunkrRepository();
    private final BaddieHubRepository baddieHubRepository = new BaddieHubRepository();
    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final ExecutorService bunkrIo = Executors.newSingleThreadExecutor();

    private EditText input;
    private ZeroChillProgressBar progress;
    private TextView status;
    private RecyclerView recycler;
    private GlobalSearchAdapter adapter;
    private OnlyFapCreatorSearchAdapter onlyFapAdapter;
    private final List<TextView> scopeViews = new ArrayList<>();
    private HorizontalScrollView scopeScroll;

    private List<NativeContentItem> crazyVideos = new ArrayList<>();
    private List<NativeContentItem> efuktVideos = new ArrayList<>();
    private List<NativeContentItem> kaoticVideos = new ArrayList<>();
    private List<NativeContentItem> xmegaResults = new ArrayList<>();
    private List<NativeContentItem> bunkrResults = new ArrayList<>();
    private List<NativeContentItem> baddieResults = new ArrayList<>();
    private List<NativeContentItem> library = new ArrayList<>();
    private Scope scope = Scope.SHOWS;
    private String activeQuery = "";
    private int generation;
    private ContentPreference searchPreference;
    private final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable liveSearch;
    private boolean restoring;
    private boolean previewMode;
    private LinearLayout recentPanel;
    private View recentLayer;
    private View searchFooter;
    private List<NativeContentItem> creatorResults = new ArrayList<>();
    private TextView searchState;
    private final List<Future<?>> requests = new ArrayList<>();
    private final Map<Integer, String> errors = new LinkedHashMap<>();
    private int pendingSources;
    private boolean destroyed;
    private String snapshotId = ScreenSnapshotStore.newId();
    private Parcelable restoredScroll;
    private long searchStarted;
    private boolean firstResultsRecorded;

    void create(Bundle state) {
        scope = initialScope(state);
        buildUi();
        restoring = true;
        if (state != null) {
            snapshotId = state.getString("snapshot", snapshotId);
            refreshScopeStyles();
            if (adapter != null) adapter.restoreOrder(state);
            input.setText(state.getString("typed", ""));
            input.setSelection(input.length());
            restoredScroll = state.getParcelable("scroll");
            activeQuery = state.getString("active", "");
            previewMode = state.getBoolean("preview", false);
            if (!previewMode) input.clearFocus();
            if (!activeQuery.isEmpty()) {
                final String savedQuery = activeQuery;
                if (scope == Scope.ONLYFAP) startOnlyFapSearch(savedQuery);
                else {
                    final int token = ++generation;
                    final Scope savedScope = scope;
                    io.execute(() -> {
                        JSONObject snapshot = ScreenSnapshotStore.read(activity, snapshotId);
                        activity.runOnUiThread(() -> {
                            if (destroyed || token != generation || savedScope != scope) return;
                            if (!restoreSnapshot(snapshot)) startScopedSearch(savedQuery);
                        });
                    });
                }
            }
        } else {
            String supplied = intent == null ? null : intent.getStringExtra(EXTRA_QUERY);
            if (supplied != null && !supplied.trim().isEmpty()) {
                input.setText(supplied.trim());
                input.setSelection(input.length());
                runSearch();
            } else input.requestFocus();
        }
        restoring = false;
        showRecentSearches();
        if (activeQuery.isEmpty()) queryChanged(input.getText().toString());
        updateContentVisibility();
    }

    private Scope initialScope(Bundle state) {
        if (state != null && state.getString("scope") != null) {
            Scope saved = parseScope(state.getString("scope"));
            if (saved != null) return saved;
        }
        if (state != null) {
            String legacy = state.getString("filter", "");
            if ("BUNKR".equals(legacy)) return Scope.ONLYFAP;
            if ("LIBRARY".equals(legacy)) return Scope.LIBRARY;
        }
        return scopeFromIntent(intent);
    }

    private static Scope scopeFromIntent(Intent intent) {
        String extra = intent == null ? "" : intent.getStringExtra(EXTRA_SCOPE);
        Scope parsed = parseScope(extra);
        return parsed == null ? Scope.SHOWS : parsed;
    }

    private static Scope parseScope(String value) {
        if (value == null) return null;
        switch (value.trim().toLowerCase(Locale.US)) {
            case "shows": return Scope.SHOWS;
            case "onlyfap":
            case "bunkr": return Scope.ONLYFAP;
            case "xmegadrive": return Scope.XMEGADRIVE;
            case "baddiehub": return Scope.BADDIEHUB;
            case "library": return Scope.LIBRARY;
            default:
                try { return Scope.valueOf(value.trim()); }
                catch (Exception ignored) { return null; }
        }
    }

    private void buildUi() {
        shell = new SearchGlassLayout(activity);
        shell.setClickable(true);
        shell.setOnClickListener(v -> dismiss.run());
        shell.installInsets();
        headerControls = new LinearLayout(activity);
        headerControls.setOrientation(LinearLayout.VERTICAL);
        headerControls.addView(buildSearchRow(), new LinearLayout.LayoutParams(-1, dp(56)));
        header = new GlassHeaderView(activity, headerControls, 28f, false, true);
        scopeSurface = new GlassHeaderView(activity, buildScopeBar(), 20f, false, true);
        if (overlay) scopeSurface.setAlpha(0f);
        View favorites = searchAction("★", "Favorite creators", v ->
                activity.startActivity(new Intent(activity, CreatorsActivity.class)));
        favoriteSurface = new GlassHeaderView(activity, favorites, 24f, false, true);
        resultSurface = new GlassHeaderView(activity, new FrameLayout(activity), 24f, true);
        resultsControls = new LinearLayout(activity);
        resultsControls.setOrientation(LinearLayout.VERTICAL);
        resultsControls.setClipChildren(true);
        resultsControls.setPadding(0, 0, 0, dp(14));
        resultsControls.setClipToPadding(false);
        resultsControls.setOnClickListener(v -> {});
        shell.install(header, favoriteSurface, resultSurface, resultsControls, scopeSurface);

        content = new FrameLayout(activity);
        content.setClipChildren(true);
        content.setClipToPadding(false);
        content.setClickable(true);
        resultsControls.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        resultLayer = new FrameLayout(activity);
        resultLayer.setClipChildren(true);
        resultLayer.setClipToPadding(false);
        content.addView(resultLayer, new FrameLayout.LayoutParams(-1, -1));
        recycler = new RecyclerView(activity);
        recycler.setLayoutManager(new LinearLayoutManager(activity));
        recycler.setBackgroundColor(Color.TRANSPARENT);
        recycler.setClipToPadding(false);
        recycler.setPadding(0, dp(68), 0, dp(4));
        recycler.setItemAnimator(null);
        onlyFapAdapter = new OnlyFapCreatorSearchAdapter(this::openCreator);
        adapter = new GlobalSearchAdapter(this::openResult);
        RecyclerView.AdapterDataObserver sizeObserver = new RecyclerView.AdapterDataObserver() {
            @Override public void onChanged() { updateResultSize(); }
            @Override public void onItemRangeInserted(int start, int count) { updateResultSize(); }
            @Override public void onItemRangeRemoved(int start, int count) { updateResultSize(); }
        };
        adapter.registerAdapterDataObserver(sizeObserver);
        onlyFapAdapter.registerAdapterDataObserver(sizeObserver);
        applyScopeAdapter();
        FrameLayout.LayoutParams listParams = new FrameLayout.LayoutParams(-1, -1);
        listParams.topMargin = -dp(68);
        resultLayer.addView(recycler, listParams);
        status = BrowseUi.text(activity, "", 12, BrowseUi.MUTED);
        status.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        status.setPadding(dp(12), dp(14), dp(12), dp(8));
        resultLayer.addView(status, new FrameLayout.LayoutParams(-1, -2));
        recentPanel = new LinearLayout(activity);
        recentPanel.setOrientation(LinearLayout.VERTICAL);
        android.widget.ScrollView recentScroll = new android.widget.ScrollView(activity);
        recentScroll.setFillViewport(false);
        recentScroll.addView(recentPanel);
        recentLayer = recentScroll;
        content.addView(recentScroll, new FrameLayout.LayoutParams(-1, -1));
        searchState = BrowseUi.text(activity, "", 11, BrowseUi.MUTED);
        searchState.setPadding(dp(14), dp(4), dp(14), dp(8));
        searchState.setVisibility(View.GONE);
        searchState.setOnClickListener(v -> {
            if (activeQuery.isEmpty()) return;
            startScopedSearch(activeQuery);
        });
        LinearLayout footer = new LinearLayout(activity);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        progress = new ZeroChillProgressBar(activity);
        progress.setVisibility(View.GONE);
        footer.addView(progress, new LinearLayout.LayoutParams(dp(40), dp(28)));
        footer.addView(searchState, new LinearLayout.LayoutParams(0, -2, 1f));
        searchFooter = footer;
        resultsControls.addView(footer);
        showRecentSearches();
        updateHints();
        SearchTextContrast.apply(shell);
        view = shell;
        updateContentVisibility();
    }

    private boolean showingRecent() { return input.getText().toString().trim().isEmpty(); }

    private void showRecentSearches() {
        if (recentPanel == null) return;
        recentPanel.removeAllViews();
        List<String> history = RecentSearchStore.load(activity);
        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = BrowseUi.text(activity, "Recent searches", 16, Color.WHITE);
        title.setPadding(dp(14), 0, 0, 0);
        heading.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        title.setGravity(Gravity.CENTER_VERTICAL);
        TextView clearHistory = BrowseUi.action(activity, "Clear all", "Clear all recent searches", v -> {
            RecentSearchStore.clear(activity);
            showRecentSearches();
            updateContentVisibility();
        });
        clearHistory.setTextSize(13.5f);
        clearHistory.setBackgroundColor(Color.TRANSPARENT);
        heading.addView(clearHistory, new LinearLayout.LayoutParams(-2, dp(48)));
        recentPanel.addView(heading);
        for (String query : history) {
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = BrowseUi.text(activity, query, 17, Color.WHITE);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER_VERTICAL);
            label.setPadding(dp(14), 0, dp(8), 0);
            label.setContentDescription("Search again for " + query);
            label.setOnClickListener(v -> {
                input.setText(query);
                input.setSelection(input.length());
                runSearch();
            });
            row.addView(label, new LinearLayout.LayoutParams(0, dp(56), 1f));
            TextView remove = BrowseUi.action(activity, "×", "Remove recent search " + query, v -> {
                RecentSearchStore.remove(activity, query);
                showRecentSearches();
                updateContentVisibility();
            });
            remove.setBackgroundColor(Color.TRANSPARENT);
            row.addView(remove, new LinearLayout.LayoutParams(dp(48), dp(48)));
            recentPanel.addView(row);
        }
        SearchTextContrast.apply(recentPanel);
        boolean recent = showingRecent();
        recentLayer.setVisibility(recent ? View.VISIBLE : View.GONE);
        resultLayer.setVisibility(recent ? View.INVISIBLE : View.VISIBLE);
        searchFooter.setVisibility(recent ? View.GONE : View.VISIBLE);
    }

    private void queryChanged(String value) {
        if (restoring || destroyed) return;
        cancelSearchWork();
        String query = value.trim();
        if (!query.equals(activeQuery)) {
            activeQuery = "";
            creatorResults = new ArrayList<>();
            clearResults();
        }
        if (query.isEmpty()) {
            progress.setVisibility(View.GONE);
            showRecentSearches();
            updateContentVisibility();
            return;
        }
        recentLayer.setVisibility(View.GONE);
        resultLayer.setVisibility(View.VISIBLE);
        searchFooter.setVisibility(View.VISIBLE);
        previewMode = false;
        status.setText(query.length() < 2 ? "Type at least 2 characters" : "");
        status.setVisibility(query.length() < 2 ? View.VISIBLE : View.GONE);
        searchState.setVisibility(query.length() < 2 ? View.GONE : View.VISIBLE);
        searchState.setText(query.length() < 2 ? "" : "Searching " + scopeLabel(scope) + "…");
        searchFooter.setVisibility(query.length() < 2 ? View.GONE : View.VISIBLE);
        progress.setVisibility(query.length() < 2 ? View.GONE : View.VISIBLE);
        if (query.length() >= 2) {
            final String requested = query;
            liveSearch = () -> startScopedSearch(requested);
            searchHandler.postDelayed(liveSearch, debounceFor(scope));
        }
        updateContentVisibility();
    }

    private long debounceFor(Scope value) {
        if (value == Scope.XMEGADRIVE) return XMEGA_DEBOUNCE_MS;
        if (value == Scope.ONLYFAP) return ONLYFAP_DEBOUNCE_MS;
        if (value == Scope.LIBRARY) return LIBRARY_DEBOUNCE_MS;
        return REMOTE_DEBOUNCE_MS;
    }

    private void cancelSearchWork() {
        if (liveSearch != null) searchHandler.removeCallbacks(liveSearch);
        generation++;
        for (Future<?> request : requests) request.cancel(true);
        requests.clear();
        if (io instanceof java.util.concurrent.ThreadPoolExecutor)
            ((java.util.concurrent.ThreadPoolExecutor) io).purge();
    }

    private void clearResults() {
        if (adapter != null) { adapter.resetEntrance(); adapter.replace(new ArrayList<>()); }
        if (onlyFapAdapter != null) { onlyFapAdapter.resetEntrance(); onlyFapAdapter.replace(new ArrayList<>()); }
        crazyVideos = new ArrayList<>();
        efuktVideos = new ArrayList<>();
        kaoticVideos = new ArrayList<>();
        xmegaResults = new ArrayList<>();
        bunkrResults = new ArrayList<>();
        baddieResults = new ArrayList<>();
        library = new ArrayList<>();
        creatorResults = new ArrayList<>();
        restoredScroll = null;
        if (recycler != null) recycler.scrollToPosition(0);
    }

    private void updateResultSize() {
        if (shell == null || recycler == null || recycler.getAdapter() == null) return;
        boolean recent = showingRecent();
        resultSurface.setRecentSurface(recent);
        if (recent) shell.setRecentContent(recentPanel);
        else {
            int count = recycler.getAdapter().getItemCount();
            boolean shortQuery = input.getText().toString().trim().length() < 2;
            shell.setResultCount(shortQuery ? 0 : Math.max(1, count), scope == Scope.ONLYFAP ? 76 : 104);
        }
    }

    private void updateContentVisibility() {
        if (content == null || input == null || resultsControls == null || closing) return;
        boolean show = !showingRecent() || !RecentSearchStore.load(activity).isEmpty();
        updateResultSize();
        if (show) content.setVisibility(View.VISIBLE);
        if (show == resultShown) {
            if (!show) {
                content.setVisibility(View.INVISIBLE);
                resultSurface.setVisibility(View.INVISIBLE);
                resultsControls.setVisibility(View.INVISIBLE);
                resultSurface.setAlpha(0f);
                resultsControls.setAlpha(0f);
            }
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
            resultSurface.setAlpha(amount);
            resultsControls.setAlpha(amount);
            float rise = dp(8) * (1f - amount);
            resultSurface.setTranslationY(rise);
            resultsControls.setTranslationY(rise);
        });
        resultsAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (!resultShown) {
                    content.setVisibility(View.INVISIBLE);
                    resultSurface.setVisibility(View.INVISIBLE);
                    resultsControls.setVisibility(View.INVISIBLE);
                }
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
        input.setHintTextColor(ZeroChillUi.color(activity, R.color.zc_text_muted));
        input.setTextColor(ZeroChillUi.color(activity, R.color.zc_text_primary));
        input.setTextSize(16f);
        input.setSingleLine(true);
        input.setMinWidth(0);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
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
            input.setText("");
            focus();
        });
        clear.setVisibility(View.GONE);
        row.addView(clear, new LinearLayout.LayoutParams(dp(44), dp(48)));
        input.addTextChangedListener(BrowseUi.onText(value -> {
            clear.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
            if (recentPanel != null) queryChanged(value);
            updateContentVisibility();
        }));
        return row;
    }

    private View buildScopeBar() {
        scopeScroll = new HorizontalScrollView(activity);
        scopeScroll.setHorizontalScrollBarEnabled(false);
        scopeScroll.setFillViewport(true);
        scopeScroll.setBackgroundColor(Color.TRANSPARENT);
        scopeScroll.setContentDescription("Search source");
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Color.TRANSPARENT);
        row.setPadding(dp(6), 0, dp(6), 0);
        scopeScroll.addView(row, new HorizontalScrollView.LayoutParams(-2, -1));
        addScope(row, "Shows", Scope.SHOWS);
        addScope(row, "OnlyFap", Scope.ONLYFAP);
        addScope(row, "Bunkr", Scope.BUNKR_ALBUMS);
        addScope(row, "XMegaDrive", Scope.XMEGADRIVE);
        addScope(row, "BaddieHub", Scope.BADDIEHUB);
        addScope(row, "Library", Scope.LIBRARY);
        refreshScopeStyles();
        return scopeScroll;
    }

    private void addScope(LinearLayout row, String label, Scope value) {
        TextView chip = new TextView(activity);
        chip.setText(label);
        chip.setTag(value);
        chip.setTextSize(13.5f);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(dp(12), 0, dp(12), 0);
        chip.setMinHeight(dp(32));
        chip.setContentDescription(label + " search");
        chip.setOnClickListener(v -> selectScope((Scope) v.getTag(), chip));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(32));
        params.setMargins(dp(3), 0, dp(3), 0);
        row.addView(chip, params);
        scopeViews.add(chip);
    }

    private void selectScope(Scope next, View chip) {
        if (next == null || destroyed) return;
        haptic(chip);
        if (next != scope) {
            scope = next;
            cancelSearchWork();
            clearResults();
            errors.clear();
            pendingSources = 0;
            applyScopeAdapter();
        }
        refreshScopeStyles();
        updateHints();
        String query = input.getText().toString().trim();
        if (query.length() >= 2) startScopedSearch(query);
        else if (query.isEmpty()) showRecentSearches();
        else {
            status.setText("Type at least 2 characters");
            status.setVisibility(View.VISIBLE);
            progress.setVisibility(View.GONE);
        }
        updateContentVisibility();
        if (scopeScroll != null && chip != null) chip.post(() -> scopeScroll.smoothScrollTo(chip.getLeft(), 0));
    }

    private void refreshScopeStyles() {
        for (TextView chip : scopeViews) {
            boolean selected = chip.getTag() == scope;
            ZeroChillUi.styleSourceRailChip(chip, selected);
            chip.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void applyScopeAdapter() {
        if (recycler == null) return;
        RecyclerView.Adapter<?> next = scope == Scope.ONLYFAP ? onlyFapAdapter : adapter;
        if (recycler.getAdapter() != next) recycler.setAdapter(next);
    }

    private void updateHints() {
        if (input == null) return;
        String hint;
        String description;
        switch (scope) {
            case ONLYFAP:
                hint = "Search creators";
                description = "Search OnlyFap creators";
                break;
            case BUNKR_ALBUMS:
                hint = "Search Bunkr albums";
                description = "Search Bunkr albums";
                break;
            case XMEGADRIVE:
                hint = "Search XMegaDrive";
                description = "Search XMegaDrive";
                break;
            case BADDIEHUB:
                hint = "Search BaddieHub";
                description = "Search BaddieHub";
                break;
            case LIBRARY:
                hint = "Search Library";
                description = "Search Library";
                break;
            case SHOWS:
            default:
                hint = "Search Shows";
                description = "Search Shows";
                break;
        }
        input.setHint(hint);
        input.setContentDescription(description);
        if (status != null && showingRecent()) status.setText(description);
    }

    private TextView searchAction(String label, String description, View.OnClickListener listener) {
        TextView action = BrowseUi.action(activity, label, description, listener);
        action.setBackgroundColor(Color.TRANSPARENT);
        action.setTextSize(22f);
        action.setPadding(0, 0, 0, 0);
        return action;
    }

    private void runSearch() {
        String query = input.getText().toString().trim();
        if (query.length() < 2) {
            input.setError("Type at least 2 characters");
            return;
        }
        BrowseUi.hideKeyboard(activity, input);
        input.clearFocus();
        previewMode = false;
        RecentSearchStore.remember(activity, query);
        recentLayer.setVisibility(View.GONE);
        resultLayer.setVisibility(View.VISIBLE);
        searchFooter.setVisibility(View.VISIBLE);
        startScopedSearch(query);
    }

    private void startScopedSearch(String query) {
        if (query == null || query.trim().length() < 2 || destroyed) return;
        switch (scope) {
            case ONLYFAP: startOnlyFapSearch(query); break;
            case BUNKR_ALBUMS: startBunkrSearch(query); break;
            case XMEGADRIVE: startXMegaDriveSearch(query); break;
            case BADDIEHUB: startBaddieHubSearch(query); break;
            case LIBRARY: startLibrarySearch(query); break;
            case SHOWS:
            default: startShowsSearch(query); break;
        }
    }

    private void openCreator(NativeContentItem item) {
        RecentSearchStore.remember(activity, input.getText().toString());
        BrowseUi.hideKeyboard(activity, input);
        activity.startActivity(NativeFeedBrowserActivity.createCreatorGallery(activity, item.title,
                item.searchQuery.isEmpty() ? item.title : item.searchQuery,
                NativeFeedBrowserActivity.creatorProfileHint(item),
                CreatorGalleryPreloader.sessionId(activity, item)));
    }

    private void publishCreators(List<NativeContentItem> next) {
        creatorResults = OnlyFapCreatorResults.merge(creatorResults, next, 80);
        if (scope == Scope.ONLYFAP && onlyFapAdapter != null) onlyFapAdapter.replace(creatorResults);
        updateResultSize();
    }

    private void startOnlyFapSearch(String query) {
        cancelSearchWork();
        applyScopeAdapter();
        searchPreference = ContentPreference.get(activity);
        creatorResults = new ArrayList<>();
        if (onlyFapAdapter != null) onlyFapAdapter.replace(creatorResults);
        activeQuery = query.trim();
        final String requestedQuery = activeQuery;
        final int token = generation;
        final Scope requestedScope = Scope.ONLYFAP;
        pendingSources = 1;
        errors.clear();
        progress.setVisibility(View.VISIBLE);
        status.setVisibility(View.GONE);
        searchState.setVisibility(View.VISIBLE);
        searchState.setText("Finding saved creators · Checking sources…");
        requests.add(io.submit(() -> {
            BundledCreatorIndex index = BundledCreatorIndex.get(activity.getApplicationContext());
            List<NativeContentItem> local = OnlyFapCreatorResults.merge(
                    searchPreference == ContentPreference.ALL ? index.matching(requestedQuery, 60) : null,
                    CreatorCategoryIndex.filter(activity, searchPreference,
                            CreatorCatalog.matching(activity.getApplicationContext(), requestedQuery, false, 60)), 60);
            local.sort(java.util.Comparator.comparingInt((NativeContentItem item) -> index.rank(item, requestedQuery))
                    .thenComparing(item -> CreatorNameMatcher.normalized(item.title)));
            activity.runOnUiThread(() -> {
                if (!delivers(token, requestedScope, requestedQuery)) return;
                publishCreators(local);
                status.setVisibility(View.GONE);
                searchState.setText(creatorResults.size() + " creators · Checking sources…");
            });
            List<NativeContentItem> result = null;
            try {
                result = new FapzoneCreatorSearchRepository().search(activity, requestedQuery, 40, (partial, complete) -> {
                    if (complete || partial == null || partial.isEmpty()) return;
                    activity.runOnUiThread(() -> {
                        if (!delivers(token, requestedScope, requestedQuery)) return;
                        publishCreators(partial);
                        status.setVisibility(View.GONE);
                        searchState.setText(creatorResults.size() + " creators · Checking sources…");
                    });
                });
            } catch (Exception ignored) { }
            List<NativeContentItem> creators = result;
            activity.runOnUiThread(() -> {
                if (!delivers(token, requestedScope, requestedQuery)) return;
                if (creators != null) CreatorCatalog.remember(activity.getApplicationContext(), creators);
                pendingSources = 0;
                publishCreators(creators);
                progress.setVisibility(View.GONE);
                searchState.setText(creators == null ? "Creator search incomplete · Tap to retry"
                        : creatorResults.size() + (creatorResults.size() == 1 ? " creator" : " creators"));
                searchState.setContentDescription(searchState.getText());
                if (creatorResults.isEmpty()) {
                    status.setText(creators == null ? "Some creator sources couldn't be checked.\nTap the message below to retry."
                            : "No creator matches for “" + requestedQuery + "”\n\nTry a shorter or more general search.");
                    status.setVisibility(View.VISIBLE);
                } else {
                    status.setVisibility(View.GONE);
                    restoreScroll();
                }
            });
        }));
    }

    private boolean delivers(int token, Scope requested, String query) {
        return !destroyed && !activity.isFinishing() && !activity.isDestroyed()
                && token == generation && requested == scope && query.equals(activeQuery)
                && (requested != Scope.ONLYFAP || searchPreference == ContentPreference.get(activity));
    }

    private void startShowsSearch(String query) {
        cancelSearchWork();
        applyScopeAdapter();
        searchPreference = ContentPreference.get(activity);
        clearResults();
        activeQuery = query.trim();
        final String requested = activeQuery;
        final int token = generation;
        final Scope requestedScope = Scope.SHOWS;
        errors.clear();
        pendingSources = 3;
        searchStarted = android.os.SystemClock.elapsedRealtime();
        firstResultsRecorded = false;
        renderResults();
        source(token, requestedScope, requested, 0, "CrazyShit", () ->
                label(repository.fetchFeed(activity, repository.searchUrl(requested), 1), "CrazyShit"));
        source(token, requestedScope, requested, 1, "EFukt", () ->
                label(efuktRepository.search(activity, requested), "EFukt"));
        source(token, requestedScope, requested, 2, "Kaotic", () ->
                label(webVideoRepository.search(activity, requested), "Kaotic"));
    }

    private void startXMegaDriveSearch(String query) {
        if (!runsXMegaDrive(scope, query)) return;
        cancelSearchWork();
        applyScopeAdapter();
        clearResults();
        activeQuery = query.trim();
        final String requested = activeQuery;
        final int token = generation;
        final Scope requestedScope = Scope.XMEGADRIVE;
        if (!runsXMegaDrive(requestedScope, requested)) return;
        errors.clear();
        pendingSources = 1;
        searchStarted = android.os.SystemClock.elapsedRealtime();
        firstResultsRecorded = false;
        renderResults();
        source(token, requestedScope, requested, 0, "XMegaDrive", () ->
                xMegaDriveRepository.search(activity, requested));
    }

    private void startBunkrSearch(String query) {
        cancelSearchWork();
        applyScopeAdapter();
        clearResults();
        activeQuery = query.trim();
        final String requested = activeQuery;
        final int token = generation;
        errors.clear();
        pendingSources = 1;
        searchStarted = android.os.SystemClock.elapsedRealtime();
        firstResultsRecorded = false;
        renderResults();
        source(bunkrIo, token, Scope.BUNKR_ALBUMS, requested, 0, "Bunkr", () ->
                bunkrRepository.searchAlbums(activity, requested, 1));
    }

    private void startBaddieHubSearch(String query) {
        cancelSearchWork();
        applyScopeAdapter();
        clearResults();
        activeQuery = query.trim();
        final String requested = activeQuery;
        final int token = generation;
        final Scope requestedScope = Scope.BADDIEHUB;
        errors.clear();
        pendingSources = 1;
        searchStarted = android.os.SystemClock.elapsedRealtime();
        firstResultsRecorded = false;
        renderResults();
        source(token, requestedScope, requested, 0, "BaddieHub", () ->
                baddieHubRepository.search(activity, requested));
    }

    private void startLibrarySearch(String query) {
        cancelSearchWork();
        applyScopeAdapter();
        clearResults();
        activeQuery = query.trim();
        final String requested = activeQuery;
        final int token = generation;
        final Scope requestedScope = Scope.LIBRARY;
        errors.clear();
        pendingSources = 1;
        searchStarted = android.os.SystemClock.elapsedRealtime();
        firstResultsRecorded = false;
        renderResults();
        source(token, requestedScope, requested, 0, "Library", () -> searchLibrary(requested));
    }

    private void source(int token, Scope requested, String query, int id, String title,
                        Callable<List<NativeContentItem>> fetch) {
        source(io, token, requested, query, id, title, fetch);
    }

    private void source(ExecutorService executor, int token, Scope requested, String query, int id,
                        String title, Callable<List<NativeContentItem>> fetch) {
        requests.add(executor.submit(() -> {
            List<NativeContentItem> items = null;
            try {
                if (Thread.currentThread().isInterrupted()) return;
                items = fetch.call();
            } catch (Exception ignored) { }
            List<NativeContentItem> result = items;
            activity.runOnUiThread(() -> {
                if (!delivers(token, requested, query)) return;
                pendingSources = Math.max(0, pendingSources - 1);
                if (result == null) errors.put(id, title);
                else accept(requested, id, result);
                renderResults();
                if (pendingSources == 0 && requested != Scope.LIBRARY) saveSnapshot();
            });
        }));
    }

    private void accept(Scope requested, int id, List<NativeContentItem> result) {
        if (requested == Scope.SHOWS) {
            if (id == 0) crazyVideos = result;
            else if (id == 1) efuktVideos = result;
            else if (id == 2) kaoticVideos = result;
        } else if (requested == Scope.XMEGADRIVE) xmegaResults = result;
        else if (requested == Scope.BUNKR_ALBUMS) bunkrResults = result;
        else if (requested == Scope.BADDIEHUB) baddieResults = result;
        else if (requested == Scope.LIBRARY) library = result;
    }

    private List<NativeContentItem> label(List<NativeContentItem> items, String source) {
        ArrayList<NativeContentItem> labeled = new ArrayList<>();
        if (items == null) return labeled;
        for (NativeContentItem item : items) {
            if (item == null || item.isSection()) continue;
            if (source.equals(item.uploader)) labeled.add(item);
            else labeled.add(new NativeContentItem(
                    item.kind, item.title, item.url, item.imageUrl, item.views, source,
                    item.comments, item.description, item.searchQuery, item.publishedAtMillis,
                    item.aspectRatioHint));
        }
        return labeled;
    }

    private void saveSnapshot() {
        try {
            JSONObject data = new JSONObject().put("query", activeQuery);
            data.put("scope", scope.name());
            data.put("preference", ContentPreference.get(activity).value);
            data.put("complete", pendingSources == 0);
            data.put("crazyVideos", ContentItemCodec.encodeList(crazyVideos, 80));
            data.put("efuktVideos", ContentItemCodec.encodeList(efuktVideos, 80));
            data.put("kaoticVideos", ContentItemCodec.encodeList(kaoticVideos, 80));
            data.put("xmega", ContentItemCodec.encodeList(xmegaResults, 40));
            data.put("bunkrAlbums", ContentItemCodec.encodeList(bunkrResults, 48));
            data.put("baddie", ContentItemCodec.encodeList(baddieResults, 48));
            data.put("library", ContentItemCodec.encodeList(library, 80));
            ScreenSnapshotStore.save(activity, snapshotId, data);
        } catch (Exception ignored) { }
    }

    private boolean restoreSnapshot(JSONObject data) {
        if (data == null || !activeQuery.equals(data.optString("query"))) return false;
        if (!scope.name().equals(data.optString("scope"))) return false;
        if (scope == Scope.ONLYFAP
                && !ContentPreference.get(activity).value.equals(data.optString("preference"))) return false;
        searchPreference = ContentPreference.get(activity);
        crazyVideos = ContentItemCodec.decodeList(data.optJSONArray("crazyVideos"), 80);
        efuktVideos = ContentItemCodec.decodeList(data.optJSONArray("efuktVideos"), 80);
        kaoticVideos = ContentItemCodec.decodeList(data.optJSONArray("kaoticVideos"), 80);
        xmegaResults = ContentItemCodec.decodeList(data.optJSONArray("xmega"), 40);
        bunkrResults = ContentItemCodec.decodeList(data.optJSONArray("bunkrAlbums"), 48);
        baddieResults = ContentItemCodec.decodeList(data.optJSONArray("baddie"), 48);
        library = ContentItemCodec.decodeList(data.optJSONArray("library"), 80);
        pendingSources = data.optBoolean("complete", false) ? 0 : pendingSources;
        renderResults();
        return data.optBoolean("complete", false);
    }

    private List<NativeContentItem> searchLibrary(String query) {
        LinkedHashMap<String, NativeContentItem> result = new LinkedHashMap<>();
        try {
            for (FavoriteStore.Item saved : FavoriteStore.load(activity)) {
                NativeContentItem content = saved.content;
                if (!LibrarySearch.matches(query, saved.title, saved.url,
                        content == null ? "" : content.title,
                        content == null ? "" : content.uploader,
                        content == null ? "" : content.description,
                        content == null ? "" : content.searchQuery)) continue;
                NativeContentItem item = content == null
                        ? new NativeContentItem(NativeContentItem.KIND_MEDIA, saved.title, saved.url, "", "", "Library", "")
                        : content;
                putLibrary(result, item);
            }
        } catch (Exception ignored) { }
        try {
            for (PlaybackHistoryStore.Item history : PlaybackHistoryStore.load(activity)) {
                NativeContentItem content = history.content;
                if (!LibrarySearch.matches(query, history.title, history.pageUrl,
                        content == null ? "" : content.title,
                        content == null ? "" : content.uploader,
                        history.progressPercent() > 0 ? history.progressPercent() + "% watched" : "")) continue;
                NativeContentItem item = content != null ? content : new NativeContentItem(
                        NativeContentItem.KIND_MEDIA, history.title, history.pageUrl, history.posterUrl,
                        "", "Library", "",
                        history.progressPercent() > 0 ? history.progressPercent() + "% watched" : "");
                putLibrary(result, item);
            }
        } catch (Exception ignored) { }
        try {
            for (VideoDownloadStore.Entry download : VideoDownloadStore.entries(activity)) {
                if (!LibrarySearch.matches(query, download.title, download.pageUrl, download.mediaUrl,
                        download.content == null ? "" : download.content.title)) continue;
                NativeContentItem item = download.content != null ? download.content : new NativeContentItem(
                        NativeContentItem.KIND_MEDIA,
                        download.title.isEmpty() ? "Download" : download.title,
                        download.pageUrl.isEmpty() ? download.mediaUrl : download.pageUrl,
                        download.imageUrl, "", "Library", "", "Download");
                if (item.url == null || item.url.isEmpty()) continue;
                putLibrary(result, item);
            }
        } catch (Exception ignored) { }
        try {
            String owner = ZeroChillSessionStore.currentUserId(activity);
            for (NativeContentItem liked : SocialContentContextStore.likedVideos(activity, owner)) {
                if (liked == null) continue;
                if (!LibrarySearch.matches(query, liked.title, liked.url, liked.uploader,
                        liked.description, liked.searchQuery)) continue;
                putLibrary(result, liked);
            }
        } catch (Exception ignored) { }
        try {
            for (CreatorCatalog.FavoriteGroup group : CreatorCatalog.favoriteGroups(activity)) {
                if (group == null || group.item == null) continue;
                NativeContentItem creator = group.item;
                if (!LibrarySearch.matches(query, creator.title, creator.url, creator.searchQuery, creator.uploader)) continue;
                putLibrary(result, creator);
            }
        } catch (Exception ignored) { }
        ArrayList<NativeContentItem> items = new ArrayList<>(result.values());
        return items.size() > 80 ? new ArrayList<>(items.subList(0, 80)) : items;
    }

    private void putLibrary(LinkedHashMap<String, NativeContentItem> result, NativeContentItem item) {
        if (item == null || item.url == null || item.url.isEmpty() || result.containsKey(item.url)) return;
        result.put(item.url, item);
    }

    private void renderResults() {
        if (activeQuery.isEmpty() || adapter == null || scope == Scope.ONLYFAP) return;
        ArrayList<GlobalSearchAdapter.Entry> output = new ArrayList<>();
        if (scope == Scope.SHOWS) {
            appendSection(output, "CrazyShit", crazyVideos, GlobalSearchAdapter.SOURCE_REMOTE, 40);
            appendSection(output, "EFukt", efuktVideos, GlobalSearchAdapter.SOURCE_REMOTE, 40);
            appendSection(output, "Kaotic", kaoticVideos, GlobalSearchAdapter.SOURCE_REMOTE, 40);
        } else if (scope == Scope.XMEGADRIVE) {
            appendKind(output, "Videos", xmegaResults, false);
            appendKind(output, "Categories", xmegaResults, true);
        } else if (scope == Scope.BUNKR_ALBUMS) {
            appendSection(output, "Albums", bunkrResults, GlobalSearchAdapter.SOURCE_REMOTE, 48);
        } else if (scope == Scope.BADDIEHUB) {
            appendKind(output, "Videos", baddieResults, false);
            appendKind(output, "Categories", baddieResults, true);
        } else if (scope == Scope.LIBRARY) {
            appendSection(output, "Library", library, GlobalSearchAdapter.SOURCE_LIBRARY, 80);
        }
        if (!output.isEmpty() && !firstResultsRecorded && searchStarted > 0) {
            firstResultsRecorded = true;
            AppPerformance.record("Search first results", android.os.SystemClock.elapsedRealtime() - searchStarted);
        }
        adapter.replace(output);
        progress.setVisibility(pendingSources > 0 ? View.VISIBLE : View.GONE);
        String unavailable = unavailableNow();
        searchState.setVisibility(View.VISIBLE);
        searchState.setText(pendingSources > 0
                ? "Searching " + scopeLabel(scope) + " · " + pendingSources + " remaining"
                : unavailable.isEmpty() ? scopeLabel(scope) + " search complete"
                : "Unavailable: " + unavailable + " · Tap to retry");
        searchState.setContentDescription(searchState.getText());
        if (output.isEmpty()) {
            status.setText(pendingSources > 0 ? "" : !unavailable.isEmpty()
                    ? "Some sources could not be reached.\nTap the message below to retry."
                    : "No matches for “" + activeQuery + "”\n\nTry a shorter or more general search.");
            status.setVisibility(pendingSources > 0 ? View.GONE : View.VISIBLE);
        } else {
            status.setVisibility(View.GONE);
            restoreScroll();
        }
    }

    private void appendKind(List<GlobalSearchAdapter.Entry> out, String title,
                            List<NativeContentItem> items, boolean categories) {
        if (items == null || items.isEmpty()) return;
        ArrayList<NativeContentItem> matched = new ArrayList<>();
        for (NativeContentItem item : items) {
            if (item == null) continue;
            if (categories == item.isCategory()) matched.add(item);
        }
        appendSection(out, title, matched, GlobalSearchAdapter.SOURCE_REMOTE, 40);
    }

    private String unavailableNow() {
        ArrayList<String> unavailable = new ArrayList<>();
        for (String error : errors.values()) if (error != null && !error.isEmpty()) unavailable.add(error);
        return String.join(", ", unavailable);
    }

    private static String scopeLabel(Scope value) {
        switch (value) {
            case ONLYFAP: return "OnlyFap";
            case BUNKR_ALBUMS: return "Bunkr";
            case XMEGADRIVE: return "XMegaDrive";
            case BADDIEHUB: return "BaddieHub";
            case LIBRARY: return "Library";
            case SHOWS:
            default: return "Shows";
        }
    }

    private void appendSection(List<GlobalSearchAdapter.Entry> out, String title,
                               List<NativeContentItem> items, int source, int limit) {
        if (items == null || items.isEmpty()) return;
        out.add(GlobalSearchAdapter.Entry.section(title + "  •  " + items.size()));
        int count = Math.min(limit, items.size());
        for (int i = 0; i < count; i++) out.add(GlobalSearchAdapter.Entry.item(items.get(i), source));
    }

    private void restoreScroll() {
        if (restoredScroll != null && recycler.getLayoutManager() != null) {
            recycler.getLayoutManager().onRestoreInstanceState(restoredScroll);
            restoredScroll = null;
        }
    }

    private void openResult(NativeContentItem item) {
        if (item == null || item.url == null || item.url.isEmpty()) return;
        RecentSearchStore.remember(activity, input.getText().toString());
        haptic(recycler);
        if (item.isCreator()) {
            openCreator(item);
            return;
        }
        if (item.isSeries() || item.isCategory()) {
            activity.startActivity(scope == Scope.SHOWS
                    ? NativeFeedBrowserActivity.createShowDetails(activity, item, feedSource(item))
                    : NativeFeedBrowserActivity.create(activity, item.title, item.url, false, feedSource(item)));
            return;
        }

        progress.setVisibility(View.VISIBLE);
        final int requestGeneration = generation;
        final Scope requestedScope = scope;
        final List<NativeContentItem> watchItems = new ArrayList<>();
        if (requestedScope == Scope.SHOWS) {
            watchItems.addAll(crazyVideos);
            watchItems.addAll(efuktVideos);
            watchItems.addAll(kaoticVideos);
        } else if (requestedScope == Scope.LIBRARY) {
            for (NativeContentItem candidate : library) {
                if (!OnlyFapVideoMetadata.applies(candidate)) watchItems.add(candidate);
            }
        }
        io.execute(() -> {
            CrazyShitRepository.StreamInfo stream = null;
            try { stream = PlayableSourceRouter.resolve(activity, item.url); }
            catch (Exception ignored) { }
            CrazyShitRepository.StreamInfo resolved = stream;
            activity.runOnUiThread(() -> {
                if (destroyed || activity.isFinishing() || requestGeneration != generation
                        || requestedScope != scope) return;
                progress.setVisibility(View.GONE);
                if (resolved == null || resolved.mediaUrl == null || resolved.mediaUrl.isEmpty()) {
                    ZeroChillToast.makeText(activity, "Couldn't resolve this video natively right now.",
                            ZeroChillToast.LENGTH_SHORT).show();
                    return;
                }
                Intent playback = new Intent(activity, VideoDetailActivity.class);
                playback.putExtra(PlayerActivity.EXTRA_MEDIA_URL, resolved.mediaUrl);
                playback.putExtra(PlayerActivity.EXTRA_PAGE_URL,
                        requestedScope == Scope.SHOWS || requestedScope == Scope.LIBRARY
                                ? item.url : resolved.pageUrl);
                playback.putExtra(PlayerActivity.EXTRA_TITLE,
                        item.title == null || item.title.trim().isEmpty() ? resolved.title : item.title);
                playback.putExtra(VideoDetailActivity.EXTRA_VIEWS, item.views);
                playback.putExtra(VideoDetailActivity.EXTRA_UPLOADER, item.uploader);
                playback.putExtra(VideoDetailActivity.EXTRA_COMMENTS, item.comments);
                playback.putExtra(VideoDetailActivity.EXTRA_MEDIA_REFERER, resolved.requestReferer);
                putPlaybackSource(playback, item);
                if (requestedScope == Scope.SHOWS || (requestedScope == Scope.LIBRARY
                        && !OnlyFapVideoMetadata.applies(item))) {
                    ShowsWatchSession.attach(playback, watchItems, false, "Shows");
                }
                try {
                    String agent = WebSettings.getDefaultUserAgent(activity);
                    if (XMegaDriveRepository.isXMegaDriveUrl(item.url)
                            || (resolved.pageUrl != null && XMegaDriveRepository.isXMegaDriveUrl(resolved.pageUrl))) {
                        String session = XMegaDriveBrowserFetch.sessionUserAgent();
                        if (session != null && !session.trim().isEmpty()) agent = session.trim();
                    }
                    playback.putExtra(PlayerActivity.EXTRA_USER_AGENT, agent);
                } catch (Exception ignored) { }
                try {
                    String cookies = CookieManager.getInstance().getCookie(resolved.mediaUrl);
                    if ((cookies == null || cookies.isEmpty()) && resolved.pageUrl != null) {
                        cookies = CookieManager.getInstance().getCookie(resolved.pageUrl);
                    }
                    if (cookies != null) playback.putExtra(PlayerActivity.EXTRA_COOKIES, cookies);
                } catch (Exception ignored) { }
                activity.startActivity(playback);
            });
        });
    }

    private String feedSource(NativeContentItem item) {
        String url = item == null || item.url == null ? "" : item.url;
        if (XMegaDriveRepository.isCategoryUrl(url) || XMegaDriveRepository.isXMegaDriveUrl(url))
            return NativeFeedBrowserActivity.SOURCE_XMEGADRIVE;
        if (BaddieHubRepository.isBaddieHubUrl(url)) return NativeFeedBrowserActivity.SOURCE_BADDIEHUB;
        if (WebVideoSourceRepository.isKaoticUrl(url)) return NativeFeedBrowserActivity.SOURCE_KAOTIC;
        if (BunkrRepository.isAlbumUrl(url)) return NativeFeedBrowserActivity.SOURCE_BUNKR;
        if (EfuktRepository.isEfuktUrl(url)) return NativeFeedBrowserActivity.SOURCE_EFUKT;
        return NativeFeedBrowserActivity.SOURCE_CRAZYSHIT;
    }

    private void putPlaybackSource(Intent playback, NativeContentItem item) {
        String url = item.url == null ? "" : item.url;
        if (XMegaDriveRepository.isXMegaDriveUrl(url)) {
            playback.putExtra(VideoDetailActivity.EXTRA_SOURCE, NativeFeedBrowserActivity.SOURCE_XMEGADRIVE);
        } else if (BaddieHubRepository.isBaddieHubUrl(url)) {
            playback.putExtra(VideoDetailActivity.EXTRA_SOURCE, NativeFeedBrowserActivity.SOURCE_BADDIEHUB);
        } else if (WebVideoSourceRepository.isKaoticUrl(url)) {
            playback.putExtra(VideoDetailActivity.EXTRA_SOURCE, NativeFeedBrowserActivity.SOURCE_KAOTIC);
        } else if (BunkrRepository.isBunkrUrl(url)) {
            playback.putExtra(VideoDetailActivity.EXTRA_SOURCE, NativeFeedBrowserActivity.SOURCE_BUNKR);
        } else if (EfuktRepository.isEfuktUrl(url)) {
            playback.putExtra(VideoDetailActivity.EXTRA_RELATED_FEED_URL, EfuktRepository.SERIES);
            playback.putExtra(VideoDetailActivity.EXTRA_SOURCE, NativeFeedBrowserActivity.SOURCE_EFUKT);
        }
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
        } else dismiss.run();
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
        if (expanding && morphProgress == 0f) {
            header.setAlpha(0f);
            favoriteSurface.setAlpha(0f);
            scopeSurface.setAlpha(0f);
        }
        if (expanding) {
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
                scopeSurface.setAlpha(amount);
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

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private void haptic(View target) {
        if (!activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE).getBoolean("haptics_enabled", true)) return;
        target.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    void resume() {
        if (searchPreference != null && searchPreference != ContentPreference.get(activity)
                && scope == Scope.ONLYFAP && activeQuery.length() >= 2) {
            startOnlyFapSearch(activeQuery);
            return;
        }
        if (showingRecent()) { showRecentSearches(); updateContentVisibility(); }
        else if (scope == Scope.ONLYFAP && onlyFapAdapter != null) onlyFapAdapter.notifyDataSetChanged();
        else if (adapter != null) adapter.notifyDataSetChanged();
    }

    void save(Bundle state) {
        state.putString("typed", input.getText().toString());
        state.putString("active", activeQuery);
        state.putString("scope", scope.name());
        state.putString("snapshot", snapshotId);
        state.putBoolean("preview", previewMode);
        if (adapter != null) adapter.saveOrder(state);
        try { state.putString("creators", ContentItemCodec.encodeList(creatorResults, 80).toString()); }
        catch (Exception ignored) { }
        state.putParcelable("scroll", recycler.getLayoutManager().onSaveInstanceState());
        if (scope != Scope.ONLYFAP && scope != Scope.LIBRARY) saveSnapshot();
    }

    void close() {
        destroyed = true;
        if (morphAnimator != null) { morphAnimator.removeAllListeners(); morphAnimator.cancel(); }
        if (resultsAnimator != null) { resultsAnimator.removeAllListeners(); resultsAnimator.cancel(); }
        generation++;
        searchHandler.removeCallbacksAndMessages(null);
        for (Future<?> request : requests) request.cancel(true);
        requests.clear();
        if (adapter != null) adapter.close();
        io.shutdownNow();
        bunkrIo.shutdownNow();
    }
}
