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
  - 旅行感悟填写
  - 经纬度录入并接入旅行地图
- 足迹管理
  - 编辑自己的足迹
  - 删除自己的足迹
- 搜索与筛选
  - 按关键词搜索标题、地点、正文、作者
  - 按分类筛选
  - 按地点筛选
  - 按“全部动态 / 仅关注的人”切换信息流
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
  - 行程计划管理
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
JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17
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

## Offline Support

- The app now registers a service worker for partial offline use.
- Pages and resources you have already visited can be reopened without network.
- An offline fallback page is available at `/offline.html`.
- The post editor automatically saves text drafts to local storage, so you can continue writing while offline.
- Final submission, login state refresh, image upload, and the latest synchronized data still require network access.

## WeChat Mini Program

- A new mini program client scaffold is available under `miniapp/`.
- This first version keeps the existing Spring Boot account system and exposes mini-program-friendly APIs under `/api/mini/**`.
- Supported mini program flows in this version:
  - username/password login and registration
  - feed browsing
  - publish a travel footprint
  - personal center
  - province-based footprint distribution
  - post detail page
- Local development steps:
  - start the Java backend with `mvn spring-boot:run`
  - open the `miniapp/` folder in WeChat DevTools
  - keep `miniapp/config.js` pointing to your backend address, default is `http://127.0.0.1:8080`
  - when using local debugging in WeChat DevTools, keep domain validation disabled
- Current note:
  - this version has not yet integrated real `wx.login` + OpenID binding
  - once you have a formal WeChat mini program `appid`, HTTPS domain, and backend secrets, it can be upgraded to official WeChat authorization login

## Render Deployment

- The project now includes `render.yaml` and `Dockerfile` for Render deployment.
- Local development still uses H2 by default.
- Render deployment uses PostgreSQL through the `DATABASE_URL` environment variable and converts it to a JDBC URL automatically at startup.
- Uploaded files should be stored on the Render disk mounted at `/app/uploads`.
- Recommended Render setup in this repository:
  - Web service: Docker runtime
  - Web service plan: `starter`
  - PostgreSQL plan: `basic-256mb`
  - Persistent disk mount path: `/app/uploads`
- Basic deployment flow:
  - create a standalone GitHub repository for this project
  - push the current project code to that repository
  - create a new Render Blueprint from the GitHub repository
  - Render will read `render.yaml` and provision the web service, PostgreSQL, and disk
