# 热榜聚合 (HotRank)

6 站热搜合成**一个综合榜**的安卓原生 App。Kotlin + Jetpack Compose。

不只是把 6 个榜拼在一起 —— 而是把各站热度归一化后重新算出一个**综合热度**，
并且用「多平台同榜」作为核心排序信号。

---

## 一、聚合算法（这个 App 的核心）

### 要解决什么问题

各平台的热度值量纲完全不同，实测四种形态：

| 平台 | hot_value 实际返回 |
|---|---|
| 微博 / 百度 / 抖音 | `"1834890"` 纯数字 |
| 知乎 | `"3504 万热度"` 带中文单位 |
| B站 | `"1395775播放"` 带后缀 |
| 头条 | `""` **根本不给** |

直接比大小毫无意义。轮流交错插入（v0.1 的做法）也不叫聚合 —— 那只是省了 6 次点击。

### 四步排序

**1) 平台内归一化**（关键：只在平台内部比，跨平台只比归一化后的值）

```
热度分 heatScore = ln(1+h) / ln(1+该平台最高热度)       → 0~1
名次分 rankScore = 1 - (名次-1)/(总数-1)                → 0~1
```

- 用**对数**是因为热度是重尾分布：榜首 180 万、第 50 名可能才 3 万，
  线性归一会让头部几条把其余全压成 0.02，尾部信息全丢。
- 平台不给热度值（头条）时退化为纯名次分，并打 **0.85 折**，避免它白占便宜。

**2) 加权合成** `base = 平台权重 × (0.6 × 热度分 + 0.4 × 名次分)`

平台权重是产品判断，不是技术参数，集中放在 `Platform` 枚举里：

| 微博 | 百度 | 抖音 | 头条 | 知乎 | B站 |
|---|---|---|---|---|---|
| 1.00 | 0.95 | 0.95 | 0.90 | 0.88 | 0.85 |

**3) 跨平台事件聚类** —— 把「同一件事」的多条标题合成一个事件

- 标题归一化：去掉标点/空格/emoji，转小写
- 相似度：字符二元组 Jaccard
- **星型聚类**：新条目只和「组代表」比，不做单链接聚类
  （否则 A~B、B~C 会传递合并成一个大杂烩）

两道必要的守卫，都是实测踩出来的：

> **① 数字冲突否决** —— 两边都含数字且毫无交集，直接判定不是同一事件。
> 没有这条，阈值 0.40 时 `iPhone17Pro线下降价` 会被并进 `iPhone18Pro` 事件组。
>
> **② 包含关系需短串 ≥ 6 字** —— 防止极短标题被任意长标题吞掉。
>
> 另外注意：Java 正则的 `\w` 是 ASCII 语义，**不含中文**。归一化必须写 `\p{IsHan}`，
> 否则中文被整段删掉、所有标题归一化成空串，结果是全部合并成一个事件。
> （Python 的 `\w` 默认 Unicode，移植时极易踩这个坑。）

**4) 多站同榜加成** —— 聚合榜真正的价值所在

```
涉及 n 个不同平台 → 分数 ×(1 + 0.15 × (n-1))，上限 1.6×
```

实测效果：「**中国女篮无缘世界杯四强**」在微博只排**第 47 位**，
但微博 + 百度 + 抖音 + 头条四个平台都在推，加成后升到**全榜第 2**。

这就是单一榜单永远给不了的信息 —— 不被单站算法埋没。

最后线性映射成 **0~100 的综合热度**，全榜第一恒为 100，直接显示在卡片右侧。

### 调参入口

全在 `data/RankingEngine.kt` 顶部：`W_HEAT` / `W_RANK` / `NO_HEAT_PENALTY` /
`SIM_THRESHOLD` / `CLUSTER_STEP` / `CLUSTER_CAP`。
平台权重在 `model/HotItem.kt` 的 `Platform` 枚举。

`tools/` 下有用来调参的脚本，改完参数可以先用真实数据验证再打包。

---

## 二、关键词订阅

- 订阅列表本地存（SharedPreferences + JSON，几十条的量级不值得上 Room）
- 关键词做去标点小写包含匹配，中文直接子串命中
- 后台用 **WorkManager** 每 30 分钟检查一次（系统调度，不用自己保活）
- 命中后发通知；**已推送过的（关键词, 标题）组合不重复打扰**
- 无订阅时自动停掉后台任务，不留无用唤醒
- Android 13+ 在用户打开开关时才请求通知权限，不在一进 App 就弹窗

> ⚠️ 国内 ROM 注意：MIUI / EMUI / ColorOS 等会限制后台任务。
> 如果收不到提醒，去系统设置里给本 App 开「自启动」并关闭「电池优化」。

---

## 三、怎么在手机上把 APK 弄出来

**不用电脑，不用 Android Studio。**

### 路径 A：云端构建（推荐，已验证跑通）

代码推到 GitHub，GitHub 的机器编译，手机只负责下载成品。

仓库：`https://github.com/dayd57087-lgtm/HotRank`

APK 固定下载地址（**以后更新不变**）：

```
https://github.com/dayd57087-lgtm/HotRank/releases/download/latest-build/app-debug.apk
```

改代码后重新推送，同一个链接自动更新。

一键推送 + 触发构建（不需要在手机上装 git）：

```sh
python3 tools/push-to-github.py --token <PAT> --repo HotRank
python3 tools/watch-build.py --token <PAT> --owner <用户名> --repo HotRank --dispatch
```

构建失败时直接抓日志看报错，不用去网页翻：

```sh
python3 tools/build-log.py --token <PAT> --owner <用户名> --repo HotRank
```

> 用 Git Data API 推送时，**新建** ref 不会触发 Actions，必须手动 dispatch；
> 而后续**更新**已存在的 ref 会正常触发 push 事件。

### 路径 B：Termux 本地构建（完全离线，未实测）

```sh
pkg install -y git && cd ~/HotRank && sh tools/build-termux.sh
```

脚本已处理 ARM 手机上绕不开的坑：装 JDK 17 + Termux 的 aarch64 版 aapt2、
下 `platform-34` 取 android.jar、用 `android.aapt2FromMavenOverride` 顶掉
Google 的 x86_64 版 aapt2（Google 只发 x86_64，在 ARM 上跑不起来）。

---

## 四、技术栈

AGP 8.5.2 / Kotlin 1.9.24 / Compose BOM 2024.09.00 / compileSdk 34 / minSdk 26 / JDK 17 / Gradle 8.7

OkHttp + org.json + SharedPreferences + WorkManager。

**刻意不用 Hilt / Room** —— 一个 GET 接口 + 一个本地缓存，上依赖注入和 ORM 是负收益。
**刻意不内嵌 WebView** —— 点击直接交给系统浏览器，省掉一整套适配和内容合规问题。

Gradle 仓库与 distributionUrl 都指向国内镜像（阿里云 + 腾讯云），国内网络同步不用挂梯子。

---

## 五、目录结构

```
app/src/main/java/com/minis/hotrank/
├── MainActivity.kt              入口，edge-to-edge
├── HotViewModel.kt              UiState + 订阅操作
├── model/HotItem.kt             HotItem / Platform(含权重) / RankedEvent
├── data/
│   ├── RankingEngine.kt         ★ 聚合排序算法
│   ├── HotApi.kt                双源拉取 + 解析 + 热度数值化
│   ├── HotRepository.kt         并发调度 + 缓存 + 调用排序
│   └── SubscriptionStore.kt     订阅存储 + 关键词匹配
├── notify/Notifier.kt           通知渠道与推送
├── work/KeywordWorker.kt        后台检查 + 调度
└── ui/
    ├── HotScreen.kt             主界面（渐变头部 / 事件卡片 / 骨架屏）
    ├── SubscribeSheet.kt        订阅管理面板
    └── theme/Theme.kt           配色与渐变

tools/                           开发期工具，不进 APK
├── probe_api.py                 探测各平台字段结构
├── proto_ranking.py             聚合算法原型（可独立跑）
├── tune_threshold.py            阈值扫描 + 数字守卫验证
├── verify_pipeline.py           端到端解析合并验证
├── push-to-github.py            一键推送
├── watch-build.py               触发并轮询构建
├── build-log.py                 抓取构建日志
└── build-termux.sh              Termux 本地构建
```

---

## 六、数据源

| 平台 | 主源 uapis.cn | 备用源 60s.viki.moe |
|---|---|---|
| 微博 | ✅ 50 条 | ✅ 50 条 |
| 知乎 | ✅ 30 条 | ⚠️ 时常返 0 条 |
| 百度 | ✅ 51 条 | ❌ |
| 头条 | ✅ 50 条（无热度值） | ✅ 50 条 |
| 抖音 | ✅ 50 条 | ❌ |
| B站 | ✅ 100 条 → 截取 50 | ❌ |

- 主源 `https://uapis.cn/api/v1/misc/hotboard?type={weibo|zhihu|baidu|toutiao|douyin|bilibili}`
- 备用源 `https://60s.viki.moe/v2/{weibo|zhihu|toutiao}`
- 都不需要 API Key。**但都是第三方公益接口，随时可能变**，
  所以做了三层兜底：主源 → 备用源 → 过期缓存。
- 40 个请求 / 小时的量级，对这类公益接口是礼貌的。

加平台：`Platform` 枚举加一行，确认接口支持该 type 即可，其余代码不用动。

---

## 七、下一步候选

1. 收藏（本地 Room）
2. 分享卡片（生成图片，唯一的自然增长入口）
3. 长按复制标题 / 链接
4. 桌面小组件（Glance）
5. 订阅的关键词支持多词 AND / OR
