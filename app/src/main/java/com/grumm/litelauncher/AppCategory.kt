package com.grumm.litelauncher

import android.content.pm.ApplicationInfo

/** Категории автосортировки. [OTHER] в папку не собирается. */
enum class AppCategory(val labelRes: Int) {
    GAMES(R.string.category_games),
    SOCIAL(R.string.category_social),
    ENTERTAINMENT(R.string.category_entertainment),
    CREATIVITY(R.string.category_creativity),
    PRODUCTIVITY(R.string.category_productivity),
    FINANCE(R.string.category_finance),
    SHOPPING(R.string.category_shopping),
    NAVIGATION(R.string.category_navigation),
    INFORMATION(R.string.category_information),
    TOOLS(R.string.category_tools),
    OTHER(0);

    companion object {
        /** Большинство приложений не указывает категорию, поэтому сначала смотрим на имя пакета. */
        private val KEYWORDS = listOf(
            SOCIAL to listOf(
                "telegram", "whatsapp", "viber", "messenger", "facebook", "instagram", "vkontakte", "com.vk.",
                "twitter", "discord", "skype", "snapchat", "odnoklassniki", "messaging", "dialer", "contacts",
            ),
            ENTERTAINMENT to listOf(
                "youtube", "musically", "tiktok", "netflix", "spotify", "twitch", "kinopoisk", "soundcloud",
                "music", "player", "video",
            ),
            CREATIVITY to listOf(
                "camera", "gallery", "photo", "capcut", "canva", "snapseed", "lightroom", "picsart",
            ),
            PRODUCTIVITY to listOf(
                "android.apps.docs", "microsoft.office", "calendar", "notes", "gmail", "email", "mail",
                "outlook", "disk", "todo", "notion", "slack", "zoom", "teams", "voicenote",
            ),
            FINANCE to listOf(
                "sberbank", "tinkoff", "tbank", "alfabank", "vtb", "bank", "wallet", "pay", "binance",
            ),
            SHOPPING to listOf("ozon", "wildberries", "avito", "aliexpress", "amazon", "ebay", "market", "shop"),
            NAVIGATION to listOf("maps", "navi", "dublgis", "2gis", "uber", "taxi", "yango", "waze", "transport"),
            INFORMATION to listOf("chrome", "browser", "firefox", "opera", "searchplugin", "news", "weather"),
            TOOLS to listOf("settings", "calculator", "clock", "files", "vending", "samsungapps"),
        )

        fun of(info: ApplicationInfo): AppCategory {
            if (info.category == ApplicationInfo.CATEGORY_GAME) return GAMES
            val packageName = info.packageName.lowercase()
            KEYWORDS.firstOrNull { (_, words) -> words.any(packageName::contains) }?.let { return it.first }
            return fromDeclared(info)
        }

        private fun fromDeclared(info: ApplicationInfo) = when (info.category) {
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> ENTERTAINMENT
            ApplicationInfo.CATEGORY_IMAGE -> CREATIVITY
            ApplicationInfo.CATEGORY_SOCIAL -> SOCIAL
            ApplicationInfo.CATEGORY_NEWS -> INFORMATION
            ApplicationInfo.CATEGORY_MAPS -> NAVIGATION
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> PRODUCTIVITY
            else -> if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0) TOOLS else OTHER
        }
    }
}
