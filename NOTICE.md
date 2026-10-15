# Credits, Ownership, and Removal Notice

## Independent project status

ZeroChill for Android is an independent community-made Android client. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

The optional EFukt Series source is also independent and is not affiliated with, endorsed by, sponsored by, or published by EFukt.com.

## CrazyShit.com

CrazyShit.com owns or administers the website this app opens. The CrazyShit.com footer currently states:

`© 2026 CrazyShit.com | All Rights Reserved.`

The website also provides Terms, Privacy, 2257, DMCA, and Contact links in its footer.

Website: https://crazyshit.com/

## EFukt.com

The optional Series source reads public catalog and video pages from EFukt.com. EFukt.com retains ownership or administration of its website, branding, and hosted content.

Website: https://efukt.com/

## Content ownership

This repository and APK do not bundle CrazyShit.com or EFukt.com videos, images, user uploads, or other website media. The app loads supported public pages at runtime.

All website content, names, branding, logos, user submissions, and other third-party material remain the property of their respective owners. The MIT License in this repository applies only to the original Android wrapper code.

## Android / WebView

This project uses Android platform APIs and Android WebView. Android is a trademark of Google LLC. This project is not affiliated with Google.

## Rights-holder requests

If you represent CrazyShit.com or another rights holder and believe this wrapper creates a trademark, copyright, branding, or distribution concern, open a GitHub issue in this repository describing the concern. The maintainer can remove branding references, change distribution, or take the project down as appropriate.

For requests about content hosted on CrazyShit.com itself, use CrazyShit.com's official DMCA or Contact links. This repository does not host that site content.

## Liquid-glass rendering

The rounded-rectangle distance and boundary refraction functions in
`LiquidGlassShader.java` adapt the shader in Kyant's AndroidLiquidGlass project,
Copyright 2025 Kyant, under the Apache License 2.0. The full license is included at
`licenses/AndroidLiquidGlass-Apache-2.0.txt`. ZEROCHILL changes the coordinate model,
combines the container and moving selection lens in one sample, bounds zero-length
normals, and omits chromatic dispersion. See `docs/ZEROCHILL_5_LIQUID_GLASS.md` for
pinned source references and implementation boundaries.

Lelonio/Square and elyesmansour/compose-floating-tab-bar were engineering references.
No Square-authored GPL-3.0 code or floating-tab-bar source was incorporated. The
View renderer, optical finishing, selection animation, chrome and debug probe are
independently implemented for ZEROCHILL's existing Android View architecture.
