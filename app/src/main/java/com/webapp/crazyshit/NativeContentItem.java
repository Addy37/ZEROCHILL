package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public final class NativeContentItem {
    public static final String KIND_MEDIA = "media";
    public static final String KIND_IMAGE = "image";
    public static final String KIND_MEME = "meme";
    public static final String KIND_CATEGORY = "category";
    public static final String KIND_SERIES = "series";
    public static final String KIND_SECTION = "section";
    public static final String KIND_CREATOR = "creator";

    public final String kind;
    public final String title;
    public final String url;
    public final String imageUrl;
    public final String views;
    public final String uploader;
    public final String comments;
    public final String description;
    public final String searchQuery;
    public final long publishedAtMillis;
    public final float aspectRatioHint;
    final String sourceMediaUrl;
    final String sourcePageUrl;
    final List<String> creatorAliases;
    final List<String> creatorProfileUrls;

    public NativeContentItem(
            String kind,
            String title,
            String url,
            String imageUrl,
            String views,
            String uploader,
            String comments
    ) {
        this(kind, title, url, imageUrl, views, uploader, comments, "", "");
    }

    public NativeContentItem(
            String kind,
            String title,
            String url,
            String imageUrl,
            String views,
            String uploader,
            String comments,
            String description
    ) {
        this(kind, title, url, imageUrl, views, uploader, comments, description, "");
    }

    public NativeContentItem(
            String kind,
            String title,
            String url,
            String imageUrl,
            String views,
            String uploader,
            String comments,
            String description,
            String searchQuery
    ) {
        this(kind, title, url, imageUrl, views, uploader, comments, description, searchQuery, 0L, 0f);
    }

    public NativeContentItem(
            String kind,
            String title,
            String url,
            String imageUrl,
            String views,
            String uploader,
            String comments,
            String description,
            String searchQuery,
            long publishedAtMillis
    ) {
        this(
                kind,
                title,
                url,
                imageUrl,
                views,
                uploader,
                comments,
                description,
                searchQuery,
                publishedAtMillis,
                0f
        );
    }

    public NativeContentItem(
            String kind,
            String title,
            String url,
            String imageUrl,
            String views,
            String uploader,
            String comments,
            String description,
            String searchQuery,
            long publishedAtMillis,
            float aspectRatioHint
    ) {
        this(kind, title, url, imageUrl, views, uploader, comments, description, searchQuery,
                publishedAtMillis, aspectRatioHint, Collections.emptyList(), Collections.emptyList(), "", "");
    }

    private NativeContentItem(
            String kind, String title, String url, String imageUrl, String views, String uploader,
            String comments, String description, String searchQuery, long publishedAtMillis,
            float aspectRatioHint, List<String> creatorAliases, List<String> creatorProfileUrls,
            String sourceMediaUrl, String sourcePageUrl
    ) {
        this.kind = kind == null ? KIND_MEDIA : kind;
        this.title = title == null ? "" : title;
        this.url = url == null ? "" : url;
        this.imageUrl = imageUrl == null ? "" : imageUrl;
        this.views = views == null ? "" : views;
        this.uploader = uploader == null ? "" : uploader;
        this.comments = comments == null ? "" : comments;
        this.description = description == null ? "" : description;
        this.searchQuery = searchQuery == null ? "" : searchQuery;
        this.publishedAtMillis = Math.max(0L, publishedAtMillis);
        this.aspectRatioHint = aspectRatioHint > 0f && Float.isFinite(aspectRatioHint)
                ? aspectRatioHint
                : 0f;
        this.sourceMediaUrl = sourceMediaUrl;
        this.sourcePageUrl = sourcePageUrl;
        this.creatorAliases = creatorAliases;
        this.creatorProfileUrls = creatorProfileUrls;
    }

    NativeContentItem withCreatorIdentity(List<String> aliases, List<String> profileUrls) {
        if (!isCreator() || (creatorAliases.isEmpty() && creatorProfileUrls.isEmpty()
                && (aliases == null || aliases.isEmpty()) && (profileUrls == null || profileUrls.isEmpty()))) {
            return this;
        }
        return new NativeContentItem(kind, title, url, imageUrl, views, uploader, comments,
                description, searchQuery, publishedAtMillis, aspectRatioHint,
                boundedIdentityValues(aliases), boundedIdentityValues(profileUrls), sourceMediaUrl, sourcePageUrl);
    }

    NativeContentItem withSourceMedia(String mediaUrl, String pageUrl) {
        if (sourceMediaUrl.equals(mediaUrl) && sourcePageUrl.equals(pageUrl)) return this;
        return new NativeContentItem(kind, title, url, imageUrl, views, uploader, comments,
                description, searchQuery, publishedAtMillis, aspectRatioHint, creatorAliases,
                creatorProfileUrls, mediaUrl, pageUrl);
    }

    private static List<String> boundedIdentityValues(List<String> input) {
        if (input == null || input.isEmpty()) return Collections.emptyList();
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String value : input) {
            if (value != null && !value.trim().isEmpty()) values.add(value.trim());
            if (values.size() == 8) break;
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    public boolean isCategory() {
        return KIND_CATEGORY.equals(kind);
    }

    public boolean isSeries() {
        return KIND_SERIES.equals(kind);
    }

    public boolean isMeme() {
        return KIND_MEME.equals(kind);
    }

    public boolean isImage() {
        return KIND_IMAGE.equals(kind) || KIND_MEME.equals(kind);
    }

    public boolean isGif() {
        return isImage() && (gifPath(url) || gifPath(sourceMediaUrl));
    }

    private static boolean gifPath(String value) {
        if (value == null || value.isEmpty()) return false;
        int query = value.indexOf('?');
        int fragment = value.indexOf('#');
        int end = value.length();
        if (query >= 0) end = Math.min(end, query);
        if (fragment >= 0) end = Math.min(end, fragment);
        return value.substring(0, end).toLowerCase(java.util.Locale.ROOT).endsWith(".gif");
    }

    public boolean isVideo() {
        return KIND_MEDIA.equals(kind);
    }

    public boolean isSection() {
        return KIND_SECTION.equals(kind);
    }

    public boolean isCreator() {
        return KIND_CREATOR.equals(kind);
    }

    public NativeContentItem merge(NativeContentItem other) {
        if (other == null) return this;
        return new NativeContentItem(
                kind,
                choose(title, other.title),
                choose(url, other.url),
                choose(imageUrl, other.imageUrl),
                choose(views, other.views),
                choose(uploader, other.uploader),
                choose(comments, other.comments),
                choose(description, other.description),
                choose(searchQuery, other.searchQuery),
                publishedAtMillis > 0L ? publishedAtMillis : other.publishedAtMillis,
                aspectRatioHint > 0f ? aspectRatioHint : other.aspectRatioHint,
                creatorAliases, creatorProfileUrls,
                choose(sourceMediaUrl, other.sourceMediaUrl), choose(sourcePageUrl, other.sourcePageUrl)
        );
    }

    private static String choose(String first, String second) {
        return first != null && !first.trim().isEmpty() ? first : second;
    }
}
