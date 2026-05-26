# Third-Party Licenses

Runestone includes or depends on the following third-party components. Each is used in compliance with its respective license.

---

## mkxp-z

- **Source:** https://github.com/mkxp-z/mkxp-z
- **License:** GNU General Public License v2 or later (GPLv2+)
- **Copyright:** Ancurio, Splendide-Imaginarius, and contributors
- **Description:** Open-source cross-platform player for RPG Maker XP / VX / VX Ace games. Modified fork of mkxp (Ancurio/mkxp).

When built with HTTPS support enabled (default), mkxp-z links against OpenSSL (Apache v2 license), making the resulting binaries effectively GPLv3.

**Obligations:**
1. This notice and the GPLv2 license text must accompany any distribution.
2. Source code for mkxp-z is available at the link above.
3. Modifications to mkxp-z, if distributed, must be made available under GPLv2+.

---

## Android System WebView

- **Provider:** Google / Android Open Source Project
- **License:** Apache 2.0 / BSD-style (Chromium)
- **Description:** System component pre-installed on Android devices. Used to render MV/MZ HTML5 games via the standard `android.webkit.WebView` API.

No redistribution required — this is a system API used at runtime.

---

## Android SDK / Jetpack

- **License:** Apache 2.0
- **Description:** Standard Android development libraries used under their published terms.

---

## EasyMV-AndroidRPGMVPlayer (reference)

- **Source:** https://github.com/KEKE046/EasyMV-AndroidRPGMVPlayer
- **License:** MIT
- **Copyright:** KEKE046
- **Description:** Reference implementation for running RPG Maker MV games in Android WebView. Used for architectural inspiration; no code is directly copied without attribution.
