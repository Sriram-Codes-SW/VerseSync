package dev.sriramcodessw.versesync.data

/** Pure list moves for setlist entries (ADR-20), kept apart from the database so they can be tested alone. */
object SetlistOrder {
    /**
     * Returns [ids] with the id at [from] moved so that it ends at [to]. A position outside the list
     * is clamped to the nearest end, and a [from] outside the list, or a move to the same place,
     * returns the list unchanged.
     */
    fun move(
        ids: List<Int>,
        from: Int,
        to: Int,
    ): List<Int> {
        if (ids.isEmpty() || from !in ids.indices) return ids
        val target = to.coerceIn(0, ids.lastIndex)
        if (target == from) return ids
        val result = ids.toMutableList()
        val moved = result.removeAt(from)
        result.add(target, moved)
        return result
    }
}
