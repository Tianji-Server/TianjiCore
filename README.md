# TianjiCore

天际服使用的多端功能插件套件（Paper 功能插件 + Velocity 代理端统一管理插件），提供首次进服欢迎消息、配方解锁同步、生物行为限制以及自定义玩偶摆件功能，并通过内置轻量 HTTP 控制通道由 Velocity 端统一管理多台 Paper 子服。

## 目录

- [运行环境与安装](#运行环境与安装)
- [功能模块](#功能模块)
- [命令与权限](#命令与权限)
- [配置文件](#配置文件)
- [构建与验证](#构建与验证)
- [更多文档](#更多文档)
- [开源协议](#开源协议)

## 运行环境与安装

- **构建版本**：`1.0.4`
- **运行要求**：Java 25、Paper `26.2`（基于 Paper API `26.2.build.60-beta` 编译）、Velocity `3.5.x`（基于 Velocity API `3.5.1` 编译）

安装与配对步骤：

1. 获取或构建产物：
   - Paper 端：`paper/target/TianjiCore-1.0.4.jar`
   - Velocity 端：`velocity/target/TianjiCore-Velocity-1.0.4.jar`
2. 将 `TianjiCore-1.0.4.jar` 放入各后端 Paper 服务器的 `plugins/` 目录并启动。
   - 首次启动会自动生成 `plugins/TianjiCore/config.yml`，并在 `control-server.token` 为空时自动生成随机控制密钥。
3. 将 `TianjiCore-Velocity-1.0.4.jar` 放入 Velocity 代理端的 `plugins/` 目录并启动。
   - 首次启动会生成 `plugins/tianjicore-velocity/config.yml`。
4. 在 Velocity 端 `plugins/tianjicore-velocity/config.yml` 中配置各 Paper 子服的 `url` 与对应 `token`（或 `default-token`），执行 `/tc reload proxy` 即可生效。

## 功能模块

功能模块运行在后端 Paper 服务器上：

| 模块键 | 名称 | 功能概述 | 命令别名 | 支持 `toggle` |
| --- | --- | --- | --- | --- |
| `firstjoinmessage` | 首次进服消息 | 新玩家首次进服延迟 60 tick 发送 MiniMessage 欢迎消息 | `welcome`、`firstjoin` | 是 |
| `recipebugfix` | 配方修复 | 玩家进服后下一 tick 同步解锁缓存中的全部命名空间配方 | `recipe`、`recipes` | 否 |
| `phantomspawnblocker` | 阻止幻翼生成 | 拦截所有来源的幻翼生成事件（自然、命令、刷怪蛋等） | `phantom`、`phantomblocker` | 是 |
| `endermanmushroombugfix` | 禁止末影人搬动方块 | 禁止末影人在任意世界拾取或放置任何方块（保留旧配置键以兼容） | `enderman`、`endermanblockmoveblocker`、`mushroomfix` | 否 |
| `fumo` | 玩偶 | 管理员通过 GUI 制作固定坐姿的 6 槽装备玩偶摆件，支持自由摆放与潜行左键回收 | `doll`、`plush`、`plushie` | 是 |

各模块的详细机制、边缘规则与实现细节请参阅 [模块详细说明](docs/modules.md)。

## 命令与权限

### 插件管理命令（Velocity 端）

`/tianjicore`（别名 `/tc`）已迁移至 Velocity 端统一管理。`<module>` 参数支持模块键或上表中的任意别名（不区分大小写）；可选 `[server]` 参数省略时默认作用于全部已配置子服（`all`），支持在代理控制台与游戏内执行。

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/tianjicore help` | 查看命令帮助、受管子服与可用模块参数 | 无额外权限 |
| `/tianjicore status [server]` | 查看全部或指定子服模块的实际运行状态 | `tianjicore.command.admin` |
| `/tianjicore toggle <module> [server]` | 切换全部或指定子服的可开关模块并写回对应子服 `config.yml` | `tianjicore.command.admin` |
| `/tianjicore reload <module> [server]` | 重新读取目标子服 `config.yml` 并刷新指定模块 | `tianjicore.command.admin` |
| `/tianjicore reload plugin [server]` | 重载 Velocity 端配置并刷新全部或指定子服模块 | `tianjicore.command.admin` |
| `/tianjicore reload proxy` | 仅重载 Velocity 端自身的 `config.yml`（子服地址与密钥配置） | `tianjicore.command.admin` |

- 整体重载除 `plugin` 外，还接受 `core`、`all`、`tianjicore`；代理端配置重载接受 `proxy`、`velocity`。
- 即使目标 Paper 子服当前无玩家在线，也可直接从 Velocity 控制台或任意子服管理其模块。
- 若某个子服离线、超时或密钥校验失败，命令会单独标红提示该子服失败原因，不影响其他子服结果汇总。

### 玩偶命令（Paper 端）

主命令为 `/tianji-fumo`，别名 `/fumo`（因涉及打开 Paper 端物品栏 GUI，保留在 Paper 端注册）。

| 命令 | 说明 | 权限 | 默认权限 |
| --- | --- | --- | --- |
| `/tianji-fumo` / `/tianji-fumo help` | 查看玩偶命令帮助 | `tianjicore.fumo.admin` | OP |
| `/tianji-fumo craft [名称]` | 打开 6 槽玩偶编辑台 GUI（仅玩家，名称支持 MiniMessage） | `tianjicore.fumo.admin` | OP |

> 制作玩偶需要所在子服的 `fumo` 模块处于开启状态；已制作出来的玩偶可由任何玩家在世界中右键放置（潜行放置可对齐 45° 朝向）、潜行左键回收。

## 配置文件

### Paper 端 `paper/src/main/resources/config.yml`

```yaml
modules:
  firstjoinmessage:
    enabled: true
  recipebugfix:
    enabled: true
  phantomspawnblocker:
    enabled: true
  # 禁止末影人搬动所有方块，保留旧配置键以兼容已有配置。
  endermanmushroombugfix:
    enabled: true
  fumo:
    enabled: true

first-join-message:
  message: "<gold>欢迎首次加入服务器！"

control-server:
  enabled: true
  host: "127.0.0.1"
  port: 28765
  # 首次启动若为空将自动生成随机密钥；请将该密钥同步配置到 Velocity 端 plugins/tianjicore-velocity/config.yml。
  token: ""
```

- `first-join-message.message` 支持 MiniMessage 格式（如 `<gold>`、`<bold>`），修改后可执行 `/tc reload firstjoinmessage [server]` 或 `/tc reload plugin` 生效。
- 不支持 `toggle` 的模块（`recipebugfix`、`endermanmushroombugfix`）可通过修改配置文件中的 `enabled` 字段后执行 `/tc reload <module> [server]` 启停。

### Velocity 端 `velocity/src/main/resources/config.yml`

```yaml
# Velocity 端 HTTP 请求超时时间（毫秒）
request-timeout-ms: 5000

# 全局默认控制密钥（当单个子服未单独配置 token 时使用）
default-token: ""

# 受管的后端 Paper 服务器列表（请填写对应子服的 Paper control-server 地址与密钥）
servers:
  lobby:
    url: "http://127.0.0.1:28765"
    token: ""
```

## 构建与验证

使用 JDK 25 与 Maven 在仓库根目录执行多模块编译打包：

```sh
mvn clean package
```

- 构建产物：
  - Paper 插件：`paper/target/TianjiCore-1.0.4.jar`
  - Velocity 插件：`velocity/target/TianjiCore-Velocity-1.0.4.jar`
- 依赖打包：
  - Paper 端将 Lamp（`lamp.common` / `lamp.bukkit` `4.0.0-rc.17`）与 `common` 模块打包，并重定位至 `relocate.TianjiCore.revxrsal`；Paper API 为 `provided` 范围。
  - Velocity 端将 Lamp（`lamp.common` / `lamp.brigadier` / `lamp.velocity` `4.0.0-rc.17`）与 `common` 模块打包，并重定位至 `relocate.TianjiCoreVelocity.revxrsal`；Velocity API 为 `provided` 范围。
  - HTTP 控制通道完全基于 JDK 25 内置 `com.sun.net.httpserver.HttpServer` 与 `java.net.http.HttpClient`，不引入额外网络库。
- 本项目不设测试代码（见 [AGENTS.md](AGENTS.md)），修改后通过编译打包及服务端实际运行 `/tianjicore status` 与日志检查验证。

## 更多文档

- [模块详细说明](docs/modules.md) — 全部五个模块的触发时机、配置说明与玩偶完整玩法及保护机制
- [代码结构与开发说明](docs/architecture.md) — 多模块目录结构、HTTP 控制协议、模块生命周期管理与构建约定
- [AI 协作规范](AGENTS.md) — 仓库内代码修改、构建与 Git 提交规范

## 开源协议

本项目基于 [MIT License](LICENSE) 开源。
