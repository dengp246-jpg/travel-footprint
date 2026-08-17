# 旅迹 App 脱离电脑运行：Back4app 免费部署

## 最终架构

- Android APK：安装在手机，连接公网 HTTPS 地址。
- Spring Boot：Back4app Containers 免费容器。
- 数据库与图片：现有 Neon Free PostgreSQL；图片使用数据库存储模式。
- 本地电脑：只用于开发和更新，关机后不影响手机使用。

Back4app 官方免费容器目前为 0.25 CPU、256 MB RAM、100 GB 流量，支持 GitHub、Dockerfile 和 Java，并明确说明免费方案不要求信用卡。项目的 Dockerfile 已加入 256 MB 容器所需的 JVM 内存限制。

## 在 Back4app 创建免费容器

1. 打开 <https://www.back4app.com/>，使用 GitHub 登录。
2. 进入控制台，选择 `Build new app`。
3. 选择 `Containers as a Service` 或 `Container`，不要选择需要购买的付费套餐。
4. 授权 GitHub，并选择仓库 `dengp246-jpg/travel-footprint`。
5. 填写部署信息：

   | 项目 | 填写内容 |
   | --- | --- |
   | App name | `travel-footprint` |
   | Branch | `main` |
   | Root directory | `/` 或留空 |
   | Dockerfile | `Dockerfile` |
   | Plan | `Free · $0` |
   | Health check path | `/health` |
   | Auto deploy | 开启 |

6. 在 Environment Variables 中逐项添加：

   | 变量名 | 值 |
   | --- | --- |
   | `DATABASE_URL` | Neon 控制台中复制的完整连接串 |
   | `APP_ADMIN_BOOTSTRAP_PASSWORD` | 自己设置的高强度初始管理员密码 |
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `APP_UPLOAD_STORAGE_MODE` | `database` |
   | `APP_DEMO_SEED_ENABLED` | `false` |
   | `APP_UPLOAD_MAX_IMAGE_SIZE_BYTES` | `2097152` |
   | `APP_UPLOAD_MAX_VIDEO_SIZE_BYTES` | `20971520` |

   `DATABASE_URL` 和管理员密码属于秘密，只填写在 Back4app 环境变量页面，不要发到聊天、截图或提交进 GitHub。

7. 点击 `Create App`，等待状态变成 `Available` 或部署变成 `Ready`。
8. 从 `Actions` 中打开平台分配的 HTTPS URL。先访问 `/health`，应看到应用、数据库和图片存储均为可用状态。

## 验证并生成固定云地址 APK

在 D 盘 PowerShell 中执行，域名替换为 Back4app 提供的实际 HTTPS 地址：

```powershell
Set-Location "D:\codex project\shujujiegoukeshe"

powershell -ExecutionPolicy Bypass -File .\scripts\verify-cloud-deployment.ps1 `
  -ServerUrl "https://你的-back4app-域名"

powershell -ExecutionPolicy Bypass -File .\scripts\build-android-cloud.ps1 `
  -ServerUrl "https://你的-back4app-域名"
```

生成文件：

```text
D:\codex project\shujujiegoukeshe\outputs\travel-footprint-android-cloud.apk
```

把 APK 发到手机安装。此版本会直接连接 Back4app 公网服务，不再要求电脑 Wi-Fi 地址或模拟器地址。

## 手机直接下载安装

云端专用 APK 构建完成并推送到 GitHub、等待 Back4app 自动重新部署后，在手机浏览器打开：

```text
https://你的-back4app-域名/download/android
```

Android 若提示阻止安装，请只为当前浏览器临时允许“安装未知应用”。

## 账号与数据

- 第一次部署可用 `admin` 和环境变量中的 `APP_ADMIN_BOOTSTRAP_PASSWORD` 登录，登录后立即修改密码。
- 普通用户可以自行注册。
- 本地 H2 数据不会自动迁入 Neon；云端第一次启动使用空数据库。
- 图片保存到 Neon，因此容器重启不会造成图片丢失。
- 免费容器资源有限，服务端图片限制为 2 MB 更稳定；网页端会在提交前自动压缩手机原图，多图总量也会控制在请求限制内。

## 更新项目

代码推送到 GitHub `main` 后，Back4app 会自动重新构建。普通网页功能更新不需要重新安装 APK；只有公网域名、Android 原生代码或签名变化时才需要重新生成 APK。

## 备用方案

如果 Back4app 账号所在地区暂时无法创建免费容器，可使用 Azure for Students。它不要求信用卡，但需要有效学校邮箱或学生身份认证，因此不作为当前首选。
