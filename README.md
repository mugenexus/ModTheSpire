# ModTheSpire Alter

《杀戮尖塔》模组加载器，可通过 Steam 的 **Play with Mods** 打开中文启动器。

## 安装

1. 下载并解压 [最新版](https://github.com/mugenexus/ModTheSpire-Alter/releases/latest)。
2. 通过 Steam 启动时，保留 [原版 ModTheSpire](https://steamcommunity.com/sharedfiles/filedetails/?id=1605060445) 的工坊订阅。关闭启动器，在对应 Steam 库的 `steamapps/workshop/content/646570/1605060445/` 中备份并替换 `ModTheSpire.jar`，然后选择 **Play with Mods**。Steam 重新下载工坊内容时可能覆盖改版。
3. 手动启动时，将 `ModTheSpire.jar` 与对应系统的启动脚本放在游戏目录。Windows 运行 `MTS.cmd`，Linux 运行 `sh MTS.sh`。Linux 脚本依次查找游戏自带的 Java 8、`JAVA_HOME` 和 `PATH`。本地模组 JAR 放在游戏目录的 `mods/` 中。

## 使用

- **模组备注**：右键选择 **设置模组备注**，或点击详情区的备注按钮。备注首行作为显示名称，其余行可记录补充信息。备注跨配置组共享，清空即可恢复原名。搜索支持原名和备注名称。
- **显示模式**：通过 **切到列表** 和 **切到封面** 在全列表与全图标之间切换。两种视图按同一加载顺序显示所有模组。无封面时显示 Steam 图标，右键菜单可以设置封面。封面模式支持右键拖动和 Alt+方向键排序。
- **封面缓存**：缩略图会存到本地，下次打开优先读取。点击 **封面缓存** 可查看占用并清除。
- **布局**：拖动分隔线可调整模组区和详情区宽度，重开时恢复。
- **配置组**：在 **本次启用** 一栏勾选要一起启动的组，至少保留一组。上方下拉框用于选择当前编辑的组。重复模组只加载一次，各组内容独立保存。底部会显示加载数量和缺失前置提示。
- **日志**：点击 **启动游戏** 后，可用 **分析错误** 提取异常摘要，或用 **保存日志** 导出完整日志。

## 构建

使用 JDK 8，把游戏自带的 `desktop-1.0.jar` 放到仓库同级的 `lib/` 目录，再运行 `mvnw.cmd package`（Windows）或 `./mvnw package`（Linux）。构建产物位于 `target/ModTheSpire.jar`。

模组开发参考[原版 Wiki](https://github.com/kiooeht/ModTheSpire/wiki/SpirePatch)，上游变更见 [CHANGELOG](CHANGELOG.md)。

## 致谢

kiooeht（原作者）、t-larson（多模组加载和界面）、test447（启动器和路径定位）、reckter（Maven 构建）、FlipskiZ（模组初始化）、pk27602017（UTF-8 支持）、bwwq（界面主题）。本项目沿用 [MIT 许可证](LICENSE)。
