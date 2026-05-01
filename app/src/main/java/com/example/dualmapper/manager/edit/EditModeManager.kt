package com.example.dualmapper.manager.edit

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import com.example.dualmapper.view.MappedKeyView
import java.util.concurrent.CopyOnWriteArrayList

class EditModeManager(private val container: FrameLayout) {
    private var isEditMode = false
    private val keyViews = CopyOnWriteArrayList<MappedKeyView>()
    private var focusedView: MappedKeyView? = null
    var isLayoutLocked = false

    fun setEditMode(enabled: Boolean) {
        isEditMode = enabled
        keyViews.forEach { it.setEditing(enabled) }
        if (!enabled) {
            focusedView?.setFocused(false)
            focusedView = null
        }
    }

    fun addKeyView(keyView: MappedKeyView, x: Int, y: Int) {
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = x
            topMargin = y
        }
        keyView.layoutParams = params
        keyView.applyScale()
        container.addView(keyView)
        keyViews.add(keyView)
        keyView.setEditing(isEditMode)
    }

    fun removeKeyView(keyView: MappedKeyView) {
        container.removeView(keyView)
        keyViews.remove(keyView)
        if (focusedView == keyView) focusedView = null
    }

    fun getAllKeyViews(): List<MappedKeyView> = keyViews.toList()

    fun setFocusedView(view: MappedKeyView?) {
        focusedView?.setFocused(false)
        focusedView = view
        focusedView?.setFocused(true)
    }

    fun getFocusedView(): MappedKeyView? = focusedView

    fun moveFocus(dx: Int, dy: Int) {
        val current = focusedView ?: return
        val params = current.layoutParams as FrameLayout.LayoutParams
        val newX = (params.leftMargin + dx).coerceAtLeast(0)
        val newY = (params.topMargin + dy).coerceAtLeast(0)
        params.leftMargin = newX
        params.topMargin = newY
        current.layoutParams = params
    }

    fun selectNextFocus(direction: Int) {
        val current = focusedView ?: return
        val next = findNearestView(current, direction)
        next?.let {
            focusedView?.setFocused(false)
            focusedView = it
            it.setFocused(true)
        }
    }

    private fun findNearestView(current: MappedKeyView, direction: Int): MappedKeyView? {
        val currentParams = current.layoutParams as FrameLayout.LayoutParams
        return keyViews.filter { it != current }.filter { view ->
            val params = view.layoutParams as FrameLayout.LayoutParams
            when (direction) {
                KeyEvent.KEYCODE_DPAD_LEFT -> params.leftMargin < currentParams.leftMargin
                KeyEvent.KEYCODE_DPAD_RIGHT -> params.leftMargin > currentParams.leftMargin
                KeyEvent.KEYCODE_DPAD_UP -> params.topMargin < currentParams.topMargin
                KeyEvent.KEYCODE_DPAD_DOWN -> params.topMargin > currentParams.topMargin
                else -> false
            }
        }.minByOrNull { view ->
            val params = view.layoutParams as FrameLayout.LayoutParams
            val dx = (params.leftMargin - currentParams.leftMargin).absoluteValue
            val dy = (params.topMargin - currentParams.topMargin).absoluteValue
            dx + dy
        }
    }
}