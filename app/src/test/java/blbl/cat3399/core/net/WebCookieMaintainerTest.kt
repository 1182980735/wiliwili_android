package blbl.cat3399.core.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebCookieMaintainerTest {
    @Test
    fun regenerateBuvid3OnlyWhenValueEndsWithInfoc() {
        assertTrue(shouldRegenerateBuvid3("ABC-INFOC"))
        assertTrue(shouldRegenerateBuvid3(" infoc "))
        assertFalse(shouldRegenerateBuvid3("ABC-INFO"))
        assertFalse(shouldRegenerateBuvid3(""))
        assertFalse(shouldRegenerateBuvid3(null))
    }
}
