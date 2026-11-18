# 代码结构与开发说明

本文档面向插件维护者与开发者，介绍 `TianjiCore` 的多模块目录布局、HTTP 远程控制机制与构建约定。

---

## 目录结构

```text
TianjiCore/
├── pom.xml                                                 # Maven 多模块父工程（Java 25 / Lamp / Shade）
├── AGENTS.md                                               # AI 协作与构建提交规范
├── README.md                                               # 项目概览与快速上手
├── docs/
│   ├── architecture.md                                     # 本文档：架构与开发说明
│   └── modules.md                                          # 五个功能模块的详细规格说明
├── common/
│   ├── pom.xml                                             # 共享控制协议与 DTO 模块
│   └── src/main/java/org/tianjiserver/tianjicore/common/protocol/
│       ├── ControlProtocol.java                            # 端点路径、安全随机数 Token 生成与常量时间鉴权
│       ├── ModuleInfoDto.java                              # 模块状态快照 DTO
│       ├── StatusResponse.java                             # status 响应 DTO
│       ├── ToggleRequest.java                              # toggle 请求 DTO
│       ├── ToggleResponse.java                             # toggle 响应 DTO
│       ├── ToggleStatus.java                               # toggle 状态枚举
│       ├── ReloadRequest.java                              # reload 请求 DTO
│       ├── ReloadResponse.java                             # reload 响应 DTO
│       ├── ReloadFailureDto.java                           # reload 失败项 DTO
│       ├── ReloadStatus.java                               # reload 状态枚举
│       └── ErrorResponse.java                              # 通用 HTTP 错误响应 DTO
├── paper/
│   ├── pom.xml                                             # Paper 端插件模块（产出 TianjiCore-<version>.jar）
│   └── src/main/
│       ├── resources/
│       │   ├── config.yml                                  # 默认配置文件（含 control-server 配置）
│       │   └── plugin.yml                                  # Paper 插件清单（仅声明 /tianji-fumo 命令）
│       └── java/org/tianjiserver/tianjicore/
│           ├── TianjiCore.java                             # Paper 主入口（JavaPlugin）
│           ├── TianjiCoreModuleHelper.java                 # 模块管理对外封装
│           ├── TianjiCoreModuleManager.java                # 模块注册、启停、重载与配置持久化
│           ├── LifecycleModule.java                        # 可选生命周期清理接口（shutdown）
│           ├── control/
│           │   └── ControlHttpServer.java                  # 内置 HTTP 控制服务端与主线程桥接
│           ├── feature/
│           │   ├── FirstJoinMessage.java                   # 首次进服欢迎消息模块
│           │   ├── PhantomSpawnBlocker.java                # 阻止幻翼生成模块
│           │   └── fumo/
│           │       ├── FumoModule.java                     # 玩偶模块入口与事件路由
│           │       ├── FumoCommand.java                    # /tianji-fumo (/fumo) 命令处理器
│           │       ├── FumoCraftGui.java                   # 管理员玩偶编辑台会话管理与事件分发
│           │       ├── FumoGuiHolder.java                  # 编辑台单玩家会话状态与 InventoryHolder
│           │       ├── FumoGuiLayout.java                  # 6×9（54 格）十字架装备槽与右侧面板布局定义
│           │       ├── FumoGuiSlots.java                   # 十字架槽位占位符、合法性校验与 Shift 智能填入
│           │       ├── FumoGuiRenderer.java                # 编辑台背景、分割线与右侧控制面板渲染
│           │       ├── FumoGuiInteraction.java             # 槽位放置/拖拽交互与快捷操作处理
│           │       ├── FumoGuiItems.java                   # 编辑台物品构建、文本与音效反馈工具
│           │       ├── FumoItemCodec.java                  # 玩偶物品/实体 PDC 编解码器
│           │       ├── FumoPose.java                       # 固定坐姿与放置随机微调
│           │       ├── FumoTrackerManager.java             # 隐形重力追踪与落点同步管理器
│           │       └── FumoEntityListener.java             # 放置、回收与实体保护逻辑
│           └── fixer/
│               ├── EndermanBlockMoveBlocker.java           # 禁止末影人搬动方块模块
│               └── RecipeBugFix.java                       # 配方缓存与登录同步修复模块
└── velocity/
    ├── pom.xml                                             # Velocity 端插件模块（产出 TianjiCore-Velocity-<version>.jar）
    └── src/main/
        ├── resources/
        │   └── config.yml                                  # Velocity 端默认子服节点与超时配置
        └── java/org/tianjiserver/tianjicore/velocity/
            ├── TianjiCoreVelocity.java                     # Velocity 主入口（@Plugin）
            ├── VelocityConfig.java                         # YAML 配置解析、校验与不可变快照管理
            ├── ControlHttpClient.java                      # 基于 HttpClient 的并行异步控制客户端
            └── TianjiCoreCommand.java                      # /tianjicore (/tc) 代理端管理命令处理器
```

---

## 核心机制

### 1. 模块注册与生命周期（Paper 端）

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

### 2. 轻量 HTTP 远程控制通道（`common` + `paper` + `velocity`）

- **零额外依赖传输**：
  - Paper 端 `ControlHttpServer` 基于 JDK 25 内置 `com.sun.net.httpserver.HttpServer` 与虚拟线程执行器（`Executors.newVirtualThreadPerTaskExecutor()`），监听 `GET /api/v1/status`、`POST /api/v1/toggle`、`POST /api/v1/reload`。
  - Velocity 端 `ControlHttpClient` 基于 JDK 25 内置 `java.net.http.HttpClient#sendAsync` 并行向多个子服发起请求并聚合结果，不阻塞代理主线程或命令线程，且无需目标子服有玩家在线即可由控制台触发管理操作。
  - 报文使用 Paper 与 Velocity 运行时自带的 Gson 编解码，协议 DTO 统一沉淀在 `common` 模块。
- **鉴权与线程安全**：
  - 所有请求须携带 `Authorization: Bearer <token>`，Paper 端使用 `MessageDigest.isEqual` 常量时间比对；若首次启动 `control-server.token` 为空则自动生成高熵随机密钥并写回 `config.yml`。
  - Paper 端收到控制请求后，通过 `Bukkit.getScheduler().callSyncMethod` 调度到 Bukkit 主线程执行模块操作，保障事件注册与实体/GUI 清理的线程安全。
  - 若远程 `reload`（模块或 `plugin`）修改了 `control-server` 自身配置：仅 `token` 变更时热更新鉴权配置，无需重启端口；`port` 变更时先预绑定并启动新监听（失败则保留原监听并在响应中上报失败），再在响应写回后关闭旧监听；`enabled=false` 时在响应写回后停用监听；仅 `host` 变更时在响应写回后于虚拟线程中切换监听并支持失败回滚。
  - Velocity 端执行 `/tc reload plugin` 时若检测到子服配置中的 `token` 发生变化，在遇到 `401` 时会自动使用旧快照中的原密钥重试一次，支持两端同步密钥轮换。

### 3. 命令框架集成与命名规范

- **Velocity 端（`VelocityLamp`）**：
  - 使用 `lamp.common`、`lamp.brigadier` 与 `lamp.velocity`（`4.0.0-rc.17`）注册全局管理命令 `/tianjicore`（缩写别名 `/tc`，不注册 `/tianji`）。
  - 命令参数支持动态 Tab 补全：`[server]` 补全 `all` 与已配置子服名；`<module>` / `<target>` 补全模块键及 `plugin` / `proxy`。
- **Paper 端（`BukkitLamp`）**：
  - 已完全移除 `/tianjicore`（`/tc`）命令注册与 `plugin.yml` 声明，避免在代理网络中产生命令冲突，仅保留依赖服务端 Inventory GUI 的 `/tianji-fumo`（别名 `/fumo`）。
  - `FumoCommand` 通过 `Supplier<Optional<FumoModule>>` 按需获取当前已启用的 `fumo` 模块实例，兼容运行时的远程 `toggle` 与 `reload`。
  - **命令命名规范**：模块独立命令的主命令名**必须**以 `tianji-<module>` 格式注册，仅当命令特征极强、几乎不可能与其他插件冲突时才允许额外附带无前缀短别名。

### 4. 构建与依赖策略

- **目标平台**：Java 25 + Paper API `26.2.build.60-beta`（`provided`）+ Velocity API `3.5.1`（`provided`）。
- **Shade 重定位**：
  - `paper` 模块将 `revxrsal` 重定位至 `relocate.TianjiCore.revxrsal`。
  - `velocity` 模块将 `revxrsal` 重定位至 `relocate.TianjiCoreVelocity.revxrsal`，并通过 `maven-compiler-plugin` 的 `annotationProcessorPaths` 生成 `velocity-plugin.json`。
- **无测试约束**：根据 [AGENTS.md](../AGENTS.md) 规范，仓库不保留测试目录与测试依赖，构建通过根目录下 `mvn clean package` 完成全模块编译与打包。
