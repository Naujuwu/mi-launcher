package com.mio.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Build
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import kotlin.math.max

internal fun HomeView.openFolder(slot: String) {
    val id = store.folderIdFromSlot(slot) ?: return

    if (store.folders[id] == null) return

    folderPageAnimator?.cancel()

    openFolderId = id
    folderPage = 0
    folderPageOffset = 0f
    mode = MODE_NONE

    setWindowBlur(true)
    invalidate()
}

internal fun HomeView.closeFolder() {
    if (renameEdit != null) commitRename()

    folderPageAnimator?.cancel()

    openFolderId = null
    folderPage = 0
    folderPageOffset = 0f
    mode = MODE_NONE

    setWindowBlur(false)
    invalidate()
}

// Desenfoca el fondo de pantalla detrás de la ventana (Android 12+)
internal fun HomeView.setWindowBlur(on: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    try {
        val w = act.window
        val lp = w.attributes

        if (on) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            lp.blurBehindRadius = dp(60f).toInt()
        } else {
            lp.flags = lp.flags and
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            lp.blurBehindRadius = 0
        }

        w.attributes = lp
    } catch (_: Throwable) {
    }
}

internal fun HomeView.animateFolderToPage(target: Int) {
    val id = openFolderId ?: return
    val folder = store.folders[id] ?: return

    val pages = max(1, (folder.apps.size + 8) / 9)

    if (target !in 0 until pages) {
        folderPageOffset = 0f
        invalidate()
        return
    }

    folderPageAnimator?.cancel()
    folderPanelGeometry()

    val from = folderPageOffset

    val to = when {
        target > folderPage -> -folderPanel.width()
        target < folderPage -> folderPanel.width()
        else -> 0f
    }

    val animator = ValueAnimator.ofFloat(from, to).apply {
        duration = 260L
        interpolator = DecelerateInterpolator(1.5f)

        addUpdateListener {
            folderPageOffset = it.animatedValue as Float
            invalidate()
        }

        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (openFolderId != id) return

                folderPage = target
                folderPageOffset = 0f
                invalidate()
            }
        })
    }

    folderPageAnimator = animator
    animator.start()
}
