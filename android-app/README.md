# 旅迹 Android 应用

此目录是现有 Spring Boot + Thymeleaf 网站的 Android 容器，不复制数据库，也不在手机内运行 Java 后端。应用保存用户配置的服务器地址，并使用系统 WebView 访问同一套旅迹功能。

## 当前能力

- 原生应用启动图标和独立窗口
- 首次启动配置服务器地址，之后可从顶部工具栏更换
- 登录 Cookie、网页本地存储和文件上传
- 仅对当前配置的可信服务器开放前台定位授权，用于网页端自动到访提醒；切到后台或关闭页面后停止定位
- 页面加载进度、刷新、后退、离线重试和服务器错误提示
- 外部链接交给系统浏览器处理
- SSL 验证失败时拒绝继续连接
- 调试版允许局域网 HTTP，正式版只允许 HTTPS

## 构建

在仓库根目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-android.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\build-android.ps1
```

工具链、缓存和 SDK 都会保存在项目内的 D 盘忽略目录。最终调试安装包位于 `outputs/travel-footprint-android-debug.apk`。

未指定 `-ServerUrl` 的通用测试包不再默认连接模拟器，首次启动会要求填写服务器地址。`http://10.0.2.2:8080` 仅供模拟器开发，真机局域网调试可使用电脑地址，例如 `http://192.168.1.20:8080`；两者都依赖电脑持续运行。用于正式分发时，应先部署 HTTPS 后端，然后以 `-ServerUrl` 指定地址重新构建。

## 脱离电脑运行

后端部署到 HTTPS 公网地址后，运行：

```powershell
powershell -ExecutionPolicy Bypass -File ..\scripts\build-android-cloud.ps1 `
  -ServerUrl "https://travel-footprint-xxxx.onrender.com"
```

生成的 `outputs/travel-footprint-android-cloud.apk` 已内置服务器地址，首次启动不会再要求填写电脑 IP。完整云端部署步骤见 `docs/CLOUD-APP-DEPLOYMENT.md`。

云端版覆盖安装在旧调试版之上时，也会自动清除旧调试地址并切换到构建时内置的 HTTPS 地址。
