package com.example.carpetai.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 每个假人的运行时上下文，包含对话历史、token 配额、任务状态等。
 */
public class PlayerContext {

    public String playerName;
    public String lastAction;
    public long lastActionTime;
    public long lastMessageTime;
    public int taskProgress;

    // 对话历史（最近 N 轮，每轮 {role, content}）
    public final List<Map<String, String>> dialogueHistory = new ArrayList<>();

    // 当前 token 消耗（近似）
    public int tokenUsage = 0;
    public int tokenBudget = 0; // 0 = 无限制

    // 任务队列
    public boolean isBusy = false;
    public String currentTask = "";

    public PlayerContext(String playerName) {
        this.playerName = playerName;
        this.taskProgress = 0;
    }

    public void addDialogue(String role, String content) {
        dialogueHistory.add(Map.of("role", role, "content", content));
    }

    public void trimHistory(int maxRounds) {
        while (dialogueHistory.size() > maxRounds * 2) {
            dialogueHistory.removeFirst();
        }
    }

    public boolean canAct(long cooldownMs) {
        return System.currentTimeMillis() - lastActionTime >= cooldownMs;
    }

    public boolean isOverBudget() {
        return tokenBudget > 0 && tokenUsage >= tokenBudget;
    }
}