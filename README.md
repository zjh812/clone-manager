# 多开应用管理 (Clone Manager)

原生 Android 应用分身 / 多开管理器，通过 **Root shell** 直接管理 Android Framework 原生
`android.os.usertype.profile.CLONE` Profile。

**不是**虚拟机 / 沙箱 / Parallel Space 类容器——它操作的是 Android 系统原生的多用户机制。

## 功能

- Root 检测（`su -c id`）
- 读取 CLONE Profile 列表（真实 userId，不硬编码）
- 动态读取 `mMaxAllowedPerParent`（Framework 改成 10/30 自动跟随）
- 创建分身（`pm create-user --profileOf 0 --user-type android.os.usertype.profile.CLONE`）
- 删除分身（先 `am stop-user -w -f` 再 `pm remove-user`，5 秒轮询确认）
- 启动分身（`am start-user -w`）
- 停止分身（`am stop-user -w -f`，必须加 `-f`）
- 重命名分身（`pm rename-user`）
- 分身应用管理：
  - 查看分身内已安装应用
  - 把主系统应用安装到分身（`pm install-existing --user`）
  - 卸载分身内应用（`pm uninstall --user`，不影响主用户）
  - 清除分身内应用数据（`pm clear --user`）
  - 启动分身内应用（`am start --user`）
- 调试日志页（300 条环形缓冲，复制/清空）
- 系统兼容性检测（动态识别当前 ROM 支持的命令）

## 支持设备

实测在 **Lenovo TB321FU / ZUI / Rooted** 上工作。

> ⚠️ 本应用依赖 Root 权限。不同 ROM / Framework 对 `cmd user` / `pm` / `am` 的命令入口和参数
> 可能不同。App 首次运行会自动检测当前 ROM 支持哪些命令，未确认的功能不会启用。

## 技术栈

- Kotlin + Coroutines + ViewModel + StateFlow
- AndroidX + Material 3 + ViewBinding
- 分层架构：Activity → ViewModel → Repository → RootService(su) → shell
- Gradle 8.9 / AGP 8.5.2 / compileSdk 35 / minSdk 26

## 构建

```bash
export JAVA_HOME=<JDK 17 路径>
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

或直接用 Android Studio 打开项目根目录。

## 安全设计

- User 0（系统主用户）永远保护，禁止删除/停止
- 非 CLONE 类型用户永远不显示为可管理分身
- 删除分身需二次确认 + 手动输入分身名称
- 清除数据 / 卸载应用需二次确认
- 关键系统应用（SystemUI / Settings / Launcher / 本应用自身）禁止卸载
- 所有 Root 命令有超时 / exitCode / stderr 捕获，UI 不崩溃

## 许可证

[MIT](LICENSE)
