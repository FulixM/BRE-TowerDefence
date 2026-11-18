# BRE Tower Defence

A Kotlin/libGDX tower-defense game: build a defence along either of two routes and protect the core through ten increasingly difficult waves. The simulation runs at a fixed 60 updates per second, independently of rendering.

![Actual first-mission gameplay](docs/screenshots/first-mission.png)

## Run

Requires JDK 21 and an OpenGL-capable desktop. The first build downloads Gradle and dependencies.

```sh
./gradlew lwjgl3:run
```

On Windows use `gradlew.bat lwjgl3:run`. Build a runnable desktop JAR with `./gradlew lwjgl3:jar`; it is written to `lwjgl3/build/libs/`.

## Play

The game opens on The Corridor at Normal difficulty. Use **Mission** to choose a route and difficulty. The Corridor has a long central lane and two approaches to its bends. Switchback adds a return bend, so tower positions can cover different stretches of the route.

| Difficulty | Starting gold | Base lives | Enemy health |
| --- | ---: | ---: | ---: |
| Relaxed | 260 | 30 | 80% |
| Normal | 220 | 20 | 100% |
| Veteran | 180 | 12 | 125% |

Choose a tower in the sidebar and click free ground beside the route. The preview shows range and whether placement is valid. Build at bends to cover several route segments. Select a placed tower to inspect its damage, fire rate and range, preview and buy either of its two upgrade levels, change its target priority, or sell it for 65% of its total investment. First targets the enemy furthest along the route, Strongest targets the one with the most remaining health, and Nearest targets the closest enemy. The result screen shows how many enemies your towers defeated and how many reached the core.

| Tower | Cost | Role |
| --- | ---: | --- |
| Pulse | 75 | Rapid single-target damage |
| Cannon | 120 | Splash damage against groups |
| Frost | 90 | Slows targets by 45% so other towers have more time |
| Rail | 140 | Long-range shot that ignores armor |
| Arc | 125 | Lightning jumps to two nearby enemies with falling damage |
| Venom | 100 | Poisons an enemy for three seconds |
| Inferno | 110 | Hits every enemy in its short range |
| Beacon | 85 | Marks an enemy for 2.5 seconds so it takes 25% more damage |

Start each wave when ready; there is no preparation timer. Killing enemies earns gold, clearing a wave grants a bonus, and each enemy reaching the core costs one life. Drifters arrive first, followed by Runners and Bulwarks. Ironclads reduce damage unless hit by Rail, Wardens absorb damage with shields, and Reavers regain health if left alone. Press **G** to open the enemy guide and see when each type arrives. Inspect the next wave in the bottom bar. Survive wave 10 to win; zero lives ends the mission. Building and upgrading remain available during waves. Damage resolves instantly; shot traces and impact rings provide visual feedback.

| Input | Action |
| --- | --- |
| Mouse | Choose/build/select towers and use HUD buttons |
| 1–8 | Choose a tower in catalogue order |
| G | Read the enemy guide and pause the current wave |
| U | Upgrade selected tower |
| T | Cycle the selected tower’s target priority |
| Space | Start the next wave; pause/resume during combat |
| P | Pause/resume |
| F | Toggle 1x / 2x speed |
| Right-click / Esc | Cancel placement or selection; Esc otherwise pauses |
| R | Request restart; confirm with Enter/R or cancel with Esc |

The window scales with a FitViewport and ignores clicks in letterboxing. Text is rasterized from the bundled DejaVu Sans font at the current framebuffer resolution, including HiDPI scaling; it is regenerated when the viewport density changes. Towers, gates and effects use four-sample antialiasing when available, with a supersampled battlefield fallback when the window system cannot provide it. Text stays at native resolution. Losing application focus pauses the game.

## Verify

```sh
./gradlew build test
./gradlew lwjgl3:smokeTest
```

The first command runs the simulation and campaign-balance tests. The second needs a graphical display and exercises actual rendering, input, mission selection, tower management, pause, restart and viewport scaling. On Linux CI use `xvfb-run -a ./gradlew lwjgl3:smokeTest`; the screenshot is written to `lwjgl3/build/reports/desktop-smoke.png`. CI runs both checks and uploads reports.

## Structure

- `core/.../simulation`: plain Kotlin rules, movement, combat, waves and economy; no graphics dependencies in these classes.
- `core/.../ui`: input, HUD, viewport, pause and mission screens.
- `core/.../rendering`: procedural battlefield, unit silhouettes and tick-driven combat effects.
- `lwjgl3`: desktop launcher, packaging and rendered smoke check.
- `assets`: original template graphics retained; procedural battlefield art and a bundled, openly licensed DejaVu Sans Latin font (license in `assets/fonts/LICENSE.txt`). No runtime asset downloads.

Design choices and the next priorities are in [game-design.md](docs/game-design.md). Tower values, difficulty rules and automated campaign results are in [balance.md](docs/balance.md). Missions are independent sessions.

Executed checks, limitations and team contributions are recorded in [verification.md](docs/verification.md).
