# Balance and player experience

The first playable mission is deliberately finite: ten manually started waves, 20 base lives and 220 starting gold. Preparation has no timer. A player can inspect the route, choose investments and read the next wave composition before committing. Building, upgrading and selling remain possible during a wave.

## Tower roles

| Tower | Cost | Base range | Damage | Attack period | Purpose |
| --- | ---: | ---: | ---: | ---: | --- |
| Pulse | 75 | 135 | 12 | 0.30 s | Affordable, reliable damage against an individual target |
| Cannon | 120 | 155 | 34 | 1.10 s | Damage to every enemy within 58 units of the impact |
| Frost | 90 | 125 | 6 | 0.70 s | Reduces the target's speed by 45% for 1.5 seconds |

Frost effects refresh without multiplying. A support-only defense cannot replace damage towers. Cannon's lower single-target damage per second rewards groups; Pulse is the cheaper choice against isolated targets. Towers select the enemy furthest along the route and use ID order to resolve ties.

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

The mixed strategy's no-leak result deliberately leaves room for imperfect human placement. It demonstrates a viable route through the first mission, not a claim that mixed towers are the unique optimal strategy. Higher difficulty modes should be considered only after players have tested the controls and first mission.
