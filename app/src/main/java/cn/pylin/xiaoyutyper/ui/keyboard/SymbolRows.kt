package cn.pylin.xiaoyutyper.ui.keyboard

import cn.pylin.xiaoyutyper.ime.PinyinMode

internal fun dailySymbolRows(mode: PinyinMode, more: Boolean): List<List<String>> =
    if (mode == PinyinMode.Chinese) {
        if (more) chineseSymbolRowsMore else chineseSymbolRows
    } else {
        if (more) englishSymbolRowsMore else englishSymbolRows
    }

internal fun secureSymbolRows(more: Boolean): List<List<String>> =
    if (more) secureRowsMore else secureRows

private val numberRow = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

private val chineseSymbolRows = listOf(
    numberRow,
    listOf("-", "/", ":", ";", "(", ")", "¥", "&", "@", "\""),
    listOf("“", "”", "、", "？", "！", "…", "~"),
)

private val chineseSymbolRowsMore = listOf(
    listOf("【", "】", "「", "」", "（", "）", "《", "》", "〈", "〉"),
    listOf("“", "”", "‘", "’", "·", "—", "￥", "%", "+", "="),
    listOf("：", "；", "？", "！", "…", "、", "~"),
)

private val englishSymbolRows = listOf(
    numberRow,
    listOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "\""),
    listOf("#", "%", "?", "!", "'", "_", "="),
)

private val englishSymbolRowsMore = listOf(
    listOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "="),
    listOf("\\", "|", "~", "`", "<", ">", ":", ";", "(", ")"),
    listOf("-", "/", "?", "!", "'", "_", "\""),
)

private val secureRows = listOf(
    numberRow,
    listOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "\""),
    listOf(".", ",", "?", "!", "'", "_", "="),
)

private val secureRowsMore = listOf(
    listOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "="),
    listOf("\\", "|", "~", "`", "<", ">", ":", ";", "(", ")"),
    listOf(".", ",", "?", "!", "'", "_", "\""),
)
