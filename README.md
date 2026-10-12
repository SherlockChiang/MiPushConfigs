# MiPush 配置维护版

本配置包来源于**冰梦（冰之梦殇520 / Bzmshang）**整理和提供的 MiPush 配置，取自其 [MiPush Framework 使用指南](https://blog.bzmshang.top/MiPush-Framework_User-Guide)及指南中的配置下载入口。原作者和来源属于冰梦；本仓库由 **SherlockChiang** 维护，提供缺失依赖修补、规则调整和校验工具。

指南也引用了 [NihilityT/MiPushConfigurations](https://github.com/NihilityT/MiPushConfigurations)。本次补全中的全局可选规则、QA 渠道复用和群消息原生排版参考该仓库，具体来源见 [NOTICE.md](NOTICE.md)。请在再分发时保留原作者署名、来源链接和本仓库的修改说明。

## QQ 配置可能失效

在当前测试环境的新版 **QQ 9.3.70** 中，我们观察到：刚开机时收到 MiPush 消息，随后 QQ 自身连接建立，普通消息由 QQ 自己发布。新版 QQ 可能优先使用自己的普通通知，而不显示 MiPush 类型通知。

**这些 JSON 只处理进入 MiPush Framework 的消息，无法改变 QQ 自己发布的普通通知，也不能强制 QQ 服务器使用 MiPush。** 因而好友／群聊分类、会话头像、MessagingStyle 和跳转规则可能不生效。推送记录没有新增 QQ 消息，不等于 QQ 消息没有收到。

可通过通知来源区分两条路径：MiPush 转发通知的 `opPkg` 为 `com.xiaomi.xmsf`，本包的好友／群聊渠道为 `ch_com.tencent.mobileqq_contact_chat` / `ch_com.tencent.mobileqq_group_chat`；本次观察到的 QQ 普通通知 `opPkg` 为 `com.tencent.mobileqq`，渠道为 `CHANNEL_ID_SHOW_BADGE`。具体渠道可能随版本变化。

## 安装

1. 下载本仓库，将 **`configs/` 内的全部 18 个 JSON 文件**复制到手机的一个新目录，例如 `Documents/MiPushConfigs`。不要只复制 QQ 主配置，也不要将整个仓库作为配置目录。
2. 在推送服务的设置中选择这个目录，查看配置诊断，确认“缺失引用”为零。
3. 用真实好友和群聊消息检查效果。配置测试通过代表规则可以解析和执行，通知外观、QQ 上游下发及 Android 点击权限仍需实际设备验证。

保留旧目录便于回滚；遇到问题，在推送服务中重新选择旧配置目录即可。更新时整包替换到新目录，避免旧配置里的同名定义覆盖新规则。

默认配置保留 QQ 会话样式、个人／群组分组、头像和消息标识，以及企业微信支持。默认保留推送中的原始点击意图，减少因改写为通用聊天链接而丢失跳转参数的情况。好友消息不会仅因显示名称相同就被合并。

## 可选功能

开关集中在 [`configs/0_维护版_可选开关.json`](configs/0_维护版_可选开关.json)。每项 `match.packageName` 默认是 **`(?!)`**，表示不匹配任何应用，功能关闭。将某项改为精确包名正则即可开启，例如：

```json
"开关-QQ昵称简化": [
  { "match": { "packageName": "^com\\.tencent\\.mobileqq$" }, "stop": false }
]
```

多个应用可写成 `^(?:com\.example\.one|com\.example\.two)$`；写入 JSON 时需将反斜杠再转义为 `\\`。只调整需要的开关。

| 开关 | 开启后的行为 |
| --- | --- |
| 同标题会话 | 同标题通知使用同一通知 ID，可能覆盖原有消息 |
| 过滤运营消息 | 对选定应用执行旧配置的运营消息过滤；“没有 channel_id”只是旧规则的判断，可能过滤正常通知 |
| 后台唤醒应用 | 添加 `__awake=true`，可增加后台启动和耗电 |
| 只显示一条消息 | 一个应用使用一个通知 ID，新通知覆盖旧通知 |
| 直接打开意图 | 设置 `use_clicked_activity=true`；是否能打开原始目的页还取决于 Framework 和目标应用 |
| QQ渠道复用 | 使用 QA 的 `QQ_Friend` / `QQ_Group` / `QQ_Friend_Special` 渠道，需设备实际存在对应渠道 |
| QQ全体消息过滤 | 过滤群聊正文含 `@全体成员` 的消息，保留好友消息 |
| QQ昵称简化 | 群昵称含 `\|`、`｜`、`·` 时取首段，可能改变昵称含义 |
| QQ意图重整 | 将旧 QQ 推送聊天意图改为 `mqqwpa://`，可能丢失原始参数 |
| 来电自动打开 | 对 QQ／企业微信已定义的来电规则执行 `wake\|open` |
| 关键词亮屏 | QQ 特别关心或正文含“有急事／收到请回复”时点亮屏幕 |
| 全局同应用成组 | 为选定应用设置全局通知组；默认让会话配置自行分组 |

默认关闭自动唤醒、自动打开和关键词亮屏，减少配置触发的额外后台活动；这不改变推送服务自身的心跳、连接或注册策略。副标题清理和消息标识规则仍按原配置执行。

## 九项缺失引用修补

原来的 16 个配置文件包含九项悬空引用。新包提供实际规则，未用空数组冒充依赖。带开关的规则需明确启用；“群消息整形-原生通知样式”仅用于非 MessagingStyle、且未经过经典排版的通知，避免重复修改。

| 缺失引用 | 修补方式 |
| --- | --- |
| `$-将标题相同的通知视为同一会话` | 精确应用开关 + 既有同标题规则 |
| `$-干掉运营消息` | 精确应用开关 + 既有过滤规则 |
| `$-收到消息后台唤醒应用` | 精确应用开关 + `__awake` 标记 |
| `$-只显示一条消息` | 精确应用开关 + 包名哈希通知 ID |
| `$-直接打开意图` | 精确应用开关 + 既有直接打开规则 |
| `含间隔符的群昵称提取首词` | 精确应用开关 + 群昵称解析和首尾空白清理 |
| `群消息整形-原生通知样式` | 非 MessagingStyle、未排版通知的群名副标题规则 |
| `QQ-复用QA通知渠道` | 精确应用开关 + 三种已知 QA 渠道映射 |
| `QQ-屏蔽全体消息` | 精确应用开关 + 群聊限定的关键词过滤 |

具体修正还包括：转义并限定 `jumptype` 字段边界；群聊的文件／语音消息不会被个人消息回退规则覆盖；只有明确个人渠道才标记为好友，未知／资讯渠道保留原类型；群聊解析支持中文冒号和换行；头像链接使用 HTTPS；清理空白的正则能移除多个尾部空格。

## 验证

校验工具无需 npm 依赖，需要 Node.js 20+；Java 正则检查需要 JDK 17：

```sh
node tools/validate.mjs
node --test tests/validate.test.mjs
node tools/check-java-patterns.mjs
```

当前版本已通过 5 项校验测试、46 条 Java 正则编译检查，以及 Framework 真实配置引擎的 18 项合成消息测试，详见 [VALIDATION.md](VALIDATION.md)。

`validate.mjs` 检查文件无 BOM、JSON 重复字段、跨文件重复配置名、直接／条件引用和引用循环。文件统一为无 BOM 的 UTF-8，避免不同 JSON 加载库的兼容差异。`check-java-patterns.mjs` 使用 Java 的正则引擎编译匹配表达式，而非用 JavaScript 正则替代 Android 的规则。

另提供 Framework 真实配置引擎的合成消息测试。需要已配置好 JDK 17 和 Android SDK 的 [MiPushFramework](https://github.com/SherlockChiang/MiPushFramework) 本地仓库。在 Framework 目录中执行（路径按本机修改）：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:ANDROID_SDK_ROOT = 'C:\path\to\Android\Sdk'
.\gradlew.bat --stop
.\gradlew.bat :push:testNormalDebugUnitTest `
  --tests com.xiaomi.xmsf.push.utils.MaintainedConfigurationSmokeTest `
  -I 'C:\path\to\MiPushConfigs\tools\test-framework.init.gradle' `
  '-Dmipush.configRepository=C:\path\to\MiPushConfigs' `
  --no-daemon --max-workers=1 --console=plain `
  '-Dkotlin.compiler.execution.strategy=in-process'
```

测试覆盖好友、群聊语音、中文冒号／多行正文、未知渠道、字段误匹配、同名好友、特别关心，以及可选过滤、QA 渠道、昵称、意图和后台唤醒。所有样本均为合成数据，测试不会发送通知或访问真实头像地址。

自定义通知图标是独立资源，可从 [fankes/AndroidNotifyIconAdapt](https://github.com/fankes/AndroidNotifyIconAdapt) 获取并遵守其署名与授权；本仓库不捆绑本地 `icon/` 的第三方图片库。
