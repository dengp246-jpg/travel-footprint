# 旅迹 App 脱离电脑运行

当前 Android App 是联网客户端。要让电脑关机后手机仍能登录、发布、查看地图与报告，需要把 Spring Boot 后端、数据库和上传文件放到公网云服务。

## 推荐架构

- Android APK：安装在手机，固定连接 HTTPS 云端地址
- Spring Boot：Render Free Docker Web Service
- 业务数据：Neon Free PostgreSQL
- 上传图片：随业务数据保存到 Neon PostgreSQL，不使用付费磁盘
- 本机电脑：只用于继续开发，不再参与 App 日常运行

## 第一次部署

1. 把当前完整代码推送到 GitHub 仓库。
2. 登录 Render，选择 `New +` → `Blueprint`。
3. 连接 GitHub 仓库 `dengp246-jpg/travel-footprint`。
4. Render 会读取仓库根目录的 `render.yaml`，准备以下资源：
   - `travel-footprint` Free Web Service
3. 在 Neon 免费账户中创建 PostgreSQL 项目，复制连接串。
4. 回到 Render 的 `travel-footprint` 环境变量页面，将 Neon 连接串填入 `DATABASE_URL`。
5. 确认 `APP_UPLOAD_STORAGE_MODE=database`，然后开始部署。
5. 确认数据库显示 `Free`；Web Service 与上传磁盘仍按 `render.yaml` 使用付费常驻方案，然后由账号所有者确认创建。
6. 等待部署显示 `Live`，复制 HTTPS 服务地址，例如：

   ```text
   https://travel-footprint-xxxx.onrender.com
   ```

7. 在 D 盘项目根目录验证云服务：

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\verify-cloud-deployment.ps1 `
     -ServerUrl "https://travel-footprint-xxxx.onrender.com"
   ```

8. 构建固定云端地址的安装包：

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\build-android-cloud.ps1 `
     -ServerUrl "https://travel-footprint-xxxx.onrender.com"
   ```

9. 将 `outputs/travel-footprint-android-cloud.apk` 发送到手机安装。安装后会直接打开云端旅迹，不需要电脑 IP。

## 生产账号

生产模式默认不会创建 `lin / 123456` 等演示用户。首次部署后：

- 在 Render 服务的 Environment 页面找到自动生成的 `APP_ADMIN_BOOTSTRAP_PASSWORD`。
- 用 `admin` 和该密码登录，然后立即修改密码。
- 普通用户可通过注册页面自行创建。

## 数据说明

- 本机 `data/` 中的 H2 数据不会自动上传到 PostgreSQL。
- 云端部署后会从空数据库开始，原本电脑内的账号、足迹和计划不会自动出现。
- 如需保留旧数据，需要单独执行 H2 → PostgreSQL 数据迁移。
- 手机使用的图片必须保存在持久磁盘或对象存储；否则云服务重启后图片会丢失。

## 完全免费方案限制

当前 `render.yaml` 不会创建任何付费资源：Web Service 使用 Render Free，数据库使用外部 Neon Free，图片直接存进 PostgreSQL，因此不需要 Render Persistent Disk。

- Render Free 服务连续 15 分钟无访问会休眠；下次打开时通常需要等待约一分钟唤醒。
- Neon Free 当前每项目提供 0.5 GB 数据库存储和每月免费计算额度。图片也占用这部分容量，建议上传前压缩，并仅用于课程展示或小规模使用。
- 免费额度、休眠策略可能由服务商调整，正式长期运营前需要重新核对。

## 手机直接下载 APK

部署镜像会把 `distribution/travel-footprint-android.apk` 放入免费云服务器。部署成功后，可直接在手机浏览器打开：

```text
https://你的服务地址/download/android
```

也可以登录网页，在设置页点击“下载 APK”。下载完成后允许浏览器安装未知来源应用，即可继续安装。通用安装包首次启动时填写当前网站的 HTTPS 地址；执行 `scripts/build-android-cloud.ps1` 并重新部署后，下载到的云端专用版会自动连接本站。

## 后续更新

`render.yaml` 已开启自动部署。代码推送到 GitHub 后，Render 会自动重新构建服务；正常网页功能更新通常不需要重新安装 APK。只有云端域名、Android 原生容器或签名发生变化时才需要重新生成 APK。
