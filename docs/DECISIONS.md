# Решения

Дата | Решение | Причина
--- | --- | ---
2026-09-24 | Версии: AGP 9.4.1, Kotlin 2.4.20, Gradle 9.7.1, Compose BOM 2026.09.00, JDK 21 (компиляция в 17), compileSdk/targetSdk 37, build-tools 37.0.0, JUnit 6.1.3 | Проверены по Google Maven / Maven Central / services.gradle.org в день начала проекта; AGP 9 требует JDK 17+ и Gradle ≥ 9.6
2026-09-24 | DI — ручной (без Koin/Hilt), пока приложение маленькое | Для двух модулей ручной DI проще и без кодогенерации; Koin добавим, если граф вырастет
2026-09-24 | AGP 9 встроенный Kotlin: плагин `org.jetbrains.kotlin.android` не подключается, только `kotlin.plugin.compose` | Так работает AGP 9.x; проверено на рабочем примере
2026-09-24 | `:core` тестируется на JUnit 6 (преемник JUnit 5, `useJUnitPlatform`) | JUnit 5 переименован в 6.x; API Jupiter тот же
2026-09-24 | compileSdk/targetSdk = 37, пакет SDK называется `platforms;android-37.0` (новая схема именования с extension-суффиксом) | Проверено через `sdkmanager --list`; AGP 9.4 поддерживает максимум API 37
