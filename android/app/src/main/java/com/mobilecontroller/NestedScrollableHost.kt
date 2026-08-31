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
    private var isHorizontalDragging = false

    init {
        touchSlop = (ViewConfiguration.get(context).scaledTouchSlop * 0.40f).toInt().coerceAtLeast(6)
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = e.x
                initialY = e.y
                isHorizontalDragging = false
                // Claim immediate touch priority from parent ScrollView / SwipeRefreshLayout
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - initialX
                val dy = e.y - initialY
                val absDx = dx.absoluteValue
                val absDy = dy.absoluteValue

                if (absDx > touchSlop || absDy > touchSlop) {
                    if (absDx > absDy) {
                        // Definitively horizontal swipe -> keep 100% exclusive control inside device carousel
                        isHorizontalDragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    } else if (!isHorizontalDragging && absDy > absDx * 1.15f) {
                        // Definitively vertical scroll -> release to parent vertical ScrollView
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isHorizontalDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onInterceptTouchEvent(e)
    }
}
