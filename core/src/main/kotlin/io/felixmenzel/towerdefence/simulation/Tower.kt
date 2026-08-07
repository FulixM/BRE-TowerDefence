package io.felixmenzel.towerdefence.simulation

class Tower(
    val x: Double,
    val y: Double,
    val range: Double,
    val damage: Double,
    val attackIntervalTicks: Long,
) {
    init {
        require(range >= 0.0)
        require(damage >= 0.0)
        require(attackIntervalTicks > 0L)
    }

    private var nextAttackTick = 0L

    fun update(
        currentTick: Long,
        enemies: List<Enemy>,
    ) {
        if (currentTick < nextAttackTick) {
            return
        }

        val target =
            enemies
                .asSequence()
                .filter { enemy ->
                    enemy.isAlive &&
                        !enemy.hasReachedGoal &&
                        isInRange(enemy)
                }.minWithOrNull(
                    compareBy<Enemy>(
                        { distanceSquaredTo(it) },
                        { it.id },
                    ),
                )
                ?: return

        target.takeDamage(damage)

        nextAttackTick =
            currentTick + attackIntervalTicks
    }

    private fun isInRange(enemy: Enemy): Boolean = distanceSquaredTo(enemy) <= range * range

    private fun distanceSquaredTo(enemy: Enemy): Double {
        val deltaX = enemy.x - x
        val deltaY = enemy.y - y

        return deltaX * deltaX + deltaY * deltaY
    }
}
