package com.mio.launcher

import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

internal fun HomeView.handleFolderTouch(e: MotionEvent): Boolean {
    when (e.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            folderPageAnimator?.cancel()
            folderDownX = e.x
            folderDownY = e.y
            folderPageOffset = 0f
            folderDragPkg = null
            mode = MODE_FOLDER

            ui.removeCallbacks(folderLongPress)
            ui.postDelayed(
                folderLongPress,
                ViewConfiguration.getLongPressTimeout().toLong()
            )

            return true
        }

        MotionEvent.ACTION_MOVE -> {
            val dx = e.x - folderDownX
            val dy = e.y - folderDownY

            if (folderDragPkg != null) {
                dragX = e.x
                dragY = e.y

                if (!folderPanel.contains(e.x, e.y)) {
                    leaveFolderWithDrag()
                } else {
                    invalidate()
                }

                return true
            }

            if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                ui.removeCallbacks(folderLongPress)
            }

            if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                val folder = openFolderId?.let { store.folders[it] }

                if (folder != null) {
                    val pages = (folder.apps.size + 8) / 9

                    folderPageOffset = when {
                        dx < 0 && folderPage >= pages - 1 -> dx * 0.25f
                        dx > 0 && folderPage <= 0 -> dx * 0.25f
                        else -> dx
                    }

                    invalidate()
                }
            }

            return true
        }

        MotionEvent.ACTION_UP -> {
            ui.removeCallbacks(folderLongPress)

            val dx = e.x - folderDownX
            val dy = e.y - folderDownY

            val id = openFolderId
            val folder = id?.let { store.folders[it] }

            val draggedPkg = folderDragPkg

            if (draggedPkg != null) {
                folderDragPkg = null

                if (folder != null) {
                    val to = folderCellAt(e.x, e.y)
                    val from = folder.apps.indexOf(draggedPkg)

                    if (to >= 0 && from >= 0 && to != from) {
                        folder.apps.removeAt(from)
                        folder.apps.add(to, draggedPkg)
                        store.save()
                    }
                }

                mode = MODE_NONE
                invalidate()
                return true
            }

            if (folder == null) {
                closeFolder()
                return true
            }

            folderPanelGeometry()

            if (abs(dx) > touchSlop * 2f && abs(dx) > abs(dy)) {
                val pages = (folder.apps.size + 8) / 9

                val target = when {
                    dx < -width / 5f && folderPage < pages - 1 ->
                        folderPage + 1

                    dx > width / 5f && folderPage > 0 ->
                        folderPage - 1

                    else -> folderPage
                }

                animateFolderToPage(target)
                return true
            }

            if (folderTitleArea().contains(e.x, e.y)) {
                startRename(id)
                return true
            }

            if (!folderPanel.contains(e.x, e.y)) {
                closeFolder()
                return true
            }

            val index = folderCellAt(e.x, e.y)

            if (index >= 0) {
                val pkg = folder.apps.getOrNull(index)

                if (pkg != null) {
                    launch(pkg)
                    closeFolder()
                }
            }

            return true
        }

        MotionEvent.ACTION_CANCEL -> {
            ui.removeCallbacks(folderLongPress)
            folderDragPkg = null
            animateFolderToPage(folderPage)
            mode = MODE_NONE
            return true
        }
    }

    return true
}
