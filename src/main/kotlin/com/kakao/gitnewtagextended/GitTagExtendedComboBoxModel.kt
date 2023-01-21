package com.kakao.gitnewtagextended

import java.io.Serializable
import java.util.*
import javax.swing.AbstractListModel
import javax.swing.MutableComboBoxModel

/**
 * This Class is a revised version of the javax.swing.DefaultComboBoxModel Class.
 * The change is the removal of code from the removeAllElements method that initializes the selectedObject to null.
 */
class GitTagExtendedComboBoxModel<E>(val objects: Vector<E>) : AbstractListModel<E>(), MutableComboBoxModel<E>, Serializable {
    var selectedObject: Any? = null

    /**
     * Set the value of the selected item. The selected item may be null.
     *
     * @param anObject The combo box value or null for no selection.
     */
    override fun setSelectedItem(anObject: Any?) {
        if (selectedObject != null && selectedObject != anObject ||
            selectedObject == null && anObject != null
        ) {
            selectedObject = anObject
            fireContentsChanged(this, -1, -1)
        }
    }

    // implements javax.swing.ComboBoxModel
    override fun getSelectedItem(): Any? {
        return selectedObject
    }

    // implements javax.swing.ListModel
    override fun getSize(): Int {
        return objects!!.size
    }

    // implements javax.swing.ListModel
    override fun getElementAt(index: Int): E? {
        return if (index >= 0 && index < objects!!.size) objects!!.elementAt(index) else null
    }

    /**
     * Returns the index-position of the specified object in the list.
     *
     * @param anObject the object to return the index of
     * @return an int representing the index position, where 0 is
     * the first position
     */
    fun getIndexOf(anObject: Any?): Int {
        return objects!!.indexOf(anObject)
    }

    // implements javax.swing.MutableComboBoxModel
    override fun addElement(anObject: E?) {
        objects!!.addElement(anObject)
        fireIntervalAdded(this, objects!!.size - 1, objects!!.size - 1)
        if (objects!!.size == 1 && selectedObject == null && anObject != null) {
            setSelectedItem(anObject)
        }
    }

    // implements javax.swing.MutableComboBoxModel
    override fun insertElementAt(anObject: E, index: Int) {
        objects!!.insertElementAt(anObject, index)
        fireIntervalAdded(this, index, index)
    }

    // implements javax.swing.MutableComboBoxModel
    override fun removeElementAt(index: Int) {
        if (getElementAt(index) === selectedObject) {
            if (index == 0) {
                setSelectedItem(if (size == 1) null else getElementAt(index + 1))
            } else {
                setSelectedItem(getElementAt(index - 1))
            }
        }
        objects!!.removeElementAt(index)
        fireIntervalRemoved(this, index, index)
    }

    // implements javax.swing.MutableComboBoxModel
    override fun removeElement(anObject: Any?) {
        val index = objects!!.indexOf(anObject)
        if (index != -1) {
            removeElementAt(index)
        }
    }

    /**
     * Empties the list.
     */
    fun removeAllElements() {
        if (objects!!.size > 0) {
            val firstIndex = 0
            val lastIndex = objects!!.size - 1
            objects!!.removeAllElements()
            selectedObject = null
            fireIntervalRemoved(this, firstIndex, lastIndex)
        } else {
            selectedObject = null
        }
    }

    /**
     * Adds all of the elements present in the collection.
     *
     * @param c the collection which contains the elements to add
     * @throws NullPointerException if `c` is null
     */
    fun addAll(c: Collection<E?>) {
        if (c.isEmpty()) {
            return
        }
        val startIndex = size
        objects!!.addAll(c)
        fireIntervalAdded(this, startIndex, size - 1)
    }

    /**
     * Adds all of the elements present in the collection, starting
     * from the specified index.
     *
     * @param index index at which to insert the first element from the
     * specified collection
     * @param c the collection which contains the elements to add
     * @throws ArrayIndexOutOfBoundsException if `index` does not
     * fall within the range of number of elements currently held
     * @throws NullPointerException if `c` is null
     */
    fun addAll(index: Int, c: Collection<E?>) {
        if (index < 0 || index > size) {
            throw ArrayIndexOutOfBoundsException(
                "index out of range: " +
                        index
            )
        }
        if (c.isEmpty()) {
            return
        }
        objects!!.addAll(index, c)
        fireIntervalAdded(this, index, index + c.size - 1)
    }
}