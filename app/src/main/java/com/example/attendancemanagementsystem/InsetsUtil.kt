package com.example.attendancemanagementsystem

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding

/**
 * Apply edge-to-edge insets handling:
 * - Make window draw edge-to-edge
 * - Make toolbar sit below the status bar (padding + background area)
 * - Push the content container down so it doesn't overlap toolbar/statusbar
 * - Add bottom padding to avoid nav bar overlap (for the content or provided navAnchoredView)
 *
 * Usage: call immediately after setContentView(...) in your Activity.
 *
 * Notes:
 * - If the toolbar is inside an AppBarLayout (common CoordinatorLayout / AppBarLayout pattern),
 *   we do NOT force a top margin onto the content container because AppBarLayout + appbar_scrolling_view_behavior
 *   already handle layout offsets. In that case we only set toolbar padding/height and bottom padding.
 */
object InsetsUtil {
    fun applyEdgeToEdge(
        window: Window,
        root: View,
        toolbar: View,
        contentContainer: View,
        navAnchoredView: View? = null
    ) {
        // Let us control system insets (edge-to-edge)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val sysInsets = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
            )

            // Read actionBarSize from current theme (px)
            var actionBarSizePx = 0
            val tv = TypedValue()
            if (root.context.theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
                actionBarSizePx = TypedValue.complexToDimensionPixelSize(tv.data, root.resources.displayMetrics)
            }
            val totalToolbarHeight = sysInsets.top + actionBarSizePx

            // Ensure toolbar content is placed below status bar and toolbar background covers status bar area
            toolbar.updatePadding(top = sysInsets.top)
            toolbar.updateLayoutParams<ViewGroup.LayoutParams> {
                height = totalToolbarHeight
            }

            // Detect if toolbar is inside AppBarLayout (CoordinatorLayout pattern).
            // If so, AppBarLayout + appbar_scrolling_view_behavior will handle content offsets.
            val toolbarParentName = toolbar.parent?.javaClass?.simpleName ?: ""
            val isInsideAppBarLayout = toolbarParentName.contains("AppBar", ignoreCase = true) ||
                    toolbarParentName.contains("AppBarLayout", ignoreCase = true)

            if (!isInsideAppBarLayout) {
                // Move the main content container (if possible) down so it sits *below* the taller toolbar.
                val lp = contentContainer.layoutParams
                if (lp is MarginLayoutParams) {
                    lp.topMargin = totalToolbarHeight
                    contentContainer.layoutParams = lp
                } else {
                    // fallback: apply top padding if margins are not available
                    contentContainer.updatePadding(top = totalToolbarHeight)
                }
            } else {
                // If inside AppBarLayout, remove any previously enforced top margin (defensive)
                val lp = contentContainer.layoutParams
                if (lp is MarginLayoutParams && lp.topMargin > 0) {
                    lp.topMargin = 0
                    contentContainer.layoutParams = lp
                }
            }

            // Bottom padding: prefer navAnchoredView if provided else apply to content container
            val bottomPadTarget = navAnchoredView ?: contentContainer

            // If bottomPadTarget is a ScrollView, update its padding (scroll area will include padding)
            bottomPadTarget.updatePadding(bottom = sysInsets.bottom)

            // We've handled insets
            WindowInsetsCompat.CONSUMED
        }
    }
}
