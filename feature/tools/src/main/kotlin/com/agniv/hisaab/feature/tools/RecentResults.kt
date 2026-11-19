package com.agniv.hisaab.feature.tools

import com.agniv.hisaab.core.domain.tools.RecentResult

/** A result for "Recently used", stamped now. What [a], [b] and [c] mean per tool is read back in the hub. */
internal fun recent(
    tool: Tool,
    a: Long,
    b: Long = 0,
    c: Long = 0,
) = RecentResult(tool.name, System.currentTimeMillis(), a, b, c)
