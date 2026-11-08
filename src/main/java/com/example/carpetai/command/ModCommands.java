package com.example.carpetai.command;

import com.example.carpetai.CarpetAIFakePlayer;
import com.example.carpetai.api.LLMClient;
import com.example.carpetai.action.ActionExecutor;
import com.example.carpetai.config.ModConfig;
import com.example.carpetai.entity.TaskQueue;
import com.example.carpetai.entity.PlayerContext;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import carpet.patches.EntityPlayerMPFake;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT;

public class ModCommands {

    public static void register() {
        EVENT.register((dispatcher, registryAccess, environment) -> {
            // 安全基线：/ai 全命令树（含 /ai set key 等配置命令）要求 OP 权限等级 2，
            // 无权限玩家不可见也不可执行；服务器控制台拥有最高等级不受影响。
            var aiRoot = CommandManager.literal("ai")
                .requires(src -> src.hasPermissionLevel(2));

            // /ai <假人> <内容>  — 主交互命令
            aiRoot.then(CommandManager.argument("playerName", StringArgumentType.word())
                .then(CommandManager.argument("prompt", StringArgumentType.greedyString())
                    .executes(context -> {
                        var source = context.getSource();
                        String playerName = StringArgumentType.getString(context, "playerName");
                        String prompt = StringArgumentType.getString(context, "prompt");
                        MinecraftServer server = source.getServer();

                        // 检查假人
                        var fakePlayer = server.getPlayerManager().getPlayer(playerName);
                        if (!(fakePlayer instanceof EntityPlayerMPFake fake)) {
                            source.sendError(Text.literal("Player '" + playerName + "' not found or not a fake player."));
                            return 0;
                        }

                        // 在主线程上捕获假人当前状态，供 LLM 决策参考
                        String botState = buildBotState(fake);

                        // 在主线程上对对话历史做防御性快照：历史写入方（回调、/ai clear）
                        // 全部在主线程，快照也在主线程完成，worker 线程不再触碰可变历史，
                        // 消除 worker 复制与主线程写入并发修改的竞态窗口
                        PlayerContext snapshotCtx = TaskQueue.get(playerName);
                        List<Map<String, String>> historySnapshot =
                            snapshotCtx != null ? snapshotCtx.snapshotHistory() : null;

                        boolean submitted = TaskQueue.submit(playerName,
                            () -> executeAI(server, source, playerName, fake, prompt, botState, historySnapshot));
                        if (!submitted) {
                            source.sendError(Text.literal("[AI] " + playerName + " is busy or task queue is full."));
                            return 0;
                        }

                        source.sendMessage(Text.literal("§e[AI] " + playerName + " is thinking..."));
                        return Command.SINGLE_SUCCESS;
                    })
                )
            );

            // /ai list — 列出所有假人及其状态
            aiRoot.then(CommandManager.literal("list")
                .executes(context -> {
                    var source = context.getSource();
                    MinecraftServer server = source.getServer();
                    // 只展示当前仍在线的假人：上下文在假人被删后会残留（保留对话
                    // 记忆以便重建同名假人续聊），幽灵条目不应再列为"活跃"
                    List<Map.Entry<String, PlayerContext>> online = new ArrayList<>();
                    for (var entry : TaskQueue.all().entrySet()) {
                        if (server.getPlayerManager().getPlayer(entry.getKey())
                                instanceof EntityPlayerMPFake) {
                            online.add(entry);
                        }
                    }
                    if (online.isEmpty()) {
                        source.sendMessage(Text.literal("§7[AI] No active fake players."));
                        return Command.SINGLE_SUCCESS;
                    }
                    source.sendMessage(Text.literal("§6[AI] Active fake players:"));
                    for (var entry : online) {
                        PlayerContext ctx = entry.getValue();
                        String status = ctx.isBusy ? "§e(busy)" : "§a(idle)";
                        source.sendMessage(Text.literal("  §7- " + entry.getKey() + " " + status
                            + " §7token:" + ctx.tokenUsage + "/" + ctx.tokenBudget
                            + " §7history:" + ctx.dialogueHistory.size() / 2 + " turns"));
                    }
                    return Command.SINGLE_SUCCESS;
                })
            );

            // /ai clear <假人> — 清除某假人的对话历史
            aiRoot.then(CommandManager.literal("clear")
                .then(CommandManager.argument("playerName", StringArgumentType.word())
                    .executes(context -> {
                        String name = StringArgumentType.getString(context, "playerName");
                        PlayerContext ctx = TaskQueue.get(name);
                        if (ctx != null) {
                            ctx.clearDialogue();
                            ctx.tokenUsage = 0;
                            context.getSource().sendMessage(Text.literal("§a[AI] Cleared context for " + name));
                        } else {
                            context.getSource().sendMessage(Text.literal("§c[AI] No context for " + name));
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                )
            );

            // /ai config — 查看当前配置
            aiRoot.then(CommandManager.literal("config")
                .executes(context -> {
                    ModConfig cfg = ModConfig.load();
                    var source = context.getSource();
                    source.sendMessage(Text.literal("§6[AI] Config:"));
                    source.sendMessage(Text.literal("§7  Provider: " + cfg.llmProvider));
                    source.sendMessage(Text.literal("§7  Model: " + cfg.model));
                    source.sendMessage(Text.literal("§7  API URL: " + (cfg.apiUrl.isEmpty() ? "(default)" : cfg.apiUrl)));
                    source.sendMessage(Text.literal("§7  MaxTokens: " + cfg.maxTokens + " | Temp: " + cfg.temperature));
                    source.sendMessage(Text.literal("§7  ContextLength: " + cfg.contextLength + " | Budget: " + cfg.maxTokenBudget));
                    source.sendMessage(Text.literal("§7  MaxConcurrent: " + cfg.maxConcurrentTasks + " | Cooldown: " + cfg.actionCooldownMs + "ms"));
                    return Command.SINGLE_SUCCESS;
                })
            );

            // /ai set provider|model|key <value> — 运行时修改配置
            aiRoot.then(CommandManager.literal("set")
                .then(CommandManager.literal("provider")
                    .then(CommandManager.argument("name", StringArgumentType.word())
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            ModConfig cfg = ModConfig.load();
                            cfg.llmProvider = name;
                            ModConfig.save();
                            context.getSource().sendMessage(Text.literal("§a[AI] Provider set to " + name));
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                .then(CommandManager.literal("model")
                    .then(CommandManager.argument("name", StringArgumentType.greedyString())
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            ModConfig cfg = ModConfig.load();
                            cfg.model = name;
                            ModConfig.save();
                            context.getSource().sendMessage(Text.literal("§a[AI] Model set to " + name));
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                .then(CommandManager.literal("key")
                    .then(CommandManager.argument("key", StringArgumentType.greedyString())
                        .executes(context -> {
                            String key = StringArgumentType.getString(context, "key");
                            ModConfig cfg = ModConfig.load();
                            cfg.apiKey = key;
                            ModConfig.save();
                            context.getSource().sendMessage(Text.literal("§a[AI] API key updated."));
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
            );

            dispatcher.register(aiRoot);
        });
    }

    private static void executeAI(MinecraftServer server, ServerCommandSource source, String playerName,
                                   EntityPlayerMPFake fakePlayer, String prompt, String botState,
                                   List<Map<String, String>> historySnapshot) {
        // busy 标志恰好释放一次：回调已提交时由回调的 finally 释放（晚于回调真正执行完），
        // 未提交（出错/中断）时由本方法 finally 释放。isBusy 的释放时机不再早于回调，
        // 消除"busy 已释放、回调尚未执行"窗口内新任务与回调写历史的竞态。
        boolean callbackSubmitted = false;
        try {
            PlayerContext ctx = TaskQueue.get(playerName);
            ModConfig config = ModConfig.load();

            // 检查 token 预算（worker 线程上的用户反馈必须回主线程）
            if (ctx != null && ctx.isOverBudget()) {
                server.execute(() -> source.sendError(
                    Text.literal("[AI] " + playerName + " has exceeded token budget.")));
                return;
            }

            String systemPrompt = buildSystemPrompt(playerName, ctx, botState);

            // 调用 LLM（带历史快照）。此调用阻塞数十秒，必须留在后台线程；
            // 快照已在命令执行时（主线程）完成，worker 线程不再读取可变历史，避免并发修改。
            String response = LLMClient.complete(systemPrompt, prompt, historySnapshot);

            // WAIT：在后台线程等待，占住该假人的任务槽，防止等待期间插入新指令
            if ("WAIT".equals(ActionExecutor.parseActionType(response))) {
                Thread.sleep(Math.min(ActionExecutor.parseWaitSeconds(response), 30) * 1000L);
            }

            // 动作执行涉及世界/实体操作，必须回到服务器主线程
            server.execute(() -> {
                try {
                    // 回调跨越了最长 90s 的阻塞调用，期间假人可能被 /player remove 移除或死亡：
                    // 入口先做存活校验，失效则记日志并安全返回（finally 仍会释放 busy）
                    if (fakePlayer.isRemoved() || !fakePlayer.isAlive()) {
                        CarpetAIFakePlayer.LOGGER.warn(
                            "[Carpet AI] Bot '{}' is removed or dead after the LLM call, skipping actions.",
                            playerName);
                        return;
                    }

                    // 更新上下文
                    if (ctx != null) {
                        ctx.addDialogue("user", prompt);
                        ctx.addDialogue("assistant", response);
                        ctx.trimHistory(config.contextLength);
                        ctx.lastMessageTime = System.currentTimeMillis();
                        // 粗略估算 token
                        ctx.tokenUsage += (prompt.length() + response.length()) / 3;
                        if (ctx.tokenBudget == 0) ctx.tokenBudget = config.maxTokenBudget;
                    }

                    ActionExecutor.execute(fakePlayer, response, ctx);

                    source.sendMessage(Text.literal("§a[AI] " + playerName + " responded."));
                } catch (Exception e) {
                    // 主线程回调兜底：异常不再沿 tick 任务路径向上传播；
                    // 用户可见文案用固定模板，原始异常（可能含带 key 的 URL）只进服务器日志
                    CarpetAIFakePlayer.LOGGER.error("AI callback error for " + playerName, e);
                    source.sendError(Text.literal(
                        "[AI] " + playerName + " failed to process the action, see the server log for details."));
                } finally {
                    // busy 标志在回调真正执行完毕后才释放
                    TaskQueue.release(playerName);
                }
            });
            callbackSubmitted = true;

        } catch (InterruptedException e) {
            // 服务器停止时的正常中断，静默返回（finally 会释放 busy）
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // 原始异常可能包含带 API key 的 URL 等敏感信息，只进服务器日志；
            // 对命令源的反馈一律 server.execute 回主线程，并使用固定文案模板（不回显 e.getMessage()）
            CarpetAIFakePlayer.LOGGER.error("AI execution error for " + playerName, e);
            server.execute(() -> source.sendError(Text.literal(
                "[AI] " + playerName + " task failed, see the server log for details.")));
        } finally {
            if (!callbackSubmitted) {
                TaskQueue.release(playerName);
            }
        }
    }

    /** 在服务器主线程上读取假人的当前状态，注入到系统提示词中。 */
    private static String buildBotState(EntityPlayerMPFake bot) {
        var stack = bot.getMainHandStack();
        return String.format(
            "Your current state: position=(%.1f, %.1f, %.1f), yaw=%.0f, pitch=%.0f, health=%.1f, main_hand=%s. ",
            bot.getX(), bot.getY(), bot.getZ(), bot.getYaw(), bot.getPitch(), bot.getHealth(),
            stack.isEmpty() ? "empty" : stack.getName().getString());
    }

    private static String buildSystemPrompt(String playerName, PlayerContext ctx, String botState) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a Minecraft bot named ").append(playerName).append(". ");
        sb.append("You are a fake player controlled by Carpet mod. ");
        sb.append("Respond with a SINGLE JSON object representing an action to perform. ");
        sb.append("Available actions: ");
        sb.append("MOVE(x,y,z), LOOK(yaw,pitch), CHAT(message), JUMP, CROUCH, ");
        sb.append("BREAK_BLOCK(x,y,z), PLACE_BLOCK(x,y,z), ATTACK(target), ");
        sb.append("FOLLOW(target,distance), USE_ITEM(nearbyBlock), DROP, SWAP_HOTBAR(slot), WAIT(seconds). ");
        sb.append("Example: {\"action\":\"MOVE\",\"x\":100,\"y\":64,\"z\":200} ");
        sb.append("Example: {\"action\":\"CHAT\",\"message\":\"Hello!\"} ");

        if (botState != null) {
            sb.append(botState);
        }

        if (ctx != null && ctx.lastAction != null) {
            sb.append("Your last action was: ").append(ctx.lastAction).append(". ");
        }

        return sb.toString();
    }
}