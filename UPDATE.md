# 更新记录（MatterReassembler · NeoForge 1.21.1）

> 单文件更新记录。新变更**只追加**到本文件，禁止新建 `UPDATE-*.md`。
> 条目写**功能语义**（效果 / 实现思路 / 边界 / 验证），不写具体类名、文件名、路径。
> 顺序：**最新在最上**。已发布条目的「效果/边界」不可改写；纠正写新条目并注明「纠正 <日期> 条目」。
> 与代码冲突时以设计方案为准；本文件只记「发生了什么」。

---

## 条目模板

```
## YYYY-MM-DD · <一句话主题>

| 项 | 内容 |
|----|------|
| 变更类型 | 新增 / 调整 / 修正 / 加固 / 清理 / 建立 |
| 涉及范围 | <受影响的功能面> |
| 是否可回滚 | 是 / 否（原因） |
| 已知风险 | <未覆盖的路径 / 需要开发者确认的点；无则写「无新增」> |

### 效果
### 实现
### 边界
### 验证
```

**验证栏写法（硬性）**：只写本轮**真实机器输出**能指到的结论——`构建通过`、`T1 探针 N passed / M failed`、`T2 冒烟 <校验点>`。没有记录就写「未验证」并给人工测点；命令启动但未终止同样算未验证，并点名是哪条命令。

---

## 2026-11-14 · 修复：线缆连不上——补注册 AE2 的「网格节点宿主」capability

| 项 | 内容 |
|----|------|
| 变更类型 | 修正（实机缺陷） |
| 涉及范围 | AE2 网络接入、能力注册 |
| 是否可回滚 | 是 |
| 已知风险 | 无新增；修复后仍需实机复测接线（见「未验证」） |

### 效果

- 方块可以被 ME 线缆接入网络：线缆会把它当作一个可连接的网格成员，外观随之从白色框架变黑。
- 修复前：方块实体能建出节点、Jade 也可能显示状态，但**线缆完全看不见它**——网络里等于不存在，合成旁路因此整体不可用。

### 实现

- 在注册期显式声明 AE2 的「网格节点宿主」capability，provider 直接返回方块实体自身（网格连接基类已实现该接口）。
- 根因：1.20.1 由 Forge 的能力桥接自动把「实现了网格接口的方块实体」暴露给线缆；1.21 的 NeoForge 取消了方块实体的 `getCapability` 覆写入口，改成注册期显式声明，**第三方方块不注册就不会被发现**——而且不注册不报错、不告警，只是静默不连接。
- 一代机与二代机各注册一次（两个方块实体类型）。

### 边界

- 只影响「能否被线缆发现并接入」；能量接口、开关、合成接管判定与结算逻辑一字未改。
- AE2 的其它能力（存储、合成机器、通用库存）本方块不需要，未注册。

### 验证

- **T0 字节码核对（先 RED 后 GREEN，同一判据）**：
  - RED：修复前产物里对 `IN_WORLD_GRID_NODE_HOST` 的引用 **0** 处（对照：`EnergyStorage` 6 处，证明核对方式本身有效）。
  - GREEN：修复后 **2** 处（两个方块实体类型各一）。
- **编译**：`./gradlew compileJava --offline` → `BUILD SUCCESSFUL`。
- **构建**：`./gradlew build --offline` → `BUILD SUCCESSFUL`；产物 60,361 字节，sha256 `1450fd0f…949f3`，已同步到 `F:\Downloads`（两侧 sha256 一致）。
- **未验证（T3）**：实机接线（测试表 A3）。根因证据来自字节码与 AE2 自身注册模式的对照，不是实机复现。

---

## 2026-11-14 · 审查修正：拦下无主产物的计划、日志降噪、去掉多余的保存钩子

| 项 | 内容 |
|----|------|
| 变更类型 | 修正（审查发现） |
| 涉及范围 | 接管资格判定、不接管日志、方块实体保存钩子 |
| 是否可回滚 | 是 |
| 已知风险 | 无新增；「不接管」的逐台明细改为按需输出，排查时需开 `debugLogging` 或调日志级别 |

### 效果

- **拦下「没有主产物」的计划**：此前若计划的主产物为空，判定仍可能通过，结果是材料被扣走、主产物一件不投。现在这是一条独立否决原因，永不接管。
- **不接管日志降噪**：摘要行保留 info（它带着判定所需的全部事实），逐台方块明细降到 debug。以前网络里有 50 台机器时，一次点击就是 51 行日志，反复点会迅速把有用日志滚掉。
- **恢复 AE2 的保存合并**：去掉自己写的保存钩子。基类实现除了标记「已变更」，还会在 tick 末合并一次（去重），自己写就绕过了那层合并。

### 实现

- 资格判定新增一条「计划有主产物」条件，插在「计划形态」组里（模拟/缺失项之后、方块之前）——判定顺序仍是「先报最根本的一条」。
- 逐台明细增加空元素防御，与筛合格方块时的口径对齐。
- 删除方块实体里对保存钩子的覆写，并在原处留注释说明为什么不该加回来（1.20.1 的基类成员是抽象才需要，1.21 已由基类实现）。

### 边界

- 新增的否决条件只影响「主产物为空」这一种计划；正常计划的行为一字未变。
- 明细日志需要显式开启：`debugLogging=true` **或** 日志级别已调到 debug，两者任一即可。

### 验证

- **T1 探针（先 RED 后 GREEN）**：新增 3 条断言（新否决路径 + 2 条判定顺序性）。
  - RED：`javac` 报 `Inputs` 构造器参数数量不匹配 —— 证明断言确实钉住了「实现未提供该能力」。
  - GREEN：`=== T1 RESULT: 47 passed / 0 failed ===`（原 44 条全部保持通过，无回归）。
- **编译**：`./gradlew compileJava --offline` → `BUILD SUCCESSFUL`（删除保存钩子覆写后，接口契约仍由基类满足）。
- **构建**：`./gradlew build --offline` → `BUILD SUCCESSFUL`；产物 `matterreassembler-NeoForge-1.21.1-1.0.0.jar`（60,135 字节）。
- **T2 冒烟复验**：`runServer` 限时 150s → `Done (` 命中 1 次、`[ERROR]` 命中 **0** 次、配方解析错误 **0** 次；配置按默认值生成 `matterreassembler-common.toml`。
  - 日志里 2 条含项目名的 WARN 是 NeoForge dev 环境已知项（`VanillaPackResourcesBuilder` 的 assets URL schema），与本模组代码无关。
- **未验证（T3）**：真实下单时「无主产物计划」被拒的实际表现（该路径正常玩法不可达），以及降噪后明细日志在 `debugLogging=true` 下的实际输出。

---

## 2026-11-14 · T2 冒烟通过：Mixin 真实注入、服务端就绪、配方条件化与兼容级别修正

| 项 | 内容 |
|----|------|
| 变更类型 | 修正（运行期） |
| 涉及范围 | Mixin 配置、二代机配方、开发环境运行时依赖 |
| 是否可回滚 | 是 |
| 已知风险 | 无新增；`Mixing` 行在非 verbose 日志下被级别过滤，日常冒烟看不到它 |

### 效果

- **Mixin 真实注入成功**：`CraftingServiceMixin` 进入了 AE2 的合成服务，合成旁路的唯一注入点成立。
- **服务端可无头启动**：到达 `Done (0.777s)! For help, type "help"`，`latest.log` 中 **0 条 ERROR**，AE2 自身 mixin 与本模组 mixin 同场无冲突。
- **Jade 兼容层在真实运行时被加载**：`Loaded plugin from com.wenxing.matterreassembler.compat.jade.MatterReassemblerJadePlugin` —— 泛型接口迁移（`IComponentProvider<BlockAccessor>`）在运行期成立，不只是编译通过。
- **二代机配方不再污染日志**：原先「引用未注册物品」在 1.21 会抛 `Parsing error loading recipe` ERROR（1.20.1 只是 skip），改为条件配方后该错误消失，未装 wenxingtools 时配方被静默跳过。
- **消除 Mixin 兼容级别警告**：声明 `JAVA_21` 时 Mixin 0.8.7 会警告「高于本版本支持的上限（JAVA_13）」。改按上限声明后警告消失。

### 实现

- Mixin 兼容级别改为 `JAVA_13`（Mixin 0.8.7 的实际上限）；参照 AE2 自身用 `JAVA_11` 的做法。
- 二代机配方加 `neoforge:conditions` + `neoforge:item_exists`（NeoForge 1.21 的配方条件码，注册在 `neoforge` 命名空间）。
- 开发环境补 AE2 的硬依赖 `guideme`（缺它时 FML 在依赖检查阶段直接拒绝启动）。
- 服务端 run 配置改用非默认端口（本机 25565 被占用），仅影响本地冒烟，不影响产物。

### 边界

- 条件配方只保证「未装 wenxingtools 时不报错、不出配方」；装了 wenxingtools 之后的合成行为仍属 T3。
- 冒烟服务端是空世界：没有 ME 网络、没有玩家，因此**合成接管的实际结算路径没有执行过**。

### 验证

- T2：`runServer` 限时 150s 后强制终止（Gradle 报 exit 143，属限时终止而非崩溃）。
  - `Done (` 命中 1 次；`[ERROR]` 命中 **0** 次；`Parsing error loading recipe` 命中 **0** 次；`higher than the maximum level`（兼容级别警告）命中 **0** 次。
  - Mixin 注入证据来自一次带 `-Dmixin.debug.verbose=true` 的冒烟（临时参数已在验证后移除）：
    `Mixing CraftingServiceMixin from matterreassembler.mixins.json into appeng.me.service.CraftingService`。
    该行在非 verbose 下被日志级别过滤，属正常，**不代表注入失败**。
- **未验证（T3）**：真实下单的旁路结算（扣料/产物投放/扣电/无 CPU 任务）、顶面开关与外观联动、手持物品时 Shift+右键 不被吞、Jade 四行显示的实际观感。

---

## 2026-11-14 · 修正三处阻塞：配置重载、元数据展开键、AE2 的 guideme 依赖

| 项 | 内容 |
|----|------|
| 变更类型 | 修正（构建期） |
| 涉及范围 | 配置类、构建脚本、开发运行时依赖 |
| 是否可回滚 | 是 |
| 已知风险 | 无 |

### 效果

- 配置里的两个 `long` 键恢复为 `LongValue` 类型（编译通过）。
- 元数据模板新增的作者/描述/依赖版本范围四个字段能被构建期展开（不再 `Missing property`）。
- 开发环境能真正启动 AE2（此前 FML 直接拒绝加载）。

### 实现

- 1.21.1 的配置规范有两套 `defineInRange`：带 `Class` 的泛型版返回 `ConfigValue<V>`，**不带 `Class` 的 long 专版**才返回 `LongValue`。原先误用泛型版导致类型不匹配。
- `generateModMetadata` 的展开键是**白名单**，模板里出现的每个 `${...}` 都必须在构建脚本的属性表里声明，缺一个就整任务失败（不是警告）。
- AE2 硬依赖 `guideme ≥ 21.1.1`，本模组并不直接引用它的类，但运行期必须有；只在 `localRuntime` 里加，不下发为依赖。

### 边界

- 展开键白名单意味着：以后往 `neoforge.mods.toml` 模板里加任何新变量，都必须同步改构建脚本，否则构建直接失败（这个失败是好事，比产物里留 `${...}` 强）。

### 验证

- 编译：`./gradlew compileJava --offline` → 先 `2 个错误`（`ConfigValue<V>` 与 `LongValue` 不一致），修正后 `BUILD SUCCESSFUL`。
- 构建：`./gradlew build --offline` → 先 `Missing property (mod_authors) for Groovy template expansion`，补齐后 `BUILD SUCCESSFUL`。
- 启动：`runServer` → 先 `Mod ae2 requires guideme 21.1.1 or above / Currently, guideme is not installed`，补 jar 后进入下一条（见上一条目）。

---

## 2026-11-14 · 从 1.20.1 迁移到 1.21.1：全量源码、资源与三件套文档落地

| 项 | 内容 |
|----|------|
| 变更类型 | 建立（迁移） |
| 涉及范围 | 全部源码、全部资源、构建配置、元数据、文档 |
| 是否可回滚 | 是（真源在 Forge 1.20.1 仓库，随时可对照） |
| 已知风险 | 本项目目录**不是 git 仓库**，没有版本历史可回退；初始化 git 属不可逆动作，需开发者同意 |

### 效果

- 物质重组器与「物质重组器 2」在 1.21.1 上功能齐备：`long` 电量池 + 四面能量接口、顶面空手潜行开关、ME 网络在线状态驱动外观、合成旁路（扣料 + 投放产物 + 扣 FE）、Jade 四项显示、三段物品 tooltip、六项配置。
- 玩法语义与 1.20.1 一致；差异只在加载器 API（详见设计方案 §8 的映射表）。

### 实现

三类改动：

1. **纯逻辑层零改动**：计费口径、成本计算（含溢出饱和与向上取整）、接管资格判定、收支账本（含两条竞态回滚与短缺口量化）、FE 格式化——这些本来就不依赖 MC/加载器，原样迁移，仅把文档引用改成 1.21.1。
2. **AE2 接入层按 19.x 重构适配**：网格基类换成无库存的 `AENetworkedBlockEntity`（旧 `AENetworkInvBlockEntity` 已删除），并因此省掉两个空壳方法；方块交互从 `onActivated` 换成 `useWithoutItem`（空手由引擎保证）；持久化方法补 `HolderLookup.Provider` 形参；能量能力从「覆写 `getCapability`」改为「注册期 `RegisterCapabilitiesEvent` 绑定」。
3. **加载器层按 NeoForge 21.1 重写**：模组入口改成构造器参数注入；注册表用 `DeferredRegister.Blocks/Items` 专用入口（注册方补 `setId`）；`RegistryObject` 换 `DeferredHolder`；配置用 `ModConfigSpec` + `ModContainer#registerConfig`；资源路径改 1.21 单数目录；配方结果改 `id` 字段；元数据文件改 `neoforge.mods.toml` 并在其中声明 `[[mixins]]`。

依赖以 `libs/` 本地 jar 接入（AE2 19.2.19 / Jade 15.10.6 / guideme 21.1.22）：本机 `maven.neoforged.net` 不可达，Modrinth maven 的 artifact 布局随版本漂移，本地 jar 让构建完全离线可重现。

### 边界

- 音频、动画、光照等纯观感项未做任何调整（贴图与 `.mcmeta` 原样复制）。
- 1.20.1 曾有的 GTCEu 电网直连**没有带回**：真源在 2026-11-11 已移除该功能，迁移版与真源保持一致。
- 二代机与一代机共用模型/贴图（外观一致是有意的）。
- `wenxingtools:custom_spawner` 在 1.21.1 侧确实存在（同族仓库实测），但本仓库的冒烟环境不装它，配方靠条件码规避。

### 验证

- **构建**：`./gradlew build --offline` → `BUILD SUCCESSFUL in 6s`；产物 `build/libs/matterreassembler-NeoForge-1.21.1-1.0.0.jar`（59,963 字节）。
- **T0 产物核对**：
  - class 计数 27（含 `Ledger$Outcome`/`Ledger$Preflight`/`Ledger$Result`/`TakeoverDecision$Inputs` 等内部类），`examplemod` 命中 **0**。
  - 元数据：`modId="matterreassembler"`、`version="1.0.0"`、`displayName="Matter Reassembler"`、`authors="wenxing"`、`config="matterreassembler.mixins.json"`；依赖四条 `neoforge`(required) / `minecraft`(required) / `ae2`(required, `[19.2.0,20)`) / `jade`(optional, `[15.0.0,)`)。
  - 资源路径为 1.21 单数：`data/matterreassembler/recipe/`（2 个）、`data/matterreassembler/loot_table/blocks/`（2 个）；`matterreassembler.mixins.json` 在 jar 根。
- **T1 隔离探针**：`44 passed / 0 failed`。覆盖 FE 格式化（0/负数/千位/long 上限/单次上限文案）、成本口径（按次、按量、向上取整、乘法饱和、负输入）、资格判定全部八条否决路径、账本六条路径（成功、材料不足、产物塞满、抽料竞态回滚、插产物竞态回滚、同 key 净额）。探针按 AGENTS §4 在交付前删除。
  - 过程中出现过 1 次失败（`ledger.insertRace.materialsBack` 期望 10 实得 9）：根因是**探针夹具**把「插入竞态」设成对每次 insert 都生效，连回滚还料也被扣了一次。修夹具（竞态只触发一次）后全绿，断言未放宽。
- **T2 无头冒烟**：见上一条目（服务端就绪、Mixin 注入、0 ERROR）。
- **未验证（T3）**：全部实机行为。人工测点见设计方案 §11 第 13–16 项。
