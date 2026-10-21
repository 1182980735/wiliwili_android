# wiliwili Android 技术验证版 — 来源记录

本项目仅用于个人、不对外分发的技术验证，目的是验证在不改动 wiliwili 上游代码的前提下，
基于 Android 原生界面栈复刻 wiliwili 的视觉与遥控/手柄操作体验是否可行。

## 代码来源

| 用途 | 来源 | 版本 |
| --- | --- | --- |
| Android 宿主、网络、登录、播放器、UI 基础 | https://github.com/1182980735/wiliwili.git 同工作区的 `blbl-main` 参考工程 | Git HEAD `c9514a66`（main） |
| wiliwili 视觉规范、交互与资源参考 | https://github.com/xfangfang/wiliwili | Git HEAD `88e5876`（yoga，v1.6.0） |

## 使用边界

- 本目录仅作个人技术验证，不发布、不分发、不用于商业用途。
- `blbl-main` 当前未声明 LICENSE；本目录中的复制代码不改变其版权归属。
- 引用 wiliwili 的名称、颜色、交互设计和 129 个 SVG 图标仅用于个人研究；wiliwili 上游采用 GPLv3。
- 生成的 `wiliwili_*.xml` VectorDrawable 由 `resources/svg/*.svg` 批量转换而来，转换脚本保留在 `android/tools/svg_to_vector.py`。
- 如未来需要分发，必须先取得 blbl-main 作者授权并重新审查全部第三方依赖/资源许可证。
- IjkPlayer AAR、B 站接口及相关第三方资源按各自上游条件使用；本验证版不应作为可分发产品。

## 本次技术验证目标

1. 用 Android View 体系重建 wiliwili 的“侧边栏 + 顶部二级 Tab + 视频网格”主壳。
2. 复用 Android 侧成熟的 OKHttp/WBI/登录/Media3 播放能力，验证完整链路。
3. 以 theme token 实现 wiliwili 深色主题（主色 #FF6699）和遥控焦点态。
4. 保留来源记录，避免误将参考工程当作可再分发依赖。
