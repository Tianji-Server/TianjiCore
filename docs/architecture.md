# 代码结构与开发说明

本文档面向插件维护者与开发者，介绍 `TianjiCore` 的目录布局、核心机制与构建约定。

---

## 目录结构

```text
TianjiCore/
├── pom.xml                                      # Maven 单模块配置（Java 25 / Paper API / Lamp / Shade）
├── AGENTS.md                                    # AI 协作与构建提交规范
├── README.md                                    # 项目概览与快速上手
├── docs/
│   ├── architecture.md                          # 本文档：架构与开发说明
│   └── modules.md                               # 五个功能模块的详细规格说明
└── src/main/
    ├── resources/
    │   ├── config.yml                           # 默认配置文件
    │   └── plugin.yml                           # Paper 插件清单（命令、权限）
    └── java/org/tianjiserver/tianjicore/
        ├── TianjiCore.java                      # 插件主入口（JavaPlugin）
        ├── TianjiCoreCommand.java               # /tianjicore (/tc) 命令处理器
        ├── TianjiCoreModuleHelper.java          # 命令层与模块管理器的薄封装
        ├── TianjiCoreModuleManager.java         # 模块注册、启停、重载与配置持久化
        ├── LifecycleModule.java                 # 可选生命周期清理接口（shutdown）
        ├── feature/
        │   ├── FirstJoinMessage.java            # 首次进服欢迎消息模块
        │   ├── PhantomSpawnBlocker.java         # 阻止幻翼生成模块
        │   └── fumo/
        │       ├── FumoModule.java              # 玩偶模块入口与事件路由
        │       ├── FumoCommand.java             # /tianji-fumo (/fumo) 命令处理器
        │       ├── FumoCraftGui.java            # 管理员玩偶编辑台会话管理与事件分发
        │       ├── FumoGuiHolder.java           # 编辑台单玩家会话状态与 InventoryHolder
        │       ├── FumoGuiLayout.java           # 6×9（54 格）十字架装备槽与右侧面板布局定义
        │       ├── FumoGuiSlots.java            # 十字架槽位占位符、合法性校验与 Shift 智能填入
        │       ├── FumoGuiRenderer.java         # 编辑台背景、分割线与右侧控制面板渲染
        │       ├── FumoGuiInteraction.java      # 槽位放置/拖拽交互与快捷操作处理
        │       ├── FumoGuiItems.java            # 编辑台物品构建、文本与音效反馈工具
        │       ├── FumoItemCodec.java           # 玩偶物品/实体 PDC 编解码器
        │       ├── FumoPose.java                # 固定坐姿与放置随机微调
        │       ├── FumoTrackerManager.java      # 隐形重力追踪与落点同步管理器
        │       └── FumoEntityListener.java      # 放置、回收与实体保护逻辑
        └── fixer/
            ├── EndermanBlockMoveBlocker.java    # 禁止末影人搬动方块模块
            └── RecipeBugFix.java                # 配方缓存与登录同步修复模块
```

---

## 核心机制

### 1. 模块注册与生命周期

- **集中注册**：所有模块在 `TianjiCoreModuleManager#bootstrap()` 中通过 `registerModule(...)` 统一注册，指定：
  - 模块主键（配置路径 `modules.<key>.enabled`）
  - 中文显示名称
  - 是否支持 `/tc toggle` 运行时开关
  - 监听器工厂（`Supplier<? extends Listener>`）
  - 命令别名列表（不区分大小写）
- **启停与监听器隔离**：
  - 模块启用时由工厂创建实例并通过 `PluginManager#registerEvents` 注册监听器。
  - 模块关闭或重载前，若实例实现了 `LifecycleModule`，会先调用 `shutdown()` 释放资源（如关闭打开的 GUI、退还物品、取消调度任务并清理临时实体），再通过 `HandlerList.unregisterAll` 注销事件监听器。
- **故障隔离与 YAML 预检**：
  - 重载前会先使用独立 `YamlConfiguration#load` 校验 `config.yml` 语法，防止 `JavaPlugin#reloadConfig()` 吞掉解析异常导致误报成功。
  - 整体重载按模块顺序应用，单个模块启动失败时记录原因并继续处理后续模块，不会因部分失败中断其他模块。

### 2. 命令框架集成与命名规范

- 使用 `io.github.revxrsal:lamp.common` 与 `lamp.bukkit`（`4.0.0-rc.17`）注解驱动命令解析。
- 在 `TianjiCore#onEnable()` 中通过 `BukkitLamp.builder(this).build()` 注册 `TianjiCoreCommand` 与 `FumoCommand`。
- `FumoCommand` 通过 `Supplier<Optional<FumoModule>>` 按需获取当前已启用的 `fumo` 模块实例，避免直接持有过期引用，从而兼容运行时的 `toggle` 与 `reload`。
- **命令命名规范**：
  - 插件核心管理主命令为 `/tianjicore`，缩写别名为 `/tc`（不注册 `/tianji`）。
  - 模块独立命令的主命令名**必须**以 `tianji-<module>` 格式注册（例如 `/tianji-fumo`），避免与其他插件的根命令冲突。
  - 仅当模块命令名特征极强、几乎不可能与其他插件冲突时，才允许额外附带无前缀短别名（例如 `/fumo`）；`plugin.yml` 中的命令主名与 `aliases` 须与 Lamp `@Command` 注解保持一致。

### 3. 构建与依赖策略

- **目标平台**：Java 25 + Paper API `26.2.build.60-beta`（`provided`）。
- **Shade 重定位**：`maven-shade-plugin` 将 `revxrsal` 包重定位至 `relocate.TianjiCore.revxrsal`，避免与其他插件的 Lamp 版本冲突。
- **无测试约束**：根据 [AGENTS.md](../AGENTS.md) 规范，仓库不保留测试目录与测试依赖，构建通过 `mvn clean package` 完成编译和打包。
