package hrln.interactive.sidekit.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import hrln.interactive.sidekit.design.icons.SidekitIcon
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.design.theme.contentColorFor

/** Item "Colour Variant" from the Item component set (62:950). */
enum class ItemColour { Primary, Secondary, Success, Destructive, Muted }

@Composable
fun ItemColour.container(): Color = when (this) {
    ItemColour.Primary -> SidekitTheme.colors.surfaceA3
    ItemColour.Secondary -> SidekitTheme.colors.emphasisA2
    ItemColour.Success -> SidekitTheme.colors.successA1
    ItemColour.Destructive -> SidekitTheme.colors.dangerA2
    ItemColour.Muted -> SidekitTheme.colors.surfaceA2
}

/**
 * Rounded SideKit surface, the base of items, tiles and keys: 36 radius, 104 high by default.
 * Clickable only when [onClick] is set.
 */
@Composable
fun SidekitTile(
    modifier: Modifier = Modifier,
    color: Color = SidekitTheme.colors.surfaceA3,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .height(SidekitTheme.spacing.itemHeight)
            .clip(SidekitTheme.shapes.item)
            .background(color)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = contentAlignment,
        content = {
            CompositionLocalProvider(LocalSidekitContentColor provides SidekitTheme.colors.contentColorFor(color)) { content() }
        }
    )
}

/**
 * Item (62:950): Single Text, Dual Text (with [secondary]), Icon Text (with [icon]) and
 * Link (with [showLinkArrow]) variants.
 */
@Composable
fun SidekitItem(
    title: String,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    /** Put the secondary value at the row's trailing edge (toggles and compact state rows). */
    secondaryToEnd: Boolean = false,
    icon: SidekitIcon? = null,
    showLinkArrow: Boolean = false,
    /**
     * Keep [showLinkArrow] when [secondary] is present too. Off by default so rows that show a
     * value keep their existing look; settings rows that open another page opt in.
     */
    showLinkArrowWithSecondary: Boolean = false,
    colour: ItemColour = ItemColour.Primary,
    titleDecoration: TextDecoration? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable RowScope.() -> Unit)? = null
) {
    SidekitTile(
        modifier = modifier.fillMaxWidth(),
        color = colour.container(),
        onClick = onClick,
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SidekitTheme.spacing.itemPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SidekitTheme.spacing.itemContentGap)
        ) {
            leading?.invoke(this)
            if (icon != null) SidekitIconImage(icon)
            SidekitText(title, textDecoration = titleDecoration)
            if (secondary != null) {
                SidekitText(
                    secondary,
                    Modifier.weight(1f),
                    color = SidekitTheme.colors.surfaceA6,
                    textAlign = if (secondaryToEnd) TextAlign.End else null
                )
            }
            // The arrow marks a row that opens another page. With a secondary value it must be
            // requested explicitly, so existing rows keep the arrow off.
            if (showLinkArrow && (secondary == null || showLinkArrowWithSecondary)) {
                Box(
                    if (secondary == null) Modifier.weight(1f) else Modifier,
                    contentAlignment = Alignment.CenterEnd
                ) {
                    SidekitIconImage(SidekitIcon.ArrowRightAlt)
                }
            }
        }
    }
}

/**
 * Compact Item: the short 74-high row used for list-level actions such as "See more" and
 * "Search in". It keeps the item variants but steps the type scale down so the row still reads
 * as a row instead of a squeezed tile. A secondary value sits at the right edge so label/value
 * rows scan as two columns.
 */
@Composable
fun SidekitCompactItem(
    title: String,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    icon: SidekitIcon? = null,
    showLinkArrow: Boolean = false,
    colour: ItemColour = ItemColour.Primary,
    titleDecoration: TextDecoration? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable RowScope.() -> Unit)? = null
) {
    SidekitTile(
        modifier = modifier
            .fillMaxWidth()
            .height(SidekitTheme.spacing.compactRowHeight),
        color = colour.container(),
        onClick = onClick,
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SidekitTheme.spacing.itemPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SidekitTheme.spacing.itemContentGap)
        ) {
            leading?.invoke(this)
            if (icon != null) SidekitIconImage(icon)
            SidekitText(title, style = SidekitTheme.typography.bodySmall, textDecoration = titleDecoration)
            if (secondary != null) {
                SidekitText(
                    secondary,
                    Modifier.weight(1f),
                    style = SidekitTheme.typography.small,
                    color = LocalSidekitContentColor.current.copy(alpha = 0.82f),
                    textAlign = TextAlign.End
                )
            } else if (showLinkArrow) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    SidekitIconImage(SidekitIcon.ArrowRightAlt)
                }
            }
        }
    }
}

/** Item, Icon Only variant (104 x 104 by default; pass a weight to stretch it). */
@Composable
fun SidekitIconItem(
    icon: SidekitIcon,
    contentDescription: String,
    modifier: Modifier = Modifier.size(SidekitTheme.spacing.itemHeight),
    colour: ItemColour = ItemColour.Primary,
    color: Color = colour.container(),
    iconSize: Dp = SidekitTheme.spacing.iconSize,
    onClick: (() -> Unit)? = null
) {
    SidekitTile(modifier = modifier, color = color, onClick = onClick, onClickLabel = contentDescription) {
        SidekitIconImage(icon, size = iconSize, contentDescription = contentDescription)
    }
}

/** A row of items separated by the `item-outer-gap` token (Item Line 62:1045, grids, key rows). */
@Composable
fun SidekitItemRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SidekitTheme.spacing.itemOuterGap),
        content = content
    )
}

/** Free-height item surface with the standard 30 inset (note body, message bubbles, info cards). */
@Composable
fun SidekitCard(
    modifier: Modifier = Modifier,
    color: Color = SidekitTheme.colors.surfaceA3,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(SidekitTheme.shapes.item)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(SidekitTheme.spacing.itemPadding),
        content = {
            CompositionLocalProvider(LocalSidekitContentColor provides SidekitTheme.colors.contentColorFor(color)) { content() }
        }
    )
}
