package com.mobilecontroller

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
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

    private var touchSlop = 0
    private var initialX = 0f
    private var initialY = 0f
    private var isDraggingHorizontally = false

    init {
        touchSlop = (ViewConfiguration.get(context).scaledTouchSlop * 0.35f).toInt().coerceAtLeast(6)
    }

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = e.x
                initialY = e.y
                isDraggingHorizontally = false
                // Forbid all ancestors (SwipeRefreshLayout, ScrollView, Root ViewPager2) from intercepting
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (e.x - initialX).absoluteValue
                val dy = (e.y - initialY).absoluteValue

                if (dx > touchSlop || dy > touchSlop) {
                    if (dx > dy) {
                        // Horizontal swipe -> lock touch inside device carousel
                        isDraggingHorizontally = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    } else if (!isDraggingHorizontally && dy > dx * 1.3f) {
                        // Vertical scroll -> release to parent ScrollView
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
        // Redundant safety check to keep parents locked during horizontal drags
        if (isDraggingHorizontally) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return super.onInterceptTouchEvent(e)
    }
}
