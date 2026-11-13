# AGENTS.md — MatterReassembler（NeoForge 1.21.1）

> 本文件是本仓库的开发协作者规范。AI / Agent 进入本项目后必须先读本文，再改代码。
> 功能语义以 `DESIGN-1.21.1.md` 为准；变更流水以 `UPDATE.md` 为准。本文管**怎么干活**。
> 三份文件的分工不可混：**意图 → 设计方案；流水 → 更新记录；纪律 → 本文**。
> **本仓库是 1.21.1 迁移版**；玩法真源在 `MatterReassembler-Forge-1.20.1` 仓库。

---

## 0. 已识别环境（锁定，不得擅自变更）

| 项 | 值 |
|---|---|
| 项目名 | MatterReassembler（物质重组器） |
| mod_id | `matterreassembler` |
| Minecraft | **1.21.1**（锁定） |
| 加载器 | **NeoForge 21.1.x**（`neo_version=21.1.200`） |
| Java | **21** |
| 映射 | Parchment 1.21.1（`2024.11.17`）；类名走 Mojang official |
| 构建系统 | **ModDevGradle 2.0.146** |
| 硬依赖 | **AE2 19.2.x（NeoForge）**，本仓库以 `libs/appliedenergistics2-19.2.19.jar` 接入 |
| 软依赖 | **Jade 15.x（NeoForge）**，`libs/Jade-1.21.1-NeoForge-15.10.6.jar`，`compileOnly` + `localRuntime` |
| Mixin | **有**（唯一注入点：`CraftingService#submitJob` 的 HEAD 短路），声明在 `neoforge.mods.toml` 的 `[[mixins]]` |
| 无 Access Transformer | 本模组不需要 |
| 包名 | `com.wenxing.matterreassembler`（全链同步：`build.gradle`、`gradle.properties`、`neoforge.mods.toml`） |
| 数据路径风格 | **1.21 单数**：`recipe/`、`loot_table/`（不是 1.20 的 `recipes/`、`loot_tables/`） |
| 元数据文件 | `src/main/templates/META-INF/neoforge.mods.toml`（构建期展开；旧名 `mods.toml` 不再被读取） |
| 设计文档 | `DESIGN-1.21.1.md`（单文件，语义真源） |
| 变更记录 | `UPDATE.md`（**单文件，只追加**） |

### 0.1 工程现状（2026-11-14 实测，非推测）

| 事实 | 证据 |
|---|---|
| MDK 示例文件**已清空** | `src/main/resources/assets/examplemod/` 已删；`gradle.properties` 的 `mod_id` 等已改名 |
| 全部源码**已迁移** | 19 个 class 源文件在 `src/main/java/com/wenxing/matterreassembler/`（主类 / 方块 4 / 能量 2 / 接管 6 / 配置 1 / 物品 1 / Jade 3 / Mixin 1） |
| 编译**通过** | `./gradlew compileJava --offline` → `BUILD SUCCESSFUL` |
| 依赖**走本地 jar** | `libs/` 两个 jar；`build.gradle` 用 `compileOnly files(...)` + `localRuntime files(...)`。原因见 §8 R9：本机 `maven.neoforged.net` 不可达，Modrinth maven 的 artifact 布局随版本漂移，本地 jar 让构建完全离线可重现 |
| NeoForge 产物**在本地缓存** | `build/moddev/artifacts/neoforge-21.1.200-merged.jar`（32 MB，MC+NeoForge 合并，official 名），API 锚点一律按它核对 |
| **非 git 仓库** | 本项目目录尚未 `git init`（2026-11-14 实测 `fatal: not a git repository`）。初始化属不可逆动作，需开发者明确同意 |
| 无测试框架 | 没有 `src/test/`；纯逻辑验证一律用 `.rivet/scratch/` 一次性探针，交付前删除 |

**因此：**「能构建」只等于「API 适配正确、产物结构完整」，不等于玩法可用。凡是「玩家操作后世界如何响应」的结论一律归 T3，由开发者实测。

---

## 1. 角色与目标

你是拥有 10 年 Minecraft 模组开发经验的专业开发人员，精通 **NeoForge 1.21.1**、**AE2 19.x API**、
NeoForge Energy Capability（`Capabilities.EnergyStorage.BLOCK`）、Mixin 与 Jade 插件。

本模组定位：**AE2 的「合成旁路」方块**——用巨额 FE 换取「跳过样板执行、直接交付产物」的能力。
它是玩法设施，不是平衡设施。

**声明（强制）：**

- 数值（每合成 FE 成本、池上限策略、吞吐）**全部走配置**，默认值见设计方案。
- AI **可以**指出数值/实现/性能上的问题并给建议。
- AI **不得**擅自改默认数值来「平衡」它，也不得给机器加**未被要求**的限制（例：额外的启动延迟、冷却、每刻拉取）。
- 已裁定为「有意设计」的项（见 §8），不得当缺陷反复提。
- **玩法以 1.20.1 真源为准**：本仓库只做 API 适配与版本差异修复。若必须先在本仓库改玩法，`UPDATE.md` 必须注明「真源未同步」。

---

## 2. 版本策略

1. 仅在开发者明确要求时升级 MC / NeoForge / Parchment / Java / AE2 / Jade。
2. 未经确认禁止改 `gradle.properties` 里 `minecraft_version`、`neo_version`、`parchment_*`、`ae2_version`、`jade_version`。
3. 禁止擅自迁回 Forge；跨加载器去真源仓库，不在本仓库内兼容。
4. 版本号写在 `gradle.properties` 与 `UPDATE.md`；不为补丁新建独立更新文件。
5. AE2 是**硬依赖**，其小版本变化必须重跑 §4 的 T0/T2 全套；Jade 是软依赖，其缺失必须**静默降级**。

---

## 3. 每次更新后的检查（强制）

### 3.1 关联检查表

| 改了什么 | 必须再看 |
|---|---|
| 注册（方块/物品/方块实体/创造栏） | 注册名、语言文件（中英）、创造栏、配方、战利品表、`blockstates`/`models` 文件名与注册名一致；方块/物品必须走 `DeferredRegister.Blocks/Items` 专用入口（注册方补 `setId`） |
| 能量 Capability | **绑定在 `RegisterCapabilitiesEvent`**（不是方块实体覆写）；面判定（`Direction` 白名单）、`simulate` 与 `modulate` 结果一致、`long`↔`int` 钳制、关闭态直接返回 0、**不得在 `serverTick` 主动拉/推** |
| 电量持久化 | NBT 键、`setChanged()` 调用；`loadTag`/`saveAdditional` 现已带 `HolderLookup.Provider` 形参，**漏掉形参会静默变成不复写**（编译期就报 @Override 错，但别靠运气） |
| 开关状态 | 服务端权威、外观属性、Jade 显示、能量拒付、合成拒付（四处必须同步翻转） |
| 方块交互 | 1.21 把交互拆成 `useItemOn`（手持物品）与 `useWithoutItem`（空手）。本方块**只覆写后者**——空手判定由引擎保证，比手写 `heldItem.isEmpty()` 更严 |
| 合成接管 | 资格条件（节点 active / 开启 / 电量足）、材料抽取与回滚、FE 扣除时机与主线程、**机器请求（requester ≠ null）是否被误接管** |
| Mixin | 目标签名用 `javap` 核对 **AE2 19.2.x 产物**（见 §3.1.1）；**必须跑 T2** |
| 软依赖（Jade） | 缺失时**不加载**兼容类；兼容类不得出现在主类常量池（否则 `NoClassDefFoundError`） |
| 配方与语言 | `recipe/`、`loot_table/` **单数**路径；`lang/en_us.json` + `lang/zh_cn.json` 键全覆盖 |
| 配置 | `ModConfigSpec` 键名稳定；默认值变更写进 `UPDATE.md`；`defineInRange` 有**两套重载**（见 §3.1.2） |
| 构建属性 / 元数据 | **`gradle.properties` 里不得直接写非 ASCII**：Gradle 以 ISO-8859-1 读取，中文会经 `processResources` 的 `expand` 变成双重编码。中文一律写 `\uXXXX`，并在 `unzip -p <jar> META-INF/neoforge.mods.toml` 里核对字节 |

#### 3.1.1 Mixin 与 AE2 类名的特殊规则（本仓库高频踩点）

- AE2 是**模组**，其自有类与方法名不进任何官方映射，运行期保持原名——判断「类/方法是否存在」要读 AE2 产物或 `javap`，不能靠 MC 映射表反推。
- 本项目唯一注入点的描述符**全部是 AE2 自有类型**（`ICraftingPlan` / `ICraftingRequester` / `ICraftingCPU` / `boolean` / `IActionSource`）＋返回 `ICraftingSubmitResult`，这是刻意的：描述符不依赖 MC 映射，跨小版本稳定。
- 注入仍然写 `remap = false`：1.21 全链路用 official 名，Annotation Processor 不需要也不应去查映射表。
- 注入目标的 `static` 修饰符与形参必须与目标方法逐字一致；错配只在**启动期**以 `MixinApplyError` / `InvalidInjectionException` 暴露，编译与 T0 拦不住 → 见 §4「Mixin 改动强制门」。
- `priority`：数值**越小越先应用**。

#### 3.1.2 `ModConfigSpec` 的两套 `defineInRange`

1.21.1 同时存在：
- `defineInRange(String, long, long, long)` → **`LongValue`**（本仓库用它）
- `defineInRange(String, V, V, V, Class<V>)` → `ConfigValue<V>`（泛型版）

写错会编译失败（`ConfigValue<V>` 与声明类型 `LongValue` 不一致）。本仓库实测踩过一次，见 `UPDATE.md`。

### 3.2 构建

- 改代码 → `./gradlew build --offline`（本机离线构建，缓存齐全）。
- 仅文档 → 不构建。
- 失败必须修或如实报，不得「重试到通」。

### 3.3 静态自检（每次交付前过一遍）

1. 服务端权威；客户端只做展示与预测。
2. 一切能量增减发生在服务端线程；解析 `Future` 的线程不等于服务端线程（**必须** `level.getServer().execute`）。
3. 拒绝裸递归、拒绝在 `serverTick` 编排能量搬运。
4. 注释只写**非显然的 WHY**（三处必须有：`long`→`int` 钳制、空手交互为什么只覆写 `useWithoutItem`、空 `patternTimes` 的风险裁定）。
5. 软依赖的类加载隔离到位：兼容类只在 Jade 存在时被其扫描器加载。
6. 配置读写的默认值与 DESIGN 一致。
7. `ResourceLocation` 一律用 `fromNamespaceAndPath`（1.21 已移除公开构造器）。

### 3.4 未验证项

写明「未实机验证」+ 人工测点（前置/步骤/期望/失败/回归）。**不得**用一句「未实机验证」笼统带过。

---

## 4. 结构性验收（AI 可执行）

本节沿用「产物与代码的结构性事实」与「实机行为」的分界：**T0–T2 与单元探针都不是「玩家操作后世界如何响应」的证据**，不得这样表述。

| 级别 | 名称 | 状态 | 能证明什么 | 耗时 |
|---|---|---|---|---|
| T0 | 产物结构核对 | **默认允许；每次交付必做** | 代码以意图形态进入产物 | 秒级 |
| T1 | 隔离逻辑验证 | **允许**（限 `.rivet/scratch/`） | 纯函数/纯判定的行为正确 | 秒级 |
| T2 | 无头运行时冒烟 | **允许**（限时、后台、日志落盘） | 注册/加载/接线/Mixin 在真实运行时成立 | 分钟级 |
| T3 | 真实客户端行为 | **仍禁止**，归开发者 | 需图形界面或真实连接的断言 | — |

**T0 —— 产物结构核对（必做）**：对**自己产出的 jar** 只读核对——`unzip -l` 确认新增 class（含内部类 `Outer$Inner`）全部在产物内；`javap -p -c` 核对常量、注册名字面量、关键 `putfield`/`invokevirtual` 目标；`unzip -p <jar> META-INF/neoforge.mods.toml` 核对 modId/版本/依赖；`assets/`、`data/`、`mixins.json` 随产物打包。
证据格式：`verify: T0 | <命令> | exit=0 | 命中: <计数/取值>`。禁止把「`javap` 看到字段写入」说成「运行时生效」。

**T1 —— 隔离逻辑验证（允许）**：位置限 `.rivet/scratch/`，交付前删除；不引入新依赖。准入条件是被测逻辑不依赖 MC 运行时——所以本项目的核心判定必须抽成静态纯函数：电量钳制、数值格式化、能量面白名单、接管资格判定、材料/产物收支核算、计费口径。断言逐条输出 `PASS/FAIL` + 实际值，**不得为通过而弱化断言**。
证据格式：`verify: T1 | <探针路径> | <N passed / M failed>`。

**T2 —— 无头运行时冒烟（允许）**：`./gradlew runServer --offline`（后台、限时、日志落盘；本仓库 `runs.server` 已配 `programArgument '--nogui'`，**不要额外传 `--nogui`**，Gradle 会当成自身选项报错）。判据：MOD 加载无异常、`logs/latest.log` 无 `ERROR`/未捕获异常、出现 `Done (…)! For help, type "help"`、AE2 与本模组均无 Mixin 失败；有合成接管 Mixin 时**必须**出现 `Mixing CraftingServiceMixin from matterreassembler.mixins.json into appeng.me.service.CraftingService`。
证据格式：`verify: T2 | runServer <时长> | <校验点结果>`。

**Mixin 改动强制门**：新增或修改注入点**必须跑一次 T2**。编译与 T0 都拦不住修饰符/形参错配，只有启动期的 `MixinApplyError` / `InvalidInjectionException` 会暴露。

**可验证性前置（设计门禁）**：新增核心逻辑至少一条不变量必须落在 T0/T1 可覆盖的形态上（静态纯函数、纯状态机、常量集中定义）。计划若显示核心断言 T3 占比过半，视为设计未完成，须重新设计。
`DESIGN-1.21.1.md` §11 是本条的落地表，改设计时必须同步那张表。

**交付报告证据栏（固定格式）**：

```
verify: T0 | <命令>            | exit=0 | 命中: <计数/取值>
verify: T1 | <探针路径>         | <N passed / M failed>
verify: T2 | runServer <时长>   | <校验点结果>
unverified: T3 | <断言>          | 障碍: <具体> | 人工测点 #N
```

「未运行」写「未运行」；命令启动但未终止（超时/挂起）同样写「未验证」，并点名是哪条命令没跑完。

---

## 5. 失败重试上限（硬性）

同一件事**失败两次即停手**，改汇报给用户：

- 口径是「同一目标 + 同一手段」：同一条命令、同一种调用形态、同一个构建/验证流程，第二次失败即到上限。
- 到上限后不得再试第三次，不得靠换参数、加 `--force`、绕开门禁「试到通」。
- 唯一例外：第二次尝试基于**新证据**换了假设，并在说明里写清「这次为何不同」——此时按第一次重新计数。
- 汇报固定五项：① 要达成什么；② 两次失败的原始输出（命令 + 退出码 + 报错原文）；③ 已排除的可能；④ 根因判断或待验证假设；⑤ 需要用户做的一件事。
- 汇报后等指示，不自行续试。

本仓库已知的两类「两次失败」高发点：依赖解析（`maven.neoforged.net` 不可达；换一次来源仍失败即停）；Mixin 启动期报错（按报错改一次仍失败即停，附完整堆栈）。

---

## 6. 工作流（每次任务）

1. 读 `DESIGN-1.21.1.md` 与 `UPDATE.md`；玩法争议打开 1.20.1 真源仓库对照。
2. 只改与当前任务相关的内容；遵守设计方案「非目标」。
3. 代码改动 → 同步更新**设计方案**（效果/实现/边界）与**更新记录**（只追加）。
4. 改了代码则构建（§3.2），按改动选择 §4 的 T0/T1/T2。
5. 交付：摘要、证据栏、人工测点建议、产物、风险。
6. 涉及不可逆动作（`git init`、改构建脚本、删文件、改版本）→ 先确认。

**日志分级**：诊断观测用 `LOGGER.debug`；异常与降级用 `LOGGER.warn` 带上下文；**禁止**在 `serverTick` 或每次能量交互上打 `info`（能源接收器每 tick 调用，必刷屏）。例外：合成接管的**不接管原因**打 `info`——它是玩家操作级事件，频率极低，且「为什么不接管」正是玩家最需要知道的信息。

---

## 7. 本仓库工程红线（NeoForge 1.21.1 + AE2 19.x）

| 能力 | 本项目必须用 | 反面 |
|---|---|---|
| 方块实体基类 | AE2 的**网格连接**基类 `AENetworkedBlockEntity` | 自造 `BlockEntity` 再手搓网格节点 |
| 内部库存 | 本方块**没有**物品栏，所以用无库存基类 | 误用 `AENetworkedInvBlockEntity`（会逼你实现两个空壳方法） |
| 网格节点 | `IManagedGridNode`（基类 `createMainNode()` 里构建） | 直接 `IGridNode` 手工生命周期 |
| 电量存储 | 自有 `long` 池 + `IEnergyStorage` 适配（`int` 钳制） | 用 `EnergyStorage`（内部 `int`，必溢出） |
| 能量 capability | `RegisterCapabilitiesEvent#registerBlockEntity` + `Capabilities.EnergyStorage.BLOCK` | 覆写 `BlockEntity#getCapability`（1.21 已无此入口） |
| 方块交互 | `useWithoutItem`（空手）/ `useItemOn`（手持） | `onActivated`（AE2 15.x 的旧钩子，19.x 已移除） |
| 配置 | `ModConfigSpec` + `ModContainer#registerConfig` | `ForgeConfigSpec` / `ModLoadingContext.get()` |
| 资源位置 | `ResourceLocation.fromNamespaceAndPath(ns, path)` | `new ResourceLocation(ns, path)`（构造器已移除） |
| 物品 tooltip | `appendHoverText(ItemStack, Item.TooltipContext, List, TooltipFlag)` | 1.20 的 `Level` 形参 |
| 状态同步 | `BlockState`（外观/开关，引擎自动同步）+ Jade 服务端数据（电量） | 把 `long` 塞进 `getUpdateTag` 全量块包 |
| 数据目录 | `recipe/`、`loot_table/`（**单数**） | 1.20 的 `recipes/`、`loot_tables/`（1.21 不读） |
| 配方结果 | `"result": {"id": "...", "count": 1}` | 1.20 的 `{"item": "..."}` |
| ItemStack 数据 | **Data Components** | NBT（1.20 写法） |
| 玩家数据 | NeoForge **Attachment**（本模组目前不用） | Capability 玩家数据 |

其他红线：

- **能源面只能由 capability 的 `Direction` 上下文判定**，不得用「玩家点击面」推断。
- **`serverTick` 内不得主动 `receiveEnergy`/`extractEnergy`**（含对邻居、对 capability 缓存的探测式拉取）。
- 扣电与材料收发一律在服务端线程，且**先模拟后执行**（`simulate` 成功再 `modulate`）。
- 接管失败必须**完整回滚**（材料回网、FE 不扣、返回原版结果）。
- 注册名稳定：`matter_reassembler`、`matter_reassembler_2` 不改。
- 不在错误线程改世界/方块实体数据。
- 软依赖类不得出现在主类常量池里（否则 `NoClassDefFoundError`）。

---

## 8. 已识别风险与裁定（本仓库专属，改相关面必读）

| # | 风险 | 裁定 |
|---|---|---|
| R1 | **空 `patternTimes` 的计划不会崩，但会让 CPU 永久挂起**（完成只发生在「产物入账使剩余数归零」时；空样板集永远不产出）。1.20.1 端已用 AE2 源码证据链裁定。 | 采纳「不把交付交给 CPU」：接管时自行完成材料抽取与产物投放；提交面只保留最小 Mixin。1.21 侧 `ICraftingPlan` 八个方法与 1.20.1 逐字一致（T0 实测），结论继续成立 |
| R2 | 在计算完成时就扣 FE → 玩家在确认界面取消，电已扣。 | 扣电点保持在**确认提交**时刻（`submitJob` HEAD）；`beginCraftingCalculation` 的返回值仅用于展示与预检 |
| R3 | `IEnergyStorage` 只能 `int`，外部机器看到的上限是 21 亿，而内部 `long` 可更大。 | 接口钳制、内部 `long`；Jade 显示**内部值**，tooltip 说明「单次交互上限 2,147,483,647 FE」 |
| R4 | 接管发生在计算线程完成的回调里。 | 一切副作用经 `level.getServer().execute` 回主线程；1.21 侧执行器另加 `isSameThread()` 断言兜底 |
| R5 | 按次计费使「一次请求 1000 个」与「请求 1 个」成本相同 | **有意**（开发者 1.20.1 端定稿）；要按量收费改 `chargeMode=PER_UNIT`，不改代码 |
| R6 | **本机 `maven.neoforged.net` 不可达**（姊妹仓库 `attributearch-neoforge-1.21.1/build.gradle` 有明确注释与 `local-maven` 镜像安排）。 | 构建一律 `--offline`，依赖全部走 `~/.gradle/caches` 与 `libs/`。新增依赖前先确认缓存已有该坐标 |
| R7 | AE2 19.x 与 15.x 的**类骨架不同**：`AENetworkInvBlockEntity` 已被删除，改为 `AENetworkedBlockEntity`（无库存）/ `AENetworkedInvBlockEntity`（有库存）两个基类。 | 本方块用前者；迁移任何新方块先 `javap` 确认基类存在性与强制实现面（§3.1.1） |
| R8 | Modrinth maven 的 artifact 布局随版本漂移，且 `maven.modrinth:ae2:19.2.19` 的坐标不可直接解析。 | 依赖 jar 放 `libs/`，坐标固定为文件名；换版本时只改 `gradle.properties` 的 `ae2_version` 并替换 jar |
| R9 | 二代机配方依赖第三方物品 `wenxingtools:custom_spawner`。 | 仍为普通 shapeless（1.20.1 端实测 `forge:conditional` 在运行期不可用）。未装 wenxingtools 时该配方被跳过并打日志，不影响其它功能。1.21 侧的 conditional 序列化器未再验证 |

---

## 9. 变更边界与安全

- 以 `DESIGN-1.21.1.md` 为意图真源；代码与文档冲突时先改代码，再补文档（并记 `UPDATE.md`）。
- 删除文件、覆盖设计、改构建脚本、改版本、初始化 git、任何破坏性操作：**先确认**。
- 交付写明 jar 路径与构建结果；未构建写明原因。
- 不把「AE2 官方源码长这样」当作本项目已验证——外部源码只用于**核对签名**，不当行为证据。

---

## 10. 姊妹仓库

| 仓库 | 角色 |
|---|---|
| `MatterReassembler-Forge-1.20.1` | **玩法真源**。语义冲突时以它为准；本仓库只做 API 适配 |
| `attributearch-neoforge-1.21.1` | 同族 1.21.1 仓库，三件套文档与构建配置的写法参照 |

外部版本矩阵：

| 组件 | 版本 | 依赖类型 | 缺失时行为 |
|---|---|---|---|
| AE2 | 19.2.x（NeoForge） | 硬 | 不加载（`neoforge.mods.toml` 声明 `type="required"`） |
| Jade | 15.x（NeoForge） | 软 | 无显示，其余功能正常 |
| wenxingtools | 与 1.21.1 同代 | 软（仅配方） | 二代机配方被跳过并打日志 |
