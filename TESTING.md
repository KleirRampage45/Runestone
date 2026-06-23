# Runestone v0.9 — Testing Checklist

> Probar en dispositivo físico Android 14+ (arm64-v8a)

---

## 1. First Launch (Onboarding)

- [ ] App shows onboarding wizard on first launch (not home screen)
- [ ] Language selection works: English / Español / Português
- [ ] All 3 languages render correctly (no mojibake, no truncated text)
- [ ] Engine toggles enable/disable correctly (mkxp-z, easyrpg, onscripter, renpy, etc.)
- [ ] RAWG API key input accepts text, link opens browser
- [ ] "Install VX Ace RTP" toggle present
- [ ] "START PLAYING" button completes onboarding and transitions to home
- [ ] On second launch, onboarding is skipped (home screen appears directly)
- [ ] Chosen locale persists across app restarts

## 2. Home Screen

- [ ] Installed games appear as cards
- [ ] Empty state: "No games installed" message when no games present
- [ ] Search bar works with debounced filtering (300ms delay)
- [ ] Search clear button (X) resets filter
- [ ] Sort modes cycle correctly: Date Added → Name A-Z → Name Z-A → Recently Played
- [ ] Engine filter: tap to cycle through installed engine types
- [ ] Card layout toggle: 2-column → 3-column → Wide
- [ ] Long-press on card shows inspect overlay
- [ ] RESUME banner appears when a game is paused
- [ ] STOP button on RESUME banner kills the game session
- [ ] Dock bar buttons work: Home, Add, Browse, Manage, Settings

## 3. Game Import (SAF)

- [ ] Tap + on dock → opens folder browser
- [ ] Folder navigation works (drill in, go up, breadcrumbs)
- [ ] Import detection works for:
  - [ ] RPG Maker MV (www/index.html + www/data/System.json)
  - [ ] RPG Maker MZ (.rmmzproject)
  - [ ] RPG Maker VX Ace (.rvproj2 or .rgss3a)
  - [ ] RPG Maker VX (.rvproj or scripts.rvdata)
  - [ ] RPG Maker XP (.rxproj or scripts.rxdata)
  - [ ] EasyRPG 2000/2003 (RPG_RT.exe + .ldb/.lmt)
  - [ ] ONScripter (0.txt or nscript.___)
  - [ ] Ren'Py (game/ or renpy/ folder)
  - [ ] TyranoBuilder (data.ks or first.ks)
  - [ ] Flash/SWF (.swf file)
- [ ] Progress bar shows during import
- [ ] Import failure shows error message
- [ ] RTP download dialog appears when VX Ace game imported without RTP
- [ ] Game appears in home screen after successful import

## 4. Game Launch

- [ ] MV game launches in WebView
- [ ] MZ game launches in WebView
- [ ] VX Ace game launches via mkxp-z
- [ ] VX game launches via mkxp-z
- [ ] XP game launches via mkxp-z
- [ ] EasyRPG 2000/2003 game launches
- [ ] ONScripter game launches
- [ ] Ren'Py game launches
- [ ] TyranoBuilder game launches
- [ ] Flash/SWF game launches via Ruffle
- [ ] UNKNOWN engine shows "trying WebView" toast

## 5. Runtime Controls (WebView games)

- [ ] Touch overlay appears over WebView game
- [ ] D-pad works: UP/DOWN/LEFT/RIGHT
- [ ] A/B buttons trigger confirm/cancel
- [ ] Settings button (...) opens runtime menu
- [ ] Home button pauses game and returns to launcher
- [ ] Runtime menu: RESUME returns to game
- [ ] Runtime menu: HOME pauses and goes to launcher
- [ ] Runtime menu: CONTROLS ON/OFF toggles overlay
- [ ] Runtime menu: BASIC/FULL toggles controller preset
- [ ] Runtime menu: PORTRAIT/LANDSCAPE rotates layout
- [ ] Runtime menu: KEYBOARD opens virtual keyboard
- [ ] Layout rotation works (portrait console split, landscape 4:3)
- [ ] Control layout editor (EDIT) allows drag repositioning
- [ ] Control layout persists across game relaunches

## 6. Controller Support

- [ ] Physical gamepad: D-pad navigates home screen
- [ ] Button A = select, Button B = back
- [ ] START = import, SELECT = manage, X = store, Y = filter
- [ ] L1/R1 cycle card layout / sort
- [ ] MODE = settings
- [ ] Controller combo (L2+R2) resumes paused game
- [ ] Controller navigation auto-enables on first controller input

## 7. Store / Catalogue

- [ ] Browse button shows Available Games screen
- [ ] Default catalogue loads (bundled JSON)
- [ ] Game cards show title, engine badge, download button
- [ ] Download starts and shows progress
- [ ] Pause/Resume download works
- [ ] Download completes → extraction starts → game installed
- [ ] Installed badge appears on store cards
- [ ] Refresh button reloads catalogue
- [ ] Add Source: custom catalogue URL
- [ ] Manage Sources: remove sources
- [ ] Provider Settings: switch to public catalogue

## 8. Manage Games

- [ ] Manage screen shows all installed games with storage info
- [ ] Per-game settings: opens PerGameSettingsScreen
- [ ] Hero card cover: pick custom image
- [ ] Metadata: fetch from RAWG (with API key)
- [ ] Metadata: edit title, developer, publisher, genres, year, description
- [ ] Input section: layout mode, hide gamepad, diagonal, X/Y buttons, haptics
- [ ] Controller Profile section: preset selector, L1/R1, L2/R2 toggles
- [ ] Video section: FPS, VSync, integer/smooth scaling, brightness, contrast, gamma
- [ ] Audio section: mute toggles, volume sliders
- [ ] Performance section: threaded rendering, background loading, shadows, particles
- [ ] Fonts section: use game fonts, bold, italic, scale, line spacing
- [ ] Patch install: select ZIP file, applies patches with backup
- [ ] Delete game: Keep Saves / Delete Fully options
- [ ] View Saves: shows save file list
- [ ] Save Actions: sync, backup, restore, export ZIP, import ZIP, view backups

## 9. Settings

- [ ] DISPLAY section: layout mode, UI mode, smooth/integer scaling, text scale, keep screen on
- [ ] GAMEPAD section: hide gamepad, diagonal, haptics, button opacity/scale, mapping
- [ ] AUDIO section: audio extension, emulation
- [ ] RGSS section: dialog logs, ruby18, vsync, frame skip, shaders
- [ ] MV/MZ section: WebGL, canvas, HTTP server, desktop mode
- [ ] HTML section: renderer, quality, scale mode
- [ ] APPLICATION section: Theme toggle (Dark/Light/System)
- [ ] Color Palette: Amber, Emerald, Royal, Crimson, Ocean, Monochrome
- [ ] RAWG API Key input
- [ ] RUNTIME PACKAGES: RTP install button
- [ ] HELP & ABOUT section renders
- [ ] Settings persist across app restart

## 10. Theme System

- [ ] Default theme is Dark
- [ ] Toggle to Light theme in Settings → APPLICATION → Theme
- [ ] Light theme: backgrounds are light/white, text is dark
- [ ] Dark theme: backgrounds are near-black, text is light
- [ ] System mode follows device dark/light setting
- [ ] Theme change applies immediately (overlay rebuilds)
- [ ] All 9 screens use correct theme colors:
  - [ ] HomeScreen
  - [ ] SettingsScreen
  - [ ] AvailableGamesScreen
  - [ ] ManageFilesScreen
  - [ ] SourcesScreen
  - [ ] ProviderSettingsScreen
  - [ ] GameFolderBrowserScreen
  - [ ] GameDetailOverlay
  - [ ] ImportProgressScreen

## 11. i18n / Locale

- [ ] English: all UI strings in English
- [ ] Español: all UI strings in Spanish
- [ ] Português: all UI strings in Portuguese
- [ ] Locale persists after app restart
- [ ] No hardcoded English strings visible when using ES/PT
- [ ] Special characters render correctly (ñ, ç, á, é, í, ó, ú, â, ê, ô, ã, õ)

## 12. Cover Art

- [ ] RAWG cover art appears when API key is set
- [ ] Fallback cover from game files:
  - [ ] MV/MZ: www/img/titles1/*.png
  - [ ] RGSS: Title.png in game root
  - [ ] .rpgmvp decoded correctly
- [ ] Custom cover via per-game settings works
- [ ] Fallback thumbnail is scaled to max 480px

## 13. Play Stats / Session Tracking

- [ ] Play time increments while game is running
- [ ] Play time persists across app restarts
- [ ] Recently Played sort works
- [ ] RESUME banner shows for paused games
- [ ] STOP button on RESUME banner records play time

## 14. RTP Installer

- [ ] VX Ace RTP download triggers when importing a game that needs it
- [ ] Download progress shows percentage
- [ ] Extraction progress shows file count
- [ ] Installed RTP is detected on subsequent imports
- [ ] Innoextract JNI extracts .exe archive correctly

## 15. Error Handling

- [ ] Missing game directory shows toast and returns to home
- [ ] Import failure shows error and does not crash
- [ ] Download failure shows error notification
- [ ] Extraction failure shows error and cleans up temp files
- [ ] Unknown engine gracefully shows "trying WebView"
- [ ] Kill signal from STOP button terminates GameActivity
- [ ] Activity recreation (config change) doesn't crash

## 16. Performance

- [ ] Home screen scroll is smooth (no jank)
- [ ] Game list sorting/filtering is instant (<16ms)
- [ ] Import progress doesn't block UI
- [ ] Download doesn't block UI
- [ ] Splash screen dismisses within 500ms of game scan completion
- [ ] Play stats read doesn't trigger disk I/O (uses in-memory cache)
- [ ] Game size cache read doesn't trigger disk I/O
- [ ] No `runBlocking` on main thread (verify with StrictMode)

## 17. Regression Tests

- [ ] All existing game imports still work (MV, MZ, VX Ace, XP, EasyRPG, ONScripter, Ren'Py)
- [ ] Save/load still works in all engines
- [ ] Audio still works in all engines
- [ ] Controller still works in all engines
- [ ] RTP still works for VX Ace games
- [ ] Store downloads still work
- [ ] Patch system still applies patches
