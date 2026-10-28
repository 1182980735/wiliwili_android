# Bilibili 412 风控调研与专栏弹窗改造

## 412 是什么

B 站返回：

```text
错误号: 412
由于触发哔哩哔哩安全风控策略，该次访问请求被拒绝。
```

这是 B 站边缘/风控层拒绝请求，不一定是账号封禁，常见原因：

- 匿名 WebView 直接访问 H5 页面
- 缺少 App 登录 Cookie 或 buvid3/buvid4 指纹
- User-Agent、Referer、Origin 不像正常客户端
- WebView 的 TLS/HTTP2/JS 指纹与普通浏览器差异
- 同一 IP/设备短时间重复请求
- 直接访问 m.bilibili.com 的 opus 页面触发 App 拉起或安全校验

## 为什么之前会触发

旧实现让 WebView 直接加载：

```text
https://www.bilibili.com/opus/<id>
```

WebView 没有使用 App 的 `BiliClient` Cookie 链路，B 站返回的是匿名 H5 页面，可能跳转：

```text
bilibili://opus/detail/<id>
```

并且页面会注入“App 内打开 / 浏览方式”推广层。部分动态还会被 412 拒绝。

## 新方案

不再让 WebView 请求 B 站页面，改为：

```text
BiliClient（App Cookie / UA / Referer）
  -> /x/polymer/web-dynamic/v1/detail
  -> JSON
  -> AppPopup 原生渲染
```

接口：

```text
https://api.bilibili.com/x/polymer/web-dynamic/v1/detail?timezone_offset=-480&id=<id>
```

已验证使用 App 登录 Cookie 请求返回 `code=0`，并可从 `data.item.modules` 解析：

- 作者、发布时间
- 正文 `module_desc.text`
- 视频动态 `major.archive`
- 封面、时长、播放/弹幕数
- 点赞/评论数

## 优点

- 不再加载 B 站 H5，因此不会再触发 WebView 的 412 风控
- 不依赖浏览器/WebView 页面脚本
- 打开弹窗立即显示加载进度
- 内容使用应用内原生视图，遥控器焦点和主题样式一致
- 网络失败时显示卡片自身摘要作为兜底，不再跳到浏览器

## 注意

- 正文和视频信息以动态详情接口返回为准；部分超长专栏/Opus 可能需要后续补充分页正文接口。
- 如果 App 登录态失效，详情接口可能返回错误，此时弹窗显示卡片摘要和失败原因。
