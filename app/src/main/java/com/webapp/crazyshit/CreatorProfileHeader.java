package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Creator identity and favorite action shared by the gallery header. */
final class CreatorProfileHeader extends LinearLayout {
    private final NativeContentItem creator;
    private final TextView favorite;
    private final ImageView banner;
    private final ExecutorService heroIo = Executors.newSingleThreadExecutor();
    private boolean detached;
    private FrameLayout compactFavorite;
    private ImageView compactAvatar;
    private TextView compactFavoriteBadge;
    private android.content.SharedPreferences favoritePreferences;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener favoriteListener =
            (prefs, key) -> refresh();

    CreatorProfileHeader(Context context, String title, String query, String url) {
        this(context, title, query, url, "");
    }

    CreatorProfileHeader(
            Context context,
            String title,
            String query,
            String url,
            String galleryCacheKey
    ) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(Color.BLACK);

        NativeContentItem found = new NativeContentItem(
                NativeContentItem.KIND_CREATOR,
                title,
                url,
                "",
                "",
                "",
                "",
                "",
                query
        );
        for (NativeContentItem item : CreatorCatalog.all(context)) {
            if (CreatorFavoriteStore.key(item).equals(CreatorFavoriteStore.key(found))) {
                found = item;
                break;
            }
        }
        creator = found;
        CreatorCatalog.FavoriteGroup mergedGroup =
                CreatorMergeUi.findManualGroup(context, galleryCacheKey);

        FrameLayout hero = new FrameLayout(context);
        hero.setBackgroundColor(Color.rgb(13, 15, 18));

        banner = new ImageView(context);
        banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        banner.setImageDrawable(new ColorDrawable(Color.rgb(13, 15, 18)));
        banner.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        hero.addView(banner, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(context);
        shade.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable heroShade = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {
                        Color.argb(20, 0, 0, 0),
                        Color.argb(72, 0, 0, 0),
                        Color.argb(242, 0, 0, 0)
                }
        );
        shade.setBackground(heroShade);
        hero.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout identity = new LinearLayout(context);
        identity.setOrientation(HORIZONTAL);
        identity.setGravity(Gravity.BOTTOM | Gravity.CENTER_VERTICAL);
        identity.setPadding(dp(16), dp(16), dp(16), dp(14));

        ImageView avatar = new ImageView(context);
        avatar.setBackground(BrowseUi.rounded(context, BrowseUi.SURFACE, 30));
        avatar.setClipToOutline(true);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setImageResource(R.drawable.ic_more_account);
        avatar.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        identity.addView(avatar, new LayoutParams(dp(60), dp(60)));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        labels.setGravity(Gravity.BOTTOM);
        labels.setPadding(dp(12), 0, dp(10), dp(2));

        TextView name = BrowseUi.text(context, title, 28, Color.WHITE);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(name, new LayoutParams(-1, -2));

        LinearLayout meta = new LinearLayout(context);
        meta.setOrientation(HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);

        TextView subtitle = BrowseUi.text(
                context,
                mergedGroup == null
                        ? "OnlyFap creator"
                        : "Merged from " + mergedGroup.members.size() + " profiles",
                12,
                Color.rgb(205, 209, 216)
        );
        subtitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        meta.addView(subtitle, new LayoutParams(-2, -2));

        if (mergedGroup != null) {
            TextView mergeBadge = CreatorMergeUi.badge(context);
            CreatorMergeUi.bind(
                    mergeBadge,
                    mergedGroup,
                    () -> CreatorMergeUi.showMembers(context, mergedGroup)
            );
            LayoutParams mergeParams = new LayoutParams(-2, dp(22));
            mergeParams.setMarginStart(dp(7));
            meta.addView(mergeBadge, mergeParams);
        }
        labels.addView(meta, new LayoutParams(-1, -2));

        identity.addView(labels, new LayoutParams(0, -2, 1f));

        favorite = BrowseUi.action(context, "", "Favorite creator", v -> {
            toggleFavorite();
        });
        favorite.setTextSize(13);
        favorite.setTag("creator_favorite");
        identity.addView(favorite, new LayoutParams(-2, dp(46)));

        FrameLayout.LayoutParams identityParams = new FrameLayout.LayoutParams(-1, -2);
        identityParams.gravity = Gravity.BOTTOM;
        hero.addView(identity, identityParams);

        addView(hero, new LayoutParams(-1, dp(184)));

        loadAvatar(avatar);
        showHero(creator.imageUrl, creator.url);

        loadOnlyHavenHero(query);
        refresh();
    }

    private void loadOnlyHavenHero(String query) {
        String cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.length() < 2) return;
        Context appContext = getContext().getApplicationContext();
        heroIo.execute(() -> {
            try {
                OnlyHavenRepository repository = new OnlyHavenRepository();
                List<OnlyHavenRepository.Creator> matches =
                        repository.searchCreators(appContext, cleanQuery, 6);
                if (matches == null) return;
                for (OnlyHavenRepository.Creator match : matches) {
                    if (!OnlyHavenRepository.matchesHeaderCreator(cleanQuery, match)) continue;
                    if (creator.imageUrl.isEmpty()) post(() -> showHero(match.imageUrl, match.url));
                    String heroUrl = repository.creatorHeaderUrl(match);
                    if (heroUrl.isEmpty()) return;
                    post(() -> showHero(heroUrl, match.url));
                    return;
                }
            } catch (IOException ignored) {
            }
        });
    }

    private void showHero(String heroUrl, String referer) {
        if (detached || heroUrl == null || heroUrl.isEmpty()) return;
        Glide.with(banner)
                .load(withReferer(heroUrl, referer))
                .centerCrop()
                .transition(ThumbnailFades.hero())
                .placeholder(new ColorDrawable(Color.rgb(13, 15, 18)))
                .error(Glide.with(banner)
                        .load(creator.imageUrl.isEmpty() ? null : withReferer(creator.imageUrl, creator.url))
                        .centerCrop()
                        .error(new ColorDrawable(Color.rgb(13, 15, 18))))
                .into(banner);
    }

    private GlideUrl withReferer(String imageUrl, String referer) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder();
        if (referer != null && !referer.trim().isEmpty()) {
            headers.addHeader("Referer", referer);
        }
        return new GlideUrl(imageUrl, headers.build());
    }

    private void loadAvatar(ImageView avatar) {
        if (creator.imageUrl.isEmpty()) return;
        Glide.with(avatar)
                .load(withReferer(creator.imageUrl, creator.url))
                .circleCrop()
                .transition(ThumbnailFades.avatar())
                .placeholder(R.drawable.ic_more_account)
                .error(R.drawable.ic_more_account)
                .into(avatar);
    }

    void bindCompactFavorite(FrameLayout action) {
        compactFavorite = action;
        action.setFocusable(true);
        action.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(
                    View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.ToggleButton.class.getName());
                info.setCheckable(true);
                info.setChecked(host.isSelected());
            }
        });

        // Keep the complete photo and corner badge inside a centered 40dp cluster.
        FrameLayout cluster = new FrameLayout(getContext());
        action.addView(cluster, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        FrameLayout ring = new FrameLayout(getContext());
        ring.setTag("creator_favorite_ring");
        GradientDrawable outline = BrowseUi.rounded(getContext(), Color.BLACK, 18);
        outline.setStroke(dp(1), UiPalette.PRIMARY);
        ring.setBackground(outline);
        ring.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        FrameLayout.LayoutParams ringParams =
                new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.TOP | Gravity.START);
        cluster.addView(ring, ringParams);

        compactAvatar = new ImageView(getContext());
        compactAvatar.setTag("creator_favorite_avatar");
        compactAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        compactAvatar.setBackground(BrowseUi.rounded(getContext(), BrowseUi.SURFACE, 16));
        compactAvatar.setClipToOutline(true);
        compactAvatar.setImageResource(R.drawable.ic_more_account);
        ring.addView(compactAvatar, new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER));
        loadAvatar(compactAvatar);

        compactFavoriteBadge = BrowseUi.text(getContext(), "+", 13, UiPalette.ON_PRIMARY);
        // A decorative icon, not body text: keep its glyph inside the badge at large font sizes.
        compactFavoriteBadge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, dp(12));
        compactFavoriteBadge.setIncludeFontPadding(false);
        compactFavoriteBadge.setTag("creator_favorite_badge");
        compactFavoriteBadge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        compactFavoriteBadge.setGravity(Gravity.CENTER);
        GradientDrawable badge = BrowseUi.rounded(getContext(), UiPalette.PRIMARY, 8);
        badge.setStroke(dp(1), Color.BLACK);
        compactFavoriteBadge.setBackground(badge);
        compactFavoriteBadge.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        cluster.addView(compactFavoriteBadge,
                new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.BOTTOM | Gravity.END));
        ZeroChillMotion.installPressFeedback(action);
        action.setOnClickListener(v -> toggleFavorite());
        refresh();
    }

    void toggleFavorite() {
        CreatorFavoriteStore.toggle(getContext(), creator);
        refresh();
    }

    boolean isFavoriteOffscreen(int verticalOffset) {
        android.graphics.Rect bounds = new android.graphics.Rect();
        favorite.getDrawingRect(bounds);
        offsetDescendantRectToMyCoords(favorite, bounds);
        return favorite.getHeight() > 0 && bounds.bottom + verticalOffset <= 0;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        favoritePreferences = getContext().getSharedPreferences("creator_favorites", Context.MODE_PRIVATE);
        favoritePreferences.registerOnSharedPreferenceChangeListener(favoriteListener);
        refresh();
    }

    void refresh() {
        boolean saved = CreatorFavoriteStore.contains(getContext(), creator);
        favorite.setText(saved ? "★  Favorited" : "☆  Favorite");
        favorite.setTextColor(saved ? UiPalette.ON_PRIMARY : Color.WHITE);
        favorite.setBackground(BrowseUi.rounded(
                getContext(),
                saved ? UiPalette.PRIMARY : Color.argb(190, 20, 24, 29),
                23
        ));
        String description = (saved ? "Unfavorite " : "Favorite ") + creator.title;
        favorite.setContentDescription(description);
        favorite.setSelected(saved);
        if (compactFavorite != null) {
            compactFavoriteBadge.setText(saved ? "✓" : "+");
            compactFavorite.setContentDescription(description);
            compactFavorite.setSelected(saved);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        detached = true;
        if (favoritePreferences != null) favoritePreferences.unregisterOnSharedPreferenceChangeListener(favoriteListener);
        heroIo.shutdownNow();
        if (compactAvatar != null) Glide.with(compactAvatar).clear(compactAvatar);
        Glide.with(banner).clear(banner);
        super.onDetachedFromWindow();
    }

    private int dp(int value) {
        return BrowseUi.dp(getContext(), value);
    }
}
