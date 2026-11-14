# IBS branding handoff

Shared palette in ui/theme/Color.kt (IbsBrand) feeds both Compose and the native navigation UI. Wallpaper-derived dynamic colors no longer replace the IBS palette. Light mode uses #F3F8FC, navy text, cobalt actions, white business cards and blue-gray borders. Dark mode retains branded colors with light text on dark surfaces; business cards remain white with navy content. Selected preference chips use pale aqua and navy with cobalt borders; interactive target sizes remain intact.

Home retains the existing weighted scrolling list and fixed, IME-aware footer. Go Here! is a cobalt outline; Let's Go is a full-width cobalt action. Suggestion position logic, location, filtering and route behavior are unchanged.

No separate pin or wordmark asset exists in the repository. IbsBrandHeader reads a still from the existing bundled splash video on an IO dispatcher, preserves the original cobalt pin/wordmark pixels and white toilet, separates them at the blank gap and caches them for the process. It does not replay or modify the splash. The supplied screenshot guided the artwork region; actual header appearance must be checked by the user. A supplied transparent source logo would allow replacing this extraction with a static drawable later. Extraction failure falls back to an IBS wordmark.

Navigation SDK dependency remains 8.1.0. IbsNavigationStyle uses supported NavigationFragment.setStylingOptions with cobalt primary and navy secondary backgrounds for BOTH day and night. Instructions, next step, distance and both maneuver icons are white. SDK foreground setters are shared across day/night; there are no separate day/night foreground setters in this styling surface. Map, route line, ETA and camera behavior are retained.
Official API: https://developers.google.com/maps/documentation/navigation/android-sdk/reference/com/google/android/libraries/navigation/StylingOptions

App-owned header shows only final destination and restroom names when available. Native controls use the same shared palette; Change Stop results have white rounded cards, navy text and blue-gray borders.

Verification:
- :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin passed.
- 104 unit tests passed, including contrast checks; instrumentation sources compile but were not run.
- White on cobalt: 6.30:1. White on navy: 15.38:1. Navy on selected pale aqua and light background exceeds 7:1.
- git diff --check passed (Git reports existing LF/CRLF normalization notices).
- No manual emulator launch or UI traversal. Header artwork extraction, dropdown/keyboard geometry and actual SDK-rendered day/night banners remain for the user's smoke test.
