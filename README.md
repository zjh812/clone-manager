# 多开应用管理（Clone Manager）

针对 **Lenovo TB321FU（ZUI，已 Root）** 的原生 Android CLONE Profile 管理器。

直接调用 Android Framework 已有的 `android.os.usertype.profile.CLONE` 用户/Profile 机制，**不是**虚拟机 / Parallel Space / 容器类方案。

## 设备实测命令

本仓库所有命令均在 TB321FU 实机验证通过，不猜命令：

| 操作 | 命令 |
|---|---|
| Root 检测 | `su -c id` → `uid=0(root)` |
| 用户列表 | `cmd user list` |
| 用户详情/类型/上限 | `dumpsys user` |
| 启动分身 | `am start-user -w <USER_ID>` |
| 停止分身 | `am stop-user -w -f <USER_ID>`（必须 `-f`）|
| 创建分身 | `pm create-user --profileOf 0 --user-type android.os.usertype.profile.CLONE "<名称>"` |
| 删除分身 | `pm remove-user <USER_ID>` |
| 重命名分身 | `pm rename-user <USER_ID> "<新名称>"` |
| 列分身内应用 | `pm list packages --user <USER_ID>` |
| 安装已有应用到分身 | `pm install-existing --user <USER_ID> <PKG>` |
| 分身内卸载 | `pm uninstall --user <USER_ID> <PKG>` |
| 清分身应用数据 | `pm clear --user <USER_ID> <PKG>` |
| 启动分身内应用 | `am start --user <USER_ID> -n <COMPONENT>` |

> 注意：ZUI 把 create-user/remove-user 从 `cmd user` 挪到了 `pm`，`cmd user create-user` 是 `Unknown command`。

## 特性

- 动态读取 `mMaxAllowedPerParent`（framework 改 10 / 30 自动适配，绝不硬编码）
- 只识别 `android.os.usertype.profile.CLONE`，User 0 与非 CLONE 用户永远保护
- 真实 userId（900/901/...），不使用列表序号代替
- 删除/卸载/清数据均二次确认（删除需手动输入分身名称）
- 所有 Root 操作异步、带 timeout、记录 stdout/stderr/exitCode
- 调试日志页（环形缓冲 300 条，复制/清空）

## 技术栈

Kotlin · Coroutines · ViewModel · StateFlow · Material 3 · ViewBinding · AGP 8.5.2 · Kotlin 2.0.20 · compileSdk 35 · minSdk 26

## 构建

```bash
gradle :app:assembleDebug
```

APK 安装后需授予 Root 权限。

## 安全声明

- 不修改 `/system/framework`、不自动 patch services.jar；`mMaxAllowedPerParent` 由使用者自行改 framework
- 不内置危险操作开关；V1 不提供高级模式
- 删除分身不可恢复，应用前请备份
