# MatterReassembler 设计方案（Forge 1.20.1）

> 意图真源。与代码冲突时以本文为准；变更只记 `UPDATE.md`。
> 正文写**功能语义**：效果、实现思路、边界。具体类名/文件路径集中在**附录 A**，正文不散布。
> 外部 API 锚点仅用于**签名核对**，不作为行为证据；标注了「待核」的条目必须在 Wave 0 用产物核对后才可依赖。

---

## 1. 项目定位

AE2 附属方块「物质重组器」。它把 ME 网络的合成请求**旁路**：不执行样板、不经过 crafting CPU 的产物生产流程，改为「扣光材料 + 扣光 FE → 直接投放最终产物」。

一句话语义：**买时间，不买材料**。材料一分不少，样板一刻不跑，电费按产物单位计。

它解决的是「配方树极深、样板极多、CPU 极慢」时的等待成本，而不是绕过配方本身。

### 1.1 概念约定

| 概念 | 语义 |
|------|------|
| 电量池 | 方块实体内部 `long`，只由外部 `IEnergyStorage` 调用或覆盖板注入改变 |
| 开启/关闭 | 关闭 = 拒绝一切能量交互、拒绝一切合成接管；外观变暗 |
| 在线 | 方块实体的 AE 网格节点处于 active（有电、有频道、已连网） |
| 接管 | 用本方块替 ME 网络完成一次合成请求的材料结算与产物投放 |
| 旁路成本 | 按产物单位计费的 FE（见 §6.3） |

---

## 2. 方块与外观

### 2.1 效果

- 默认外观：白色框架 + 中心黑洞材质（自发光/半透明感，纯纹理动画实现，不写自定义渲染器）。
- 方块接入 AE 网络且节点 active：框架变为黑色。
- 关闭（`isEnabled == false`）：外观变暗 + 红色指示灯（用同一模型的不同纹理/自发光层表达）。
- 因此外观有 **2×2 = 4 种组合**（在线与否 × 开启与否），全部由方块状态驱动。

### 2.2 实现

- 属性：两个布尔方块状态属性——`online`（网格 active）、`enabled`（开关）。四种组合映射到四个模型变体（或两个模型 + 两个纹理变体）。
- 黑洞材质用**贴图动画**（`.mcmeta` 帧序列）实现「动态」，渲染层显式声明 `cutout`（半透明像素不被当作实色）。
- `online` 由服务端维护：方块实体自身的 `tick` 中**每 10 刻**比对一次「节点是否 active」与当前属性，不一致才写状态并 `setChanged()`。不做每刻探测、不发自定义包。
- `enabled` 只由 §5 的交互改动。

### 2.3 边界

- 外观纯客户端可见，服务端权威值是 `enabled` 与电量；任何视觉差异不得反过来影响服务端判定。
- `online` 变化引起的模型/光照刷新有 0–10 刻延迟，属既定代价（换取零网络包）。

---

## 3. 配方与物品属性

- 配方：8 个泥土，形状 3+3+2（两整行 + 中间一行居中两格），输出 1 个本方块。
- 物品 tooltip 固定三段：① 空手 Shift+右键顶面开关；② 旁路成本（每单位 FE，读配置）；③ 若已装 GTMThings，追加「使用更高电压等级的无线能源覆盖板可以更快积累电量」。
- 物品与方块同注册名 `matter_reassembler`，稳定不可改。
- 采集：需正确工具（镐），掉落自身，无额外战利品表特化。

---

## 4. 能量系统

### 4.1 效果

- 内部电量池为 `long`，语义上界 `9,223,372,036,854,775,807` FE。
- 对外 `IEnergyStorage`：`getEnergyStored()` / `getMaxEnergyStored()` 返回 `int`，把内部值与前两者钳制到 `2,147,483,647`。
- `receiveEnergy` / `extractEnergy` 内部**全程 `long` 运算**，无中间 `int` 转换，无溢出。
- 机器**从不主动**输入或输出能量：不轮询邻居、不在 tick 里推拉、不主动向网络索取。
- 只有四个侧面参与能量交互；顶面用于开关交互，**不暴露**能量；底面**默认暴露**，可配置关闭。

### 4.2 实现

- `receiveEnergy(maxReceive, simulate)`：`space = Long.MAX_VALUE - energy`；`accepted = min(maxReceive, space)`；`simulate` 时只返回 `accepted`。
- `extractEnergy(maxExtract, simulate)`：`amount = min(maxExtract, energy)`。
- 关闭态：两者**直接返回 0**（连 `simulate` 也返回 0——外部机器据此认为「抽不动」，不会陷入每刻重试取到 0 却以为有电的错觉）。
- 面判定抽成**静态纯函数**：`isEnergyFace(Direction, exposeDown)` → 水平四面 true；`UP` false；`DOWN` 取配置；`null`（内部访问）false。
- 存档：`long` 写入 NBT（`putLong`/`getLong`），`loadTag`/`saveAdditional` 内 `setChanged()` 由写入路径触发。

### 4.3 边界

- 对外可见上限恒为 `2,147,483,647 FE`，即使内部已经存到 20 亿以上；单次 `receiveEnergy` 上限同样是 `int`，因此**注满 20 亿以上需要外部机器多次调用**。这是 Forge 接口的硬约束，不是缺陷，须在 tooltip 与 Jade 中说明。
- 内部池不设自定上限（受 `long` 物理上界约束）；不做「饱和即蒸发」。
- 抽取在关闭态返回 0 —— 停机时能量被锁在方块内，符合「关机不响应能量操作」的语义。

---

## 5. 开关交互

### 5.1 效果

- 顶面 + 空手 + Shift + 右键 → 翻转开关，动作条/聊天栏无提示（靠外观与 Jade 反馈），客户端播放方块交互音。
- 任意其他组合（手持物品 / 未按 Shift / 非顶面）→ 完全交回原版与其它模组，不拦截、不消耗、不翻状态。

### 5.2 实现

- 判定顺序固定为：`player.getMainHandItem().isEmpty()` → `player.isShiftKeyDown()` → 命中面 `== Direction.UP` → 服务端执行。任一条不满足直接返回「不处理」（不是返回成功），保证其它模组的 Shift+右键 交互不被吞。
- 只在服务端翻转（`level.isClientSide` 早退），翻转后写方块状态、`setChanged()`、并让 Jade 的服务端数据在下一帧自然刷新。
- **注释必须写明**：空手判定是为了不与「手持物品的 Shift+右键」冲突；这是本方块唯一的物品交互面豁免点。

### 5.3 边界

- 关闭态下仍可被重新打开（开关不受电量与网络状态限制）。
- 覆盖板可以装在四个侧面；顶面永远不参与覆盖板交互（见 §8.2）。
- 副手持有物品不影响（只看主手），这是刻意的：避免「副手火把 + 空主手」时无法开关。若后续要更严，改判定为「主副手皆空」。

---

## 6. 合成接管（核心）

### 6.1 效果

玩家在 AE2 合成确认界面点「开始」时，若本模组接管：

- 材料按原计划的 `usedItems()` 从 ME 网络**原样扣除**（该计划本来就会优先使用库存中间产物，因此「扣中间产物而不是原料」这一行为由 AE2 计算阶段决定，本模组不改）。
- 不创建 crafting CPU 任务、不执行任何样板、不占用 CPU 存储。
- 产物 `finalOutput()` **直接插入 ME 网络**（与 CPU 完成任务时把产物交回网络的效果一致）。
- 按 §6.3 扣 FE。
- 界面对玩家呈现「请求成功」，物品即刻出现在网络里。

不满足任何一条资格条件 → 完全走 AE2 原版路径（本模组不介入）。

### 6.2 实现

**拦截点（设计裁定 D2）**：注入 AE2 合成服务的 `submitJob` 方法**入口**（可取消），而不是注入 `beginCraftingCalculation` 的返回。理由：

1. 只有「确认提交」这一刻玩家才真的要合成；在计算完成时就扣电，玩家点取消后电已扣（风险 R2）。
2. 计算完成回调运行在计算线程，`submitJob` 在服务端线程——副作用天然在主线程。
3. `beginCraftingCalculation` 的返回值仍需原样给确认界面做**真实材料/字节展示**，不篡改能避免 UI 与真实结算不一致。

**为什么必须在提交时刻自行结算（设计裁定 D1，关键）**：把「`patternTimes` 为空」的计划交回 CPU 不会报错，但会让任务**永久挂起**（证据链见 §7）。因此接管时本模组自己完成「扣料 + 投放产物 + 扣电」，并把原版提交短路为「成功」，**不创建任何 CPU 任务**。

**接管资格（全部为真才接管）**：

| 条件 | 判定来源 | 说明 |
|------|----------|------|
| 主开关开启 | 配置 | 全局停用开关 |
| 请求来自玩家 | `requestingMachine == null` | 机器/自动化请求**不接管**，交回原版（边界 B1） |
| 计划非模拟 | `!plan.simulation()` | 缺料计划不接管 |
| 存在合格方块 | 网格内本方块实体集合 | 至少一台：节点 active + 开启 + 电量 ≥ 总成本 |
| 材料可全部抽取 | 网络库存 `simulate` 抽取逐项通过 | 与 AE2 自身抽取口径一致：任何一项不足即不接管 |
| 产物可全部插入 | 网络库存 `simulate` 插入足量 | 插不进就交回原版，避免「材料扣了产物塞不下」 |

多台合格方块时，**取电量最多的一台**（同等电量取坐标字典序，保证确定性）。

**执行序（严格顺序，任一步失败回滚）**：

1. 主线程：逐项 `simulate` 抽取材料，全通过才继续。
2. 主线程：`simulate` 插入产物，返回值必须等于产物数量。
3. 主线程：`MODULATE` 逐项抽取材料。
4. 主线程：`MODULATE` 插入产物；若实际插入量 < 产物量（极端竞态），把已抽取材料**原样插回网络**，不扣电，返回原版失败结果（交由原版 CPU 路径重试）。
5. 主线程：扣 FE、`setChanged()`。
6. 返回「提交成功、无链接」。

**收支核算必须抽成不依赖 `Level` 的纯类**（抽象出一个最小「库存」接口：`extract(simulate)` / `insert(simulate)` / `rollback`），以便 T1 探针覆盖回滚路径。这是本设计最重要的可验证性安排。

### 6.3 计费口径（设计裁定 D1，2026-11-10 定稿：按次）

- **默认按次计费**：`总成本 = energyPerJob(默认 100,000)`，一次接管一次性扣除，与请求数量无关。
- 这是开发者的明确选择：旁路成本是「开一次旁路」的门票钱，不随产物数量浮动；想按量收费时把 `chargeMode` 改成 `PER_UNIT` 即可（`总成本 = energyPerUnit × 单位数`），不改代码。
- 已记录的取舍：按次计费下，一次请求 1000 个与请求 1 个成本相同，旁路在「大批量请求」上会非常便宜——这是**有意**的（`AGENTS.md` §8 已登记，不视为缺陷）。
- 单位数口径（`PER_UNIT` 时才用到）：可堆叠物品按个数；其它键类型按 `finalOutput().what().getAmountPerUnit()` 归一，非整数向上取整。
- 计数与扣除都在服务端线程、与材料结算同一原子段完成：材料扣不动或产物投不下时**一分钱不扣**。

### 6.4 边界

| 编号 | 边界 |
|------|------|
| B1 | 机器/Crafting Card 等 `ICraftingRequester` 请求**不接管**：它们需要链接与请求清单，替代语义会打破其状态机。 |
| B2 | 材料不足、产物插不下、电量不足 → 一律交回原版，本模组不产生任何「部分完成」。 |
| B3 | 原版确认界面要求「选中的 CPU 存储足够计划字节」，本模组不创建 CPU 任务但仍走该前置：没有可用 CPU 时无法发起合成，因此也无法触发旁路。配置 `relaxCpuByteRequirement`（默认 false）可让拦截层在**计算完成后**把计划的字节数归零，从而让任意小 CPU 通过前置（纯 UI 放宽，不影响结算）。 |
| B4 | 接管成功后 CPU 面板不出现任务——这是刻意语义（没有任务）。玩家在 CPU 界面看不到进度属预期。 |
| B5 | 一次接管 = 一次请求的全部数量；中途取消的概念不存在（结算瞬间完成）。 |
| B6 | 不接管 AE2 的「自动补充/自动合成」在后台发起的请求（同 B1）。 |
| B7 | 计划里的 `emittedItems`（副产物/容器物）**按原样插入网络**，不丢弃——否则容器类配方会吞桶吞瓶。 |
| B8 | 与「子网」「无线终端」无差别：只认发起请求的网格。 |

---

## 7. 「空 `patternTimes` 会被接受吗」——风险裁定（必读）

### 7.1 结论

**不会被拒绝，但会让 CPU 永久挂起。** 任务不崩、不报错、不完成。

### 7.2 证据链（来源：AE2 `forge/1.20.1` 分支源码，2026-11-10 抓取，见附录 A）

1. 提交路径的校验只有三条：已有任务 → CPU_BUSY；节点不 active → CPU_OFFLINE；`availableStorage < plan.bytes()` → CPU_TOO_SMALL。**没有任何针对 `patternTimes` 的校验**。
2. 通过校验后立即 `tryExtractInitialItems(plan, …)`：按 `plan.usedItems()` 逐项从网络抽取到 CPU 内部库存（真实扣料）。因此「交回 CPU」意味着材料会被搬进 CPU。
3. 任务对象构造时：`remainingAmount = finalOutput().amount()`；`waitingFor` 来自 `emittedItems()`；`tasks` 来自 `patternTimes()` 逐项累加。
4. 完成任务**只有一个出口**：产物被插入 CPU（`insert`）时把 `remainingAmount` 减到 0 → `finishJob(true)`。
5. 空 `patternTimes` ⇒ `tasks` 为空 ⇒ 没有样板被执行 ⇒ 没有产物入账 ⇒ `remainingAmount` 永不归零 ⇒ **任务挂起**；材料滞留 CPU 内部库存，直到玩家取消或 CPU 被拆除才回到网络。

### 7.3 方案比较

| 方案 | 做法 | 结论 |
|------|------|------|
| A. 空 `patternTimes` 交回 CPU | 按原提示词 | **否决**：材料进 CPU 后挂死，CPU 被占，玩家必须手动取消。 |
| B. 放一个「永不执行的占位样板」 | `patternTimes` 放虚拟 `IPatternDetails` | **否决**：CPU 会向各 provider 推送该样板；无 provider 承载 → 每次 tick 重试、永不完成，等价于 A 更差（还多出无效推送开销）。 |
| C. Mixin 放宽 CPU 校验并代其完成 | 改 `CraftingCpuLogic` | **否决**：注入面扩大到 tick 主循环与完成判定，AE2 小版本升级即碎；违背「尽量减少 Mixin」。 |
| **D. 不把交付交给 CPU（采纳）** | 在提交入口短路：自行扣料 + 投放产物 + 扣电，返回成功 | 只有一处 HEAD 注入、只依赖 AE2 自有类型的方法描述符；CPU 完全不参与，无挂起、无残留。 |

### 7.4 最小测试方案（Wave 3 的执行依据）

- **T1（隔离，必做）**：把「资格判定 + 收支账本 + 回滚」写成纯类，用探针覆盖：材料足/不足、产物插得下/插不下、第 4 步回滚、电量不足、关闭态。断言逐条 `PASS/FAIL`。
- **T2（无头，必做）**：`runServer` 冒烟——Mixin 成功注入（日志出现 `Mixing … into appeng.me.service.CraftingService`）、AE2 正常加载、无 `ERROR`。此级**不能**证明合成行为。
- **T3（真实客户端，开发者执行，必须登记）**：
  1. 木棍合成（`minecraft:stick` ×4）走旁路：确认界面点开始 → 材料减少、产物立即出现在网络、FE 按单位扣除、CPU 面板无任务、无卡住的 CPU。
  2. 电量不足 → 走原版 CPU，正常出产物。
  3. 关闭开关 → 走原版。
  4. 材料故意缺一项 → 走原版报缺料。
  5. 网络满（塞满存储元件）→ 走原版，不丢材料。
  6. 同一请求连续点两次 → 第二次不得重复扣电（材料已不在，原版报错）。

---

## 8. GTMThings 无线能源覆盖板兼容（软依赖）

### 8.1 效果

- 四个侧面装了 GTMThings 的无线能源接收器覆盖板时，覆盖板送来的能量**直接进入内部 `long` 池**，不经过 `IEnergyStorage.receiveEnergy`。
- 覆盖板受自身电压等级与速率限制（当前等级 1A），Jade/tooltip 提示「更高电压等级的覆盖板充得更快」。

### 8.2 实现

- **类加载隔离**：兼容类只在 `ModList.get().isLoaded("gtmthings")` 为真时被引用；主类常量池不出现该类名（否则未装时 `NoClassDefFoundError`）。
- **只扫水平四面的覆盖板**；顶面永远不参与（顶面是开关）。底面按 §4 的配置语义：若底面暴露能量，则允许覆盖板安装与注入；若关闭底面能量接口，底面覆盖板仍可安装但注入被忽略（并在 Jade 提示「底面能量接口已关闭」）。**待开发时确认**：GTMThings 是否允许在非能量面安装覆盖板；不允许则本边界自动消失。
- **两层探测**：
  1. 优先「被动推送」：若覆盖板自身会向宿主推送/查询接口，本方只需实现该接口方法把值累加进池。
  2. 退路「主动轮询」：每 `pollCoverInterval`（默认 20）刻向覆盖板询问「可交付能量」并取走。
- 第 2 层是对**覆盖板**的拉取，**不违反** §4「机器不主动输入能量」的约束——该约束针对 Forge FE Capability 的对等读写；覆盖板是挂在本方块上的本地设备，语义等价于「自己的部件」。此裁定必须写进代码注释，避免后来者误判为违规。
- 反射失败（类名/字段/方法名变动）→ 每次启动**只 warn 一次**并列出探测到的候选类名，功能降级为「标准 FE 通路」，不抛异常、不阻断加载。
- **能量守恒**：注入前先 `simulate` 式读取覆盖板可交付量，注入后再从覆盖板扣除；只做一次单向搬运，不做「读一次扣两次」。

### 8.3 边界

- 覆盖板注入不受 `isEnabled` 影响？**受影响**：关闭态一律不接收（与本方块其它能量入口一致），避免「关机还在偷偷充电」。
- 覆盖板注入不设 `int` 钳制（内部就是 `long`），因此**覆盖板是唯一能一次性把电量推过 21 亿的通路**。这是设计意图，须在 Jade 与文档中体现。
- GTMThings 的 API 目前无公开资料：Wave 4 交付的是「反射探测 + 占位注入点 + 清晰日志」，具体类名由开发者在实机环境补齐。**不得**在文档或注释中虚构 API 名。

---

## 9. Jade 信息显示

### 9.1 效果

准星指向方块时显示四项：

| 字段 | 文案（中文） | 取值 |
|------|--------------|------|
| 网络 | `已接入 ME 网络` / `未接入` | 节点 active |
| 状态 | `运行中` / `已关闭` | `enabled` |
| 电量 | `9,223,372,036,854,775,807 FE` | 内部 `long`，千位分隔 |
| 吞吐 | `2,147,483,647 FE/t` | 接口单次上限常量 |

### 9.2 实现

- 插件用 Jade 的注解式注册：一个实现插件接口的类 + 注解声明；在插件注册回调里做两次登记——**服务端数据提供者**（键为方块实体类）与**客户端组件提供者**（键为方块类）。
- 服务端数据里放 `energy`(long)、`enabled`(bool)、`online`(bool)、`throughput`(int)。**不复用 `getUpdateTag`**：`getUpdateTag` 会把 8 字节 long 塞进每个玩家的块实体全量包，而 Jade 的数据通道只在准星指向时按需发送。
- 客户端只做「读数据 → 格式化 → 追加行」，不做任何计算或网络请求。
- 数字格式化抽成静态纯函数（`formatFe(long)`：`%,d` + 单位后缀，固定 `Locale.ROOT` 以免某些语言环境下分隔符变空格），供 Jade 与 tooltip、聊天提示共用。
- 未装 Jade：插件类不被加载即可（不在主类里 `new` 它、不 import 到公共路径），其余功能正常。

### 9.3 边界

- Jade 显示的电量是「准星指向那一刻」的服务端快照，最多滞后一个客户端 tick。
- 关闭态仍显示电量（玩家需要知道停机时池里还有多少）。
- 若某次服务端数据缺失（例如方块实体刚被移除），客户端显示 `-` 而不是 `0`，避免误读。

---

## 10. 配置项（`ForgeConfigSpec`）

| 键 | 类型 | 默认 | 语义 |
|----|------|------|------|
| `takeoverEnabled` | bool | true | 合成接管总开关（关闭后本模组只当储电方块） |
| `energyPerJob` | long | 100000 | 每次接管 FE 成本（`PER_JOB` 生效） |
| `energyPerUnit` | long | 100000 | 每产物单位 FE 成本（`PER_UNIT` 生效） |
| `chargeMode` | enum | `PER_JOB` | `PER_JOB`（默认，按次）/ `PER_UNIT`（按量） |
| `exposeDownFace` | bool | true | 底面是否暴露能量接口 |
| `pollCoverInterval` | int | 20 | GTMThings 覆盖板轮询间隔（刻），`0` = 只用被动推送 |
| `relaxCpuByteRequirement` | bool | false | 计算完成后把计划字节归零，放宽确认界面 CPU 存储前置（纯 UI） |
| `debugLogging` | bool | false | 接管判定的细节日志（debug 级） |

配置键名一旦发布不得重命名（会静默重置玩家存档配置）。

---

## 11. 可验证性设计（设计门禁）

| # | 核心断言 | 级别 | 落地形态 |
|---|----------|------|----------|
| 1 | `long` 池加法不溢出（`receive` 到 `Long.MAX_VALUE` 后仍返回 0） | **T1** | 静态纯函数/纯类 |
| 2 | `getEnergyStored`/`getMaxEnergyStored` 钳制到 `Integer.MAX_VALUE` | **T1** | 纯函数 |
| 3 | 能量面白名单（UP 否、水平是、DOWN 依配置、null 否） | **T1** | 静态纯函数 |
| 4 | 关闭态所有能量操作返回 0（含 simulate） | **T1** | 纯函数 |
| 5 | 接管资格判定（开关/模拟/敌方请求/电量/材料/产物） | **T1** | 静态纯函数（输入为布尔与数值） |
| 6 | 收支账本：正常路径、材料不足、产物插不下、第 4 步回滚 | **T1** | 纯类 + 抽象库存接口 |
| 7 | 计费口径（PER_UNIT / PER_JOB，含向上取整） | **T1** | 静态纯函数 |
| 8 | FE 千位格式化（含 0、`Long.MAX_VALUE`、负数拒绝） | **T1** | 静态纯函数 |
| 9 | 覆盖板注入合并（多次注入累加、饱和不溢出） | **T1** | 纯函数 |
| 10 | 方块/物品/方块实体注册与创造栏出现 | **T2** | 无头冒烟 |
| 11 | Mixin 注入成功、AE2 正常加载 | **T2** | 无头冒烟（强制门） |
| 12 | 配方 `data/matterreassembler/recipes/` 进入产物 | **T0** | `unzip -l` |
| 13 | 实际合成 8 泥土出本方块 | **T3** | 开发者 |
| 14 | 顶面空手 Shift+右键开/关，外观与 Jade 同步变化 | **T3** | 开发者 |
| 15 | 手持物品时 Shift+右键不被本模组吞掉 | **T3** | 开发者（含第三方模组物品） |
| 16 | 旁路端到端（扣料/产物/扣电/无 CPU 任务） | **T3** | 开发者 |
| 17 | GTMThings 覆盖板实际充能速率 | **T3** | 开发者 |

T3 占比 5/17 ≈ 29%，未过半，设计门禁通过。**改动任何一条 T1 断言所属逻辑时，必须同步本表**。

---

## 12. 非目标

- 不做 EU/AE 的直接实现（转换由 GTCEu 与 AE2 能源接收器负责）。
- 不做自定义 GUI（本方块无界面）。
- 不做「按配方树深度分级计费」。
- 不做跨版本（NeoForge / 1.21）兼容。
- 不接管自动化请求（B1）。
- 不改 AE2 的样板、provider、CPU 任何内部逻辑。

---

## 13. 有意设计

| 现象 | 裁定 |
|------|------|
| 旁路后 CPU 面板无任务 | 有意：语义上就是「没有任务」 |
| 单次能量交互上限 21 亿（需多次调用） | 接口硬约束，非缺陷 |
| 覆盖板是唯一能一次推过 21 亿的通路 | 有意 |
| 停机时能量被锁在方块内 | 有意（关机不响应能量操作） |
| 无可用 CPU 时无法发起旁路 | 有意（保持原版确认前置）；可用配置放宽 |
| 一次接管瞬时完成、无取消 | 有意 |

---

## 14. 实施路线（分波，每波可独立验收）

> 粒度：每波一个可交付单元；**每波结束必须能跑出本节列出的验证命令**。
> Wave 0 未完成前，任何人不得宣称「构建通过 / 可交付」（见 AGENTS §0.1）。

### Wave 0 · 基建与签名核对（不写业务逻辑）

> **完成情况（2026-11-10）**：1–6 全部落地。依赖实测可用——`compileClasspath` 解析出
> `appeng:appliedenergistics2-forge:15.4.10_mapped_official_1.20.1` 与
> `maven.modrinth:jade:11.13.3+forge_mapped_official_1.20.1`；GTMThings **未引入坐标**（反射方案，见 §8.2）；
> 附录 A 锚点已用 `javap` 对实际产物核对（B-1/B-2/B-3 销号）。

1. 改 `gradle.properties`：`mod_id=matterreassembler`、`mod_name=Matter Reassembler`、`mod_group_id=com.wenxing.matterreassembler`、版本字段。
2. 接入依赖：AE2 15.4.x（硬）、Jade（软，`compileOnly` + `runtimeOnly`）、GTMThings（软，`compileOnly` + `runtimeOnly`）。坐标与可达性见附录 B-2。
3. 包名迁移 `com.example.examplemod` → `com.wenxing.matterreassembler`，删模板示例注册项（保留 `Config` 骨架）。
4. `mods.toml`：写死 `modId`、声明 AE2 硬依赖与两个软依赖（`mandatory=false`）。
5. **签名核对**：对 AE2 产物 `javap` 核对附录 A 的六个锚点 + 待核项（`IGrid#getMachines`、`getGridConnectableSides` 默认实现、`getItemFromBlockEntity`）。
6. `git init` + 首次提交（**需开发者确认后执行**）。

验证：`.\gradlew.bat build`（exit=0）+ `unzip -l` 产物含 `mods.toml`（T0）。
**若依赖坐标拉不下来：换一次来源仍失败即停手（AGENTS §5），附两次原始报错。**

### Wave 1 · 方块 + 方块实体 + 能量 + 开关

> **完成情况（2026-11-10）**：1–5 落地。T1 探针 27 passed / 0 failed（电量钳制、饱和接收、抽取边界、面白名单）；
> `build` exit=0；产物含 8 个 class 与全套资源。**T2 未通过**：`runServer` 停在 EULA 同意页，
> 未进入注册与方块加载阶段（mod 已被 FML 识别）。

1. 方块类（含两个方块状态属性、`use` 开关判定、ticker 注册、掉落与采集属性）。
2. 方块实体：基类选 AE2 的「网络 + 库存」基类，`createMainNode()` 里建节点（不要求频道，避免与网络规划冲突——**待开发时确认**：是否 `REQUIRE_CHANNEL`，默认不要求）。
3. 能量适配器：`long` 池 + `IEnergyStorage`（`int` 钳制）、面白名单、关闭态拒付。
4. NBT 持久化、`enabled` 状态写回、`online` 每 10 刻复核。
5. 资源：`blockstates`（4 变体）、`models`（2 模型）、`textures`（白/黑框架 + 黑洞 `.mcmeta` 动画 + 关闭指示灯）、`lang`（中英）、`recipes`（8 泥土）、`loot_tables`。

验证：T1 探针（断言 1–4）；`runServer` 冒烟（断言 10、11）；T0 产物核对（断言 12）。

### Wave 2 · 接管判定 + 收支账本（**无 Mixin**）

1. 静态纯函数：`canTakeOver(...)`、`charge(...)`、`formatFe(...)`。
2. 纯类账本：抽象最小库存接口 → 抽取/插入/回滚三段，覆盖 6 条失败路径。
3. 单元探针放 `.rivet/scratch/`，交付前删除；断言逐条 `PASS/FAIL`。

验证：T1 探针（断言 5、6、7、8、9）。

### Wave 3 · Mixin 接入 + 接管执行

1. `mixins.json` + Mixin 类：注入 AE2 合成服务 `submitJob` 的 `@At("HEAD")`、`cancellable`，仅当 `canTakeOver` 为真才取消并执行账本。
2. 服务端线程保证：Mixin 内不解析 `Future`、不跨线程；`submitJob` 本身在服务端线程，直接执行，另加线程断言。
3. 失败路径：账本失败 → **不取消**（返回原版流程），保证「要么完整完成、要么完全没发生」。
4. 注释写上 §7 的风险结论与出处。

验证：`runServer` 冒烟出现 `Mixing … into appeng.me.service.CraftingService` 且无 `ERROR`（T2，**强制门**）；T1 账本全绿；T3 测点 1–6 登记进 `UPDATE.md`。

### Wave 4 · GTMThings 软兼容（反射 + 占位）

1. 兼容类（独立类，`isLoaded` 门控）：扫描水平四面的覆盖板、反射探测类名/字段、被动推送优先、轮询退路、失败降级 warn 一次。
2. 注入路径直接写 `long` 池（不经过 `IEnergyStorage`），关闭态忽略。
3. 日志列出探测到的候选类名，供开发者补齐真实映射。

验证：T1（断言 9）；未装 GTMThings 时 `runServer` 无 `NoClassDefFoundError`（T2）；装了之后的行为登记为 T3。

### Wave 5 · Jade + 收尾

1. Jade 插件（服务端数据 + 客户端组件）+ 格式化函数复用。
2. tooltip 三段文案、中英文语言键全覆盖。
3. 配置项接线与默认值核对（DESIGN §10）。
4. 文档对齐：`DESIGN` 偏移项回写、`UPDATE` 追加条目。
5. 产物核对 + 交付报告证据栏。

验证：T0（断言 12、`lang` 键匹配）；T3 测点 7（Jade 四项显示、开关与在线状态变化）。

---

## 附录 A · 外部契约锚点（签名核对用，非行为证据）

来源：AE2 `AppliedEnergistics/Applied-Energistics-2` 分支 `forge/1.20.1`；Jade `Snownee/Jade` 分支 `1.20-forge`。抓取时间 2026-11-10。

**已用实际依赖产物复核**（T0）：对 `appliedenergistics2-forge-15.4.10_mapped_official_1.20.1.jar` 执行 `javap -p`，本表和下列条目全部吻合——唯一的修正是接口包名：`IGridConnectedBlockEntity` 实际在 `appeng.me.helpers`（不是 `appeng.api.networking`）。

### A.1 AE2

| 锚点 | 内容 |
|------|------|
| `appeng/api/networking/crafting/ICraftingService.java:86` | `Future<ICraftingPlan> beginCraftingCalculation(Level level, ICraftingSimulationRequester simRequester, AEKey craftWhat, long amount, CalculationStrategy strategy)` |
| `appeng/api/networking/crafting/ICraftingService.java:106` | `ICraftingSubmitResult submitJob(ICraftingPlan job, @Nullable ICraftingRequester requestingMachine, @Nullable ICraftingCPU target, boolean prioritizePower, IActionSource src)` ← **本项目唯一注入点** |
| `appeng/api/networking/crafting/ICraftingPlan.java` | 接口：`finalOutput()` / `bytes()` / `simulation()` / `multiplePaths()` / `usedItems()` / `emittedItems()` / `missingItems()` / `patternTimes()` |
| `appeng/crafting/CraftingPlan.java:27-35` | `record CraftingPlan(GenericStack finalOutput, long bytes, boolean simulation, boolean multiplePaths, KeyCounter usedItems, KeyCounter emittedItems, KeyCounter missingItems, Map<IPatternDetails, Long> patternTimes) implements ICraftingPlan` |
| `appeng/crafting/execution/CraftingSubmitResult.java:15` | `static ICraftingSubmitResult successful(@Nullable ICraftingLink link)` |
| `appeng/menu/me/crafting/CraftConfirmMenu.java:185 / :266` | 玩家路径：先 `beginCraftingCalculation` 拿计划做展示，确认后 `submitJob(result, null, selectedCpu, true, actionSrc)` |
| `appeng/crafting/execution/CraftingCpuLogic.java:84-113` | `trySubmitJob` 三条校验 → `tryExtractInitialItems` 真实扣料 → 构造任务 → `cluster.updateOutput` |
| `appeng/crafting/execution/CraftingCpuLogic.java:302-308 / :325` | 完成判定只在 `insert` 里 `remainingAmount <= 0 → finishJob(true)` |
| `appeng/crafting/execution/ExecutingCraftingJob.java:66-75` | `remainingAmount = finalOutput().amount()`；`waitingFor` ← `emittedItems()`；`tasks` ← `patternTimes()` |
| `appeng/crafting/execution/CraftingCpuHelper.java:47-72` | `tryExtractInitialItems`：按 `usedItems()` 从网络抽取进 CPU 库存，失败逐项回灌 |
| `appeng/blockentity/grid/AENetworkInvBlockEntity.java:27-48` | `extends AEBaseInvBlockEntity implements IGridConnectedBlockEntity`；ctor `(BlockEntityType<?>, BlockPos, BlockState)`；`getMainNode()` / `createMainNode()`；`loadTag`/`saveAdditional` 自行收发节点 NBT |
| `appeng/blockentity/grid/AENetworkInvBlockEntity.java:60-82` | `onChunkUnloaded`/`setRemoved` → `getMainNode().destroy()`；`onReady()` → `create(level, worldPosition)`；`clearRemoved()` → `scheduleInit()` |
| `appeng/api/networking/GridHelper.java` | `createManagedNode(host, listener)`（节点工厂） |

### A.2 Jade

| 锚点 | 内容 |
|------|------|
| `snownee/jade/api/WailaPlugin.java` | 类注解 `@WailaPlugin`（`RUNTIME` 保留，插件发现用） |
| `snownee/jade/api/IWailaPlugin.java` | `default void register(IWailaCommonRegistration)`、`default void registerClient(IWailaClientRegistration)` |
| `snownee/jade/api/IWailaCommonRegistration.java:20` | `void registerBlockDataProvider(IServerDataProvider<BlockAccessor> dataProvider, Class<? extends BlockEntity> block)` |
| `snownee/jade/api/IWailaClientRegistration.java:71` | `void registerBlockComponent(IBlockComponentProvider provider, Class<? extends Block> block)` |
| `snownee/jade/api/IBlockComponentProvider.java` | `void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)` |
| `snownee/jade/api/IServerDataProvider.java` | `void appendServerData(CompoundTag data, T accessor)` |

---

## 附录 B · 未核实项与待定决策（Wave 0 必须销号）

| 编号 | 事项 | 处置 |
|------|------|------|
| B-1 | `IGrid#getMachines(Class<T>)` 的准确签名与返回类型 | **已销号（2026-11-10 T0）**：`public abstract <T> Set<T> getMachines(Class<T>)`，直接可用 |
| B-2 | AE2 15.4.x 与本项目可用的 Maven 来源 | **已销号（2026-11-10）**：`https://modmaven.dev` → `appeng:appliedenergistics2-forge:15.4.10`（带完整 POM，`fg.deobf` 正常重映射）。Jade 走 `https://api.modrinth.com/maven` → `maven.modrinth:jade:11.13.3+forge` |
| B-3 | `AENetworkInvBlockEntity` 的强制实现面 | **已销号（2026-11-10 T0）**：基类自身抽象成员来自 `AEBaseInvBlockEntity`（`getInternalInventory()`、`onChangeInventory(InternalInventory,int)`），接口 `appeng.me.helpers.IGridConnectedBlockEntity` 强制 `getMainNode()` 与 `saveChanges()`；`getGridConnectableSides(BlockOrientation)` 是 **default**，按需覆写即可 |
| B-4 | 节点是否设 `REQUIRE_CHANNEL`（默认不设：本方块不占频道） | Wave 1 定稿并写进 `UPDATE.md` |
| B-5 | GTMThings 覆盖板 API 类名/字段/推送方式（公开资料缺失） | Wave 4 反射探测 + 占位；**不得虚构类名**。**已定（2026-11-10）**：因此**不引入 GTMThings 依赖坐标**，联调时把 jar 丢进 `run/mods` |
| B-6 | `CraftConfirmMenu` 对「成功但无链接」的客户端处理（是否会报错或残留待确认状态） | Wave 3 读该方法 266 行之后的处理逻辑 + T3 测点 1 |
| B-7 | 计费口径最终形态 | **已定（2026-11-10）：按次 `PER_JOB`**；`PER_UNIT` 保留为配置项 |
| B-8 | 底面是否允许安装 GTMThings 覆盖板 | Wave 4 实机确认；不支持则该边界自动消失 |
| B-9 | 黑洞「动态」是走 `.mcmeta` 帧动画还是第三方着色器 | 默认帧动画（零代码）；如要更强可后续替换，不改逻辑 |
