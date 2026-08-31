package com.mobilecontroller

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.viewpager2.widget.ViewPager2
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

    private val viewPager: ViewPager2?
        get() = if (childCount > 0) getChildAt(0) as? ViewPager2 else null

    // Low-threshold fling detector to immediately advance on quick thumb swipes/flicks without springing back
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val vp = viewPager ?: return false
            val adapter = vp.adapter ?: return false
            val absVx = velocityX.absoluteValue
            val absVy = velocityY.absoluteValue

            if (absVx > absVy && absVx > 200f) {
                if (velocityX < -200f && vp.currentItem < adapter.itemCount - 1) {
                    vp.setCurrentItem(vp.currentItem + 1, true)
                    return true
                } else if (velocityX > 200f && vp.currentItem > 0) {
                    vp.setCurrentItem(vp.currentItem - 1, true)
                    return true
                }
            }
            return false
        }
    })

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(e)

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
