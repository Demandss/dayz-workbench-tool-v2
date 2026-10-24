@file:Suppress("ktlint:standard:max-line-length")

package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import com.github.demandss.dayz_workbench_tool.layout.WidgetType

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
    val hint: String,
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
    hint: String,
    default: String = "0",
) = PropSpec(key, PropKind.CHECK, hint, default = default)

private fun number(
    key: String,
    hint: String,
    default: String = "",
) = PropSpec(key, PropKind.NUMBER, hint, default = default)

private fun numbers(
    key: String,
    hint: String,
    default: String = "",
) = PropSpec(key, PropKind.NUMBERS, hint, default = default)

private fun text(
    key: String,
    hint: String,
) = PropSpec(key, PropKind.TEXT, hint)

private fun color(
    key: String,
    hint: String,
    default: String = "",
) = PropSpec(key, PropKind.COLOR, hint, default = default)

private fun choice(
    key: String,
    hint: String,
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
                "Вкл: виджет не получает фокус ввода (клавиатура и геймпад обходят его).\nВыкл: виджет может получить фокус.",
            ),
            text("next up", "Имя виджета, который получает фокус при нажатии «вверх» (навигация геймпадом и стрелками)"),
            text("next down", "Имя виджета, который получает фокус при нажатии «вниз»"),
            text("next left", "Имя виджета, который получает фокус при нажатии «влево»"),
            text("next right", "Имя виджета, который получает фокус при нажатии «вправо»"),
        ),
    )

private val FONT =
    listOf(
        text("font", "Путь к шрифту без расширения, например gui/fonts/MetronBook; пусто — шрифт по умолчанию"),
        check(
            "exact text",
            "Вкл: размер шрифта задан в пикселях (exact text size).\nВыкл: размер считается долей высоты виджета (text_proportion).",
        ),
        number("exact text size", "Размер шрифта в пикселях при включённом exact text"),
        number("text_proportion", "Размер шрифта как доля высоты виджета, когда exact text выключен", "1"),
        check("bold text", "Вкл: жирный шрифт."),
        check("italic text", "Вкл: курсив."),
        number("text sharpness", "Резкость контура шрифта: больше значит резче"),
    )

private val TEXT_LOOK =
    listOf(
        choice("text halign", "Выравнивание текста по горизонтали внутри виджета", *ALIGN_H, default = "left"),
        choice("text valign", "Выравнивание текста по вертикали внутри виджета", *ALIGN_V, default = "top"),
        numbers("text offset", "Сдвиг текста от края в пикселях: «x y»"),
        numbers("text spacing", "Дополнительный интервал между символами и строками: «x y»"),
        check("size to text h", "Вкл: ширина виджета подгоняется под текст."),
        check("size to text v", "Вкл: высота виджета подгоняется под текст."),
        check("wrap", "Вкл: длинный текст переносится на следующую строку."),
        number("shadow size", "Размер тени текста в пикселях; 0 — без тени"),
        color("shadow color", "Цвет тени текста: r g b a от 0 до 1"),
        numbers("shadow offset", "Смещение тени: «x y»"),
        number("shadow opacity", "Непрозрачность тени от 0 до 1"),
        number("outline size", "Толщина обводки текста в пикселях; 0 — без обводки"),
        color("outline color", "Цвет обводки текста: r g b a от 0 до 1"),
    )

private val RICH_EXTRA =
    listOf(
        check("condense whitespace", "Вкл: подряд идущие пробелы сворачиваются в один."),
        check("strip newlines", "Вкл: переводы строк в тексте убираются."),
        numbers("content offset", "Сдвиг всего содержимого: «x y»"),
    )

private val STATE_COLORS =
    listOf(
        color("text color", "Цвет текста: r g b a от 0 до 1"),
        color("disabled text color", "Цвет текста выключенного виджета (disabled)"),
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
                    listOf(text("text", "Текст виджета; строка вида #ключ берётся из stringtable")) + FONT + TEXT_LOOK +
                        listOf(color("text color", "Цвет текста; если не задан, берётся color")),
                ),
            )
        }

        WidgetType.MULTILINE_TEXT -> {
            listOf(
                PropSection(
                    "Text",
                    listOf(text("text", "Текст виджета; строка вида #ключ берётся из stringtable")) + FONT + TEXT_LOOK + RICH_EXTRA +
                        STATE_COLORS,
                ),
            )
        }

        WidgetType.RICH_TEXT -> {
            listOf(
                PropSection(
                    "Text",
                    listOf(text("text", "Текст виджета; строка вида #ключ берётся из stringtable")) + FONT + TEXT_LOOK + RICH_EXTRA +
                        STATE_COLORS,
                ),
            )
        }

        WidgetType.BUTTON -> {
            focusable(
                "Button",
                text("text", "Подпись кнопки; строка вида #ключ берётся из stringtable"),
                choice(
                    "switch",
                    "Как кнопка нажимается: normal — обычная, once — срабатывает один раз, toggle — держит нажатое состояние",
                    "normal",
                    "once",
                    "toggle",
                    default = "normal",
                ),
                text("font", "Путь к шрифту без расширения, например gui/fonts/MetronBook; пусто — шрифт по умолчанию"),
                check(
                    "exact text",
                    "Вкл: размер шрифта задан в пикселях (exact text size).\nВыкл: размер считается долей высоты кнопки (text_proportion).",
                ),
                number("exact text size", "Размер шрифта в пикселях при включённом exact text"),
                number("text_proportion", "Размер шрифта как доля высоты кнопки, когда exact text выключен", "1"),
                choice("text_halign", "Выравнивание подписи по горизонтали", *ALIGN_H, default = "center"),
                choice("text_valign", "Выравнивание подписи по вертикали", *ALIGN_V, default = "center"),
                numbers("text_offset", "Сдвиг подписи от края в пикселях: «x y»"),
                check("bold text", "Вкл: жирный шрифт."),
                check("italic text", "Вкл: курсив."),
                number("text sharpness", "Резкость контура шрифта: больше значит резче"),
                color("text color", "Цвет подписи: r g b a от 0 до 1"),
                color("disabled text color", "Цвет подписи выключенной кнопки (disabled)"),
                color("text outline color", "Цвет обводки подписи"),
                color("text shadow color", "Цвет тени подписи"),
            )
        }

        WidgetType.CHECKBOX, WidgetType.THREE_STATE_CHECKBOX -> {
            focusable(
                "CheckBox",
                check("checked", "Вкл: флажок отмечен при показе."),
                text("text", "Подпись рядом с флажком"),
                text("font", "Путь к шрифту без расширения"),
                color("text color", "Цвет подписи: r g b a от 0 до 1"),
                number("text outline size", "Толщина обводки подписи в пикселях"),
                color("text outline color", "Цвет обводки подписи"),
                color("text shadow color", "Цвет тени подписи"),
            )
        }

        WidgetType.EDIT_BOX, WidgetType.PASSWORD_EDIT_BOX -> {
            focusable(
                if (type == WidgetType.EDIT_BOX) "EditBox" else "PasswordEditBox",
                text("text", "Текст в поле при показе"),
                check("Use default text", "Вкл: пока поле пусто, показывается подсказка из text цветом Default text color."),
                check("limit visible", "Вкл: видимая часть строки ограничена шириной поля."),
                *(
                    if (type ==
                        WidgetType.PASSWORD_EDIT_BOX
                    ) {
                        arrayOf(check("hide text", "Вкл: символы заменяются точками."))
                    } else {
                        emptyArray()
                    }
                ),
                text("font", "Путь к шрифту без расширения"),
                check("exact text", "Вкл: размер шрифта задан в пикселях (exact text size)."),
                number("exact text size", "Размер шрифта в пикселях при включённом exact text"),
                choice("text halign", "Выравнивание текста по горизонтали", *ALIGN_H, default = "left"),
                choice("text valign", "Выравнивание текста по вертикали", *ALIGN_V, default = "top"),
                check("bold text", "Вкл: жирный шрифт."),
                number("text sharpness", "Резкость контура шрифта: больше значит резче"),
                color("text color", "Цвет вводимого текста: r g b a от 0 до 1"),
                color("Default text color", "Цвет подсказки пустого поля"),
                color("disabled text color", "Цвет текста выключенного поля (disabled)"),
                color("text outline color", "Цвет обводки текста"),
            )
        }

        WidgetType.MULTILINE_EDIT_BOX -> {
            focusable(
                "MultilineEditBox",
                text("text", "Текст в поле при показе"),
                number("lines", "Сколько строк видно в поле"),
                check("limit visible", "Вкл: видимая часть ограничена размером поля."),
                check("size to text v", "Вкл: высота поля подгоняется под текст."),
                check("size to text h", "Вкл: ширина поля подгоняется под текст."),
                text("font", "Путь к шрифту без расширения"),
                number("exact text size", "Размер шрифта в пикселях"),
                number("text_proportion", "Размер шрифта как доля высоты виджета"),
                check("bold text", "Вкл: жирный шрифт."),
                check("italic text", "Вкл: курсив."),
                color("text color", "Цвет текста: r g b a от 0 до 1"),
            )
        }

        WidgetType.SLIDER -> {
            focusable(
                "Slider",
                number("minimum", "Наименьшее значение ползунка", "0"),
                number("maximum", "Наибольшее значение ползунка", "1"),
                number("current", "Значение ползунка при показе"),
                number("step", "Шаг изменения значения; 0 — плавно"),
                check("vertical", "Вкл: ползунок вертикальный."),
                check("flipped", "Вкл: направление значений обратное."),
                check("fill in", "Вкл: пройденная часть полосы закрашена."),
                check("draw marker", "Вкл: на полосе рисуется маркер текущего значения."),
                number("marker thickness", "Толщина маркера как доля длины полосы"),
                check("listen to input", "Вкл: ползунок реагирует на ввод мыши и геймпада.", default = "1"),
            )
        }

        WidgetType.PROGRESS_BAR -> {
            listOf(
                PropSection(
                    "ProgressBar",
                    listOf(
                        number("minimum", "Значение, при котором полоса пуста", "0"),
                        number("maximum", "Значение, при котором полоса полна", "100"),
                        number("current", "Текущее значение при показе"),
                        check("vertical", "Вкл: полоса растёт снизу вверх."),
                        check("flipped", "Вкл: полоса растёт в обратную сторону."),
                    ),
                ),
            )
        }

        WidgetType.XCOMBO_BOX -> {
            focusable(
                "XComboBox",
                text("items", "Пункты списка через точку с запятой: «Alpha;Beta;Gamma»"),
                number("current item", "Номер выбранного пункта при показе, с нуля"),
                text("text", "Подпись, пока ничего не выбрано"),
                text("font", "Путь к шрифту без расширения"),
                color("text color", "Цвет текста: r g b a от 0 до 1"),
                check("bold text", "Вкл: жирный шрифт."),
                number("text sharpness", "Резкость контура шрифта"),
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
                        number("Columns", "Сколько колонок в сетке"),
                        number("Rows", "Сколько рядов в сетке"),
                    )
                } else {
                    emptyList()
                }
            val content =
                if (type == WidgetType.SPACER) {
                    emptyList()
                } else {
                    listOf(
                        choice("content_halign", "Как строка детей выравнивается по горизонтали", *ALIGN_H, default = "left"),
                        choice("content_valign", "Как строка детей выравнивается по вертикали", *ALIGN_V, default = "top"),
                    )
                }
            listOf(
                PropSection(
                    title,
                    grid +
                        listOf(
                            number("Padding", "Отступ от края виджета до детей в пикселях"),
                            number("Margin", "Расстояние между детьми в пикселях"),
                            check("Size To Content H", "Вкл: ширина виджета подгоняется под детей."),
                            check("Size To Content V", "Вкл: высота виджета подгоняется под детей."),
                            check("Ignore invisible", "Вкл: скрытые дети не занимают места."),
                        ) + content,
                ),
            )
        }

        WidgetType.SCROLL -> {
            listOf(
                PropSection(
                    "Scroll",
                    listOf(
                        check("Scrollbar V", "Вкл: показывается вертикальная полоса прокрутки."),
                        check("Scrollbar H", "Вкл: показывается горизонтальная полоса прокрутки."),
                        check("Scrollbar V Left", "Вкл: вертикальная полоса стоит слева."),
                        check("Ignore invisible", "Вкл: скрытые дети не входят в размер прокрутки."),
                    ),
                ),
            )
        }

        WidgetType.WINDOW -> {
            listOf(
                PropSection(
                    "Window",
                    listOf(
                        check("title visible", "Вкл: показывается заголовок окна."),
                        text("text", "Заголовок окна"),
                        text("font", "Путь к шрифту без расширения"),
                    ) + STATE_COLORS,
                ),
            )
        }

        WidgetType.TEXT_LISTBOX, WidgetType.SERVER_BROWSER -> {
            focusable(
                if (type == WidgetType.TEXT_LISTBOX) "TextListbox" else "ServerBrowser",
                check("title visible", "Вкл: показывается строка заголовков."),
                number("colums", "Сколько колонок (так в файле, с опечаткой)"),
                number("lines", "Сколько строк видно"),
                *(
                    if (type == WidgetType.TEXT_LISTBOX) {
                        arrayOf(
                            check("highlight row", "Вкл: строка под курсором подсвечивается."),
                            color("text color", "Цвет текста: r g b a от 0 до 1"),
                        )
                    } else {
                        emptyArray()
                    }
                ),
            )
        }

        WidgetType.HTML -> {
            listOf(PropSection("Html", listOf(check("wrap", "Вкл: длинный текст переносится."))))
        }

        WidgetType.EMBEDDED -> {
            listOf(
                PropSection(
                    "Embedded",
                    listOf(
                        text("layout", "Путь к .layout, который показывается внутри виджета"),
                        text("Caption", "Подпись, которую получает вложенный лайаут"),
                    ),
                ),
            )
        }

        WidgetType.ITEM_PREVIEW -> {
            listOf(PropSection("ItemPreview", listOf(check("force flip enable", "Вкл: предмет можно переворачивать."))))
        }

        WidgetType.VIDEO -> {
            listOf(
                PropSection(
                    "Video",
                    listOf(
                        text("text", "Подпись поверх видео"),
                        color("text color", "Цвет подписи"),
                        numbers("text offset", "Сдвиг подписи: «x y»"),
                        color("text background", "Цвет фона подписи"),
                        color("background color", "Цвет фона видео"),
                        number("outline size", "Толщина обводки в пикселях"),
                        color("outline color", "Цвет обводки"),
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
                            number("minimum", "Значение, при котором полоса пуста", "0"),
                            number("maximum", "Значение, при котором полоса полна", "100"),
                            number("current", "Текущее значение при показе"),
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
