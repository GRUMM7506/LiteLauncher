# Lite Launcher

A small, fast Android launcher with smooth, finger-driven motion. No network access, no analytics, about 300 KB.

Built for and tested on one low-end phone: Samsung Galaxy A12 (Android 12, 3 GB RAM). It targets Android 11 and newer, but other devices are untested, so expect rough edges.

[Русский](#русский)

## Features

- **App grid:** 5×7 by default, swipes left and right. Columns, rows and icon size are configurable.
- **Automatic folders:** apps are sorted into categories such as Social, Productivity and Utilities, like the Windows 11 Start menu. A folder appears once a category has 3 apps. You can turn this off.
- **Motion:** opening the grid follows your finger and settles on a spring, and you can catch it mid-flight.
- **Wallpapers drawn in code:** a dark and a light variant that switch with the system theme, plus a light live wallpaper that only draws while visible.
- **Widgets, hidden apps, pinned apps, search.**

Long-press an empty spot on the home screen for widgets, wallpaper and settings.

## Install

Download the APK from [Releases](https://github.com/GRUMM7506/LiteLauncher/releases), install it, then choose it under **Settings → Apps → Default apps → Home app**.

## Permissions

| Permission | Why |
|---|---|
| `REQUEST_DELETE_PACKAGES` | The "Uninstall" menu item |
| `EXPAND_STATUS_BAR` | Swipe down for notifications |
| `SET_WALLPAPER` | The Lite wallpaper, only if you turn it on |

There is no `INTERNET` permission.

## Build

Requires Android Studio's JDK and the Android SDK.

```bash
./gradlew assembleRelease
```

Without a `keystore.properties` file, the release build is signed with your debug key.

## Русский

Маленький и быстрый лаунчер для Android с плавным управлением за пальцем. Без доступа к сети и аналитики, около 300 КБ.

Сделан и проверен на одном недорогом телефоне: Samsung Galaxy A12 (Android 12, 3 ГБ RAM). Работает на Android 11 и новее, но на других устройствах не проверялся.

- **Сетка приложений:** по умолчанию 5×7, листается вправо-влево. Колонки, ряды и размер иконок настраиваются.
- **Автоматические папки:** приложения сортируются по категориям, как в меню «Пуск» Windows 11. Папка появляется от 3 приложений, функцию можно выключить.
- **Движение:** список открывается за пальцем и доводится пружиной, его можно поймать на лету.
- **Обои, нарисованные кодом:** тёмные и светлые меняются вместе с темой системы, а лёгкие живые обои рисуют только пока видны.
- **Виджеты, скрытие и закрепление приложений, поиск.**

Долгое нажатие на пустое место главного экрана открывает виджеты, обои и настройки.

**Установка:** скачайте APK из [Releases](https://github.com/GRUMM7506/LiteLauncher/releases), установите и выберите его в **Настройки → Приложения → Выбор приложений по умолчанию → Главный экран**.
