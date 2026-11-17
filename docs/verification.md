# Verification — first playable mission

Verified on 2026-11-17 with JDK 21, Gradle 9.6.1, Kotlin 2.4.0 and the project's declared libGDX/KTX dependencies. No production framework or engine was added.

## Executed checks

- `./gradlew clean build test lwjgl3:smokeTest` completed successfully after a clean rebuild.
- 35 simulation tests discovered and executed: Enemy 8, Tower 6, FixedStepLoop 5, GameSimulation 12, BalanceSimulation 4. Zero failures, errors or skipped tests.
- Runnable desktop JAR and desktop distribution archives built.
- The desktop smoke check ran the real LWJGL/OpenGL game on a virtual Xorg display using Mesa software rendering. It asserted invalid and valid placement, gold deductions, upgrade, sale and refund, wave start, pause/resume with frozen ticks, restart confirmation/cancellation/reset, window resize, coordinate mapping and rejection of letterbox clicks.
- A rendered screenshot was captured and visually inspected. It shows the actual game, not a mockup.
- Independent code review covered the simulation, economy, terminal states, input/modal handling, rendering and resource disposal.

The constrained execution environment initially lacked a JDK, cached Gradle distribution and a display. The required JDK/Gradle were downloaded outside the repository, the environment's per-process proxy was supplied to Gradle, and Xorg was started alongside the smoke check. These environment adaptations do not alter project dependencies or require special repository configuration. CI uses JDK 21 and `xvfb-run` for the same tasks.

## Balance probes

An undefended mission loses in wave 3. A single Pulse upgraded whenever affordable loses in wave 6. A mixed, budget-constrained 12-position plan survives all ten waves with 20 lives and 410 gold. Repeated identical orders produce identical results.

These checks establish an achievable first mission and catch major regressions. They do not prove every strategy is equally effective or replace extended human playtesting. This development cycle used automated desktop interactions and visual inspection; it did not include a prolonged human play session or testing on Windows/macOS/HiDPI hardware.

## Team contributions

| Role | Contribution |
| --- | --- |
| Game Designer | Closed gameplay loop, tower roles, trade-offs, design document and acceptance criteria |
| Gameplay Programmer | Movement fixes, deterministic combat, waves, economy, tower management, end states; virtual desktop setup |
| UI/UX Designer | HUD, mouse/keyboard input, placement/inspection/upgrade/sale, modals, scaling and letterbox handling |
| Visual & Effects Designer | Procedural field, unit silhouettes, range previews, health/slow indicators and tick-driven hit effects |
| Balance & Player Experience | Budget-constrained full-mission strategies, replay tests and balance documentation |
| Software Architect & Quality Engineer | Independent review, 31 rules tests and real rendered desktop smoke test |
| Creative Director & Product Owner | Shared scope, prioritization, cross-discipline decisions and final product review |
| Lead | Integration, build/test configuration, CI, documentation, final clean build and pull request preparation |

The environment supports six simultaneous subagents plus the lead, so the seventh specialized role followed the initial direction pass. Code contributions used isolated worktrees and branches before integration.

## Next priorities

1. Human playtests and comparisons of several competitive build plans; tune waves using those results.
2. Exact before/after upgrade stats and an in-game enemy legend.
3. A second authored map and an explicit difficulty selector.
4. Original sound effects with volume controls.
5. Visible selectable targeting priorities and later progression, once the core mission is validated.
