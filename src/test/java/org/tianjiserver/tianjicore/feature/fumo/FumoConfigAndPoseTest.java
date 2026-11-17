package org.tianjiserver.tianjicore.feature.fumo;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.util.EulerAngle;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FumoConfigAndPoseTest {

    @Test
    void parsesCustomPoseDegreesAndAppliesRadiansToArmorStand() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("fumo.pose.head", List.of(90.0, -45.0, 30.0));
        yaml.set("fumo.pose.left-arm", List.of(-30.0, 15.0, -10.0));
        yaml.set("fumo.pose.invalid", List.of(10.0, 20.0));

        FumoPose pose = FumoPose.fromConfig(yaml.getConfigurationSection("fumo.pose"));
        assertEquals(Math.toRadians(90.0), pose.head().getX(), 1e-6);
        assertEquals(Math.toRadians(-45.0), pose.head().getY(), 1e-6);
        assertEquals(Math.toRadians(30.0), pose.head().getZ(), 1e-6);
        assertEquals(FumoPose.DEFAULT.body(), pose.body());

        ArmorStand stand = mock(ArmorStand.class);
        pose.applyTo(stand);
        verify(stand).setHeadPose(pose.head());
        verify(stand).setBodyPose(pose.body());
        verify(stand).setLeftArmPose(pose.leftArm());
        verify(stand).setRightArmPose(pose.rightArm());
        verify(stand).setLeftLegPose(pose.leftLeg());
        verify(stand).setRightLegPose(pose.rightLeg());
    }

    @Test
    void malformedPoseFallsBackToDefaultAngles() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("fumo.pose.head", List.of(10.0, "bad"));
        yaml.set("fumo.pose.body", "not-a-list");
        yaml.set("fumo.pose.left-leg", List.of(Double.NaN, 0.0, 0.0));

        FumoPose pose = FumoPose.fromConfig(yaml.getConfigurationSection("fumo.pose"));
        assertEquals(FumoPose.DEFAULT, pose);
    }

    @Test
    void configReadsAllFumoSettingsAndBuiltInContainerBlocks() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("fumo.attributes.small", false);
        yaml.set("fumo.attributes.arms", false);
        yaml.set("fumo.attributes.base-plate", true);
        yaml.set("fumo.attributes.gravity", true);
        yaml.set("fumo.attributes.custom-model-data", 1001);
        yaml.set("fumo.craft.require-at-least-one-item", false);
        yaml.set("fumo.craft.blocked-materials", List.of("bedrock", "UNKNOWN_ITEM"));
        yaml.set("fumo.protection.prevent-explosion-damage", false);
        yaml.set("fumo.protection.prevent-environmental-damage", false);
        yaml.set("fumo.protection.sneak-right-click-pickup", false);
        yaml.set("fumo.limits.max-per-chunk", 8);

        FumoConfig config = FumoConfig.from(yaml);
        assertFalse(config.small());
        assertFalse(config.arms());
        assertTrue(config.basePlate());
        assertTrue(config.gravity());
        assertEquals(1001, config.customModelData());
        assertFalse(config.requireAtLeastOneItem());
        assertTrue(config.isBlockedMaterial(Material.BEDROCK));
        assertTrue(config.isBlockedMaterial(Material.SHULKER_BOX));
        assertTrue(config.isBlockedMaterial(Material.RED_SHULKER_BOX));
        assertTrue(config.isBlockedMaterial(Material.BUNDLE));
        assertFalse(config.isBlockedMaterial(Material.DIAMOND_SWORD));
        assertFalse(config.preventExplosionDamage());
        assertFalse(config.preventEnvironmentalDamage());
        assertFalse(config.sneakRightClickPickup());
        assertEquals(8, config.maxPerChunk());
    }

    @Test
    void degreesToEulerAngleMatchesExactRadians() {
        EulerAngle angle = FumoPose.degreesToEulerAngle(180.0, -90.0, 45.0);
        assertEquals(Math.PI, angle.getX(), 1e-6);
        assertEquals(-Math.PI / 2.0, angle.getY(), 1e-6);
        assertEquals(Math.PI / 4.0, angle.getZ(), 1e-6);
    }

    @Test
    void defaultPoseSpreadsArmsOutward15DegreesAndLegs90DegreesApart() throws Exception {
        assertEquals(FumoPose.degreesToEulerAngle(-30.0, 0.0, -15.0), FumoPose.DEFAULT.leftArm());
        assertEquals(FumoPose.degreesToEulerAngle(-30.0, 0.0, 15.0), FumoPose.DEFAULT.rightArm());
        assertEquals(FumoPose.degreesToEulerAngle(-85.0, -45.0, 0.0), FumoPose.DEFAULT.leftLeg());
        assertEquals(FumoPose.degreesToEulerAngle(-85.0, 45.0, 0.0), FumoPose.DEFAULT.rightLeg());

        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(stream);
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            );
            assertEquals(FumoPose.DEFAULT, FumoPose.fromConfig(yaml.getConfigurationSection("fumo.pose")));
        }
    }
}
