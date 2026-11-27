package com.mio.launcher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotifService : NotificationListenerService() {

    companion object {
        @Volatile var counts: Map<String, Int> = emptyMap()
        @Volatile var dnd: Boolean = false
        @Volatile var connected: Boolean = false
        var onChanged: (() -> Unit)? = null
    }

    override fun onListenerConnected() {
        connected = true
        refresh()
    }

    override fun onListenerDisconnected() {
        connected = false
        counts = emptyMap()
        onChanged?.invoke()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refresh()
    }

    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        refresh()
    }

    private fun refresh() {
        val map = HashMap<String, Int>()
        try {
            val list = activeNotifications ?: emptyArray()
            for (sbn in list) {
                val flags = sbn.notification.flags
                if ((flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue
                if ((flags and Notification.FLAG_ONGOING_EVENT) != 0) continue
                if (!sbn.isClearable) continue
                map[sbn.packageName] = (map[sbn.packageName] ?: 0) + 1
            }
        } catch (e: Exception) {
            // el sistema aún no nos conectó
        }
        counts = map
        val f = currentInterruptionFilter
        dnd = f == INTERRUPTION_FILTER_PRIORITY ||
            f == INTERRUPTION_FILTER_NONE ||
            f == INTERRUPTION_FILTER_ALARMS
        onChanged?.invoke()
    }
}
