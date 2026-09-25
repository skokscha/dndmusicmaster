# Источники бесплатной музыки (fetch-music.sh)

`scripts/fetch-music.sh` скачивает готовый набор D&D-музыки (55 треков на все
25 зон колеса + 4 боевые зоны) в `local-music/` по структуре из `docs/CONTENT.md`.

- **Аудио не попадает в репозиторий**: `local-music/` в `.gitignore`. В git
  лежат только манифест (`scripts/music_manifest.tsv`), сам скрипт и
  сгенерированный `local-music/ATTRIBUTION.md` с полными кредитами.
- Только **CC0** (public domain) и **CC-BY 3.0/4.0** (нужна атрибуция — она
  генерируется скриптом автоматически). CC-BY-SA и кастомные лицензии
  (OGA-BY) не используются.
- Битые/недоступные URL проверяются заранее: `scripts/fetch-music.sh --check`.

## Использование

```bash
scripts/fetch-music.sh            # скачать всё (~200 МБ) в local-music/
scripts/fetch-music.sh --check    # только проверить доступность всех URL
```

Затем скопируйте `local-music/music/` в корень библиотеки на устройстве
(или `adb push local-music/music/. <корень библиотеки>/music/`) и пересканируйте.

## Источники

| Источник | Лицензия | Что взято |
| --- | --- | --- |
| [OpenGameArt](https://opengameart.org) | CC0 / CC-BY 3.0 / CC-BY 4.0 (у каждого файла своя, см. страницу ассета) | 28 оркестровых треков: деревни, таверны, рынки, битвы, данжи, мистика (RandomMind, Matthew Pablo, cynicmusic, Hitctrl, MintoDog и др.) |
| [incompetech (Kevin MacLeod)](https://incompetech.com) | CC-BY 4.0 на всю коллекцию | 27 треков: «Frost Waltz», «Danse Macabre», «Ossuary», «Village Consort», «Sneaky-стиль» и т.д. |

## Распределение по зонам

Каждая строка манифеста = файл → папка зоны. Осмысленное соответствие
«настроение трека ↔ зона» кураторилось вручную: например, «Medieval: Market Day»
→ `happy/vivid-town`, «Dungeon of Agony» → `creepy/creepy`, «Great Labyrinth» →
переход `haunted` (creepy↔mystic), «Sardana» → переход `festive` (funny↔happy).
Изменить раскладку можно, просто отредактировав колонку `target_path` в
манифесте — скрипт разложит файлы по новым папкам.

MP3-исходники incompetech и часть OGA-файлов — MP3: для музыкальных зон это
нормально (шов не критичен), для бесшовных петель окружений (`ambience/`,
`weather/`) по-прежнему нужны OGG/Opus — их этот скрипт не трогает.
