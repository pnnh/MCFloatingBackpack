# Data Model: 可见可拖动空面板

## FloatingPanelState（运行时，客户端）

进入世界后由客户端持有；退出世界时可清空「拖动中」等瞬态，但坐标应已落盘。

| 字段 | 规则 |
| --- | --- |
| visible | 面板是否绘制。由快捷键切换。默认 `false`（进世界不自动弹出）。 |
| posX, posY | 面板左上角的 GUI 坐标（与当前 GUI 缩放一致的逻辑像素）。 |
| width, height | 固定占位尺寸（实现选定常量，本阶段不可配置）。 |
| dragging | 是否正在拖动。 |
| dragOffsetX/Y | 按下时指针相对面板原点的偏移，用于跟手拖动。 |

校验：

- `visible == false` 时不绘制、不处理面板命中（快捷键仍可改为 true）。
- 每次更新位置后（及从配置加载后）运行钳制，保证面板与屏幕相交且标题栏可点。

## PanelLayoutPreference（持久化，客户端本地）

| 字段 | 规则 |
| --- | --- |
| posX, posY | 上次松开拖动后的坐标。 |
| （可选）version | 配置格式版本，便于以后加缩放字段。 |

关系：`FloatingPanelState` 启动时从偏好加载坐标；拖动结束写回偏好。`visible` 默认不持久化（每次进世界关闭），除非实现时明确选择持久化开关状态——本特征默认不持久化可见性。

## PanelHitZone（派生，不持久化）

| 区域 | 规则 |
| --- | --- |
| dragBar | 顶部标题条；按下开始拖动。 |
| body | 面板其余区域；吞点击，无物品逻辑。 |
| outside | 不拦截，事件交给下层 Screen/世界。 |

## 状态转换

```text
hidden --(toggle)--> visible
visible --(toggle)--> hidden
visible + press dragBar --> dragging
dragging + move --> update pos
dragging + release --> clamp --> save preference --> visible
```

无服务端实体、无物品栈、无网络消息。
