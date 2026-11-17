# TianjiCore

天际服使用的 Paper 多模块功能插件，提供首次进服欢迎消息、配方解锁同步、生物行为限制以及自定义玩偶摆件功能。

## 目录

- [运行环境与安装](#运行环境与安装)
- [功能模块](#功能模块)
- [命令与权限](#命令与权限)
- [配置文件](#配置文件)
- [构建与验证](#构建与验证)
- [更多文档](#更多文档)
- [开源协议](#开源协议)

## 运行环境与安装

- **构建版本**：`1.2.1`
- **运行要求**：Java 25、Paper `26.2`（基于 Paper API `26.2.build.60-beta` 编译）
- **可选依赖**：`Vault`（`softdepend`，当前全部五个模块均无需安装 Vault）

安装步骤：

1. 获取或构建产物 `target/TianjiCore-1.2.1.jar`。
2. 将 Jar 放入服务端 `plugins/` 目录并启动服务器。
3. 首次启动会自动生成 `plugins/TianjiCore/config.yml`，所有已注册模块默认开启。

## 功能模块

| 模块键 | 名称 | 功能概述 | 命令别名 | 支持 `toggle` |
| --- | --- | --- | --- | --- |
| `firstjoinmessage` | 首次进服消息 | 新玩家首次进服延迟 60 tick 发送 MiniMessage 欢迎消息 | `welcome`、`firstjoin` | 是 |
| `recipebugfix` | 配方修复 | 玩家进服后下一 tick 同步解锁缓存中的全部命名空间配方 | `recipe`、`recipes` | 否 |
| `phantomspawnblocker` | 阻止幻翼生成 | 拦截所有来源的幻翼生成事件（自然、命令、刷怪蛋等） | `phantom`、`phantomblocker` | 是 |
| `endermanmushroombugfix` | 禁止末影人搬动方块 | 禁止末影人在任意世界拾取或放置任何方块（保留旧配置键以兼容） | `enderman`、`endermanblockmoveblocker`、`mushroomfix` | 否 |
| `fumo` | 玩偶 | 管理员通过 GUI 制作固定坐姿的 6 槽装备玩偶摆件，支持自由摆放与蹲下回收 | `doll`、`plush`、`plushie` | 是 |

各模块的详细机制、边缘规则与实现细节请参阅 [模块详细说明](docs/modules.md)。

## 命令与权限

### 插件管理命令

主命令为 `/tianjicore`，别名 `/tianji`、`/tc`。`<module>` 参数支持模块键或上表中的任意别名（不区分大小写），支持控制台与玩家执行。

| 命令 | 说明 | 权限 | 默认权限 |
| --- | --- | --- | --- |
| `/tianjicore help` | 查看命令帮助与可用模块参数 | 无额外权限 | 所有人 |
| `/tianjicore status` | 查看全部模块的实际运行状态 | `tianjicore.command.admin` | OP |
| `/tianjicore toggle <module>` | 切换支持 `toggle` 的模块并写回 `config.yml` | `tianjicore.command.admin` | OP |
| `/tianjicore reload <module>` | 重新读取 `config.yml` 并按配置启停/重启指定模块 | `tianjicore.command.admin` | OP |
| `/tianjicore reload plugin` | 重新读取 `config.yml` 并按配置刷新全部模块 | `tianjicore.command.admin` | OP |

- 整体重载除 `plugin` 外，还接受 `core`、`all`、`tianjicore`。
- 重载中单个模块失败不会中断其余模块，也不会整体回滚；配置文件存在 YAML 语法错误时，会报错并保留重载前的运行状态。
- `/tianjicore status` 反映模块的实际运行状态（启动失败的模块即使配置为 `enabled: true` 也会显示关闭）。

### 玩偶命令

| 命令 | 说明 | 权限 | 默认权限 |
| --- | --- | --- | --- |
| `/fumo` / `/fumo help` | 查看玩偶命令帮助 | `tianjicore.fumo.admin` | OP |
| `/fumo craft [名称]` | 打开 6 槽玩偶制作台 GUI（仅玩家，名称支持 MiniMessage） | `tianjicore.fumo.admin` | OP |

> 制作玩偶需要 `fumo` 模块处于开启状态；已制作出来的玩偶可由任何玩家在世界中右键放置、潜行左键回收。

## 配置文件

默认 `src/main/resources/config.yml`：

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
```

- `first-join-message.message` 支持 MiniMessage 格式（如 `<gold>`、`<bold>`），修改后可执行 `/tc reload firstjoinmessage` 或 `/tc reload plugin` 生效。
- 不支持 `toggle` 的模块（`recipebugfix`、`endermanmushroombugfix`）可通过修改配置文件中的 `enabled` 字段后执行 `/tc reload <module>` 来启停。

## 构建与验证

使用 JDK 25 与 Maven 执行编译打包：

```sh
mvn clean package
```

- 构建产物：`target/TianjiCore-1.2.1.jar`
- 依赖打包：Lamp 命令框架（`lamp.common` / `lamp.bukkit` `4.0.0-rc.17`）会打包并重定位至 `relocate.TianjiCore.revxrsal`；Paper API 与 Vault API 为 `provided` 范围，不打入产物。
- 本项目不设测试代码（见 [AGENTS.md](AGENTS.md)），修改后通过编译打包及服务端实际运行 `/tianjicore status` 与日志检查验证。

## 更多文档

- [模块详细说明](docs/modules.md) — 全部五个模块的触发时机、配置说明与玩偶完整玩法及保护机制
- [代码结构与开发说明](docs/architecture.md) — 项目目录结构、模块生命周期管理与构建约定
- [AI 协作规范](AGENTS.md) — 仓库内代码修改、构建与 Git 提交规范

## 开源协议

本项目基于 [MIT License](LICENSE) 开源。
