# 旅迹 Travel Footprint

一个基于 Java 17、Spring Boot、Thymeleaf 和 H2 的旅游足迹系统课程设计项目，适合在 VS Code 中直接打开和运行。

## 当前功能

- 用户注册、登录、退出登录
- 个人资料设置
  - 昵称与个人简介编辑
  - 用户头像上传
- 旅游足迹发布
  - 标题、地点、分类、标签、出行日期
  - 景点照片上传
  - MP4/WebM 旅行视频上传与在线播放
  - 旅行感悟填写
  - 经纬度录入并接入旅行地图
- 足迹管理
  - 编辑自己的足迹
  - 删除自己的足迹
- 内容审核
  - 新发布的普通用户足迹进入管理员审核队列
  - 新评论进入管理员审核队列
  - 管理员可在后台审核帖子、评论并启用/停用用户
- 搜索与筛选
  - 按关键词搜索标题、地点、正文、作者
  - 按分类筛选
  - 按地点筛选
  - 按“全部动态 / 仅关注的人”切换信息流
  - 首页支持“全部内容 / 用户原创 / 景点资料”分区切换
  - 首页信息流支持分页浏览
- 社交互动
  - 点赞
  - 收藏景点足迹
  - 评论
  - 评论回复
  - 关注用户
  - 私信聊天
  - 通知中心
- 旅行辅助功能
  - 景点评分
  - 旅行地图展示
  - 网页版地图使用高德地图 JS API 2.0，支持道路底图、缩放拖动、地图类型切换、足迹聚合与高德导航
  - 个人地图按照实际旅行日期绘制旅行路线（缺少日期时才使用发布日期兜底）
  - 地图支持浏览器全屏与 `Esc` 退出，不支持原生全屏时自动使用沉浸模式
  - 个人地图提供“时空旅行故事”，按实际旅行日期逐段播放路线，故事抽屉可直接查看照片或视频
  - 全国地图会自动聚合省内相邻地点，点击聚合点可进入省域视图
  - 发布与编辑足迹时提供离线地点建议，可自动填写省份、经纬度并预览地图落点
  - 登录用户可在“记录”菜单开启前台自动到访提醒：移动超过 500 米并停留 2 分钟后提示记录足迹，同一地点 24 小时内不重复提醒
  - 地图点位支持故事预览抽屉，可直接查看照片、视频、摘要和详情入口
  - 旅行护照根据真实足迹自动生成省份印章、旅行勋章与最近旅程签注
  - 发布和编辑页提供隐私结果预览，提交前明确提示内容可见范围与地图点位精度
  - 顶部导航按“记录 / 计划 / 探索 / 我的”重新分组，降低功能查找成本
  - 微信小程序原生端同步提供旅行护照、时空故事、地图视频故事卡和隐私结果预览
  - 微信小程序“我的 → 到访提醒”提供同等的前台定位、停留判断和一键发布预填能力
  - Android 应用通过安全 WebView 完整承载上述网站能力，支持系统视频文件选择器与可信服务器的前台定位授权
  - 行程计划管理

以上体验创新均为本地确定性逻辑，不包含 AI 旅行助手，也不调用大模型服务。
- 公开网页数据导入
  - 可从公开 Wikipedia 景点页面抓取基础景点信息
  - 导入字段包括标题、简介、地点、分类、标签、坐标和来源链接
- 默认演示数据
  - 首次启动自动创建演示用户、样例足迹、关注关系、私信、评分、收藏和计划

## 技术栈

- Java 17
- Spring Boot 3
- Spring MVC
- Spring Data JPA
- Thymeleaf
- H2 Database
- 自定义 CSS 界面

## 推荐的 VS Code 扩展

- `Extension Pack for Java`
- `Spring Boot Extension Pack`

## 环境要求

- JDK 17
- Maven 3.9+

检查命令：

```bash
java -version
javac -version
mvn -v
```

## Windows 安装说明

### 1. 配置 JDK 17

如果还没有安装 JDK 17，请先安装，并设置环境变量：

```text
JAVA_HOME=D:\software\jdk-17
Path 增加：%JAVA_HOME%\bin
```

### 2. 配置 Maven

如果 `mvn -v` 提示找不到命令，需要安装 Maven 并设置：

```text
MAVEN_HOME=D:\software\apache-maven-3.9.9
Path 增加：%MAVEN_HOME%\bin
```

当前这台开发机器已经安装并验证过 Maven 3.9.9。

## 打开项目

在 VS Code 中打开目录：

```text
D:\codex project\shujujiegoukeshe
```

## 启动方式

### 方式一：终端启动

```bash
mvn spring-boot:run
```

首次使用网页版地图时，复制 `.env.example` 为 `.env`，填写 `AMAP_JS_KEY` 和 `AMAP_SECURITY_JS_CODE`。项目默认通过 Spring Boot 内置的 `/_AMapService` 同源代理保护安全密钥：

```properties
AMAP_JS_KEY=你的Web端Key
AMAP_SECURITY_JS_CODE=你的安全密钥
AMAP_PROXY_ENABLED=true
```

Back4app 使用同名环境变量即可，不要把真实密钥写入源码、Dockerfile 或提交到 GitHub。完整说明见 `高德地图配置说明.md`。

### 方式二：一键脚本启动

```bat
start-app.bat
```

### 方式三：VS Code 启动面板

- 打开“运行和调试”
- 选择 `Run TravelFootprintApplication`
- 点击运行

## 停止与状态查看

```bat
status-app.bat
stop-app.bat
```

说明：

- `start-app.bat`：后台启动系统
- `status-app.bat`：查看系统是否运行
- `stop-app.bat`：安全停止系统

运行日志会写入：

- `run/app.out.log`
- `run/app.err.log`

## 公开景点导入

登录后可以在首页点击：

- `导入公开景点数据`

系统会从预设的公开 Wikipedia 景点页面抓取信息并写入当前数据库。

当前默认导入的景点来源包括：

- West Lake
- Gulangyu
- Huangshan
- Jiuzhaigou
- 稻城亚丁
- Terracotta Army
- Potala Palace
- Mount Emei
- Longmen Grottoes
- Leshan Giant Buddha
- Wulingyuan
- Old Town of Lijiang
- Classical Gardens of Suzhou
- Mount Wuyi
- The Bund

说明：

- 抓取结果会写入普通足迹表中，作者显示为 `旅迹景点助手`
- 导入数据会保留来源链接，详情页可以跳转查看原始公开页面
- 同一来源链接不会重复导入
- 当前抓取链路基于 Windows PowerShell 的网页请求能力，适合本机当前运行环境

## 访问地址

- 首页：[http://localhost:8080](http://localhost:8080)
- H2 控制台：[http://localhost:8080/h2-console](http://localhost:8080/h2-console)

H2 默认连接信息：

- JDBC URL: `jdbc:h2:file:./data/travel-footprint`
- User Name: `sa`
- Password: 留空

## 测试命令

```bash
mvn test
```

## 演示账号

- `lin / 123456`
- `yue / 123456`
- `qing / 123456`
- `admin / 123456`

## 目录结构

```text
src/main/java/com/example/travelfootprint
├─ config
├─ controller
├─ model
├─ repository
└─ service

src/main/resources
├─ static/css
├─ templates
└─ application.properties
```

## 数据与文件说明

- 上传图片保存在项目根目录的 `uploads/`
- H2 数据库文件保存在项目根目录的 `data/`
- 运行日志保存在项目根目录的 `run/`
- 以上目录均已加入 `.gitignore`
- 默认图片上传限制为 5MB，仅接受真实内容与声明类型一致的 JPG、PNG、GIF、WebP 文件；网页端会在上传前自动压缩手机原图，云端 2MB 限制下无需手动处理普通照片
- 可以通过环境变量 `APP_UPLOAD_MAX_IMAGE_SIZE_BYTES` 调整服务端图片大小限制
- 每篇足迹可上传一个 MP4 或 WebM 视频，默认上限 20MB；服务端会校验真实视频文件头，并通过与足迹相同的审核和隐私规则提供访问
- 可以通过环境变量 `APP_UPLOAD_MAX_VIDEO_SIZE_BYTES` 调整视频大小限制，网页端和微信小程序的提示与校验会自动同步该值
- 视频能力覆盖网页/PWA、原生微信小程序与 Android 软件：均可上传和播放；网页与小程序还支持替换、删除，Android 软件通过同一网页流程完成管理
- 到访提醒仅在用户主动开启且页面处于前台时读取位置。浏览器/Android 需使用 HTTPS（本机 `localhost` 调试除外）；网页会先尝试兼容的粗略定位并继续等待高精度结果，坐标只发送到当前服务器做离线地点匹配，不调用外部地图或 AI 服务，也不会保存到提醒记录中
- 项目默认使用相对路径，因此从 `D:\codex project\shujujiegoukeshe` 启动时，数据库、上传和日志都会保存在 D 盘

## 网页端优化说明

- 手机端导航会在 720px 以下折叠为菜单按钮，表单与内容卡片切换为单列布局
- 发布页提供字符计数、图片预览、文件大小提示、内联错误提示和防重复提交
- 普通用户修改已公开足迹后，内容会重新进入管理员审核队列
- 网页端与微信小程序端共享停用用户、内容审核和公开可见性规则
- 浏览器修改请求启用统一 CSRF 防护，小程序 API 与 H2 控制台使用独立接口规则
- 首页点赞、评论、收藏和评分数据使用批量聚合查询，减少信息流加载时的数据库访问次数

## 后续可扩展方向

- 管理员后台与内容审核
- 景点攻略模块
- 多图上传相册
- 分页加载
- 更细的通知分类
- MySQL 持久化部署

## China Map Generation

- The current travel map uses a generated SVG instead of the uploaded JPG.
- Source boundary data: Apache ECharts China map data under Apache License 2.0
- Regenerate command:

```bash
python scripts/generate_china_map_svg.py
```

## Interactive Map Explorer

- The public and personal maps support keyword, travel-year, category, province, photo-only, and result-order filters.
- Map markers are linked with the footprint cards below the map: selecting either side highlights and locates the other.
- Multiple posts at the same city or attraction share one visible marker and remain available from the marker popup.
- Map results support remembered card/list layouts, and the current filtered view can be copied as a shareable link.
- The immersive atlas uses a dark local SVG map, glowing markers, and a synchronized travel-intelligence sidebar without external map services. Publication-time-ordered routes appear only on the personal map; the public map shows distribution points without connecting different users.
- Province-aware landmark validation prevents a place name from being plotted into a province that conflicts with the selected province. Valid stored coordinates take priority, and a province heat layer visualizes footprint density.
- The map summary shows visible posts, covered provinces, and distinct mapped locations.
- All map assets and generated province SVG files remain inside this D-drive project; no external map API key is required.

## Travel Reports

- Log in and open `报告` in the main navigation to view an automatically generated personal travel report.
- Reports support weekly, monthly, and yearly views, and use each footprint's travel date for period grouping.
- Each report includes travel totals, active days, province coverage, theme and destination rankings, a period footprint map, photos, and a chronological timeline.
- Use `上一期` and `下一期` to browse historical periods. The current report can also be shared or printed/saved as PDF from the browser.
- Travel expenses recorded in the ledger are summarized in matching weekly, monthly, and yearly reports.

## Photo Albums, Calendar, and Travel Ledger

- A footprint can contain up to nine JPG, PNG, GIF, or WebP images. In the post editor, drag selected photos to reorder them and choose the cover before publishing.
- A footprint can also contain one MP4 or WebM travel video up to 20 MB. Videos use the same moderation and visibility checks as the footprint.
- Existing single-photo posts remain compatible. Album covers can be changed later from the footprint detail page.
- Footprints can be linked to one of the current user's trip plans. The plan page shows linked footprints, completed travel days, progress, and linked spending.
- Open `日历` after logging in to browse personal footprints by their actual travel date and move between months.
- Open `账本` to record categorized travel expenses, optionally linked to a trip plan or footprint. Monthly totals and category distribution are calculated automatically.
- Database tables and columns for these features are created by the existing `spring.jpa.hibernate.ddl-auto=update` setting; no separate migration command is required for local development.

## Recap, Privacy, Goals, and Data Export

- Weekly, monthly, and yearly reports compare footprint, travel-day, province, and expense totals with the previous matching period.
- Open `/recap` after logging in for an immersive annual journey recap with yearly rhythm, map, preferences, spending, and photo highlights.
- The map supports province selection followed by a second-level city/location drilldown while preserving the other active filters.
- Each footprint can be public, visible only to followers, or private. A separate map-privacy option replaces the exact public point and location text with a province-level approximate position.
- Open `目标` to create yearly goals for footprints, covered provinces, travel days, or completed trip plans. Progress updates automatically from existing data.
- The settings page can export the signed-in user's profile, footprints, albums, plans, expenses, and goals as UTF-8 JSON. Password hashes are never included.

## Travel Companions, Collaborative Plans, and Account Security

- Open `/discover` after logging in to see travel-companion recommendations based on shared public provinces and travel categories. Private footprints and messages are never used for matching.
- A plan owner can invite another enabled user by username. The recipient can accept or decline the invitation from the plan page.
- Accepted companions can associate their own footprints and ledger entries with the shared plan. Only the plan owner can invite members, remove members, or delete the plan; companions may leave it themselves.
- Plans starting within seven days appear in the upcoming panel and create a one-time notification for every owner or accepted companion.
- Notifications remain unread until opened, support individual read state, and can also be marked as read in one action.
- The settings page supports password changes with current-password verification and session-ID renewal. The last login and password-change times are displayed for the account owner.
- The PWA install prompt is exposed from settings when supported. The service worker deliberately excludes messages, notifications, settings, exports, admin pages, APIs, and the H2 console from offline page caching.
- Existing H2 file databases are migrated at startup so new notification categories remain compatible without deleting historical data.

## Destination Wishlist and Trip Workspace

- Open `/wishlist` to record destinations with province, priority, target year, status, and a short reason. A destination can be converted directly into a collaborative plan.
- Every visible plan has a dedicated workspace at `/plans/{id}`. Owners and accepted companions can add dated itinerary activities, mark them complete, and maintain a shared preparation checklist.
- Checklist items support document, transport, accommodation, packing, and other categories, and can be assigned to any accepted participant.
- The workspace brings together itinerary progress, checklist progress, linked footprints, plan expenses, base plan editing, and collaborator-aware actions.
- Download `/plans/{id}/calendar.ics` to import the plan into calendar applications. When activities exist, each activity becomes a calendar event; otherwise the full trip is exported as an all-day event.
- Read-only plan sharing is disabled by default and can only be controlled by the plan owner. Public share pages expose dates, destination, and itinerary only; budget, private notes, preparation items, and participant accounts stay hidden.
- Personal JSON exports now also include the destination wishlist, owned-plan activities, and owned-plan checklist items.

## Destination Guides and Smart Trip Planning

- Open `/guides` to browse a destination guide center built from the local place catalog and approved public footprints. Province, keyword, popular-location, and travel-theme filters never include private or followers-only content.
- Every trip workspace now calculates a 0–100 readiness score from dates, itinerary coverage, budget, checklist progress, and document, transport, and accommodation preparation.
- The smart itinerary panel generates deterministic suggestions from the plan destination, the local place catalog, and approved public content. It does not call a cloud AI service or upload private trip data.
- Suggested activities are previews only. Owners and accepted collaborators choose which suggestions to adopt; the server recomputes and validates every selected key, ignores stale or modified values, filters duplicate locations, and preserves existing activities.
- Destination guides and smart suggestions are informational. Confirm opening hours, reservations, weather, and transport conditions before departure.

## Global Search, Personalized Recommendations, and Advanced Insights

- Open `/search` to search approved public footprints, the local map-place catalog, and trip plans visible to the signed-in user. Private plans never appear to outsiders, and approximate public locations keep their exact place text hidden.
- Open `/recommendations` after signing in for a local, explainable inspiration feed. Ranking uses the signed-in user's footprint categories, provinces, and favorites; it never reads messages, passwords, or plans the user cannot access.
- Recommendation cards show why an item matched. Use `减少此类推荐` to remove a result from the personal feed, or `恢复全部` to reset this feedback; dismissal preferences are included in the personal JSON export.
- Open `/insights` for an all-time private dashboard with travel diversity, revisit rate, travel rhythm, yearly monthly pulse, season and category structure, spending structure, province milestones, and actionable improvement ideas.
- `/search`, `/recommendations`, and `/insights` are excluded from service-worker page caching because their rendered results may contain private account data.
- All search, recommendation, and insight calculations run against the local application database. No personal data is sent to an external recommendation or AI service.

## Official Province Tourism Import

- After logging in, you can click `导入各省文旅官方数据` on the home page.
- The system will import curated province-level official tourism guide cards into the normal travel post feed.
- Imported cards keep the official source URL and source name, and repeated clicks will skip duplicates.
- This batch uses curated official province tourism department or bureau portal links rather than live scraping, because some official sites use anti-bot protection.

## Official Featured Routes Import

- After logging in, you can also click `导入官方精品线路数据`.
- This batch imports curated official route or themed-play cards based on province tourism department or bureau pages.
- Imported records are stored in the normal travel post feed with category `官方精品线路`.
- Repeated imports are deduplicated by the official source URL.

## Baidu Scenic Description Import

- After logging in, click `导入百度景点描述数据`.
- This batch imports attraction-focused description cards instead of province portals or route summaries.
- Each imported record keeps a Baidu source link and is stored in the normal travel post feed with category `景点资料`.
- Repeated imports are deduplicated by the source URL.
- The webpage button and interaction stay the same, but the import source is now editable through the local file `data/baidu-scenic-seeds.json`.
- To add your own scenic records, append a new JSON object in that file with at least `title`, `province`, `location`, `description`, and `sourceUrl`, then click the same homepage import button again.

## Offline Support

- The app now registers a service worker for partial offline use.
- Pages and resources you have already visited can be reopened without network.
- An offline fallback page is available at `/offline.html`.
- The post editor automatically saves text drafts to local storage, so you can continue writing while offline.
- Final submission, login state refresh, image upload, and the latest synchronized data still require network access.
- Mini-program login tokens are stored as SHA-256 hashes in the database with a configurable validity period, so a cloud container restart no longer signs every mini-program user out.
- Mini-program photo publishing optimizes mobile originals before upload to stay within the 2MB Back4app image limit.

## WeChat Mini Program

- A native WeChat Mini Program client is available under `miniapp/` and shares the existing Spring Boot data model through `/api/mini/**`.
- Supported mini program flows:
  - username/password login and registration
  - premium travel feed, filtering, pull-to-refresh, post detail, likes, and favorites
  - travel-footprint publishing with photo upload and native location selection
  - foreground arrival reminders with explicit opt-in, 500 m movement/2 min dwell checks, and one-tap publishing prefill
  - native map markers; the public map has no route line, while the personal map connects points by publish time
  - trip-plan browsing, creation, progress, and deletion
  - weekly, monthly, and yearly travel reports
  - personal archive and configurable backend connection for real-device debugging
- Local development steps:
  - start the Java backend with `mvn spring-boot:run`
  - open the `miniapp/` folder in WeChat DevTools
  - the default backend address is `http://127.0.0.1:8080`; use the in-app `连接设置` page to switch addresses
  - for a physical device on the same Wi-Fi, use the computer LAN address, such as `http://192.168.43.15:8080`
  - when using local debugging in WeChat DevTools, keep domain validation disabled
- Before submission, deploy the backend to HTTPS and configure the request/uploadFile legal domains in the WeChat admin console.
- The current account flow intentionally reuses the website account system. Real `wx.login` + OpenID binding requires a formal appid and backend secret configuration.
- See `miniapp/README.md` for import, debugging, preview, and release preparation.

## Back4app Free Container Deployment

- The project includes a `Dockerfile` tuned for Back4app's 256 MB free container.
- Local development still uses H2 by default.
- Cloud deployment uses PostgreSQL through the `DATABASE_URL` environment variable and converts it to a JDBC URL automatically at startup.
- Recommended Back4app setup in this repository:
  - Container plan: Back4app `Free` ($0, no credit card required)
  - PostgreSQL: external Neon Free connection supplied as `DATABASE_URL`
  - Uploaded images: stored in PostgreSQL with `APP_UPLOAD_STORAGE_MODE=database`
  - Persistent disk: none
- Basic deployment flow:
  - connect Back4app Containers to `dengp246-jpg/travel-footprint`
  - deploy the `main` branch with the repository-root `Dockerfile`
  - create a Neon Free PostgreSQL project and paste its connection string into the Back4app `DATABASE_URL` environment variable
  - select the Back4app Free container and use `/health` as its health check

## Production Hardening and Final Acceptance

- Browser responses now include a Content Security Policy, strict referrer policy, permissions policy, frame protection, MIME-sniffing protection, request IDs, and `Server-Timing` diagnostics. HSTS is emitted for secure HTTPS requests.
- Repeated failed logins from the same account and client address are temporarily limited. Configure the threshold with `APP_LOGIN_MAX_ATTEMPTS` and the rolling window with `APP_LOGIN_WINDOW_MINUTES`.
- `/uploads/**` is no longer a directly exposed static directory. Avatar files remain public, while footprint photos repeat the same public, followers-only, private, disabled-user, and moderation checks used by the footprint page.
- HTML, JSON, CSS, JavaScript, and SVG responses support compression. Versioned CSS, JavaScript, and bundled images receive browser cache headers; the service worker itself remains `no-store` so updates are discovered promptly.
- Open `/health` for the deployment health probe. It reports only overall, database, and upload-storage readiness and does not expose credentials, filesystem paths, or exception details.
- Unknown routes use a safe 404 page. Internal exception messages and stack traces are disabled in HTTP error responses.
- Every response includes `X-Request-Id`; requests slower than one second are written to the server log with method, path, status, duration, and request ID, but not the query string.

### Local and production profiles

- Local development keeps `APP_DEMO_SEED_ENABLED=true` by default, so the documented demo accounts remain available.
- The `prod` profile disables the H2 console, enables Thymeleaf template caching, requires secure session cookies, disables demo-data seeding, and hides error details.
- Start a production-profile instance manually with:

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
$env:APP_DEMO_SEED_ENABLED="false"
$env:APP_ADMIN_BOOTSTRAP_PASSWORD="replace-with-a-strong-unique-password"
mvn spring-boot:run
```

- The Docker image activates the production profile automatically. Set a strong `APP_ADMIN_BOOTSTRAP_PASSWORD` in the Back4app environment page for the first `admin` login, then change the password from account settings.
- An existing administrator is never silently assigned the bootstrap password, and a deliberately disabled administrator is no longer re-enabled during startup.

### Final self-check and D-drive backup

Run the complete test suite and build the deployable JAR:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\final-check.ps1
```

The script refuses to run outside the D drive, verifies `data/`, `uploads/`, and `run/`, runs all tests, packages the application, and checks the final JAR.

For a consistent local H2 and upload snapshot, stop the application first and then run:

```powershell
.\stop-app.bat
powershell -ExecutionPolicy Bypass -File .\scripts\backup-local-data.ps1
.\start-app.bat
```

Backups are created under the ignored D-drive directory `backups/<timestamp>/`. The backup script refuses to copy the database while port 8080 is still serving the application.

## Android Application

- The `android-app/` directory contains an installable Android shell for the existing Spring Boot + Thymeleaf application. The backend and database remain on the server; the phone connects through the configured server URL.
- The app includes persistent server configuration, login cookies, file selection for photo uploads, loading progress, back navigation, external-link handling, offline retry, and strict SSL failure handling.
- Debug builds allow a LAN HTTP address for local testing. Release builds reject cleartext HTTP and must use an HTTPS deployment.
- Android SDK, Gradle, caches, and generated build files stay in ignored directories under this D-drive project.

Install the D-drive Android toolchain and build the APK:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-android.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\build-android.ps1
```

The installable debug package is copied to `outputs/travel-footprint-android-debug.apk`. The Android emulator can use `http://10.0.2.2:8080`; a physical phone on the same Wi-Fi should use the computer's LAN address, such as `http://192.168.1.20:8080`. See `android-app/README.md` for details.

When the backend and APK have both been built, a phone can download the package directly from `/download/android`. The Docker image includes `distribution/travel-footprint-android.apk`, so the same download works from the public HTTPS deployment while the development computer is off. The response uses the Android package MIME type and an attachment filename. Override the source APK with `APP_ANDROID_APK_PATH` when needed.

### Android App without a running computer

The APK is a network client, so the backend must run somewhere even when the development computer is off. Deploy the included Docker image on the Back4app Free container, connect it to Neon Free PostgreSQL, verify its HTTPS URL, and then build an APK with that URL embedded:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-cloud-deployment.ps1 `
  -ServerUrl "https://your-back4app-domain"
powershell -ExecutionPolicy Bypass -File .\scripts\build-android-cloud.ps1 `
  -ServerUrl "https://your-back4app-domain"
```

The resulting `outputs/travel-footprint-android-cloud.apk` opens the cloud service immediately and no longer depends on the computer or local Wi-Fi. The build script also refreshes `distribution/travel-footprint-android.apk`; after the updated image is deployed, Android users can open `https://<your-service>/download/android` on the phone and download it directly. See `docs/CLOUD-APP-DEPLOYMENT.md` for deployment, costs, initial credentials, and local-data migration notes.
