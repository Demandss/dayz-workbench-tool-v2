@file:Suppress("ktlint:standard:max-line-length")

package com.github.demandss.dayz_workbench_tool.layout_editor.behavior

/**
 * The Enforce Script of every behaviour, written to be copied into a mod as it is. `__CLASS__` stands for the name of the class
 * the user picked and `__LAYOUT__` for the path of the layout fragment of a class that builds widgets. Parameters are read as
 * text, so that an absent one can be told from a zero. Written for the game, so in English like the rest of the code.
 */
internal object ScriptSource {
    private const val FIND_TARGET = """
    protected Widget FindTarget(Widget w, string name)
    {
        if (name == "" || name == "self" || name == "0")
            return w;

        if (name == "parent" || name == "1")
        {
            if (w.GetParent())
                return w.GetParent();

            return w;
        }

        Widget top = w;
        while (top.GetParent())
            top = top.GetParent();

        Widget found = top.FindAnyWidget(name);
        if (found)
            return found;

        return w;
    }
"""

    private const val PARAM_FLOAT = """
    protected float ParamFloat(string value, float fallback)
    {
        if (value == "")
            return fallback;

        return value.ToFloat();
    }
"""

    private const val PARSE_HEX = """
    protected int ParseHexColor(string hex, int fallbackColor)
    {
        string h = hex;

        if (h.IndexOf("#") == 0)
            h = h.Substring(1, h.Length() - 1);
        else if (h.IndexOf("0x") == 0 || h.IndexOf("0X") == 0)
            h = h.Substring(2, h.Length() - 2);

        if (h.Length() < 6)
            return fallbackColor;

        string rgb = h.Substring(0, 6);
        string alpha = "FF";
        if (h.Length() >= 8)
            alpha = h.Substring(6, 2);

        return ("0x" + alpha + rgb).HexToInt();
    }
"""

    private const val UPDATE_SUBSCRIPTION = """
#ifndef WORKBENCH
    void ~__CLASS__()
    {
        if (g_Game && g_Game.GetUpdateQueue(CALL_CATEGORY_GUI))
            g_Game.GetUpdateQueue(CALL_CATEGORY_GUI).Remove(this.Update);
    }
#endif
"""

    private const val SUBSCRIBE = """
#ifndef WORKBENCH
        g_Game.GetUpdateQueue(CALL_CATEGORY_GUI).Insert(Update);
#endif
"""

    /** Scale around the centre of a widget, shared by the classes that grow or shrink it. */
    private const val APPLY_SCALE = """
    protected void ApplyScale(float k)
    {
        float w = m_W * k;
        float h = m_H * k;
        m_Target.SetSize(w, h);
        m_Target.SetPos(m_X + (m_W - w) * 0.5, m_Y + (m_H - h) * 0.5);
    }
"""

    private val scripts: Map<String, String> =
        mapOf(
            "HoverScale" to
                """
class __CLASS__ : ScriptedWidgetEventHandler
{
    reference string Scale;
    reference string Target;

    protected Widget m_Widget;
    protected Widget m_Target;
    protected float  m_X, m_Y, m_W, m_H;

    void OnWidgetScriptInit(Widget w)
    {
        m_Widget = w;
        m_Widget.SetHandler(this);

        m_Target = FindTarget(w, Target);
        m_Target.GetPos(m_X, m_Y);
        m_Target.GetSize(m_W, m_H);
    }
$FIND_TARGET$PARAM_FLOAT$APPLY_SCALE
    override bool OnMouseEnter(Widget w, int x, int y)
    {
        if (w == m_Widget)
            ApplyScale(ParamFloat(Scale, 1.05));

        return false;
    }

    override bool OnMouseLeave(Widget w, Widget enterW, int x, int y)
    {
        if (w == m_Widget)
            ApplyScale(1);

        return false;
    }
}
""",
            "PressScale" to
                """
class __CLASS__ : ScriptedWidgetEventHandler
{
    reference string Scale;
    reference string Target;

    protected Widget m_Widget;
    protected Widget m_Target;
    protected float  m_X, m_Y, m_W, m_H;

    void OnWidgetScriptInit(Widget w)
    {
        m_Widget = w;
        m_Widget.SetHandler(this);

        m_Target = FindTarget(w, Target);
        m_Target.GetPos(m_X, m_Y);
        m_Target.GetSize(m_W, m_H);
    }
$FIND_TARGET$PARAM_FLOAT$APPLY_SCALE
    override bool OnMouseButtonDown(Widget w, int x, int y, int button)
    {
        if (w == m_Widget)
            ApplyScale(ParamFloat(Scale, 0.95));

        return false;
    }

    override bool OnMouseButtonUp(Widget w, int x, int y, int button)
    {
        if (w == m_Widget)
            ApplyScale(1);

        return false;
    }

    override bool OnMouseLeave(Widget w, Widget enterW, int x, int y)
    {
        if (w == m_Widget)
            ApplyScale(1);

        return false;
    }
}
""",
            "PressOffset" to
                """
class __CLASS__ : ScriptedWidgetEventHandler
{
    reference string OffsetX;
    reference string OffsetY;
    reference string Target;

    protected Widget m_Widget;
    protected Widget m_Target;
    protected float  m_X, m_Y;

    void OnWidgetScriptInit(Widget w)
    {
        m_Widget = w;
        m_Widget.SetHandler(this);

        m_Target = FindTarget(w, Target);
        m_Target.GetPos(m_X, m_Y);
    }
$FIND_TARGET$PARAM_FLOAT
    override bool OnMouseButtonDown(Widget w, int x, int y, int button)
    {
        if (w == m_Widget)
            m_Target.SetPos(m_X + ParamFloat(OffsetX, 0), m_Y + ParamFloat(OffsetY, 2));

        return false;
    }

    override bool OnMouseButtonUp(Widget w, int x, int y, int button)
    {
        if (w == m_Widget)
            m_Target.SetPos(m_X, m_Y);

        return false;
    }

    override bool OnMouseLeave(Widget w, Widget enterW, int x, int y)
    {
        if (w == m_Widget)
            m_Target.SetPos(m_X, m_Y);

        return false;
    }
}
""",
            "HoverColor" to
                """
class __CLASS__ : ScriptedWidgetEventHandler
{
    reference string HoverColor;
    reference string Target;

    protected Widget m_Widget;
    protected Widget m_Target;
    protected int    m_BaseColor;
    protected int    m_HoverColor;

    void OnWidgetScriptInit(Widget w)
    {
        m_Widget = w;
        m_Widget.SetHandler(this);

        m_Target = FindTarget(w, Target);
        m_BaseColor = m_Target.GetColor();
        m_HoverColor = ParseHexColor(HoverColor, m_BaseColor);
    }
$FIND_TARGET$PARSE_HEX
    override bool OnMouseEnter(Widget w, int x, int y)
    {
        if (w == m_Widget)
            m_Target.SetColor(m_HoverColor);

        return false;
    }

    override bool OnMouseLeave(Widget w, Widget enterW, int x, int y)
    {
        if (w == m_Widget)
            m_Target.SetColor(m_BaseColor);

        return false;
    }
}
""",
            "HoverShow" to
                """
class __CLASS__ : ScriptedWidgetEventHandler
{
    reference string Target;

    protected Widget m_Widget;
    protected Widget m_Shown;

    void OnWidgetScriptInit(Widget w)
    {
        m_Widget = w;
        m_Widget.SetHandler(this);

        m_Shown = FindTarget(w, Target);
        if (m_Shown != m_Widget)
            m_Shown.Show(false);
    }
$FIND_TARGET
    override bool OnMouseEnter(Widget w, int x, int y)
    {
        if (w == m_Widget && m_Shown != m_Widget)
            m_Shown.Show(true);

        return false;
    }

    override bool OnMouseLeave(Widget w, Widget enterW, int x, int y)
    {
        if (w == m_Widget && m_Shown != m_Widget)
            m_Shown.Show(false);

        return false;
    }
}
""",
            "HoverImage" to
                """
class __CLASS__ : ScriptedWidgetEventHandler
{
    reference string Image;
    reference string Target;

    protected Widget      m_Widget;
    protected ImageWidget m_Picture;

    void OnWidgetScriptInit(Widget w)
    {
        m_Widget = w;
        m_Widget.SetHandler(this);

        m_Picture = ImageWidget.Cast(FindTarget(w, Target));
        if (m_Picture && Image != "")
            m_Picture.LoadImageFile(1, Image);
    }
$FIND_TARGET
    override bool OnMouseEnter(Widget w, int x, int y)
    {
        if (w == m_Widget && m_Picture && Image != "")
            m_Picture.SetImage(1);

        return false;
    }

    override bool OnMouseLeave(Widget w, Widget enterW, int x, int y)
    {
        if (w == m_Widget && m_Picture)
            m_Picture.SetImage(0);

        return false;
    }
}
""",
            "HoverText" to
                """
class __CLASS__ : ScriptedWidgetEventHandler
{
    reference string HoverColor;
    reference string BaseColor;
    reference string Target;

    protected Widget m_Widget;
    protected Widget m_Target;
    protected int    m_Base;
    protected int    m_Hover;

    void OnWidgetScriptInit(Widget w)
    {
        m_Widget = w;
        m_Widget.SetHandler(this);

        m_Target = FindTarget(w, Target);
        m_Base = ParseHexColor(BaseColor, 0xFFFFFFFF);
        m_Hover = ParseHexColor(HoverColor, m_Base);
    }
$FIND_TARGET$PARSE_HEX
    protected void SetTextTint(int color)
    {
        UIWidget ui = UIWidget.Cast(m_Target);
        if (ui)
            ui.SetTextColor(color);
        else
            m_Target.SetColor(color);
    }

    override bool OnMouseEnter(Widget w, int x, int y)
    {
        if (w == m_Widget)
            SetTextTint(m_Hover);

        return false;
    }

    override bool OnMouseLeave(Widget w, Widget enterW, int x, int y)
    {
        if (w == m_Widget)
            SetTextTint(m_Base);

        return false;
    }
}
""",
            "Blink" to
                """
class __CLASS__ : ScriptedWidgetEventHandler
{
    reference string Period;
    reference string MinAlpha;
    reference string Target;

    protected Widget m_Target;
    protected float  m_Time;

    void OnWidgetScriptInit(Widget w)
    {
        m_Target = FindTarget(w, Target);
$SUBSCRIBE    }
$FIND_TARGET$PARAM_FLOAT$UPDATE_SUBSCRIPTION
#ifndef WORKBENCH
    protected void Update(float tDelta)
    {
        m_Time = m_Time + tDelta;

        float period = Math.Max(ParamFloat(Period, 1.2), 0.05);
        float low = Math.Clamp(ParamFloat(MinAlpha, 0.2), 0, 1);

        m_Target.SetAlpha(low + (1 - low) * (0.5 + 0.5 * Math.Cos(Math.PI2 * m_Time / period)));
    }
#endif
}
""",
            "FadeIn" to
                """
class __CLASS__ : ScriptedWidgetEventHandler
{
    reference string Duration;
    reference string Delay;
    reference string Target;

    protected Widget m_Target;
    protected float  m_Time;

    void OnWidgetScriptInit(Widget w)
    {
        m_Target = FindTarget(w, Target);
        m_Target.SetAlpha(0);
$SUBSCRIBE    }
$FIND_TARGET$PARAM_FLOAT$UPDATE_SUBSCRIPTION
#ifndef WORKBENCH
    protected void Update(float tDelta)
    {
        m_Time = m_Time + tDelta;

        float done = Math.Clamp((m_Time - ParamFloat(Delay, 0)) / Math.Max(ParamFloat(Duration, 0.5), 0.01), 0, 1);
        m_Target.SetAlpha(done);

        if (done >= 1)
            g_Game.GetUpdateQueue(CALL_CATEGORY_GUI).Remove(this.Update);
    }
#endif
}
""",
            "Shake" to
                """
class __CLASS__ : ScriptedWidgetEventHandler
{
    reference string Amplitude;
    reference string Duration;
    reference string Target;

    protected Widget m_Widget;
    protected Widget m_Target;
    protected float  m_X, m_Y;
    protected float  m_Time = -1;

    void OnWidgetScriptInit(Widget w)
    {
        m_Widget = w;
        m_Widget.SetHandler(this);

        m_Target = FindTarget(w, Target);
        m_Target.GetPos(m_X, m_Y);
$SUBSCRIBE    }
$FIND_TARGET$PARAM_FLOAT$UPDATE_SUBSCRIPTION
    override bool OnClick(Widget w, int x, int y, int button)
    {
        if (w == m_Widget)
            m_Time = 0;

        return false;
    }

#ifndef WORKBENCH
    protected void Update(float tDelta)
    {
        if (m_Time < 0)
            return;

        m_Time = m_Time + tDelta;

        float length = Math.Max(ParamFloat(Duration, 0.4), 0.05);
        if (m_Time >= length)
        {
            m_Time = -1;
            m_Target.SetPos(m_X, m_Y);
            return;
        }

        float fade = 1 - m_Time / length;
        m_Target.SetPos(m_X + ParamFloat(Amplitude, 8) * fade * Math.Sin(Math.PI2 * 7 * m_Time), m_Y);
    }
#endif
}
""",
            "ProgressTween" to
                """
class __CLASS__ : ScriptedWidgetEventHandler
{
    reference string Value;
    reference string Duration;
    reference string Target;

    protected SimpleProgressBarWidget m_Bar;
    protected float m_From;
    protected float m_To;
    protected float m_Time;
    protected float m_Length;

    void OnWidgetScriptInit(Widget w)
    {
        m_Bar = SimpleProgressBarWidget.Cast(FindTarget(w, Target));
        if (!m_Bar)
            return;

        m_Bar.SetCurrent(0);
        AnimateTo(ParamFloat(Value, 75));
$SUBSCRIBE    }

    /** Fills the bar smoothly from where it is to [value], on the scale of its `current`. */
    void AnimateTo(float value)
    {
        if (!m_Bar)
            return;

        m_From = m_Bar.GetCurrent();
        m_To = value;
        m_Time = 0;
        m_Length = Math.Max(ParamFloat(Duration, 1.2), 0.05);
    }
$FIND_TARGET$PARAM_FLOAT$UPDATE_SUBSCRIPTION
#ifndef WORKBENCH
    protected void Update(float tDelta)
    {
        if (!m_Bar || m_Time >= m_Length)
            return;

        m_Time = m_Time + tDelta;

        float k = Math.Clamp(m_Time / m_Length, 0, 1);
        k = 1 - (1 - k) * (1 - k);
        m_Bar.SetCurrent(Math.Lerp(m_From, m_To, k));
    }
#endif
}
""",
            "CountUp" to
                """
class __CLASS__ : ScriptedWidgetEventHandler
{
    reference string Value;
    reference string Duration;
    reference string Target;

    protected TextWidget m_Text;
    protected float m_From;
    protected float m_To;
    protected float m_Time;
    protected float m_Length;

    void OnWidgetScriptInit(Widget w)
    {
        m_Text = TextWidget.Cast(FindTarget(w, Target));
        if (!m_Text)
            return;

        m_Text.SetText("0");
        CountTo(ParamFloat(Value, 100));
$SUBSCRIBE    }

    /** Counts the number in the text up (or down) to [value]. */
    void CountTo(float value)
    {
        if (!m_Text)
            return;

        m_From = m_To;
        m_To = value;
        m_Time = 0;
        m_Length = Math.Max(ParamFloat(Duration, 1.5), 0.05);
    }
$FIND_TARGET$PARAM_FLOAT$UPDATE_SUBSCRIPTION
#ifndef WORKBENCH
    protected void Update(float tDelta)
    {
        if (!m_Text || m_Time >= m_Length)
            return;

        m_Time = m_Time + tDelta;

        float k = Math.Clamp(m_Time / m_Length, 0, 1);
        k = 1 - (1 - k) * (1 - k);
        int shown = Math.Round(Math.Lerp(m_From, m_To, k));
        m_Text.SetText(shown.ToString());
    }
#endif
}
""",
            "DragMove" to
                """
class __CLASS__ : ScriptedWidgetEventHandler
{
    reference string Target;

    protected Widget m_Widget;
    protected Widget m_Target;
    protected bool   m_Dragging;
    protected int    m_StartMouseX, m_StartMouseY;
    protected float  m_StartX, m_StartY;

    void OnWidgetScriptInit(Widget w)
    {
        m_Widget = w;
        m_Widget.SetHandler(this);

        string target = Target;
        if (target == "")
            target = "parent";

        m_Target = FindTarget(w, target);
$SUBSCRIBE    }
$FIND_TARGET$UPDATE_SUBSCRIPTION
    override bool OnMouseButtonDown(Widget w, int x, int y, int button)
    {
        if (w != m_Widget || button != 0)
            return false;

        GetMousePos(m_StartMouseX, m_StartMouseY);
        m_Target.GetScreenPos(m_StartX, m_StartY);
        m_Dragging = true;
        return true;
    }

    override bool OnMouseButtonUp(Widget w, int x, int y, int button)
    {
        m_Dragging = false;
        return false;
    }

#ifndef WORKBENCH
    protected void Update(float tDelta)
    {
        if (!m_Dragging)
            return;

        int mouseX, mouseY;
        GetMousePos(mouseX, mouseY);
        m_Target.SetScreenPos(m_StartX + mouseX - m_StartMouseX, m_StartY + mouseY - m_StartMouseY);
    }
#endif
}
""",
            "EditFilter" to
                """
class __CLASS__ : ScriptedWidgetEventHandler
{
    reference string Mode;
    reference string Allowed;
    reference string MaxLength;

    protected EditBoxWidget m_Edit;

    void OnWidgetScriptInit(Widget w)
    {
        m_Edit = EditBoxWidget.Cast(w);
        if (m_Edit)
            m_Edit.SetHandler(this);
    }

    protected bool Accepts(string ch)
    {
        if (Allowed.IndexOf(ch) != -1)
            return true;

        string mode = Mode;
        if (mode == "")
            mode = "digits";

        string digits = "0123456789";
        string letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

        if (mode == "digits")
            return digits.IndexOf(ch) != -1;

        if (mode == "letters")
            return letters.IndexOf(ch) != -1;

        if (mode == "letters_digits")
            return digits.IndexOf(ch) != -1 || letters.IndexOf(ch) != -1;

        return false;
    }

    override bool OnChange(Widget w, int x, int y, bool finished)
    {
        if (w != m_Edit)
            return false;

        string text = m_Edit.GetText();
        string kept = "";
        int limit = MaxLength.ToInt();

        for (int i = 0; i < text.Length(); i++)
        {
            string ch = text.Get(i);
            if (!Accepts(ch))
                continue;

            if (limit > 0 && kept.Length() >= limit)
                break;

            kept = kept + ch;
        }

        if (kept != text)
            m_Edit.SetText(kept);

        return false;
    }
}
""",
            "SelectGroup" to
                """
class __CLASS__ : ScriptedWidgetEventHandler
{
    reference string Items;
    reference string Selected;
    reference string OnColor;
    reference string OffColor;

    ref Widget          layoutRoot;
    ref ScriptInvoker   m_OnSelected = new ScriptInvoker();

    protected const string LAYOUT = "__LAYOUT__";

    protected int m_Count;
    protected int m_Index;
    protected int m_On;
    protected int m_Off;

    void OnWidgetScriptInit(Widget w)
    {
        layoutRoot = g_Game.GetWorkspace().CreateWidgets(LAYOUT, w);
        layoutRoot.SetHandler(this);

        m_On = ParseHexColor(OnColor, 0xFFE8622A);
        m_Off = ParseHexColor(OffColor, 0xFF3A3D4A);

        while (layoutRoot.FindAnyWidget("Item" + m_Count))
            m_Count++;

        Select(Selected.ToInt(), false);
    }
$PARSE_HEX
    /** Turns choice [index] on and the others off. */
    void Select(int index, bool notify = true)
    {
        if (index < 0 || index >= m_Count)
            return;

        m_Index = index;

        for (int i = 0; i < m_Count; i++)
        {
            Widget back = layoutRoot.FindAnyWidget("Back" + i);
            if (i == m_Index)
                back.SetColor(m_On);
            else
                back.SetColor(m_Off);
        }

        if (notify)
            m_OnSelected.Invoke(m_Index);
    }

    int GetSelected()
    {
        return m_Index;
    }

    override bool OnClick(Widget w, int x, int y, int button)
    {
        string name = w.GetName();
        if (name.IndexOf("Item") == 0)
        {
            Select(name.Substring(4, name.Length() - 4).ToInt());
            return true;
        }

        return false;
    }
}
""",
            "Collapse" to
                """
class __CLASS__ : ScriptedWidgetEventHandler
{
    reference string Title;
    reference string Body;
    reference string Open;
    reference string HeaderColor;

    ref Widget          layoutRoot;
    ref ScriptInvoker   m_OnToggled = new ScriptInvoker();

    protected const string LAYOUT = "__LAYOUT__";

    protected ButtonWidget m_Header;
    protected Widget       m_Body;
    protected bool         m_Open;

    void OnWidgetScriptInit(Widget w)
    {
        layoutRoot = g_Game.GetWorkspace().CreateWidgets(LAYOUT, w);
        layoutRoot.SetHandler(this);

        m_Header = ButtonWidget.Cast(layoutRoot.FindAnyWidget("Header"));
        layoutRoot.FindAnyWidget("HeaderBack").SetColor(ParseHexColor(HeaderColor, 0xFF3A3D4A));

        Widget top = w;
        while (top.GetParent())
            top = top.GetParent();

        m_Body = top.FindAnyWidget(Body);

        SetOpen(Open != "0", false);
    }
$PARSE_HEX
    /** Opens or closes the body. */
    void SetOpen(bool open, bool notify = true)
    {
        m_Open = open;

        string mark = ">";
        if (m_Open)
            mark = "v";

        m_Header.SetText(mark + "  " + Title);

        if (m_Body)
            m_Body.Show(m_Open);

        if (notify)
            m_OnToggled.Invoke(m_Open);
    }

    bool IsOpen()
    {
        return m_Open;
    }

    override bool OnClick(Widget w, int x, int y, int button)
    {
        if (w == m_Header)
        {
            SetOpen(!m_Open);
            return true;
        }

        return false;
    }
}
""",
            "NumberStepper" to
                """
class __CLASS__ : ScriptedWidgetEventHandler
{
    reference string Value;
    reference string Min;
    reference string Max;
    reference string Increment;
    reference string FieldColor;

    ref Widget          layoutRoot;
    ref ScriptInvoker   m_OnChanged = new ScriptInvoker();

    protected const string LAYOUT = "__LAYOUT__";

    protected TextWidget m_Text;
    protected int        m_Value;
    protected int        m_Low;
    protected int        m_High;
    protected int        m_Step;

    void OnWidgetScriptInit(Widget w)
    {
        layoutRoot = g_Game.GetWorkspace().CreateWidgets(LAYOUT, w);
        layoutRoot.SetHandler(this);

        m_Text = TextWidget.Cast(layoutRoot.FindAnyWidget("ValueText"));
        layoutRoot.FindAnyWidget("Back").SetColor(ParseHexColor(FieldColor, 0xFF3A3D4A));

        m_Low = Min.ToInt();
        m_High = 10;
        if (Max != "")
            m_High = Max.ToInt();

        m_Step = 1;
        if (Increment != "")
            m_Step = Math.Max(Increment.ToInt(), 1);

        m_Value = 5;
        if (Value != "")
            m_Value = Value.ToInt();

        SetValue(m_Value, false);
    }
$PARSE_HEX
    /** Sets the number, kept between the lowest and the highest. */
    void SetValue(int value, bool notify = true)
    {
        m_Value = Math.Clamp(value, m_Low, m_High);
        m_Text.SetText(m_Value.ToString());

        if (notify)
            m_OnChanged.Invoke(m_Value);
    }

    int GetValue()
    {
        return m_Value;
    }

    override bool OnClick(Widget w, int x, int y, int button)
    {
        if (w.GetName() == "Minus")
        {
            SetValue(m_Value - m_Step);
            return true;
        }

        if (w.GetName() == "Plus")
        {
            SetValue(m_Value + m_Step);
            return true;
        }

        return false;
    }
}
""",
        )

    /** The script of the behaviour [name] for the class [className], or null when there is none. */
    fun of(
        name: String,
        className: String,
        layoutPath: String = "",
    ): String? {
        val body = scripts[name] ?: return null
        return body
            .replace("__CLASS__", className)
            .replace("__LAYOUT__", layoutPath)
            .trim('\n') + "\n"
    }
}
