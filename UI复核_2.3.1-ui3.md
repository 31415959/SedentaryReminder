# UI 复核记录 v2.3.1-ui3（修复验证）

> 复核对象：`APK\SedentaryReminder-v2.3.1-ui3.apk`（SHA256 `2F7AE81B…C86`，versionCode 21 / versionName 2.3.1）
> 复核范围：`42de5b6` 提交对上一轮（UI复核_2.3.1-ui2.md）问题的修复效果；模拟器 127.0.0.1:16448 实测，未改代码。
> 本次截图：`screenshots/v3_a_monitoring.png`（监测中）、`v3_b_stopped.png`（停止态）、`v3_c_restart.png`、`v3_d_alert.png`（全屏提醒）、`v3_e_stats.png`（统计页）、`v3_f_settings.png`（设置页）、`v3_g_drawer.png`（抽屉）、`v3_h_stats_scrolled.png`（统计页滚动）。

---

## 总体结论

**上一轮全部 P0/P1 与新发现 A–E 均已修复并经实测确认，无回归。** 剩余问题均为 P2 级细节（通知图标蓝色、菜单图标线宽、柱状图图例与配色），不阻塞发布。

---

## ✅ 修复确认（逐条实测）

### P0-1 按钮状态逻辑 —— ✅ 已修复
- 监测中：主按钮"停止监测"（v3_a 实测）；点击后转"未开始 / 开始监测"（v3_b 实测）；再次点击恢复监测（v3_c）。
- 无意义的独立"停止"按钮已移除，监测中/未监测均无冗余停止入口；未监测时统计卡数字变"—"占位。
- 代码：`btnStart` 文本随 `MonitorService.running` 切换，点击行为双态；`btnStop` 从布局移除，`SideNav`/`MainActivity` 无残留引用（grep 验证）。

### P0-2 顶部空白 / 进度条存在感 —— ✅ 已修复
- 新增 `progress_horizontal.xml`（圆角 4dp）：0% 时轨道 `#D8C5A6` 在卡面 `#F0E8DB` 上**清晰可见**（v3_a），不再是隐形线。
- 状态卡 paddingBottom 18→12dp、状态→统计 10→6dp、统计→控制 10→8dp；顶部信息组明显收紧。
- 代码：`setProgressColor()` 通过 LayerDrawable/ClipDrawable 动态改色，`progressTint`/`progressBackgroundTint` 已移除，不冲突。

### P1-3 按钮层级 —— ✅ 已修复
- 控制卡只剩主按钮（渐变实心 + 阴影）+ 一个居中小胶囊"测试提醒"，层级清晰。

### P1-4 统计卡数字+图标居中 —— ✅ 已修复
- 数字+图标水平居中成组（图标 marginStart 8dp），标签也居中；两卡对称。

### P1-5 状态文字颜色统一 —— ✅ 已修复（保留进度反馈）
- "正在监测"与正文均为深棕黑 `#3F362E`；时间数字用状态色（正常棕、≥80% 橙、超时深橙）并 **BOLD**（`renderDetail` 新增 `StyleSpan`）；未改变近目标转橙的反馈逻辑。

### P1-6 底部说明 —— ✅ 已修复（`text_hint_dark #7C7163`、13sp、行距 3dp）

### P2-8 状态栏 / 顶栏 —— ✅ 已修复
- `AppTheme`：`windowLightStatusBar=true` + `statusBarColor/@color/bg_main` —— 状态栏深色图标清晰可读，与页面同色（各页实测）。
- 顶栏背景改 `@color/bg_main`，与页面融为一体；菜单文字按钮换为 `ic_menu` 矢量三横线。

### 新问题 A/B/C/D —— ✅ 全部修复
- A 统计页绿数字 `#1E6B52` → `@color/text_title`，两卡数字一致（v3_e）。
- B 设置页 3 个 SeekBar 与 2 个 CheckBox → `@color/accent_brown`（v3_f）。
- C 统计摘要拆两行（16sp 粗 + 13sp 灰），"数据积累中"不再断行孤字（v3_e）。
- D 趋势图高度 170→190dp、Y 轴改动态 `maxTarget`（60 起、向上取整 10），数据点不再贴底；数据点与日期轴共用 `cx` 槽中心，对齐逻辑正确（读屏偏差系读图误差，非 bug）。

### 工程问题 E —— ✅ 已修复
- `colors.xml`/`styles.xml`/`values-v28/`/`btn_ghost`/`ic_steps`/`ic_bell`/`ic_menu`/`progress_horizontal` 均已提交（42de5b6）；README 指向 ui3 APK（1a5ddef）。

### 其他页面
- 全屏提醒（AlertActivity）：深棕底 + 白字 + 白色状态栏图标，可读、无主题改动回归（v3_d）。
- 抽屉（v3_g）、统计页分段控件/柱状图（v3_h）无回归。
- `tvWeekSummary` → `tvWeekSummary1/2` 重命名无残留引用。

---

## ⚠️ 残留 / 新发现（均为 P2 级，不阻塞）

1. **`ic_stat` 为蓝色**（`#2F6BFF` 圆环时钟，launcher 图标 + 通知小图标共用）：v3_d 通知卡片上蓝色圆环醒目，与暖棕体系冲突；若追求全链路暖色统一需改（launcher 图标变暖棕会牺牲辨识度，需产品判断）。
2. **`ic_menu` 线条 1.8dp**，与 `ic_steps`/`ic_bell` 的 1.5dp 不一致——P2-7"线条粗细统一"反而被新图标打破（建议统一为 1.5dp）。
3. **统计页柱状图双柱色过于接近**：提醒柱 `0xFF8A7F73`（灰褐）与活动柱 `0xFF8B6F5B`（摩卡棕）肉眼几乎无法区分；且图例文案"深色=提醒 · **橙色**=有效活动"与实际双色不符（无橙色元素）——建议图例改"深灰=提醒 · 摩卡=有效活动"或拉大两柱色差。
4. 趋势图在数据稀疏期（仅 2-3 天有数据）卡片仍显大片空白——数据特性，非缺陷；可选优化：无数据提示。


## ui4 修改记录（处理 P2 残留）

> 构建：Gradle `assembleRelease` 成功；APK `H:\SedentaryReminder\APK\SedentaryReminder-v2.3.1-ui4.apk`（64,988 字节），已安装 `127.0.0.1:16448` 与 `emulator-5556`。

- [x] **菜单图标线宽统一**：`ic_menu.xml` strokeWidth `1.8dp → 1.5dp`，与 `ic_steps`/`ic_bell` 一致。
- [x] **柱状图配色与图例**：`StatsChartView` 提醒柱 `0xFF8A7F73 → 0xFF3F362E`（深灰），活动柱保持 `0xFF8B6F5B`（摩卡棕），两柱对比拉大；周/月/年图例文案统一改为“深灰 = 提醒 · 摩卡 = 有效活动”，`activity_stats.xml` 默认图例同步。
- [ ] **`ic_stat` 蓝色图标**：未自动改。launcher 与通知小图标共用 `ic_stat.xml`（`#2F6BFF`），改成暖棕会影响桌面辨识度，属产品取舍；如需修改，下一轮可只改通知小图标或连 launcher 一起换暖棕。
- [ ] **趋势图数据稀疏期空提示**：本轮未加，可选优化。

### 实测
- `127.0.0.1:16448` 统计页 UI dump：`tvLegend` 文案已变为“深灰 = 提醒 · 摩卡 = 有效活动”。
- APK 已同步安装到两台模拟器，设备自动旋转已恢复。

---

## 附注

- 测试期间产生的数据污染（`t_20260822_alerts=1`、`enabled=false`）已从设备 prefs 清理，app 已恢复到监测运行状态。
- 上一轮复核中"关键数字高亮失效"为误判（坐标采样把"坐"字当作"0"），实际高亮一直正常，无需处理。
