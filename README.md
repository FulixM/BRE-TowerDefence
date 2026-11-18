# BRE Tower Defence

A Kotlin/libGDX tower-defense game. Build a defence beside the route and protect the core through increasingly difficult waves.

![Actual first-mission gameplay](docs/screenshots/first-mission.png)

## Run

Requires JDK 21 and an OpenGL-capable desktop. The first build downloads Gradle and dependencies.

```sh
./gradlew lwjgl3:run
```

On Windows use `gradlew.bat lwjgl3:run`. Build a runnable desktop JAR with `./gradlew lwjgl3:jar`; it is written to `lwjgl3/build/libs/`.

## Play

Use **Mission** to choose a route and difficulty. The mission screen previews the route, starting resources and enemy health. Place towers on free ground beside the route. Select a tower to see its stats, preview its next upgrade, change its target priority or sell it.

Start each wave when ready. The bottom bar previews the next wave, and the enemy guide explains when each enemy type appears and how to counter it. Survive all waves to win.

| Input | Action |
| --- | --- |
| Mouse | Choose/build/select towers and use HUD buttons |
| 1–8 | Choose a tower in catalogue order |
| M | Open mission setup |
| G | Read the enemy guide and pause the current wave |
| E | Arm or disarm the selected tower as the wave recorder |
| U | Upgrade selected tower |
| T | Cycle the selected tower’s target priority |
| Space | Start the next wave; pause/resume during combat |
| P | Pause/resume |
| F | Toggle 1x / 2x speed |
| Right-click / Esc | Cancel placement or selection; Esc otherwise pauses |
| R | Request restart; confirm with Enter/R or cancel with Esc |

Before starting a wave, select a tower and use **Record [E]**. Its shots replay at the same times and positions in the next wave, even if you sell the tower. Enemies can move past a point before the echo fires. Purple rings mark upcoming impacts.

## Verify

Run the test closest to the change.

| Change | Command |
| --- | --- |
| Wave echo | `./gradlew --quiet --console=plain :core:test --tests '*WaveEchoTest'` |
| Campaign balance | `./gradlew --quiet --console=plain :core:test --tests '*BalanceSimulationTest'` |
| HUD, input or rendering | `./gradlew --quiet --console=plain :lwjgl3:smokeTest` |
| Full build and tests | `./gradlew --quiet --console=plain build` |

The desktop smoke test needs a display. On headless Linux, prefix the command with `xvfb-run -a`. The screenshot is written to `lwjgl3/build/reports/desktop-smoke.png`. PR CI runs the full build and the desktop smoke test, and uploads available diagnostics on failure.

## Structure

- `core/.../simulation`: plain Kotlin rules, movement, combat, waves and economy; no graphics dependencies in these classes.
- `core/.../ui`: input, HUD, viewport, pause and mission screens.
- `core/.../rendering`: procedural battlefield, unit silhouettes and tick-driven combat effects.
- `lwjgl3`: desktop launcher, packaging and rendered smoke check.
- `assets`: original template graphics retained; procedural battlefield art and a bundled, openly licensed DejaVu Sans Latin font (license in `assets/fonts/LICENSE.txt`). No runtime asset downloads.

See [AGENTS.md](AGENTS.md) for entry points, [game-design.md](docs/game-design.md) for design decisions, [balance.md](docs/balance.md) for campaign probes, and [verification.md](docs/verification.md) for first-mission verification.
