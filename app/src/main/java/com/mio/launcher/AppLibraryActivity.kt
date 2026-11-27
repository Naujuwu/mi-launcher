
package com.mio.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class AppLibraryActivity : Activity() {

    private data class LibraryApp(
        val name: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable
    )

    private lateinit var content: LinearLayout
    private lateinit var search: EditText
    private val allApps = mutableListOf<LibraryApp>()

    private val categories = linkedMapOf(
        "Social" to listOf("social", "communication", "messaging"),
        "Entertainment" to listOf("music", "video", "entertainment"),
        "Creativity" to listOf("photo", "camera", "art", "design"),
        "Productivity" to listOf("office", "productivity", "business"),
        "Finance" to listOf("finance", "bank", "payment"),
        "Utilities" to listOf("tool", "utility", "calculator"),
        "Games" to listOf("game"),
        "Travel" to listOf("travel", "map", "navigation"),
        "Shopping" to listOf("shop", "store", "shopping"),
        "Education" to listOf("education", "book", "learning"),
        "Health & Fitness" to listOf("health", "fitness", "sport"),
        "Other" to emptyList()
    )

    private val prefs by lazy {
        getSharedPreferences("app_library", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(30, 30, 35)
        window.navigationBarColor = Color.rgb(30, 30, 35)

        loadApps()
        buildInterface()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)

        val installed = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }

        val known = prefs.getStringSet("known_apps", emptySet())?.toMutableSet()
            ?: mutableSetOf()

        val current = installed.map { it.activityInfo.packageName }.toSet()

        val added = current - known
        if (known.isNotEmpty() && added.isNotEmpty()) {
            prefs.edit().putStringSet(
                "recent_apps",
                (added + (prefs.getStringSet("recent_apps", emptySet()) ?: emptySet()))
                    .toMutableSet()
            ).apply()
        }

        prefs.edit().putStringSet("known_apps", current).apply()

        allApps.clear()

        installed.forEach { info ->
            val pkg = info.activityInfo.packageName

            if (pkg in (prefs.getStringSet("hidden_apps", emptySet()) ?: emptySet())) {
                return@forEach
            }

            allApps.add(
                LibraryApp(
                    info.loadLabel(pm).toString(),
                    pkg,
                    info.loadIcon(pm)
                )
            )
        }

        allApps.sortBy { it.name.lowercase() }
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), 0)
            setBackgroundColor(Color.rgb(27, 27, 32))
        }

        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val title = TextView(this).apply {
            text = "App Library"
            textSize = 29f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(12), 0, dp(18))
        }

        search = EditText(this).apply {
            hint = "Search"
            textSize = 16f
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(0xFFAAAAAA.toInt())
            setPadding(dp(16), 0, dp(16), 0)
            background = roundedBackground(0xFF414148.toInt(), 28)
        }

        search.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int
                ) {}

                override fun onTextChanged(
                    s: CharSequence?, start: Int, before: Int, count: Int
                ) {
                    render(s?.toString().orEmpty())
                }

                override fun afterTextChanged(s: android.text.Editable?) {}
            }
        )

        root.addView(title)
        root.addView(search, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
        ))

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)
        render()
    }

    private fun render(query: String = "") {
        content.removeAllViews()

        if (query.isNotBlank()) {
            sectionTitle("Search Results")
            appGrid(allApps.filter {
                it.name.contains(query, true) ||
                    it.packageName.contains(query, true)
            })
            return
        }

        sectionTitle("Suggestions")

        val suggestions = allApps
            .sortedByDescending {
                NotifService.counts[it.packageName] ?: 0
            }
            .take(4)

        appGrid(suggestions)

        sectionTitle("Recently Added")

        val recentPackages = prefs.getStringSet(
            "recent_apps", emptySet()
        ) ?: emptySet()

        val recent = allApps.filter {
            it.packageName in recentPackages
        }

        appGrid(recent.take(4))

        categories.forEach { (category, keywords) ->
            val matching = allApps.filter { app ->
                val name = app.name.lowercase()
                val pkg = app.packageName.lowercase()

                if (category == "Other") {
                    categories.values.flatten().none {
                        name.contains(it) || pkg.contains(it)
                    }
                } else {
                    keywords.any {
                        name.contains(it) || pkg.contains(it)
                    }
                }
            }

            if (matching.isNotEmpty()) {
                sectionTitle(category)
                appGrid(matching.take(4))
            }
        }
    }

    private fun sectionTitle(text: String) {
        val title = TextView(this).apply {
            this.text = text
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(22), 0, dp(12))
        }

        content.addView(title)
    }

    private fun appGrid(apps: List<LibraryApp>) {
        if (apps.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No applications"
                textSize = 13f
                setTextColor(0xFFAAAAAA.toInt())
                setPadding(0, dp(8), 0, dp(12))
            }
            content.addView(empty)
            return
        }

        apps.chunked(4).forEach { group ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            group.forEach { app ->
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(3), dp(8), dp(3), dp(8))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        packageManager.getLaunchIntentForPackage(
                            app.packageName
                        )?.let { startActivity(it) }
                    }

                    setOnLongClickListener {
                        showAppOptions(app)
                        true
                    }
                }

                val icon = ImageView(this).apply {
                    setImageDrawable(app.icon)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }

                item.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))

                val label = TextView(this).apply {
                    text = app.name
                    textSize = 10f
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(Color.WHITE)
                }

                item.addView(label, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(22)
                ))

                val badge = NotifService.counts[app.packageName] ?: 0
                if (badge > 0 && !NotifService.dnd) {
                    val badgeView = TextView(this).apply {
                        text = if (badge > 999) "999+" else badge.toString()
                        textSize = 9f
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        setPadding(dp(5), dp(2), dp(5), dp(2))
                        background = roundedBackground(
                            0xFFFF383C.toInt(), 20
                        )
                    }

                    item.addView(badgeView)
                }

                row.addView(item, LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ))
            }

            repeat(4 - group.size) {
                row.addView(View(this), LinearLayout.LayoutParams(
                    0, dp(76), 1f
                ))
            }

            content.addView(row)
        }
    }

    private fun showAppOptions(app: LibraryApp) {
        val options = arrayOf(
            "Add to Home Screen",
            "Hide Application",
            "App Info"
        )

        android.app.AlertDialog.Builder(this)
            .setTitle(app.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> addToHome(app.packageName)
                    1 -> hideApp(app.packageName)
                    2 -> {
                        val intent = Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:${app.packageName}")
                        )
                        startActivity(intent)
                    }
                }
            }
            .show()
    }

    private fun addToHome(pkg: String) {
        val layout = getSharedPreferences("layout", Context.MODE_PRIVATE)
        val pages = layout.getString("pages", null)

        val pageSize = 24
        val pageList = (pages ?: "").split(";").toMutableList()

        val last = pageList.lastOrNull()?.split(",")?.toMutableList()
            ?: MutableList(pageSize) { "" }

        val normalized = last.toMutableList()
        while (normalized.size < pageSize) normalized.add("")

        val existing = pageList.any {
            it.split(",").contains(pkg)
        }

        if (existing) return

        val index = normalized.indexOfFirst { it.isEmpty() }

        if (index >= 0) {
            normalized[index] = pkg
            pageList[pageList.lastIndex] = normalized.joinToString(",")
        } else {
            val newPage = MutableList(pageSize) { "" }
            newPage[0] = pkg
            pageList.add(newPage.joinToString(","))
        }

        layout.edit().putString("pages", pageList.joinToString(";")).apply()

        android.widget.Toast.makeText(
            this, "Added to Home Screen", android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private fun hideApp(pkg: String) {
        val hidden = prefs.getStringSet(
            "hidden_apps", emptySet()
        )?.toMutableSet() ?: mutableSetOf()

        hidden.add(pkg)

        prefs.edit().putStringSet("hidden_apps", hidden).apply()
        render(search.text.toString())
    }

    private fun roundedBackground(color: Int, radius: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
}
