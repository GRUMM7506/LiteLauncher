package com.grumm.litelauncher

import android.app.Notification
import android.content.pm.LauncherActivityInfo
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Счётчики уведомлений по приложениям. Только главный поток. Содержимое уведомлений не хранится. */
object NotificationBadges {
    private data class Source(val packageName: String, val user: UserHandle)

    private val active = HashMap<String, Source>()
    private var counts = emptyMap<Source, Int>()

    var onChanged: (() -> Unit)? = null

    fun count(info: LauncherActivityInfo) =
        counts[Source(info.componentName.packageName, info.user)] ?: 0

    fun replaceAll(notifications: List<StatusBarNotification>) {
        active.clear()
        notifications.forEach { active[it.key] = Source(it.packageName, it.user) }
        publish()
    }

    fun put(notification: StatusBarNotification) {
        active[notification.key] = Source(notification.packageName, notification.user)
        publish()
    }

    fun remove(key: String) {
        if (active.remove(key) != null) publish()
    }

    fun clear() {
        active.clear()
        publish()
    }

    private fun publish() {
        counts = active.values.groupingBy { it }.eachCount()
        onChanged?.invoke()
    }
}

/** Считает уведомления для значков. Работает, только если пользователь выдал доступ к уведомлениям. */
class BadgeListenerService : NotificationListenerService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onListenerConnected() {
        Thread(::loadActive).start()
    }

    override fun onListenerDisconnected() = NotificationBadges.clear()

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        if (countsForBadge(sbn, rankingMap)) NotificationBadges.put(sbn) else NotificationBadges.remove(sbn.key)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) {
        NotificationBadges.remove(sbn.key)
    }

    /** Запрос списка гоняет через binder все уведомления, поэтому не в главном потоке. */
    private fun loadActive() {
        val shown = runCatching {
            val ranking = currentRanking
            activeNotifications.orEmpty().filter { countsForBadge(it, ranking) }
        }.getOrDefault(emptyList())
        mainHandler.post { NotificationBadges.replaceAll(shown) }
    }

    /** Постоянные уведомления и сводки групп не считаем, а настройки канала уважаем. */
    private fun countsForBadge(sbn: StatusBarNotification, rankingMap: RankingMap): Boolean {
        if (sbn.isOngoing || sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        val ranking = Ranking()
        return !rankingMap.getRanking(sbn.key, ranking) || ranking.canShowBadge()
    }
}
