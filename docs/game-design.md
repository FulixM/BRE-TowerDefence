# BRE Tower Defence: first playable mission

## Direction

Keep the existing Kotlin/libGDX/KTX desktop project, fixed simulation step and waypoint map. The first milestone is one complete, replayable defence mission. A player should make a useful decision in the first minute and understand why a defence succeeds or fails.

The original assets are project-template logos rather than established game art. Distinct procedural shapes and a consistent tactical-board presentation can therefore establish the game's visual identity without paid assets or an asset pipeline.

## Core loop

1. Inspect the route, starting resources and next wave.
2. Choose a tower and place it beside the route; preview its range and placement validity.
3. Start the wave when ready. Watch enemies, health and attacks to identify weak coverage.
4. Spend kill rewards and wave-completion income on additional coverage or upgrades. Selling allows a partial recovery from mistakes.
5. Defend through the final wave to win. Enemies reaching the exit consume base lives; losing every life ends the mission.
6. Restart directly to try a different composition and placement.

Preparation has no timer. This creates room to learn and compare choices without demanding fast mouse input. Pause and faster playback change the pace rather than the rules.

## Strategic roles

| Role | Strength | Trade-off | Readable feedback |
| --- | --- | --- | --- |
| Rapid | Reliable, frequent single-target damage | Cannot damage a whole group at once | Frequent narrow shots |
| Cannon | Area damage against dense groups | Slow attacks and greater initial investment | Broad impact burst |
| Frost | Slows enemies to extend nearby damage towers' firing time | Low direct damage; needs supporting damage | Blue shots and a visible slowed status |
| Standard enemy | Teaches route and damage expectations | No special ability | Baseline shape and speed |
| Runner | Tests coverage and timely damage | Lower health than a tank | Smaller silhouette and faster movement |
| Bulwark | Tests sustained damage and upgrade investment | Slow enough to reward good route coverage | Larger silhouette and health pool |

Roles should be distinguished by both shape and colour. Colour alone is not enough. Range previews, health bars, attack effects and selection indicators explain the simulation without requiring a debug console.

Use stable target selection. Prioritising enemies furthest along the route makes tower behaviour predictable near the exit. Ties should resolve consistently. Slowing should refresh a bounded effect rather than multiply indefinitely; a player should benefit from mixing support and damage instead of building a permanent freeze lock.

## Progression and economy

Introduce threats gradually: standard enemies first, runners after the player has learned to build, tanks after income makes additional damage practical, then denser mixed waves. Later waves combine existing demands rather than introducing unreadable immunities.

Gold has three clear uses: more coverage, stronger existing towers, or a different composition. Upgrades should have a fixed cap and visible costs. Selling refunds only part of the total investment, so experimenting is possible while repeated rebuilds still have a cost. Kill rewards and a completion bonus give concrete progress; passive interest and speed bonuses are outside this milestone.

## Technical and player-facing acceptance criteria

- A complete mission reaches either victory or defeat and can restart without restarting the application.
- Invalid, overlapping, on-road and unaffordable builds do not spend gold.
- Upgrades and sales can only affect an existing tower, and an enemy rewards gold or costs lives at most once.
- A dead or escaped enemy cannot be selected or attacked and is eventually removed from active state.
- Fixed-step movement follows the route exactly, including corners and multiple crossed waypoints.
- Pausing freezes gameplay; window resizing preserves alignment between map, placement preview and pointer input.
- Buttons communicate unavailable actions and basic controls remain discoverable on screen.
- A deterministic baseline defence can complete the mission; no defence eventually loses. These are regression checks, not proof of perfect balance.
- Gameplay tests run without an OpenGL window. Desktop rendering, pointer interaction and legibility require separate visual playtesting.

## Deliberate limits and next steps

This milestone does not add a campaign, permanent unlocks, loot, multiplayer, procedural maps, elemental resistance, or a new entity framework. These systems would multiply balancing and interface work before the base game is proven.

Prioritised follow-up:

1. Use repeated player sessions and deterministic scenario comparisons to tune upgrade value, mixed compositions and wave difficulty.
2. Add original sound cues and music with volume controls. Keep every cue understandable without audio.
3. Consider mission achievements and a small campaign after player sessions establish which maps and strategies support replay.
