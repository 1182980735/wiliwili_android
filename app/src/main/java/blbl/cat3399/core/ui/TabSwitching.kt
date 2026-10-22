package blbl.cat3399.core.ui

import android.view.View
import android.view.ViewGroup
import com.google.android.material.tabs.TabLayout

internal fun View.findFirstVisibleTabLayout(): TabLayout? {
    if (this is TabLayout && isShown && tabCount > 0) return this
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            val found = getChildAt(i).findFirstVisibleTabLayout()
            if (found != null) return found
        }
    }
    return null
}

internal fun TabLayout.selectRelativeTab(direction: Int): Boolean {
    if (tabCount <= 0) return false
    val current = selectedTabPosition
    if (current < 0) {
        getTabAt(if (direction < 0) tabCount - 1 else 0)?.select()
        return true
    }
    val target = (current + direction).coerceIn(0, tabCount - 1)
    if (target != current) getTabAt(target)?.select()
    return true
}
