package org.tianjiserver.tianjicore.feature.fumo;

import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.util.EulerAngle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FumoConfigAndPoseTest {

    @Test
    void defaultPoseAppliesFixedRadiansToArmorStand() {
        ArmorStand stand = mock(ArmorStand.class);
        FumoPose.DEFAULT.applyTo(stand);

        verify(stand).setHeadPose(FumoPose.DEFAULT.head());
        verify(stand).setBodyPose(FumoPose.DEFAULT.body());
        verify(stand).setLeftArmPose(FumoPose.DEFAULT.leftArm());
        verify(stand).setRightArmPose(FumoPose.DEFAULT.rightArm());
        verify(stand).setLeftLegPose(FumoPose.DEFAULT.leftLeg());
        verify(stand).setRightLegPose(FumoPose.DEFAULT.rightLeg());
    }

    @Test
    void builtInContainerMaterialsAreBlocked() {
        assertTrue(FumoCraftGui.isBlockedContainerMaterial(null));
        assertTrue(FumoCraftGui.isBlockedContainerMaterial(Material.SHULKER_BOX));
        assertTrue(FumoCraftGui.isBlockedContainerMaterial(Material.RED_SHULKER_BOX));
        assertTrue(FumoCraftGui.isBlockedContainerMaterial(Material.BUNDLE));
        assertFalse(FumoCraftGui.isBlockedContainerMaterial(Material.DIAMOND_SWORD));
    }

    @Test
    void degreesToEulerAngleMatchesExactRadians() {
        EulerAngle angle = FumoPose.degreesToEulerAngle(180.0, -90.0, 45.0);
        assertEquals(Math.PI, angle.getX(), 1e-6);
        assertEquals(-Math.PI / 2.0, angle.getY(), 1e-6);
        assertEquals(Math.PI / 4.0, angle.getZ(), 1e-6);
    }

    @Test
    void defaultPoseSpreadsArmsOutward15DegreesAndLegs90DegreesApart() {
        assertEquals(FumoPose.degreesToEulerAngle(-30.0, 0.0, -15.0), FumoPose.DEFAULT.leftArm());
        assertEquals(FumoPose.degreesToEulerAngle(-30.0, 0.0, 15.0), FumoPose.DEFAULT.rightArm());
        assertEquals(FumoPose.degreesToEulerAngle(-85.0, -45.0, 0.0), FumoPose.DEFAULT.leftLeg());
        assertEquals(FumoPose.degreesToEulerAngle(-85.0, 45.0, 0.0), FumoPose.DEFAULT.rightLeg());
    }
}
