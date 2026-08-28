package com.example.carpetai.action;

import com.example.carpetai.CarpetAIFakePlayer;
import com.example.carpetai.config.ModConfig;
import com.example.carpetai.entity.PlayerContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.Entity;

/**
 * 解析 LLM 返回的 JSON 动作并执行。
 * 注意：本类所有方法都必须在服务器主线程上调用。
 */
public class ActionExecutor {

    /**
     * 执行 LLM 返回的动作。返回是否成功执行。
     */
    public static boolean execute(ServerPlayerEntity player, String response, PlayerContext ctx) {
        try {
            JsonObject action = JsonParser.parseString(extractJson(response)).getAsJsonObject();
            if (!action.has("action")) {
                CarpetAIFakePlayer.LOGGER.warn("LLM response JSON has no 'action' field: {}", response);
                return false;
            }
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
                        player.getEntityWorld().getServer().getPlayerManager().broadcast(
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
                    player.getEntityWorld().getServer().getPlayerManager().broadcast(
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
                        player.getInventory().setSelectedSlot(slot);
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
                    player.getEntityWorld().getServer().getPlayerManager().broadcast(
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

    /**
     * 从 LLM 原始回复中提取 JSON 字符串。
     * 除 ``` 围栏外，LLM 还常在 JSON 前后附带说明文字，回退到截取首个 '{' 到最后一个 '}'。
     */
    private static String extractJson(String response) {
        String s = response.trim();
        if (s.contains("```json")) {
            s = s.split("```json")[1].split("```")[0];
        } else if (s.contains("```")) {
            s = s.split("```")[1].split("```")[0];
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            s = s.substring(start, end + 1);
        }
        return s;
    }

    /** 解析动作类型，解析失败返回 null。供命令层在主线程外判断 WAIT 等控制类动作。 */
    public static String parseActionType(String response) {
        try {
            JsonObject action = JsonParser.parseString(extractJson(response)).getAsJsonObject();
            return action.has("action") ? action.get("action").getAsString().toUpperCase() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析 WAIT 的秒数，缺省 1 秒。 */
    public static int parseWaitSeconds(String response) {
        try {
            JsonObject action = JsonParser.parseString(extractJson(response)).getAsJsonObject();
            if (action.has("seconds")) return Math.max(0, action.get("seconds").getAsInt());
        } catch (Exception ignored) {
        }
        return 1;
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
        var world = player.getEntityWorld();
        // 用 ofFloored 而非 (int) 强转：负坐标时 (int) 向零截断会取到错误的方块
        var pos = BlockPos.ofFloored(x, y, z);
        var state = world.getBlockState(pos);
        if (state.isAir()) {
            CarpetAIFakePlayer.LOGGER.warn("BREAK_BLOCK: no block at {},{},{}", x, y, z);
            return false;
        }
        // 设置玩家看向方块，然后模拟破坏
        player.lookAt(EntityAnchorArgumentType.EntityAnchor.FEET, pos.toCenterPos());
        // 破坏方块，1.21.1 只需要 BlockPos 参数
        player.interactionManager.tryBreakBlock(pos);
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
        var world = player.getEntityWorld();
        // 负坐标时 (int) 强转会向零截断、取错方块，必须向下取整
        var pos = BlockPos.ofFloored(x, y, z);
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
        // interactBlock 只能对已有的非空气方块交互，目标是空气位时会直接被跳过、永远放不出方块，
        // 因此直接用手中物品对应的方块设置目标位置。
        Block block = Block.getBlockFromItem(stack.getItem());
        if (block == Blocks.AIR) {
            CarpetAIFakePlayer.LOGGER.warn("PLACE_BLOCK: item in hand is not a block");
            return false;
        }
        player.lookAt(EntityAnchorArgumentType.EntityAnchor.FEET, pos.toCenterPos());
        world.setBlockState(pos, block.getDefaultState(), 3);
        if (!player.isCreative()) {
            stack.decrement(1);
        }
        CarpetAIFakePlayer.LOGGER.info("{} placed {} at {}", player.getName().getString(), block, pos);
        return true;
    }

    private static boolean executeAttack(ServerPlayerEntity player, JsonObject action) {
        // 攻击最近的目标实体；提示词与实现统一用 target，同时兼容 entity 字段
        String targetName = null;
        if (action.has("target")) {
            targetName = action.get("target").getAsString();
        } else if (action.has("entity")) {
            targetName = action.get("entity").getAsString();
        }
        var world = player.getEntityWorld();
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
        if (!action.has("target")) {
            CarpetAIFakePlayer.LOGGER.warn("FOLLOW: missing 'target' field");
            return false;
        }
        String targetName = action.get("target").getAsString();
        var world = player.getEntityWorld();
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