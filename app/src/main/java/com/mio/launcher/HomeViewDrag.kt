package com.mio.launcher

import android.view.HapticFeedbackConstants

internal fun HomeView.startDrag(pkg: String) {
    mode = MODE_DRAG

    dragPkg = pkg

    srcInDock = pressInDock
    srcPage = currentPage
    srcIndex = pressIndex

    dragX = lastX
    dragY = lastY

    edgeDir = 0

    performHapticFeedback(
        HapticFeedbackConstants.LONG_PRESS
    )

    parent?.requestDisallowInterceptTouchEvent(true)

    invalidate()
}

internal fun HomeView.leaveFolderWithDrag() {
    val pkg = folderDragPkg ?: return
    val fid = openFolderId ?: return

    folderDragPkg = null

    closeFolder()

    dragFromFolderId = fid
    dragPkg = pkg

    srcInDock = false
    srcPage = currentPage
    srcIndex = -1

    edgeDir = 0
    mode = MODE_DRAG

    parent?.requestDisallowInterceptTouchEvent(true)
    invalidate()
}

internal fun HomeView.dropFromFolder(pkg: String, folderId: String) {
    val dIdx = dockCellAt(dragX, dragY)
    val gIdx = if (dIdx >= 0) -1 else gridCellAt(dragX, dragY)

    if (dIdx < 0 && gIdx < 0) return

    val arr: Array<String?> = if (dIdx >= 0) {
        store.dock
    } else {
        store.pages.getOrNull(currentPage) ?: return
    }

    val idx = if (dIdx >= 0) dIdx else gIdx
    val target = arr.getOrNull(idx)

    when {
        target == null -> {
            store.removeAppFromFolder(folderId, pkg)
            arr[idx] = pkg
        }

        store.isFolderSlot(target) -> {
            val tid = store.folderIdFromSlot(target)

            if (tid != null && tid != folderId) {
                store.removeAppFromFolder(folderId, pkg)
                store.addAppToFolder(tid, pkg)
            }
        }

        else -> {
            store.removeAppFromFolder(folderId, pkg)

            store.createFolder(pkg, target)
        }
    }
}

internal fun HomeView.checkEdge() {
    val dir = when {
        dragX < dp(30f) -> -1
        dragX > width - dp(30f) -> 1
        else -> 0
    }

    if (dir != edgeDir) {
        ui.removeCallbacks(edgeRunnable)

        edgeDir = dir

        if (dir != 0) {
            ui.postDelayed(edgeRunnable, 600)
        }
    }
}

internal fun HomeView.dropDragged() {
    val pkg = dragPkg
    val fromFolder = dragFromFolderId
    dragFromFolderId = null

    if (pkg != null && fromFolder != null) {
        dropFromFolder(pkg, fromFolder)
    } else if (pkg != null) {
        val dIdx = dockCellAt(dragX, dragY)

        val target = if (dIdx >= 0) {
            store.dock.getOrNull(dIdx)
        } else {
            val gIdx = gridCellAt(dragX, dragY)

            if (gIdx >= 0) {
                store.pages.getOrNull(currentPage)?.getOrNull(gIdx)
            } else {
                null
            }
        }

        val targetIsValid = dIdx >= 0 ||
            gridCellAt(dragX, dragY) >= 0

        if (targetIsValid) {
            when {
                target == null -> {
                    place(
                        pkg,
                        dIdx >= 0,
                        currentPage,
                        if (dIdx >= 0) dIdx else gridCellAt(dragX, dragY)
                    )
                }

                target == pkg -> {
                    // No hacer nada al soltar sobre el mismo espacio.
                }

                store.isFolderSlot(pkg) -> {
                    // Las carpetas se pueden mover entre espacios.
                    place(
                        pkg,
                        dIdx >= 0,
                        currentPage,
                        if (dIdx >= 0) dIdx else gridCellAt(dragX, dragY)
                    )
                }

                store.isFolderSlot(target) -> {
                    val folderId = store.folderIdFromSlot(target)

                    if (folderId != null) {
                        store.addAppToFolder(folderId, pkg)
                    }
                }

                else -> {
                    val folder = store.createFolder(pkg, target)

                    if (folder == null) {
                        place(
                            pkg,
                            dIdx >= 0,
                            currentPage,
                            if (dIdx >= 0) dIdx else gridCellAt(dragX, dragY)
                        )
                    }
                }
            }
        }
    }

    dragPkg = null
    edgeDir = 0

    currentPage = store.trimEmptyPages(currentPage)

    store.save()
    invalidate()
}

internal fun HomeView.place(
    pkg: String,
    toDock: Boolean,
    toPage: Int,
    toIdx: Int
) {
    val target = if (toDock) {
        store.dock
    } else {
        store.pages.getOrNull(toPage) ?: return
    }

    val src = if (srcInDock) {
        store.dock
    } else {
        store.pages.getOrNull(srcPage) ?: return
    }

    if (toIdx !in target.indices || srcIndex !in src.indices) return

    val displaced = target[toIdx]

    target[toIdx] = pkg
    src[srcIndex] = displaced
}

// Animaciones
