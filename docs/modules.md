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

- 执行 `/tianji-fumo craft [名称]`（或别名 `/fumo craft [名称]`）打开 54 格（6×9）GUI（标题 `玩偶编辑台`）：
  - `[名称]` 可选，支持 MiniMessage，默认值为 `<gold>玩偶`（禁用斜体）。
  - 模块处于关闭状态时无法打开编辑台，命令会提示先执行 `/tc toggle fumo` 开启。
- **左侧十字架装备区**（带浅灰色玻璃板占位符，由第 6 列 `5, 14, 23, 32, 41, 50` 灰色玻璃板分割线与右侧面板分隔）：
  - 头部（`HEAD`）：格位 `11`（十字架上方）
  - 主手（`HAND`）：格位 `19`（十字架左侧，对应玩偶右手）
  - 胸甲（`CHEST`）：格位 `20`（十字架中央，对应玩偶躯干）
  - 副手（`OFF_HAND`）：格位 `21`（十字架右侧，对应玩偶左手）
  - 护腿（`LEGS`）：格位 `29`（十字架中下）
  - 靴子（`FEET`）：格位 `38`（十字架底部）
- **右侧控制面板与快捷操作**：
  - **玩偶概览**（格位 `15`）：实时展示目标名称、装配进度（`x / 6 槽`）及各槽位当前装备。
  - **二创声明开关**（格位 `16`，默认开启）：点击可切换是否在成品玩偶物品 Lore 最底部附加两行斜体声明：`非官方《东方Project》` / `(上海アリス幻樂団) 二创`。
  - **操作说明**（格位 `17`）：提示十字架槽位对应关系、回收方式及禁止放入的物品类型。
  - **交换主副手**（格位 `24`）：一键互换主手（`19`）与副手（`21`）槽位物品。
  - **导入穿戴护甲**（格位 `25`）：将玩家身上穿戴的头/胸/腿/靴自动移入左侧对应的空护甲槽位（已占用或不可放入的物品跳过）。
  - **清空全部装备**（格位 `26`）：退还左侧已放入的全部装备至玩家背包（背包满时自然掉落）并恢复占位符。
  - **确认制作玩偶**（格位 `42`）：放入至少 1 件装备后高亮可用，点击打包生成玩偶物品。
  - **取消制作**（格位 `44`）：关闭编辑台并退还全部装备。
- **交互校验与防误操作**：
  - 空槽位支持左键/右键放置、数字键（`NUMBER_KEY`）、副手交换键（`SWAP_OFFHAND`）以及真实物品槽位上的拖拽；在背包内 `Shift + 点击` 物品时优先匹配物品自身装备栏，否则按 `主手 → 副手 → 头部 → 胸甲 → 护腿 → 靴子` 顺序自动填入首个空位。
  - 禁止放入收纳袋（`BUNDLE`）、潜影盒（`*SHULKER_BOX`）、带有 `Container` 方块状态的物品（`BlockStateMeta`）、GUI 占位符以及已制作好的玩偶本身。
  - 禁止在 GUI 打开时通过双击收集物品到鼠标（`COLLECT_TO_CURSOR`）或向非十字架槽位拖拽等方式越过输入槽校验。
  - 确认制作前要求至少放入 1 件装备，且玩家背包至少有 1 个空位（`firstEmpty() != -1`）。
  - 点击取消按钮、关闭界面、玩家退出服务器，或模块停用/重载（`shutdown()`）时，未完成的槽内物品会自动退还至玩家背包（背包满时自然掉落）。

### 5.2 物品编码与无外部存储设计

- 生成的玩偶物品材质为 `Material.ARMOR_STAND`：
  - 6 槽装备通过 `ItemStack.serializeItemsAsBytes` 序列化后写入物品 PDC（`fumo_equipment`）。
  - 物品 Lore 展示 `玩偶摆件` 标识、分割线（`────────────────`）、`放置方法`（`• 右键 点击方块即可放置` / `• 潜行 + 右键 对齐 45° 朝向放置`）与 `拆除方法`（`• 潜行 + 左键 点击玩偶即可回收`）；若开启了二创声明开关，会在底部分割线下方附加两行斜体声明，并将状态同步写入物品与实体 PDC（`fumo_derivative_notice`），确保放置回收后仍保留。
  - 物品名称仅显示在物品栏中，放置到世界后**不显示头顶悬浮名**（`customName(null)`、`setCustomNameVisible(false)`），而是将原始显示名称以 Gson 形式隐式保存在实体 PDC（`fumo_item_name`），以便回收时完整还原。
  - 插件不写入任何外部文件或数据库，也不记录制作者、放置者或归属权。

### 5.3 放置、朝向与坐姿细节

- 玩家手持玩偶物品在主手或副手右键点击方块（`Action.RIGHT_CLICK_BLOCK`）时触发放置，并取消原版盔甲架放置行为：
  - 目标方块：若点击的方块本身可穿过（`isPassable()`）则在其位置生成，否则在其所点击面相邻方块生成；目标位置不可穿过时拒绝放置并提示空间不足。
  - 位置与朝向：水平居中于目标方块中心（`+0.5, 0.0, +0.5`），默认面向玩家（`playerYaw + 180°`）；**按住潜行键放置时会将偏航角对齐到 45° 的整数倍**。
  - 生成校验与消耗规则：先生成隐形追踪盔甲架与玩偶盔甲架，若任一实体生成被其他插件或保护规则取消（`!isValid()`），立即清理已生成的关联实体并提示无法放置，**不会扣除玩家物品**；仅当两者均有效生成并开始追踪后，在非创造模式下消耗 1 个玩偶物品（创造模式下不消耗）。
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

- **回收方式**：任何玩家均可**蹲下（潜行）**并对准 Fumo 实体（或其活跃的追踪盔甲架）**左键**进行回收，恢复为原始 Fumo 物品并放入背包（背包满时在实体位置自然掉落）。
- **防破坏保护**：
  - 锁定 Fumo 与追踪盔甲架全部 6 个装备槽的增删改操作。
  - 拦截原版盔甲架装备操作（`PlayerArmorStandManipulateEvent`）、非潜行交互、各类实体伤害（`EntityDamageEvent`）、击退（`EntityKnockbackEvent`）及传送门传送（`EntityPortalEvent`）。
