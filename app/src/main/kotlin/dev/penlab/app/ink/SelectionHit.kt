package dev.penlab.app.ink

/** A touch inside the box may claim a resize handle only within this share of the box's short side. */
const val HANDLE_INTERIOR_SHARE = 0.3f

/**
 * Whether a touch [distancePx] from a resize handle grabs it (all values in screen pixels).
 *
 * Outside the selection's box a handle claims the full [grabPx]: that is where fingers and the pen
 * aim for one. Inside the box the claim shrinks with the box ([HANDLE_INTERIOR_SHARE] of its short
 * side, never more than [grabPx]), so on a small selection the middle still drags it instead of
 * resizing (device QA: a 36 px tall selection resized when dragged from its centre). On a box
 * bigger than about 3 grab radii across nothing changes.
 *
 * [boxShortSidePx] is the box as drawn (margin included), at the current zoom.
 */
fun handleClaimsTouch(distancePx: Float, insideBox: Boolean, boxShortSidePx: Float, grabPx: Float): Boolean {
  val reach = if (insideBox) minOf(grabPx, boxShortSidePx * HANDLE_INTERIOR_SHARE) else grabPx
  return distancePx < reach
}
