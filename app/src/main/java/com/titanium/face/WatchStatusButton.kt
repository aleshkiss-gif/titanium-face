package com.titanium.face

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

/** Interactive titanium watch with animated particulate display and tactile press ripples. */
class WatchStatusButton(context: Context) : View(context) {
    var status = 0
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private var phase = 0f
    private var pressX = 0.5f
    private var pressY = 0.5f
    private var pressTime = -10000L
    private var pressedDepth = 0f
    private val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 8500L
        repeatCount = android.animation.ValueAnimator.INFINITE
        interpolator = android.view.animation.LinearInterpolator()
        addUpdateListener { phase = (it.animatedValue as Float) * 6.283185f; invalidate() }
    }
    init { isClickable = true; isFocusable = true; contentDescription = "WFM · состояние подключения часов" }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (!animator.isStarted) animator.start() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
    private fun color(c: Int, alpha: Int = 255) = Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c))
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w * .49f; val cy = h * .5f
        val radius = min(w * .42f, h * .42f)
        val active = when(status) { 2 -> 0xff4aff68.toInt(); 1 -> 0xff35aaff.toInt(); else -> 0xff9299a3.toInt() }
        val elapsed = (android.os.SystemClock.uptimeMillis() - pressTime).coerceAtLeast(0L) / 1000f
        pressedDepth = if (isPressed) 1f else max(0f, 1f - elapsed * 1.7f)
        canvas.save()
        canvas.scale(1f - pressedDepth * .035f, 1f - pressedDepth * .035f, cx, cy)
        paint.style = Paint.Style.FILL
        val strap = RectF(cx-radius*.54f, cy-radius*1.30f, cx+radius*.54f, cy+radius*1.30f)
        paint.shader = LinearGradient(strap.left, 0f, strap.right, 0f,
            intArrayOf(0xff020d24.toInt(),0xff0b3774.toInt(),0xff04142e.toInt()),null,Shader.TileMode.CLAMP)
        canvas.drawRoundRect(strap, radius*.12f,radius*.12f,paint)
        paint.shader = null
        // Side buttons and the orange crown.
        paint.color = 0xff343d4b.toInt()
        canvas.drawRoundRect(RectF(cx+radius*.90f,cy-radius*.40f,cx+radius*1.13f,cy-radius*.16f),radius*.07f,radius*.07f,paint)
        canvas.drawRoundRect(RectF(cx+radius*.90f,cy+radius*.23f,cx+radius*1.10f,cy+radius*.42f),radius*.07f,radius*.07f,paint)
        paint.color = 0xffff8917.toInt()
        canvas.drawRoundRect(RectF(cx+radius*.97f,cy-radius*.10f,cx+radius*1.17f,cy+radius*.18f),radius*.07f,radius*.07f,paint)
        // Brushed titanium case and dark bezel.
        paint.shader = LinearGradient(cx-radius,cy-radius,cx+radius,cy+radius,
            intArrayOf(0xffe0e8f0.toInt(),0xff354353.toInt(),0xff8b9dad.toInt(),0xff1c2532.toInt(),0xffa7b9c8.toInt()),
            null,Shader.TileMode.CLAMP)
        canvas.drawCircle(cx,cy,radius,paint)
        paint.shader = null
        paint.color = 0xff111a29.toInt(); canvas.drawCircle(cx,cy,radius*.91f,paint)
        paint.color = 0xff596b7d.toInt(); canvas.drawCircle(cx,cy,radius*.855f,paint)
        paint.color = 0xff030b13.toInt(); canvas.drawCircle(cx,cy,radius*.825f,paint)
        for (i in 0 until 24) {
            val angle = (i*15f-90f)*Math.PI.toFloat()/180f
            paint.color = if (i%3==0) 0xffff8617.toInt() else 0xff647487.toInt()
            paint.strokeWidth = if (i%3==0) radius*.026f else radius*.010f
            canvas.drawLine(cx+cos(angle)*radius*.86f,cy+sin(angle)*radius*.86f,
                cx+cos(angle)*radius*(if(i%3==0).94f else .90f),
                cy+sin(angle)*radius*(if(i%3==0).94f else .90f),paint)
        }
        // Luminous display with dynamic sand-like waves.
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(cx,cy,radius*.84f,
            intArrayOf(color(active,105),color(active,32),0xff030a12.toInt()),
            floatArrayOf(0f,.67f,1f),Shader.TileMode.CLAMP)
        canvas.drawCircle(cx,cy,radius*.79f,paint)
        paint.shader = null
        canvas.save()
        val clip = Path().apply { addCircle(cx,cy,radius*.77f,Path.Direction.CW) }
        canvas.clipPath(clip)
        val step = max(3f*density,radius*.024f)
        var y = cy-radius*.72f
        while(y <= cy+radius*.72f) {
            var x = cx-radius*.74f
            while(x <= cx+radius*.74f) {
                val dx=(x-cx)/radius; val dy=(y-cy)/radius
                val wave = sin(dx*8f + phase + sin(dy*6f+phase)*1.2f) * .18f +
                    sin(dy*12f-phase*.7f)*.10f
                val rippleDist = hypot(x-(cx-radius+pressX*radius*2),y-(cy-radius+pressY*radius*2))/radius
                val ripple = if (elapsed<1.5f) exp(-abs(rippleDist-elapsed*1.7f)*18f)*(1f-elapsed/1.5f) else 0f
                val brightness = ((sin((dy-wave)*22f)+1f)*.5f*.52f + ripple*.9f).coerceIn(0f,1f)
                paint.color = color(active,(18+brightness*170).toInt())
                canvas.drawCircle(x,y+wave*radius*.16f,step*(.12f+brightness*.17f),paint)
                x+=step
            }
            y+=step
        }
        canvas.restore()
        paint.style=Paint.Style.STROKE
        paint.strokeWidth=radius*.018f
        paint.color=active
        canvas.drawCircle(cx,cy,radius*.785f,paint)
        paint.style=Paint.Style.FILL
        paint.typeface=Typeface.create("sans-serif-black",Typeface.BOLD)
        paint.textAlign=Paint.Align.CENTER
        paint.color=Color.WHITE
        paint.setShadowLayer(radius*.055f,0f,0f,active)
        paint.textSize=radius*.43f
        canvas.drawText("WFM",cx,cy+radius*.13f,paint)
        paint.clearShadowLayer()
        paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        paint.textSize=radius*.115f
        canvas.drawText(when(status) { 2->"Часы подключены";1->"Режим ожидания";else->"Нет соединения" },cx,cy+radius*.36f,paint)
        paint.textSize=radius*.23f
        canvas.drawText(when(status) {2->"✓";1->"◌";else->"×"},cx,cy-radius*.25f,paint)
        canvas.restore()
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressX=(event.x/width).coerceIn(0f,1f)
                pressY=(event.y/height).coerceIn(0f,1f)
                pressTime=android.os.SystemClock.uptimeMillis()
                isPressed=true; invalidate(); return true
            }
            MotionEvent.ACTION_UP -> { isPressed=false; invalidate(); performClick(); return true }
            MotionEvent.ACTION_CANCEL -> { isPressed=false; invalidate(); return true }
        }
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
