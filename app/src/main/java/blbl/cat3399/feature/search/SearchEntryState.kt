package blbl.cat3399.feature.search

/**
 * 标记“下一次进入搜索页”应回到输入面板。
 * 返回搜索结果详情时不会设置该标记，因此结果页仍可保留。
 */
object SearchEntryState {
    @Volatile
    private var requestInputOnNextShow: Boolean = false

    fun requestInputOnNextShow() {
        requestInputOnNextShow = true
    }

    fun consumeRequestInputOnNextShow(): Boolean {
        val value = requestInputOnNextShow
        requestInputOnNextShow = false
        return value
    }
}
