package com.mio.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator

internal fun HomeView.showDots() {
    ui.removeCallbacks(hideDots)
    animateDots(1f)
}

internal fun HomeView.scheduleHideDots() {
    ui.removeCallbacks(hideDots)
    ui.postDelayed(hideDots, 800)
}

internal fun HomeView.animateDots(target: Float) {
    if (dotsTarget == target) return

    dotsTarget = target
    dotsAnimator?.cancel()

    val a = ValueAnimator.ofFloat(dotsMix, target)
    a.duration = 180

    a.addUpdateListener {
        dotsMix = it.animatedValue as Float
        invalidate()
    }

    dotsAnimator = a
    a.start()
}

internal fun HomeView.launch(pkg: String) {
    act.packageManager
        .getLaunchIntentForPackage(pkg)
        ?.let {
            libPushRecent(pkg)
            act.startActivity(it)
        }
}

internal fun HomeView.animateToPage(target: Int) {
    if (target < 0 || target > store.pages.size) return

    pageAnimator?.cancel()
    showDots()

    val from = pageOffset

    val to = when {
        target > currentPage -> -width.toFloat()
        target < currentPage -> width.toFloat()
        else -> 0f
    }

    val a = ValueAnimator.ofFloat(from, to)
    a.duration = 220

    a.addUpdateListener {
        pageOffset = it.animatedValue as Float
        invalidate()
    }

    a.addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            currentPage = target
            pageOffset = 0f

            if (currentPage != store.pages.size) libReset()
            scheduleHideDots()
            invalidate()
        }
    })

    pageAnimator = a
    a.start()
}
