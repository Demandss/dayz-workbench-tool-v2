package com.github.demandss.dayz_workbench_tool.styles

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** One slot of a state: `Center`, `LeftTop` and so on, with the name of an image of the style's imageset. */
data class StyleItem(
    val name: String,
    val image: String,
)

data class StyleState(
    val name: String,
    val items: List<StyleItem>,
)

/** A style of a widget type. [color] is the ARGB number as written in the file. */
data class StyleDef(
    val name: String,
    val font: String,
    val imageSet: String,
    val color: String,
    val states: List<StyleState>,
)

/** The styles that belong to one widget type, such as `ButtonWidget`. */
data class WidgetStyles(
    val widget: String,
    val styles: List<StyleDef>,
)

data class StyleSheet(
    val widgets: List<WidgetStyles>,
) {
    /** The styles of a widget type; [widgetClass] may be `ButtonWidgetClass` or `ButtonWidget`. */
    fun stylesFor(widgetClass: String): List<StyleDef> {
        val name = widgetClass.removeSuffix("Class")
        return widgets.filter { it.widget == name }.flatMap { it.styles }
    }
}

object StylesFormat {
    /** Reads a `.styles` file. Returns null when it is not XML or has no `WidgetStyles` root. */
    fun parse(text: String): StyleSheet? =
        runCatching {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(text.trim().removePrefix("").toByteArray(Charsets.UTF_8)))
            val root = doc.documentElement
            if (root.tagName != "WidgetStyles") return null
            StyleSheet(
                children(root, "Widget").map { w ->
                    WidgetStyles(
                        w.getAttribute("Name"),
                        children(w, "Style").map { s ->
                            StyleDef(
                                s.getAttribute("Name"),
                                s.getAttribute("Font"),
                                s.getAttribute("ImageSet"),
                                s.getAttribute("Color"),
                                children(s, "State").map { st ->
                                    StyleState(
                                        st.getAttribute("Name"),
                                        children(st, "Item").map { StyleItem(it.getAttribute("Name"), it.getAttribute("Image")) },
                                    )
                                },
                            )
                        },
                    )
                },
            )
        }.getOrNull()

    private fun children(
        parent: Element,
        tag: String,
    ): List<Element> {
        val out = ArrayList<Element>()
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n is Element && n.tagName == tag) out += n
        }
        return out
    }

    private fun esc(v: String) =
        v
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    /** The sheet as text, indented by four spaces like the vanilla file. */
    fun serialize(sheet: StyleSheet): String {
        val sb = StringBuilder("<WidgetStyles>\n")
        for (w in sheet.widgets) {
            sb.append("    <Widget Name=\"${esc(w.widget)}\">\n")
            for (s in w.styles) {
                val head = "<Style Name=\"${esc(
                    s.name,
                )}\" Font=\"${esc(s.font)}\" ImageSet=\"${esc(s.imageSet)}\" Color=\"${esc(s.color)}\""
                if (s.states.isEmpty()) {
                    sb.append("        $head />\n")
                } else {
                    sb.append("        $head>\n")
                    for (st in s.states) {
                        sb.append("            <State Name=\"${esc(st.name)}\">\n")
                        for (i in st.items) sb.append("                <Item Name=\"${esc(i.name)}\" Image=\"${esc(i.image)}\" />\n")
                        sb.append("            </State>\n")
                    }
                    sb.append("        </Style>\n")
                }
            }
            sb.append("    </Widget>\n")
        }
        sb.append("</WidgetStyles>\n")
        return sb.toString()
    }
}
