# Lite Launcher — контекст проекта

Личный лаунчер для Samsung Galaxy A12 (SM-A125F). Главная цель — **минимальное потребление RAM и мгновенный отклик**. Всё остальное вторично.

## Целевое устройство
- Samsung Galaxy A12 SM-A125F, Android 12 (One UI 4.1, API 31), root (Magisk)
- MediaTek Helio P35 (8× Cortex-A53), **3 ГБ RAM**, eMMC, экран 720×1600
- Система агрессивно выгружает процессы → холодный старт лаунчера должен быть быстрым

## Окружение разработчика
- Windows, PowerShell. Команды — только для PowerShell, не bash.
- Android Studio (JDK из `C:\Program Files\Android\Android Studio\jbr`), Android SDK
- ADB: `C:\adb\adb.exe` (или `adb` из PATH)
- Сборка: `.\gradlew.bat assembleDebug`, установка: `.\gradlew.bat installDebug`
- Общение и комментарии к коммитам — на русском. UI-строки — на русском (`res/values/strings.xml`).

## Стек и жёсткие правила производительности
- **Kotlin + классические Android View.** Без Jetpack Compose, без Flutter/React Native.
- Базовый класс — `android.app.Activity` (не `AppCompatActivity`). Тема — `@android:style/Theme.DeviceDefault.NoActionBar` с `android:windowShowWallpaper=true` и прозрачным фоном.
- Разрешённые зависимости: `androidx.core:core-ktx`, `androidx.recyclerview:recyclerview`. Любую другую — только с обоснованием. Шаблонные `appcompat`, `material`, `constraintlayout` — удалить.
- Без сети, без аналитики, без разрешения `INTERNET`, без `QUERY_ALL_PACKAGES` (используй `LauncherApps` и `<queries>` с интентом MAIN/LAUNCHER).
- Список приложений — через `LauncherApps.getActivityList()`; обновление — `LauncherApps.Callback` (установка/удаление/обновление), без полной перезагрузки списка.
- Иконки грузить в фоновом потоке (один `Executor`), кэшировать в `LruCache` с ограничением по размеру, масштабировать до размера ячейки. В UI-потоке не делать IO и декодирования.
- Лейаут — плоский (`FrameLayout`/`LinearLayout`), без лишней вложенности. `RecyclerView` с `setHasFixedSize(true)`, стабильными ID.
- Настройки (закреплённые приложения) — `SharedPreferences`.
- Release: `isMinifyEnabled = true`, `isShrinkResources = true`, R8. Для личного использования release можно подписывать debug-ключом.
- `minSdk = 30`, `targetSdk`/`compileSdk` — последние стабильные, доступные в SDK.

## Манифест лаунчера
- Activity с `intent-filter`: `MAIN` + `HOME` + `DEFAULT`
- `launchMode="singleTask"`, `stateNotNeeded="true"`, `excludeFromRecents="true"`, `clearTaskOnLaunch="true"`, `resumeWhilePausing="true"`, `screenOrientation="portrait"`
- Кнопка «Назад» на главном экране ничего не делает; в списке приложений — закрывает его.

## Функции v1
1. Главный экран: крупные часы и дата (системный формат, обновление по `ACTION_TIME_TICK`), под ними 4–6 закреплённых приложений (иконка + подпись).
2. Свайп вверх (или тап по пустому месту) → полноэкранный список всех приложений по алфавиту, поле поиска сверху с автофокусом и открытой клавиатурой; поиск по названию без учёта регистра, Enter запускает первое совпадение.
3. Долгий тап по приложению → меню: «Закрепить/Открепить», «О приложении» (`ACTION_APPLICATION_DETAILS_SETTINGS`), «Удалить» (`ACTION_DELETE`).
4. Системные обои, тёмная полупрозрачная подложка у списка, читаемый текст.
5. Нажатие «Домой» в списке → возврат на главный экран.

## Критерии качества
- Debug-сборка собирается без предупреждений-ошибок, release — с R8.
- PSS лаунчера после запуска: цель < 40 МБ (`adb shell dumpsys meminfo <package>`).
- Открытие списка приложений — без видимых подтормаживаний на A12.
- Код без мёртвых файлов шаблона, понятные имена, короткие функции.

## Рабочий цикл
1. Внести изменения → `.\gradlew.bat assembleDebug`.
2. Если телефон подключён (`adb devices`) → `.\gradlew.bat installDebug`, затем `adb shell am start -n <package>/.MainActivity`.
3. Проверить логи: `adb logcat -d *:E | Select-String <package>`.
4. Коммитить логическими шагами (git).
