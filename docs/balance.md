# Balance and player experience

Each mission is deliberately finite: ten manually started waves. Normal starts with 20 base lives and 220 gold. Preparation has no timer. A player can inspect the route, choose investments and read the next wave composition before committing. Building, upgrading and selling remain possible during a wave.

## Tower roles

| Tower | Cost | Base range | Damage | Attack period | Purpose |
| --- | ---: | ---: | ---: | ---: | --- |
| Pulse | 75 | 135 | 12 | 0.30 s | Affordable, reliable damage against an individual target |
| Cannon | 120 | 155 | 34 | 1.10 s | Damage to every enemy within 58 units of the impact |
| Frost | 90 | 125 | 6 | 0.70 s | Reduces the target's speed by 45% for 1.5 seconds |

Frost effects refresh without multiplying. A support-only defense cannot replace damage towers. Cannon's lower single-target damage per second rewards groups; Pulse is the cheaper choice against isolated targets. The default First priority selects the enemy furthest along the route. Strongest selects by remaining health, and Nearest by distance to the tower. All three resolve ties by enemy ID.

Two upgrades are available per tower. Each adds 14 range and 65% of base damage, and reduces the attack interval by 8% of its base value. Upgrade prices are 80%, then 130% of the tower's base price (rounded to whole gold). Selling refunds 65% of the total investment, rounded down. This lets players correct an investment while retaining a cost for repositioning; repeatedly selling and rebuilding cannot generate gold.

## Enemy and wave progression

| Enemy | Base HP | Speed | Kill gold | First appearance |
| --- | ---: | ---: | ---: | --- |
| Drifter | 80 | 50 | 9 | Wave 1 |
| Runner | 55 | 86 | 10 | Wave 3 |
| Bulwark | 190 | 36 | 16 | Wave 5 |

Bulwark has more health rather than hidden damage resistance. This keeps the first mission understandable and avoids an unexplained counter rule.

Wave `w` contains `8 + 2 * (w - 1)` enemies. Health multiplies by `1 + 0.22 * (w - 1) + 0.045 * (w - 1)^2`. Spawn intervals shrink from 0.85 seconds toward a 0.45-second floor. From wave 3 every third slot is a Runner; from wave 5 every fifth slot is a Bulwark, taking precedence over Runner slots. Completing a wave grants `25 + 5 * w` gold. Each escaped enemy costs one life and grants no kill gold.

The intended opening is to cover a bend with damage towers, add support as Runners arrive, and choose between improving good positions and expanding coverage. Bend coverage makes range valuable; the path clearance and tower spacing rules prevent stacking all investments at the same coordinate.

## Automated campaign checks

`BalanceSimulationTest` exercises complete missions using only public commands and legitimately earned gold:

- An undefended base must lose before wave 10.
- One optimally funded, fully upgraded Pulse must not carry the entire mission.
- A fixed mixed build plan must be able to win all ten waves with positive lives.
- Replaying identical build orders must produce identical result, tick count, gold and lives.

Every wave has a two-minute simulated timeout so a broken wave cannot silently hang a test. These are guardrails for gross difficulty regressions, not proof that every tower combination is equally effective. Human playtesting remains necessary for perceived fairness, placement discoverability and strategy diversity.

Baseline campaign probes (actual Kotlin simulation, no rendering):

| Strategy | Result | Last wave | Lives | Gold remaining |
| --- | --- | ---: | ---: | ---: |
| No defense | Defeat | 3 | 0 | 285 |
| One Pulse, upgraded whenever affordable | Defeat | 6 | 0 | 686 |
| Mixed 12-position build plan, then upgrades | Victory | 10 | 20 | 410 |

The mixed strategy's no-leak result deliberately leaves room for imperfect human placement. It demonstrates a viable route through the first mission, not a claim that mixed towers are the unique optimal strategy. The mission options below add a more forgiving entry and a stricter resource challenge without changing the tower rules.


## Mission options and second-map verification

The default remains Corridor / Normal with the original path, starting resources and health values. Switchback uses a longer 1,460-unit route with alternating bends rather than Corridor's 1,240-unit route. A separate placement plan is necessary; the old coordinates intentionally are not reused on a different path.

| Difficulty | Starting gold | Starting lives | Enemy health multiplier |
| --- | ---: | ---: | ---: |
| Relaxed | 260 | 30 | 0.80 |
| Normal | 220 | 20 | 1.00 |
| Veteran | 180 | 12 | 1.25 |

Spawn counts, spawn cadence, kill bounties and completion rewards stay identical between difficulties. Veteran can still buy two initial Pulse towers; it cannot initially buy Pulse plus Cannon. All reference strategies use the default First target priority, make decisions only between waves, never sell and never edit simulation state. They build as many consecutive positions from the plan as affordable, then upgrade existing towers in construction order while affordable. They do not skip an unaffordable build-plan entry to select a cheaper later tower.

The original Corridor plan remains in `BalanceSimulationTest`. The new Switchback plan is:

| Order | Tower | X | Y |
| --- | --- | ---: | ---: |
| 1 | Pulse | 320 | 330 |
| 2 | Pulse | 420 | 240 |
| 3 | Cannon | 580 | 310 |
| 4 | Frost | 320 | 240 |
| 5 | Cannon | 650 | 290 |
| 6 | Pulse | 160 | 320 |
| 7 | Cannon | 420 | 470 |
| 8 | Pulse | 580 | 470 |
| 9 | Frost | 650 | 470 |
| 10 | Cannon | 810 | 320 |
| 11 | Pulse | 160 | 230 |
| 12 | Cannon | 580 | 80 |

Actual deterministic Kotlin probes for all six mission choices:

| Map | Difficulty | Full mixed plan | Lives remaining | Gold remaining | Simulation ticks | Undefended base |
| --- | --- | --- | ---: | ---: | ---: | --- |
| Corridor | Relaxed | Victory | 30 | 354 | 11,526 | Defeat |
| Corridor | Normal | Victory | 20 | 410 | 13,783 | Defeat |
| Corridor | Veteran | Victory | 12 | 370 | 16,080 | Defeat |
| Switchback | Relaxed | Victory | 30 | 354 | 15,485 | Defeat |
| Switchback | Normal | Victory | 20 | 410 | 16,437 | Defeat |
| Switchback | Veteran | Victory | 12 | 370 | 20,355 | Defeat |

Each full-plan victory kills all 170 enemies. To check that Veteran is meaningfully stricter rather than merely labeled differently, a second Switchback probe stops construction after the first four positions while continuing affordable upgrades:

| Difficulty | Four-tower result | Lives remaining | Escaped enemies |
| --- | --- | ---: | ---: |
| Relaxed | Victory | 25 | 5 |
| Normal | Victory | 5 | 15 |
| Veteran | Defeat | 0 | 12 |

The complete plan establishes a viable budget-legal strategy on every option; the limited plan demonstrates that Veteran punishes underinvestment. Tests assert meaningful win/loss outcomes and repeatability rather than exact gold or tick counts, so harmless balancing changes do not require rewriting implementation snapshots. No production tuning was needed after these probes. These strategies do not establish that every targeting priority or tower mix is equally strong, and live player feedback is still needed.
