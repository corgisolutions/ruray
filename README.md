# RURay

<div align="center">
  <img src="app/src/main/res/drawable/logo.gif" width="120" height="120" alt="RURay"/>
  
  **обход белых списков для России**
  
  [Скачать APK](https://github.com/corgisolutions/ruray/releases/latest)
</div>

---

## 🇷🇺 Русский

### Что происходит

Сотовую связь глушат «ради нашей безопасности от дронов». Радиоволнам, конечно, всё равно — сидите вы в MAX или в Instagram. Это тестирование белых списков.

Постановление Правительства РФ №1667 от 12.10.2024 вступает в силу **01.03.2026**. Оно даёт Роскомнадзору полные права на изоляцию российского сегмента интернета. Провайдеры будут обязаны пропускать трафик только к «одобренным» ресурсам.

Мы к этому готовимся.

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

Government decree №1667 (12.10.2024) comes into effect **01.03.2026**. It gives Roskomnadzor full authority to isolate the Russian internet segment. ISPs will be required to allow traffic only to "approved" resources.

We're preparing for this.

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

<div align="center">
  <sub>@corgisolutions</sub>
</div>