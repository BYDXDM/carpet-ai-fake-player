# Carpet AI Fake Player

一个基于 **Carpet 模组** 的 Fabric 附属模组，让游戏内通过 `/player` 召唤的假人（Fake Player）具备 **LLM 驱动的自主行动能力**。

假人可以理解玩家用聊天命令下达的指令，自主执行移动、放置/破坏方块、攻击实体、跟随玩家等动作，并且接入多家大模型 API 作为"大脑"。

> 支持 Minecraft **1.21.11** · Fabric 加载器 · JDK 21 · Carpet ≥ 1.4.194

---

## ✨ 功能特性

- 🧠 **LLM 驱动**：假人通过大模型理解玩家指令，返回 JSON 动作并执行
- 🔌 **多提供商**：OpenAI / Anthropic / Google Gemini / Groq / 本地 Ollama / 任意 OpenAI 兼容端点
- 💬 **对话交互**：`/ai <假人> <内容>` 与假人对话，也支持 `/tell <假人>` 私聊
- 🤖 **自主行动**：MOVE / LOOK / CHAT / JUMP / CROUCH / BREAK_BLOCK / PLACE_BLOCK / ATTACK / FOLLOW / DROP 等
- 📦 **多假人并发**：内置任务队列，支持多假人同时处理任务，可配置并发上限
- 🧾 **记忆与上下文**：自动维护对话历史，token 配额管理，防止 API 配额过度消耗
- ⚙️ **运行时配置**：通过命令即可切换提供商 / 模型 / API Key，无需改文件

---

## 📦 安装

前置依赖（需先安装）：

| 依赖 | 版本 | 说明 |
|---|---|---|
| Minecraft | 1.21.11 | 固定 |
| Fabric Loader | ≥ 0.19.3 | 模组加载器 |
| Fabric API | 0.141.6+1.21.11 | 必装 |
| Carpet (fabric-carpet) | ≥ 1.4.194 | 本模组核心依赖 |

> 本模组已**内置打包** OkHttp / Gson / Cloth Config，无需额外安装这些库。

**步骤**：
1. 下载 `carpet-ai-fake-player-1.0.0.jar`
2. 放入 `mods/` 文件夹
3. 启动服务器 → 用 Carpet 的 `/player <名字> spawn` 召唤假人 → 配置好 API → 开始对话

---

## 🚀 快速上手

```minecraft
# 1. 召唤一个假人（Carpet 命令）
/player Bob spawn at 100 64 200

# 2. 配置大模型（任选其一，见下方"配置大模型"）
/ai set provider openai
/ai set key sk-xxxx
/ai set model gpt-4o-mini

# 3. 与假人对话，让它执行动作
/ai Bob 走到 120, 64, 210
/ai Bob 帮我挖掉脚下的方块
/ai Bob 攻击附近那只僵尸
/ai Bob 聊天说你好
```

假人会回复一个动作并执行。每个动作有 2 秒冷却时间（可配置）。

---

## ⚙️ 配置大模型

有两种方式配置，任选其一。

### 方式一：命令（推荐，实时生效）

```minecraft
/ai set provider openai     # 切换提供商
/ai set model gpt-4o-mini   # 设置模型名
/ai set key sk-xxxx         # 设置 API Key
/ai config                  # 查看当前配置
```

### 方式二：配置文件

编辑 `config/carpet-ai-fake-player.json`（首次运行后自动生成），修改后重启服务器。

```json
{
  "llmProvider": "openai",
  "apiUrl": "",
  "apiKey": "",
  "model": "gpt-4o-mini",
  "maxTokens": 2048,
  "temperature": 0.7,
  "contextLength": 10,
  "maxTokenBudget": 0,
  "maxConcurrentTasks": 3,
  "taskTimeoutSeconds": 300,
  "actionCooldownMs": 2000,
  "maxMoveDistance": 100.0
}
```

### 支持的提供商

| 提供商 | `llmProvider` 值 | 默认端点 | 默认模型建议 |
|---|---|---|---|
| OpenAI | `openai` | `api.openai.com/v1/chat/completions` | `gpt-4o-mini` |
| Anthropic | `anthropic` | `api.anthropic.com/v1/messages` | `claude-3-5-sonnet` |
| Google Gemini | `google` | `generativelanguage.googleapis.com` | `gemini-1.5-flash` |
| Groq | `groq` | `api.groq.com/openai/v1/chat/completions` | `llama-3.1-8b-instant` |
| 本地 Ollama | `ollama` | `localhost:11434/v1/chat/completions` | `llama3` |
| 自定义 | `custom` | 任意 OpenAI 兼容端点 | — |

> **自定义端点**：把 `llmProvider` 设为任意未知值，并在 `apiUrl` 填入你的 OpenAI 兼容 `/chat/completions` 地址即可。

> **API Key 提示**：Gemini 的 key 需要带"API 密钥"格式，且模型通过 `:generateContent` 接口调用。Ollama 本地通常不需要 key。

---

## 🛠️ 命令大全

| 命令 | 说明 |
|---|---|
| `/ai <假人> <内容>` | 与假人对话，让它执行动作 |
| `/ai list` | 列出所有假人及状态（busy/idle、token 用量、历史轮数） |
| `/ai clear <假人>` | 清除该假人的对话历史与 token 用量 |
| `/ai config` | 查看当前 LLM 配置 |
| `/ai set provider <name>` | 切换提供商 |
| `/ai set model <name>` | 设置模型名 |
| `/ai set key <key>` | 设置 API Key |
| `/tell <假人> <内容>` | 私聊假人（等价于对话交互） |

---

## 🎭 动作系统

假人由 LLM 驱动：每次对话，大模型被要求返回**一个 JSON 对象**来描述要执行的动作，模组解析后执行。

| 动作 | 参数 | 说明 |
|---|---|---|
| `MOVE` | `x, y, z` | 移动到指定坐标（受 `maxMoveDistance` 限制） |
| `LOOK` | `yaw, pitch` | 转向指定角度 |
| `CHAT` | `message` | 以假人名义发言 |
| `JUMP` | — | 跳跃 |
| `CROUCH` | — | 切换潜行 |
| `BREAK_BLOCK` | `x, y, z` | 破坏指定方块（5 格内） |
| `PLACE_BLOCK` | `x, y, z` | 在空位放置手中方块 |
| `ATTACK` | `target`(可选) | 攻击最近的目标实体，或按名字指定 |
| `FOLLOW` | `target, distance` | 跟随指定玩家，保持距离 |
| `USE_ITEM` | — | 使用物品（预留） |
| `DROP` | — | 丢弃手持物品 |
| `SWAP_HOTBAR` | `slot` | 切换快捷栏 0-8 格 |
| `WAIT` | `seconds` | 等待一段时间 |

> **安全限制**：所有移动类动作受 `maxMoveDistance` 距离上限约束；方块操作限制在 5 格内；动作间有 `actionCooldownMs` 冷却。

---

## 🧩 架构

```
com.example.carpetai/
  CarpetAIFakePlayer.java           入口(Mixins/EntryPoint)
  api/
    LLMProvider.java                LLM 提供商标识抽象
    LLMClient.java                  统一调度 + 提供商注册表
    OpenAIProvider.java             OpenAI/Ollama/Groq/自定义
    AnthropicProvider.java          Claude (/v1/messages)
    GoogleProvider.java             Gemini (generateContent)
  command/
    ModCommands.java                /ai 命令体系
  config/
    ModConfig.java                  JSON 配置读写
  entity/
    PlayerContext.java              假人上下文(历史/token/busy)
    TaskQueue.java                  多假人并发任务队列
  action/
    ActionExecutor.java             解析并执行动作
```

### 主要模块职责

- **LLM 抽象**：`LLMProvider` 接口定义 `apiUrl / buildRequestBody / extractContent / headers`，新增提供商只需实现该接口并注册到 `LLMClient`。
- **任务队列**：`TaskQueue` 用线程池管理多假人并发，每个假人同一时间只能跑一个任务（busy 锁），并受 `maxConcurrentTasks` 全局上限约束。
- **上下文管理**：`PlayerContext` 记录对话历史、token 用量与配额，自动 `trimHistory` 裁剪到 `contextLength` 轮。
- **动作执行**：`ActionExecutor` 解析 LLM 返回的 JSON，做校验（距离/冷却）后调用服务端 API 执行。

---

## 📜 常见问题

**Q: 假人提示 "not found or not a fake player"？**
→ 确认已用 Carpet `/player` 命令召唤，名字拼写一致。

**Q: 假人没反应 / "Unknown LLM provider"？**
→ 检查 `/ai set provider` 是否用对了值，或 API Key / 网络是否通畅。

**Q: 移动距离太大被拒绝？**
→ `MOVE` 受 `maxMoveDistance`（默认 100 格）限制，可调大配置。

**Q: 需要数据库类依赖吗？**
→ 不需要，OkHttp / Gson / Cloth Config 已内置打包进 jar。

---

## 🗺️ 路线图（待实现）

- [ ] Mod Menu 图形化配置界面（Cloth Config 集成，当前为纯 JSON / 命令配置）
- [ ] 真实寻路（当前 MOVE 为瞬移定位；Carpet 假人无公开寻路 API，需接 Baritone——但 Baritone 目前最高支持 1.20.1，1.21.11 暂不可用）
- [ ] 村民交易等更复杂的实体交互
- [ ] 物品栏/背包管理进阶

---

## 📄 许可证

MIT

---

## 🙏 致谢

- [Carpet](https://github.com/gnembon/fabric-carpet)
- [Fabric](https://fabricmc.net/)
- 参考项目：Quackingly、GamesAI Extra、CarpetBaritoneIntegration
