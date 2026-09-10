# 热榜聚合 (HotRank) — MVP

6 站热搜合成一条流的安卓原生 App。Kotlin + Jetpack Compose。

## 一、MVP 功能边界

**做了：**
- 混合聚合流：微博 / 知乎 / 百度 / 头条 / 抖音 / B站，按「各平台第 N 名」交错排列
- 平台筛选 Chip（全部 / 单平台）
- 下拉刷新 + 顶栏手动刷新
- 10 分钟本地缓存；断网或单站挂掉时降级到旧缓存，不白屏
- 点击条目跳系统浏览器
- 深色模式跟随系统

**故意没做（留给 v2）：** 搜索、收藏、关键词订阅推送、分享卡片、个性化排序、登录、桌面小组件。
理由：这些都不是「用户愿不愿意每天打开它」的答案，先让打开这件事成立。

## 二、数据源（已实测）

| 平台 | 主源 uapis.cn | 备用源 60s.viki.moe |
|---|---|---|
| 微博 | ✅ 50 条 | ✅ 50 条 |
| 知乎 | ✅ 30 条 | ⚠️ 时常返 0 条 |
| 百度 | ✅ 51 条 | ❌ |
| 头条 | ✅ 50 条（无热度值） | ✅ 50 条 |
| 抖音 | ✅ 50 条 | ❌ |
| B站 | ✅ 100 条 | ❌ |

- 主源：`https://uapis.cn/api/v1/misc/hotboard?type={weibo|zhihu|baidu|toutiao|douyin|bilibili}`
- 备用源：`https://60s.viki.moe/v2/{weibo|zhihu|toutiao}`
- 两个源都不需要 API Key。**但都是第三方公益接口，随时可能变**，所以 `HotApi` 里做了「主源失败自动走备用源，全失败退回旧缓存」三层兜底。
- 想加平台：`Platform` 枚举加一行，确认接口支持该 type 即可，其余代码不用动。

## 三、几个刻意的设计选择

1. **不做热度混排。** 微博 183 万和知乎「3504 万热度」不是一个量纲，强行归一排序出来的名次看着科学其实是假的。改成按名次交错，语义诚实。
2. **不做 WebView。** 点击直接 `ACTION_VIEW` 交给系统浏览器，省掉一整套 WebView 适配和内容合规问题。
3. **不用 Hilt / Room。** 一个 GET 接口 + 一个本地缓存，上依赖注入和 ORM 是负收益。缓存用 SharedPreferences 存归一化后的 JSON。
4. **并发拉取。** 6 个平台用 `async` 并发，整体耗时约等于最慢的那一个（实测 < 1s）。
5. **单平台封顶 50 条、混合流每平台取前 20。** B站一次返 100 条，全量交错会变成 300+ 条流水账。

## 四、怎么在手机上把 APK 弄出来

**不用电脑，不用 Android Studio。** 项目已内置 gradle wrapper（`gradlew` + `gradle-wrapper.jar`），拿过去就能跑。

### 路径 A：云端构建（推荐，最省事）

代码推到 GitHub，让 GitHub 的机器编译，手机只负责下载成品。

```sh
# 1. 手机上装 Termux（F-Droid 或 GitHub Releases 都有），然后：
pkg install -y git

# 2. 解压本项目到任意目录，进到项目根目录
cd ~/HotRank

# 3. 推到你的 GitHub 仓库（先在 GitHub 网页上建一个空仓库，别勾 README）
git init -b main
git add .
git commit -m "init"
git remote add origin https://github.com/你的用户名/你的仓库.git
git push -u origin main        # 提示输密码时，粘贴 Personal Access Token（不是账号密码）
```

推上去之后 `.github/workflows/build-apk.yml` 会自动跑，约 3~5 分钟。之后手机浏览器打开：

```
https://github.com/你的用户名/你的仓库/releases/download/latest-build/app-debug.apk
```

**这个链接点一下就下载，不用登录**，下载完直接安装（首次要在系统设置里允许「安装未知来源应用」）。改了代码重新 push，同一个链接就更新，不用换地址。

> 没建过 PAT 的话：GitHub 网页 → Settings → Developer settings → Personal access tokens → Fine-grained tokens，权限只勾 `Contents: Read and write` 就够。

### 路径 B：Termux 本地构建（完全离线，但需要一起调错）

不想用 GitHub 就走这条。**我已经把能预判的坑都写进脚本了**，但这条路径我没法在沙箱里实测，可能会卡在某个细节上：

```sh
pkg install -y git
cd ~/HotRank
sh tools/build-termux.sh
```

脚本干了这几件事（都是 ARM 手机上绕不开的）：
1. 装 JDK 17 + Termux 的 **aarch64 版 aapt2**
2. 直接下 `platform-34` 包取出 `android.jar`（这个 jar 与架构无关，能用）
3. **用 Termux 的 aapt2 顶掉 Google 的 x86_64 版**（Google 只发 x86_64，在 ARM 上根本跑不起来，这是最大的坑）
4. 手工搭出 AGP 认的 SDK 目录结构 + 许可文件
5. `./gradlew assembleDebug`

失败了就把报错整段发我，我来改脚本。

### 验证过的构建环境

AGP 8.5.2 / Kotlin 1.9.24 / Compose BOM 2024.09.00 / compileSdk 34 / **minSdk 26** / JDK 17 / Gradle 8.7
Gradle 仓库与 distributionUrl 都指向国内镜像（阿里云 + 腾讯云），国内网络同步不用挂梯子。

## 五、目录结构

```
app/src/main/java/com/minis/hotrank/
├── MainActivity.kt              入口，edge-to-edge + 主题
├── HotViewModel.kt              UiState（loading/refreshing/merged/failed）
├── model/HotItem.kt             HotItem + Platform 枚举（加平台改这里）
├── data/HotApi.kt               OkHttp 拉取 + 双源解析 + 热度归一
├── data/HotRepository.kt        并发调度 + 10 分钟缓存 + 交错合并
└── ui/
    ├── HotScreen.kt             列表页（Chip / 下拉刷新 / 卡片行）
    └── theme/Theme.kt           浅色 + 深色配色

.github/workflows/build-apk.yml  云端构建，产出可下载的 APK
gradlew / gradlew.bat            已内置，不用再生成

tools/                           开发期工具，不进 APK
├── probe_api.py                 探测各平台字段结构
├── verify_pipeline.py           镜像跑一遍解析+归一+合并逻辑
└── build-termux.sh              手机端 Termux 本地构建
```

## 六、v2 候选（按我建议的优先级）

1. **收藏** — 本地 Room，看到好条目一键存
2. **关键词订阅 + 本地通知** — 真正的召回理由，用户会为它每天回来
3. **分享卡片** — 生成图片分享到微信，是唯一的自然增长入口
4. 长按复制标题/链接、搜索历史条目
5. 桌面小组件（Glance）
