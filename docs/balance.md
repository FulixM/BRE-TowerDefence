# Balance and player experience

Preparation is untimed so players can inspect the route and plan investments. Coverage at bends rewards placement. Support towers extend or amplify damage, while attack towers handle isolated targets or groups. Partial resale makes it possible to rework a defence at a cost.

For current values, see [Tower.kt](../core/src/main/kotlin/io/felixmenzel/towerdefence/simulation/Tower.kt), [Enemy.kt](../core/src/main/kotlin/io/felixmenzel/towerdefence/simulation/Enemy.kt), [Mission.kt](../core/src/main/kotlin/io/felixmenzel/towerdefence/simulation/Mission.kt), and [Waves.kt](../core/src/main/kotlin/io/felixmenzel/towerdefence/simulation/Waves.kt).

## Automated campaign checks

[BalanceSimulationTest](../core/src/test/kotlin/io/felixmenzel/towerdefence/simulation/BalanceSimulationTest.kt) holds the executable plans and coordinates. It checks that an undefended base and a lone upgraded Pulse lose, a mixed defence can win on both maps at every difficulty, and repeated runs produce identical outcomes.

The reference plans use public commands and earned gold. They build in a fixed order between waves, then upgrade in construction order. The short Switchback probe stops after four towers and continues buying upgrades. Each wave has a simulated timeout.

## Crosswind Pass and Vanguard

Crosswind Pass trades long right-angle bends for two diagonal ramps. The reference plan covers the entry before investing near the middle lane. On Veteran it survives the full campaign with seven lives without echo assistance.

Vanguards arrive in the final two waves. Sunder removes shielding faster, while Rail handles armor. Regular damage remains useful once the shield is gone.

## Original fifteen-wave campaign on Corridor and Switchback

The final formations rotate the main threats to reward different parts of a mixed defence. Enemy health keeps rising, but more slowly than in the opening. The original ten waves retain their timing, composition and health.

In deterministic probes, both reference plans won at all difficulties. Relaxed and Normal retained all lives. Veteran finished with ten lives on each map after losing two in the Armored Column. Limiting the plan to eight towers still wins both maps on Normal but loses in waves 12–13 on Veteran.

## Historical results from the ten-wave expanded roster

The reference plans beat both maps at every difficulty without losing lives. They finished with 419 gold on Relaxed, 447 on Normal, and 411 on Veteran. The four-tower Switchback plan won on Relaxed with 22 lives, lost in wave 10 on Normal, and lost in wave 9 on Veteran.

These probes show that a mixed defence works and early expansion matters. Player sessions are needed to compare alternative builds and assess how easily players discover effective placements.

## Historical results with the three-tower roster

Corridor / Normal baseline:

| Strategy | Result | Last wave | Lives | Gold remaining |
| --- | --- | ---: | ---: | ---: |
| No defense | Defeat | 3 | 0 | 285 |
| One Pulse, upgraded whenever affordable | Defeat | 6 | 0 | 686 |
| Mixed 12-position build plan, then upgrades | Victory | 10 | 20 | 410 |

Full mixed plans by mission:

| Map | Difficulty | Full mixed plan | Lives remaining | Gold remaining | Simulation ticks | Undefended base |
| --- | --- | --- | ---: | ---: | ---: | --- |
| Corridor | Relaxed | Victory | 30 | 354 | 11,526 | Defeat |
| Corridor | Normal | Victory | 20 | 410 | 13,783 | Defeat |
| Corridor | Veteran | Victory | 12 | 370 | 16,080 | Defeat |
| Switchback | Relaxed | Victory | 30 | 354 | 15,485 | Defeat |
| Switchback | Normal | Victory | 20 | 410 | 16,437 | Defeat |
| Switchback | Veteran | Victory | 12 | 370 | 20,355 | Defeat |

Four-tower Switchback probe:

| Difficulty | Four-tower result | Lives remaining | Escaped enemies |
| --- | --- | ---: | ---: |
| Relaxed | Victory | 25 | 5 |
| Normal | Victory | 5 | 15 |
| Veteran | Defeat | 0 | 12 |
