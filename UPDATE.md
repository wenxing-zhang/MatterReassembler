# 更新记录（MatterReassembler · Forge 1.20.1）

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

## 2026-11-10 · 依赖接入：AE2 15.4.10 与 Jade 11.13.3 通道打通，mods.toml 声明三方依赖，附录锚点对产物核对

| 项 | 内容 |
|----|------|
| 变更类型 | 新增（构建基建；Wave 0） |
| 涉及范围 | 构建脚本仓库与依赖、版本属性、元数据依赖声明、设计方案附录 A/B 与实施路线 |
| 是否可回滚 | 是（移除仓库与三条依赖即可回到纯骨架） |
| 已知风险 | AE2 版本被钉死在 15.4.10；`mods.toml` 声明 `ae2` 为硬依赖后，无 AE2 的环境将拒绝加载（符合设计） |

### 效果

- 工程现在**真的能引用 AE2 与 Jade 的类型**：编译期有 AE2 全量 API 与 Jade 插件接口，运行期只额外带 Jade。
- 玩家侧安装约束由元数据表达：没有 AE2 就不加载；没有 Jade 或 GTMThings 照常加载，功能各自降级。
- DESIGN 附录里三条「待核」项被实测销号，实现阶段不必再猜 API 形状。

### 实现

- AE2 走 `modmaven.dev`（官方坐标、带完整 POM），坐标 `appeng:appliedenergistics2-forge:15.4.10`，经 `fg.deobf` 重映射到本项目映射。
- Jade 走 Modrinth 的 maven 镜像（无鉴权），坐标 `maven.modrinth:jade:11.13.3+forge`；`compileOnly` + `runtimeOnly`，玩家缺失时静默降级。
- **GTMThings 刻意不引入坐标**：其覆盖板 API 无公开资料，本项目用反射探测（DESIGN §8.2）；实机联调时把 jar 放进 `run/mods`，避免为了一个不确定的 API 绑定一个不确定的版本。
- 两个仓库都用 `content { includeGroup ... }` 限定组，避免把无关构件从第三方仓库解析。
- 版本属性集中在 `gradle.properties`（`ae2_version`、`ae2_version_range`、`jade_version`），元数据里的 AE2 版本范围经 `processResources` 展开，不在 `mods.toml` 里写死。

### 边界

- 依赖只有一个**可用**版本被验证（15.4.10）；同系列其它版本未试，升级时按 AGENTS §2 走确认。
- Jade 只验证了「能解析」；插件接口的实际使用属 Wave 5。
- 未引入 Access Transformer，也未引入任何 Mixin 基础设施（仍是 Wave 3）。

### 验证

- `verify: 依赖解析 | .\gradlew.bat dependencies --configuration compileClasspath | exit=0 | 命中: appeng:appliedenergistics2-forge:15.4.10_mapped_official_1.20.1、maven.modrinth:jade:11.13.3+forge_mapped_official_1.20.1`
- `verify: 重映射产物 | 缓存目录 | 命中: deobf_dependencies/appeng/appliedenergistics2-forge/15.4.10_mapped_official_1.20.1/…jar 存在`
- `verify: T0 | javap -p -classpath <AE2 产物> appeng.api.networking.crafting.ICraftingService | exit=0 | 命中: beginCraftingCalculation(Level, ICraftingSimulationRequester, AEKey, long, CalculationStrategy) → Future<ICraftingPlan>；submitJob(ICraftingPlan, ICraftingRequester, ICraftingCPU, boolean, IActionSource) → ICraftingSubmitResult（描述符内零 MC 类型）`
- `verify: T0 | javap -p appeng.api.networking.IGrid | 命中: public abstract <T> Set<T> getMachines(Class<T>)`
- `verify: T0 | javap -p appeng.me.helpers.IGridConnectedBlockEntity | 命中: abstract getMainNode() / saveChanges()；default getGridConnectableSides(BlockOrientation)`
- `verify: T0 | javap -p appeng.blockentity.AEBaseInvBlockEntity | 命中: abstract getInternalInventory() / onChangeInventory(InternalInventory,int)`
- `verify: T0 | javap -p appeng.crafting.CraftingPlan | 命中: final class … extends Record implements ICraftingPlan，8 个分量与规范构造器`
- `verify: 构建 | .\gradlew.bat build | exit=0 | BUILD SUCCESSFUL in 10s（7 actionable tasks）`
- `verify: T0 | unzip -p build\libs\*.jar META-INF/mods.toml | 命中: modId="ae2" mandatory=true、modId="jade" mandatory=false、modId="gtmthings" mandatory=false`
- **未实机验证**（T2/T3）：无方块、无可加载内容，`runServer` 冒烟无意义；人工测点留到 Wave 1。

---

## 2026-11-10 · 修正：模组描述中文乱码；订正协作者规范里的 Mixin 口径

| 项 | 内容 |
|----|------|
| 变更类型 | 修正（交付缺陷）+ 文档订正 |
| 涉及范围 | 构建属性到产物的文本编码链；协作者规范的环境表与关联检查表 |
| 是否可回滚 | 是 |
| 已知风险 | 无（纯文本修正）；将来在 `gradle.properties` 直接写中文会再次复现，靠新增的检查行兜住 |

### 效果

- 模组列表里的描述恢复为中文正常显示（此前是 `é  å  æ ¹å` 这类乱码）。
- 协作者规范不再声称「Mixin 已有」——实际注入基础设施（构建脚本配置 + 注入配置清单）尚未接入，只是设计上计划有。

### 实现

- 描述文本改为 `\uXXXX` 转义写法：属性文件按 ISO-8859-1 读取时，转义序列仍能被正确还原成字符，绕开编码链路本身。
- 关联检查表新增一行：「构建属性里不得直接写非 ASCII，中文一律 `\uXXXX`；核对时看产物字节而非终端显示」。
- 环境表的 Mixin 行改为「计划有 / 基础设施尚未接入」，并附实测口径（构建脚本里 mixin 配置 0 处、无注入配置清单）。

### 边界

- 只改描述与文档，不动 `build.gradle` 的 `expand` 配置：该配置按 UTF-8 写出是合理的，问题在读属性这一端。
- `-Dfile.encoding=UTF-8` 不是解法：属性文件的读取字符集由 `Properties` 语义固定为 ISO-8859-1，改 JVM 默认字符集不影响它。
- 不影响加载与运行：此前乱码只出现在模组列表文本上，`modId`/版本/作者等 ASCII 字段一直是正确的。

### 验证

- `verify: 构建 | .\gradlew.bat build | exit=0 | BUILD SUCCESSFUL in 10s（7 actionable tasks，4 executed）`
- `verify: T0 | unzip -p build\libs\matterreassembler-1.0.0.jar META-INF/mods.toml | sed -n '/^description=/,+2p' | xxd` → 命中 `e9 9984 e58aa0 …`（正确 UTF-8；修正前同一位置为 `c3 a9 c299 c284 …`）
- `verify: T0 | 同命令按 UTF-8 视读` → 命中 `description='''AE2 附加方块：物质重组器（Matter Reassembler）。` 与 `用 FE 换取跳过样板执行的合成旁路。需要 AE2 15.4.x。'''`
- **未实机验证**（T3）：进游戏打开模组列表看描述。障碍：需图形客户端。人工测点：① 模组列表搜 Matter Reassembler，描述中文正常、无乱码；② 单人世界能正常进入（mods.toml 多行字符串解析无异常）。

---

## 2026-11-10 · 删净 MDK 示例、建立主类骨架、仓库主分支落地；计费口径定为按次

| 项 | 内容 |
|----|------|
| 变更类型 | 清理 + 建立 |
| 涉及范围 | 工程骨架、构建元数据、版本控制、计费默认值 |
| 是否可回滚 | 是（恢复到 MDK 模板即可；示例文件可从 `forge-1.20.1-47.4.0-mdk.zip` 取回） |
| 已知风险 | 依赖仍未接入、功能面为零；「能编译」只证明骨架可编译 |

### 效果

- 工程不再是 Forge MDK 模板：示例方块/物品/创造栏/示例配置全部移除，`examplemod` 标识在源码、构建脚本、元数据里**一处不剩**。
- 主类换成 `com.wenxing.matterreassembler.MatterReassembler`（`@Mod("matterreassembler")`），带日志与两条注册表（方块、物品），**不含任何 AE2 引用**——依赖未落地前工程仍可编译。
- 版本控制从零建立，主分支名 `MatterReassembler-Forge-1.20.1`，远程指向 `git@github.com:wenxing-zhang/MatterReassembler.git`。
- 计费口径定稿：**按次**（一次接管固定 100,000 FE，与请求数量无关）；按量模式保留为配置项。

### 实现

- 删除示例主类与示例配置，连同 `com\example\examplemod` 目录一并清除（不留空包）。
- 构建元数据改名：`mod_id`、`mod_name`、`mod_group_id`、`mod_authors`、`mod_description`；元数据文件里指向示例 logo 的引用改为本模组名。
- 主类只做注册表接线，AE2 相关注册项留到 Wave 1，避免「依赖没接好、代码先引用」的死结。
- 计费参数拆成两个键：「每次接管成本」与「每单位成本」，由计费模式决定用哪个，默认走前者。

### 边界

- 现在**没有**方块、没有方块实体、没有资源文件、没有配方，也没有 AE2/Jade/兼容依赖——本条目只交付骨架与仓库。
- 主类里刻意不加示例注册项：注册名一旦发布就要稳定，宁缺勿滥。
- 版本控制走 SSH：本机 `https://github.com` 的 443 不通，HTTPS 远端不可用（已登记为 AGENTS §8 R8）。

### 验证

- `verify: T0 | grep -rn "examplemod" --include=*.java --include=*.gradle --include=*.toml --include=*.properties | exit=1（无匹配） | 命中: 0 处`
- `verify: T0 | 源码树 | 仅 src\main\java\com\wenxing\matterreassembler\MatterReassembler.java + 两个资源文件 | 命中: com\example\ 目录已不存在`
- `verify: 构建 | .\gradlew.bat compileJava | exit=0 | BUILD SUCCESSFUL in 14s（1 actionable task）`
- **未实机验证**：骨架无方块，无法放置/交互；功能测点见后续 Wave 条目的 T3 登记。

---

## 2026-11-10 · 纠正：合成接管改为「提交入口自行结算」，否决空样板集方案

| 项 | 内容 |
|----|------|
| 变更类型 | 修正（设计层，未写代码） |
| 涉及范围 | 合成接管的拦截点、材料结算与产物投放责任归属 |
| 是否可回滚 | 是（仅文档；代码尚未存在） |
| 已知风险 | 接管成功后 CPU 面板不出现任务；机器/自动化请求不在接管范围内 |

### 效果

- 合成接管的语义定为：**扣材料 + 扣电 + 直接投放产物**，不创建任何 CPU 任务。
- 拦截时机从「合成计算完成时」改到「玩家确认提交时」——计算结束只用于展示真实材料与字节数，玩家点取消不会产生任何扣费。
- 原提示词里「把 `patternTimes` 置空的自定义计划交回 CPU」这一做法**被否决**。

### 实现

- 在合成服务的提交入口做一次入口注入（可取消）；命中接管条件时短路原版流程，由本模组完成结算，并返回「成功、无链接」。
- 结算顺序固定为：模拟扣料 → 模拟投放产物 → 实扣材料 → 实投产物 → 扣电；任一步失败**不取消原版流程**，让原版 CPU 路径照常处理，保证「要么完整完成、要么完全没发生」。
- 反应式入口（放置/挖掘/状态查询）不做任何主动推拉能量。

### 边界

- 材料不足、产物插不进网络、电量不足、计划本身是模拟（缺料）→ 一律交还原版。
- 需要 `ICraftingRequester` 链接的机器请求不接管：它们依赖链接与请求清单，替代会破坏其状态机。
- 原版确认界面仍要求「选中 CPU 存储 ≥ 计划字节」，因此没有可用 CPU 时无法发起请求，也就无法走旁路；提供配置放宽该前置（纯 UI 放宽，不影响结算）。

### 验证

- **未验证**（本轮仅文档，无代码、无构建）。
- 否决依据：读了目标版本 AE2 源码——提交校验只查「已有任务 / 节点不 active / 字节不足」三项，随后**立即按计划的材料清单真实扣料进 CPU**；任务对象从样板集构建执行清单，完成出口只有一个「产物入账使剩余量归零」。空样板集 ⇒ 没有样板可执行 ⇒ 剩余量永不归零 ⇒ **任务永久挂起、材料滞留 CPU**。故「不报错」不等于「可用」。
- 建议测点（Wave 3 后执行，需真实客户端）：① 木棍合成走旁路：材料减、产物即到、电按单位扣、CPU 面板无任务、无挂起 CPU；② 电量不足 → 原版出产物；③ 关闭开关 → 原版；④ 缺一项材料 → 原版报缺料；⑤ 网络塞满 → 原版，材料不丢；⑥ 连点两次 → 不重复扣电。

---

## 2026-11-10 · 立档：三件套规范落地，登记工程现状

| 项 | 内容 |
|----|------|
| 变更类型 | 建立 |
| 涉及范围 | 项目文档骨架（协作者规范 / 设计方案 / 更新记录）与工程现状盘点 |
| 是否可回滚 | 是（删三份文档即可） |
| 已知风险 | 依赖尚未接入；仓库尚未初始化版本控制；文档中的外部 API 锚点来自上游源码快照，须在 Wave 0 用实际依赖产物复核 |

### 效果

- 三份文档分工固定：**协作者规范**管怎么干活（环境锁定、关联检查、结构性验收分级、失败重试上限、工程红线、已知风险裁定）；**设计方案**管功能语义与实施分波；**更新记录**管流水。
- 工程现状被明确登记，后续任何「可交付」宣称都以 Wave 0 完成为前提。

### 实现

- 协作者规范：环境锁定表 + 「改了什么必须再看什么」的关联检查表 + 四级结构性验收（产物核对 / 隔离逻辑 / 无头冒烟 / 真实客户端）+ 失败两次即停手 + 可验证性前置门禁（核心断言必须至少落在前两级）。
- 设计方案：功能语义（方块外观、能量契约、开关交互、合成接管、软依赖兼容、信息显示、配置）+ 「空样板集会被接受吗」的完整证据链与四方案比较 + 可验证性分配表（17 条断言，真实客户端 5 条）+ 五个实施波次与每波验证命令。
- 更新记录：本文件，只追加。

### 边界

- 不写任何代码，不改构建脚本、不改依赖、不初始化版本控制——均需开发者确认后另立条目。
- 外部 API 锚点（AE2 / 信息显示插件）仅用于签名核对，不作为「运行时行为如何」的证据。

### 验证

- **未验证**（纯文档，无构建、无测试）。
- 事实性登记（本轮实测）：工程为未改动的 Forge MDK 模板（`mod_id` 仍为模板值，只有示例主类与示例配置）；构建脚本内无 AE2 / Jade / 第三方兼容依赖；**不是 git 仓库**（`git status` 报 `not a git repository`）；无测试目录。
