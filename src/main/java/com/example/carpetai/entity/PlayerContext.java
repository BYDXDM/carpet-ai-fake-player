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
    // 读写一律通过下方 synchronized 方法进行，外部只读访问请用 snapshotHistory() 取防御性快照
    public final List<Map<String, String>> dialogueHistory = new ArrayList<>();

    // 当前 token 消耗（近似）
    public int tokenUsage = 0;
    public int tokenBudget = 0; // 0 = 无限制

    // 任务队列（isBusy 由后台工作线程与主线程共同读写，需 volatile 保证可见性）
    public volatile boolean isBusy = false;
    public String currentTask = "";

    public PlayerContext(String playerName) {
        this.playerName = playerName;
        this.taskProgress = 0;
    }

    /** 返回对话历史的防御性快照（与写入方共用本对象锁，避免并发修改）。 */
    public synchronized List<Map<String, String>> snapshotHistory() {
        return new ArrayList<>(dialogueHistory);
    }

    public synchronized void addDialogue(String role, String content) {
        dialogueHistory.add(Map.of("role", role, "content", content));
    }

    public synchronized void trimHistory(int maxRounds) {
        while (dialogueHistory.size() > maxRounds * 2) {
            dialogueHistory.removeFirst();
        }
    }

    /** 清空对话历史（/ai clear 与快照读取共用本对象锁）。 */
    public synchronized void clearDialogue() {
        dialogueHistory.clear();
    }

    public boolean canAct(long cooldownMs) {
        return System.currentTimeMillis() - lastActionTime >= cooldownMs;
    }

    public boolean isOverBudget() {
        return tokenBudget > 0 && tokenUsage >= tokenBudget;
    }
}