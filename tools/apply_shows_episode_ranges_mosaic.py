from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path, old, new):
    file = ROOT / path
    text = file.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"anchor missing in {path}: {old[:120]!r}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


def write(path, content):
    file = ROOT / path
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text(content, encoding="utf-8")


# NativeFeedAdapter: local episode numbering while preserving source titles.
replace_once(
    "app/src/main/java/com/webapp/crazyshit/NativeFeedAdapter.java",
    """    private boolean closed;\n    private boolean episodePresentation;\n\n    void setEpisodePresentation(boolean episode) {\n        episodePresentation = episode;\n        if (episode) setViewMode(VIEW_LIST);\n    }\n""",
    """    private boolean closed;\n    private boolean episodePresentation;\n    private int episodeNumberOffset;\n\n    void setEpisodePresentation(boolean episode) {\n        episodePresentation = episode;\n        if (episode) setViewMode(VIEW_LIST);\n    }\n\n    void setEpisodeNumberOffset(int offset) {\n        int next = Math.max(0, offset);\n        if (episodeNumberOffset == next) return;\n        episodeNumberOffset = next;\n        if (episodePresentation) notifyDataSetChanged();\n    }\n""",
)
replace_once(
    "app/src/main/java/com/webapp/crazyshit/NativeFeedAdapter.java",
    "        holder.title.setText(displayTitle(item));\n",
    """        String visibleTitle = displayTitle(item);\n        holder.title.setText(episodePresentation\n                ? \"#\" + (episodeNumberOffset + position + 1) + \"  \" + visibleTitle\n                : visibleTitle);\n""",
)

# NativeFeedBrowserActivity: keep the full ordered source list and show 25-item windows.
replace_once(
    "app/src/main/java/com/webapp/crazyshit/NativeFeedBrowserActivity.java",
    """    private String showImageUrl;\n    private String showDescription;\n    private String showKind;\n    private boolean showDetailsMode;\n""",
    """    private String showImageUrl;\n    private String showDescription;\n    private String showKind;\n    private final ArrayList<NativeContentItem> showEpisodes = new ArrayList<>();\n    private TextView episodeRangeAction;\n    private int episodeRangeStart;\n    private boolean showDetailsMode;\n""",
)
replace_once(
    "app/src/main/java/com/webapp/crazyshit/NativeFeedBrowserActivity.java",
    """        restoredBrowserState = state;\n        if (state != null) {\n            browserSnapshot = state.getString(\"browser_snapshot\", browserSnapshot);\n""",
    """        restoredBrowserState = state;\n        episodeRangeStart = state == null\n                ? 0\n                : Math.max(0, state.getInt(\"episode_range_start\", 0));\n        if (state != null) {\n            browserSnapshot = state.getString(\"browser_snapshot\", browserSnapshot);\n""",
)
replace_once(
    "app/src/main/java/com/webapp/crazyshit/NativeFeedBrowserActivity.java",
    """            TextView section = text(\"EPISODES\", 12, UiPalette.PRIMARY);\n            section.setTypeface(null, android.graphics.Typeface.BOLD);\n            section.setLetterSpacing(0.12f);\n            section.setGravity(Gravity.CENTER_VERTICAL);\n            section.setPadding(dp(16), dp(9), dp(16), dp(8));\n            detailsFeed.addView(section, new LinearLayout.LayoutParams(-1, dp(40)));\n            detailsFeed.addView(refresh, new LinearLayout.LayoutParams(-1, 0, 1f));\n""",
    """            LinearLayout episodeBar = new LinearLayout(this);\n            episodeBar.setOrientation(LinearLayout.HORIZONTAL);\n            episodeBar.setGravity(Gravity.CENTER_VERTICAL);\n            episodeBar.setPadding(dp(16), dp(5), dp(12), dp(5));\n\n            TextView section = text(\"EPISODES\", 12, UiPalette.PRIMARY);\n            section.setTypeface(null, android.graphics.Typeface.BOLD);\n            section.setLetterSpacing(0.12f);\n            section.setGravity(Gravity.CENTER_VERTICAL);\n            episodeBar.addView(section, new LinearLayout.LayoutParams(0, -1, 1f));\n\n            episodeRangeAction = text(\"1–25 ▾\", 12, Color.WHITE);\n            episodeRangeAction.setTypeface(null, android.graphics.Typeface.BOLD);\n            episodeRangeAction.setGravity(Gravity.CENTER);\n            episodeRangeAction.setPadding(dp(13), 0, dp(13), 0);\n            episodeRangeAction.setBackground(ZeroChillUi.rounded(\n                    this,\n                    ZeroChillUi.color(this, R.color.zc_surface_glass_strong),\n                    ZeroChillUi.color(this, R.color.zc_edge),\n                    R.dimen.zc_radius_pill\n            ));\n            episodeRangeAction.setContentDescription(\"Choose episode range\");\n            episodeRangeAction.setVisibility(View.GONE);\n            episodeRangeAction.setOnClickListener(v -> showEpisodeRangePicker());\n            ZeroChillMotion.installPressFeedback(episodeRangeAction);\n            episodeBar.addView(episodeRangeAction, new LinearLayout.LayoutParams(-2, dp(34)));\n\n            detailsFeed.addView(episodeBar, new LinearLayout.LayoutParams(-1, dp(48)));\n            detailsFeed.addView(refresh, new LinearLayout.LayoutParams(-1, 0, 1f));\n""",
)
replace_once(
    "app/src/main/java/com/webapp/crazyshit/NativeFeedBrowserActivity.java",
    """        } else {\n            adapter.replace(new ArrayList<>());\n        }\n        updateShowDetailsHeader();\n        load(false);\n""",
    """        } else {\n            if (showDetailsMode) {\n                showEpisodes.clear();\n                episodeRangeStart = 0;\n                adapter.setEpisodeNumberOffset(0);\n            }\n            adapter.replace(new ArrayList<>());\n            updateEpisodeRangeUi();\n        }\n        updateShowDetailsHeader();\n        load(false);\n""",
)
replace_once(
    "app/src/main/java/com/webapp/crazyshit/NativeFeedBrowserActivity.java",
    """                    } else if (append) {\n                        adapter.append(result);\n                    } else {\n                        adapter.replace(result);\n                    }\n""",
    """                    } else if (showDetailsMode) {\n                        if (append) appendShowEpisodes(result);\n                        else replaceShowEpisodes(result);\n                        applyEpisodeRange(false, -1);\n                    } else if (append) {\n                        adapter.append(result);\n                    } else {\n                        adapter.replace(result);\n                    }\n""",
)
replace_once(
    "app/src/main/java/com/webapp/crazyshit/NativeFeedBrowserActivity.java",
    """        final List<NativeContentItem> watchItems = showDetailsMode && adapter != null\n                ? adapter.snapshot() : java.util.Collections.emptyList();\n""",
    """        final List<NativeContentItem> watchItems = showDetailsMode\n                ? new ArrayList<>(showEpisodes)\n                : java.util.Collections.emptyList();\n""",
)
replace_once(
    "app/src/main/java/com/webapp/crazyshit/NativeFeedBrowserActivity.java",
    """    private void updateShowDetailsHeader() {\n        if (showDetailsHeader == null || adapter == null) return;\n        showDetailsHeader.setItemCount((int) adapter.snapshot().stream().filter(ShowsEpisodePlayback::playable).count());\n        ShowsEpisodePlayback.Selection selection = ShowsEpisodePlayback.select(adapter.snapshot(), PlaybackHistoryStore.load(this));\n        showDetailsHeader.setPlaybackAction(selection.positionMs > 0L, selection.item != null);\n    }\n\n    private void playSeries() {\n        if (adapter == null) return;\n        ShowsEpisodePlayback.Selection selection = ShowsEpisodePlayback.select(adapter.snapshot(), PlaybackHistoryStore.load(this));\n        if (selection.item != null) openVideo(selection.item, selection.positionMs);\n    }\n\n    private int itemCount() {\n        return isBunkr()\n                ? (bunkrGalleryAdapter == null ? 0 : bunkrGalleryAdapter.getItemCount())\n                : (adapter == null ? 0 : adapter.getItemCount());\n    }\n""",
    """    private void replaceShowEpisodes(List<NativeContentItem> items) {\n        showEpisodes.clear();\n        appendShowEpisodes(items);\n    }\n\n    private void appendShowEpisodes(List<NativeContentItem> items) {\n        if (items == null || items.isEmpty()) return;\n        LinkedHashSet<String> seen = new LinkedHashSet<>();\n        for (NativeContentItem existing : showEpisodes) {\n            if (existing != null && existing.url != null && !existing.url.isEmpty()) {\n                seen.add(existing.url);\n            }\n        }\n        for (NativeContentItem item : items) {\n            if (item == null || item.isSection() || item.url == null || item.url.isEmpty()) continue;\n            if (seen.add(item.url)) showEpisodes.add(item);\n        }\n    }\n\n    private void applyEpisodeRange(boolean scrollToSelection, int selectedIndex) {\n        if (!showDetailsMode || adapter == null) return;\n        if (showEpisodes.isEmpty()) {\n            episodeRangeStart = 0;\n            adapter.setEpisodeNumberOffset(0);\n            adapter.replace(new ArrayList<>());\n            updateEpisodeRangeUi();\n            return;\n        }\n        int requested = selectedIndex >= 0 ? selectedIndex : episodeRangeStart;\n        episodeRangeStart = ShowsEpisodeRanges.startForIndex(requested, showEpisodes.size());\n        adapter.setEpisodeNumberOffset(episodeRangeStart);\n        adapter.replace(ShowsEpisodeRanges.slice(showEpisodes, episodeRangeStart));\n        updateEpisodeRangeUi();\n        if (scrollToSelection && recycler != null && adapter.getItemCount() > 0) {\n            int local = selectedIndex >= 0 ? selectedIndex - episodeRangeStart : 0;\n            recycler.scrollToPosition(Math.max(0, Math.min(local, adapter.getItemCount() - 1)));\n        }\n    }\n\n    private void updateEpisodeRangeUi() {\n        if (episodeRangeAction == null) return;\n        int total = showEpisodes.size();\n        episodeRangeAction.setVisibility(\n                total > ShowsEpisodeRanges.RANGE_SIZE ? View.VISIBLE : View.GONE\n        );\n        if (total > 0) {\n            episodeRangeAction.setText(ShowsEpisodeRanges.label(episodeRangeStart, total) + \" ▾\");\n            episodeRangeAction.setContentDescription(\n                    \"Choose episode range, current \" + ShowsEpisodeRanges.label(episodeRangeStart, total)\n            );\n        }\n    }\n\n    private void showEpisodeRangePicker() {\n        if (showEpisodes.size() <= ShowsEpisodeRanges.RANGE_SIZE) return;\n        ShowsEpisodeRangeSheet.show(\n                this,\n                new ArrayList<>(showEpisodes),\n                episodeRangeStart,\n                selectedIndex -> {\n                    episodeRangeStart = ShowsEpisodeRanges.startForIndex(\n                            selectedIndex,\n                            showEpisodes.size()\n                    );\n                    applyEpisodeRange(true, selectedIndex);\n                }\n        );\n    }\n\n    private List<NativeContentItem> showEpisodeSnapshot() {\n        return showDetailsMode ? new ArrayList<>(showEpisodes) : adapter.snapshot();\n    }\n\n    private void updateShowDetailsHeader() {\n        if (showDetailsHeader == null || adapter == null) return;\n        List<NativeContentItem> items = showEpisodeSnapshot();\n        showDetailsHeader.setItemCount((int) items.stream().filter(ShowsEpisodePlayback::playable).count());\n        ShowsEpisodePlayback.Selection selection = ShowsEpisodePlayback.select(\n                items,\n                PlaybackHistoryStore.load(this)\n        );\n        showDetailsHeader.setPlaybackAction(selection.positionMs > 0L, selection.item != null);\n    }\n\n    private void playSeries() {\n        if (adapter == null) return;\n        ShowsEpisodePlayback.Selection selection = ShowsEpisodePlayback.select(\n                showEpisodeSnapshot(),\n                PlaybackHistoryStore.load(this)\n        );\n        if (selection.item != null) openVideo(selection.item, selection.positionMs);\n    }\n\n    private int itemCount() {\n        if (showDetailsMode) return showEpisodes.size();\n        return isBunkr()\n                ? (bunkrGalleryAdapter == null ? 0 : bunkrGalleryAdapter.getItemCount())\n                : (adapter == null ? 0 : adapter.getItemCount());\n    }\n""",
)
replace_once(
    "app/src/main/java/com/webapp/crazyshit/NativeFeedBrowserActivity.java",
    """            org.json.JSONObject value = new org.json.JSONObject().put(\"page\", currentPage).put(\"end\", endReached)\n                    .put(\"items\", ContentItemCodec.encodeList(adapter.snapshot(), 2000));\n""",
    """            List<NativeContentItem> persistedItems = showDetailsMode\n                    ? new ArrayList<>(showEpisodes)\n                    : adapter.snapshot();\n            org.json.JSONObject value = new org.json.JSONObject().put(\"page\", currentPage).put(\"end\", endReached)\n                    .put(\"items\", ContentItemCodec.encodeList(persistedItems, 2000));\n""",
)
replace_once(
    "app/src/main/java/com/webapp/crazyshit/NativeFeedBrowserActivity.java",
    """                } else if (feed != null) {\n                    adapter.replace(ContentItemCodec.decodeList(feed.optJSONArray(\"items\"), 2000));\n                    currentPage = feed.optInt(\"page\"); endReached = feed.optBoolean(\"end\");\n                    updateShowDetailsHeader();\n""",
    """                } else if (feed != null) {\n                    List<NativeContentItem> restoredItems =\n                            ContentItemCodec.decodeList(feed.optJSONArray(\"items\"), 2000);\n                    if (showDetailsMode) {\n                        replaceShowEpisodes(restoredItems);\n                        applyEpisodeRange(false, -1);\n                    } else {\n                        adapter.replace(restoredItems);\n                    }\n                    currentPage = feed.optInt(\"page\"); endReached = feed.optBoolean(\"end\");\n                    updateShowDetailsHeader();\n""",
)
replace_once(
    "app/src/main/java/com/webapp/crazyshit/NativeFeedBrowserActivity.java",
    """        state.putString(\"browser_snapshot\", browserSnapshot);\n        state.putString(\"gallery_session\", bunkrGallerySessionId);\n""",
    """        state.putString(\"browser_snapshot\", browserSnapshot);\n        state.putString(\"gallery_session\", bunkrGallerySessionId);\n        state.putInt(\"episode_range_start\", episodeRangeStart);\n""",
)

# ShowsCollectionWarmCache: expose one callback path and include all Shows category sources.
replace_once(
    "app/src/main/java/com/webapp/crazyshit/ShowsCollectionWarmCache.java",
    """import android.content.Context;\n\nimport java.util.ArrayList;\n""",
    """import android.content.Context;\nimport android.os.Handler;\nimport android.os.Looper;\n\nimport java.util.ArrayList;\n""",
)
replace_once(
    "app/src/main/java/com/webapp/crazyshit/ShowsCollectionWarmCache.java",
    """import java.util.concurrent.ConcurrentHashMap;\nimport java.util.concurrent.RejectedExecutionException;\n""",
    """import java.util.concurrent.ConcurrentHashMap;\nimport java.util.concurrent.CopyOnWriteArrayList;\nimport java.util.concurrent.RejectedExecutionException;\n""",
)
replace_once(
    "app/src/main/java/com/webapp/crazyshit/ShowsCollectionWarmCache.java",
    """    private static final int MAX_ENTRIES = 12;\n\n    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(\n""",
    """    private static final int MAX_ENTRIES = 16;\n    private static final Handler MAIN = new Handler(Looper.getMainLooper());\n\n    interface Listener {\n        void onLoaded(String url, List<NativeContentItem> items);\n    }\n\n    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(\n""",
)
replace_once(
    "app/src/main/java/com/webapp/crazyshit/ShowsCollectionWarmCache.java",
    """    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();\n    private static final LinkedHashMap<String, CachedPage> CACHE =\n""",
    """    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();\n    private static final Map<String, CopyOnWriteArrayList<Listener>> WAITERS =\n            new ConcurrentHashMap<>();\n    private static final LinkedHashMap<String, CachedPage> CACHE =\n""",
)
replace_once(
    "app/src/main/java/com/webapp/crazyshit/ShowsCollectionWarmCache.java",
    """    static void request(Context context, NativeContentItem item) {\n        if (context == null || item == null) return;\n        String url = clean(item.url);\n        if (!supported(url) || get(url) != null || !IN_FLIGHT.add(url)) return;\n\n        Context appContext = context.getApplicationContext();\n        try {\n            IO.execute(() -> {\n                try {\n                    List<NativeContentItem> items = fetch(appContext, url);\n                    if (items != null && !items.isEmpty()) put(url, items);\n                } catch (Exception ignored) {\n                } finally {\n                    IN_FLIGHT.remove(url);\n                }\n            });\n        } catch (RejectedExecutionException ignored) {\n            IN_FLIGHT.remove(url);\n        }\n    }\n""",
    """    static void request(Context context, NativeContentItem item) {\n        request(context, item, null);\n    }\n\n    static void request(Context context, NativeContentItem item, Listener listener) {\n        if (context == null || item == null) return;\n        String url = clean(item.url);\n        if (!supported(url)) return;\n\n        List<NativeContentItem> cached = get(url);\n        if (cached != null) {\n            if (listener != null) MAIN.post(() -> listener.onLoaded(url, cached));\n            return;\n        }\n        if (listener != null) {\n            WAITERS.computeIfAbsent(url, ignored -> new CopyOnWriteArrayList<>()).add(listener);\n        }\n        if (!IN_FLIGHT.add(url)) return;\n\n        Context appContext = context.getApplicationContext();\n        try {\n            IO.execute(() -> {\n                List<NativeContentItem> loaded = null;\n                try {\n                    loaded = fetch(appContext, url);\n                    if (loaded != null && !loaded.isEmpty()) put(url, loaded);\n                } catch (Exception ignored) {\n                } finally {\n                    IN_FLIGHT.remove(url);\n                    dispatch(url, loaded);\n                }\n            });\n        } catch (RejectedExecutionException ignored) {\n            IN_FLIGHT.remove(url);\n            dispatch(url, null);\n        }\n    }\n\n    private static void dispatch(String url, List<NativeContentItem> items) {\n        CopyOnWriteArrayList<Listener> listeners = WAITERS.remove(url);\n        if (listeners == null || listeners.isEmpty()) return;\n        List<NativeContentItem> copy = items == null\n                ? new ArrayList<>()\n                : new ArrayList<>(items);\n        MAIN.post(() -> {\n            for (Listener listener : listeners) {\n                if (listener != null) listener.onLoaded(url, new ArrayList<>(copy));\n            }\n        });\n    }\n""",
)
replace_once(
    "app/src/main/java/com/webapp/crazyshit/ShowsCollectionWarmCache.java",
    """        if (EfuktRepository.isEfuktUrl(url)) {\n            return new EfuktRepository().fetchSeriesFeed(context, url, 1);\n        }\n        return new CrazyShitRepository().fetchFeed(context, url, 1);\n""",
    """        if (EfuktRepository.isEfuktUrl(url)) {\n            return new EfuktRepository().fetchSeriesFeed(context, url, 1);\n        }\n        if (BaddieHubRepository.isBaddieHubUrl(url)) {\n            return new BaddieHubRepository().fetchCategory(context, url, 1);\n        }\n        if (XMegaDriveRepository.isCategoryUrl(url)) {\n            return new XMegaDriveRepository().fetchCategory(context, url, 1);\n        }\n        return new CrazyShitRepository().fetchFeed(context, url, 1);\n""",
)
replace_once(
    "app/src/main/java/com/webapp/crazyshit/ShowsCollectionWarmCache.java",
    """        return url.startsWith(CrazyShitRepository.BASE)\n                || EfuktRepository.isEfuktUrl(url)\n                || WebVideoSourceRepository.isKaoticUrl(url);\n""",
    """        return url.startsWith(CrazyShitRepository.BASE)\n                || EfuktRepository.isEfuktUrl(url)\n                || WebVideoSourceRepository.isKaoticUrl(url)\n                || BaddieHubRepository.isBaddieHubUrl(url)\n                || XMegaDriveRepository.isCategoryUrl(url);\n""",
)

# ShowsHubView: overlay a lazy 2x2 mosaic on category cards only.
replace_once(
    "app/src/main/java/com/webapp/crazyshit/ShowsHubView.java",
    """            ImageView image = new ImageView(getContext());\n            image.setScaleType(ImageView.ScaleType.CENTER_CROP);\n            frame.addView(image, new FrameLayout.LayoutParams(\n                    -1, portrait ? -1 : Math.round(width * 9f / 16f)));\n\n            View shade = new View(getContext());\n""",
    """            ImageView image = new ImageView(getContext());\n            image.setScaleType(ImageView.ScaleType.CENTER_CROP);\n            int artworkHeight = portrait ? -1 : Math.round(width * 9f / 16f);\n            frame.addView(image, new FrameLayout.LayoutParams(-1, artworkHeight));\n\n            ShowsCategoryMosaicView mosaic = new ShowsCategoryMosaicView(getContext());\n            frame.addView(mosaic, new FrameLayout.LayoutParams(-1, artworkHeight));\n\n            View shade = new View(getContext());\n""",
)
replace_once(
    "app/src/main/java/com/webapp/crazyshit/ShowsHubView.java",
    "            return new RailHolder(card, image, source, title);\n",
    "            return new RailHolder(card, image, mosaic, source, title);\n",
)
replace_once(
    "app/src/main/java/com/webapp/crazyshit/ShowsHubView.java",
    """            holder.card.setContentDescription(\"Open \" + item.title);\n            holder.card.setOnClickListener(v -> {\n                if (clickListener != null) clickListener.onOpen(item, holder.image);\n            });\n            loadArtwork(holder.image, item, false);\n""",
    """            holder.card.setContentDescription(\"Open \" + item.title);\n            if (item.isCategory()) holder.mosaic.bind(item);\n            else holder.mosaic.clear();\n            holder.card.setOnClickListener(v -> {\n                if (clickListener != null) {\n                    View sourceView = holder.mosaic.isShowing() ? holder.mosaic : holder.image;\n                    clickListener.onOpen(item, sourceView);\n                }\n            });\n            loadArtwork(holder.image, item, false);\n""",
)
replace_once(
    "app/src/main/java/com/webapp/crazyshit/ShowsHubView.java",
    """        public void onViewRecycled(@NonNull RailHolder holder) {\n            Glide.with(holder.image).clear(holder.image);\n            holder.card.setOnClickListener(null);\n            super.onViewRecycled(holder);\n        }\n""",
    """        public void onViewRecycled(@NonNull RailHolder holder) {\n            Glide.with(holder.image).clear(holder.image);\n            holder.mosaic.clear();\n            holder.card.setOnClickListener(null);\n            super.onViewRecycled(holder);\n        }\n""",
)
replace_once(
    "app/src/main/java/com/webapp/crazyshit/ShowsHubView.java",
    """    private static final class RailHolder extends RecyclerView.ViewHolder {\n        final MaterialCardView card;\n        final ImageView image;\n        final TextView source;\n        final TextView title;\n\n        RailHolder(MaterialCardView card, ImageView image, TextView source, TextView title) {\n            super(card);\n            this.card = card;\n            this.image = image;\n            this.source = source;\n            this.title = title;\n        }\n    }\n""",
    """    private static final class RailHolder extends RecyclerView.ViewHolder {\n        final MaterialCardView card;\n        final ImageView image;\n        final ShowsCategoryMosaicView mosaic;\n        final TextView source;\n        final TextView title;\n\n        RailHolder(\n                MaterialCardView card,\n                ImageView image,\n                ShowsCategoryMosaicView mosaic,\n                TextView source,\n                TextView title\n        ) {\n            super(card);\n            this.card = card;\n            this.image = image;\n            this.mosaic = mosaic;\n            this.source = source;\n            this.title = title;\n        }\n    }\n""",
)

# New pure range helper.
write(
    "app/src/main/java/com/webapp/crazyshit/ShowsEpisodeRanges.java",
    r'''package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.List;

/** Ordered, source-agnostic grouping for large Shows collections. */
final class ShowsEpisodeRanges {
    static final int RANGE_SIZE = 25;

    private ShowsEpisodeRanges() {
    }

    static int startForIndex(int index, int total) {
        if (total <= 0) return 0;
        int safe = Math.max(0, Math.min(index, total - 1));
        return (safe / RANGE_SIZE) * RANGE_SIZE;
    }

    static String label(int start, int total) {
        if (total <= 0) return "1–0";
        int safeStart = startForIndex(start, total);
        int end = Math.min(total, safeStart + RANGE_SIZE);
        return (safeStart + 1) + "–" + end;
    }

    static List<NativeContentItem> slice(List<NativeContentItem> items, int requestedStart) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        if (items == null || items.isEmpty()) return result;
        int start = startForIndex(requestedStart, items.size());
        int end = Math.min(items.size(), start + RANGE_SIZE);
        result.addAll(items.subList(start, end));
        return result;
    }

    static List<Integer> starts(int total) {
        ArrayList<Integer> starts = new ArrayList<>();
        for (int start = 0; start < total; start += RANGE_SIZE) starts.add(start);
        return starts;
    }
}
''',
)

# New ZEROCHILL bottom sheet for range jumping and title search.
write(
    "app/src/main/java/com/webapp/crazyshit/ShowsEpisodeRangeSheet.java",
    r'''package com.webapp.crazyshit;

import android.app.Activity;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;
import java.util.Locale;

/** Compact range picker for source lists that do not expose season metadata. */
final class ShowsEpisodeRangeSheet {
    interface Listener {
        void onSelected(int index);
    }

    private ShowsEpisodeRangeSheet() {
    }

    static void show(
            Activity activity,
            List<NativeContentItem> episodes,
            int selectedStart,
            Listener listener
    ) {
        if (activity == null || activity.isFinishing() || episodes == null || episodes.isEmpty()) return;

        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.setDismissWithAnimation(true);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 18), dp(activity, 14), dp(activity, 18), dp(activity, 20));
        root.setBackground(ZeroChillUi.rounded(
                activity,
                Color.rgb(8, 10, 12),
                ZeroChillUi.color(activity, R.color.zc_edge),
                R.dimen.zc_radius_large
        ));

        TextView title = text(activity, "Jump to episodes", 21f, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView count = text(
                activity,
                episodes.size() + " episodes  •  groups of " + ShowsEpisodeRanges.RANGE_SIZE,
                12f,
                ZeroChillUi.color(activity, R.color.zc_text_secondary)
        );
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-1, -2);
        countParams.topMargin = dp(activity, 3);
        root.addView(count, countParams);

        EditText search = new EditText(activity);
        search.setSingleLine(true);
        search.setTextSize(15f);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(ZeroChillUi.color(activity, R.color.zc_text_muted));
        search.setHint("Search episode titles");
        search.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        search.setBackground(ZeroChillUi.rounded(
                activity,
                ZeroChillUi.color(activity, R.color.zc_surface_glass_strong),
                ZeroChillUi.color(activity, R.color.zc_edge),
                R.dimen.zc_radius_medium
        ));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(activity, 48));
        searchParams.topMargin = dp(activity, 14);
        root.addView(search, searchParams);

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, dp(activity, 8), 0, dp(activity, 4));
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, dp(activity, 430));
        scrollParams.topMargin = dp(activity, 4);
        root.addView(scroll, scrollParams);

        Runnable renderRanges = () -> renderRanges(
                activity,
                rows,
                episodes.size(),
                selectedStart,
                listener,
                dialog
        );
        renderRanges.run();

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s == null ? "" : s.toString().trim();
                if (query.isEmpty()) renderRanges.run();
                else renderMatches(activity, rows, episodes, query, listener, dialog);
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        dialog.setContentView(root);
        dialog.show();
    }

    private static void renderRanges(
            Activity activity,
            LinearLayout rows,
            int total,
            int selectedStart,
            Listener listener,
            BottomSheetDialog dialog
    ) {
        rows.removeAllViews();
        int current = ShowsEpisodeRanges.startForIndex(selectedStart, total);
        for (int start : ShowsEpisodeRanges.starts(total)) {
            String label = "Episodes " + ShowsEpisodeRanges.label(start, total);
            addRow(activity, rows, label, start == current, () -> {
                if (listener != null) listener.onSelected(start);
                dialog.dismiss();
            });
        }
    }

    private static void renderMatches(
            Activity activity,
            LinearLayout rows,
            List<NativeContentItem> episodes,
            String query,
            Listener listener,
            BottomSheetDialog dialog
    ) {
        rows.removeAllViews();
        String needle = query.toLowerCase(Locale.US);
        int shown = 0;
        for (int index = 0; index < episodes.size() && shown < 40; index++) {
            NativeContentItem item = episodes.get(index);
            String title = item == null || item.title == null ? "" : item.title.trim();
            if (!title.toLowerCase(Locale.US).contains(needle)) continue;
            final int selected = index;
            addRow(activity, rows, "#" + (index + 1) + "  " + title, false, () -> {
                if (listener != null) listener.onSelected(selected);
                dialog.dismiss();
            });
            shown++;
        }
        if (shown == 0) {
            TextView empty = text(
                    activity,
                    "No matching episode titles",
                    14f,
                    ZeroChillUi.color(activity, R.color.zc_text_secondary)
            );
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(activity, 28), 0, dp(activity, 28));
            rows.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private static void addRow(
            Activity activity,
            LinearLayout rows,
            String label,
            boolean selected,
            Runnable action
    ) {
        TextView row = text(
                activity,
                label,
                15f,
                selected ? UiPalette.PRIMARY : Color.WHITE
        );
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setTypeface(null, selected
                ? android.graphics.Typeface.BOLD
                : android.graphics.Typeface.NORMAL);
        row.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
        row.setBackground(ZeroChillUi.rounded(
                activity,
                selected
                        ? ZeroChillUi.color(activity, R.color.zc_cyan_container)
                        : ZeroChillUi.color(activity, R.color.zc_surface_glass_strong),
                selected ? UiPalette.PRIMARY : ZeroChillUi.color(activity, R.color.zc_edge),
                R.dimen.zc_radius_medium
        ));
        row.setOnClickListener(v -> action.run());
        ZeroChillMotion.installPressFeedback(row);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(activity, 50));
        params.topMargin = dp(activity, 6);
        rows.addView(row, params);
    }

    private static TextView text(Activity activity, String value, float size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
''',
)

# Deterministic-per-session random artwork selection.
write(
    "app/src/main/java/com/webapp/crazyshit/ShowsCategoryMosaicSelector.java",
    r'''package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

final class ShowsCategoryMosaicSelector {
    static final long SESSION_SEED = System.nanoTime() ^ System.currentTimeMillis();

    private ShowsCategoryMosaicSelector() {
    }

    static List<NativeContentItem> select(
            List<NativeContentItem> items,
            String categoryUrl,
            long seed,
            int limit
    ) {
        LinkedHashMap<String, NativeContentItem> unique = new LinkedHashMap<>();
        if (items != null) {
            for (NativeContentItem item : items) {
                if (item == null || !ShowsEpisodePlayback.playable(item)) continue;
                String image = item.imageUrl == null ? "" : item.imageUrl.trim();
                if (image.isEmpty()) continue;
                unique.putIfAbsent(image, item);
            }
        }
        ArrayList<NativeContentItem> pool = new ArrayList<>(unique.values());
        long categorySeed = seed ^ (categoryUrl == null ? 0L : categoryUrl.hashCode() * 0x9E3779B97F4A7C15L);
        Collections.shuffle(pool, new Random(categorySeed));
        int take = Math.min(Math.max(0, limit), pool.size());
        return new ArrayList<>(pool.subList(0, take));
    }
}
''',
)

# 2x2 artwork surface used by every category card, with the normal card image as fallback.
write(
    "app/src/main/java/com/webapp/crazyshit/ShowsCategoryMosaicView.java",
    r'''package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;

import java.util.List;

/** Lazy 2x2 episode-art collage for a Shows category card. */
final class ShowsCategoryMosaicView extends FrameLayout {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private final ImageView[] tiles = new ImageView[4];
    private int generation;
    private String categoryUrl = "";

    ShowsCategoryMosaicView(Context context) {
        super(context);
        setVisibility(View.GONE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setBackgroundColor(Color.rgb(12, 14, 17));

        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(2);
        grid.setRowCount(2);
        addView(grid, new FrameLayout.LayoutParams(-1, -1));

        int gap = Math.max(1, Math.round(context.getResources().getDisplayMetrics().density));
        for (int index = 0; index < tiles.length; index++) {
            ImageView tile = new ImageView(context);
            tile.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.setBackground(new ColorDrawable(Color.rgb(18, 20, 23)));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(index / 2, 1f),
                    GridLayout.spec(index % 2, 1f)
            );
            params.width = 0;
            params.height = 0;
            params.setMargins(
                    index % 2 == 0 ? 0 : gap,
                    index / 2 == 0 ? 0 : gap,
                    0,
                    0
            );
            grid.addView(tile, params);
            tiles[index] = tile;
        }
    }

    void bind(NativeContentItem category) {
        int request = ++generation;
        categoryUrl = category == null || category.url == null ? "" : category.url.trim();
        setVisibility(View.GONE);
        if (category == null || categoryUrl.isEmpty()) return;

        ShowsCollectionWarmCache.request(getContext(), category, (url, items) -> {
            if (request != generation || !categoryUrl.equals(url)) return;
            List<NativeContentItem> selected = ShowsCategoryMosaicSelector.select(
                    items,
                    categoryUrl,
                    ShowsCategoryMosaicSelector.SESSION_SEED,
                    tiles.length
            );
            if (selected.size() < 2) return;
            for (int index = 0; index < tiles.length; index++) {
                load(tiles[index], selected.get(index % selected.size()));
            }
            setVisibility(View.VISIBLE);
        });
    }

    boolean isShowing() {
        return getVisibility() == View.VISIBLE;
    }

    void clear() {
        generation++;
        categoryUrl = "";
        setVisibility(View.GONE);
        for (ImageView tile : tiles) {
            if (tile != null) {
                Glide.with(tile).clear(tile);
                tile.setImageDrawable(new ColorDrawable(Color.rgb(18, 20, 23)));
            }
        }
    }

    private void load(ImageView view, NativeContentItem item) {
        String imageUrl = item == null || item.imageUrl == null ? "" : item.imageUrl.trim();
        if (imageUrl.isEmpty()) return;
        String pageUrl = item.url == null ? "" : item.url.trim();
        String agent = USER_AGENT;
        if (XMegaDriveRepository.isXMegaDriveUrl(imageUrl)
                || XMegaDriveRepository.isXMegaDriveUrl(pageUrl)) {
            String session = XMegaDriveBrowserFetch.sessionUserAgent();
            if (session != null && !session.trim().isEmpty()) agent = session.trim();
        }

        LazyHeaders.Builder headers = new LazyHeaders.Builder().addHeader("User-Agent", agent);
        if (!pageUrl.isEmpty()) headers.addHeader("Referer", pageUrl);
        headers.addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        try {
            String cookies = CookieManager.getInstance().getCookie(imageUrl);
            if ((cookies == null || cookies.trim().isEmpty()) && !pageUrl.isEmpty()) {
                cookies = CookieManager.getInstance().getCookie(pageUrl);
            }
            if (cookies != null && !cookies.trim().isEmpty()) headers.addHeader("Cookie", cookies);
        } catch (Exception ignored) {
        }

        Glide.with(view)
                .load(new GlideUrl(imageUrl, headers.build()))
                .disallowHardwareConfig()
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .centerCrop()
                .dontAnimate()
                .transition(ThumbnailFades.thumbnail())
                .placeholder(new ColorDrawable(Color.rgb(18, 20, 23)))
                .error(new ColorDrawable(Color.rgb(18, 20, 23)))
                .into(view);
    }

    @Override
    protected void onDetachedFromWindow() {
        clear();
        super.onDetachedFromWindow();
    }
}
''',
)

# Focused unit coverage.
write(
    "app/src/test/java/com/webapp/crazyshit/ShowsEpisodeRangesTest.java",
    r'''package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ShowsEpisodeRangesTest {
    @Test
    public void largeSourceListUsesStableTwentyFiveItemRanges() {
        List<NativeContentItem> items = new ArrayList<>();
        for (int i = 0; i < 61; i++) {
            items.add(new NativeContentItem(
                    NativeContentItem.KIND_MEDIA,
                    "Source title " + i,
                    "https://example.com/" + i,
                    "https://example.com/" + i + ".jpg",
                    "",
                    "",
                    ""
            ));
        }

        assertEquals(0, ShowsEpisodeRanges.startForIndex(24, items.size()));
        assertEquals(25, ShowsEpisodeRanges.startForIndex(25, items.size()));
        assertEquals(50, ShowsEpisodeRanges.startForIndex(60, items.size()));
        assertEquals("26–50", ShowsEpisodeRanges.label(25, items.size()));
        assertEquals("51–61", ShowsEpisodeRanges.label(50, items.size()));
        assertEquals(25, ShowsEpisodeRanges.slice(items, 25).size());
        assertEquals(11, ShowsEpisodeRanges.slice(items, 50).size());
        assertEquals(3, ShowsEpisodeRanges.starts(items.size()).size());
    }
}
''',
)
write(
    "app/src/test/java/com/webapp/crazyshit/ShowsCategoryMosaicSelectorTest.java",
    r'''package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ShowsCategoryMosaicSelectorTest {
    @Test
    public void categoryMosaicIsRandomizedButStableForOneSessionSeed() {
        List<NativeContentItem> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            items.add(new NativeContentItem(
                    NativeContentItem.KIND_MEDIA,
                    "Item " + i,
                    "https://example.com/video/" + i,
                    "https://example.com/thumb/" + i + ".jpg",
                    "",
                    "",
                    ""
            ));
        }
        items.add(new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                "Missing art",
                "https://example.com/missing",
                "",
                "",
                "",
                ""
        ));

        List<NativeContentItem> first = ShowsCategoryMosaicSelector.select(
                items, "https://example.com/category/a", 12345L, 4);
        List<NativeContentItem> second = ShowsCategoryMosaicSelector.select(
                items, "https://example.com/category/a", 12345L, 4);

        assertEquals(4, first.size());
        assertEquals(first.get(0).imageUrl, second.get(0).imageUrl);
        assertEquals(first.get(1).imageUrl, second.get(1).imageUrl);
        for (NativeContentItem item : first) assertFalse(item.imageUrl.isEmpty());
    }
}
''',
)

print("Shows episode ranges and category mosaic patch applied")
