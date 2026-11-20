# Puzzle Nook

An Android app with 31 logic puzzles that runs offline. It is made for phones with a stylus and for colour
e-ink tablets. Every puzzle is generated on the device and can be solved by step-by-step logic,
with no guessing needed.

## Puzzles

| Group | Games |
|---|---|
| Digits | Sudoku, Killer Sudoku, Jigsaw Sudoku, Kakuro, Futoshiki, Calcudoku, Skyscrapers, Kropki, Suguru, Hidato |
| Shading | Nonogram, Star Battle, Hitori, Nurikabe, Takuzu, Mosaic, Light Up, Tents, Kurodoko, Norinori, Battleships, Aquarium, Heyawake, LITS |
| Lines | Slitherlink, Masyu, Yajilin, Bridges (Hashi) |
| Regions | Shikaku, Fillomino, Galaxies |

## Features

- Four difficulties (Easy, Medium, Hard, Expert). Each puzzle is generated and checked against
  that game's logic solver, and harder levels need harder techniques.
- Hints that point to the next cell the logic can settle. Optional error marks, plus help limits
  and time penalties for scoring.
- Handwritten digit input with a stylus (S Pen; low-latency pen on Onyx Boox devices).
- E-ink theme: flat greys, no animation, and wrong cells marked without relying on red.
- Statistics and win history for each game and difficulty, and saved games for every puzzle.
- English and Slovene.

## Building

Requirements: JDK 17 or newer and the Android SDK (compileSdk 36). The app runs on Android 8.0
(API 26) or newer.

```powershell
.\gradlew.bat :app:assembleDebug        # debug APKs (arm64, x86_64)
.\gradlew.bat :app:testDebugUnitTest    # unit tests
.\gradlew.bat :app:assembleRelease      # unsigned release APKs
```

On Linux or macOS use `./gradlew`. APKs land in `app/build/outputs/apk/`, and there is one per CPU
type. Sign a release APK with `apksigner` before you install it.

## Project layout

The whole app lives in one module, `app`, with the package `com.puzzlenook`. Each family of games has its own
package (`sudoku`, `shade`, `slither`, `region`, `bridges`, `nonogram`, `hidato`) with its
generator, logic solver and state. The Compose screens and boards are in `ui`. Unit tests mirror
those packages under `app/src/test`.

## License

Copyright (C) 2026 OBELIKS

This program is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version. See [LICENSE](LICENSE).

The app uses third-party libraries under their own licenses. Two of them are closed source:
Google ML Kit Digital Ink Recognition and the Onyx Boox SDK.
