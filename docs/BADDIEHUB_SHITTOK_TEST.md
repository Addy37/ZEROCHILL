# BaddieHub ShitTok device test

This branch adds BaddieHub to normal ShitTok refills only. It does not participate in the cold-start source race.

## Expected behavior

- BaddieHub clips appear with uploader label `BaddieHub`.
- Only videos whose effective media height is greater than their width enter the feed.
- Landscape and square clips are rejected before they become ShitTok pages.
- Direct media resolution is cached for the process lifetime.
- Slow or failed metadata checks do not block the other ShitTok sources.

## Device checks

1. Open ShitTok and swipe long enough for normal refills to occur.
2. Confirm BaddieHub clips eventually appear and play normally.
3. Confirm every BaddieHub clip shown is portrait.
4. Confirm swiping remains smooth while BaddieHub validation runs.
5. Confirm existing Kaotic, Shit Show, Fapello/OnlyFap, OnlyHaven, CrazyShit, and EFukt content still appears.

## Test-only limitation

BaddieHub source values are compiled for this device-test pass. Before release approval, move its domain, routes, headers, selectors, timeouts, and availability switch into the existing remote source configuration system.
