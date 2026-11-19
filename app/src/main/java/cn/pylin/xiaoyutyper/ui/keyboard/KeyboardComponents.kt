package cn.pylin.xiaoyutyper.ui.keyboard

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.pylin.xiaoyutyper.R
import cn.pylin.xiaoyutyper.ime.KeyboardLayer
import cn.pylin.xiaoyutyper.ime.ShiftMode
import cn.pylin.xiaoyutyper.pinyin.PinyinCandidate

@Composable
fun KeyboardKey(
    modifier: Modifier = Modifier,
    kind: KeyboardKeyKind = KeyboardKeyKind.Letter,
    highlighted: Boolean = false,
    semanticsLabel: String? = null,
    onPress: () -> Unit,
    onRelease: () -> Unit = {},
    content: @Composable (Color) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val active = pressed || highlighted
    val colors = XiaoyuTheme.colors

    val backgroundColor = when {
        active -> colors.primary
        kind == KeyboardKeyKind.Function -> colors.surface
        else -> colors.surfaceMuted
    }
    val borderColor = if (active) colors.primary else colors.border
    val contentColor = when {
        active -> colors.onPrimary
        kind == KeyboardKeyKind.Function -> colors.textSecondary
        else -> colors.text
    }

    Box(
        modifier = modifier
            .clip(KeyboardKeyShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, KeyboardKeyShape)
            .then(
                if (semanticsLabel != null) {
                    Modifier.semantics {
                        role = Role.Button
                        onClick(label = semanticsLabel) {
                            currentOnPress()
                            currentOnRelease()
                            true
                        }
                    }
                } else {
                    Modifier
                },
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    pressed = true
                    currentOnPress()
                    try {
                        waitForUpOrCancellation()?.consume()
                    } finally {
                        pressed = false
                        currentOnRelease()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        content(contentColor)
    }
}

@Composable
fun KeyLabel(
    text: String,
    color: Color,
    fontSize: TextUnit = 17.sp,
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
    )
}

@Composable
fun KeyIcon(
    @DrawableRes iconRes: Int,
    color: Color,
    size: Dp = 19.dp,
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(size),
    )
}

@Composable
fun KeyboardHead(composing: String, floating: Boolean) {
    val colors = XiaoyuTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (floating) KeyboardLayout.WIDE_HEAD_HEIGHT_DP.dp else 28.dp)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (composing.isNotEmpty()) {
            Text(
                text = composing,
                modifier = Modifier.fillMaxWidth(0.7f),
                color = colors.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (floating) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(56.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(colors.borderStrong),
            )
        }
    }
}

@Composable
fun imeBottomBarHeight(): Dp {
    val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return maxOf(inset, 48.dp)
}

@Composable
fun KeyboardBody(
    modifier: Modifier = Modifier,
    floating: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = XiaoyuTheme.colors
    val shape = if (floating) RoundedCornerShape(8.dp) else RectangleShape
    val bottomPadding = if (floating) 10.dp else 8.dp + imeBottomBarHeight()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (floating) {
                    Modifier
                } else {
                    Modifier.shadow(
                        elevation = 10.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = colors.shadow,
                        spotColor = colors.shadow,
                    )
                },
            )
            .clip(shape)
            .background(colors.surface)
            .then(
                if (floating) {
                    Modifier.border(1.dp, colors.border, shape)
                } else {
                    Modifier.drawBehind {
                        drawLine(
                            color = colors.border,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                },
            )
            .padding(
                start = 6.dp,
                end = 6.dp,
                top = 2.dp,
                bottom = bottomPadding,
            ),
        content = content,
    )
}

@Composable
private fun KeyboardKind(
    @DrawableRes iconRes: Int,
    label: String,
) {
    val colors = XiaoyuTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KeyIcon(iconRes, colors.textMuted, 14.dp)
        Text(
            text = label,
            color = colors.textMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
fun KeyboardStatusBar(
    @DrawableRes iconRes: Int,
    label: String,
    onHide: () -> Unit,
) {
    val colors = XiaoyuTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        KeyboardKind(iconRes, label)
        Box(
            modifier = Modifier
                .heightIn(min = 28.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onHide,
                )
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.ime_action_hide),
                color = colors.primary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun CandidateButton(
    candidate: PinyinCandidate,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val colors = XiaoyuTheme.colors
    Box(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clip(KeyboardKeyShape)
            .background(if (pressed) colors.primary else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = candidate.word,
            color = if (pressed) colors.onPrimary else colors.text,
            fontSize = 15.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun CandidateRow(
    candidates: List<PinyinCandidate>,
    @StringRes hintRes: Int?,
    onSelect: (PinyinCandidate) -> Unit,
) {
    if (candidates.isEmpty()) {
        val hint = if (hintRes != null) stringResource(hintRes) else ""
        val colors = XiaoyuTheme.colors
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = hint,
                color = colors.textMuted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        return
    }

    val listState = rememberLazyListState()
    LaunchedEffect(candidates) {
        listState.scrollToItem(0)
    }
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(
            items = candidates,
            key = { candidate -> candidate.word },
        ) { candidate ->
            CandidateButton(candidate = candidate, onClick = { onSelect(candidate) })
        }
    }
}

@Composable
fun RowScope.SymbolToggleKey(
    layer: KeyboardLayer,
    @StringRes backLabelRes: Int,
    onToggle: () -> Unit,
) {
    val label = stringResource(
        if (layer == KeyboardLayer.Letters) R.string.ime_key_symbols else backLabelRes,
    )
    KeyboardKey(
        modifier = Modifier
            .weight(2f)
            .fillMaxHeight(),
        kind = KeyboardKeyKind.Function,
        semanticsLabel = label,
        onPress = onToggle,
    ) { color ->
        if (layer == KeyboardLayer.Letters) {
            KeyLabel(text = label, color = color, fontSize = 12.sp)
        } else {
            KeyIcon(R.drawable.ic_ime_arrow_left, color)
        }
    }
}

@Composable
fun RowScope.SpaceKey(
    weight: Float,
    onSpace: () -> Unit,
) {
    val label = stringResource(R.string.ime_key_space)
    KeyboardKey(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight(),
        semanticsLabel = label,
        onPress = onSpace,
    ) { color ->
        KeyLabel(text = label, color = color, fontSize = 13.sp)
    }
}

@Composable
private fun RowScope.BackspaceKey(
    onDown: () -> Unit,
    onReleased: () -> Unit,
) {
    KeyboardKey(
        modifier = Modifier
            .weight(1.5f)
            .fillMaxHeight(),
        kind = KeyboardKeyKind.Function,
        semanticsLabel = stringResource(R.string.ime_key_backspace),
        onPress = onDown,
        onRelease = onReleased,
    ) { color ->
        KeyIcon(R.drawable.ic_ime_backspace, color)
    }
}

private val letterRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

@Composable
fun ColumnScope.LetterKeyRows(
    shift: ShiftMode,
    onShift: () -> Unit,
    onLetter: (String) -> Unit,
    onBackspaceDown: () -> Unit,
    onBackspaceReleased: () -> Unit,
) {
    val uppercase = shift != ShiftMode.Off
    val shiftLabel = stringResource(R.string.ime_key_shift)

    letterRows.forEachIndexed { index, row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (index == 2) {
                KeyboardKey(
                    modifier = Modifier
                        .weight(1.5f)
                        .fillMaxHeight(),
                    kind = KeyboardKeyKind.Function,
                    highlighted = shift != ShiftMode.Off,
                    semanticsLabel = shiftLabel,
                    onPress = onShift,
                ) { color ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        KeyIcon(
                            iconRes = R.drawable.ic_ime_arrow_up,
                            color = color,
                            size = 19.dp,
                        )
                        if (shift == ShiftMode.Locked) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(5.dp)
                                    .size(4.dp)
                                    .background(color, CircleShape),
                            )
                        }
                    }
                }
            }

            row.forEach { char ->
                KeyboardKey(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    semanticsLabel = char.toString(),
                    onPress = { onLetter(char.toString()) },
                ) { color ->
                    KeyLabel(
                        text = if (uppercase) char.uppercaseChar().toString() else char.toString(),
                        color = color,
                    )
                }
            }

            if (index == 2) {
                BackspaceKey(onBackspaceDown, onBackspaceReleased)
            }
        }
    }
}

@Composable
fun ColumnScope.SymbolKeyRows(
    rows: List<List<String>>,
    moreSymbols: Boolean,
    onToggleMore: () -> Unit,
    onSymbol: (String) -> Unit,
    onBackspaceDown: () -> Unit,
    onBackspaceReleased: () -> Unit,
) {
    val toggleLabel = stringResource(
        if (moreSymbols) R.string.ime_symbols_back else R.string.ime_symbols_more,
    )
    rows.forEachIndexed { index, row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (index == 2) {
                KeyboardKey(
                    modifier = Modifier
                        .weight(1.5f)
                        .fillMaxHeight(),
                    kind = KeyboardKeyKind.Function,
                    semanticsLabel = toggleLabel,
                    onPress = onToggleMore,
                ) { color ->
                    KeyLabel(
                        text = toggleLabel,
                        color = color,
                        fontSize = 11.sp,
                    )
                }
            }

            row.forEach { symbol ->
                KeyboardKey(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    semanticsLabel = symbol,
                    onPress = { onSymbol(symbol) },
                ) { color ->
                    KeyLabel(text = symbol, color = color)
                }
            }

            if (index == 2) {
                BackspaceKey(onBackspaceDown, onBackspaceReleased)
            }
        }
    }
}

@Composable
fun KeyboardRowsContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        content = content,
    )
}

@Composable
fun KeyboardScaffold(
    height: Dp,
    floating: Boolean,
    head: @Composable () -> Unit,
    bar: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        head()
        KeyboardBody(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            floating = floating,
        ) {
            bar()
            KeyboardRowsContainer(
                modifier = Modifier.weight(1f),
                content = content,
            )
        }
    }
}
