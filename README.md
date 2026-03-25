# 🎮 CraftLLC YouTube Command Mod [CYCM]

**CYCM** is a high-performance Minecraft mod designed for streamers. It bridges **YouTube Live Chat** and **Telegram** directly into your game, giving your audience real-time control over gameplay events through a powerful command system.

<center>
  <img src="https://img.shields.io/modrinth/dt/cycm?style=for-the-badge" alt="Downloads">
  <img src="https://img.shields.io/modrinth/v/cycm?style=for-the-badge" alt="Latest Version">
  <img src="https://img.shields.io/modrinth/game-versions/cycm?style=for-the-badge&label=minecraft" alt="Minecraft Version">
</center>

---

## ✨ Key Features

*   **🛰️ Dual-Source Integration**: Process commands from YouTube and Telegram simultaneously.
*   **🔐 OAuth2 Authentication**: Secure YouTube API integration with token-based authorization.
*   **🚀 Two Connection Modes**: Choose between Direct API polling or Browser Bridge (HTTP) mode.
*   **⛓️ Command Chaining**: Execute multiple actions in one line using `|`.
*   **🔁 Smart Repetitions**: Repeat commands with delays and fair budget distribution.
*   **🛡️ Command Blocking**: Protect your game by blacklisting dangerous commands.
*   **💬 HUD Optimization**: Automatic message grouping (`x2`, `x3`) and real-time status actionbar.
*   **🌍 Multilingual**: Full support for English, Ukrainian, and Russian.
*   **📊 Detailed Logging**: Separate logs for commands and chat messages.

---

## 🛰️ Advanced Syntax

CYCM isn't just a relay; it's a command engine with powerful features.

### ⚡ Command Repetitions & Delays
*   **Basic Syntax**: `/command +[Count] [DelaySeconds]`
*   **Examples**:
    *   `/summon sheep +5` — Spawns 5 sheep instantly.
    *   `/tnt +10 2` — Spawns 10 TNT total, one every 2 seconds.
    *   `/say Hello +3 1` — Says "Hello" 3 times with 1-second intervals.

### 🔗 Command Chaining
Execute multiple commands in sequence using the `|` separator:
*   `/tnt | /summon zombie +5 2 | /say Watch out!`
*   `/tp @p ~ ~10 ~ | /summon creeper`
*   `/say Phase 1 | !sleep 3 | /say Phase 2`

**Fair Budget System**: If the global limit is 20 repeats and you send `/cmd1 +15 | /cmd2 +10`, the mod distributes fairly: `cmd1` gets 15, `cmd2` gets 5 (total = 20).

### `!sleep` Delay Segment
Use `!sleep <seconds>` inside a chain to pause before the next segment:
*   `/summon lightning_bolt | !sleep 2 | /tnt 8 5`
*   `/say Get ready | !sleep 5 | /say Go`

### 🔓 Special Characters
*   Use `\` to escape special symbols: `/say \+10` will literally say "+10" in chat.

---

## ⌨️ Client Commands

### 🎛️ Core Controls

| Command | Description |
| :--- | :--- |
| `/cycm <on\|off>` | Master toggle for the entire mod. |
| `/cycm restart` | Reloads configurations and restarts all services. |
| `/cycm stop` | Stop all scheduled repetitions and delayed commands. |
| `/cycm block <cmd>` | Add a command to the persistent blocklist. |
| `/cycm unblock <cmd\|all>` | Remove command(s) from the blocklist. |
| `/cycm blocklist` | View all currently blocked commands. |
| `/cycm execute <cmd>` | Execute CYCM syntax manually (Alias: `/ce`). |

### 📺 YouTube Integration

| Command | Description |
| :--- | :--- |
| `/cycm ytmode <api\|http>` | Switch between Direct API or Browser Bridge mode. |
| `/cycm youtube key <apiKey>` | Set your YouTube API key. |
| `/cycm youtube id <videoId>` | Set the live stream video ID. |
| `/cycm youtube oa2client <clientId>` | Set OAuth2 Client ID. |
| `/cycm youtube oa2secret <clientSecret>` | Set OAuth2 Client Secret. |
| `/cycm youtube connect` | Start OAuth2 authorization flow. |
| `/cycm youtube send <on\|off>` | Enable/disable sending Minecraft chat to YouTube. |
| `/cycm source <on\|off> youtube` | Enable/disable YouTube as a command source. |

### 📱 Telegram Integration

| Command | Description |
| :--- | :--- |
| `/cycm tg token <token>` | Set your Telegram bot token. |
| `/cycm source <on\|off> telegram` | Enable/disable Telegram as a command source. |

### ⚙️ Settings & Utilities

| Command | Description |
| :--- | :--- |
| `/cycm source list` | View status of all active connections. |
| `/cycm grouping <on\|off>` | Toggle automatic message grouping in chat. |
| `/cycm actionbar <on\|off>` | Toggle the real-time status overlay. |
| `/cycm num <N>` | Set global maximum repetitions per message (default: 20). |
| `/cycm delay <S>` | Set global maximum delay in seconds (default: 5). |
| `/cycm tnt count <N>` | Set the maximum allowed TNT count for `/tnt` (default: 20). |
| `/cycm tnt radius <R>` | Set the maximum allowed TNT ring radius for `/tnt` (default: 8). |
| `/cycm http messages port <port>` | Set the HTTP server port for browser mode. |
| `/cycm http ui port <port>` | Set the port for the Web Control Panel. |
| `/cycm http ui <on\|off>` | Enable or disable the Web Control Panel. |

---

## 🌐 Web Control Panel

CYCM features a modern **Web-based Control Panel** that allows you to configure the mod from your browser. This is especially useful for dual-monitor setups where you can adjust settings without alt-tabbing or typing long commands in-game.

### 🚀 How to use
1.  **Enable the UI**: Run `/cycm http ui on` in Minecraft.
2.  **Access it**: Open your browser and go to `http://localhost:21457` (default port).
3.  **Configure**: Adjust YouTube keys, Telegram tokens, command limits, and blocklists in real-time.

### 🎨 Features
*   **Real-time sync**: Changes made in the Web UI are instantly applied to the mod.
*   **Multi-language support**: Available in English, Ukrainian, and Russian.
*   **Comprehensive control**: Manage all aspects of the mod from one dashboard.
*   **Visual Blocklist**: Easily add or remove blocked commands with a simple interface.

### 🛠️ Special Game Commands

| Shortcut | Full Command | Description |
| :--- | :--- | :--- |
| `/tnt [count] [radius]` | Special local helper | Spawns one TNT by default. With `count`, it spawns that many TNT at the player. With both `count` and `radius`, it spawns a TNT ring around the player. The requested values are capped by `/cycm tnt count` and `/cycm tnt radius`. |
| `/ka` or `/killaura` | — | Kills all mobs within 20 blocks. |
| `/ke` or `/killentities` | — | Removes all non-player entities from world. |
| `/blocklist` | `/cycm blocklist` | Shows blocked commands. |

---

## 📥 Installation & Setup

### 1️⃣ Install the Mod
1. Download the latest `.jar` file from [Modrinth](https://modrinth.com/mod/cycm).
2. Place it in your Fabric `mods` folder.
3. Launch Minecraft with Fabric Loader.

### 2️⃣ YouTube Setup (API Mode)

**Option A: OAuth2 (Recommended)**
1. Create a project in [Google Cloud Console](https://console.cloud.google.com/).
2. Enable the **YouTube Data API v3**.
3. Create OAuth 2.0 credentials (Desktop app type).
4. In-game, run:
   ```
   /cycm youtube oa2client <YOUR_CLIENT_ID>
   /cycm youtube oa2secret <YOUR_CLIENT_SECRET>
   /cycm youtube connect
   ```
5. Click the link, authorize in browser, and return to Minecraft.
6. Enable YouTube source: `/cycm source on youtube`

**Option B: Simple API Key (Read-only)**
1. Get an API key from [Google Cloud Console](https://console.cloud.google.com/).
2. In-game, run:
   ```
   /cycm youtube key <YOUR_API_KEY>
   /cycm youtube id <YOUR_VIDEO_ID>
   /cycm ytmode api
   /cycm source on youtube
   ```

### 3️⃣ YouTube Setup (HTTP/Browser Mode)
1. Run `/cycm ytmode http` in-game.
2. Install the [UserScript](https://gist.github.com/CraftLLC/61c4f1df67de5b6a88c72c533c5f4964) in your browser (requires Tampermonkey/Violentmonkey).
3. Open your YouTube live stream in the browser.
4. The script will relay chat messages to your local Minecraft instance.

### 4️⃣ Telegram Setup
1. Create a bot via [@BotFather](https://t.me/BotFather) and get the token.
2. In-game, run:
   ```
   /cycm tg token <YOUR_BOT_TOKEN>
   /cycm source on telegram
   ```
3. Start a chat with your bot on Telegram and send `/start`.

---

## 📂 Configuration Files

All files are stored in `config/cycm/`:

| File | Purpose |
| :--- | :--- |
| `cycm.json` | Main config: API keys, ports, mode settings, feature toggles, blocked commands, max repeats, max delay, max TNT count, and max TNT radius. |
| `live.json` | Persistent state: OAuth tokens, processed message IDs, stream info. |
| `commands_log.txt` | Log of all executed commands. |
| `chat_log.txt` | Log of all chat messages received. |

**Note**: OAuth2 tokens are automatically refreshed. The `live.json` file stores access tokens, refresh tokens, and expiry timestamps.

---

## 🔒 Security & Safety

*   **Command Blocking**: Use `/cycm block <command>` to prevent specific commands from being executed.
*   **Mod Commands Protected**: Core CYCM commands (`/cycm`, `/ce`) cannot be blocked or executed remotely.
*   **Fair Limits**: Default limits prevent spam: 20 max repeats, 5-second max delay, 20 max TNT count, and 8 max TNT radius.
*   **Pipeline Waits**: `!sleep <seconds>` pauses the current chained script before continuing to the next segment.
*   **OAuth2**: Tokens are stored locally and refreshed automatically. Never share your `live.json` file.
*   **Minecraft Formatting**: Color codes (`§`) are automatically stripped from outgoing YouTube messages.

---

## 🎨 Stream Description Templates

Copy these templates for your YouTube stream description. Click to expand your preferred language:

<details>
<summary>🇺🇦 Українська (Ukrainian)</summary>

```
🎮 КЕРУЙ ГРОЮ ЧЕРЕЗ ЧАТ!

Пишіть повідомлення або команди в чат YouTube — і вони виконаються прямо в моєму Minecraft!

📝 Як це працює:
• Прості повідомлення: Просто пишіть у чат.
• Команди: Пишіть команди (з / або без), наприклад: /tnt, /killaura
• Повторення: /summon sheep +5 (заспавнить 5 овечок)
• Затримка: /tnt +5 2 (динаміт кожні 2 секунди)
• Ліміти: до 20 повторень, до 5 секунд затримки
• Декілька команд: /summon sheep | summon cow

⚔️ Спеціальні команди:
• /tnt — Заспавнити динаміт
• /killaura (або /ka) — Вбити всіх мобів у радіусі 20 блоків
• /killentities (або /ke) — Вбити всіх мобів у світі

Я можу блокувати небажані команди, які занадто заважають грі. Веселіться! 🎉
```

</details>

<details>
<summary>🇬🇧 English</summary>

```
🎮 CONTROL THE GAME VIA CHAT!

Write messages or commands in the YouTube chat — and they'll execute live in my Minecraft!

📝 How it works:
• Simple messages: Just type in chat.
• Commands: Enter commands (with or without /), e.g.: /tnt, /killaura
• Repetitions: /summon sheep +5 (spawns 5 sheep)
• Delays: /tnt +5 2 (5 TNT total, one every 2 seconds)
• Limits: up to 20 repetitions, up to 5 seconds delay
• Multiple commands: /summon sheep | summon cow

⚔️ Special commands:
• /tnt [count] [radius] — Spawn primed TNT or a TNT ring
• /killaura (or /ka) — Kill all mobs within 20 blocks
• /killentities (or /ke) — Remove all mobs from the world

I can block unwanted commands that disrupt gameplay too much. Have fun! 🎉
```

</details>

<details>
<summary>🇷🇺 Русский (Russian)</summary>

```
🎮 УПРАВЛЯЙ ИГРОЙ ЧЕРЕЗ ЧАТ!

Пишите сообщения или команды в чат YouTube — и они выполнятся прямо в моём Minecraft!

📝 Как это работает:
• Простые сообщения: Просто пишите в чат.
• Команды: Пишите команды (с / или без), например: /tnt, /killaura
• Повторения: /summon sheep +5 (заспавнит 5 овец)
• Задержка: /tnt +5 2 (динамит каждые 2 секунды)
• Лимиты: до 20 повторений, до 5 секунд задержки
• Несколько команд: /summon sheep | summon cow

⚔️ Специальные команды:
• /tnt — Заспавнить динамит
• /killaura (или /ka) — Убить всех мобов в радиусе 20 блоков
• /killentities (или /ke) — Убить всех мобов в мире

Я могу блокировать нежелательные команды, которые слишком мешают игре. Веселитесь! 🎉
```

</details>

---

## 🐛 Troubleshooting

**Messages not appearing from YouTube?**
- Verify authorization: `/cycm youtube connect`
- Check if source is enabled: `/cycm source list`
- Ensure you're using the correct Video ID
- Check logs in `run/logs/latest.log`

**Can't send messages to YouTube?**
- Run `/cycm youtube send on`
- Verify OAuth2 authorization is complete
- Check that you have the necessary YouTube API scopes

**HTTP mode not working?**
- Ensure the HTTP server port isn't blocked (default: 21456)
- Verify the UserScript is installed and active
- Check browser console for errors

**Telegram bot not responding?**
- Verify the token is correct
- Ensure you've sent `/start` to the bot
- Check if Telegram source is enabled

---

## 👥 Credits & Support

*   **Developer**: [CraftLLC](https://m.youtube.com/@CraftLLCOF)
*   **Telegram Support**: [@DinyaMC](https://t.me/DinyaMC)
*   **Issues & Suggestions**: [GitHub Issues](https://github.com/CraftLLC/CYCM/issues)

---

*Developed by creators, for creators. Elevate your stream with CYCM.* 🚀

---

## 📄 License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.



