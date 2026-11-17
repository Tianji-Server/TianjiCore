package org.tianjiserver.tianjicore.feature.fumo;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.Set;

/**
 * Fumo 模块运行时配置快照。
 */
public record FumoConfig(
        FumoPose pose,
        boolean small,
        boolean arms,
        boolean basePlate,
        boolean gravity,
        int customModelData,
        boolean requireAtLeastOneItem,
        Set<Material> blockedMaterials,
        boolean preventExplosionDamage,
        boolean preventEnvironmentalDamage,
        boolean sneakRightClickPickup,
        int maxPerChunk
) {

    /**
     * 默认配置实例。
     */
    public static final FumoConfig DEFAULT = new FumoConfig(
            FumoPose.DEFAULT,
            true,
            true,
            false,
            false,
            0,
            true,
            Set.of(),
            true,
            true,
            true,
            16
    );

    /**
     * 从插件配置加载 Fumo 参数，缺失项自动回退至默认值。
     */
    public static FumoConfig from(FileConfiguration config) {
        if (config == null) {
            return DEFAULT;
        }

        ConfigurationSection root = config.getConfigurationSection("fumo");
        if (root == null) {
            return DEFAULT;
        }

        FumoPose pose = FumoPose.fromConfig(root.getConfigurationSection("pose"));
        ConfigurationSection attributes = root.getConfigurationSection("attributes");
        ConfigurationSection craft = root.getConfigurationSection("craft");
        ConfigurationSection protection = root.getConfigurationSection("protection");
        ConfigurationSection limits = root.getConfigurationSection("limits");

        boolean small = getBoolean(attributes, "small", DEFAULT.small);
        boolean arms = getBoolean(attributes, "arms", DEFAULT.arms);
        boolean basePlate = getBoolean(attributes, "base-plate", DEFAULT.basePlate);
        boolean gravity = getBoolean(attributes, "gravity", DEFAULT.gravity);
        int customModelData = getInt(attributes, "custom-model-data", DEFAULT.customModelData);

        boolean requireAtLeastOneItem = getBoolean(craft, "require-at-least-one-item", DEFAULT.requireAtLeastOneItem);
        Set<Material> blockedMaterials = parseBlockedMaterials(craft);

        boolean preventExplosionDamage = getBoolean(protection, "prevent-explosion-damage", DEFAULT.preventExplosionDamage);
        boolean preventEnvironmentalDamage = getBoolean(protection, "prevent-environmental-damage", DEFAULT.preventEnvironmentalDamage);
        boolean sneakRightClickPickup = getBoolean(protection, "sneak-right-click-pickup", DEFAULT.sneakRightClickPickup);

        int maxPerChunk = getInt(limits, "max-per-chunk", DEFAULT.maxPerChunk);

        return new FumoConfig(
                pose,
                small,
                arms,
                basePlate,
                gravity,
                customModelData,
                requireAtLeastOneItem,
                blockedMaterials,
                preventExplosionDamage,
                preventEnvironmentalDamage,
                sneakRightClickPickup,
                maxPerChunk
        );
    }

    /**
     * 判断指定材质是否处于内置或配置的禁止列表。
     */
    public boolean isBlockedMaterial(Material material) {
        if (material == null) {
            return true;
        }
        if (material == Material.BUNDLE || material.name().endsWith("SHULKER_BOX")) {
            return true;
        }
        return blockedMaterials.contains(material);
    }

    private static boolean getBoolean(ConfigurationSection section, String key, boolean fallback) {
        return section == null ? fallback : section.getBoolean(key, fallback);
    }

    private static int getInt(ConfigurationSection section, String key, int fallback) {
        return section == null ? fallback : section.getInt(key, fallback);
    }

    private static Set<Material> parseBlockedMaterials(ConfigurationSection craft) {
        if (craft == null) {
            return Set.of();
        }
        EnumSet<Material> result = EnumSet.noneOf(Material.class);
        for (String name : craft.getStringList("blocked-materials")) {
            Material matched = Material.matchMaterial(name.trim());
            if (matched != null) {
                result.add(matched);
            }
        }
        return result.isEmpty() ? Set.of() : Set.copyOf(result);
    }
}
