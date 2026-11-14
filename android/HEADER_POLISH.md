# Header and banner polish

Navigation now has separate Final destination and Restroom stop labels (12sp) above prominent 17sp location names. No midway stop reads None selected. Names retain two-line wrapping/ellipsis. Stop Navigation and Change Stop share equal-width, matching-height pill backgrounds, 8dp spacing and cobalt/white contrast in both day/night palettes. Their ripple feedback is retained.

The separate Simulated navigation TextView and its visibility code have been removed. The existing simulator itself is unchanged. Redundant active-guidance copy remains hidden; errors, loading/retry recovery and map-only instructions remain. Restroom arrival explicitly tells the user to tap Continue to destination.

The overview ImageButton is now 72dp. The supplied PNG has an inset circular outline, giving approximately a 63dp visible circle rather than the earlier smaller vector circle. Its right margin is 8dp and bottom margin 80dp, above the SDK hazard control. Its artwork is centered and its original proportions retained. Overview/follow behavior, accessibility labels, Recenter policy, Google attribution and SDK guidance are unchanged.

The Home banner uses a 76×96dp toilet-pin area and a weighted 96dp-high wordmark area with ContentScale.Fit, 8dp separation and 12dp horizontal/8dp vertical padding. The exact supplied I Better Stop wordmark replaces the old IBS artwork; the PNG's original colors/background and proportions are preserved in both themes. The containing banner retains the shared light/slate theme.

Source artwork found in:
C:/Users/taylo/OneDrive/Desktop/.final semester!/CSC 490/I Better Stop royal blue wordmark.png
C:/Users/taylo/OneDrive/Desktop/.final semester!/CSC 490/Cyan route overview button.png

The originals were not modified. Because IDE workspace write APIs accept text, the original PNG bytes are packaged losslessly as raw/ibs_wordmark.b64 and raw/ibs_overview.b64. BrandAssets decodes/downsamples/caches them off the main thread. These are original artwork imports, not font reconstructions or replacement illustrations. Existing splash playback and pin reuse remain intact.

Verification:
- :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin passed.
- 117 unit tests passed, including original PNG byte count/dimension checks.
- Instrumentation sources compile; not executed.
- git diff --check passed (existing CRLF normalization notices).
- No manual emulator testing. Check pill shape, header name wrapping, image alignment, visible icon diameter, attribution/hazard clearance, actionable arrival/errors and both themes on device.
