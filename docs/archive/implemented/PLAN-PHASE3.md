# Phase 3 — Static Game Catalogue Integration

> **STATUS (2026-06-12): ARCHIVED — IMPLEMENTED.** Static catalogue
> integration shipped via `feat/provider-catalogue-settings` (June 2026).
> The `ProviderSettingsScreen` rewrite happened as part of that branch.

Branch: feature/phase12-game-provider
Catalogue URL: https://raw.githubusercontent.com/KleirRampage45/runestone-catalogue/main/games.json

## What to change

### 1. SourcesManager.kt — Add static catalogue support

Modify /home/asukate/Development/Runestone/app/src/main/java/com/runestone/app/provider/SourcesManager.kt:

Add a `fetchGamesFromCatalogue()` method that:
- Takes a URL String parameter
- Creates a URL from it, opens HttpURLConnection
- Parses the response as JSON object with a "games" array
- Maps each game object to AvailableGame
- Returns List<AvailableGame> via callback
- Runs on background thread
- Handles errors gracefully

Add a helper to detect static URLs:
```kotlin
fun isStaticCatalogueUrl(url: String): Boolean = 
    url.endsWith(".json") || url.contains("raw.githubusercontent.com")
```

Modify `fetchGamesFromSources()` to:
- If apiUrl is a static catalogue URL, use fetchGamesFromCatalogue() directly
- If not, use the existing /games?source= approach
- This maintains backward compatibility

### 2. ProviderSettingsScreen.kt — Simplify for static catalogue

Rewrite /home/asukate/Development/Runestone/app/src/main/java/com/runestone/app/ui/ProviderSettingsScreen.kt:

- Add a "Use Public Catalogue" accent button at the top that sets the default URL
- Input field for API URL with clear label: "Game Catalogue URL"
- Explanation text: "Paste a raw JSON URL or REST API endpoint"
- Current URL display with edit
- "Clear Sources" button
- "How to get a catalogue" help text pointing to the GitHub repo

### 3. AvailableGamesScreen.kt — Clean up

Check /home/asukate/Development/Runestone/app/src/main/java/com/runestone/app/ui/AvailableGamesScreen.kt:

- Verify the "GET" button triggers handleDownload properly
- The download states from Phase 2 should be displayed correctly on cards
- Loading states should clear after catalogue fetch

### 4. MainActivity.kt — Add default catalogue constant

Add:
```kotlin
companion object {
    const val DEFAULT_CATALOGUE_URL = "https://raw.githubusercontent.com/KleirRampage45/runestone-catalogue/main/games.json"
}
```

Create a `setupDefaultCatalogue()` method that auto-configures if no URL is set.

## BUILD & VERIFY

- cd /home/asukate/Development/Runestone && ./gradlew :app:assembleDebug
- Fix any errors
- adb install -r app/build/outputs/apk/debug/app-debug.apk
