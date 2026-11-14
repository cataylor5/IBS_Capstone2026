# Overview control and shared appearance

The supplied black/aqua route icon is reproduced as a vector drawable (ic_route_overview.xml). The overview control is a 56dp ImageButton at the right edge, 12dp from that edge and 88dp above the bottom of the SDK map viewport, above the native report control. The Google attribution remains inside the unobstructed SDK viewport above the separate IBS ETA card. This placement uses the SDK 8.1 default control layout, not private SDK view IDs.

The accessibility action is Show route overview in follow mode and Resume following vehicle in overview. NavigationFragment.setRecenterButtonEnabled(false) suppresses native Recenter while overview is active. Leaving overview restores true and tilted vehicle following. Route changes, continuation and recreation retain the existing overview behavior. Guidance and the simulator are not paused by this control.

IbsAppearance owns a shared observable mode and saves its last value locally. NavigationFragment.addOnNightModeChangedListener reports the effective SDK mode immediately on registration and on transitions. That value drives native header/menu surfaces and the Compose theme. The SDK stays in its default automatic mode; no FORCE_DAY/FORCE_NIGHT override is introduced.

When no map is active, real Android system theme changes update Home while open. After returning from navigation, Home initially retains the last actual map mode, even if it differed from the device theme. Application configuration callbacks track system changes while a map is active without overriding the map's authority. Appearance stores no trip or location data.

The shared night palette uses slate #1D2835 background and #263442 surfaces, light #ECF3FA text, #63788C borders and aqua readable text accents. Home cards, filters, fixed footer, destination suggestions, header artwork background, native navigation header and Change Stop results all use this palette. Day surfaces are white/light neutral. Filled primary actions and the IBS ETA card remain cobalt; text actions become aqua on slate for contrast. System bar icons and Places attribution artwork also adapt.

Checks:
- :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin passed.
- 115 unit tests passed, including mode authority, return-to-Home handoff, restoration, recreation and normal-text contrast for both surface palettes.
- Instrumentation sources compile; no device tests or manual emulator traversal were performed.
- git diff --check passed (existing CRLF normalization notices only).

Smoke test:
1. Home: toggle Android Dark theme on/off through Quick Settings; check background, cards, filters, footer, destination suggestions and legible text.
2. Start a trip. Confirm the circular aqua route icon is directly above and right-aligned with the hazard-report button, clear of attribution.
3. Tap once: full route overview, native Recenter hidden, accessibility label Resume following vehicle. Tap again: tilted follow returns and the normal SDK Recenter policy is restored. Pan the map to confirm SDK Recenter can appear outside overview.
4. Check navigation surfaces when the SDK map is in night and day modes, then Stop Navigation: Home should inherit that map mode. Keep Change Stop open during an SDK mode transition to check live menu recoloring.
Important: Android Dark theme does not force the automatic Navigation SDK into night mode. Google chooses its effective navigation mode from location/time (and SDK conditions); the surrounding IBS navigation colors follow the SDK callback. For a developer-only immediate mode check, the SDK exposes setForceNightMode(FORCE_DAY/FORCE_NIGHT); restore AUTO afterward. No production theme selector was added.

Official API references:
https://developers.google.com/maps/documentation/navigation/android-sdk/reference/com/google/android/libraries/navigation/NavigationView.OnNightModeChangedListener
https://developers.google.com/maps/documentation/navigation/android-sdk/reference/com/google/android/libraries/navigation/NavigationFragment
