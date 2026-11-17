package org.tianjiserver.tianjicore.feature.fumo;

import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.tianjiserver.tianjicore.TianjiCore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Fumo 落点追踪管理器。
 * 让无物理影响的 Fumo 盔甲架以 -0.3 的相对高度追踪仅受重力影响的隐形盔甲架，
 * 并在隐形盔甲架落到表面后停止追踪并删除隐形盔甲架。
 */
final class FumoTrackerManager {

    /**
     * Fumo 盔甲架相对隐形重力盔甲架的高度偏移。
     */
    static final double TRACK_OFFSET_Y = -0.3;

    private static final double POSITION_EPSILON = 1.0e-5;

    private final TianjiCore plugin;
    private final Map<UUID, TrackingSession> sessionsByFumo = new HashMap<>();
    private final Map<UUID, UUID> fumoByTracker = new HashMap<>();

    FumoTrackerManager(TianjiCore plugin) {
        this.plugin = plugin;
    }

    /**
     * 启动 Fumo 盔甲架对隐形重力盔甲架的每 tick 追踪任务。
     */
    void startTracking(ArmorStand fumoStand, ArmorStand trackerStand, Location spawnLocation) {
        UUID fumoId = fumoStand.getUniqueId();
        UUID trackerId = trackerStand.getUniqueId();
        stopTrackingByFumoId(fumoId);

        TrackingSession session = new TrackingSession(
                fumoStand,
                trackerStand,
                spawnLocation.getX(),
                spawnLocation.getZ()
        );
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> tickSession(session),
                1L,
                1L
        );
        session.bindTask(task);

        sessionsByFumo.put(fumoId, session);
        fumoByTracker.put(trackerId, fumoId);
    }

    /**
     * 停止指定 Fumo 盔甲架当前的追踪任务，并清理关联的隐形盔甲架。
     */
    void stopTrackingByFumoId(UUID fumoId) {
        TrackingSession existing = sessionsByFumo.get(fumoId);
        if (existing != null) {
            finishSession(existing);
        }
    }

    /**
     * 根据隐形追踪盔甲架 UUID 查找其关联的 Fumo 盔甲架。
     */
    Optional<ArmorStand> findTrackedFumo(UUID trackerId) {
        UUID fumoId = fumoByTracker.get(trackerId);
        if (fumoId == null) {
            return Optional.empty();
        }
        TrackingSession session = sessionsByFumo.get(fumoId);
        if (session == null || !session.fumoStand.isValid()) {
            return Optional.empty();
        }
        return Optional.of(session.fumoStand);
    }

    /**
     * 判断给定隐形盔甲架是否正处于活跃追踪会话中。
     */
    boolean isActiveTracker(UUID trackerId) {
        return fumoByTracker.containsKey(trackerId);
    }

    /**
     * 模块停用或插件关闭时取消全部追踪任务并删除临时隐形盔甲架。
     */
    void shutdown() {
        for (TrackingSession session : List.copyOf(sessionsByFumo.values())) {
            finishSession(session);
        }
        sessionsByFumo.clear();
        fumoByTracker.clear();
    }

    private void tickSession(TrackingSession session) {
        ArmorStand fumo = session.fumoStand;
        ArmorStand tracker = session.trackerStand;
        if (!fumo.isValid() || !tracker.isValid() || !fumo.getWorld().equals(tracker.getWorld())) {
            finishSession(session);
            return;
        }

        // 先读取着地状态再做水平校正，避免传送过程影响本 tick 的 isOnGround 判断。
        boolean onGround = tracker.isOnGround();
        enforceGravityOnlyMotion(tracker, session.anchorX, session.anchorZ);
        syncFumoLocation(fumo, tracker);

        if (onGround) {
            finishSession(session);
        }
    }

    private static void enforceGravityOnlyMotion(ArmorStand tracker, double anchorX, double anchorZ) {
        Vector velocity = tracker.getVelocity();
        double clampedVy = Math.min(velocity.getY(), 0.0);
        Location loc = tracker.getLocation();
        boolean driftedHorizontally = Math.abs(loc.getX() - anchorX) > POSITION_EPSILON
                || Math.abs(loc.getZ() - anchorZ) > POSITION_EPSILON;

        if (driftedHorizontally) {
            Location corrected = new Location(
                    loc.getWorld(),
                    anchorX,
                    loc.getY(),
                    anchorZ,
                    loc.getYaw(),
                    loc.getPitch()
            );
            tracker.teleport(corrected, PlayerTeleportEvent.TeleportCause.PLUGIN, TeleportFlag.Relative.VELOCITY_Y);
            if (velocity.getY() > 0.0) {
                tracker.setVelocity(new Vector(0.0, 0.0, 0.0));
            }
            return;
        }

        if (velocity.getX() != 0.0 || velocity.getZ() != 0.0 || velocity.getY() > 0.0) {
            tracker.setVelocity(new Vector(0.0, clampedVy, 0.0));
        }
    }

    private static void syncFumoLocation(ArmorStand fumo, ArmorStand tracker) {
        Location trackerLoc = tracker.getLocation();
        Location fumoLoc = fumo.getLocation();
        double targetX = trackerLoc.getX();
        double targetY = trackerLoc.getY() + TRACK_OFFSET_Y;
        double targetZ = trackerLoc.getZ();

        if (Math.abs(fumoLoc.getX() - targetX) <= POSITION_EPSILON
                && Math.abs(fumoLoc.getY() - targetY) <= POSITION_EPSILON
                && Math.abs(fumoLoc.getZ() - targetZ) <= POSITION_EPSILON) {
            return;
        }

        Location target = new Location(
                trackerLoc.getWorld(),
                targetX,
                targetY,
                targetZ,
                fumoLoc.getYaw(),
                fumoLoc.getPitch()
        );
        fumo.teleport(target);
    }

    private void finishSession(TrackingSession session) {
        session.cancelTask();
        sessionsByFumo.remove(session.fumoStand.getUniqueId(), session);
        fumoByTracker.remove(session.trackerStand.getUniqueId(), session.fumoStand.getUniqueId());
        if (session.trackerStand.isValid()) {
            session.trackerStand.remove();
        }
    }

    private static final class TrackingSession {

        private final ArmorStand fumoStand;
        private final ArmorStand trackerStand;
        private final double anchorX;
        private final double anchorZ;
        private BukkitTask task;

        private TrackingSession(ArmorStand fumoStand, ArmorStand trackerStand, double anchorX, double anchorZ) {
            this.fumoStand = fumoStand;
            this.trackerStand = trackerStand;
            this.anchorX = anchorX;
            this.anchorZ = anchorZ;
        }

        private void bindTask(BukkitTask task) {
            this.task = task;
        }

        private void cancelTask() {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
    }
}
