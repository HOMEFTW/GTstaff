package com.andgatech.gtstaff.fakeplayer;

import net.minecraft.entity.EnumCreatureType;
import net.minecraftforge.event.entity.living.LivingSpawnEvent.CheckSpawn;

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

        for (FakePlayer fp : FakePlayerRegistry.getAll().values()) {
            if (!fp.isMonsterRepelling()) continue;
            if (fp.dimension != dim) continue;
            int range = fp.getMonsterRepelRange();
            double dx = fp.posX - spawnX;
            double dy = fp.posY - spawnY;
            double dz = fp.posZ - spawnZ;
            if (dx * dx + dy * dy + dz * dz <= (double) range * range) {
                event.setResult(Event.Result.DENY);
                return;
            }
        }
    }
}
