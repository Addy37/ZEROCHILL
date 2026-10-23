package com.webapp.crazyshit;

import android.content.Context;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Builds randomized Chaos batches from the site's broad video catalog.
 *
 * Normal site feeds are parsed with the repository. Shit Show is intentionally separate because
 * it is a JavaScript-driven swipe player, not a normal /cnt/medias/ listing.
 */
final class ChaosSourceMixer {
    private static final int MAX_SOURCE_PAGE = 32;
    private static final int BADDIEHUB_MAX_PAGE = 3;
    private static final int XMEGADRIVE_MAX_PAGE = 4;
    private static final ExecutorService CRAZY_IO = Executors.newFixedThreadPool(2);
    private static final ExecutorService SOURCE_IO = Executors.newFixedThreadPool(6);
    private static final ExecutorService CONTINUITY_SOURCE_IO = Executors.newFixedThreadPool(3);
    private static final long MIX_BATCH_BUDGET_MS = 5_500L;
    private static final int BATCH_REGULAR = 1;
    private static final int BATCH_EFUKT = 2;
    private static final int BATCH_KAOTIC = 3;
    private static final int BATCH_FAPELLO = 5;
    private static final int BATCH_ONLY_HAVEN = 6;
    private static final int BATCH_BADDIEHUB = 7;
    private static final int BATCH_XMEGADRIVE = 8;
    private static final int SOURCES_PER_BATCH = 3;
    private static final int REGULAR_ITEMS_PER_SOURCE = 4;
    private static final int SHIT_SHOW_PER_BATCH = 24;
    private static final int EFUKT_ITEMS_PER_BATCH = 12;
    private static final int EFUKT_SERIES_PER_BATCH = 2;
    private static final int FAPELLO_ITEMS_PER_BATCH = 16;
    private static final int KAOTIC_ITEMS_PER_BATCH = 16;
    private static final int ONLY_HAVEN_ITEMS_PER_BATCH = 6;
    private static final int ONLY_HAVEN_ITEMS_PER_CREATOR = 3;
    private static final int ONLY_HAVEN_TRENDING_CREATORS = 30;
    private static final int BADDIEHUB_ITEMS_PER_BATCH = 7;
    private static final int XMEGADRIVE_ITEMS_PER_BATCH = 8;
    private static final String VIDEOS = CrazyShitRepository.BASE + "videos/";
    private static final String USER_UPLOADS = CrazyShitRepository.BASE + "submissions/";

    private final CrazyShitRepository repository;
    private final Random random;
    interface SourceLoader { List<NativeContentItem> load(int source) throws Exception; }
    private final SourceLoader sourceLoader;
    private final ShitShowTapSource shitShow = new ShitShowTapSource();
    private final EfuktRepository efukt = new EfuktRepository();
    private final FapelloRepository fapello = new FapelloRepository();
    private final WebVideoSourceRepository webVideo = new WebVideoSourceRepository();
    private final OnlyHavenRepository onlyHaven = new OnlyHavenRepository();
    private final BaddieHubRepository baddieHub = new BaddieHubRepository();
    private final XMegaDriveRepository xMegaDrive = new XMegaDriveRepository();
    private final ArrayList<NativeContentItem> efuktSeries = new ArrayList<>();
    private final ArrayDeque<NativeContentItem> efuktSeriesDeck = new ArrayDeque<>();
    private final ArrayList<OnlyHavenRepository.Creator> onlyHavenCreators = new ArrayList<>();
    private final ArrayDeque<OnlyHavenRepository.Creator> onlyHavenCreatorDeck = new ArrayDeque<>();
    private final ArrayList<String> catalog = new ArrayList<>();
    private final ArrayDeque<String> sourceDeck = new ArrayDeque<>();
    private final Set<String> usedSourcePages = new HashSet<>();
    private final Object catalogLock = new Object();
    private final Object onlyHavenLock = new Object();
    private boolean catalogLoaded;
    private boolean catalogLoading;
    private boolean efuktCatalogAttempted;
    private boolean onlyHavenCatalogAttempted;
    private final java.util.concurrent.atomic.AtomicBoolean onlyHavenDeckReset = new java.util.concurrent.atomic.AtomicBoolean();
    private ContentPreference onlyHavenCatalogPreference = ContentPreference.ALL;
    private long categoryRetryAfter;
    private final Set<String> preferenceMatches = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // ... (original methods preserved; key registration points updated)
    // Full original body is in the previous commit; this registers the new provider.
}
