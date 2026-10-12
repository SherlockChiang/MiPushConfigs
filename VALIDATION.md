# 验证记录 — 2026-10-12

## 已通过

- 静态配置扫描：18 个 JSON、65 个配置名，未解析引用、重复定义和引用循环均为零。直接引用和条件引用同时检查；原 16 个文件的扫描结果为九项缺失引用。
- Node.js `24.18.1`：`node --test tests/validate.test.mjs`，5 项测试，0 失败；包含缺失条件引用、循环、跨文件／JSON 对象内重复定义及 BOM 的负例。
- Temurin JDK `17.0.20+8`：`node tools/check-java-patterns.mjs`，46 条不同 Java 正则编译成功。检查匹配字段和 replace 表达式的模式，不把替换文本误当成正则。
- Framework `6609e6c8` 的真实 `Configurations`、`ConfigurationsLoader`、`PackageConfig.Walker` 和 `Lisp`：`MaintainedConfigurationSmokeTest`，18 项测试，0 失败、0 错误、0 跳过。
- Framework 测试使用 Gradle wrapper 8.5、JDK 17、`--no-daemon --max-workers=1 --console=plain`。通过外部 init 脚本注入测试，未改 Framework 的生产源码或构建文件，未安装 APK。

## 合成消息覆盖

好友会话和 HTTPS 头像、群语音／中文冒号／多行正文、未知资讯渠道、jumptype 字段边界负例、同名好友与文件／语音通知的身份区分、已有图标标记的群分类、特别关心、原始点击意图保留、群全体消息过滤、QA 渠道复用、昵称简化、可选聊天意图重整、后台唤醒白名单、同标题／单条／直接打开／运营过滤的实际效果、企业微信来电分类、默认无额外唤醒／自动打开／全局通知组，以及非 MessagingStyle 的原生群排版回退。

测试使用合成 Thrift 消息及虚拟包名，Android `TextUtils` 在 JVM 中模拟；不会联系推送服务器、加载网络头像或发布手机通知。此验证覆盖配置解析和整形结果，不代替 QQ 上游下发、系统通知显示、深链点击及实际电量测试。

## 兼容修补

最初的 JVM 引擎验证发现六个源文件带 UTF-8 BOM，所用 org.json 加载库拒绝解析。配置已全部格式化为无 BOM UTF-8，随后全部场景通过。静态校验现在会拒绝带 BOM 的配置，避免掩盖库间兼容差异。

## 使用和回滚

将 `configs/` 的 18 个 JSON 作为完整目录导入；先保留原目录，回滚时在应用设置中重新选择原目录。本次未自动切换手机配置目录，最终外观和真实通知到达仍由用户导入后验收。
