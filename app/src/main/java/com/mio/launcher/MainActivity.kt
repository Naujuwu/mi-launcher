package com.mio.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : Activity() {

    private lateinit var home: HomeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        home = HomeView(this)
        setContentView(home)
        ViewCompat.setOnApplyWindowInsetsListener(home) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            home.setInsets(bars.top, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(home)
    }

    override fun onResume() {
        super.onResume()
        home.reloadApps()
        checkNotificationAccess()
    }

    private fun hasNotifAccess(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?: return false
        return flat.split(":").any {
            ComponentName.unflattenFromString(it)?.packageName == packageName
        }
    }

    private fun checkNotificationAccess() {
        if (hasNotifAccess()) {
            if (!NotifService.connected) {
                try {
                    NotificationListenerService.requestRebind(
                        ComponentName(this, NotifService::class.java)
                    )
                } catch (e: Exception) {
                    // sin acceso todavía
                }
            }
            return
        }
        val prefs = getSharedPreferences("layout", Context.MODE_PRIVATE)
        if (prefs.getBoolean("askedNotif", false)) return
        prefs.edit().putBoolean("askedNotif", true).apply()
        AlertDialog.Builder(this)
            .setTitle("Globos de notificaciones")
            .setMessage("Para mostrar los números sobre los iconos, activa el acceso a notificaciones para Mi Launcher.")
            .setPositiveButton("Activar") { _, _ ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            .setNegativeButton("Ahora no", null)
            .show()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        home.goToFirstPage()
    }

    @Deprecated("Launcher: el botón atrás solo vuelve a la primera página")
    override fun onBackPressed() {
        home.goToFirstPage()
    }
}
