# Shows episode browsing and category artwork

## Large source lists

Shows collections keep the order and titles returned by their source. ZEROCHILL does not infer seasons when the source does not expose season metadata.

When a loaded collection contains more than 10 playable items, the detail page displays a range selector beside the EPISODES heading. Ranges use the loaded source order in groups of 10, such as `1–10`, `11–20`, and `21–30`. The footer shows the visible range and total, with Previous 10 and Next 10 controls where a loaded neighboring range exists. At the loaded tail, Load more videos continues the source's existing pagination when available.

The range sheet also searches the titles that are already loaded for that collection. Local `#N` labels are navigation aids only and do not replace or persist over source titles.

The browser keeps the full loaded collection for episode counts, Resume or Play First selection, watch-session launch context, pagination, and snapshot restore. Only the visible RecyclerView is reduced to the selected range. Range and scroll position survive activity recreation; switching ranges scrolls the RecyclerView to its new start without changing the collapsing header position.

## Category artwork

Category surfaces can use a 2 by 2 thumbnail mosaic built from playable items inside the category. The existing category artwork remains the fallback when fewer than two usable thumbnails are available.

Mosaic selection is randomized once per app process and remains stable for that process so visible category cards do not continuously reshuffle. Once a category detail opens, its compact hero uses a single thumbnail from the loaded playable items. It rotates among distinct thumbnail URLs about every 13 seconds, preloads the next image, and never repeats the immediately previous image. The original category artwork remains underneath and is shown when fewer than two usable thumbnails are loaded. Detail text and playback actions stay fixed as images change.

Category cards reuse the bounded Shows collection warm cache. The category detail hero uses the collection already loaded by the browser. The artwork feature does not crawl every page of a category solely to build artwork.

Supported category warm-cache paths include CrazyShit, EFukt, Kaotic, BaddieHub, and XMegaDrive using their existing repository routes and request handling.

## Compatibility

This UI behavior does not change application ID, signing identity, persisted user collections, source parsers, backend APIs, Supabase, or remote configuration.
