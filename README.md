# Accessories to Curios

Minecraft **1.21.1 NeoForge** 反向桥接模组：将依赖 **Accessories** 的模组饰品、槽位、能力接口、属性加成、生命周期回调、3D/第一人称渲染与交互逻辑全部合并进 **Curios API**，并移除重复的 Accessories 背包按钮、快捷键与独立饰品界面。

## 功能特性

- **统一饰品栏入口**：隐藏生存/创造背包中的重复 Accessories 与兼容模组饰品按钮及快捷键，服务端/客户端自动将打开 Accessories 界面的请求重定向至 `CuriosScreen`（保留箭袋、腰带等独立物品容器界面）。
- **槽位自动同步与本地化**：自动把 Accessories 的通用槽位与 `UniqueSlotHandling` 独立槽位（如天境 `aether:*`）同步注册到 Curios，保留原有栏位翻译与图标，过滤内部护甲代理槽位。
- **完整能力与生命周期桥接**：通过 `BridgedCurio` 与 `AccessoriesCapability` 双向实时同步，支持右键穿戴、Shift+左键优先级、常规与动态属性/`SlotAttribute` 扩缩容、`onEquip`/`onUnequip`/`tick`、掉落规则（`accessories.keepAccessoryInventory`）、Tooltip 本地化、旧存档迁移与小眼睛渲染显隐开关。

## 构建

```powershell
$env:JAVA_HOME = 'C:\Users\30819\.jdks\ms-21.0.11'
.\gradlew.bat build
```
