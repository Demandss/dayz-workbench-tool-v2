package com.github.demandss.dayz_workbench_tool.styles

/** The slots a state can hold for the widgets that draw a frame, in the order of the vanilla file. */
val STYLE_SLOTS = listOf("LeftTop", "Top", "RightTop", "Right", "RightBottom", "Bottom", "LeftBottom", "Center", "Left")

/** The states vanilla styles use. */
val STYLE_STATES = listOf("Normal", "Disabled", "Highlight", "Pushed", "Focus")

/** Replaces the style at [widget], [style] with the result of [change]. Out of range indices change nothing. */
fun StyleSheet.withStyleAt(
    widget: Int,
    style: Int,
    change: (StyleDef) -> StyleDef,
): StyleSheet {
    val w = widgets.getOrNull(widget) ?: return this
    val s = w.styles.getOrNull(style) ?: return this
    val styles = w.styles.toMutableList().also { it[style] = change(s) }
    return copy(widgets = widgets.toMutableList().also { it[widget] = w.copy(styles = styles) })
}

/** [name] or `name_2`, `name_3`... when a style of the widget type already has it. */
fun WidgetStyles.uniqueStyleName(name: String): String {
    val taken = styles.map { it.name }.toSet()
    if (name !in taken) return name
    var n = 2
    while ("${name}_$n" in taken) n++
    return "${name}_$n"
}

/** Appends an empty style to the widget type at [widget] and returns the sheet and the index of the new style. */
fun StyleSheet.withAddedStyle(
    widget: Int,
    name: String,
    imageSet: String = "",
): Pair<StyleSheet, Int>? {
    val w = widgets.getOrNull(widget) ?: return null
    val style =
        StyleDef(
            w.uniqueStyleName(name),
            font = "",
            imageSet = imageSet,
            color = "4294967295",
            states = listOf(StyleState("Normal", emptyList())),
        )
    val updated = w.copy(styles = w.styles + style)
    return copy(widgets = widgets.toMutableList().also { it[widget] = updated }) to updated.styles.lastIndex
}

/** A copy of the style right after it, named `<name>_copy`. */
fun StyleSheet.withDuplicatedStyle(
    widget: Int,
    style: Int,
): Pair<StyleSheet, Int>? {
    val w = widgets.getOrNull(widget) ?: return null
    val s = w.styles.getOrNull(style) ?: return null
    val copy = s.copy(name = w.uniqueStyleName(s.name + "_copy"))
    val styles = w.styles.toMutableList().also { it.add(style + 1, copy) }
    return copy(widgets = widgets.toMutableList().also { it[widget] = w.copy(styles = styles) }) to style + 1
}

fun StyleSheet.withoutStyle(
    widget: Int,
    style: Int,
): StyleSheet {
    val w = widgets.getOrNull(widget) ?: return this
    if (style !in w.styles.indices) return this
    return copy(widgets = widgets.toMutableList().also { it[widget] = w.copy(styles = w.styles.filterIndexed { i, _ -> i != style }) })
}

/** Sets the image of the slot [slot] in the state at [state]; an empty [image] keeps the slot with no image, like vanilla does. */
fun StyleDef.withItem(
    state: Int,
    slot: String,
    image: String,
): StyleDef {
    val st = states.getOrNull(state) ?: return this
    val items =
        if (st.items.any { it.name == slot }) {
            st.items.map { if (it.name == slot) it.copy(image = image) else it }
        } else {
            st.items + StyleItem(slot, image)
        }
    return copy(states = states.toMutableList().also { it[state] = st.copy(items = items) })
}

fun StyleDef.withoutItem(
    state: Int,
    slot: String,
): StyleDef {
    val st = states.getOrNull(state) ?: return this
    return copy(states = states.toMutableList().also { it[state] = st.copy(items = st.items.filter { i -> i.name != slot }) })
}

/** Adds a state with every slot of [slots] empty. A state with that name already there is left alone. */
fun StyleDef.withAddedState(
    name: String,
    slots: List<String> = STYLE_SLOTS,
): StyleDef = if (states.any { it.name == name }) this else copy(states = states + StyleState(name, slots.map { StyleItem(it, "") }))

fun StyleDef.withoutState(state: Int): StyleDef = copy(states = states.filterIndexed { i, _ -> i != state })

/** The ARGB number of a style colour as the eight hex digits `AARRGGBB`. */
fun styleColorHex(value: String): String =
    (value.trim().toLongOrNull() ?: 0xFFFFFFFFL)
        .and(0xFFFFFFFFL)
        .toString(16)
        .padStart(8, '0')
        .uppercase()

/** The decimal number for eight hex digits, or null when [hex] is not `AARRGGBB` (a leading `#` is fine). */
fun styleColorFromHex(hex: String): String? {
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 8 || clean.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) return null
    return clean.toLong(16).toString()
}
