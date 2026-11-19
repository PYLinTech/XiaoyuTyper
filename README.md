# XiaoyuTyper

XiaoyuTyper is an offline Android pinyin input method (IME) built with
Jetpack Compose and a native SQLite dictionary. It is an IME-only package:
there is no launcher activity, setup screen, test screen, or settings UI.
Enable and select it from Android's input method settings. The current
release is pre-release 0.1.0.

## Features

- Native `InputMethodService` with a Jetpack Compose keyboard.
- Chinese pinyin input backed by a preloaded 1,012,871-word dictionary and
  843,967 pinyin keys, plus an English letter mode.
- Initial abbreviation input for single characters and two- to three-character
  words (for example `nh` for 你好, `zg` or `zhg` for 中国), backed by a
  dedicated initial index.
- Daily keyboard with a candidate bar, shift and shift lock, number and symbol
  pages, extra symbols, comma and period keys, space-to-commit, and repeat
  backspace.
- Secure keyboard for password fields (`TYPE_TEXT_VARIATION_PASSWORD`,
  visible/web passwords, and numeric passwords): English-only text entry,
  symbol pages, and next/done actions.
- Light and dark palettes switched automatically with the system theme.
- Portrait dock: full-width, 28 dp composing head,
  `max(315 dp, 36% of screen height)` content height, and a bottom bar of
  `max(navigation bar inset, 48 dp)`.
- Landscape floating panel: capped at 620 x 310 dp and 55% of the landscape
  height, 2:1 aspect ratio, 8 dp body radius, 22 dp head with a draggable
  grip, and a 12 dp bottom margin. The IME reports zero insets so the host app
  is overlaid instead of resized. The last position is remembered and
  re-clamped into the current safe area when the keyboard is restored or the
  display changes.
- First-launch extraction of a bundled XZ dictionary and process-level
  preloading in `Application.onCreate`, before the keyboard UI is created.
- All UI copy comes from Android string resources. Simplified Chinese
  (`zh-CN`) is the only bundled language, and AGP generates the locale config
  so translations can be added through locale resource folders.
- No network permission; the keyboard works fully offline.

## Requirements

- JDK 17 or newer.
- Android SDK 37 (`compileSdk = 37`, `minSdk = 26`, `targetSdk = 37`).
- Android Studio with a recent stable release, or the Gradle wrapper.

The debug APK is about 33 MB and the optimized release APK is about 23 MB
because the compressed dictionary accounts for nearly all of the package size.

## Build

Debug APK:

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Signed release APK:

Create a local `keystore.properties` in the project root. It is not part of
the source tree:

```properties
storeFile=../release.jks
storePassword=your-store-password
keyAlias=your-key-alias
keyPassword=your-key-password
storeType=PKCS12
```

Then run:

```bash
./gradlew :app:assembleRelease
```

The signed APK is written to `app/build/outputs/apk/release/app-release.apk`.
The release build enables R8 code shrinking, resource shrinking, and
Chinese-only resource filtering, and signs with v2 and v3 schemes. Signing is
skipped when `keystore.properties` is absent, so the project can still build
an unsigned package without the production key.

## Install and enable

Install the APK:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Enable and select XiaoyuTyper from **Settings > System > Languages & input >
On-screen keyboards > Manage on-screen keyboards**, or use adb:

```bash
adb shell ime enable cn.pylin.xiaoyutyper/.XiaoyuInputMethodService
adb shell ime set cn.pylin.xiaoyutyper/.XiaoyuInputMethodService
```

The package has no launcher activity, so there is no home screen icon to open.
Manage the IME from Android's input method settings.

## Dictionary

The dictionary is generated from
[rime-frost](https://github.com/gaboolic/rime-frost) at commit
`60e84477cadd07db60bd29a852212f075f0814a9` (2026-11-18, GPL-3.0). It keeps all
Han-character single characters, core multi-character words with weight >= 21,
and selected cell dictionaries (idioms, history, geography, places, sports,
food, games, medication, literature, names, animals, industry, math, chess,
computer, media, music, input methods, and others). The `composite` dictionary
is excluded. Per-key candidate lists are capped at 80.

The resulting database contains 1,012,871 unique words and 843,967 keys in a
67.2 MB SQLite file. It also contains 129,997 initial-abbreviation entries
across 11,102 initial keys, covering single characters and two- to
three-character words. The database is shipped as a 23 MB XZ asset:
`app/src/main/assets/pinyin-v1.db.xz`. It is unpacked to `filesDir/pinyin-v1.db`
on first use and opened read-only. Obsolete versioned database files are
removed automatically.

Regenerate the dictionary with:

```bash
python3 tools/build_pinyin_db.py
```

Downloads are cached under `tools/.cache/`. Set `RIME_FROST_SOURCE_DIR` to a
local rime-frost checkout to build offline. `MIN_CORE_MULTI_WEIGHT`,
`MAX_PER_KEY`, and `RIME_FROST_REF` can be overridden with environment
variables.

## Engine

The Kotlin engine is a behavior port of the pinyin-ime ranking rules (MIT):

- Longest-prefix candidates first, then exact full-pinyin matches, exact
  initial matches, word length, and global frequency.
- Exact full-pinyin keys are tracked separately from prefix matches and
  receive a ranking boost.
- Exact initial matches are marked separately and receive a ranking boost,
  including single characters and short words.
- Single characters and two-character words are favored through
  complete-syllable-length inputs. Non-exact three-character and longer words
  are demoted for short inputs, while exact matches and initial abbreviations
  are exempt.
- A 300-candidate cap and a 200-entry LRU cache.
- Prefix-to-word lookup through `entries(key, word_id)` plus a precomputed
  `prefix_top(prefix, word_id, freq)` table for one- and two-letter prefixes.

## Project layout

- `app/src/main/java/cn/pylin/xiaoyutyper/XiaoyuApplication.kt`: process
  startup and dictionary preloading.
- `app/src/main/java/cn/pylin/xiaoyutyper/XiaoyuInputMethodService.kt`: IME
  service, window configuration, and input connection handling.
- `app/src/main/java/cn/pylin/xiaoyutyper/ime/`: keyboard state controllers.
- `app/src/main/java/cn/pylin/xiaoyutyper/pinyin/`: SQLite dictionary, engine,
  and repository.
- `app/src/main/java/cn/pylin/xiaoyutyper/ui/keyboard/`: Daily and Secure
  Compose keyboards.
- `app/src/main/assets/pinyin-v1.db.xz`: preloaded dictionary and initial
  abbreviation index.
- `app/src/main/assets/licenses/`: third-party license texts.
- `app/src/main/res/values/strings.xml`: localized UI strings.
- `app/src/main/res/resources.properties`: default resource locale (`zh-CN`).
- `app/src/main/res/xml/method.xml`: IME metadata and subtypes.
- `tools/build_pinyin_db.py`: dictionary builder.

## Checks

```bash
./gradlew :app:lintDebug
```

There are no unit or instrumentation test sources.

## License and notices

XiaoyuTyper source code is released under the MIT License. See
[LICENSE](LICENSE).

Third-party components:

- **rime-frost** dictionary data (GPL-3.0). The bundled SQLite dictionary is a
  modified derivative generated by `tools/build_pinyin_db.py`. The modified
  data remains licensed under GPL-3.0.
- **pinyin-ime** engine behavior (MIT). The candidate generation and ranking
  rules are a Kotlin port; no UI code is used.
- **LKWorkMate** web keyboard as the layout and interaction reference. No web
  assets are shipped.
- **Remix Icon** v4.6.0 (Apache-2.0) for the keyboard icons.
- **XZ for Java** (public domain) for first-launch dictionary extraction.
- **AndroidX, Jetpack Compose, Kotlin, and Kotlin Coroutines** (Apache-2.0).

Full third-party license texts are bundled in
`app/src/main/assets/licenses/`.
