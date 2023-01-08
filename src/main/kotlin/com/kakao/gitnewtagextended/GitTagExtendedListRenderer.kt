package com.kakao.gitnewtagextended

import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants

class GitTagListRenderer : JPanel(),
    ListCellRenderer<Any> {
    override fun getListCellRendererComponent(
        list: JList<out Any>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        removeAll()

        if (value == null) {
            return this
        }

        layout = BorderLayout(10, 0)
        font = list!!.font

        if (value is GitTagExtended) {
            val _value = value.refname
            val creator = value.creator
            val tagNameComponent = JLabel(_value, null, SwingConstants.LEFT)
            val tagCreatorComponent = JLabel(creator, null, SwingConstants.LEFT)
            tagCreatorComponent.foreground = UIUtil.getInactiveTextColor()
            val tagCreatorDate = JLabel(value.creatordate, null, SwingConstants.RIGHT)
            tagCreatorDate.foreground = UIUtil.getInactiveTextColor()

            if (isSelected) {
                background = list.selectionBackground
                foreground = list.selectionForeground
            } else {
                background = list.background
                foreground = list.foreground
            }

            add(tagNameComponent, BorderLayout.WEST)
            add(tagCreatorComponent, BorderLayout.CENTER)
            add(tagCreatorDate, BorderLayout.EAST)
        }

        return this
    }
}