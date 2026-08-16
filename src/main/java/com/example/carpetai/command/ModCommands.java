package com.example.carpetai.command;

import com.example.carpetai.CarpetAIFakePlayer;
import com.example.carpetai.api.LLMClient;
import com.example.carpetai.action.ActionExecutor;
import com.example.carpetai.config.ModConfig;
import com.example.carpetai.entity.TaskQueue;
import com.example.carpetai.entity.PlayerContext;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;

import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import carpet.patches.EntityPlayerMPFake;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT;

public class ModCommands {

    public static void register() {
        EVENT.register((dispatcher, registryAccess, environment) -> {
            var aiRoot = CommandManager.literal("ai");

            // /ai <假人> <内容>  — 主交互命令
            aiRoot.then(CommandManager.argument("playerName", StringArgumentType.word())
                .then(CommandManager.argument("prompt", StringArgumentType.greedyString())
                    .executes(context -> {
                        String playerName = StringArgumentType.getString(context, "playerName");
                        String prompt = StringArgumentType.getString(context, "prompt");
                        ServerPlayerEntity source = context.getSource().getPlayer();

                        if (source == null) {
                            context.getSource().sendError(Text.literal("This command can only be used by players."));
                            return 0;
                        }

                        // 检查假人
                        ServerPlayerEntity fakePlayer = source.getServer().getPlayerManager().getPlayer(playerName);
                        if (fakePlayer == null || !(fakePlayer instanceof EntityPlayerMPFake)) {
                            source.sendMessage(Text.literal("§c[AI] Player '" + playerName + "' not found or not a fake player."));
                            return 0;
                        }

                        // 任务队列
                        boolean submitted = TaskQueue.submit(playerName, () -> executeAI(source, playerName, fakePlayer, prompt));
                        if (!submitted) {
                            source.sendMessage(Text.literal("§c[AI] " + playerName + " is busy or task queue is full."));
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
                    var all = TaskQueue.all();
                    if (all.isEmpty()) {
                        source.sendMessage(Text.literal("§7[AI] No active fake players."));
                    } else {
                        source.sendMessage(Text.literal("§6[AI] Active fake players:"));
                        for (var entry : all.entrySet()) {
                            PlayerContext ctx = entry.getValue();
                            String status = ctx.isBusy ? "§e(busy)" : "§a(idle)";
                            source.sendMessage(Text.literal("  §7- " + entry.getKey() + " " + status
                                + " §7token:" + ctx.tokenUsage + "/" + ctx.tokenBudget
                                + " §7history:" + ctx.dialogueHistory.size() / 2 + " turns"));
                        }
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
                            ctx.dialogueHistory.clear();
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

    private static void executeAI(ServerPlayerEntity source, String playerName,
                                   ServerPlayerEntity fakePlayer, String prompt) {
        try {
            PlayerContext ctx = TaskQueue.get(playerName);
            ModConfig config = ModConfig.load();

            // 检查 token 预算
            if (ctx != null && ctx.isOverBudget()) {
                source.sendMessage(Text.literal("§c[AI] " + playerName + " has exceeded token budget."));
                return;
            }

            String systemPrompt = buildSystemPrompt(playerName, ctx);

            // 调用 LLM（带历史记录）
            String response = LLMClient.complete(
                systemPrompt, prompt,
                ctx != null ? ctx.dialogueHistory : null
            );

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

            // 执行动作
            ActionExecutor.execute(fakePlayer, response, ctx);

            source.sendMessage(Text.literal("§a[AI] " + playerName + " responded."));

        } catch (Exception e) {
            CarpetAIFakePlayer.LOGGER.error("AI execution error for " + playerName, e);
            source.sendMessage(Text.literal("§c[AI] Error: " + e.getMessage()));
        }
    }

    private static String buildSystemPrompt(String playerName, PlayerContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a Minecraft bot named ").append(playerName).append(". ");
        sb.append("You are a fake player controlled by Carpet mod. ");
        sb.append("Respond with a SINGLE JSON object representing an action to perform. ");
        sb.append("Available actions: ");
        sb.append("MOVE(x,y,z), LOOK(yaw,pitch), CHAT(message), JUMP, CROUCH, ");
        sb.append("BREAK_BLOCK(x,y,z), PLACE_BLOCK(x,y,z), ATTACK(entity), ");
        sb.append("USE_ITEM(nearbyBlock), DROP, SWAP_HOTBAR(slot), WAIT(seconds). ");
        sb.append("Example: {\"action\":\"MOVE\",\"x\":100,\"y\":64,\"z\":200} ");
        sb.append("Example: {\"action\":\"CHAT\",\"message\":\"Hello!\"} ");

        if (ctx != null && ctx.lastAction != null) {
            sb.append("Your last action was: ").append(ctx.lastAction).append(". ");
        }

        return sb.toString();
    }
}