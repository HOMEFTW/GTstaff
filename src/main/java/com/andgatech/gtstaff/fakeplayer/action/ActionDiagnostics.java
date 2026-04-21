package com.andgatech.gtstaff.fakeplayer.action;

import net.minecraft.entity.player.EntityPlayerMP;

import com.andgatech.gtstaff.GTstaff;
import com.andgatech.gtstaff.config.Config;

public final class ActionDiagnostics {

    private ActionDiagnostics() {}

    public static void logUse(EntityPlayerMP player, UseResult result) {
        if (!Config.fakePlayerActionDiagnostics || result == null) {
            return;
        }
        GTstaff.LOG.info(
            "[ActionDiagnostics] use bot={} blockUsed={} itemUsed={} bridgeUsed={} swingTriggered={}",
            botName(player),
            result.blockUsed(),
            result.itemUsed(),
            result.bridgeUsed(),
            result.swingTriggered());
    }

    public static void logAttack(EntityPlayerMP player, AttackResult result) {
        if (!Config.fakePlayerActionDiagnostics || result == null) {
            return;
        }
        GTstaff.LOG.info(
            "[ActionDiagnostics] attack bot={} accepted={} usedFallback={} swung={}",
            botName(player),
            result.accepted(),
            result.usedFallback(),
            result.swung());
    }

    private static String botName(EntityPlayerMP player) {
        if (player == null) {
            return "<null>";
        }
        try {
            return player.getCommandSenderName();
        } catch (RuntimeException ignored) {
            return "<unresolved>";
        }
    }
}
