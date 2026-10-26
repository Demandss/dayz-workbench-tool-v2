@file:Suppress("ktlint:standard:max-line-length")

package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.resources.*
import org.jetbrains.compose.resources.StringResource

/** How the value of a property is written in a .layout file and edited in the properties panel. */
internal enum class PropKind {
    /** `0` or `1`. */
    CHECK,

    /** One number. */
    NUMBER,

    /** Two or more numbers on one line, e.g. `8 0`. */
    NUMBERS,

    /** A text in quotes. */
    TEXT,

    /** One of [PropSpec.options], written as it is. */
    ENUM,

    /** `r g b a`, each from 0 to 1. */
    COLOR,
}

/** One property a widget class has in the Workbench: its name in the file, the kind of value and what it does. */
internal class PropSpec(
    val key: String,
    val kind: PropKind,
    val hint: StringResource,
    val options: List<String> = emptyList(),
    /** What the game does when the file does not set the property; shown as a hint, never written. */
    val default: String = "",
)

/** A group of properties shown under one header. */
internal class PropSection(
    val title: String,
    val specs: List<PropSpec>,
)

private fun check(
    key: String,
    hint: StringResource,
    default: String = "0",
) = PropSpec(key, PropKind.CHECK, hint, default = default)

private fun number(
    key: String,
    hint: StringResource,
    default: String = "",
) = PropSpec(key, PropKind.NUMBER, hint, default = default)

private fun numbers(
    key: String,
    hint: StringResource,
    default: String = "",
) = PropSpec(key, PropKind.NUMBERS, hint, default = default)

private fun text(
    key: String,
    hint: StringResource,
) = PropSpec(key, PropKind.TEXT, hint)

private fun color(
    key: String,
    hint: StringResource,
    default: String = "",
) = PropSpec(key, PropKind.COLOR, hint, default = default)

private fun choice(
    key: String,
    hint: StringResource,
    vararg options: String,
    default: String = "",
) = PropSpec(key, PropKind.ENUM, hint, options.toList(), default)

private val ALIGN_H = arrayOf("left", "center", "right")
private val ALIGN_V = arrayOf("top", "center", "bottom")

private val FOCUS =
    PropSection(
        "Focus",
        listOf(
            check(
                "no focus",
                Res.string.hint_on_the_widget_does_not_get_the_input,
            ),
            text("next up", Res.string.hint_name_of_the_widget_that_gets_the_focus),
            text("next down", Res.string.hint_name_of_the_widget_that_gets_the_focus_2),
            text("next left", Res.string.hint_name_of_the_widget_that_gets_the_focus_3),
            text("next right", Res.string.hint_name_of_the_widget_that_gets_the_focus_4),
        ),
    )

private val FONT =
    listOf(
        text("font", Res.string.the_path_to_the_font_without_the),
        check(
            "exact text",
            Res.string.hint_on_the_font_size_is_given_in_pixels,
        ),
        number("exact text size", Res.string.font_size_in_pixels_when_exact_text_is),
        number("text_proportion", Res.string.hint_font_size_as_a_share_of_the_widget, "1"),
        check("bold text", Res.string.on_bold_font),
        check("italic text", Res.string.on_italic_font),
        number("text sharpness", Res.string.hint_sharpness_of_the_font_edge_larger_is),
    )

private val TEXT_LOOK =
    listOf(
        choice("text halign", Res.string.hint_horizontal_alignment_of_the_text_inside, *ALIGN_H, default = "left"),
        choice("text valign", Res.string.hint_vertical_alignment_of_the_text_inside, *ALIGN_V, default = "top"),
        numbers("text offset", Res.string.offset_of_the_text_from_the_edge_in),
        numbers("text spacing", Res.string.hint_extra_spacing_between_characters_and),
        check("size to text h", Res.string.on_the_width_of_the_widget_fits_the_text),
        check("size to text v", Res.string.on_the_height_of_the_widget_fits_the),
        check("wrap", Res.string.on_long_text_wraps_onto_the_next_line),
        number("shadow size", Res.string.the_size_of_the_text_shadow_in_pixels_0),
        color("shadow color", Res.string.the_text_shadow_colour_r_g_b_a_from_0_to),
        numbers("shadow offset", Res.string.offset_of_the_shadow_x_y),
        number("shadow opacity", Res.string.opacity_of_the_shadow_from_0_to_1),
        number("outline size", Res.string.hint_thickness_of_the_text_outline_in_pixels),
        color("outline color", Res.string.hint_color_of_the_text_outline_r_g_b_a_from_0),
    )

private val RICH_EXTRA =
    listOf(
        check("condense whitespace", Res.string.hint_on_consecutive_spaces_are_collapsed_into),
        check("strip newlines", Res.string.hint_on_line_breaks_in_the_text_are_removed),
        numbers("content offset", Res.string.hint_offset_of_the_whole_content_x_y),
    )

private val STATE_COLORS =
    listOf(
        color("text color", Res.string.the_text_colour_r_g_b_a_from_0_to_1),
        color("disabled text color", Res.string.text_colour_of_a_disabled_widget),
    )

private fun focusable(
    title: String,
    vararg specs: PropSpec,
) = listOf(PropSection(title, specs.toList()), FOCUS)

/** The properties of a widget class beyond the common ones: the groups the properties panel shows for it. */
internal fun schemaFor(
    type: WidgetType,
    originalClassName: String = "",
): List<PropSection> =
    when (type) {
        WidgetType.TEXT -> {
            listOf(
                PropSection(
                    "Text",
                    listOf(text("text", Res.string.hint_text_of_the_widget_a_string_like_key_is)) + FONT + TEXT_LOOK +
                        listOf(color("text color", Res.string.the_text_colour_when_it_is_not_set_color)),
                ),
            )
        }

        WidgetType.MULTILINE_TEXT -> {
            listOf(
                PropSection(
                    "Text",
                    listOf(text("text", Res.string.hint_text_of_the_widget_a_string_like_key_is)) + FONT + TEXT_LOOK + RICH_EXTRA +
                        STATE_COLORS,
                ),
            )
        }

        WidgetType.RICH_TEXT -> {
            listOf(
                PropSection(
                    "Text",
                    listOf(text("text", Res.string.hint_text_of_the_widget_a_string_like_key_is)) + FONT + TEXT_LOOK + RICH_EXTRA +
                        STATE_COLORS,
                ),
            )
        }

        WidgetType.BUTTON -> {
            focusable(
                "Button",
                text("text", Res.string.hint_caption_of_the_button_a_string_like_key),
                choice(
                    "switch",
                    Res.string.hint_how_the_button_is_pressed_normal_is,
                    "normal",
                    "once",
                    "toggle",
                    default = "normal",
                ),
                text("font", Res.string.the_path_to_the_font_without_the),
                check(
                    "exact text",
                    Res.string.hint_on_the_font_size_is_given_in_pixels_2,
                ),
                number("exact text size", Res.string.font_size_in_pixels_when_exact_text_is),
                number("text_proportion", Res.string.hint_font_size_as_a_share_of_the_button, "1"),
                choice("text_halign", Res.string.hint_horizontal_alignment_of_the_caption, *ALIGN_H, default = "center"),
                choice("text_valign", Res.string.hint_vertical_alignment_of_the_caption, *ALIGN_V, default = "center"),
                numbers("text_offset", Res.string.offset_of_the_caption_from_the_edge_in),
                check("bold text", Res.string.on_bold_font),
                check("italic text", Res.string.on_italic_font),
                number("text sharpness", Res.string.hint_sharpness_of_the_font_edge_larger_is),
                color("text color", Res.string.the_caption_colour_r_g_b_a_from_0_to_1),
                color("disabled text color", Res.string.caption_colour_of_a_disabled_button),
                color("text outline color", Res.string.hint_color_of_the_caption_outline),
                color("text shadow color", Res.string.caption_shadow_colour),
            )
        }

        WidgetType.CHECKBOX, WidgetType.THREE_STATE_CHECKBOX -> {
            focusable(
                "CheckBox",
                check("checked", Res.string.on_the_box_is_checked_when_shown),
                text("text", Res.string.the_caption_next_to_the_box),
                text("font", Res.string.hint_path_to_the_font_without_the_extension),
                color("text color", Res.string.the_caption_colour_r_g_b_a_from_0_to_1),
                number("text outline size", Res.string.hint_thickness_of_the_caption_outline_in),
                color("text outline color", Res.string.hint_color_of_the_caption_outline),
                color("text shadow color", Res.string.caption_shadow_colour),
            )
        }

        WidgetType.EDIT_BOX, WidgetType.PASSWORD_EDIT_BOX -> {
            focusable(
                if (type == WidgetType.EDIT_BOX) "EditBox" else "PasswordEditBox",
                text("text", Res.string.the_text_in_the_field_when_shown),
                check("Use default text", Res.string.on_while_the_field_is_empty_the_hint),
                check("limit visible", Res.string.on_the_visible_part_of_the_line_is),
                *(
                    if (type ==
                        WidgetType.PASSWORD_EDIT_BOX
                    ) {
                        arrayOf(check("hide text", Res.string.on_characters_are_replaced_by_dots))
                    } else {
                        emptyArray()
                    }
                ),
                text("font", Res.string.hint_path_to_the_font_without_the_extension),
                check("exact text", Res.string.on_the_font_size_is_given_in_pixels),
                number("exact text size", Res.string.font_size_in_pixels_when_exact_text_is),
                choice("text halign", Res.string.hint_horizontal_alignment_of_the_text, *ALIGN_H, default = "left"),
                choice("text valign", Res.string.hint_vertical_alignment_of_the_text, *ALIGN_V, default = "top"),
                check("bold text", Res.string.on_bold_font),
                number("text sharpness", Res.string.hint_sharpness_of_the_font_edge_larger_is),
                color("text color", Res.string.the_colour_of_the_typed_text_r_g_b_a),
                color("Default text color", Res.string.the_colour_of_the_hint_of_an_empty_field),
                color("disabled text color", Res.string.text_colour_of_a_disabled_field),
                color("text outline color", Res.string.hint_color_of_the_text_outline),
            )
        }

        WidgetType.MULTILINE_EDIT_BOX -> {
            focusable(
                "MultilineEditBox",
                text("text", Res.string.the_text_in_the_field_when_shown),
                number("lines", Res.string.hint_how_many_lines_are_visible_in_the_field),
                check("limit visible", Res.string.on_the_visible_part_is_limited_by_the),
                check("size to text v", Res.string.on_the_height_of_the_field_fits_the_text),
                check("size to text h", Res.string.on_the_width_of_the_field_fits_the_text),
                text("font", Res.string.hint_path_to_the_font_without_the_extension),
                number("exact text size", Res.string.font_size_in_pixels),
                number("text_proportion", Res.string.hint_font_size_as_a_share_of_the_widget_2),
                check("bold text", Res.string.on_bold_font),
                check("italic text", Res.string.on_italic_font),
                color("text color", Res.string.the_text_colour_r_g_b_a_from_0_to_1),
            )
        }

        WidgetType.SLIDER -> {
            focusable(
                "Slider",
                number("minimum", Res.string.hint_the_smallest_value_of_the_slider, "0"),
                number("maximum", Res.string.the_largest_value_of_the_slider, "1"),
                number("current", Res.string.hint_value_of_the_slider_when_shown),
                number("step", Res.string.the_step_of_the_value_0_means_smooth),
                check("vertical", Res.string.on_the_slider_is_vertical),
                check("flipped", Res.string.on_the_values_run_in_the_opposite),
                check("fill in", Res.string.hint_on_the_passed_part_of_the_bar_is_filled),
                check("draw marker", Res.string.on_a_marker_of_the_current_value_is),
                number("marker thickness", Res.string.hint_thickness_of_the_marker_as_a_share_of),
                check("listen to input", Res.string.hint_on_the_slider_reacts_to_the_mouse_and, default = "1"),
            )
        }

        WidgetType.PROGRESS_BAR -> {
            listOf(
                PropSection(
                    "ProgressBar",
                    listOf(
                        number("minimum", Res.string.hint_the_value_at_which_the_bar_is_empty, "0"),
                        number("maximum", Res.string.the_value_at_which_the_bar_is_full, "100"),
                        number("current", Res.string.hint_the_current_value_when_shown),
                        check("vertical", Res.string.on_the_bar_grows_from_the_bottom_up),
                        check("flipped", Res.string.on_the_bar_grows_in_the_opposite),
                    ),
                ),
            )
        }

        WidgetType.XCOMBO_BOX -> {
            focusable(
                "XComboBox",
                text("items", Res.string.list_items_separated_by_semicolons_alpha),
                number("current item", Res.string.hint_number_of_the_selected_item_when_shown),
                text("text", Res.string.the_caption_while_nothing_is_selected),
                text("font", Res.string.hint_path_to_the_font_without_the_extension),
                color("text color", Res.string.the_text_colour_r_g_b_a_from_0_to_1),
                check("bold text", Res.string.on_bold_font),
                number("text sharpness", Res.string.hint_sharpness_of_the_font_edge),
            )
        }

        WidgetType.SPACER, WidgetType.WRAP_SPACER, WidgetType.GRID_SPACER -> {
            val title =
                when (type) {
                    WidgetType.SPACER -> "Spacer"
                    WidgetType.WRAP_SPACER -> "WrapSpacer"
                    else -> "GridSpacer"
                }
            val grid =
                if (type == WidgetType.GRID_SPACER) {
                    listOf(
                        number("Columns", Res.string.hint_how_many_columns_the_grid_has),
                        number("Rows", Res.string.how_many_rows_in_the_grid),
                    )
                } else {
                    emptyList()
                }
            val content =
                if (type == WidgetType.SPACER) {
                    emptyList()
                } else {
                    listOf(
                        choice("content_halign", Res.string.hint_how_the_row_of_children_is_aligned, *ALIGN_H, default = "left"),
                        choice("content_valign", Res.string.hint_how_the_row_of_children_is_aligned_2, *ALIGN_V, default = "top"),
                    )
                }
            listOf(
                PropSection(
                    title,
                    grid +
                        listOf(
                            number("Padding", Res.string.hint_padding_from_the_edge_of_the_widget_to),
                            number("Margin", Res.string.hint_distance_between_the_children_in_pixels),
                            check("Size To Content H", Res.string.hint_on_the_width_of_the_widget_fits_the),
                            check("Size To Content V", Res.string.hint_on_the_height_of_the_widget_fits_the),
                            check("Ignore invisible", Res.string.hint_on_hidden_children_take_no_space),
                        ) + content,
                ),
            )
        }

        WidgetType.SCROLL -> {
            listOf(
                PropSection(
                    "Scroll",
                    listOf(
                        check("Scrollbar V", Res.string.on_the_vertical_scroll_bar_is_shown),
                        check("Scrollbar H", Res.string.on_the_horizontal_scroll_bar_is_shown),
                        check("Scrollbar V Left", Res.string.on_the_vertical_bar_is_on_the_left),
                        check("Ignore invisible", Res.string.hint_on_hidden_children_are_not_part_of_the),
                    ),
                ),
            )
        }

        WidgetType.WINDOW -> {
            listOf(
                PropSection(
                    "Window",
                    listOf(
                        check("title visible", Res.string.on_the_title_of_the_window_is_shown),
                        text("text", Res.string.title_of_the_window),
                        text("font", Res.string.hint_path_to_the_font_without_the_extension),
                    ) + STATE_COLORS,
                ),
            )
        }

        WidgetType.TEXT_LISTBOX, WidgetType.SERVER_BROWSER -> {
            focusable(
                if (type == WidgetType.TEXT_LISTBOX) "TextListbox" else "ServerBrowser",
                check("title visible", Res.string.on_the_row_of_titles_is_shown),
                number("colums", Res.string.how_many_columns_spelled_so_in_the_file),
                number("lines", Res.string.hint_how_many_lines_are_visible),
                *(
                    if (type == WidgetType.TEXT_LISTBOX) {
                        arrayOf(
                            check("highlight row", Res.string.on_the_row_under_the_cursor_is),
                            color("text color", Res.string.the_text_colour_r_g_b_a_from_0_to_1),
                        )
                    } else {
                        emptyArray()
                    }
                ),
            )
        }

        WidgetType.HTML -> {
            listOf(PropSection("Html", listOf(check("wrap", Res.string.on_long_text_wraps))))
        }

        WidgetType.EMBEDDED -> {
            listOf(
                PropSection(
                    "Embedded",
                    listOf(
                        text("layout", Res.string.the_path_to_the_layout_shown_inside_the),
                        text("Caption", Res.string.hint_the_caption_that_the_embedded_layout),
                    ),
                ),
            )
        }

        WidgetType.ITEM_PREVIEW -> {
            listOf(PropSection("ItemPreview", listOf(check("force flip enable", Res.string.hint_on_the_item_can_be_flipped))))
        }

        WidgetType.VIDEO -> {
            listOf(
                PropSection(
                    "Video",
                    listOf(
                        text("text", Res.string.a_caption_over_the_video),
                        color("text color", Res.string.caption_colour),
                        numbers("text offset", Res.string.offset_of_the_caption_x_y),
                        color("text background", Res.string.hint_background_color_of_the_caption),
                        color("background color", Res.string.hint_background_color_of_the_video),
                        number("outline size", Res.string.hint_thickness_of_the_outline_in_pixels),
                        color("outline color", Res.string.hint_color_of_the_outline),
                    ),
                ),
            )
        }

        else -> {
            if (originalClassName == "SimpleProgressBarWidgetClass") {
                listOf(
                    PropSection(
                        "SimpleProgressBar",
                        listOf(
                            number("minimum", Res.string.hint_the_value_at_which_the_bar_is_empty, "0"),
                            number("maximum", Res.string.the_value_at_which_the_bar_is_full, "100"),
                            number("current", Res.string.hint_the_current_value_when_shown),
                        ),
                    ),
                )
            } else {
                emptyList()
            }
        }
    }

/** Every property the panel has a row for, for this class. */
internal fun schemaKeys(
    type: WidgetType,
    originalClassName: String = "",
): Set<String> = schemaFor(type, originalClassName).flatMap { section -> section.specs.map { it.key } }.toSet()
