# Источники бесплатного контента (fetch-скрипты)

Три скрипта скачивают готовый D&D-набор в `local-music/` по структуре из
`docs/CONTENT.md`: музыка по зонам колеса + реальные эмбиенты/погода/звуки.

- **Аудио не попадает в репозиторий**: `local-music/` в `.gitignore`. В git
  лежат манифесты, скрипты и документация.
- Ключи API хранятся в `scripts/fetch-keys.local` (gitignored; формат:
  `FREESOUND_TOKEN=…`, `JAMENDO_CLIENT_ID=…`) — их можно получить бесплатно:
  Freesound → «Apply for API key», Jamendo → devportal.jamendo.com.
- Только свободные лицензии; кредиты генерируются автоматически:
  `local-music/ATTRIBUTION*.md`.

## Скрипты

| Скрипт | Источник | Лицензии | Что даёт |
| --- | --- | --- | --- |
| `fetch-music.sh` | [OpenGameArt](https://opengameart.org) + [incompetech](https://incompetech.com) (Kevin MacLeod) | CC0 / CC-BY 3.0 / CC-BY 4.0 | 55 оркестровых треков на все 25 зон колеса + 4 боевые зоны (`music_manifest.tsv`) |
| `fetch-freesound.sh` | [Freesound](https://freesound.org) | только CC0 (фильтр в API) | 39 реальных звуков: 4 окружения (forest/tavern/dungeon/coast с base day+night, spots, layers), погода (дождь/гроза/ветер), одиночные звуки 7 категорий (`freesound_manifest.tsv`) |
| `fetch-jamendo.sh` | [Jamendo](https://www.jamendo.com) | CC BY / BY-NC / BY-NC-ND (записывается лицензия каждого трека) | +7 треков разнообразия в переходы и боевые зоны (`jamendo_manifest.tsv`) |

## Использование

```bash
scripts/fetch-music.sh             # музыка по зонам (~300 МБ)
scripts/fetch-freesound.sh         # эмбиенты/погода/звуки (~15 МБ)
scripts/fetch-jamendo.sh           # дополнительные треки (~25 МБ)
scripts/fetch-music.sh --check     # проверка доступности URL без скачивания
```

Затем скопируйте содержимое `local-music/` в корень библиотеки на устройстве
(или `adb push local-music/<раздел>/. <библиотека>/<раздел>/`) и пересканируйте.

## Свои файлы: сортировщик

Любую подборку треков (свою коллекцию, файлы из любых источников — лицензии
библиотеки контролирует владелец) можно раскидать по зонам вручную или
скриптом `scripts/sort-music.py`:

```bash
scripts/sort-music.py ~/Downloads/dnd_music                 # dry run: показать раскладку
scripts/sort-music.py ~/Downloads/dnd_music --apply         # скопировать в local-music/music
scripts/sort-music.py ... --apply --move                    # перемещать, не копируя
scripts/sort-music.py ... --apply --battle                  # в боевое дерево music/battle/
scripts/sort-music.py ... --map "плач=sad.tragic"           # ручное указание зоны
scripts/sort-music.py --list-zones                          # все зоны и папки
```

Скрипт угадывает зону по имени файла (русские и английские ключевые слова:
«битва», «таверна», «погоня», «epic», «dungeon»…), папки берёт из
`wheel_zones.json` — то есть ровно те же, что понимает приложение.
Нераспознанные файлы не пропадают: попадают в список UNSORTED — их можно
переименовать или принудительно указать зону через `--map`. Существующие
файлы не перезаписываются (добавляется суффикс `_02`). После раскладки —
пересканировать библиотеку в приложении.

## Нюансы

- **Freesound**: токен-авторизация даёт только публичные hq-превью
  (~128 кбит/с ogg) — для эмбиентов и онершотов этого достаточно; оригиналы
  в полном качестве требуют OAuth2. API лимитирует запросы (60/мин) — скрипт
  делает паузы, ретраи и кэширует резолв в `local-music/.freesound-resolved.tsv`
  (удалить файл, чтобы переразрешить после правки манифеста).
- **Jamendo**: каталог в основном под BY-NC / BY-NC-ND — дословное
  распространение с указанием авторства в некоммерческом приложении допустимо;
  конкретная лицензия каждого трека записана в атрибуции. API иногда отвечает
  пустым списком — скрипт ретраит.
- **Раскладка по зонам** меняется правкой манифестов: колонка пути в
  `music_manifest.tsv`, теги/индекс в `jamendo_manifest.tsv`, запросы и окна
  длительности в `freesound_manifest.tsv` — скрипты разложат файлы сами.
- MP3-исходники incompetech/OGA и треки Jamendo — MP3: для музыкальных зон это
  нормально (шов не критичен); бесшовные петли окружений (`base`, `layers/`)
  во Freesound-наборе — OGG.
