package com.example.carpetai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class ActionParser {
    public static void parseAndExecute(ServerPlayerEntity player, String response) {
        try {
            // 尝试解析 JSON
            String jsonStr = response.trim();
            if (jsonStr.contains("```json")) {
                jsonStr = jsonStr.split("```json")[1].split("```")[0];
            }
            
            JsonObject action = JsonParser.parseString(jsonStr).getAsJsonObject();
            String type = action.get("action").getAsString().toUpperCase();

            switch (type) {
                case "MOVE":
                    double x = action.get("x").getAsDouble();
                    double y = action.get("y").getAsDouble();
                    double z = action.get("z").getAsDouble();
                    player.setPosition(x, y, z);
                    break;
                    
                case "LOOK":
                    float yaw = action.get("yaw").getAsFloat();
                    float pitch = action.get("pitch").getAsFloat();
                    player.setYaw(yaw);
                    player.setPitch(pitch);
                    break;
                    
                case "CHAT":
                    String message = action.get("message").getAsString();
                    player.getServer().getPlayerManager().broadcast(Text.literal(message), false);
                    break;
                    
                case "JUMP":
                    player.jump();
                    break;
                    
                case "CROUCH":
                    // 模拟潜行状态切换
                    player.setSneaking(!player.isSneaking());
                    break;
                    
                case "SLEEP":
                    // 逻辑待实现
                    break;
                    
                default:
                    CarpetAIFakePlayer.LOGGER.warn("Unknown action type: {}", type);
            }
        } catch (Exception e) {
            CarpetAIFakePlayer.LOGGER.error("Failed to parse action", e);
        }
    }
}
