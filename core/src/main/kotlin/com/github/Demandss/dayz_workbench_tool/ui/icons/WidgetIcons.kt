package com.github.demandss.dayz_workbench_tool.ui.icons

import com.github.demandss.dayz_workbench_tool.layout.WidgetType

/** The icon of a widget class. Related classes share a shape: every edit box looks alike, every text does too. */
fun widgetIcon(type: WidgetType): AppIcon =
    when (type) {
        WidgetType.BUTTON -> AppIcon.WIDGET_BUTTON
        WidgetType.CANVAS -> AppIcon.WIDGET_CANVAS
        WidgetType.CHECKBOX, WidgetType.THREE_STATE_CHECKBOX -> AppIcon.WIDGET_CHECKBOX
        WidgetType.CONTENT, WidgetType.SPACER, WidgetType.UNKNOWN -> AppIcon.WIDGET_GENERIC
        WidgetType.EDIT_BOX, WidgetType.MULTILINE_EDIT_BOX, WidgetType.PASSWORD_EDIT_BOX -> AppIcon.WIDGET_EDITBOX
        WidgetType.EMBEDDED -> AppIcon.WIDGET_EMBED
        WidgetType.FRAME -> AppIcon.WIDGET_FRAME
        WidgetType.GRID_SPACER -> AppIcon.WIDGET_GRID
        WidgetType.HTML -> AppIcon.WIDGET_HTML
        WidgetType.IMAGE -> AppIcon.WIDGET_IMAGE
        WidgetType.ITEM_PREVIEW -> AppIcon.WIDGET_ITEM
        WidgetType.MAP -> AppIcon.WIDGET_MAP
        WidgetType.MULTILINE_TEXT, WidgetType.TEXT -> AppIcon.WIDGET_TEXT
        WidgetType.PANEL, WidgetType.SMART_PANEL -> AppIcon.WIDGET_PANEL
        WidgetType.PLAYER_PREVIEW -> AppIcon.WIDGET_PLAYER
        WidgetType.PROGRESS_BAR -> AppIcon.WIDGET_PROGRESS
        WidgetType.RICH_TEXT -> AppIcon.WIDGET_RICHTEXT
        WidgetType.SCROLL -> AppIcon.WIDGET_SCROLL
        WidgetType.SERVER_BROWSER, WidgetType.TEXT_LISTBOX -> AppIcon.WIDGET_LIST
        WidgetType.SLIDER -> AppIcon.WIDGET_SLIDER
        WidgetType.VIDEO -> AppIcon.WIDGET_VIDEO
        WidgetType.WINDOW -> AppIcon.WIDGET_WINDOW
        WidgetType.WRAP_SPACER -> AppIcon.WIDGET_WRAP
        WidgetType.XCOMBO_BOX -> AppIcon.WIDGET_COMBO
    }
