package com.webapp.crazyshit;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Mixed native search results for videos, Series, Categories and the local Library. */
final class GlobalSearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    interface Listener {
        void onOpen(NativeContentItem item);
    }

    static final int SOURCE_REMOTE = 0;
    static final int SOURCE_LIBRARY = 1;
    static final int SOURCE_ALBUM = 2;
    static final int ALBUM_ROW_HEIGHT_DP = 136;

    private static final int TYPE_SECTION = 0;
    private static final int TYPE_RESULT = 1;
    private static final int TYPE_CARD = 2;
    private static final String SITE = "https://crazyshit.com/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    static final class Entry {
        final boolean section;
        final String sectionTitle;
        final NativeContentItem item;
        final int source;
        final String provider;

        private Entry(boolean section, String sectionTitle, NativeContentItem item, int source, String provider) {
            this.section = section;
            this.sectionTitle = sectionTitle == null ? "" : sectionTitle;
            this.item = item;
            this.source = source;
            this.provider = provider == null ? "" : provider;
        }

        static Entry section(String title) {
            return new Entry(true, title, null, SOURCE_REMOTE, "");
        }

        static Entry item(NativeContentItem item, int source) {
            return item(item, source, "");
        }

        static Entry item(NativeContentItem item, int source, String provider) {
            return new Entry(false, "", item, source, provider);
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Listener listener;
    private final SearchResultFades fades = new SearchResultFades();
    private SearchCardStyle style = SearchCardStyle.LIST;
    private final List<String> sectionOrder = new ArrayList<>();
    private final Map<String, List<String>> itemOrder = new java.util.LinkedHashMap<>();
    void resetOrder() { sectionOrder.clear(); itemOrder.clear(); }
    void resetEntrance() { fades.reset(); resetOrder(); }

    void setStyle(SearchCardStyle next) {
        SearchCardStyle value = next == null ? SearchCardStyle.LIST : next;
        if (style == value) return;
        style = value;
        notifyDataSetChanged();
    }

    boolean isFullSpan(int position) {
        return position >= 0 && position < entries.size() && entries.get(position).section;
    }

    void saveOrder(android.os.Bundle state) {
        state.putStringArrayList("search.sections", new ArrayList<>(sectionOrder));
        android.os.Bundle groups = new android.os.Bundle();
        for (Map.Entry<String, List<String>> entry : itemOrder.entrySet())
            groups.putStringArrayList(entry.getKey(), new ArrayList<>(entry.getValue()));
        state.putBundle("search.items", groups);
    }

    void restoreOrder(android.os.Bundle state) {
        resetOrder();
        ArrayList<String> sections = state.getStringArrayList("search.sections");
        if (sections != null) sectionOrder.addAll(sections);
        android.os.Bundle groups = state.getBundle("search.items");
        if (groups != null) for (String key : groups.keySet()) {
            ArrayList<String> items = groups.getStringArrayList(key);
            if (items != null) itemOrder.put(key, new ArrayList<>(items));
        }
    }

    private List<Entry> stableOrder(List<Entry> next) {
        Map<String, Entry> headings = new java.util.LinkedHashMap<>();
        Map<String, Map<String, Entry>> groups = new java.util.LinkedHashMap<>();
        String section = "";
        for (Entry entry : next) {
            if (entry.section) {
                section = sectionKey(entry);
                headings.put(section, entry);
                groups.putIfAbsent(section, new java.util.LinkedHashMap<>());
                if (!sectionOrder.contains(section)) sectionOrder.add(section);
            } else {
                groups.computeIfAbsent(section, k -> new java.util.LinkedHashMap<>()).put(itemKey(entry), entry);
                if (!sectionOrder.contains(section)) sectionOrder.add(section);
            }
        }
        ArrayList<Entry> ordered = new ArrayList<>();
        for (String key : sectionOrder) {
            Map<String, Entry> group = groups.get(key);
            if (group == null) continue;
            if (headings.containsKey(key)) ordered.add(headings.get(key));
            List<String> identities = itemOrder.computeIfAbsent(key, k -> new ArrayList<>());
            for (String identity : group.keySet()) if (!identities.contains(identity)) identities.add(identity);
            for (String identity : identities) if (group.containsKey(identity)) ordered.add(group.get(identity));
        }
        return ordered;
    }

    private static String itemKey(Entry entry) {
        return entry.item.kind + ":" + entry.source + ":" + (entry.item.isCreator()
                ? OnlyFapCreatorResults.key(entry.item) : entry.item.url);
    }
    private final Map<String, String> resolvedThumbnails = new HashMap<>();
    private final Map<String, Float> naturalAspects = new HashMap<>();
    private final Set<String> requestedThumbnails = new HashSet<>();
    private final Set<String> failedDirectThumbnails = new HashSet<>();
    private final Map<String, RenderedThumbnailResolver> thumbnailJobs = new HashMap<>();
    private RenderedThumbnailResolver[] thumbnailResolvers;
    private Context appContext;
    private int resolverCursor;

    GlobalSearchAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    void replace(List<Entry> next) {
        replace(next, false);
    }

    void replace(List<Entry> next, boolean keepRank) {
        List<Entry> old = new ArrayList<>(entries);
        List<Entry> incoming = next == null ? new ArrayList<>() : next;
        List<Entry> ordered = keepRank ? incoming : stableOrder(incoming);
        androidx.recyclerview.widget.DiffUtil.DiffResult diff = androidx.recyclerview.widget.DiffUtil.calculateDiff(
                new androidx.recyclerview.widget.DiffUtil.Callback() {
                    public int getOldListSize() { return old.size(); }
                    public int getNewListSize() { return ordered.size(); }
                    public boolean areItemsTheSame(int a, int b) {
                        Entry x = old.get(a), y = ordered.get(b);
                        if (x.section != y.section) return false;
                        if (x.section) return sectionKey(x).equals(sectionKey(y));
                        return itemKey(x).equals(itemKey(y));
                    }
                    public boolean areContentsTheSame(int a, int b) {
                        Entry x = old.get(a), y = ordered.get(b);
                        if (x.section) return x.sectionTitle.equals(y.sectionTitle);
                        return x.provider.equals(y.provider) && x.item.url.equals(y.item.url) && x.item.searchQuery.equals(y.item.searchQuery)
                                && x.item.title.equals(y.item.title) && x.item.imageUrl.equals(y.item.imageUrl)
                                && x.item.views.equals(y.item.views) && x.item.uploader.equals(y.item.uploader)
                                && x.item.comments.equals(y.item.comments) && x.item.description.equals(y.item.description);
                    }
                });
        entries.clear();
        entries.addAll(ordered);
        diff.dispatchUpdatesTo(this);
        preloadDirectThumbnails();
    }

    private static String sectionKey(Entry entry) {
        return entry.sectionTitle.replaceFirst("\\s+•\\s+\\d+$", "");
    }

    void close() {
        if (thumbnailResolvers != null) {
            for (RenderedThumbnailResolver resolver : thumbnailResolvers) {
                if (resolver != null) resolver.close();
            }
        }
        thumbnailResolvers = null;
        appContext = null;
        requestedThumbnails.clear();
        failedDirectThumbnails.clear();
        thumbnailJobs.clear();
    }

    @Override
    public long getItemId(int position) {
        Entry entry = entries.get(position);
        if (entry.section) return ("section:" + sectionKey(entry)).hashCode();
        if (entry.item == null) return position;
        return itemKey(entry).hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        if (entries.get(position).section) return TYPE_SECTION;
        return style == SearchCardStyle.LIST ? TYPE_RESULT : TYPE_CARD;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        boolean landscape = parent.getResources().getConfiguration().orientation ==
                Configuration.ORIENTATION_LANDSCAPE;
        if (viewType == TYPE_SECTION) {
            TextView title = new TextView(parent.getContext());
            title.setTextColor(UiPalette.TEXT_SECONDARY);
            title.setTextSize(13f);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER_VERTICAL);
            title.setPadding(dp(parent, 12), dp(parent, 5), dp(parent, 12), dp(parent, 3));
            title.setLayoutParams(new RecyclerView.LayoutParams(-1, dp(parent, 28)));
            SearchTextContrast.apply(title);
            return new SectionHolder(title);
        }
        if (viewType == TYPE_CARD) return createCard(parent);

        MaterialCardView card = new MaterialCardView(parent.getContext());
        card.setCardBackgroundColor(Color.TRANSPARENT);
        card.setTag(R.id.zerochill_surface_styled, true);
        card.setCardElevation(0f);
        card.setForeground(new SearchResultDivider(parent.getContext()));
        card.setRadius(0);
        card.setStrokeWidth(0);
        card.setStrokeColor(Color.rgb(51, 51, 58));
        RecyclerView.LayoutParams cardParams = new RecyclerView.LayoutParams(-1, -2);
        cardParams.setMargins(0, 0, 0, 0);
        card.setLayoutParams(cardParams);
        card.setMinimumHeight(dp(parent, 104));

        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setMinimumHeight(BrowseUi.dp(row.getContext(), 104));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(parent, 12), dp(parent, 8), dp(parent, 8), dp(parent, 8));
        card.addView(row, new MaterialCardView.LayoutParams(-1, -2));

        ImageView image = new ImageView(parent.getContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(31, 31, 36));
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(parent, 116), dp(parent, 76));
        row.addView(image, imageParams);

        LinearLayout text = new LinearLayout(parent.getContext());
        text.setOrientation(LinearLayout.VERTICAL);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setPadding(dp(parent, 12), 0, dp(parent, 8), 0);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView title = new TextView(parent.getContext());
        title.setTextColor(Color.WHITE);
        title.setTextSize(17f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView meta = new TextView(parent.getContext());
        meta.setTextColor(Color.rgb(174, 174, 184));
        meta.setTextSize(13.5f);
        meta.setMaxLines(1);
        meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = dp(parent, 5);
        text.addView(meta, metaParams);

        TextView provider = new TextView(parent.getContext());
        provider.setTextSize(10.5f);
        provider.setTypeface(null, android.graphics.Typeface.BOLD);
        provider.setMaxLines(1);
        provider.setEllipsize(android.text.TextUtils.TruncateAt.END);
        provider.setTextColor(AppearanceTheme.accent(parent.getContext()));
        provider.setPadding(dp(parent, 6), dp(parent, 2), dp(parent, 6), dp(parent, 2));
        provider.setBackground(BrowseUi.rounded(parent.getContext(), UiPalette.RAISED, 6));
        LinearLayout.LayoutParams providerParams = new LinearLayout.LayoutParams(-2, -2);
        providerParams.topMargin = dp(parent, 4);
        text.addView(provider, providerParams);
        provider.setVisibility(View.GONE);

        TextView favorite = SearchResultFavorites.create(parent.getContext());
        row.addView(favorite, new LinearLayout.LayoutParams(dp(parent, 48), dp(parent, 48)));
        favorite.setVisibility(View.GONE);
        SearchTextContrast.apply(card);
        return new ResultHolder(card, image, title, meta, favorite, provider);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        bind(holder, position);
    }

    @Override
    public void onBindViewHolder(
            @NonNull RecyclerView.ViewHolder holder,
            int position,
            @NonNull List<Object> payloads
    ) {
        Entry entry = entries.get(position);
        if (entry.section || !(holder instanceof ResultHolder) || entry.item == null) {
            bind(holder, position);
            return;
        }
        if (!payloads.isEmpty() && payloads.contains("thumbnail")) {
            if (holder instanceof ResultHolder) {
                ResultHolder result = (ResultHolder) holder;
                result.boundUrl = entry.item.url == null ? "" : entry.item.url;
                loadImage(result.image, entry.item);
                return;
            }
            if (holder instanceof CardHolder) {
                CardHolder card = (CardHolder) holder;
                card.boundUrl = entry.item.url == null ? "" : entry.item.url;
                loadImage(card.image, entry.item);
                return;
            }
        }
        bind(holder, position);
    }

    private RecyclerView.ViewHolder createCard(ViewGroup parent) {
        Context context = parent.getContext();
        MaterialCardView card = new MaterialCardView(context);
        card.setCardBackgroundColor(Color.TRANSPARENT);
        card.setTag(R.id.zerochill_surface_styled, true);
        card.setCardElevation(0f);
        card.setRadius(dp(parent, 16));
        card.setStrokeWidth(0);
        card.setClickable(true);
        card.setFocusable(true);
        RecyclerView.LayoutParams cardParams = new RecyclerView.LayoutParams(-1, -2);
        card.setLayoutParams(cardParams);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        card.addView(column, new MaterialCardView.LayoutParams(-1, -2));

        FrameLayout art = new FrameLayout(context);
        RatioImage image = new RatioImage(context);
        image.setRatio(style.widthOverHeight());
        image.setScaleType(style.thumbnailScale());
        image.setBackgroundColor(Color.rgb(20, 20, 23));
        art.addView(image, new FrameLayout.LayoutParams(-1, -2));
        TextView duration = new TextView(context);
        duration.setTextColor(Color.WHITE);
        duration.setTextSize(11f);
        duration.setTypeface(null, android.graphics.Typeface.BOLD);
        duration.setPadding(dp(parent, 6), dp(parent, 2), dp(parent, 6), dp(parent, 2));
        duration.setBackground(BrowseUi.rounded(context, 0xCC000000, 8));
        duration.setVisibility(View.GONE);
        FrameLayout.LayoutParams durationParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        durationParams.setMargins(dp(parent, 8), dp(parent, 8), dp(parent, 8), dp(parent, 8));
        art.addView(duration, durationParams);
        column.addView(art, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.HORIZONTAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(dp(parent, 8), dp(parent, 8), dp(parent, 4), dp(parent, 10));
        TextView title = new TextView(context);
        title.setTextColor(Color.WHITE);
        title.setTextSize(14f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView meta = new TextView(context);
        meta.setTextColor(Color.rgb(174, 174, 184));
        meta.setTextSize(12f);
        meta.setMaxLines(2);
        meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = dp(parent, 2);
        words.addView(meta, metaParams);
        copy.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView favorite = SearchResultFavorites.create(context);
        favorite.setVisibility(View.GONE);
        copy.addView(favorite, new LinearLayout.LayoutParams(dp(parent, 36), dp(parent, 36)));
        column.addView(copy, new LinearLayout.LayoutParams(-1, -2));
        SearchTextContrast.apply(card);
        return new CardHolder(card, image, title, meta, favorite, duration);
    }

    private void bindCard(CardHolder holder, Entry entry) {
        NativeContentItem item = entry.item;
        if (item == null) return;
        ensureContext(holder.image.getContext());
        holder.boundUrl = item.url == null ? "" : item.url;
        holder.image.setTag(R.id.shows_watch_source_page, holder.boundUrl);
        holder.entranceKey = itemKey(entry);
        holder.image.setRatio(style.naturalAspect()
                ? naturalRatio(item)
                : style.widthOverHeight());
        holder.image.setScaleType(style.thumbnailScale());
        if (style.naturalAspect()) {
            holder.card.setRadius(0f);
            SearchCardStyle.clipThumbnail(holder.image, 16);
        } else {
            holder.card.setRadius(dp(holder.card, 16));
            holder.image.setClipToOutline(false);
            holder.image.setBackgroundColor(Color.rgb(20, 20, 23));
        }
        holder.title.setText(item.title);
        String duration = GalleryDuration.label(item);
        holder.duration.setText(duration);
        holder.duration.setVisibility(duration.isEmpty() ? View.GONE : View.VISIBLE);
        String meta = metaText(item, entry.source);
        if (!duration.isEmpty() && meta.startsWith(duration)) meta = meta.substring(duration.length()).trim();
        holder.meta.setText(meta);
        holder.card.setContentDescription(item.title);
        holder.card.setOnClickListener(v -> listener.onOpen(item));
        SearchResultFavorites.bind(holder.favorite, holder.card, item);
        fades.bind(holder.card, itemKey(entry));
        loadImage(holder.image, item);
        requestRenderedThumbnail(item);
    }

    private void bind(RecyclerView.ViewHolder holder, int position) {
        Entry entry = entries.get(position);
        if (holder instanceof SectionHolder) {
            ((SectionHolder) holder).title.setText(entry.sectionTitle);
            markFullSpan(holder.itemView, true);
            return;
        }
        markFullSpan(holder.itemView, false);
        if (holder instanceof CardHolder) {
            bindCard((CardHolder) holder, entry);
            return;
        }

        ResultHolder result = (ResultHolder) holder;
        NativeContentItem item = entry.item;
        if (item == null) return;
        ensureContext(result.image.getContext());
        result.boundUrl = item.url == null ? "" : item.url;
        result.entranceKey = itemKey(entry);
        fades.bind(result.card, result.entranceKey);
        boolean album = entry.source == SOURCE_ALBUM && item.isSeries();
        LinearLayout.LayoutParams imageSize = (LinearLayout.LayoutParams) result.image.getLayoutParams();
        imageSize.width = dp(result.image, album ? 104 : item.isCreator() ? 72 : 116);
        imageSize.height = dp(result.image, album ? 120 : item.isCreator() ? 72 : 76);
        result.image.setLayoutParams(imageSize);
        result.image.setScaleType(album ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
        int rowHeight = dp(result.card, album ? ALBUM_ROW_HEIGHT_DP : 104);
        result.card.setMinimumHeight(rowHeight);
        ((View) result.image.getParent()).setMinimumHeight(rowHeight);
        result.meta.setMaxLines(album ? 3 : 2);
        result.image.setBackground(BrowseUi.rounded(result.image.getContext(), Color.rgb(31, 31, 36), item.isCreator() ? 36 : 10));
        result.image.setClipToOutline(true);
        result.title.setText(item.title);
        String meta = metaText(item, entry.source);
        String duration = GalleryDuration.label(item);
        if (!album && !duration.isEmpty() && !meta.contains(duration)) meta = duration + "  •  " + meta;
        result.meta.setText(meta);
        result.provider.setText(entry.provider);
        result.provider.setTextColor(AppearanceTheme.accent(result.card.getContext()));
        result.provider.setVisibility(entry.provider.isEmpty() ? View.GONE : View.VISIBLE);
        result.card.setContentDescription(item.title);
        result.card.setOnClickListener(v -> listener.onOpen(item));
        SearchResultFavorites.bind(result.favorite, result.card, item);
        if (item.isCreator()) {
            CreatorGalleryPreloader.warm(
                    result.card.getContext(),
                    item,
                    position < 6
                            ? CreatorGalleryPreloader.PRIORITY_HIGH
                            : CreatorGalleryPreloader.PRIORITY_NORMAL
            );
        }
        loadImage(result.image, item);
        requestRenderedThumbnail(item);
    }

    private float naturalRatio(NativeContentItem item) {
        if (item == null || item.url == null) return 1f;
        Float cached = naturalAspects.get(item.url);
        if (cached != null && cached > 0f) return cached;
        return BunkrGalleryAdapter.initialTileAspect(
                item.url, item.aspectRatioHint, 0f, item.imageUrl);
    }

    private static void markFullSpan(View itemView, boolean full) {
        ViewGroup.LayoutParams params = itemView.getLayoutParams();
        if (params instanceof StaggeredGridLayoutManager.LayoutParams) {
            ((StaggeredGridLayoutManager.LayoutParams) params).setFullSpan(full);
        }
    }

    private String metaText(NativeContentItem item, int source) {
        StringBuilder meta;
        if (source == SOURCE_LIBRARY) {
            meta = new StringBuilder("Library");
        } else if (item.isCreator()) {
            meta = new StringBuilder("Creator");
        } else if (item.isSeries()) {
            meta = new StringBuilder(BunkrRepository.isAlbumUrl(item.url) ? "OnlyFap album"
                    : EromeRepository.isAlbumUrl(item.url) ? "Erome album" : "Series");
        } else if (item.isCategory()) {
            meta = new StringBuilder("Category");
        } else {
            meta = new StringBuilder("Video");
        }
        if (item.views != null && !item.views.trim().isEmpty()) {
            meta.append("  •  ").append(item.views.trim()).append(" views");
        }
        if (item.uploader != null && !item.uploader.trim().isEmpty()) {
            meta.append("  •  ").append(item.uploader.trim());
        }
        if (item.description != null && !item.description.trim().isEmpty()) {
            if (source == SOURCE_ALBUM && item.isSeries()) {
                // Counts come first so a long uploader name cannot hide them.
                meta.insert(0, item.description.trim() + "\n");
            } else {
                meta.append('\n').append(item.description.trim());
            }
        }
        return meta.toString();
    }

    private void ensureContext(Context context) {
        if (appContext != null || context == null) return;
        appContext = context.getApplicationContext();
        preloadDirectThumbnails();
    }

    /** Search starts rendered-page workers only after direct artwork is missing or fails. */
    private void ensureResolvers() {
        if (thumbnailResolvers != null || appContext == null) return;
        thumbnailResolvers = new RenderedThumbnailResolver[] {
                new RenderedThumbnailResolver(appContext, this::setResolvedThumbnail),
                new RenderedThumbnailResolver(appContext, this::setResolvedThumbnail),
                new RenderedThumbnailResolver(appContext, this::setResolvedThumbnail)
        };
    }

    private void requestRenderedThumbnail(NativeContentItem item) {
        requestRenderedThumbnail(item, "");
    }

    private void requestRenderedThumbnail(NativeContentItem item, String rejectedUrl) {
        if (item == null || item.isSection() || WikiFeetRepository.isRetiredItem(item)) return;
        if ((item.isSeries() || item.isCategory()) && !BunkrRepository.isAlbumUrl(item.url)) return;
        if (item.url == null || item.url.isEmpty()) return;
        if ((rejectedUrl == null || rejectedUrl.isEmpty()) &&
                item.imageUrl != null && !item.imageUrl.trim().isEmpty() &&
                !failedDirectThumbnails.contains(item.url)) return;
        String rejected = rejectedUrl == null ? "" : rejectedUrl;
        if (rejected.isEmpty() && failedDirectThumbnails.contains(item.url)) rejected = item.imageUrl;
        if (resolvedThumbnails.containsKey(item.url) && rejected.isEmpty()) return;
        ensureResolvers();
        if (thumbnailResolvers == null || thumbnailResolvers.length == 0) {
            requestedThumbnails.remove(item.url);
            return;
        }
        RenderedThumbnailResolver resolver = thumbnailJobs.get(item.url);
        if (resolver == null) {
            resolver = thumbnailResolvers[resolverCursor++ % thumbnailResolvers.length];
            thumbnailJobs.put(item.url, resolver);
        }
        if (!rejected.isEmpty()) {
            requestedThumbnails.add(item.url);
            resolver.request(item.url, rejected);
        } else if (requestedThumbnails.add(item.url)) {
            resolver.request(item.url);
        }
    }

    private void setResolvedThumbnail(String pageUrl, String thumbnailUrl) {
        if (pageUrl == null || pageUrl.isEmpty()) return;
        thumbnailJobs.remove(pageUrl);
        if (thumbnailUrl == null || thumbnailUrl.isEmpty()) {
            // A failed first pass should not poison this SearchActivity for the rest of its life.
            requestedThumbnails.remove(pageUrl);
            return;
        }
        resolvedThumbnails.put(pageUrl, thumbnailUrl);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.section || entry.item == null) continue;
            if (pageUrl.equals(entry.item.url)) {
                notifyItemChanged(i, "thumbnail");
            }
        }
    }

    private void loadImage(ImageView image, NativeContentItem item) {
        Glide.with(image).clear(image);
        image.setImageDrawable(new ColorDrawable(Color.rgb(31, 31, 36)));
        if (WikiFeetRepository.isRetiredItem(item) && !item.imageUrl.startsWith("file://")) return;

        byte[] embedded = null;
        if (item.isSeries() || item.isCategory()) {
            embedded = EmbeddedBrowseArtwork.get(image.getContext(), item.url);
        }
        if (embedded != null && embedded.length > 512) {
            com.bumptech.glide.RequestBuilder<Drawable> embeddedRequest = Glide.with(image)
                    .load(embedded)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(false)
                    .dontAnimate()
                    .transition(SharedArtworkReveal.artwork("spotlight", item.url, false))
                    .placeholder(new ColorDrawable(Color.rgb(31, 31, 36)))
                    .error(new ColorDrawable(Color.rgb(31, 31, 36)));
            applyCoverFit(embeddedRequest, image).into(image);
            return;
        }

        String resolved = resolvedThumbnails.get(item.url);
        boolean usingDirect = (resolved == null || resolved.isEmpty()) &&
                item.imageUrl != null && !item.imageUrl.isEmpty() &&
                !failedDirectThumbnails.contains(item.url);
        String imageUrl = usingDirect ? item.imageUrl : resolved;
        if (imageUrl == null || imageUrl.isEmpty()) return;

        Object source = imageUrl.startsWith("file://")
                ? imageUrl
                : withSiteHeaders(imageUrl, imageReferer(item));
        com.bumptech.glide.RequestBuilder<Drawable> request = Glide.with(image)
                .load(source)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
                .transition(SharedArtworkReveal.artwork("spotlight", item.url, false))
                .placeholder(new ColorDrawable(Color.rgb(31, 31, 36)))
                .error(new ColorDrawable(Color.rgb(31, 31, 36)));
        final String attemptedUrl = imageUrl;
        request.listener(new RequestListener<Drawable>() {
            @Override
            public boolean onLoadFailed(
                    GlideException error,
                    Object model,
                    Target<Drawable> target,
                    boolean firstResource
            ) {
                if (sameUrl(attemptedUrl, item.imageUrl)) failedDirectThumbnails.add(item.url);
                String alternate = resolvedThumbnails.get(item.url);
                if (sameUrl(alternate, attemptedUrl)) resolvedThumbnails.remove(item.url);
                image.post(() -> {
                    requestRenderedThumbnail(item, attemptedUrl);
                    notifyThumbnailChanged(item.url);
                });
                return false;
            }

            @Override
            public boolean onResourceReady(
                    Drawable resource,
                    Object model,
                    Target<Drawable> target,
                    DataSource dataSource,
                    boolean firstResource
            ) {
                if (style.naturalAspect() && resource != null && image instanceof RatioImage) {
                    float ratio = BunkrGalleryAdapter.displayedTileAspect(
                            item.url,
                            item.aspectRatioHint,
                            resource.getIntrinsicWidth(),
                            resource.getIntrinsicHeight()
                    );
                    if (ratio > 0f && Float.isFinite(ratio)) {
                        naturalAspects.put(item.url, ratio);
                        image.post(() -> {
                            if (image instanceof RatioImage
                                    && item.url != null
                                    && item.url.equals(((RatioImage) image).getTag(R.id.shows_watch_source_page))) {
                                ((RatioImage) image).setRatio(ratio);
                            }
                        });
                    }
                }
                return false;
            }
        });
        applyCoverFit(request, image).into(image);
    }

    private static com.bumptech.glide.RequestBuilder<Drawable> applyCoverFit(
            com.bumptech.glide.RequestBuilder<Drawable> request, ImageView image) {
        return image.getScaleType() == ImageView.ScaleType.FIT_CENTER
                ? request.fitCenter() : request.centerCrop();
    }

    private void notifyThumbnailChanged(String pageUrl) {
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (!entry.section && entry.item != null && pageUrl.equals(entry.item.url)) {
                notifyItemChanged(i, "thumbnail");
            }
        }
    }

    private boolean sameUrl(String first, String second) {
        if (first == null || second == null) return false;
        return first.trim().replace("&amp;", "&")
                .equals(second.trim().replace("&amp;", "&"));
    }

    private void preloadDirectThumbnails() {
        if (appContext == null) return;
        int loaded = 0;
        for (Entry entry : entries) {
            if (entry.section || entry.item == null) continue;
            NativeContentItem item = entry.item;
            if (item.imageUrl == null || item.imageUrl.trim().isEmpty()) continue;
            if (!item.imageUrl.startsWith("file://") && WikiFeetRepository.isRetiredItem(item)) continue;
            Object source = item.imageUrl.startsWith("file://")
                    ? item.imageUrl
                    : withSiteHeaders(item.imageUrl, imageReferer(item));
            Glide.with(appContext)
                    .load(source)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .dontAnimate()
                    .preload(480, 270);
            if (++loaded >= 18) break;
        }
    }

    private GlideUrl withSiteHeaders(String imageUrl, String pageUrl) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", pageUrl == null || pageUrl.isEmpty() ? SITE : pageUrl)
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");

        try {
            String cookies = CookieManager.getInstance().getCookie(imageUrl);
            if (cookies == null || cookies.trim().isEmpty()) {
                cookies = CookieManager.getInstance().getCookie(pageUrl == null ? SITE : pageUrl);
            }
            if (cookies == null || cookies.trim().isEmpty()) {
                cookies = CookieManager.getInstance().getCookie(SITE);
            }
            if (cookies != null && !cookies.trim().isEmpty()) headers.addHeader("Cookie", cookies);
        } catch (Exception ignored) {
        }

        return new GlideUrl(imageUrl, headers.build());
    }

    private String imageReferer(NativeContentItem item) {
        String eromeReferer = EromeRepository.imageReferer(item);
        if (!eromeReferer.isEmpty()) return eromeReferer;
        String wikiFeetReferer = WikiFeetRepository.imageReferer(item);
        if (!wikiFeetReferer.isEmpty()) return wikiFeetReferer;
        return item == null ? null : item.url;
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (holder instanceof ResultHolder) {
            ResultHolder result = (ResultHolder) holder;
            if (!result.entranceKey.isEmpty()) fades.show(result.card, result.entranceKey);
        } else if (holder instanceof CardHolder) {
            CardHolder card = (CardHolder) holder;
            if (!card.entranceKey.isEmpty()) fades.show(card.card, card.entranceKey);
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof ResultHolder) SearchResultFades.recycle(((ResultHolder) holder).card);
        if (holder instanceof CardHolder) SearchResultFades.recycle(((CardHolder) holder).card);
        super.onViewDetachedFromWindow(holder);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof ResultHolder) {
            ResultHolder result = (ResultHolder) holder;
            result.boundUrl = "";
            result.entranceKey = "";
            SearchResultFades.recycle(result.card);
            Glide.with(result.image).clear(result.image);
            result.card.setOnClickListener(null);
            result.card.setOnLongClickListener(null);
            result.favorite.setOnClickListener(null);
        } else if (holder instanceof CardHolder) {
            CardHolder card = (CardHolder) holder;
            card.boundUrl = "";
            card.entranceKey = "";
            SearchResultFades.recycle(card.card);
            Glide.with(card.image).clear(card.image);
            card.card.setOnClickListener(null);
            card.favorite.setOnClickListener(null);
        }
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    static final class SectionHolder extends RecyclerView.ViewHolder {
        final TextView title;

        SectionHolder(TextView title) {
            super(title);
            this.title = title;
        }
    }

    static final class ResultHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView image;
        final TextView title;
        final TextView meta;
        final TextView favorite;
        String boundUrl = "";
        String entranceKey = "";

        final TextView provider;

        ResultHolder(MaterialCardView card, ImageView image, TextView title, TextView meta, TextView favorite, TextView provider) {
            super(card);
            this.card = card;
            this.image = image;
            this.title = title;
            this.meta = meta;
            this.favorite = favorite;
            this.provider = provider;
        }
    }

    static final class CardHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final RatioImage image;
        final TextView title;
        final TextView meta;
        final TextView favorite;
        final TextView duration;
        String boundUrl = "";
        String entranceKey = "";

        CardHolder(MaterialCardView card, RatioImage image, TextView title, TextView meta,
                   TextView favorite, TextView duration) {
            super(card);
            this.card = card;
            this.image = image;
            this.title = title;
            this.meta = meta;
            this.favorite = favorite;
            this.duration = duration;
        }
    }

    static final class RatioImage extends ImageView {
        private float ratio = 16f / 9f;

        RatioImage(Context context) { super(context); }

        void setRatio(float widthOverHeight) {
            ratio = widthOverHeight > 0f ? widthOverHeight : 1f;
            requestLayout();
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = Math.max(1, Math.round(width / ratio));
            setMeasuredDimension(width, height);
        }
    }
}
