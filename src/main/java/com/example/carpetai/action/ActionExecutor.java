package com.example.carpetai.action;

import com.example.carpetai.CarpetAIFakePlayer;
import com.example.carpetai.config.ModConfig;
import com.example.carpetai.entity.PlayerContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.entity.Entity;

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

                // 扩展动作
                case "BREAK_BLOCK":
                    ok = executeBreakBlock(player, action);
                    break;
                case "PLACE_BLOCK":
                    ok = executePlaceBlock(player, action);
                    break;
                case "ATTACK":
                    ok = executeAttack(player, action);
                    break;
                case "FOLLOW":
                    ok = executeFollow(player, action);
                    break;
                case "USE_ITEM":
                    player.getServer().getPlayerManager().broadcast(
                        Text.literal("<" + player.getName().getString() + "> §7(USE_ITEM — not yet implemented)"), false);
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

    // ====== Action implementations ======

    private static boolean executeBreakBlock(ServerPlayerEntity player, JsonObject action) {
        double x = action.get("x").getAsDouble();
        double y = action.get("y").getAsDouble();
        double z = action.get("z").getAsDouble();
        // 距离检查
        double dist = player.squaredDistanceTo(x, y, z);
        if (dist > 25.0) { // 5 格以内
            CarpetAIFakePlayer.LOGGER.warn("BREAK_BLOCK too far: {}", dist);
            return false;
        }
        // 使用 ServerPlayerInteractionManager 破坏方块
        var world = player.getServerWorld();
        var pos = new net.minecraft.util.math.BlockPos((int) x, (int) y, (int) z);
        var state = world.getBlockState(pos);
        if (state.isAir()) {
            CarpetAIFakePlayer.LOGGER.warn("BREAK_BLOCK: no block at {},{},{}", x, y, z);
            return false;
        }
        // 设置玩家看向方块，然后模拟破坏
        player.lookAt(EntityAnchorArgumentType.EntityAnchor.FEET, pos.toCenterPos());
        player.interactionManager.tryBreakBlock(pos, Direction.UP, pos.toCenterPos(), true);
        CarpetAIFakePlayer.LOGGER.info("{} breaking block at {}", player.getName().getString(), pos);
        return true;
    }

    private static boolean executePlaceBlock(ServerPlayerEntity player, JsonObject action) {
        double x = action.get("x").getAsDouble();
        double y = action.get("y").getAsDouble();
        double z = action.get("z").getAsDouble();
        double dist = player.squaredDistanceTo(x, y, z);
        if (dist > 25.0) {
            CarpetAIFakePlayer.LOGGER.warn("PLACE_BLOCK too far: {}", dist);
            return false;
        }
        var world = player.getServerWorld();
        var pos = new net.minecraft.util.math.BlockPos((int) x, (int) y, (int) z);
        // 检查目标位置是否为空
        if (!world.getBlockState(pos).isAir()) {
            CarpetAIFakePlayer.LOGGER.warn("PLACE_BLOCK: position occupied at {},{},{}", x, y, z);
            return false;
        }
        // 检查手持物品
        var stack = player.getMainHandStack();
        if (stack.isEmpty()) {
            CarpetAIFakePlayer.LOGGER.warn("PLACE_BLOCK: no item in hand");
            return false;
        }
        player.lookAt(EntityAnchorArgumentType.EntityAnchor.FEET, pos.toCenterPos());
        // 使用 interactionManager 放置方块
        var result = player.interactionManager.interactBlock(
            player, player.getServerWorld(), stack, Hand.MAIN_HAND,
            new BlockHitResult(pos.toCenterPos(), Direction.UP, pos, false)
        );
        CarpetAIFakePlayer.LOGGER.info("{} placing block at {}: {}", player.getName().getString(), pos, result);
        return true;
    }

    private static boolean executeAttack(ServerPlayerEntity player, JsonObject action) {
        // 攻击最近的目标实体
        String targetName = action.has("target") ? action.get("target").getAsString() : null;
        var world = player.getServerWorld();
        double range = 5.0;
        var closest = (Entity) null;
        double closestDist = Double.MAX_VALUE;

        // 收集附近的生物实体
        var box = player.getBoundingBox().expand(range);
        var nearby = world.getOtherEntities(player, box);
        for (var entity : nearby) {
            if (entity == player) continue;
            if (!entity.isLiving()) continue;
            double d = player.squaredDistanceTo(entity);
            if (d > range * range) continue;
            if (targetName != null && !entity.getName().getString().equalsIgnoreCase(targetName)) continue;
            if (d < closestDist) {
                closestDist = d;
                closest = entity;
            }
        }
        if (closest == null) {
            CarpetAIFakePlayer.LOGGER.warn("ATTACK: no target found");
            return false;
        }
        player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, closest.getEyePos());
        player.attack(closest);
        CarpetAIFakePlayer.LOGGER.info("{} attacking {}", player.getName().getString(), closest.getName().getString());
        return true;
    }

    private static boolean executeFollow(ServerPlayerEntity player, JsonObject action) {
        String targetName = action.get("target").getAsString();
        var world = player.getServerWorld();
        // 先按名字精确匹配
        var target = (ServerPlayerEntity) null;
        for (var p : world.getPlayers()) {
            if (p.getName().getString().equalsIgnoreCase(targetName)) {
                target = p;
                break;
            }
        }
        if (target == null) {
            CarpetAIFakePlayer.LOGGER.warn("FOLLOW: target '{}' not found", targetName);
            return false;
        }
        // 保持 2 格距离跟随
        double followDist = action.has("distance") ? action.get("distance").getAsDouble() : 2.0;
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > followDist) {
            double scale = (dist - followDist) / dist;
            player.setPosition(
                player.getX() + dx * scale,
                target.getY(),  // 保持同一 Y 层
                player.getZ() + dz * scale
            );
        }
        player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, target.getEyePos());
        return true;
    }
}