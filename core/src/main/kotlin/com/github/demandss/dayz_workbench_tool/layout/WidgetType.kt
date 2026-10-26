package com.github.demandss.dayz_workbench_tool.layout

enum class WidgetType(
    val className: String,
) {
    BUTTON("ButtonWidgetClass"),
    CANVAS("CanvasWidgetClass"),
    CHECKBOX("CheckBoxWidgetClass"),
    CONTENT("ContentWidgetClass"),
    EDIT_BOX("EditBoxWidgetClass"),
    EMBEDDED("EmbededWidgetClass"),
    FRAME("FrameWidgetClass"),
    GRID_SPACER("GridSpacerWidgetClass"),
    HTML("HtmlWidgetClass"),
    IMAGE("ImageWidgetClass"),
    ITEM_PREVIEW("ItemPreviewWidgetClass"),
    MAP("MapWidgetClass"),
    MULTILINE_EDIT_BOX("MultilineEditBoxWidgetClass"),
    MULTILINE_TEXT("MultilineTextWidgetClass"),
    PANEL("PanelWidgetClass"),
    PASSWORD_EDIT_BOX("PasswordEditBoxWidgetClass"),
    PLAYER_PREVIEW("PlayerPreviewWidgetClass"),
    PROGRESS_BAR("ProgressBarWidgetClass"),
    RICH_TEXT("RichTextWidgetClass"),
    SCROLL("ScrollWidgetClass"),
    SERVER_BROWSER("ServerBrowserWidgetClass"),
    SLIDER("SliderWidgetClass"),
    SMART_PANEL("SmartPanelWidgetClass"),
    SPACER("SpacerWidgetClass"),
    TEXT_LISTBOX("TextListboxWidgetClass"),
    TEXT("TextWidgetClass"),
    THREE_STATE_CHECKBOX("ThreeStateCheckboxWidgetClass"),
    VIDEO("VideoWidgetClass"),
    WINDOW("WindowWidgetClass"),
    WRAP_SPACER("WrapSpacerWidgetClass"),
    XCOMBO_BOX("XComboBoxWidgetClass"),
    UNKNOWN(""),
    ;

    companion object {
        fun fromClassName(s: String): WidgetType = entries.firstOrNull { it.className == s } ?: UNKNOWN
    }
}
