# Banner assets and daytime overview update

- Replaced the packaged wordmark with the exact original Wide royal-blue I Better Stop wordmark.png from C:/Users/taylo/OneDrive/Desktop/.final semester!/CSC 490. Its transparent background and original 2087×754 proportions are preserved.
- Reduced banner vertical padding from 8dp to 2dp (112dp total content height to 100dp). The toilet pin remains 76×96dp and centered. Banner width and fixed footer layout are unchanged.
- Imported the exact Minimalist route pin map button.png as raw/ibs_overview_day.b64. It is used only when the SDK reports effective day mode. The existing black/aqua image remains the night icon.
- Native mode callbacks refresh the button artwork while navigation is open. Generation checks reject an image decode completed for an older mode.
- Original source files were not modified. Assets use the existing lossless base64 raw resource import and background decode/cache; no image regeneration or font recreation.
- Build :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin passed. 118 unit tests passed, including byte counts and PNG dimensions for both route icons and the new wordmark. No manual emulator testing.
