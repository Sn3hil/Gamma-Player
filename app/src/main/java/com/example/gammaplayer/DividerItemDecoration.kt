package com.example.gammaplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.util.TypedValue
import android.view.View
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView

class TopBorderItemDecoration(context: Context) : RecyclerView.ItemDecoration() {

    @RequiresApi(Build.VERSION_CODES.M)
    private val paint = Paint().apply {
        color = context.getColor(android.R.color.white)
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val leftGap: Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        70f,
        context.resources.displayMetrics
    )

    private val cornerRadius = 35f

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val childCount = parent.childCount
        for (i in 0 until childCount) {
            val child = parent.getChildAt(i)
            val params = child.layoutParams as RecyclerView.LayoutParams
            val top = child.top - params.topMargin


            val path = Path()


            path.moveTo(child.left + leftGap, top.toFloat())


            path.cubicTo(
                child.left + leftGap - cornerRadius, top - cornerRadius,  // Control point 1 (top-left curve)
                child.left + leftGap - cornerRadius, top + cornerRadius,  // Control point 2 (bottom-left curve)
                child.left + leftGap, top.toFloat()                        // End of the curve at the straight line start
            )


            path.lineTo(child.right.toFloat(), top.toFloat())

            // Draw the path with the paint object
            c.drawPath(path, paint)
        }
    }
}
