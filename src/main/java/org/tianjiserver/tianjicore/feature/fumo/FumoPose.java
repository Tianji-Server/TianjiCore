package org.tianjiserver.tianjicore.feature.fumo;

import org.bukkit.entity.ArmorStand;
import org.bukkit.util.EulerAngle;

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
     * 默认可爱坐姿（角度制）。
     */
    public static final FumoPose DEFAULT = new FumoPose(
            degreesToEulerAngle(0.0, 0.0, 0.0),
            degreesToEulerAngle(0.0, 0.0, 0.0),
            degreesToEulerAngle(-30.0, 0.0, -15.0),
            degreesToEulerAngle(-30.0, 0.0, 15.0),
            degreesToEulerAngle(-85.0, -30.0, 0.0),
            degreesToEulerAngle(-85.0, 30.0, 0.0)
    );

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
}
