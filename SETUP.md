# Подготовка окружения (Windows)

## 1. Установка (PowerShell от администратора)
```powershell
winget install Google.AndroidStudio
winget install Git.Git
```
Запустите Android Studio → мастер установки → **Standard** (скачает SDK, Platform-Tools, эмулятор можно не ставить).

## 2. Переменные окружения (PowerShell, обычный)
```powershell
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Android\Android Studio\jbr", "User")
[Environment]::SetEnvironmentVariable("ANDROID_HOME", "$env:LOCALAPPDATA\Android\Sdk", "User")
$p = [Environment]::GetEnvironmentVariable("Path", "User")
[Environment]::SetEnvironmentVariable("Path", "$p;C:\adb;$env:LOCALAPPDATA\Android\Sdk\platform-tools", "User")
```
Перезапустите PowerShell и проверьте: `java -version`, `adb version`.

## 3. Создание проекта в Android Studio
New Project → **Empty Views Activity**:
- Name: `Lite Launcher`
- Package name: `com.grumm.litelauncher`
- Save location: `C:\dev\LiteLauncher`
- Language: **Kotlin**
- Minimum SDK: **API 30**
- Build configuration language: **Kotlin DSL**

Дождитесь окончания Gradle Sync, закройте Android Studio.

## 4. Git и CLAUDE.md
```powershell
cd C:\dev\LiteLauncher
copy $HOME\Downloads\CLAUDE.md .
git init
git add .
git commit -m "Шаблон проекта"
.\gradlew.bat assembleDebug
```
Последняя команда должна закончиться `BUILD SUCCESSFUL`.

## 5. Claude Code
Установка — по официальной инструкции: https://docs.claude.com/en/docs/claude-code/setup
Затем:
```powershell
cd C:\dev\LiteLauncher
claude
```
Подключите телефон по USB (отладка включена) и вставьте стартовый промпт.

## Стартовый промпт для Claude Code
```
Прочитай CLAUDE.md — там требования, правила производительности и целевое устройство.
Проект создан из шаблона Empty Views Activity и собирается.

Задача: реализовать лаунчер v1 целиком.
1. Почисти шаблон: убери appcompat/material/constraintlayout и лишние ресурсы, переведи на android.app.Activity и системную тему с обоями.
2. Настрой манифест как HOME-лаунчер.
3. Реализуй все функции v1 из CLAUDE.md.
4. Настрой release-сборку с R8.
5. Собери, установи на подключённый телефон, проверь logcat на ошибки и замерь память через dumpsys meminfo. Если PSS выше 40 МБ — оптимизируй.
6. Коммить каждый логический шаг.

Работай самостоятельно, вопросы задавай только если что-то реально блокирует. В конце коротко отчитайся: что сделано, PSS, как назначить лаунчер по умолчанию.
```
