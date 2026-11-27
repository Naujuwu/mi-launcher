package com.mio.launcher.layout

import android.content.Context
import android.content.SharedPreferences
import com.mio.launcher.models.AppItem

/**
 * Guarda y restaura el orden de las páginas y el dock.
 */
class LayoutStore(
    context: Context,
    private val cols: Int = 4,
    private val rows: Int = 6
) {
    private val perPage = cols * rows
    private val prefs: SharedPreferences =
        context.getSharedPreferences("layout", Context.MODE_PRIVATE)

    val pages = mutableListOf<Array<String?>>()
    val dock = arrayOfNulls<String>(4)
    var firstRun = false
        private set

    fun load() {
        pages.clear()
        val saved = prefs.getString("pages", null)
        if (saved != null) {
            for (pageStr in saved.split(";")) {
                val arr = arrayOfNulls<String>(perPage)
                val parts = pageStr.split(",")
                for (i in 0 until perPage) {
                    val s = parts.getOrNull(i)
                    arr[i] = if (s.isNullOrEmpty()) null else s
                }
                pages.add(arr)
            }
        }
        if (pages.isEmpty()) pages.add(arrayOfNulls(perPage))

        dock.fill(null)
        val d = prefs.getString("dock", null)
        if (d != null) {
            val parts = d.split(",")
            for (i in 0 until 4) {
                val s = parts.getOrNull(i)
                dock[i] = if (s.isNullOrEmpty()) null else s
            }
        }
        firstRun = (saved == null)
    }

    fun save() {
        val p = pages.joinToString(";") { page ->
            page.joinToString(",") { it ?: "" }
        }
        val d = dock.joinToString(",") { it ?: "" }
        prefs.edit().putString("pages", p).putString("dock", d).apply()
    }

    /**
     * Elimina apps desinstaladas y coloca las nuevas.
     * En el primer arranque intenta poner teléfono / mensajes / navegador / cámara en el dock.
     */
    fun reconcile(apps: Map<String, AppItem>) {
        val placed = HashSet<String>()
        for (page in pages) {
            for (i in page.indices) {
                val p = page[i]
                if (p != null && (!apps.containsKey(p) || !placed.add(p))) page[i] = null
            }
        }
        for (i in dock.indices) {
            val p = dock[i]
            if (p != null && (!apps.containsKey(p) || !placed.add(p))) dock[i] = null
        }

        if (firstRun) {
            val wanted = listOf(
                listOf("phone", "teléfono", "telefono"),
                listOf("messages", "mensajes", "messaging"),
                listOf("browser", "internet", "navegador", "chrome"),
                listOf("camera", "cámara", "camara")
            )
            var di = 0
            for (names in wanted) {
                val match = apps.values.firstOrNull {
                    it.label.lowercase() in names && !placed.contains(it.pkg)
                }
                if (match != null && di < 4) {
                    dock[di] = match.pkg
                    di++
                    placed.add(match.pkg)
                }
            }
            for (i in 0 until 4) {
                if (dock[i] == null) {
                    val nx = apps.values.firstOrNull { !placed.contains(it.pkg) } ?: break
                    dock[i] = nx.pkg
                    placed.add(nx.pkg)
                }
            }
            firstRun = false
        }

        for (app in apps.values) {
            if (placed.contains(app.pkg)) continue
            var done = false
            for (page in pages) {
                val idx = page.indexOfFirst { it == null }
                if (idx >= 0) {
                    page[idx] = app.pkg
                    done = true
                    break
                }
            }
            if (!done) {
                val np = arrayOfNulls<String>(perPage)
                np[0] = app.pkg
                pages.add(np)
            }
            placed.add(app.pkg)
        }
        trimEmptyPages()
    }

    fun trimEmptyPages(currentPage: Int = 0): Int {
        while (pages.size > 1 && pages[pages.size - 1].all { it == null }) {
            pages.removeAt(pages.size - 1)
        }
        return if (currentPage > pages.size - 1) pages.size - 1 else currentPage
    }

    fun askedNotif(): Boolean = prefs.getBoolean("askedNotif", false)

    fun setAskedNotif() {
        prefs.edit().putBoolean("askedNotif", true).apply()
    }
}
