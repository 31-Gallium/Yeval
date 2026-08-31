package com.mobilecontroller

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.absoluteValue
import kotlin.math.sign

class NestedScrollableHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var touchSlop = 0
    private var initialX = 0f
    private var initialY = 0f

    init {
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    }

    private val childRecyclerView: RecyclerView?
        get() {
            var v: View? = this.getChildAt(0)
            while (v != null && v !is RecyclerView) {
                v = if (v is FrameLayout) v.getChildAt(0) else null
            }
            return v as? RecyclerView
        }

    private fun canChildScroll(orientation: Int, delta: Float): Boolean {
        val direction = -delta.sign.toInt()
        return when (orientation) {
            0 -> childRecyclerView?.canScrollHorizontally(direction) ?: false
            1 -> childRecyclerView?.canScrollVertically(direction) ?: false
            else -> throw IllegalArgumentException()
        }
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        handleInterceptTouchEvent(e)
        return super.onInterceptTouchEvent(e)
    }

    private fun handleInterceptTouchEvent(e: MotionEvent) {
        val rv = childRecyclerView ?: return
        val layoutManager = rv.layoutManager ?: return
        val isRvHorizontal = layoutManager.canScrollHorizontally()
        val orientation = if (isRvHorizontal) 0 else 1

        if (!canChildScroll(orientation, -1f) && !canChildScroll(orientation, 1f)) {
            return
        }

        if (e.action == MotionEvent.ACTION_DOWN) {
            initialX = e.x
            initialY = e.y
            parent.requestDisallowInterceptTouchEvent(true)
        } else if (e.action == MotionEvent.ACTION_MOVE) {
            val dx = e.x - initialX
            val dy = e.y - initialY
            val scaledDx = dx.absoluteValue * if (isRvHorizontal) 0.5f else 1f
            val scaledDy = dy.absoluteValue * if (isRvHorizontal) 1f else 0.5f

            if (scaledDx > touchSlop || scaledDy > touchSlop) {
                if (isRvHorizontal == (scaledDy > scaledDx)) {
                    parent.requestDisallowInterceptTouchEvent(false)
                } else {
                    if (canChildScroll(orientation, if (isRvHorizontal) dx else dy)) {
                        parent.requestDisallowInterceptTouchEvent(true)
                    } else {
                        parent.requestDisallowInterceptTouchEvent(false)
                    }
                }
            }
        }
    }
}
