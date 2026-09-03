package net.bison.domain

/**
 * Where a row being dragged has got to.
 *
 * Pulled out of the screen because it is the part that can be got wrong quietly. Whether a
 * finger's events reach the gesture at all is something only a device can answer - the test
 * harness here cannot drive a long press - but the arithmetic between "the finger has moved 180
 * pixels" and "the row is now two places further down" is arithmetic, and it can be checked
 * without a finger.
 */
object RowDrag {
    /**
     * @param to where the row ends up
     * @param travel what is left of the drag once the row has moved, so the next event carries
     *   on from where this one left off rather than from the row's new home
     */
    data class Step(
        val to: Int,
        val travel: Float,
    )

    /**
     * @param from where the row is now
     * @param travel how far the finger has come since the row last moved
     * @param rowPx the height of a row and the gap under it
     * @param rows how many rows there are
     */
    fun step(
        from: Int,
        travel: Float,
        rowPx: Float,
        rows: Int,
    ): Step {
        if (rowPx <= 0f || rows <= 0) return Step(from, travel)
        val crossed = (travel / rowPx).toInt()
        val to = (from + crossed).coerceIn(0, rows - 1)
        return Step(to, travel - (to - from) * rowPx)
    }
}
