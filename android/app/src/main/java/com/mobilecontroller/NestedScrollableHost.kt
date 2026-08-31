package com.mobilecontroller

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.absoluteValue

/**
 * NestedScrollableHost provides rock-solid horizontal swipe capture for ViewPager2
 * inside ScrollView and SwipeRefreshLayout.
 */
class NestedScrollableHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var initialX = 0f
    private var initialY = 0f
    private var isDraggingHorizontally = false

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = e.x
                initialY = e.y
                isDraggingHorizontally = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (e.x - initialX).absoluteValue
                val dy = (e.y - initialY).absoluteValue

                if (dx > 8f || dy > 8f) {
                    if (dx > dy) {
                        isDraggingHorizontally = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    } else if (!isDraggingHorizontally && dy > dx * 1.3f) {
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDraggingHorizontally = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.dispatchTouchEvent(e)
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (isDraggingHorizontally) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return super.onInterceptTouchEvent(e)
    }
}
