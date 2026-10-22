package blbl.cat3399.feature.player

import androidx.lifecycle.lifecycleScope
import blbl.cat3399.core.ui.popup.AppPopup
import kotlinx.coroutines.launch

internal fun PlayerActivity.showDlnaCastDialog() {
    val progress =
        AppPopup.progress(
            context = this,
            title = "投屏（DLNA）",
            status = "正在通过 SSDP 搜索 DLNA 媒体渲染设备…",
            negativeText = "取消",
            cancelable = true,
        )
    lifecycleScope.launch {
        val devices = DlnaDiscovery.discover(timeoutMs = 4_000L)
        if (progress?.isShowing != true) return@launch
        progress.dismiss()
        if (devices.isEmpty()) {
            AppPopup.message(
                context = this@showDlnaCastDialog,
                title = "未发现 DLNA 设备",
                message = "未搜索到可用的 DLNA 媒体渲染设备。请确认电视/盒子与设备处于同一局域网且已开启 DLNA。\n\n当前版本尚未集成 DLNA 媒体推送与控制能力，即使发现设备也不会执行投屏。",
                positiveText = "知道了",
            )
            return@launch
        }
        AppPopup.singleChoice(
            context = this@showDlnaCastDialog,
            title = "发现 ${devices.size} 台 DLNA 设备（仅发现）",
            items = devices.map { "${it.name}\n${it.host}" },
            checkedIndex = 0,
        ) { index, _ ->
            val device = devices.getOrNull(index) ?: return@singleChoice
            AppPopup.message(
                context = this@showDlnaCastDialog,
                title = "投屏能力不可用",
                message = "已发现：${device.name}\n地址：${device.host}\n\n当前版本只实现了 DLNA 设备发现，尚未集成媒体推送与控制协议，无法启动投屏。",
                positiveText = "知道了",
            )
        }
    }
}
