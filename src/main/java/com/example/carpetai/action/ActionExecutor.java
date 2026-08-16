package com.example.carpetai.action;

import com.example.carpetai.CarpetAIFakePlayer;
import com.example.carpetai.config.ModConfig;
import com.example.carpetai.entity.PlayerContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * 解析 LLM 返回的 JSON 动作并执行。
 */
public class ActionExecutor {

    /**
     * 执行 LLM 返回的动作。返回是否成功执行。
     */
    public static boolean execute(ServerPlayerEntity player, String response, PlayerContext ctx) {
        try {
            String jsonStr = response.trim();
            // 提取 JSON 块
            if (jsonStr.contains("```json")) {
                jsonStr = jsonStr.split("```json")[1].split("```")[0];
            } else if (jsonStr.contains("```")) {
                jsonStr = jsonStr.split("```")[1].split("```")[0];
            }

            JsonObject action = JsonParser.parseString(jsonStr).getAsJsonObject();
            String type = action.get("action").getAsString().toUpperCase();

            ModConfig config = ModConfig.load();

            // cooldown 检查
            if (ctx != null && !ctx.canAct(config.actionCooldownMs)) {
                CarpetAIFakePlayer.LOGGER.warn("Action cooldown not met for {}", player.getName().getString());
                return false;
            }

            boolean ok = false;

            switch (type) {
                case "MOVE":
                    double x = action.get("x").getAsDouble();
                    double y = action.get("y").getAsDouble();
                    double z = action.get("z").getAsDouble();
                    // 距离限制
                    double dist = Math.sqrt(
                        Math.pow(x - player.getX(), 2) +
                        Math.pow(y - player.getY(), 2) +
                        Math.pow(z - player.getZ(), 2));
                    if (dist > config.maxMoveDistance) {
                        CarpetAIFakePlayer.LOGGER.warn("MOVE distance {} exceeds limit {}", dist, config.maxMoveDistance);
                        player.getServer().getPlayerManager().broadcast(
                            Text.literal("<" + player.getName().getString() + "> §7(I can't move that far)"), false);
                        return false;
                    }
                    player.setPosition(x, y, z);
                    ok = true;
                    break;

                case "LOOK":
                    float yaw = getFloat(action, "yaw", player.getYaw());
                    float pitch = getFloat(action, "pitch", player.getPitch());
                    player.setYaw(yaw);
                    player.setPitch(pitch);
                    ok = true;
                    break;

                case "CHAT":
                    String message = action.get("message").getAsString();
                    player.getServer().getPlayerManager().broadcast(
                        Text.literal("<" + player.getName().getString() + "> " + message), false);
                    ok = true;
                    break;

                case "JUMP":
                    player.jump();
                    ok = true;
                    break;

                case "CROUCH":
                    player.setSneaking(!player.isSneaking());
                    ok = true;
                    break;

                case "DROP":
                    player.dropSelectedItem(false);
                    ok = true;
                    break;

                case "SWAP_HOTBAR":
                    int slot = action.get("slot").getAsInt();
                    if (slot >= 0 && slot <= 8) {
                        player.getInventory().selectedSlot = slot;
                        ok = true;
                    }
                    break;

                case "WAIT":
                    // 等待一段时间（通过 tick 系统处理，这里只是标记）
                    int seconds = action.has("seconds") ? action.get("seconds").getAsInt() : 1;
                    CarpetAIFakePlayer.LOGGER.info("{} is waiting for {} seconds", player.getName().getString(), seconds);
                    ok = true;
                    break;

                // 扩展动作（需要更多 Minecraft API 支持，暂时标记）
                case "BREAK_BLOCK":
                case "PLACE_BLOCK":
                case "ATTACK":
                case "USE_ITEM":
                    player.getServer().getPlayerManager().broadcast(
                        Text.literal("<" + player.getName().getString() + "> §7(" + type + " — not yet implemented)"), false);
                    ok = true;
                    break;

                default:
                    CarpetAIFakePlayer.LOGGER.warn("Unknown action type: {}", type);
                    return false;
            }

            if (ok && ctx != null) {
                ctx.lastAction = type;
                ctx.lastActionTime = System.currentTimeMillis();
            }
            return ok;

        } catch (Exception e) {
            CarpetAIFakePlayer.LOGGER.error("Failed to parse/execute action", e);
            return false;
        }
    }

    private static float getFloat(JsonObject obj, String key, float def) {
        return obj.has(key) ? obj.get(key).getAsFloat() : def;
    }
}