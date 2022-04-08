package com.laroomysoft.laroomy

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import java.lang.Exception

const val LINEGRAPH_DEFAULT_X_AXIS_MIN_VALUE = 0f
const val LINEGRAPH_DEFAULT_X_AXIS_MAX_VALUE = 10f
const val LINEGRAPH_DEFAULT_Y_AXIS_MIN_VALUE = 0f
const val LINEGRAPH_DEFAULT_Y_AXIS_MAX_VALUE = 10f

const val LINEGRAPH_DEFAULT_ERROR_TEXT_SIZE = 22
const val LINEGRAPH_DEFAULT_VALUE_TEXT_SIZE = 18

const val LINEGRAPH_DEFAULT_DRAW_GRID_LINES = false
const val LINEGRAPH_DEFAULT_DRAW_AXIS_VALUES = true

const val AXIS_OUT_OF_RANGE = -1f
const val INTERSECTION_NOT_SET = -1f

class LineGraphData(val xVal: Float, val yVal: Float)
class GridLineData(val absolutePosition: Float, var realValue: Float)
class LineGraphRange(var xmin: Float, var xmax: Float, var ymin: Float, var ymax: Float)

class LineGraph : View {

    constructor(context: Context) : super(context){
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
            attrs, R.styleable.LineGraph, defStyle, 0
        )
        try {
            xAxisMinValue = a.getFloat(R.styleable.LineGraph_xAxisMinValue, LINEGRAPH_DEFAULT_X_AXIS_MIN_VALUE)
            xAxisMaxValue = a.getFloat(R.styleable.LineGraph_xAxisMaxValue, LINEGRAPH_DEFAULT_X_AXIS_MAX_VALUE)
            yAxisMinValue = a.getFloat(R.styleable.LineGraph_yAxisMinValue, LINEGRAPH_DEFAULT_Y_AXIS_MIN_VALUE)
            yAxisMaxValue = a.getFloat(R.styleable.LineGraph_yAxisMaxValue, LINEGRAPH_DEFAULT_Y_AXIS_MAX_VALUE)
            drawGridLines = a.getBoolean(R.styleable.LineGraph_drawGridLines, LINEGRAPH_DEFAULT_DRAW_GRID_LINES)
            drawAxisValues = a.getBoolean(R.styleable.LineGraph_drawAxesValues, LINEGRAPH_DEFAULT_DRAW_AXIS_VALUES)
            errorTextSize = a.getDimensionPixelSize(R.styleable.LineGraph_errorTextSize, LINEGRAPH_DEFAULT_ERROR_TEXT_SIZE)
            valueTextSize = a.getDimensionPixelSize(R.styleable.LineGraph_valueTextSize, LINEGRAPH_DEFAULT_VALUE_TEXT_SIZE)
            xAxisGridIntersectionUnits = a.getFloat(R.styleable.LineGraph_xAxisGridIntersectionUnits, INTERSECTION_NOT_SET)
            yAxisGridIntersectionUnits = a.getFloat(R.styleable.LineGraph_yAxisGridIntersectionUnits, INTERSECTION_NOT_SET)
        } finally {
            a.recycle()
        }
    }

    // lineGraph property data
    private var xAxisMinValue = 0f
    private var xAxisMaxValue = 10f
    private var yAxisMinValue = 0f
    private var yAxisMaxValue = 10f
    private var errorTextSize = 22
    private var valueTextSize = 18

    var drawGridLines = false
    var drawAxisValues = true

    var xAxisGridIntersectionUnits = INTERSECTION_NOT_SET
    var yAxisGridIntersectionUnits = INTERSECTION_NOT_SET

    var drawProcessActive = false

    // x + y axis-value colors
    private var xAxisValueTextColorID = R.color.lineGraphXAxisGridValueTextColor
    set(value) {
        this.xAxisGridValueTextPaint.color = context.getColor(value)
        field = value
    }
    private var yAxisValueTextColorID = R.color.lineGraphYAxisGridValueTextColor
        set(value) {
            this.yAxisGridValueTextPaint.color = context.getColor(value)
            field = value
        }


    // line data
    var lineGraphData = ArrayList<LineGraphData>()

    // draw values
    private var xAxisAbsoluteCoordinateInY = 0f
    private var yAxisAbsoluteCoordinateInX = 0f

    // control values
    private var isRangeDataValid = true

    private fun calculateAxisPositions(absWidth: Int, absHeight: Int){
        // calculate x-Axis
        if(yAxisMinValue == 0f){
            xAxisAbsoluteCoordinateInY =
                absHeight.toFloat()
        } else {
            if(yAxisMaxValue == 0f){
                xAxisAbsoluteCoordinateInY = 0f
            } else {
                xAxisAbsoluteCoordinateInY = if(((yAxisMinValue > 0)&&(yAxisMaxValue > 0))||((yAxisMinValue < 0)&&(yAxisMaxValue < 0))){
                    AXIS_OUT_OF_RANGE
                } else {
                    // the min value must be negative (it's the only logic possibility)
                    // so make it positive
                    val neg = -(yAxisMinValue)
                    // calc the absolute range
                    val realRange = neg + yAxisMaxValue
                    // get the factor to calculate the draw position
                    val rangeFactor =
                        absHeight / realRange
                    // calculate position
                    rangeFactor * yAxisMaxValue
                }
            }
        }
        // calculate y-Axis
        if(xAxisMinValue == 0f){
            yAxisAbsoluteCoordinateInX = 0f
        } else {
            if(xAxisMaxValue == 0f){
                yAxisAbsoluteCoordinateInX = absWidth.toFloat()
            } else {
                yAxisAbsoluteCoordinateInX = if(((xAxisMinValue > 0)&&(xAxisMaxValue > 0))||((xAxisMinValue < 0)&&(xAxisMaxValue < 0))){
                    AXIS_OUT_OF_RANGE
                } else {
                    // the min value must be negative (it's the only logic possibility)
                    // so make it positive
                    val neg = -(xAxisMinValue)
                    // calc the absolute range
                    val realRange = neg + xAxisMaxValue
                    // get the factor to calculate the draw position
                    val rangeFactor = absWidth / realRange
                    // calculate position
                    rangeFactor * neg
                }
            }
        }
    }

    private fun calculateGridLinePositionsForAxisRange(
        min: Float,
        max: Float,
        absoluteRange: Float,
        intersectionUnits: Float,
        valueReverse: Boolean
    ): ArrayList<GridLineData> {
        val positionList = ArrayList<GridLineData>()

        // calculate the real range
        val intersection: Float
        val realIntersectionGap: Float
        val absoluteIntersectionGap: Float

        val realRange: Float = if ((min < 0) && (max < 0)) {
            (-(min)) + max
        } else if ((min < 0) && (max >= 0)) {
            (-(min)) + max
        } else if ((min >= 0) && (max > 0)) {
            max - min
        } else {
            // should not happen! remove??
            0f
        }

        //NOTE: the crux in this method is to distinguish between intersection and intersection-units !

        if(intersectionUnits == INTERSECTION_NOT_SET){
            // the intersection units are not set, so we calculate it:

            // calculate an appropriate intersection
            when {
                (realRange < 1) -> {
                    intersection = if (realRange < 0.5) {
                        0.05f
                    } else {
                        0.1f
                    }
                }
                (realRange < 10) -> {
                    intersection = if (realRange < 5) {
                        0.5f
                    } else {
                        1f
                    }
                }
                (realRange < 100) -> {
                    intersection = if (realRange < 50) {
                        5f
                    } else {
                        10f
                    }
                }
                (realRange < 1000) -> {
                    intersection = if (realRange < 500) {
                        50f
                    } else {
                        100f
                    }
                }
                else -> {
                    // that's a big value -> cut it to an appropriate length
                    val strNum = realRange.toString()
                    var newNum = ""

                    strNum.forEachIndexed { index, c ->
                        if (index < 3) {
                            newNum += c
                        } else {
                            return@forEachIndexed
                        }
                    }
                    val newValue =
                        newNum.toInt()

                    intersection = if (newValue < 500) {
                        50f
                    } else {
                        100f
                    }
                }
            }
            // apply the intersection
            realIntersectionGap = realRange / intersection
            absoluteIntersectionGap = absoluteRange / intersection
        } else {
            // the intersection units are set, so calculate the necessary values :

            // the intersection is necessary to get the absolute intersection gap in pixel
            intersection = realRange / intersectionUnits
            // here the real intersection gap is defined
            realIntersectionGap = intersectionUnits//realRange / this.gridIntersectionUnits
            absoluteIntersectionGap = absoluteRange / intersection
        }

        if (realIntersectionGap > 0) {

            // apply the calculated values:
            var currentRealValue = min
            var absoluteValue = 0f

            while (currentRealValue <= max) {
                val pos = GridLineData(absoluteValue, currentRealValue)
                positionList.add(pos)
                currentRealValue += realIntersectionGap
                absoluteValue += absoluteIntersectionGap
            }

            // this is bullshit!
            if(valueReverse){
                val realValueArray = ArrayList<Float>()

                for (gridLineData in positionList) {
                    realValueArray.add(gridLineData.realValue)
                }

                var sz = realValueArray.size
                sz--

                realValueArray.forEachIndexed { index, fl ->
                    positionList[sz - index].realValue = fl
                }
            }
        }
        return positionList
    }

    private val absoluteLineData: ArrayList<LineGraphData>
    get() {
        // at first get the range factor in x and y

        // x range-factor
        val xAxisRangeFactor: Float =
            when {
                // handle 0 condition
                (this.xAxisMinValue == 0f) -> {
                    this.width / this.xAxisMaxValue
                }
                (this.xAxisMaxValue == 0f) -> {
                    this.width / (-xAxisMinValue)
                }
                else -> {
                    when {
                        // both values below 0
                        ((this.xAxisMinValue < 0f) && (this.xAxisMaxValue < 0f)) -> {
                            this.width / ((-xAxisMinValue) + xAxisMaxValue)
                        }
                        // both values above 0
                        ((this.xAxisMinValue > 0f) && (this.xAxisMaxValue > 0f)) -> {
                            this.width / (xAxisMaxValue - xAxisMinValue)
                        }
                        else -> {
                            // one must be below and one above
                            this.width / ((-xAxisMinValue) + xAxisMaxValue)
                        }
                    }
                }
            }

        // y range-factor
        val yAxisRangeFactor: Float =
            when {
                // handle 0 condition
                (this.yAxisMinValue == 0f) -> {
                    this.height / this.yAxisMaxValue
                }
                (this.yAxisMaxValue == 0f) -> {
                    this.height / (-yAxisMinValue)
                }
                else -> {
                    when {
                        // both below 0
                        ((this.yAxisMinValue < 0f) && (this.yAxisMaxValue < 0f)) -> {
                            this.height / ((-yAxisMinValue) + yAxisMaxValue)

                        }
                        // both above 0
                        ((this.yAxisMinValue > 0f) && (this.yAxisMaxValue > 0f)) -> {
                            this.height / (yAxisMaxValue - yAxisMinValue)
                        }
                        else -> {
                            // min-value below and max value above
                            this.height / ((-yAxisMinValue) + yAxisMaxValue)
                        }
                    }
                }
            }

        val lData = ArrayList<LineGraphData>()

        this.lineGraphData.forEach {
            // set values in relation to the x/y zero axis values!
            val newX: Float =
                if (yAxisAbsoluteCoordinateInX != AXIS_OUT_OF_RANGE) {
                    when {
                        (it.xVal < 0f) -> {
                            yAxisAbsoluteCoordinateInX - ((-it.xVal) * xAxisRangeFactor)
                        }
                        (it.xVal > 0f) -> {
                            yAxisAbsoluteCoordinateInX + (it.xVal * xAxisRangeFactor)
                        }
                        else -> {
                            yAxisAbsoluteCoordinateInX
                        }
                    }
                } else {
                    // the axis is out of range, so calculate the axis correction value
                    when {
                        ((this.xAxisMinValue < 0f) && (this.xAxisMaxValue < 0f)) -> {
                            // calculate the absolute position of the axis, this is outside of the view
                            val yAxisTheoreticalCoordinateInX =
                                ((-this.xAxisMaxValue) * xAxisRangeFactor) + this.width

                            // calculate the draw point, even though it is outside of the view
                            when {
                                (it.xVal < 0f) -> {
                                    yAxisTheoreticalCoordinateInX - ((-it.xVal) * xAxisRangeFactor)
                                }
                                (it.xVal > 0f) -> {
                                    this.width + 10f//yAxisTheoreticalCoordinateInX + (it.xVal * xAxisRangeFactor)
                                }
                                else -> {
                                    this.width + 10f//yAxisTheoreticalCoordinateInX
                                }
                            }

                        }
                        ((this.xAxisMinValue > 0f) && (this.xAxisMaxValue > 0f)) -> {
                            // calculate the absolute position of the axis, this is outside of the view
                            val yAxisTheoreticalCoordinateInX =
                                -(this.xAxisMinValue * xAxisRangeFactor)

                            // calculate or set the draw point, even though it is outside of the view
                            when {
                                (it.xVal < 0f) -> {
                                    -10f// outside of the view
                                }
                                (it.xVal > 0f) -> {
                                    yAxisTheoreticalCoordinateInX + (it.xVal * xAxisRangeFactor)
                                }
                                else -> {
                                    -10f// outside of the view
                                }
                            }
                        }
                        else -> {
                            // this should never happen
                            this.width + 10f// set a point outside of the control, so it will not be drawn
                        }
                    }
                }

            val newY: Float =
                if (xAxisAbsoluteCoordinateInY != AXIS_OUT_OF_RANGE) {
                    when {
                        (it.yVal < 0f) -> {
                            xAxisAbsoluteCoordinateInY + ((-it.yVal) * yAxisRangeFactor)
                        }
                        (it.yVal > 0f) -> {
                            xAxisAbsoluteCoordinateInY - (it.yVal * yAxisRangeFactor)
                        }
                        else -> {
                            xAxisAbsoluteCoordinateInY
                        }
                    }
                } else {
                    // the axis is out of range, so calculate the axis correction value
                    when {
                        ((this.yAxisMinValue < 0f) && (this.yAxisMaxValue < 0f)) -> {
                            // calculate the absolute position of the axis, this is outside of the view
                            val xAxisTheoreticalCoordinateInY =
                                this.yAxisMaxValue * yAxisRangeFactor// result is negative

                            // calculate or set the draw point, even though it is outside of the view
                            when {
                                (it.yVal < 0f) -> {
                                    xAxisTheoreticalCoordinateInY + ((-it.yVal) * yAxisRangeFactor)
                                }
                                (it.yVal > 0f) -> {
                                    -10f // outside of the view
                                }
                                else -> {
                                    -10f // outside of the view
                                }
                            }
                        }
                        ((this.yAxisMinValue > 0f) && (this.yAxisMaxValue > 0f)) -> {
                            // calculate the absolute position of the axis, this is outside of the view
                            val xAxisTheoreticalCoordinateInY =
                                this.height + (this.yAxisMinValue * yAxisRangeFactor)
                            // calculate or set the draw point, even though it is outside of the view
                            when {
                                (it.yVal < 0f) -> {
                                    this.height + 10f // outside of the view
                                }
                                (it.yVal > 0f) -> {
                                    xAxisTheoreticalCoordinateInY - (it.yVal * yAxisRangeFactor)
                                }
                                else -> {
                                    this.height + 10f // outside of the view
                                }
                            }
                        }
                        else -> {
                            // this should never happen
                            this.height + 10f // outside of the view
                        }
                    }
                }
            lData.add(LineGraphData(newX, newY))
        }
        return lData
    }

    fun setRange(xAxisMin: Float, yAxisMin: Float, xAxisMax: Float, yAxisMax: Float){
        // check if the data is valid, if so: set the parameter
        if((xAxisMin == xAxisMax)||(yAxisMin == yAxisMax)||(xAxisMin > xAxisMax)||(yAxisMin > yAxisMax)){
            isRangeDataValid = false
        } else {
            xAxisMinValue = xAxisMin
            xAxisMaxValue = xAxisMax
            yAxisMinValue = yAxisMin
            yAxisMaxValue = yAxisMax
            isRangeDataValid = true
        }
    }

    fun setRange(range: LineGraphRange){
        // check if the data is valid, if so: set the parameter
        if((range.xmin == range.xmax)||(range.ymin == range.ymax)
            ||(range.xmin > range.xmax)||(range.ymin > range.ymax)){

            isRangeDataValid = false
        } else {
            xAxisMinValue = range.xmin
            xAxisMaxValue = range.xmax
            yAxisMinValue = range.ymin
            yAxisMaxValue = range.ymax
            isRangeDataValid = true
        }
    }

    fun getRange(): LineGraphRange {
        return LineGraphRange(this.xAxisMinValue, this.xAxisMaxValue, this.yAxisMinValue, this.yAxisMaxValue)
    }

    private val graphLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = context.getColor(R.color.lineGraphLineColor)
        this.style = Paint.Style.STROKE
        this.strokeWidth = 2f
    }

    private val axisLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = context.getColor(R.color.lineGraphAxisColor)
        this.style = Paint.Style.STROKE
        this.strokeWidth = 3f
    }

    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = context.getColor(R.color.lineGraphGridLineColor)
        this.style = Paint.Style.STROKE
        this.strokeWidth = 2f
    }

    private val errorTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = context.getColor(R.color.lineGraphErrorColor)
        this.textAlign = Paint.Align.CENTER
    }

    private val xAxisGridValueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = context.getColor(xAxisValueTextColorID)
        this.textAlign = Paint.Align.LEFT
    }

    private val yAxisGridValueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = context.getColor(yAxisValueTextColorID)
        this.textAlign = Paint.Align.LEFT
    }


    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        if(!this.drawProcessActive) {

            this.drawProcessActive = true


            try {

                canvas.apply {
                    if (isRangeDataValid) {
                        // this must be done anyway and must be done at first, to set the axis-base-values for further usage
                        onDrawAxes(canvas)
                        // draw others on request
                        onDrawGridLinesAndGridValues(canvas)
                        // draw the line data (if there is any)
                        onDrawLineData(canvas)
                    } else {
                        // range is invalid -> report error and do not draw!
                        if (canvas != null) {
                            errorTextPaint.textSize = errorTextSize.toFloat()
                            this?.drawText(
                                "Invalid Data",
                                width.div(2).toFloat(),
                                height.div(2).toFloat(),
                                errorTextPaint
                            )
                        }
                    }
                }

                this.drawProcessActive = false

            } catch (e: Exception){
                this.drawProcessActive = false
                Log.e("LineGraphSys:onDraw", "Exception occurred: $e")
            }
        }
    }

    private fun onDrawAxes(canvas: Canvas?){
        if (canvas != null) {
            calculateAxisPositions(canvas.width, canvas.height)
            // draw xAxis
            if(xAxisAbsoluteCoordinateInY != AXIS_OUT_OF_RANGE) {
                canvas.drawLine(
                    0f,
                    xAxisAbsoluteCoordinateInY,
                    canvas.width.toFloat(),
                    xAxisAbsoluteCoordinateInY,
                    axisLinePaint
                )
            }
            // draw yAxis
            if(yAxisAbsoluteCoordinateInX != AXIS_OUT_OF_RANGE) {
                canvas.drawLine(
                    yAxisAbsoluteCoordinateInX,
                    0f,
                    yAxisAbsoluteCoordinateInX,
                    canvas.height.toFloat(),
                    axisLinePaint
                )
            }
        }
    }

    private fun onDrawGridLinesAndGridValues(canvas: Canvas?){

        if(canvas != null) {
            if (drawAxisValues || drawGridLines) {

                xAxisGridValueTextPaint.textSize =
                    valueTextSize.toFloat()

                // get the x-Axis line-draw-data
                val xAxisGridLines =
                    this.calculateGridLinePositionsForAxisRange(
                        xAxisMinValue,
                        xAxisMaxValue,
                        canvas.width.toFloat(),
                        this.xAxisGridIntersectionUnits,
                        false
                    )

                // draw the lines and values (on request)
                xAxisGridLines.forEach {
                    // draw lines
                    if(drawGridLines) {
                        canvas.drawLine(
                            it.absolutePosition,
                            0f,
                            it.absolutePosition,
                            canvas.height.toFloat(),
                            gridLinePaint
                        )
                    }
                    // draw values
                    if (drawAxisValues) {

                        xAxisGridValueTextPaint.textAlign = Paint.Align.LEFT

                        //if(xAxisGridLines.last() != it)//?????

                        // NOTE: do not draw the value if this is the point of origin (zero)
                        if (it.realValue != 0f) {
                            if (xAxisAbsoluteCoordinateInY != AXIS_OUT_OF_RANGE) {
                                if (xAxisAbsoluteCoordinateInY > (canvas.height - (valueTextSize.toFloat() + 6f))) {
                                    // draw the values above the x-Axis
                                    if (it.absolutePosition < this.width) { // do not draw outside of the view!
                                        canvas.drawText(
                                            it.realValue.toString(),
                                            it.absolutePosition + 4f,
                                            xAxisAbsoluteCoordinateInY - 6f,
                                            xAxisGridValueTextPaint
                                        )
                                    }
                                } else {
                                    // draw the values beneath the x-Axis
                                    if (it.absolutePosition < this.width) { // do not draw outside of the view!
                                        canvas.drawText(
                                            it.realValue.toString(),
                                            it.absolutePosition + 4f,
                                            xAxisAbsoluteCoordinateInY + (valueTextSize.toFloat() + 6f),
                                            xAxisGridValueTextPaint
                                        )
                                    }
                                }
                            } else {
                                // the X-axis is not visible
                                when {
                                    ((this.yAxisMinValue > 0f) && (this.yAxisMaxValue > 0f)) -> {
                                        // the X-Axis is beneath the view, so draw the values on the bottom
                                        if (it.absolutePosition < this.width) { // do not draw outside of the view!
                                            if(it.realValue != xAxisGridLines.elementAt(0).realValue) { // do not draw the first, they overlap
                                                canvas.drawText(
                                                    it.realValue.toString(),
                                                    it.absolutePosition + 4f,
                                                    height - 6f,
                                                    xAxisGridValueTextPaint
                                                )
                                            }
                                        }
                                    }
                                    ((this.yAxisMinValue < 0f) && (this.yAxisMaxValue < 0f)) -> {
                                        // the X-Axis is above the view, so draw the values on the top
                                        if (it.absolutePosition < this.width) { // do not draw outside of the view!
                                            canvas.drawText(
                                                it.realValue.toString(),
                                                it.absolutePosition + 4f,
                                                6f + this.valueTextSize,
                                                xAxisGridValueTextPaint
                                            )
                                        }
                                    }
                                    else -> {
                                        // should not happen!
                                    }
                                }
                            }
                        }
                    }
                }

                // get the y-Axis line-draw data
                val yAxisGridLines =
                    this.calculateGridLinePositionsForAxisRange(
                        yAxisMinValue,
                        yAxisMaxValue,
                        canvas.height.toFloat(),
                        yAxisGridIntersectionUnits,
                        true
                    )

                yAxisGridValueTextPaint.textSize =
                    valueTextSize.toFloat()

                // draw the lines and values (on request)
                yAxisGridLines.forEach {
                    // draw the lines
                    if(drawGridLines) {
                        canvas.drawLine(
                            0f,
                            it.absolutePosition,
                            canvas.width.toFloat(),
                            it.absolutePosition,
                            gridLinePaint
                        )
                    }
                    // draw the values
                    if (drawAxisValues) {
                        // NOTE: do not draw the value if this is the point of origin (zero)
                        if (it.realValue != 0f) {
                            if(yAxisAbsoluteCoordinateInX != AXIS_OUT_OF_RANGE) {
                                if (yAxisAbsoluteCoordinateInX < (100).toFloat()) {
                                    // draw the values on the right side
                                    canvas.drawText(
                                        it.realValue.toString(),
                                        yAxisAbsoluteCoordinateInX + 6f,
                                        it.absolutePosition - 6f,
                                        yAxisGridValueTextPaint
                                    )
                                } else {
                                    // draw the values on the left side (right-aligned)
                                    yAxisGridValueTextPaint.textAlign = Paint.Align.RIGHT

                                    canvas.drawText(
                                        it.realValue.toString(),
                                        yAxisAbsoluteCoordinateInX - 6f,
                                        it.absolutePosition - 6f,
                                        yAxisGridValueTextPaint
                                    )
                                }
                            } else {
                                // the Y-Axis is not visible
                                when {
                                    ((xAxisMinValue > 0)&&(xAxisMaxValue > 0)) -> {
                                        // the Y-Axis is on the left of the view, so draw the values on the left side
                                        yAxisGridValueTextPaint.textAlign = Paint.Align.LEFT

                                        canvas.drawText(
                                            it.realValue.toString(),
                                            6f,
                                            it.absolutePosition - 6f,
                                            yAxisGridValueTextPaint
                                        )
                                    }
                                    ((xAxisMinValue < 0)&&(xAxisMaxValue < 0)) -> {
                                        // the Y-Axis is on the right of the view, so draw the values on the right side
                                        yAxisGridValueTextPaint.textAlign = Paint.Align.RIGHT

                                        canvas.drawText(
                                            it.realValue.toString(),
                                            this.width - 6f,
                                            it.absolutePosition - 6f,
                                            yAxisGridValueTextPaint
                                        )
                                    }
                                    else -> {
                                        // should not happen!
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onDrawLineData(canvas: Canvas?){
        // start point to end point!
        if(canvas != null) {
            val absLineData = this.absoluteLineData
            if (absLineData.size > 1) {
                absLineData.forEachIndexed { index, lData ->
                    // end with the last but one, otherwise there would be an indexOutOfRange Exception
                    if (index <= absLineData.size - 2) {
                        // only draw the line if the start-point is in the visible area
                        if(((lData.xVal <= width)&&(lData.xVal >= 0))&&((lData.yVal >= 0)&&(lData.yVal <= height))) {
                            // the start-point is visible -> draw the line
                            canvas.drawLine(
                                lData.xVal,
                                lData.yVal,
                                absLineData.elementAt(index + 1).xVal,
                                absLineData.elementAt(index + 1).yVal,
                                graphLinePaint
                            )
                        } else {
                            // the start-point is not visible, but maybe the endpoint
                            if(index > 0){
                                // check if the endpoint is in the visible area
                                val sp =
                                    absLineData.elementAt(index + 1)

                                if (((sp.xVal <= width) && (sp.xVal >= 0)) && ((sp.yVal >= 0) && (sp.yVal <= height))) {
                                    // the endpoint is visible, so the line must be partially visible, so draw the line
                                    canvas.drawLine(
                                        lData.xVal,
                                        lData.yVal,
                                        absLineData.elementAt(index + 1).xVal,
                                        absLineData.elementAt(index + 1).yVal,
                                        graphLinePaint
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}