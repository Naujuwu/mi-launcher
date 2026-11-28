package com.mio.launcher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.min
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context

// Edición del nombre en línea, estilo iOS (mantener pulsado el título)
internal fun HomeView.startRename(folderId: String) {
    if (renameEdit != null) return

    val folder = store.folders[folderId] ?: return
    val host = parent as? ViewGroup ?: return

    folderPanelGeometry()

    val textPx = width * 0.083f
    val boxH = (textPx * 1.6f).toInt()
    val boxW = folderPanel.width().toInt()

    val baseline = folderPanel.top - folderPanel.width() * 0.20f
    val centerY = baseline - textPx * 0.35f

    val box = FrameLayout(act)

    val bg = GradientDrawable().apply {
        setColor(0x8C4F5963.toInt())
        cornerRadius = boxH * 0.34f
        setStroke(dp(1f).toInt(), 0x33FFFFFF)
    }

    box.background = bg

    val edit = EditText(act).apply {
        setText(folder.name)
        setSelection(text.length)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        background = null
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        imeOptions = EditorInfo.IME_ACTION_DONE
        includeFontPadding = false
        setPadding((boxH * 0.9f).toInt(), 0, (boxH * 0.9f).toInt(), 0)

        setOnEditorActionListener { _, _, _ ->
            commitRename()
            true
        }
    }

    box.addView(
        edit,
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    )

    val clearSize = (boxH * 0.40f).toInt()

    val clear = object : View(act) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(c: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val r = min(width, height) / 2f

            p.style = Paint.Style.FILL
            p.color = 0xCCC9CDD1.toInt()
            c.drawCircle(cx, cy, r, p)

            p.style = Paint.Style.STROKE
            p.color = 0xFF3A4048.toInt()
            p.strokeWidth = r * 0.22f
            p.strokeCap = Paint.Cap.ROUND

            val d = r * 0.32f

            c.drawLine(cx - d, cy - d, cx + d, cy + d, p)
            c.drawLine(cx - d, cy + d, cx + d, cy - d, p)
        }
    }

    clear.setOnClickListener {
        edit.setText("")
    }

    box.addView(
        clear,
        FrameLayout.LayoutParams(clearSize, clearSize).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            marginEnd = (boxH * 0.3f).toInt()
        }
    )

    host.addView(
        box,
        FrameLayout.LayoutParams(boxW, boxH).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = (x + folderPanel.left).toInt()
            topMargin = (y + centerY - boxH / 2f).toInt()
        }
    )

    renameBox = box
    renameEdit = edit
    renamingFolderId = folderId

    edit.requestFocus()

    edit.post {
        val imm = act.getSystemService(Context.INPUT_METHOD_SERVICE)
            as InputMethodManager
        imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
    }

    invalidate()
}

internal fun HomeView.commitRename() {
    val edit = renameEdit ?: return
    val box = renameBox
    val id = renamingFolderId

    val name = edit.text.toString().trim()

    renameEdit = null
    renameBox = null
    renamingFolderId = null

    try {
        val imm = act.getSystemService(Context.INPUT_METHOD_SERVICE)
            as InputMethodManager
        imm.hideSoftInputFromWindow(edit.windowToken, 0)
    } catch (_: Throwable) {
    }

    (box?.parent as? ViewGroup)?.removeView(box)

    if (id != null) {
        store.folders[id]?.name = name
        store.save()
    }

    invalidate()
}

internal fun HomeView.showRenameFolderDialog(folderId: String) {
    val folder = store.folders[folderId] ?: return

    val input = EditText(act).apply {
        setText(folder.name)
        setSelection(text.length)
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setSingleLine(true)
    }

    AlertDialog.Builder(act)
        .setTitle("Nombre de la carpeta")
        .setView(input)
        .setNegativeButton("Cancelar", null)
        .setPositiveButton("Guardar") { _, _ ->
            val name = input.text.toString().trim()

            if (name.isNotEmpty()) {
                store.renameFolder(folderId, name)
                invalidate()
            }
        }
        .show()
}

// Animación de páginas de carpeta
