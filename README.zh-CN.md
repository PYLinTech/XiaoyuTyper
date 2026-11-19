# XiaoyuTyper

[English](README.md) | 简体中文

XiaoyuTyper 是一款离线 Android 拼音输入法（IME），基于 Jetpack Compose
与原生 SQLite 词库构建。它只包含输入法本身：没有启动器 Activity，也没有
设置页或测试页，请在 Android 的输入法设置中启用并选择它。当前为预发布版本
0.1.0。

## 下载

APK 下载：<https://ph.pylin.cn/s/kzvovh4uok2c6tjpml3x6xloki>

## 功能特性

- 基于原生 `InputMethodService` 的 Jetpack Compose 键盘。
- 中文拼音输入，内置 1,012,871 条词条与 843,967 个拼音键，另支持英文模式。
- 支持单字与二至三字词的简拼输入（例如 `nh` → 你好，`zg` 或 `zhg` →
  中国），由专用简拼索引支持。
- 日常键盘：候选栏、Shift 与大小写锁定、数字与符号页、更多符号、逗号句号
  键、空格上屏、退格长按连删。
- 安全键盘（密码输入框：`TYPE_TEXT_VARIATION_PASSWORD`、可见/网页密码、
  数字密码）：仅英文输入、符号页、下一步/完成。
- 明暗两套配色随系统主题自动切换。
- 竖屏停靠：全宽，28 dp 组合栏，内容高度 `max(315 dp, 屏幕高度 36%)`，
  底部栏高度 `max(导航栏 inset, 48 dp)`。
- 横屏浮动面板：宽度上限 620 dp、高度上限 310 dp 且不超过横屏高度 55%，
  2:1 比例，8 dp 圆角，22 dp 顶部带可拖动握把，底部留白 12 dp。输入法上报
  零 inset，以浮层方式覆盖宿主应用而非改变其尺寸；位置会被记忆，并在恢复
  键盘或显示变化时重新夹取到安全区域。
- 首次启动解压随包 XZ 词库，并在 `Application.onCreate` 中预热，先于键盘
  UI 创建。
- 所有界面文案来自 Android 字符串资源；当前仅内置简体中文（`zh-CN`），
  AGP 会生成 locale 配置，可通过语言资源目录添加翻译。
- 不申请网络权限，完全离线可用。

## 环境要求

- JDK 17 或更高版本。
- Android SDK 37（`compileSdk = 37`、`minSdk = 26`、`targetSdk = 37`）。
- 较新的 Android Studio，或使用 Gradle Wrapper。

调试包约 33 MB，精简后的发布包约 23 MB，体积几乎全部来自压缩词库。

## 构建

调试 APK：

```bash
./gradlew :app:assembleDebug
```

输出为 `app/build/outputs/apk/debug/app-debug.apk`。

签名发布 APK：

在项目根目录创建本地 `keystore.properties`（不属于源码树）：

```properties
storeFile=../release.jks
storePassword=your-store-password
keyAlias=your-key-alias
keyPassword=your-key-password
storeType=PKCS12
```

然后执行：

```bash
./gradlew :app:assembleRelease
```

输出为 `app/build/outputs/apk/release/app-release.apk`。发布构建启用 R8 代码
压缩、资源压缩与仅中文资源过滤，并使用 v2 + v3 签名。缺少
`keystore.properties` 时会跳过签名，仍可构建未签名包。

## 安装与启用

安装 APK：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

在 **设置 > 系统 > 语言与输入法 > 屏幕键盘 > 管理屏幕键盘** 中启用并选择
XiaoyuTyper，或使用 adb：

```bash
adb shell ime enable cn.pylin.xiaoyutyper/.XiaoyuInputMethodService
adb shell ime set cn.pylin.xiaoyutyper/.XiaoyuInputMethodService
```

应用没有启动器 Activity，桌面没有可点击的图标，请从 Android 输入法设置中
管理。

## 词库

词库由 [rime-frost](https://github.com/gaboolic/rime-frost) 生成，对应提交
`60e84477cadd07db60bd29a852212f075f0814a9`（2026-11-18，GPL-3.0）。保留
全部汉字单字、权重 ≥ 21 的核心多字词，以及精选分类词库（成语、历史、地理、
地名、体育、饮食、游戏、医药、文学、人名、动物、产业、数学、象棋、电脑、
媒体、音乐、输入法及其他）。排除 `composite` 词库。每个键的候选上限为 80。

生成的数据库包含 1,012,871 个词与 843,967 个键，SQLite 大小 67.2 MB；另含
129,997 条简拼条目，覆盖 11,102 个简拼键，支持单字与二至三字词。数据库以
23 MB 的 XZ 资源随包发布：`app/src/main/assets/pinyin-v1.db.xz`，首次使用时
解压到 `filesDir/pinyin-v1.db` 并以只读方式打开，旧的版本化数据库文件会
自动清理。

重新生成词库：

```bash
python3 tools/build_pinyin_db.py
```

下载缓存位于 `tools/.cache/`。设置 `RIME_FROST_SOURCE_DIR` 指向本地
rime-frost 检出的目录可离线构建；`MIN_CORE_MULTI_WEIGHT`、`MAX_PER_KEY`、
`RIME_FROST_REF` 可通过环境变量覆盖。

## 引擎

Kotlin 引擎是 pinyin-ime 排序规则（MIT）的行为移植：

- 先按最长匹配前缀，再按全拼精确匹配、简拼精确匹配、词长与全局词频排序。
- 全拼精确匹配与普通前缀匹配分开记录，并获得排序加权。
- 简拼精确匹配单独标记并加权，覆盖单字与短词。
- 在完整音节长度输入下，单字与两字词获得优先；非精确的三字及以上词在短
  输入下被降权，而精确匹配与简拼不受影响。
- 候选上限 300，LRU 缓存 200 条。
- 通过 `entries(key, word_id)` 进行前缀到词的查找，并对一、二位字母前缀
  使用预计算的 `prefix_top(prefix, word_id, freq)` 表。

## 工程结构

- `app/src/main/java/cn/pylin/xiaoyutyper/XiaoyuApplication.kt`：进程启动与
  词库预热。
- `app/src/main/java/cn/pylin/xiaoyutyper/XiaoyuInputMethodService.kt`：输入法
  服务、窗口配置与输入连接处理。
- `app/src/main/java/cn/pylin/xiaoyutyper/ime/`：键盘状态控制器。
- `app/src/main/java/cn/pylin/xiaoyutyper/pinyin/`：SQLite 词库、引擎与仓库。
- `app/src/main/java/cn/pylin/xiaoyutyper/ui/keyboard/`：日常与安全键盘的
  Compose 实现。
- `app/src/main/assets/pinyin-v1.db.xz`：预置词库与简拼索引。
- `app/src/main/assets/licenses/`：第三方许可证文本。
- `app/src/main/res/values/strings.xml`：本地化界面文案。
- `app/src/main/res/resources.properties`：默认资源语言（`zh-CN`）。
- `app/src/main/res/xml/method.xml`：输入法元数据与子类型。
- `tools/build_pinyin_db.py`：词库构建脚本。

## 检查

```bash
./gradlew :app:lintDebug
```

没有单元测试或仪器测试源码。

## 许可证与声明

XiaoyuTyper 源码以 MIT 许可证发布，见 [LICENSE](LICENSE)。

第三方组件：

- **rime-frost** 词库数据（GPL-3.0）。随包 SQLite 词库是由
  `tools/build_pinyin_db.py` 生成的修改衍生数据，仍按 GPL-3.0 授权。
- **pinyin-ime** 引擎行为（MIT）。候选生成与排序规则为其 Kotlin 移植，
  未使用其 UI 代码。
- **LKWorkMate** 网页键盘：布局与交互参考，未随包发布任何网页资源。
- **Remix Icon** v4.6.0（Apache-2.0）：键盘图标。
- **XZ for Java**（公有领域）：首次启动词库解压。
- **AndroidX、Jetpack Compose、Kotlin、Kotlin Coroutines**（Apache-2.0）。

完整第三方许可证文本随包位于 `app/src/main/assets/licenses/`。
