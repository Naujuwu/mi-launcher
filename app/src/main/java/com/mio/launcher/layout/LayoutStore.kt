
package com.mio.launcher.layout

import android.content.Context
import android.content.SharedPreferences
import com.mio.launcher.models.AppFolder
import com.mio.launcher.models.AppItem
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

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

    val folders = LinkedHashMap<String, AppFolder>()

    var firstRun = false
        private set

    companion object {
        private const val FOLDER_PREFIX = "@folder:"
    }

    fun isFolderSlot(value: String?): Boolean =
        value?.startsWith(FOLDER_PREFIX) == true

    fun folderIdFromSlot(value: String?): String? {
        if (!isFolderSlot(value)) return null
        return value!!.removePrefix(FOLDER_PREFIX)
    }

    fun folderSlot(id: String): String = FOLDER_PREFIX + id

    fun getFolderFromSlot(value: String?): AppFolder? {
        val id = folderIdFromSlot(value) ?: return null
        return folders[id]
    }

    fun load() {
        pages.clear()
        folders.clear()

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

        if (pages.isEmpty()) {
            pages.add(arrayOfNulls(perPage))
        }

        dock.fill(null)

        val savedDock = prefs.getString("dock", null)

        if (savedDock != null) {
            val parts = savedDock.split(",")

            for (i in 0 until 4) {
                val s = parts.getOrNull(i)
                dock[i] = if (s.isNullOrEmpty()) null else s
            }
        }

        loadFolders()

        firstRun = saved == null
    }

    private fun loadFolders() {
        val raw = prefs.getString("folders", null) ?: return

        try {
            val array = JSONArray(raw)

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue

                val id = item.optString("id")
                if (id.isBlank()) continue

                val name = item.optString("name", "Carpeta")
                val appArray = item.optJSONArray("apps") ?: JSONArray()
                val appList = mutableListOf<String>()

                for (j in 0 until appArray.length()) {
                    val pkg = appArray.optString(j)
                    if (pkg.isNotBlank() && !appList.contains(pkg)) {
                        appList.add(pkg)
                    }
                }

                if (appList.size >= 2) {
                    folders[id] = AppFolder(id, name, appList)
                }
            }
        } catch (_: Exception) {
            folders.clear()
        }
    }

    fun save() {
        val pageData = pages.joinToString(";") { page ->
            page.joinToString(",") { it ?: "" }
        }

        val dockData = dock.joinToString(",") { it ?: "" }

        val folderArray = JSONArray()

        for (folder in folders.values) {
            val item = JSONObject()
            val appArray = JSONArray()

            folder.apps.forEach { appArray.put(it) }

            item.put("id", folder.id)
            item.put("name", folder.name)
            item.put("apps", appArray)

            folderArray.put(item)
        }

        prefs.edit()
            .putString("pages", pageData)
            .putString("dock", dockData)
            .putString("folders", folderArray.toString())
            .apply()
    }

    /**
     * Elimina aplicaciones desinstaladas,
     * conserva las carpetas válidas y coloca las nuevas.
     */
    fun reconcile(apps: Map<String, AppItem>) {
        val folderPackages = HashSet<String>()

        // Limpiar el contenido de las carpetas.
        val folderIterator = folders.iterator()

        while (folderIterator.hasNext()) {
            val folder = folderIterator.next().value

            folder.apps.removeAll { pkg ->
                !apps.containsKey(pkg) || !folderPackages.add(pkg)
            }

            if (folder.apps.size < 2) {
                folderIterator.remove()
            }
        }

        // Eliminar referencias a carpetas inexistentes.
        val referencedFolders = HashSet<String>()

        fun cleanFolderSlots(array: Array<String?>) {
            for (i in array.indices) {
                val value = array[i] ?: continue

                if (isFolderSlot(value)) {
                    val id = folderIdFromSlot(value)

                    if (id == null || !folders.containsKey(id)) {
                        array[i] = null
                    } else {
                        referencedFolders.add(id)
                    }
                }
            }
        }

        pages.forEach { cleanFolderSlots(it) }
        cleanFolderSlots(dock)

        // Eliminar carpetas que ya no tienen una posición.
        folders.keys.retainAll(referencedFolders)

        val placed = HashSet<String>()

        // Las aplicaciones dentro de carpetas ya están colocadas.
        for (id in referencedFolders) {
            folders[id]?.apps?.forEach { placed.add(it) }
        }

        // Limpiar aplicaciones normales de las páginas.
        fun cleanApps(array: Array<String?>) {
            for (i in array.indices) {
                val value = array[i] ?: continue

                if (isFolderSlot(value)) continue

                if (!apps.containsKey(value) || !placed.add(value)) {
                    array[i] = null
                }
            }
        }

        pages.forEach { cleanApps(it) }
        cleanApps(dock)

        if (firstRun) {
            val wanted = listOf(
                listOf("phone", "teléfono", "telefono"),
                listOf("messages", "mensajes", "messaging"),
                listOf("browser", "internet", "navegador", "chrome"),
                listOf("camera", "cámara", "camara")
            )

            var dockIndex = 0

            for (names in wanted) {
                val match = apps.values.firstOrNull {
                    it.label.lowercase() in names &&
                        !placed.contains(it.pkg)
                }

                if (match != null && dockIndex < 4) {
                    dock[dockIndex] = match.pkg
                    placed.add(match.pkg)
                    dockIndex++
                }
            }

            for (i in 0 until 4) {
                if (dock[i] == null) {
                    val next = apps.values.firstOrNull {
                        !placed.contains(it.pkg)
                    } ?: break

                    dock[i] = next.pkg
                    placed.add(next.pkg)
                }
            }

            firstRun = false
        }

        // Colocar aplicaciones nuevas.
        for (app in apps.values) {
            if (placed.contains(app.pkg)) continue

            var done = false

            for (page in pages) {
                val index = page.indexOfFirst { it == null }

                if (index >= 0) {
                    page[index] = app.pkg
                    done = true
                    break
                }
            }

            if (!done) {
                val newPage = arrayOfNulls<String>(perPage)
                newPage[0] = app.pkg
                pages.add(newPage)
            }

            placed.add(app.pkg)
        }

        trimEmptyPages()
    }

    /**
     * Crea una carpeta a partir de dos aplicaciones.
     * La carpeta ocupa la posición de la primera aplicación.
     */
    fun createFolder(
        firstPkg: String,
        secondPkg: String,
        name: String = "Carpeta"
    ): AppFolder? {
        if (firstPkg == secondPkg) return null

        val location = findPackage(firstPkg)
            ?: findPackage(secondPkg)

        removePackage(firstPkg)
        removePackage(secondPkg)

        val id = UUID.randomUUID().toString()

        val folder = AppFolder(
            id = id,
            name = name,
            apps = mutableListOf(firstPkg, secondPkg)
        )

        folders[id] = folder

        val slot = folderSlot(id)

        if (location != null) {
            if (location.first) {
                dock[location.third] = slot
            } else {
                pages.getOrNull(location.second)?.set(location.third, slot)
            }
        } else {
            placeInFirstEmptySlot(slot)
        }

        save()
        return folder
    }

    /**
     * Añade una aplicación a una carpeta existente.
     */
    fun addAppToFolder(folderId: String, pkg: String): Boolean {
        val folder = folders[folderId] ?: return false

        if (folder.apps.contains(pkg)) return false

        removePackage(pkg)

        folders.values.forEach { other ->
            if (other.id != folderId) {
                other.apps.remove(pkg)
            }
        }

        folder.apps.add(pkg)
        save()

        return true
    }

    /**
     * Quita una aplicación de una carpeta.
     * Si queda una sola aplicación, la carpeta desaparece
     * y la aplicación restante ocupa su posición.
     */
    fun removeAppFromFolder(folderId: String, pkg: String): Boolean {
        val folder = folders[folderId] ?: return false

        if (!folder.apps.remove(pkg)) return false

        if (folder.apps.size < 2) {
            val remaining = folder.apps.firstOrNull()
            val slot = folderSlot(folderId)

            replaceSlot(slot, remaining)

            folders.remove(folderId)
        }

        save()
        return true
    }

    fun renameFolder(folderId: String, newName: String): Boolean {
        val folder = folders[folderId] ?: return false

        val cleanName = newName.trim()
        if (cleanName.isEmpty()) return false

        folder.name = cleanName
        save()

        return true
    }

    private fun findPackage(pkg: String): Triple<Boolean, Int, Int>? {
        for (pageIndex in pages.indices) {
            val index = pages[pageIndex].indexOf(pkg)

            if (index >= 0) {
                return Triple(false, pageIndex, index)
            }
        }

        val dockIndex = dock.indexOf(pkg)

        if (dockIndex >= 0) {
            return Triple(true, -1, dockIndex)
        }

        return null
    }

    private fun removePackage(pkg: String) {
        pages.forEach { page ->
            for (i in page.indices) {
                if (page[i] == pkg) page[i] = null
            }
        }

        for (i in dock.indices) {
            if (dock[i] == pkg) dock[i] = null
        }

        folders.values.forEach { folder ->
            folder.apps.remove(pkg)
        }
    }

    private fun replaceSlot(oldValue: String, newValue: String?) {
        for (page in pages) {
            for (i in page.indices) {
                if (page[i] == oldValue) {
                    page[i] = newValue
                    return
                }
            }
        }

        for (i in dock.indices) {
            if (dock[i] == oldValue) {
                dock[i] = newValue
                return
            }
        }
    }

    private fun placeInFirstEmptySlot(value: String) {
        for (page in pages) {
            val index = page.indexOfFirst { it == null }

            if (index >= 0) {
                page[index] = value
                return
            }
        }

        val newPage = arrayOfNulls<String>(perPage)
        newPage[0] = value
        pages.add(newPage)
    }

    fun trimEmptyPages(currentPage: Int = 0): Int {
        while (
            pages.size > 1 &&
            pages[pages.size - 1].all { it == null }
        ) {
            pages.removeAt(pages.size - 1)
        }

        return if (currentPage > pages.size - 1) {
            pages.size - 1
        } else {
            currentPage
        }
    }

    fun askedNotif(): Boolean =
        prefs.getBoolean("askedNotif", false)

    fun setAskedNotif() {
        prefs.edit().putBoolean("askedNotif", true).apply()
    }
}
