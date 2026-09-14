# TianjiCore

天际服使用的 Paper 插件，提供首次进服欢迎消息、配方解锁同步和生物行为限制。

## 运行与安装

- 当前构建目标：Java 25、Paper API `26.2.build.60-beta`，插件声明的 API 版本为 `26.2`。
- 将构建得到的 `TianjiCore-1.2.jar` 放入服务器的 `plugins` 目录，然后启动服务器。
- 首次启动会生成 `plugins/TianjiCore/config.yml`，所有模块默认开启。
- Vault 是可选依赖，当前四个功能模块不需要安装 Vault。

## 功能模块

| 模块键 | 功能 | 命令别名 | 支持 toggle |
| --- | --- | --- | --- |
| `firstjoinmessage` | 新玩家首次加入后延迟 60 tick 发送欢迎消息，发送时检查玩家是否在线 | `welcome`、`firstjoin` | 是 |
| `recipebugfix` | 玩家加入后下一 tick 尝试解锁缓存中的全部配方 | `recipe`、`recipes` | 否 |
| `phantomspawnblocker` | 拦截幻翼生成，包括命令、刷怪蛋等生成原因 | `phantom`、`phantomblocker` | 是 |
| `endermanmushroombugfix` | 禁止末影人在任意世界拾取或放置任何方块 | `enderman`、`endermanblockmoveblocker`、`mushroomfix` | 否 |

末影人模块保留旧模块键 `endermanmushroombugfix` 以兼容已有配置，限制范围已覆盖所有方块。

配方模块在创建实例和收到服务端加载事件时刷新缓存。其他插件在运行中增删配方后，可执行 `/tianjicore reload recipe` 刷新；刷新后在玩家下次加入时同步，不会立即同步给全部在线玩家。

## 命令与权限

主命令为 `/tianjicore`，也可使用 `/tianji` 或 `/tc`。模块参数接受模块键或上表中的别名，不区分大小写。

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/tianjicore help` | 查看命令帮助和模块参数 | 无额外权限 |
| `/tianjicore status` | 列出全部模块的实际开启或关闭状态 | `tianjicore.command.admin` |
| `/tianjicore toggle <module>` | 切换支持 toggle 的模块，并保存到配置文件 | `tianjicore.command.admin` |
| `/tianjicore reload <module>` | 重新读取配置，并按配置启停或重启指定模块 | `tianjicore.command.admin` |
| `/tianjicore reload plugin` | 重新读取配置，并按配置刷新全部模块 | `tianjicore.command.admin` |

管理权限默认授予 OP，命令也可在控制台执行。整体重载参数还接受 `core`、`all`、`tianjicore`。

重载全部成功时保留正常成功提示；失败时只报告失败目标和原因，完整异常记录在服务端日志中。整体重载中某个模块失败后，仍会继续处理其他模块；操作不会整体回滚。配置中的关闭状态成功生效也视为重载成功。`status` 显示实际运行状态，因此启动失败的模块会显示关闭，即使配置仍为 `enabled: true`。

配置文件存在 YAML 语法错误或读取失败时，重载会报告错误，并保留本次重载前的模块状态。

## 配置

默认 `config.yml`：

```yaml
modules:
  firstjoinmessage:
    enabled: true
  recipebugfix:
    enabled: true
  phantomspawnblocker:
    enabled: true
  # 保留旧配置键以兼容已有配置。
  endermanmushroombugfix:
    enabled: true

first-join-message:
  message: "<gold>欢迎首次加入服务器！"
```

欢迎消息支持 MiniMessage 格式，例如 `<gold>`、`<bold>`。修改后执行 `/tianjicore reload firstjoinmessage`，也可以使用 `/tianjicore reload plugin` 应用全部模块配置。

不支持 toggle 的模块仍可通过修改其 `enabled` 并重载来启停。例如将 `modules.endermanmushroombugfix.enabled` 设为 `false` 后，执行 `/tianjicore reload enderman`。

## 构建与验证

使用 JDK 25 和 Maven：

```sh
mvn clean package
```

构建产物为 `target/TianjiCore-1.2.jar`，命令框架会打包进插件，Paper API 和 Vault API 不会打包。自动化测试也会在构建时运行；仅运行测试可使用 `mvn test`。

部署后可用 `/tianjicore status` 检查模块状态，并结合服务端日志确认启动或重载结果。
