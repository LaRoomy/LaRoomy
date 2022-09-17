package com.laroomysoft.laroomy

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView


class ExpandCollapseExtension {

    fun expand(view: View) { //, duration: Long){

        view.visibility = View.VISIBLE

//        val widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
//        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
//        view.measure(widthSpec, heightSpec)
//
//        val mAnimator: ValueAnimator = slideAnimator(view, 0, view.measuredHeight)
//        mAnimator.duration = duration
//        mAnimator.start()
    }

    fun collapse(view: View, duration: Long){
        val fHeight = view.height

        val mAnimator = this.slideAnimator(view, fHeight, 0)

        mAnimator.addListener(object : Animator.AnimatorListener {

            override fun onAnimationStart(p0: Animator) {}
            override fun onAnimationEnd(p0: Animator) {
                view.visibility = View.GONE
            }
            override fun onAnimationCancel(p0: Animator) {}
            override fun onAnimationRepeat(p0: Animator) {}
        })
        mAnimator.duration = duration
        mAnimator.start()
    }

    private fun slideAnimator(view: View, start: Int, end: Int) : ValueAnimator {

        val valueAnimator = ValueAnimator.ofInt(start, end)

        valueAnimator.addUpdateListener {
            val value = valueAnimator.animatedValue as Int
            val layoutParams: ViewGroup.LayoutParams = view.layoutParams
            layoutParams.height = value
            view.layoutParams = layoutParams
        }
        return  valueAnimator
    }
}

class DeviceMainPropertyRecyclerView : RecyclerView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    override fun scrollTo(x: Int, y: Int) {}
}

class DevicePropertyListItemAnimator : DefaultItemAnimator() {
    
    override fun animateChange(
        oldHolder: RecyclerView.ViewHolder,
        newHolder: RecyclerView.ViewHolder,
        preInfo: ItemHolderInfo,
        postInfo: ItemHolderInfo
    ): Boolean {
        if(oldHolder == newHolder){
            dispatchChangeFinished(oldHolder, true)
        } else {
            dispatchChangeFinished(oldHolder, true)
            dispatchChangeFinished(newHolder, false)
        }
        return false
    }
}