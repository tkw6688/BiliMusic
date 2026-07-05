package com.thehbc.bilimusic.data.utils

object BiliTitleParser {
    
    // 移除分P开头的数字编号或P标志，如 "P1. 晴天" -> "晴天", "02、七里香" -> "七里香", "3 - 稻香" -> "稻香"
    private val PAGE_PREFIX_REGEX = Regex("^(?i)(?:P\\d+\\s*[-——：:\\.]?\\s*|\\d+\\s*[-——：:\\.、]\\s*|\\d+\\s+)")

    /**
     * 清理分P特有的标题，仅剔除开头的序号前缀（如 "P1."、"02、"）
     */
    fun cleanPageTitle(rawPageTitle: String): String {
        val title = rawPageTitle.replace(PAGE_PREFIX_REGEX, "")
        return title.trim().removeSurrounding("-").trim().ifEmpty { rawPageTitle }
    }
}
