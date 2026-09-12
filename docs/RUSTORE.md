# RuStore release guide

Для первого релиза используем **signed APK**. RuStore также поддерживает AAB, но APK требует меньше шагов для первого выпуска.

## 1. Один раз создать release key

Нужен JDK 17+.

```bash
keytool -genkeypair -v \
  -keystore passive-memory-recorder-release.jks \
  -alias passive-memory-recorder \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Сохраните `.jks` и пароли в надёжном месте. **Не коммитьте ключ в Git.** Все будущие обновления должны быть подписаны тем же ключом.

## 2. Локальная signed release-сборка

Скопируйте шаблон:

```bash
cp keystore.properties.example keystore.properties
```

Заполните:

```properties
storeFile=passive-memory-recorder-release.jks
storePassword=...
keyAlias=passive-memory-recorder
keyPassword=...
```

Положите `.jks` в корень проекта и выполните:

```bash
gradle :app:assembleRelease
```

Готовый файл:

```text
app/build/outputs/apk/release/app-release.apk
```

## 3. Автоматическая release-сборка в GitHub Actions

Добавьте в GitHub repository → **Settings → Secrets and variables → Actions** четыре secrets:

- `PMR_KEYSTORE_BASE64`
- `PMR_KEYSTORE_PASSWORD`
- `PMR_KEY_ALIAS`
- `PMR_KEY_PASSWORD`

### Получить `PMR_KEYSTORE_BASE64`

Linux/macOS:

```bash
base64 < passive-memory-recorder-release.jks | tr -d '\n'
```

PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("passive-memory-recorder-release.jks"))
```

После добавления secrets откройте **Actions → RuStore release → Run workflow**.

Workflow соберёт и проверит подпись, а затем создаст artifact `passive-memory-recorder-rustore` с:

```text
app-release.apk
app-release.aab
```

Для первой публикации в RuStore используйте `app-release.apk`.

## 4. Первая установка на Galaxy S26

### Вариант A — signed release APK

Это предпочтительный вариант перед публикацией в RuStore.

1. Скачайте `app-release.apk` на телефон.
2. Откройте файл через **Мои файлы / My Files**.
3. Если Android попросит, разрешите этому приложению устанавливать неизвестные приложения.
4. Нажмите **Установить**.
5. Запустите **Passive Memory Recorder**.
6. Разрешите:
   - микрофон;
   - уведомления.
7. Разрешите исключение из оптимизации батареи, когда Android покажет системный запрос.
8. Нажмите **ВКЛЮЧИТЬ ЗАПИСЬ**.
9. Проверьте, что появился файл в:

   ```text
   Internal storage / Music / PassiveMemoryRecorder
   ```

### Вариант B — debug APK для быстрой проверки

Debug APK из обычного workflow `Android build` можно поставить сразу, даже до создания release key.

Важно: debug и release APK подписаны разными ключами. Когда будете переходить на store/release build, Android потребует удалить debug-версию перед установкой release-версии.

Записанные `.m4a` лежат в общей папке `Music/PassiveMemoryRecorder`, поэтому удаление debug-приложения их не удаляет. Настройки самого приложения при удалении сбросятся.

## 5. Настройка записи телефонных звонков на Samsung

Обычное стороннее Android-приложение не может получить системный аудиопоток телефонного разговора. Поэтому телефонные звонки нужно записывать штатным Samsung Phone.

На поддерживаемой региональной прошивке:

```text
Телефон
→ ⋮
→ Настройки
→ Запись вызовов / Record calls
→ Автозапись вызовов / Auto record calls
→ Все вызовы / All calls
```

Наш recorder при этом продолжает быть слоем для окружающих разговоров. Во время телефонного звонка Android отдаёт приоритет звонку; наш микрофонный поток может стать тишиной, но сам звонок не должен ломаться из-за recorder-а.

В дальнейшем cloud-upload слой должен забирать **оба источника**:

```text
Music/PassiveMemoryRecorder/*
Samsung call recordings/*
```

и отправлять их в общий processing pipeline.

## 6. Что загрузить в RuStore

Для первой версии:

- `app-release.apk`;
- иконка 512×512;
- скриншоты интерфейса;
- название и описания;
- контакт разработчика;
- объяснение чувствительных разрешений, если консоль запросит.

### Обоснование разрешений

`RECORD_AUDIO`
: Основная функция приложения — непрерывная запись окружающего аудио по явному действию пользователя.

`FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE`
: Запись должна продолжаться при выключенном экране; во время работы постоянно отображается foreground notification.

`POST_NOTIFICATIONS`
: Показывает статус записи и предупреждение, если запись выключена.

`RECEIVE_BOOT_COMPLETED`
: После перезагрузки приложение не запускает микрофон автоматически, а показывает пользователю уведомление о том, что запись выключена.

`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
: Нужен для длительной непрерывной записи; пользователь сам подтверждает исключение через системный Android-диалог.
