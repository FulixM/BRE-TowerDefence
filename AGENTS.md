# BRE Tower Defence

Kotlin/libGDX desktop game. Use the Gradle wrapper and JDK 21.

## Where to look

Production packages below are under `core/src/main/kotlin/io/felixmenzel/towerdefence/`.

- `simulation/`: game rules. Start with `GameSimulation.kt`, then open only the relevant rule file.
- `simulation/Tower.kt`, `Enemy.kt`, `Mission.kt`, `WaveEcho.kt`: roster, difficulty, routes and echo rules.
- `ui/DefenseScreen.kt`: input, screen lifecycle and HUD coordination.
- `ui/TowerPanel.kt`, `MissionSetupPanel.kt`, `EnemyGuidePanel.kt`: individual panels. `HudCanvas.kt` draws them and handles clicks.
- `rendering/`: battlefield graphics. `lwjgl3/`: desktop launcher and smoke test.
- Core tests mirror the production packages under `core/src/test/kotlin/`.
- `README.md` covers setup and controls. `docs/balance.md` records experiments. Current rules and values live in Kotlin.

## Day-to-day work

- Start with the relevant files and test. Expand the search when tracing a dependency.
- Run the focused test first, e.g. `./gradlew --quiet --console=plain :core:test --tests '*WaveEchoTest'`.
- For UI, input or rendering changes, run `./gradlew --quiet --console=plain :lwjgl3:smokeTest` with a display (or Xvfb).
- For broad local checks use `./gradlew --quiet --console=plain build`. PR CI runs the build, tests and desktop smoke check.
- Keep test reports and screenshots in build output. Summarize checks and surface relevant errors.
- Use a feature branch and a focused PR. Keep the description to the change and its verification.
