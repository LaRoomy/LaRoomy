package com.laroomysoft.laroomy

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

abstract class SwipeToDeleteCallback(context: Context) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

    private val deleteIcon = ContextCompat.getDrawable(context, R.drawable.ic_delete_36dp)
    private val intrinsicWidth = deleteIcon?.intrinsicWidth
    private val intrinsicHeight = deleteIcon?.intrinsicHeight
    private val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    
//    override fun getMovementFlags(
//        recyclerView: RecyclerView,
//        viewHolder: RecyclerView.ViewHolder
//    ): Int {
//        /**
//         * To disable "swipe" for specific item return 0 here.
//         * For example:
//         * if (viewHolder?.itemViewType == YourAdapter.SOME_TYPE) return 0
//         * if (viewHolder?.adapterPosition == 0) return 0
//         */
//
//        //if (viewHolder.absoluteAdapterPosition == 10) return 0
//
//        return super.getMovementFlags(recyclerView, viewHolder)
//    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return false
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {

        val itemView = viewHolder.itemView
        val itemHeight = itemView.bottom - itemView.top
        val isCanceled = dX == 0f && !isCurrentlyActive

        if (isCanceled) {
            clearCanvas(c, itemView.right + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, false)
            return
        }

        // Draw the red delete background
        c.drawRoundRect(
            itemView.left.toFloat(),
            itemView.top.toFloat() + 8,
            itemView.right.toFloat() - 10,
            itemView.bottom.toFloat() - 8,
            15.0F,
            15.0F,
            itemPaint
        )

        // Calculate position of delete icon
        val deleteIconTop = itemView.top + (itemHeight - (intrinsicHeight ?: 0)) / 2
        val deleteIconMargin = (itemHeight - (intrinsicHeight ?: 0)) / 2
        val deleteIconLeft = itemView.right - deleteIconMargin - (intrinsicWidth ?: 0)
        val deleteIconRight = itemView.right - deleteIconMargin
        val deleteIconBottom = deleteIconTop + (intrinsicHeight ?: 0)

        // Draw the delete icon
        deleteIcon?.setBounds(deleteIconLeft, deleteIconTop, deleteIconRight, deleteIconBottom)
        deleteIcon?.draw(c)

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    private fun clearCanvas(c: Canvas?, left: Float, top: Float, right: Float, bottom: Float) {
        c?.drawRect(left, top, right, bottom, clearPaint)
    }

    private val itemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.FILL
        this.color = context.getColor(R.color.swipe_to_delete_item_color)
    }
}