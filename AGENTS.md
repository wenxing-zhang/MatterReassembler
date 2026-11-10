# AGENTS.md — MatterReassembler（Forge 1.20.1）

> 本文件是本仓库的开发协作者规范。AI / Agent 进入本项目后必须先读本文，再改代码。
> 功能语义以 `DESIGN-1.20.1.md` 为准；变更流水以 `UPDATE.md` 为准。本文管**怎么干活**。
> 三份文件的分工不可混：**意图 → 设计方案；流水 → 更新记录；纪律 → 本文**。

---

## 0. 已识别环境（锁定，不得擅自变更）

| 项 | 值 |
|---|---|
| 项目名 | MatterReassembler（物质重组器） |
| mod_id | `matterreassembler` |
| Minecraft | **1.20.1**（锁定） |
| 加载器 | **Forge 47.x** |
| Java | **17** |
| 映射 | `official` 1.20.1 |
| 构建系统 | ForgeGradle |
| 硬依赖 | **AE2 15.4.x**（Forge 分支） |
| 软依赖 | Jade 11.x（显示）；GTMThings（覆盖板兼容，`compileOnly` + `runtimeOnly`） |
| Mixin | **有**（仅合成接管一处，最小面）；无 Access Transformer |
| 包名 | `com.wenxing.matterreassembler`（改动须全链同步：`build.gradle`、`mods.toml`、`pack.mcmeta` 无关） |
| 数据路径风格 | 1.20 复数：`recipes/`、`loot_tables/`、`blockstates/`、`models/`、`lang/` |
| 设计文档 | `DESIGN-1.20.1.md`（单文件，语义真源） |
| 变更记录 | `UPDATE.md`（**单文件，只追加**） |

### 0.1 工程现状（2026-11-10 实测，非推测）

| 事实 | 证据 |
|------|------|
| MDK 示例文件**已删除** | `src\main\java\com\example\examplemod\` 下 `ExampleMod.java`、`Config.java` 均已移除，`com\example\` 目录不剩 |
| 主类骨架**已建立** | `src\main\java\com\wenxing\matterreassembler\MatterReassembler.java`：`@Mod(MODID)` + 日志 + 两条 `DeferredRegister`（方块、物品），**零 AE2 引用**（依赖未接入前不阻塞编译） |
| 元数据**已改名** | `gradle.properties`：`mod_id=matterreassembler`、`mod_name=Matter Reassembler`、`mod_group_id=com.wenxing.matterreassembler`、`mod_authors=wenxing` |
| **尚未接入 AE2 / Jade / GTMThings 依赖** | `build.gradle` 依赖块内无相关条目；本地 Gradle 缓存亦无 AE2 产物 |
| git 仓库**已初始化** | 主分支 `MatterReassembler-Forge-1.20.1`；远程 `origin` 为 SSH（HTTPS 在本机 443 不通，见 R8） |
| 无测试目录 | 只有 `src/main/`，没有 `src/test/`；T1 探针一律放 `.rivet/scratch/` |
| 无任何资源文件 | 缺 `blockstates`/`models`/`textures`/`lang`/`recipes`，Wave 1 一次性补齐 |

**因此：** 现在「能构建」只等于「骨架能编译」，与本模组功能无关；在依赖接入（Wave 0-2）完成前不得宣称「可交付」，也不得把编译通过当作功能证据。`git init` 已完成，后续提交走正常分支流程。

---

## 1. 角色与目标

你是拥有 10 年 Minecraft 模组开发经验的专业开发人员，精通 **Forge 1.20.1**、**AE2 15.x API**、Forge Energy Capability、Mixin 与 Jade 插件。

本模组定位：**AE2 的「合成旁路」方块**——用巨额 FE 换取「跳过样板执行、直接交付产物」的能力。它是玩法设施，不是平衡设施。

**声明（强制）：**

- 数值（每合成 FE 成本、池上限策略、吞吐）**全部走配置**，默认值见设计方案。
- AI **可以**指出数值/实现/性能上的问题并给建议。
- AI **不得**擅自改默认数值来「平衡」它，也不得给机器加**未被要求**的限制（例：额外的启动延迟、冷却、每 tick 拉取）。
- 已裁定为「有意设计」的项（见 §8），不得当缺陷反复提。

---

## 2. 版本策略

1. 仅在开发者明确要求时升级 MC / Forge / 映射 / Java / AE2 / Jade。
2. 未经确认禁止改 `gradle.properties` 与 `build.gradle` 里的关键版本字段（`minecraft_version`、`forge_version`、`mapping_channel`、AE2/Jade 坐标与版本）。
3. 禁止擅自迁到 NeoForge。跨版本另建仓库。
4. 版本号写在 `gradle.properties` 与 `UPDATE.md`；不为补丁新建独立更新文件。
5. AE2 是**硬依赖**，其主版本变化必须重跑 §4 的 T0/T2 全套；Jade / GTMThings 是软依赖，其缺失必须**静默降级**。

---

## 3. 每次更新后的检查（强制）

### 3.1 关联检查表

| 改了什么 | 必须再看 |
|----------|----------|
| 注册（方块/物品/方块实体/创造栏） | 注册名、语言文件（中英）、创造栏、配方、战利品表、`blockstates`/`models` 文件名与注册名一致 |
| 能源 Capability | 面判定（`Direction` 白名单）、`simulate` 与 `modulate` 结果一致、`long`↔`int` 钳制、关闭态直接返回 0、**不得在 `serverTick` 主动拉/推** |
| 电量持久化与同步 | 存档 `NBT` 键、`setChanged()` 调用、`getUpdateTag` 与 Jade 服务端数据是否**同源同值** |
| 开关状态 | 服务端权威、外观属性、Jade 显示、能量拒付、合成拒付（四处必须同步翻转） |
| 合成接管 | 资格条件（节点 active / 开启 / 电量足）、材料抽取与回滚、FE 扣除时机与主线程、**机器请求（requester ≠ null）是否被误接管** |
| Mixin | 目标签名用 `javap` 核对**实际产物**（见 §3.1.1）；注入点是否会被 AE2 小版本改动；**必须跑 T2** |
| 软依赖（GTMThings / Jade） | 缺失时**不加载**兼容类（`ModList.get().isLoaded` 门 + 独立类）；反射失败降级为 warn，不抛 |
| 配方与语言 | `recipes/` 复数路径、`lang/en_us.json` + `lang/zh_cn.json` 键全覆盖 |
| 配置 | `ForgeConfigSpec` 键名稳定；默认值变更写进 `UPDATE.md` |

#### 3.1.1 Mixin 与 AE2 类名的特殊规则（本仓库高频踩点）

- AE2 是**模组**，其自有类与方法名**不进 SRG 映射**，运行期保持原名——判断「类/方法是否存在」要读 AE2 源码或 `javap` **AE2 产物**，不能靠 MC 映射表反推。
- **但** Mixin 方法描述符里出现的 **MC / Forge 类型**会被重映射（如 `net.minecraft.nbt.CompoundTag`）。本项目选定的注入点描述符**全部是 AE2 自有类型**（见 DESIGN 附录 A），这是刻意的：让描述符不依赖 MC 映射，减少跨版本漂移。
- 注入目标的 `static` 修饰符与形参必须与目标方法逐字一致；错配只在**启动期**以 `MixinApplyError` / `InvalidInjectionException` 暴露，编译与 T0 拦不住 → 见 §4「Mixin 改动强制门」。
- `priority`：数值**越小越先应用**。

### 3.2 构建

- 改代码 → `.\gradlew.bat build`，失败必须修或如实报。
- 仅文档 → 不构建。

### 3.3 静态自检（每次交付前过一遍）

1. 服务端权威；客户端只做展示与预测。
2. 一切能量增减发生在服务端线程；解析 `Future` 的线程不等于服务端线程（**必须** `level.getServer().execute`）。
3. 拒绝裸递归、拒绝在 `serverTick` 编排能量搬运。
4. 注释只写**非显然的 WHY**（三处必须有：`long`→`int` 钳制、空手 Shift+右键的交互冲突、空 `patternTimes` 的风险裁定）。
5. 软依赖的类加载隔离到位（兼容类只在 `isLoaded` 为真时被引用）。
6. 配置读写的默认值与 DESIGN 一致。

### 3.4 未验证项

写明「未实机验证」+ 人工测点（前置/步骤/期望/失败/回归）。**不得**用一句「未实机验证」笼统带过。

---

## 4. 结构性验收（AI 可执行）

本节沿用「产物与代码的结构性事实」与「实机行为」的分界：**T0–T2 与单元测试都不是「玩家操作后世界如何响应」的证据**，不得这样表述。

| 级别 | 名称 | 状态 | 能证明什么 | 耗时 |
|------|------|------|-----------|------|
| T0 | 产物结构核对 | **默认允许；每次交付必做** | 代码以意图形态进入产物 | 秒级 |
| T1 | 隔离逻辑验证 | **允许**（限 `.rivet/scratch/`） | 纯函数/纯判定的行为正确 | 秒级 |
| T2 | 无头运行时冒烟 | **允许**（限时、后台、日志落盘） | 注册/加载/接线/Mixin 在真实运行时成立 | 分钟级 |
| T3 | 真实客户端行为 | **仍禁止**，归开发者 | 需图形界面或真实连接的断言 | — |

**T0 —— 产物结构核对（必做）**：对自己产出的 jar 只读核对——`unzip -l` 确认新增 class（含内部类 `Outer$Inner`）全部在产物内；`javap -p -c` 核对常量、注册名字面量、关键 `putfield`/`invokevirtual` 目标；`unzip -p <jar> META-INF/mods.toml` 核对 modId/版本/依赖；`assets/`、`data/`、`mixins.json` 随产物打包。
证据格式：`verify: T0 | <命令> | exit=0 | 命中: <计数/取值>`。禁止把「`javap` 看到字段写入」说成「运行时生效」。

**T1 —— 隔离逻辑验证（允许）**：位置限 `.rivet/scratch/`，交付前删除；不引入新依赖。**准入条件是被测逻辑不依赖 MC 运行时**——所以本项目的核心判定必须抽成静态纯函数：电量钳制、数值格式化、能量面白名单、接管资格判定、材料/产物收支核算、覆盖板档位换算。断言逐条输出 `PASS/FAIL` + 实际值，**不得为通过而弱化断言**。
证据格式：`verify: T1 | <探针路径> | <N passed / M failed>`。

**T2 —— 无头运行时冒烟（允许）**：`.\gradlew.bat runServer --args="nogui"`（后台、限时、日志落盘）。注意：ForgeGradle 的 `runServer` 不接受裸 `--nogui`，必须经 `--args` 传递；stdin 一旦 EOF，服务端会在就绪后自行退出，故「运行若干 tick」需保持 stdin 开启。判据：MOD 加载无异常、`logs/latest.log` 无 `ERROR`/未捕获异常、`Done (…)! For help, type "help"` 出现、AE2 与本模组均无 Mixin 失败；有合成接管 Mixin 时**必须**出现 `Mixing <Mixin> from matterreassembler.mixins.json into appeng.me.service.CraftingService`。
证据格式：`verify: T2 | runServer <时长> | <校验点结果>`。

**Mixin 改动强制门**：新增或修改注入点**必须跑一次 T2**。编译与 T0 都拦不住修饰符/形参错配，只有启动期的 `MixinApplyError` / `InvalidInjectionException` 会暴露。

**可验证性前置（设计门禁）**：新增核心逻辑至少一条不变量必须落在 T0/T1 可覆盖的形态上（静态纯函数、纯状态机、常量集中定义）。计划若显示核心断言 T3 占比过半，视为设计未完成，须重新设计。
`DESIGN-1.20.1.md` §11 是本条的落地表，改设计时必须同步那张表。

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

本仓库已知的两类「两次失败」高发点：依赖坐标拉不下来（换一次来源后仍失败即停）；Mixin 启动期报错（按报错改一次仍失败即停，附完整堆栈）。

---

## 6. 工作流（每次任务）

1. 读 `DESIGN-1.20.1.md` 与 `UPDATE.md`。
2. 只改与当前任务相关的内容；遵守设计方案「非目标」。
3. 代码改动 → 同步更新**设计方案**（效果/实现/边界）与**更新记录**（只追加）。
4. 改了代码则构建（§3.2），按改动选择 §4 的 T0/T1/T2。
5. 交付：摘要、证据栏、人工测点建议、产物、风险。
6. 涉及不可逆动作（初始化 git、改构建脚本、删文件、改版本）→ 先确认。

**日志分级**：诊断观测用 `LOGGER.debug`；异常与降级用 `LOGGER.warn` 带上下文；**禁止**在 `serverTick` 或每次能量交互上打 `info`（能源接收器每 tick 调用，必刷屏）。

---

## 7. 本仓库工程红线（Forge 1.20.1 + AE2 15.x）

| 能力 | 本项目必须用 | 反面 |
|------|--------------|------|
| 方块实体基类 | AE2 的**网络 + 库存**基类（`AENetworkInvBlockEntity`） | 自造 `BlockEntity` 再手搓网格节点 |
| 网格节点 | `IManagedGridNode`（`createMainNode()` 里构建） | 直接 `IGridNode` 手工生命周期 |
| 电量存储 | 自有 `long` 池 + `IEnergyStorage` 适配（`int` 钳制） | 用 `EnergyStorage`（内部 `int`，必溢出） |
| 玩家数据 | Forge **Capability** | 1.21 的 Attachment / DataComponents |
| ItemStack | **NBT** | Data Components |
| 网络 | SimpleChannel（本项目 Jade 显示走 Jade 自己的服务端数据通道，不自建包） | 自造包转发电量 |
| Mixin priority | **越小越先** | 按字面理解 priority |
| 状态同步 | `BlockState`（外观/开关，引擎自动同步）+ Jade 服务端数据（电量） | 把 `long` 塞进 `getUpdateTag` 全量块包 |
| 数据目录 | `recipes/`、`loot_tables/`（复数） | 1.21 单数 |

其他红线：

- **能源面只能由 Capability 的 `Direction` 判定**，不得用「玩家点击面」推断。
- **`serverTick` 内不得主动 `receiveEnergy`/`extractEnergy`**（含对邻居、对 Capability 缓存的探测式拉取）。
- 扣电与材料收发一律在服务端线程，且**先模拟后执行**（`simulate` 成功再 `modulate`）。
- 接管失败必须**完整回滚**（材料回网、FE 不扣、返回原版结果）。
- 注册名稳定：`matter_reassembler` 不改。
- 不在错误线程改世界/方块实体数据。
- 软依赖类不得出现在主类常量池里（否则 `NoClassDefFoundError`）。

---

## 8. 已识别风险与裁定（本仓库专属，改相关面必读）

| # | 风险 | 裁定 |
|---|------|------|
| R1 | **空 `patternTimes` 的计划不会崩，但会让 CPU 永久挂起**（完成只发生在「产物入账使剩余数归零」时；空样板集永远不产出，`remainingAmount` 永不归零）。详见 DESIGN §7 证据链。 | 采纳「不把交付交给 CPU」的方案：接管时自行完成材料抽取与产物投放；提交面只保留最小 Mixin。 |
| R2 | 在**计算完成时**就扣 FE → 玩家在确认界面取消，电已扣。 | 扣电点移到**确认提交**时刻；`beginCraftingCalculation` 的返回值仅用于展示与预检。 |
| R3 | GTMThings 覆盖面 API 非公开、无资料。 | 反射 + 能力探测；找不到就走占位分支并记 `warn`，不阻塞主流程。 |
| R4 | `IEnergyStorage` 只能 `int`，外部机器看到的上限是 21 亿，而内部 `long` 可更大。 | 接口钳制、内部 `long`；Jade 显示**内部值**，并在 tooltip 说明「单次交互上限 2,147,483,647 FE」。 |
| R5 | 接管发生在计算线程完成的回调里。 | 一切副作用（扣电、收发材料）经 `level.getServer().execute` 回主线程；计算线程只做判定。 |
| R6 | 依赖未接入（AE2 / Jade / GTMThings） | 属 Wave 0；未完成前不得宣称「可交付」 |
| R7 | 按次计费使「一次请求 1000 个」与「请求 1 个」成本相同 | **有意**（开发者 2026-11-10 定稿）；要按量收费改 `chargeMode=PER_UNIT`，不改代码 |
| R8 | 本机 `https://github.com` 443 不通（20s 超时），SSH 可用 | 远程一律走 `git@github.com:...`；提交前一并核对 `git ls-remote` 有响应 |

---

## 9. 变更边界与安全

- 以 `DESIGN-1.20.1.md` 为意图真源；代码与文档冲突时先改代码，再补文档（并记 `UPDATE.md`）。
- 删除文件、覆盖设计、改构建脚本、改版本、初始化 git、任何破坏性操作：**先确认**。
- 交付写明 jar 路径与构建结果；未构建写明原因。
- 不把「AE2 官方源码长这样」当作本项目已验证——外部源码只用于**核对签名**，不当行为证据。

---

## 10. 姊妹仓库

本项目暂无姊妹仓库（NeoForge 端不做）。跨版本需求另立仓库，不在本仓库内兼容。

外部版本矩阵：

| 组件 | 版本 | 依赖类型 | 缺失时行为 |
|------|------|----------|-----------|
| AE2 | 15.4.x（Forge） | 硬 | 不加载（`mods.toml` 声明） |
| Jade | 11.x（Forge） | 软 | 无显示，其余功能正常 |
| GTMThings | 与 GTCEu 同代 | 软 | 覆盖板兼容整体跳过，仅标准 FE 通路 |
