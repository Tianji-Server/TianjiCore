# 模块详细说明

本文档说明 `TianjiCore` 当前内置的五个功能模块的行为逻辑、边界处理与维护细节。

---

## 1. `firstjoinmessage`（首次进服消息）

- **实现类**：`org.tianjiserver.tianjicore.feature.FirstJoinMessage`
- **命令别名**：`welcome`、`firstjoin`
- **支持 `toggle`**：是
- **配置项**：`first-join-message.message`（MiniMessage 格式字符串）

### 行为细节

1. 监听 `PlayerJoinEvent`（`ignoreCancelled = true`），仅当 `player.hasPlayedBefore()` 为 `false` 时触发。
2. 延迟 **60 tick**（约 3 秒）执行发送任务，发送前校验 `player.isOnline()`，避免玩家中途离线引发异常。
3. 使用 Adventure `MiniMessage` 解析 `first-join-message.message` 并发送给该玩家。

---

## 2. `recipebugfix`（配方修复）

- **实现类**：`org.tianjiserver.tianjicore.fixer.RecipeBugFix`
- **命令别名**：`recipe`、`recipes`
- **支持 `toggle`**：否（需通过修改 `config.yml` 并执行 `/tc reload recipe` 启停）

### 行为细节

1. **配方缓存刷新**：
   - 在模块实例化及监听到 `ServerLoadEvent` 时遍历 `Bukkit.recipeIterator()`。
   - 收集所有实现 `Keyed` 接口的配方的 `NamespacedKey`，存入不可变缓存列表。
2. **玩家加入同步**：
   - 监听 `PlayerJoinEvent`（`ignoreCancelled = true`），调度到**下一 tick**（`runTask`）执行，避免服务端原生登录数据同步覆盖补发结果。
   - 校验玩家在线后调用 `player.discoverRecipes(allRecipeKeys)`，在日志中记录总配方数与本次新解锁数。
3. **动态更新说明**：
   - 其他插件在运行中增删配方后，可执行 `/tc reload recipe` 重建模块并刷新缓存；刷新后会在玩家**下次加入**时同步，不会立即推送给当前在线玩家。

---

## 3. `phantomspawnblocker`（阻止幻翼生成）

- **实现类**：`org.tianjiserver.tianjicore.feature.PhantomSpawnBlocker`
- **命令别名**：`phantom`、`phantomblocker`
- **支持 `toggle`**：是

### 行为细节

- 在 `EventPriority.HIGH` 优先级监听 `CreatureSpawnEvent`（`ignoreCancelled = true`）。
- 只要 `event.getEntityType() == EntityType.PHANTOM`，直接取消生成（覆盖自然生成、命令、刷怪蛋等所有生成原因）。

---

## 4. `endermanmushroombugfix`（禁止末影人搬动方块）

- **实现类**：`org.tianjiserver.tianjicore.fixer.EndermanBlockMoveBlocker`
- **命令别名**：`enderman`、`endermanblockmoveblocker`、`mushroomfix`
- **支持 `toggle`**：否（需通过修改 `config.yml` 并执行 `/tc reload enderman` 启停）

### 行为细节

- 保留旧配置键 `endermanmushroombugfix` 以兼容历史 `config.yml`。
- 在 `EventPriority.HIGH` 优先级监听 `EntityChangeBlockEvent`（`ignoreCancelled = true`）。
- 当事件实体为 `Enderman` 时取消事件，禁止末影人在任意世界拾取或放置任何方块。

---

## 5. `fumo`（玩偶）

- **模块入口**：`org.tianjiserver.tianjicore.feature.fumo.FumoModule`（实现了 `LifecycleModule`）
- **命令别名**：`doll`、`plush`、`plushie`
- **支持 `toggle`**：是
- **权限**：`tianjicore.fumo.admin`（默认 `op`，仅控制制作命令）

### 5.1 管理员制作流程（GUI）

- 执行 `/fumo craft [名称]` 打开 27 格 GUI（标题 `Fumo 玩偶制作台`）：
  - `[名称]` 可选，支持 MiniMessage，默认值为 `<gold>Fumo 玩偶`（禁用斜体）。
  - 模块处于关闭状态时无法打开制作台，命令会提示先执行 `/tc toggle fumo` 开启。
- **6 个装备输入槽**（GUI 格位 `9, 10, 11, 12, 14, 15`）依次对应：
  - 头部（`HEAD`）、胸甲（`CHEST`）、护腿（`LEGS`）、靴子（`FEET`）、主手（`HAND`）、副手（`OFF_HAND`）。
- **防嵌套与防误操作**：
  - 禁止放入收纳袋（`BUNDLE`）、潜影盒（`*SHULKER_BOX`）、带有 `Container` 方块状态的物品（`BlockStateMeta`）以及已制作好的 Fumo 玩偶本身。
  - 禁止在 GUI 打开时通过 Shift 点击或双击收集物品到鼠标等方式越过输入槽校验。
  - 确认制作前要求至少放入 1 件装备，且玩家背包至少有 1 个空位（`firstEmpty() != -1`）。
  - 点击取消按钮、关闭界面、玩家退出服务器，或模块停用/重载（`shutdown()`）时，未完成的槽内物品会自动退还至玩家背包（背包满时自然掉落）。

### 5.2 物品编码与无外部存储设计

- 生成的 Fumo 物品材质为 `Material.ARMOR_STAND`：
  - 6 槽装备通过 `ItemStack.serializeItemsAsBytes` 序列化后写入物品 PDC（`fumo_equipment`）。
  - 物品 Lore 自动列出已配置的各槽位物品名称与数量。
  - 物品名称仅显示在物品栏中，放置到世界后**不显示头顶悬浮名**（`customName(null)`、`setCustomNameVisible(false)`），而是将原始显示名称以 Gson 形式隐式保存在实体 PDC（`fumo_item_name`），以便回收时完整还原。
  - 插件不写入任何外部文件或数据库，也不记录制作者、放置者或归属权。

### 5.3 放置、朝向与坐姿细节

- 玩家手持 Fumo 物品在主手或副手右键点击方块（`Action.RIGHT_CLICK_BLOCK`）时触发放置，并取消原版盔甲架放置行为：
  - 目标方块：若点击的方块本身可穿过（`isPassable()`）则在其位置生成，否则在其所点击面相邻方块生成；目标位置不可穿过时拒绝放置并提示空间不足。
  - 位置与朝向：水平居中于目标方块中心（`+0.5, 0.0, +0.5`），默认面向玩家（`playerYaw + 180°`）；**按住潜行键放置时会将偏航角对齐到 45° 的整数倍**。
  - 消耗规则：非创造模式下消耗 1 个玩偶物品，创造模式下不消耗。
- **固定坐姿与随机微调**（`FumoPose`）：
  - 实体属性：小型盔甲架（`small = true`）、显示双臂（`arms = true`）、无底座（`basePlate = false`）、禁止拾取物品、不随距离自动清除。
  - 基础坐姿（角度制）：头部 `(0, 0, 0)`、躯干 `(0, 0, 0)`、左臂 `(-30, 0, -15)`、右臂 `(-30, 0, 15)`、左腿 `(-85, -30, 0)`、右腿 `(-85, 30, 0)`。
  - 放置瞬间会为头部和四肢各自施加三轴总和为 `2.0°` 的随机偏转（躯干不变），使每个摆件略带自然差异。

### 5.4 落点追踪与物理免疫

- Fumo 实体本身始终处于无物理影响状态（`gravity = false`、`canMove = false`）。
- 放置时会在 `spawnLocation` 额外生成一个临时的非持久化隐形重力盔甲架（`trackerStand`），同时将 Fumo 实体置于其下方 `0.3` 格偏移处（`TRACK_OFFSET_Y = -0.3`）。
- `FumoTrackerManager` 每 tick 执行同步：
  - 将追踪盔甲架限制为纯垂直下落（锁定初始 X/Z 坐标，清除水平速度和向上速度）。
  - 将 Fumo 实体同步至 `trackerLoc + (0, -0.3, 0)`。
  - 当 `tracker.isOnGround()` 为 `true` 时，结束追踪会话并移除临时隐形盔甲架。
- 当区块/实体加载（`EntitiesLoadEvent`）或模块初始化时，会自动确保已加载的 Fumo 保持物理免疫，并清理残留的非活跃追踪盔甲架；模块关闭或重载（`shutdown()`）也会中止全部追踪任务并清除临时实体。

### 5.5 回收与实体保护

- **回收方式**：任何玩家均可**蹲下（潜行）**并对准 Fumo 实体（或其活跃的追踪盔甲架）**左键或右键**进行回收，恢复为原始 Fumo 物品并放入背包（背包满时在实体位置自然掉落）。
- **防破坏保护**：
  - 锁定 Fumo 与追踪盔甲架全部 6 个装备槽的增删改操作。
  - 拦截原版盔甲架装备操作（`PlayerArmorStandManipulateEvent`）、非潜行交互、各类实体伤害（`EntityDamageEvent`）、击退（`EntityKnockbackEvent`）及传送门传送（`EntityPortalEvent`）。
