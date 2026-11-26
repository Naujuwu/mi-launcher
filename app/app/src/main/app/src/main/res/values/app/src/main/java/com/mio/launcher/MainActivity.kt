package com.mio.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

data class AppItem(val label: String, val pkg: String, val icon: Drawable)

class MainActivity : Activity() {

    private val apps = mutableListOf<AppItem>()
    private lateinit var grid: GridView
    private lateinit var dock: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(56), dp(12), dp(24))
        }

        grid = GridView(this).apply {
            numColumns = 4
            verticalSpacing = dp(8)
            selector = ColorDrawable(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#55FFFFFF"))
                cornerRadius = dp(30).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(grid)
        root.addView(dock)
        setContentView(root)
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        loadApps()
    }

    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps.clear()
        pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
            .forEach {
                apps.add(
                    AppItem(
                        it.loadLabel(pm).toString(),
                        it.activityInfo.packageName,
                        it.loadIcon(pm)
                    )
                )
            }

        grid.adapter = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(position: Int) = apps[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                iconView(apps[position], dp(58), true)
        }

        dock.removeAllViews()
        apps.take(4).forEach { item ->
            val v = iconView(item, dp(58), false)
            v.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            dock.addView(v)
        }
    }

    private fun iconView(item: AppItem, size: Int, showLabel: Boolean): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
        }

        val img = ImageView(this).apply {
            setImageDrawable(item.icon)
            layoutParams = LinearLayout.LayoutParams(size, size)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, view.width * 0.225f)
                }
            }
            clipToOutline = true
        }
        col.addView(img)

        if (showLabel) {
            col.addView(TextView(this).apply {
                text = item.label
                setTextColor(Color.WHITE)
                textSize = 11f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setShadowLayer(4f, 0f, 1f, Color.BLACK)
                setPadding(0, dp(4), 0, 0)
            })
        }

        col.setOnClickListener {
            packageManager.getLaunchIntentForPackage(item.pkg)?.let { startActivity(it) }
        }
        return col
    }

    @Deprecated("Launcher: ignorar el botón atrás")
    override fun onBackPressed() {
        // no hacer nada: estamos en la pantalla de inicio
    }
}
