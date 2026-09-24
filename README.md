# DnDSound

Бесплатное open-source приложение для мастеров и игроков D&D: звуковая атмосфера сессии на одном экране. Свободный аналог Soundtale, полностью офлайн.

*(English summary below.)*

## Что это

Три независимые части, играющие одновременно:

1. **Музыка** — выбирается на круглом «колесе настроений» (happy, epic, sad, tense, creepy, mystic, magical, funny + спокойный центр), с боевым режимом.
2. **Окружение (ambience)** — слоёные фоновые звуки: лес, болото, пещера и т.д. Бесшовные петли + случайные «споты» (птицы, волчий вой) + погода.
3. **Одиночные звуки (one-shots)** — гоблин, удар меча, гром. Играются мгновенно по нажатию.

Плюс **сцены** — сохранённые пресеты всех трёх частей, включаемые одним касанием.

## Приватность

- **В манифесте нет разрешения `INTERNET`.** Приложение физически не может выйти в сеть.
- Медиабиблиотека Media3 добавляет два служебных разрешения: `ACCESS_NETWORK_STATE` (оценка пропускной способности плеером) и `WAKE_LOCK` (фоновое воспроизведение). Сеть они не открывают, приложение работает офлайн.
- Без рекламы, подписок, аккаунтов и аналитики.
- Ваша музыка никуда не копируется — приложение только индексирует выбранную вами папку.

## Контент

Приложение не поставляет музыку и звуки. Один раз выберите папку с вашей библиотекой — структура папок описана в [docs/CONTENT.md](docs/CONTENT.md). Для разработки есть генератор синтетических звуков: `scripts/gen-samples.sh`.

## Сборка из исходников (Linux, без Android Studio)

```bash
scripts/00-install-deps.sh        # JDK, unzip, adb, ffmpeg (Arch/Debian)
scripts/01-setup-android-sdk.sh   # Android SDK в ~/Android/Sdk
scripts/build-debug.sh            # APK: app/build/outputs/apk/debug/
scripts/test.sh                   # unit-тесты :core
```

Установка на устройство: `scripts/install-adb.sh` (в WSL2 — беспроводная отладка, скрипт подскажет).

## Лицензия

GPL-3.0-or-later, см. [LICENSE](LICENSE).

---

# DnDSound (English)

Free, open-source sound-atmosphere app for D&D game masters and players: music, layered ambience and one-shot sounds on a single screen, mixed in real time. A free Soundtale alternative, fully offline.

**Privacy guarantee:** the manifest contains no `INTERNET` permission — the app cannot go online. No ads, no accounts, no analytics. Your audio files are never copied; the app only indexes the folder you choose.

There is no bundled content: point the app at a folder with your own audio (folder conventions in [docs/CONTENT.md](docs/CONTENT.md)).

Build from source on a clean Linux machine:

```bash
scripts/00-install-deps.sh
scripts/01-setup-android-sdk.sh
scripts/build-debug.sh   # produces app/build/outputs/apk/debug/app-debug.apk
```

Licensed under GPL-3.0-or-later. Screenshots: *(placeholder — coming with the UI stage)*
