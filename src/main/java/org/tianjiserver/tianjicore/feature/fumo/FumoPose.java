package org.tianjiserver.tianjicore.feature.fumo;

import org.bukkit.entity.ArmorStand;
import org.bukkit.util.EulerAngle;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Fumo 玩偶固定姿势。
 * 使用角度制（degrees）定义并转换为盔甲架所需弧度。
 */
public record FumoPose(
        EulerAngle head,
        EulerAngle body,
        EulerAngle leftArm,
        EulerAngle rightArm,
        EulerAngle leftLeg,
        EulerAngle rightLeg
) {

    /**
     * 放置时为每个肢体与头部施加的三轴偏转总角度（角度制）。
     */
    public static final double PLACEMENT_JITTER_TOTAL_DEGREES = 2.0;

    /**
     * 默认可爱坐姿（角度制）。
     */
    public static final FumoPose DEFAULT = new FumoPose(
            degreesToEulerAngle(0.0, 0.0, 0.0),
            degreesToEulerAngle(0.0, 0.0, 0.0),
            degreesToEulerAngle(-30.0, 0.0, -15.0),
            degreesToEulerAngle(-30.0, 0.0, 15.0),
            degreesToEulerAngle(-85.0, -20.0, 0.0),
            degreesToEulerAngle(-85.0, 20.0, 0.0)
    );

    /**
     * 基于当前姿势生成副本，为头部与四肢各自加入三轴共计 {@code totalDegrees}° 的随机偏转（躯干不变）。
     */
    public FumoPose withRandomLimbAndHeadJitter(double totalDegrees) {
        return withRandomLimbAndHeadJitter(totalDegrees, ThreadLocalRandom.current());
    }

    /**
     * 基于当前姿势生成副本，使用指定随机源为头部与四肢各自加入三轴共计 {@code totalDegrees}° 的随机偏转（躯干不变）。
     */
    public FumoPose withRandomLimbAndHeadJitter(double totalDegrees, RandomGenerator random) {
        if (totalDegrees <= 0.0) {
            return this;
        }
        return new FumoPose(
                jitterEulerAngle(head, totalDegrees, random),
                body,
                jitterEulerAngle(leftArm, totalDegrees, random),
                jitterEulerAngle(rightArm, totalDegrees, random),
                jitterEulerAngle(leftLeg, totalDegrees, random),
                jitterEulerAngle(rightLeg, totalDegrees, random)
        );
    }

    /**
     * 将固定姿势应用到目标盔甲架。
     */
    public void applyTo(ArmorStand stand) {
        stand.setHeadPose(head);
        stand.setBodyPose(body);
        stand.setLeftArmPose(leftArm);
        stand.setRightArmPose(rightArm);
        stand.setLeftLegPose(leftLeg);
        stand.setRightLegPose(rightLeg);
    }

    /**
     * 将三轴角度制（degrees）转换为 {@link EulerAngle}。
     */
    public static EulerAngle degreesToEulerAngle(double xDeg, double yDeg, double zDeg) {
        return new EulerAngle(Math.toRadians(xDeg), Math.toRadians(yDeg), Math.toRadians(zDeg));
    }

    private static EulerAngle jitterEulerAngle(EulerAngle base, double totalDegrees, RandomGenerator random) {
        double cut1 = random.nextDouble(totalDegrees);
        double cut2 = random.nextDouble(totalDegrees);
        double min = Math.min(cut1, cut2);
        double max = Math.max(cut1, cut2);

        double dx = signedOffset(min, random);
        double dy = signedOffset(max - min, random);
        double dz = signedOffset(totalDegrees - max, random);
        return base.add(Math.toRadians(dx), Math.toRadians(dy), Math.toRadians(dz));
    }

    private static double signedOffset(double magnitude, RandomGenerator random) {
        return random.nextBoolean() ? magnitude : -magnitude;
    }
}
