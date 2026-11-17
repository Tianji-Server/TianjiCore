package org.tianjiserver.tianjicore.feature.fumo;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.util.EulerAngle;

import java.util.List;

/**
 * Fumo 玩偶固定姿势配置。
 * 使用角度制（degrees）读取并转换为盔甲架所需弧度。
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
            degreesToEulerAngle(-85.0, -45.0, 0.0),
            degreesToEulerAngle(-85.0, 45.0, 0.0)
    );

    /**
     * 从配置节点解析六组固定姿势；缺失或格式非法时回退到默认值。
     */
    public static FumoPose fromConfig(ConfigurationSection section) {
        if (section == null) {
            return DEFAULT;
        }
        return new FumoPose(
                parseAngle(section, "head", DEFAULT.head),
                parseAngle(section, "body", DEFAULT.body),
                parseAngle(section, "left-arm", DEFAULT.leftArm),
                parseAngle(section, "right-arm", DEFAULT.rightArm),
                parseAngle(section, "left-leg", DEFAULT.leftLeg),
                parseAngle(section, "right-leg", DEFAULT.rightLeg)
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

    private static EulerAngle parseAngle(ConfigurationSection section, String key, EulerAngle fallback) {
        if (!section.isList(key)) {
            return fallback;
        }
        List<?> rawValues = section.getList(key);
        if (rawValues == null || rawValues.size() != 3) {
            return fallback;
        }

        double[] degrees = new double[3];
        for (int i = 0; i < 3; i++) {
            if (!(rawValues.get(i) instanceof Number number)) {
                return fallback;
            }
            double value = number.doubleValue();
            if (!Double.isFinite(value)) {
                return fallback;
            }
            degrees[i] = value;
        }
        return degreesToEulerAngle(degrees[0], degrees[1], degrees[2]);
    }
}
