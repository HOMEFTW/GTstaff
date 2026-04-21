package com.andgatech.gtstaff.fakeplayer;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingSpawnEvent.CheckSpawn;

import com.andgatech.gtstaff.fakeplayer.runtime.BotHandle;
import com.andgatech.gtstaff.fakeplayer.runtime.BotRuntimeView;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class MonsterRepellentService {

    public static final MonsterRepellentService INSTANCE = new MonsterRepellentService();

    private MonsterRepellentService() {}

    @SubscribeEvent
    public void denyMobSpawn(CheckSpawn event) {
        if (event.getResult() == Event.Result.DENY) return;
        if (event.getResult() == Event.Result.ALLOW) return;
        if (!event.entityLiving.isCreatureType(EnumCreatureType.monster, false)) return;

        int dim = event.entity.worldObj.provider.dimensionId;
        double spawnX = event.entity.posX;
        double spawnY = event.entity.posY;
        double spawnZ = event.entity.posZ;

        for (BotHandle handle : FakePlayerRegistry.getAllBotHandles()) {
            if (!(handle instanceof BotRuntimeView runtime) || !runtime.repel()
                .repelling()) {
                continue;
            }
            EntityPlayerMP player = runtime.entity()
                .asPlayer();
            if (player == null || runtime.dimension() != dim) {
                continue;
            }
            int range = runtime.repel()
                .repelRange();
            double dx = player.posX - spawnX;
            double dy = player.posY - spawnY;
            double dz = player.posZ - spawnZ;
            if (dx * dx + dy * dy + dz * dz <= (double) range * range) {
                event.setResult(Event.Result.DENY);
                return;
            }
        }
    }
}
