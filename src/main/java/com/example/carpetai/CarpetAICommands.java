package com.example.carpetai;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import carpet.CarpetServer;

import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT;

public class CarpetAICommands {

    public static void register() {
        EVENT.register((dispatcher, registryAccess, environment, registrationEnvironment) -> {
            dispatcher.register(CommandManager.literal("ai")
                .then(CommandManager.argument("playerName", StringArgumentType.word())
                    .then(CommandManager.argument("prompt", StringArgumentType.greedyString())
                        .executes(context -> {
                            String playerName = StringArgumentType.getString(context, "playerName");
                            String prompt = StringArgumentType.getString(context, "prompt");
                            ServerPlayerEntity source = context.getSource().getPlayer();
                            
                            if (source == null) {
                                context.getSource().sendError(Text.literal("This command can only be used by players."));
                                return 0;
                            }

                            // 异步调用 LLM
                            CompletableFuture.runAsync(() -> {
                                try {
                                    // 1. 检查 Carpet 假人是否存在
                                    var fakePlayer = CarpetServer.playerCommand.resolvePlayer(source, playerName);
                                    if (fakePlayer == null) {
                                        source.sendMessage(Text.literal("§c[AI] Player '" + playerName + "' not found or not a fake player."));
                                        return;
                                    }

                                    source.sendMessage(Text.literal("§e[AI] Thinking..."));

                                    // 2. 调用 LLM API
                                    String systemPrompt = "You are a Minecraft bot. Your name is " + playerName + ". " +
                                        "Respond with a SINGLE JSON object representing an action to perform. " +
                                        "Available actions: MOVE(x,y,z), LOOK(yaw,pitch), ATTACK(entity), USE_ITEM, " +
                                        "PLACE_BLOCK(x,y,z), BREAK_BLOCK(x,y,z), CHAT(message), SWAP_HOTBAR(slot), " +
                                        "CROUCH, JUMP, SNEAK, DROP, SLEEP, LOOK_AT_PLAYER(playerName). " +
                                        "Example: {\"action\":\"LOOK_AT_PLAYER\",\"player\":\"Steve\"}";
                                    
                                    String response = LLMClient.complete(systemPrompt, prompt);
                                    
                                    // 3. 解析并执行
                                    ActionParser.parseAndExecute(fakePlayer, response);
                                    source.sendMessage(Text.literal("§a[AI] Action executed."));

                                } catch (Exception e) {
                                    CarpetAIFakePlayer.LOGGER.error("AI Command Error", e);
                                    source.sendMessage(Text.literal("§c[AI] Error: " + e.getMessage()));
                                }
                            });

                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
            );
        });
    }
}
