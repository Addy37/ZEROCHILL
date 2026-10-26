from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path, old, new):
    file = ROOT / path
    text = file.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"anchor missing in {path}: {old[:100]!r}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")

# Let the detail hero bind directly to the category items already loaded by the browser.
replace_once(
    "app/src/main/java/com/webapp/crazyshit/ShowsCategoryMosaicView.java",
    """    void bind(NativeContentItem category) {\n        int request = ++generation;\n        categoryUrl = category == null || category.url == null ? \"\" : category.url.trim();\n        setVisibility(View.GONE);\n        if (category == null || categoryUrl.isEmpty()) return;\n\n        ShowsCollectionWarmCache.request(getContext(), category, (url, items) -> {\n            if (request != generation || !categoryUrl.equals(url)) return;\n            List<NativeContentItem> selected = ShowsCategoryMosaicSelector.select(\n                    items,\n                    categoryUrl,\n                    ShowsCategoryMosaicSelector.SESSION_SEED,\n                    tiles.length\n            );\n            if (selected.size() < 2) return;\n            for (int index = 0; index < tiles.length; index++) {\n                load(tiles[index], selected.get(index % selected.size()));\n            }\n            setVisibility(View.VISIBLE);\n        });\n    }\n""",
    """    void bind(NativeContentItem category) {\n        int request = ++generation;\n        categoryUrl = category == null || category.url == null ? \"\" : category.url.trim();\n        setVisibility(View.GONE);\n        if (category == null || categoryUrl.isEmpty()) return;\n\n        ShowsCollectionWarmCache.request(getContext(), category, (url, items) -> {\n            if (request != generation || !categoryUrl.equals(url)) return;\n            bindItems(url, items);\n        });\n    }\n\n    void bindItems(String url, List<NativeContentItem> items) {\n        String cleanUrl = url == null ? \"\" : url.trim();\n        if (cleanUrl.isEmpty()) return;\n        if (isShowing() && cleanUrl.equals(categoryUrl)) return;\n        categoryUrl = cleanUrl;\n        List<NativeContentItem> selected = ShowsCategoryMosaicSelector.select(\n                items,\n                categoryUrl,\n                ShowsCategoryMosaicSelector.SESSION_SEED,\n                tiles.length\n        );\n        if (selected.size() < 2) return;\n        for (int index = 0; index < tiles.length; index++) {\n            load(tiles[index], selected.get(index % selected.size()));\n        }\n        setVisibility(View.VISIBLE);\n    }\n""",
)

# RecyclerView owns recycling, so do not clear a mosaic merely because a cached card detaches.
replace_once(
    "app/src/main/java/com/webapp/crazyshit/ShowsCategoryMosaicView.java",
    """\n    @Override\n    protected void onDetachedFromWindow() {\n        clear();\n        super.onDetachedFromWindow();\n    }\n""",
    "\n",
)

# Put the mosaic on the large category detail hero as well.
replace_once(
    "app/src/main/java/com/webapp/crazyshit/ShowDetailsHeader.java",
    """    private final ImageView artwork;\n    private final TextView count;\n    private final TextView action;\n    private final String collectionUrl;\n""",
    """    private final ImageView artwork;\n    private final ShowsCategoryMosaicView categoryMosaic;\n    private final TextView count;\n    private final TextView action;\n    private final String collectionUrl;\n""",
)
replace_once(
    "app/src/main/java/com/webapp/crazyshit/ShowDetailsHeader.java",
    """        artwork.setBackground(new ColorDrawable(Color.rgb(14, 16, 19)));\n        addView(artwork, new FrameLayout.LayoutParams(-1, -1));\n\n        View shade = new View(context);\n""",
    """        artwork.setBackground(new ColorDrawable(Color.rgb(14, 16, 19)));\n        addView(artwork, new FrameLayout.LayoutParams(-1, -1));\n\n        boolean category = clean(sourceLabel, \"SHOW\")\n                .toUpperCase(java.util.Locale.US)\n                .contains(\"CATEGORY\");\n        categoryMosaic = category ? new ShowsCategoryMosaicView(context) : null;\n        if (categoryMosaic != null) {\n            addView(categoryMosaic, new FrameLayout.LayoutParams(-1, -1));\n        }\n\n        View shade = new View(context);\n""",
)
replace_once(
    "app/src/main/java/com/webapp/crazyshit/ShowDetailsHeader.java",
    """    void setPlaybackAction(boolean resumable, boolean available) {\n""",
    """    void setCategoryItems(java.util.List<NativeContentItem> items) {\n        if (categoryMosaic != null) categoryMosaic.bindItems(collectionUrl, items);\n    }\n\n    void setPlaybackAction(boolean resumable, boolean available) {\n""",
)

# Feed the hero from the exact full ordered collection used by the detail page.
replace_once(
    "app/src/main/java/com/webapp/crazyshit/NativeFeedBrowserActivity.java",
    """        showDetailsHeader.setItemCount((int) items.stream().filter(ShowsEpisodePlayback::playable).count());\n        ShowsEpisodePlayback.Selection selection = ShowsEpisodePlayback.select(\n""",
    """        showDetailsHeader.setItemCount((int) items.stream().filter(ShowsEpisodePlayback::playable).count());\n        showDetailsHeader.setCategoryItems(items);\n        ShowsEpisodePlayback.Selection selection = ShowsEpisodePlayback.select(\n""",
)

print("Category detail hero mosaic follow-up applied")
