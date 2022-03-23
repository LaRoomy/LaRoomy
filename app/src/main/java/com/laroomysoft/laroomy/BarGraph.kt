package com.laroomysoft.laroomy

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.pow

class BarGraphData(var barValue: Float, var barText: String)
class ScaleIntersectionPoints(var yVAl: Float, var text: String)

class BarGraph : View {

    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init(attrs, defStyle)
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {
        // Load attributes
        val a = context.obtainStyledAttributes(
            attrs, R.styleable.BarGraph, defStyle, 0
        )
            try {
                barTextColor = a.getInteger(R.styleable.BarGraph_barTextColor, R.color.normalTextColor)
                barGraphOutlineColor = a.getInteger(R.styleable.BarGraph_barGraphOutlineColor, R.color.normalOutlineColor)
                barDescriptionGap = a.getDimensionPixelSize(R.styleable.BarGraph_barDescriptionGap, defaultBarDescriptionGapHeight)
                barTextHeight = a.getDimensionPixelSize(R.styleable.BarGraph_barTextSize, defaultBarTextHeight)
                scaleTextSize = a.getDimensionPixelSize(R.styleable.BarGraph_scaleTextSize, 25)
                scaleSectionWidth = a.getDimensionPixelSize(R.styleable.BarGraph_scaleSectionWidth, defScaleSectionWidth)
                extraTopPadding = a.getDimensionPixelSize(R.styleable.BarGraph_extraTopPadding, 0)
                fixedMaximumValue = a.getInteger(R.styleable.BarGraph_fixedMaximumValue, -1)
            } finally {
                a.recycle()
            }
    }

    // colors
    private val barColorList = listOf(R.color.decent_bar_0_color, R.color.decent_bar_1_color, R.color.decent_bar_2_color, R.color.decent_bar_3_color, R.color.decent_bar_4_color, R.color.decent_bar_5_color, R.color.decent_bar_6_color, R.color.decent_bar_7_color, R.color.decent_bar_8_color)

    // default values
    private val defScaleSectionWidth = 30
    private val defaultBarTextHeight = 14
    private val defaultBarDescriptionGapHeight = 22

    // attribute values
    private var barTextColor = R.color.normalTextColor
    private var barGraphOutlineColor = R.color.normalOutlineColor
    private var barTextHeight = defaultBarTextHeight
    private var barDescriptionGap = defaultBarDescriptionGapHeight
    private var scaleSectionWidth = defScaleSectionWidth
    var fixedMaximumValue = -1
        set(value){
            field = if(value <= 0)
                -1
            else
                value
        }

    private val numberOfBars
        get() = this.barDataList.size

    // control values
    private val isDataValid
        get() = ((this.numberOfBars == this.barDataList.size) && this.barDataList.size > 0)
    private val hasFixedMaximumValue
        get() = (this.fixedMaximumValue > 0)

    // bar graph data
    var barDataList = ArrayList<BarGraphData>()

    // drawing data
    private var barSectionWidth = 0
    private var barWidth = 0
    private var barGap = 0
    private var barHeight = 0
    private var effectiveChartWidth = 0
    private var gridLinePositions = ArrayList<ScaleIntersectionPoints>()
    private var extraTopPadding = 0
    private var scaleTextSize = 25

    // Paint objects
    private val barTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = context.getColor(barTextColor)
        //this.textSize = resources.displayMetrics.density * barTextHeight.toFloat()
        this.textAlign = Paint.Align.CENTER
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.FILL
        //this.color = 0x888888
    }

    private val barOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = context.getColor(barGraphOutlineColor)
        this.style = Paint.Style.STROKE
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = context.getColor(barGraphOutlineColor)
    }

    // calculation methods
    private fun calculateDrawingCoordinates(w: Int, h: Int){

        this.effectiveChartWidth = w - scaleSectionWidth // reserve space for the scale

        this.barSectionWidth =
                if(this.numberOfBars > 0) this.effectiveChartWidth / this.numberOfBars
                else 0

        this.barWidth = (this.barSectionWidth / 3) * 2
        this.barGap = (this.barSectionWidth / 6)

        this.barHeight = h - barDescriptionGap // reserve space for the bottom text
        this.barHeight -= extraTopPadding// add extra padding on top position
        if(this.barHeight < 0){
            this.barHeight = 1
        }
        calculateGrid()
    }

    private fun calculateGrid(){
        this.gridLinePositions.clear()

        val bVal =
            getHighestValue()

        if(bVal > 0){

            val highestValueAsInt = bVal.toInt()
            val highestValueAsString = highestValueAsInt.toString()
            val firstChar = highestValueAsString.elementAt(0)
            var numberLength = highestValueAsString.length
            val intersection = firstChar.toString().toInt()
            var finalStartValue = -1F

            // set the basic intersection on basis of the first value
            var gridCount = when(intersection) {
                1 -> 10
                2 -> 8
                3 -> 6
                4 -> 8
                5 -> 10
                6 -> 12
                else -> intersection
            }

            // correct the intersection on basis of the second value (if there is one...)
            if (highestValueAsString.length > 1) {

                val secondValue =
                        highestValueAsString.elementAt(1).toString().toInt()

                if (secondValue > 0) {
                    when (intersection) {
                        1 -> {
                            gridCount += secondValue
                            finalStartValue = 10F + secondValue
                            numberLength--
                        }
                        2 -> {
                            val additionalIntersection = ((secondValue.toFloat() / 10F) / 0.25).toInt()
                            gridCount += additionalIntersection
                            finalStartValue = (20 + (additionalIntersection.toFloat() * 2.5)).toFloat()
                            numberLength--
                        }
                        3 -> {
                            val additionalIntersection = ((secondValue.toFloat() / 10F) / 0.5).toInt()
                            gridCount += additionalIntersection
                            finalStartValue = (30 + (additionalIntersection * 5)).toFloat()
                            numberLength--
                        }
                        4 -> {
                            val additionalIntersection = ((secondValue.toFloat() / 10F) / 0.5).toInt()
                            gridCount += additionalIntersection
                            finalStartValue = (40 + (additionalIntersection * 5)).toFloat()
                            numberLength--
                        }
                        5 -> {
                            if (secondValue > 3) {
                                if (secondValue > 8) {
                                    gridCount += 2
                                    finalStartValue = 60F
                                    numberLength--
                                } else {
                                    gridCount++
                                    finalStartValue = 55F
                                    numberLength--
                                }
                            }
                        }
                        6 -> {
                            if (secondValue > 4)
                                if (secondValue > 8) {
                                    gridCount += 2
                                    finalStartValue = 70F
                                    numberLength--
                                } else {
                                    gridCount++
                                    finalStartValue = 65F
                                    numberLength--
                                }
                        }
                        7 -> {
                            if (secondValue > 8) {
                                gridCount++
                                finalStartValue = 80F
                                numberLength--
                            }
                        }
                        8 -> {
                            if(secondValue > 8){
                                gridCount++
                                finalStartValue = 90F
                                numberLength--
                            }
                        }
                        9 -> {
                            if(secondValue > 8){
                                gridCount++
                                finalStartValue = 100F
                                numberLength--
                            }
                        }
                    }
                }
            }

            if(finalStartValue == -1F)
                finalStartValue = intersection.toFloat()

            var drawVal = (finalStartValue * (10F.pow(numberLength - 1)))
            val drawableGridLength = ((drawVal * barHeight) / bVal)
            val drawStep = drawableGridLength / gridCount
            val valueStep = (drawVal / gridCount).toInt()
            var yCoordinate = (barHeight - drawableGridLength) + extraTopPadding
            gridCount--

            for(i in 0  .. gridCount){
                // add the gridLinePositions to the ArrayList
                this.gridLinePositions.add(
                        ScaleIntersectionPoints(yCoordinate, drawVal.toInt().toString())
                )
                drawVal -= valueStep
                yCoordinate += drawStep
            }
        }
    }

    private fun getHighestValue(): Float {
        return when (this.hasFixedMaximumValue) {
            true -> fixedMaximumValue.toFloat()
            else -> {
                var baseValue = 0F

                barDataList.forEach {
                    if (it.barValue > baseValue)
                        baseValue = it.barValue
                }
                baseValue
            }
        }
    }

    private fun yBarStartValueFromBaseValue(value: Int) : Float {
        return (barHeight - ((barHeight * value) / getHighestValue())) + extraTopPadding
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        canvas.apply {
            if(isDataValid) {

                calculateDrawingCoordinates(width, height)

                barTextPaint.textSize = scaleTextSize.toFloat()
                //scaleTextPaint.textSize = scaleTextSize.toFloat()

                // draw the scale
                linePaint.color = context.getColor(R.color.barGraphGridLineColor)
                gridLinePositions.forEach {

                    this?.drawLine(scaleSectionWidth.toFloat(), it.yVAl, width.toFloat(), it.yVAl, linePaint)
                    this?.drawText(it.text, (scaleSectionWidth / 2).toFloat(), it.yVAl + (scaleTextSize / 3), barTextPaint)
                }

                barTextPaint.textSize = barTextHeight.toFloat()

                // draw the bars and the bar-description
                barDataList.forEachIndexed { index, barGraphData ->

                    val left = (scaleSectionWidth + ((index * barSectionWidth) + barGap)).toFloat()
                    val right = (left + barWidth)
                    val top = yBarStartValueFromBaseValue(barGraphData.barValue.toInt())
                    val bottom = barHeight.toFloat() + extraTopPadding
                    val xMiddle = (left - barGap) + (barSectionWidth / 2)

                    barPaint.color = context.getColor(barColorList[index])

                    this?.drawRect(left, top, right, bottom, barPaint)// bar
                    this?.drawRect(left, top, right, bottom, barOutlinePaint)// bar outline
                    this?.drawText(barGraphData.barText, xMiddle, bottom + ((barDescriptionGap / 3)*2) + (resources.displayMetrics.density * 2), barTextPaint)// bar text
                }

                // draw the axis-lines
                linePaint.color = context.getColor(R.color.normalOutlineColor)
                this?.drawLine(scaleSectionWidth.toFloat(), 0F, scaleSectionWidth.toFloat(), barHeight.toFloat() + extraTopPadding, linePaint)
                this?.drawLine(scaleSectionWidth.toFloat(), barHeight.toFloat() + extraTopPadding, (effectiveChartWidth + scaleSectionWidth).toFloat(), barHeight.toFloat() + extraTopPadding, linePaint)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val xpad = (paddingLeft + paddingRight)
        val ypad = (paddingTop + paddingBottom)

        val ww = w - xpad
        val hh = h - ypad

        calculateDrawingCoordinates(ww, hh)
    }
}