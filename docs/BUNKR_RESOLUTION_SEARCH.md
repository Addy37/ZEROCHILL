# Bunkr playback, artwork and Search repair

Source baseline: approved main `873646b74f0a18d5c4d99491b72970a3856cfeed`, including
merged PRs #350 and #352. Follow-up playback investigation uses the device-reviewed
thumbnail/Search candidate `3f0dc10b3a9e19012989eda3eb47965ca8fa00ac` on PR #353.

## Confirmed playback root cause

Device recordings 13893.mp4 and 13902.mp4 identify Bunkr file 15329122:
`https://bunkr.cr/f/Fuyd9dOX3ZbhP`, also available through
`https://dl.bunkr.cr/file/15329122`. Its album is `/a/WlA5Bbyp`.

The actual Android gallery player reproduces the failure in diagnostic run 40509430866.
Media3 reports ERROR_CODE_IO_UNSPECIFIED, caused by an ArrayIndexOutOfBoundsException
inside AacUtil.getSamplingFrequency / BoxParser.parseAudioSampleEntry. Re-resolving the
URL repeats the same parsing crash.

The video is ordinary H.264 Constrained Baseline, 854 x 480, 8-bit, with 48 kHz stereo AAC.
The MP4 audio sample entry's esds box contains the MPEG-4 AAC decoder descriptor but omits
its tag-05 AudioSpecificConfig. Media3 mistakes the following SL descriptor's one-byte
payload for AAC settings, then overruns it. Chrome's playback is more tolerant.
The original 32-byte MP4 header contract passed because it did not demux or decode audio.
The earlier signing/cache fixes did not resolve this failure.

## Playback compatibility layer

Only Bunkr MP4 gallery playback uses the compatibility data source. It buffers a bounded
moov box (maximum 8 MiB) and repairs missing AAC configuration using the MP4 audio sample
entry's supported sample rate/channel count and an AAC-LC fallback. A truncated one-byte
ASC is repaired only when its existing bits match that reconstructed configuration.
Complete existing ASC data and unrelated codec entries remain byte-for-byte unchanged.

The descriptor is rebuilt within its existing byte budget using legal expandable-size
encodings, or a free box when enough padding is available. All parent/container lengths,
media payload bytes, sample offsets, HTTP range positions and the advertised file length
remain unchanged. Entries without enough space, unsupported layouts/rates/channels and
oversized metadata pass through unchanged. No whole-video download, server remux,
transcoding or new dependency is introduced. The source sits above the existing cache,
so cached/downloaded original bytes remain untouched.

This is a narrow compatibility fallback for malformed metadata, not a general repair of
all damaged MP4 files or all AAC profiles. Audio decoding must be validated against the
actual reproduction, alongside first-frame/video progress and seeking.

## Artwork and Search

Current album DOM puts `/images/video.svg` before the raster thumbnail. The parser now
skips the type icon. Missing/failed visible Bunkr tiles can repair from file social
artwork, poster or grid cover. Two workers, 24 pending keys, duplicate request sharing,
stale-holder guards and a 256-entry cache bound the work. Positive metadata caches for
one hour; failures for five minutes. Scheduling backpressure keeps visible tiles eligible
when capacity becomes available. Existing Glide disk caching/preloading remains.

Search tabs are Shows | OnlyFap | Bunkr | XMegaDrive | BaddieHub | Library. Bunkr uses
searchAlbums and the existing native album/gallery routing. Its separate worker keeps
slow Bunkr sockets out of the other scopes' worker pool. Existing generation checks,
cancellation, snapshots and recent searches remain. The old scope=bunkr creator shortcut
alias remains OnlyFap; the new album scope persists as BUNKR_ALBUMS.

The resolver also rejects poster-prefix/unsigned fallbacks after signing failure, preserves
unrelated CDN query fields, omits cookies on signing like the current site, retains legacy
API/direct-source fallbacks and drops expired cached signed streams before reuse.

## Validation and impact

Regression fixtures include the exact esds/mp4a bytes from file 15329122, missing/valid
ASC handling, byte-position preservation, fragmented reads, bounded requests, source
recreation and seeking, plus the existing resolver/artwork/Search/gallery coverage.
The opt-in live router contract checks 32 MP4 bytes and album artwork; it is a source
contract, not a playback test. Runtime validation uses the actual Android gallery/player.

No application ID, signing identity, user-record format, migration, backend, Supabase,
remote source config, domain/route/header/retry/timeout changes, or new dependency.
Physical-device confirmation remains required. No source/item is hidden or removed.
