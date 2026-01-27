# 🎮 CraftLLC YouTube Command Mod [CYCM]

**CYCM** is a high-performance Minecraft mod designed for Streamers. It bridges **YouTube Live Chat** and **Telegram** directly into your game, giving your audience real-time control over gameplay events through a powerful command system.

<center>
  <img src="https://img.shields.io/modrinth/dt/cycm?style=for-the-badge" alt="Downloads">
  <img src="https://img.shields.io/modrinth/v/cycm?style=for-the-badge" alt="Latest Version">
  <img src="https://img.shields.io/modrinth/game-versions/cycm?style=for-the-badge&label=minecraft" alt="Minecraft Version">
</center>

---

## ✨ Key Features

*   **🛰️ Dual-Source**: Process signals from YouTube and Telegram simultaneously.
*   **🚀 Ultra-Stable HTTP**: Browser-bridge mode to bypass API quotas and limits.
*   **⛓️ Command Chaining**: Execute multiple actions in one line using `|`.
*   **🛡️ Fair Budgeting**: Intelligent repetition distribution to prevent server overloads.
*   **💬 HUD Optimization**: Automatic message grouping (`x5`) and status actionbar.
*   **🌍 Multilingual**: Full support for English, Ukrainian, and Russian.

---

## 🛰️ Advanced Syntax
CYCM isn't just a relay; it's a command engine.

### ⚡ Chaining & Repetitions
*   **Syntax**: `/command +[Count] [DelaySeconds]`
*   **Chain**: `/tnt | /summon zombie +5 2 | /say Watch out!`
*   **Escaping**: Use `\` to treat special symbols literally (e.g., `/say \+10` sends "+10" to chat).

### 🛡️ Safety & "Fair Budget"
The mod limits the *total* number of repetitions in a single message to `maxRepeats`. 
*   If the limit is 10, and someone sends `/cmd1 +6 | /cmd2 +6`, the mod executes `cmd1` 6 times and `cmd2` 4 times (fair distribution).
*   **Duration Cap**: Commands cannot exceed a calculated safety duration.

---

## ⌨️ Client Commands

| Command | Description |
| :--- | :--- |
| `/cycm <on\|off>` | Master toggle for the mod. |
| `/cycm restart` | Reloads configurations and restarts all services. |
| `/cycm ytmode <api\|http>` | Switch between Direct API or Browser Bridge. |
| `/cycm source <on\|off> <yt\|tg>` | Toggle specific sources (YouTube/Telegram). |
| `/cycm source list` | View status of all active connections. |
| `/cycm block <cmd>` | Add a command to the persistent blocklist. |
| `/cycm unblock <cmd\|all>` | Remove command(s) from the blocklist. |
| `/cycm execute <cmd>` | Run CYCM syntax from in-game (Alias: `/ce`). |
| `/cycm grouping <on\|off>` | Toggle automatic message grouping in chat. |
| `/cycm actionbar <on\|off>` | Toggle the real-time status overlay. |
| `/cycm num <N>` | Set global maximum repetitions per message. |
| `/cycm delay <S>` | Set global maximum delay between repeats. |

### 🛠️ Utilities & Shortcuts
*   `/tnt` — Shortcut for `/summon minecraft:tnt`.
*   `/ka` / `/killaura` — Cleans entities in a 20-block radius.
*   `/ke` / `/killentities` — Force-clears all non-player entities.
*   `/blocklist` — Displays all currently forbidden commands.

---

## 📥 Installation

1.  **Mod**: Place the jar in your Fabric `mods` folder.
2.  **YouTube (HTTP Mode)**: Run `/cycm ytmode http`, install the [UserScript](https://gist.github.com/CraftLLC/61c4f1df67de5b6a88c72c533c5f4964), and keep your stream open in a browser.
3.  **Telegram**: Get a token from [@BotFather](https://t.me/BotFather), set it via `/cycm tg token <token>`, and enable via `/cycm source on tg`.

---

## 📂 Configuration
Files are stored in `config/cycm/`:
*   `cycm.json`: API keys, ports, and core settings.
*   `blocked_commands.txt`: Your custom blacklist.
*   `repeating_settings.txt`: Limits for repeats and delays.

---

## 👥 Meet the Team
*   **Developer**: [CraftLLC](https://m.youtube.com/@CraftLLCOF)
*   **Telegram Support**: [@DinyaMC](https://t.me/DinyaMC)

---
*Developed by creators, for creators. Elevate your content with CYCM.*
