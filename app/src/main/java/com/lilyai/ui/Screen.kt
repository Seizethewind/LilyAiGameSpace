package com.lilyai.ui

import android.view.View

// One page of the app, shown in the area to the right of the drawer.
interface Screen {
    val title: String

    fun view(): View

    /** Called each time the page comes on screen. Refresh anything that can change. */
    fun onShow() = Unit

    /** Called when the page goes away or the app is paused. Save anything typed here. */
    fun onHide() = Unit
}
