# RURay

<div align="center">
  <img src="ruray.jpeg" width="120" height="120" alt="RURay"/>
  
  **обход белых списков для России**
  
  [Скачать APK](https://github.com/corgisolutions/ruray/releases/latest)
</div>

---

## 🇷🇺 Русский

### Что происходит

Сотовую связь глушат «ради нашей безопасности от дронов». Радиоволнам, конечно, всё равно — сидите вы в MAX или в Instagram. Это тестирование белых списков.

Постановление Правительства РФ №1667 от 27.10.2025 вступает в силу **01.03.2026**. Оно даёт Роскомнадзору полные права на изоляцию российского сегмента интернета. Провайдеры будут обязаны пропускать трафик только к «одобренным» ресурсам.

Мы к этому готовимся.

### ⚠️ Важно

**Синхронизируйте серверы заранее. Потяните экран вниз чтобы загрузить список серверов.**

Если белые списки введут полностью и GitHub перестанет работать — а все серверы в вашем списке уже мертвы — подключиться будет невозможно. Навсегда.

Варианты:
- Открывайте приложение хотя бы раз в неделю и обновляйте список
- Включите фоновое сканирование в настройках — приложение будет проверять серверы автоматически

**Про синхронизацию:** при загрузке списка серверов используется ваш текущий (возможно реальный) IP-адрес. Если это важно — делайте первую синхронизацию через другой рабочий VPN. Это рекомендация, не требование.

### Как это работает

RURay подключается к серверам через **XTLS-Reality + VLESS**. Эта технология маскирует VPN-трафик под обычный HTTPS — для ТСПУ он выглядит как обращение к обычному сайту.

Серверы расположены на IP-адресах, которые попадают под белые списки (MAX, VK, и т.д.). Даже при полной изоляции — соединение должно работать.

### Возможности

- **Автоматический поиск** — приложение само находит и тестирует рабочие серверы
- **Быстрое подключение** — подключение к лучшему серверу за секунды
- **Фоновая проверка** — серверы проверяются в фоне, мёртвые удаляются
- **Split tunneling** — можно исключить приложения из VPN (госуслуги, банки)
- **Автообнаружение** — госприложения автоматически добавляются в обход
- **Предупреждения** — предупреждаем о предустановленных приложениях слежки
- **Быстрая плитка** — включение/выключение из шторки
- **SOCKS5 прокси** — можно использовать без VPN, для браузера или других приложений
- **Автообновление** — проверка и установка обновлений через сам VPN

### Про госприложения

Приложения типа «Госуслуги», VK, банки — могут определить, что у вас включен VPN. Само по себе использование VPN не запрещено, но...

Приложение автоматически добавляет обнаруженные госприложения в обход VPN. Их трафик идёт напрямую, они не видят вашу активность в VPN. Но они всё равно могут увидеть, что VPN активен.

Если на телефоне есть **предустановленные** системные госприложения (например, MAX на некоторых устройствах) — мы покажем предупреждение. Они имеют повышенные привилегии и потенциально опасны.

### ⚠️ Распространение

**Использование VPN в России (пока что) легально.** Закон разрешает VPN для защиты данных, удалённой работы и доступа к незаблокированным ресурсам.

**Реклама и распространение VPN для обхода блокировок — нет.**

С 1 сентября 2025 года действует закон №281-ФЗ. Штрафы за рекламу средств обхода блокировок:

| | Граждане | Должностные лица | Юрлица |
|---|---|---|---|
| Первое нарушение | 50–80 тыс. ₽ | 80–150 тыс. ₽ | 200–500 тыс. ₽ |
| Повторное | 100–200 тыс. ₽ | 200–300 тыс. ₽ | до 1 млн ₽ |

Распространяя это приложение, вы принимаете на себя эти риски. Мы не призываем нарушать закон.

### Установка

1. Скачайте APK из [Releases](https://github.com/corgisolutions/ruray/releases/latest)
2. Установите (может потребоваться разрешить установку из неизвестных источников)
3. Потяните вниз чтобы загрузить список серверов
4. Нажмите «Подключить»

### Работает ли за пределами России?

Серверы в основном расположены в России и оптимизированы под российские белые списки. За пределами РФ приложение будет работать, но выбор серверов ограничен — есть смысл только если нужен именно российский IP.

### Лицензия

MIT. Делайте что хотите.

---

## 🇬🇧 English

### What's happening

Cellular networks in Russia are being "jammed" supposedly "for safety against drones". Radio waves don't care if you're on a government app or Instagram. This is whitelist testing.

Government decree №1667 (27.10.2025) comes into effect **01.03.2026**. It gives Roskomnadzor full authority to isolate the Russian internet segment. ISPs will be required to allow traffic only to "approved" resources.

We're preparing for this.

### ⚠️ Important

**Sync servers in advance. Pull down on the screen to fetch the server list.**

If whitelisting is fully implemented and GitHub stops working — and all servers in your list are already dead — you won't be able to connect. Ever.

Options:
- Open the app at least once a week and refresh the list
- Enable background scanning in settings — the app will check servers automatically

**About syncing:** fetching the server list uses your current (probably real) IP address. If this matters to you — do the first sync through another working VPN. This is a recommendation, not a requirement.

### How it works

RURay connects to servers via **XTLS-Reality + VLESS**. This technology disguises VPN traffic as regular HTTPS — to deep packet inspection it looks like a normal website visit.

Servers are located on IP addresses that fall under whitelists (MAX, VK, etc.). Even under full isolation — connection should work.

### Features

- **Auto-scan** — automatically finds and tests working servers
- **Quick connect** — connects to the best server in seconds
- **Background scanning** — servers are checked in background, dead ones removed
- **Split tunneling** — exclude apps from VPN (banking, government services)
- **Auto-detection** — government apps automatically added to bypass list
- **Warnings** — alerts about pre-installed surveillance apps
- **Quick tile** — toggle from notification shade
- **SOCKS5 proxy** — use without VPN mode, for browser or other apps
- **Auto-update** — checks and installs updates through the VPN itself

### About government apps

Apps like government services, VK, banks — can detect that VPN is active. Using a VPN itself is not illegal, but...

The app automatically adds detected government apps to VPN bypass. Their traffic goes directly, they don't see your VPN activity. But they can still see that VPN is active.

If there are **pre-installed** system government apps on your phone — we'll show a warning. They have elevated privileges and are potentially dangerous.

### ⚠️ Distribution

**Using a VPN in Russia is legal (for now).** The law permits VPN for data protection, remote work, and accessing non-blocked resources.

**Advertising and distributing VPN for bypassing blocks is not.**

Since September 1, 2025, law №281-FZ is in effect. Fines for advertising bypass tools:

| | Individuals | Officials | Legal entities |
|---|---|---|---|
| First offense | 50–80k ₽ (~$550–900) | 80–150k ₽ (~$900–1,700) | 200–500k ₽ (~$2,200–5,500) |
| Repeat | 100–200k ₽ (~$1,100–2,200) | 200–300k ₽ (~$2,200–3,300) | up to 1M ₽ (~$11,000) |

By distributing this app, you accept these risks. We do not encourage breaking the law.

### Installation

1. Download APK from [Releases](https://github.com/corgisolutions/ruray/releases/latest)
2. Install (may need to allow installation from unknown sources)
3. Pull down to fetch server list
4. Tap "Connect"

### Does it work outside Russia?

Servers are mostly located in Russia and optimized for Russian whitelists. The app will work outside Russia, but server selection is limited — only useful if you specifically need a Russian IP.

### License

MIT. Do whatever you want.

---

## Third-party components

- **MaxMind GeoIP2** — https://github.com/maxmind/GeoIP2-java

- **XTLS/Xray-core** — https://github.com/XTLS/Xray-core

- **heiher/hev-socks5-tunnel** — https://github.com/heiher/hev-socks5-tunnel

- **Libv2ray** — https://github.com/2dust/AndroidLibXrayLite

Thank you to these projects and their contributors for their excellent work!

---

<p align="center">
  <img src="app/src/main/res/drawable/logo.gif" width="32" height="32" alt="" align="middle"/>
  <sub>@corgisolutions</sub>
</p>
