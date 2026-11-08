package com.cpsfloat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Claude Code 吉祥物 Clawd 风格的像素小螃蟹（橙色方块身体、两侧小钳、四条腿、竖长黑眼睛）。
 * 点击：害羞（眯眼+红晕+轻颤）；长按：开心（爱心眼+蹦跳+飘爱心）；拖动：被提起（睁大眼睛、腿乱蹬）。
 */
class PetView(c: Context) : View(c) {

    companion object {
        const val NORMAL = 0
        const val SHY = 1
        const val LOVE = 2
        private const val BODY = 0xFFD97757.toInt()
        private const val BODY_DARK = 0xFFB85F40.toInt()
        private const val EYE = 0xFF2B2018.toInt()
        private const val PINK = 0xFFFF6B8B.toInt()
        private const val BLUSH = 0x99FF8FA3.toInt()
        private val HEART = arrayOf(".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X...")
    }

    var walking = false
    var draggable = false
    var onDrag: ((Float, Float) -> Unit)? = null
    var onDragState: ((Boolean) -> Unit)? = null
    var onReact: ((Long) -> Unit)? = null

    private var expr = NORMAL
    private var exprStart = 0L
    private var exprUntil = 0L
    private var isDragging = false
    private var nextBlink = SystemClock.uptimeMillis() + 2000L
    private var blinkUntil = 0L
    private val rnd = Random()

    private val paint = Paint()
    private val path = Path()

    private val slop = ViewConfiguration.get(c).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var longFired = false
    private var cancelled = false

    private val frame = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 33)
        }
    }
    private val longRun = Runnable {
        if (!isDragging && !cancelled) {
            longFired = true
            react(LOVE)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(frame)
        post(frame)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        removeCallbacks(longRun)
        super.onDetachedFromWindow()
    }

    fun react(kind: Int) {
        val now = SystemClock.uptimeMillis()
        val dur = if (kind == LOVE) 2600L else 1800L
        expr = kind
        exprStart = now
        exprUntil = now + dur
        onReact?.invoke(dur)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    private fun rect(canvas: Canvas, u: Float, ox: Float, oy: Float, l: Float, t: Float, r: Float, b: Float) {
        canvas.drawRect(ox + l * u, oy + t * u, ox + r * u, oy + b * u, paint)
    }

    private fun heart(canvas: Canvas, cx: Float, cy: Float, cell: Float) {
        val w = 7 * cell
        val h = 6 * cell
        for (r in 0 until 6) {
            for (cc in 0 until 7) {
                if (HEART[r][cc] == 'X') {
                    val l = cx - w / 2f + cc * cell
                    val t = cy - h / 2f + r * cell
                    canvas.drawRect(l, t, l + cell, t + cell, paint)
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        if (expr != NORMAL && now > exprUntil) expr = NORMAL
        if (now >= nextBlink) {
            blinkUntil = now + 140L
            nextBlink = now + 2200L + rnd.nextInt(3500)
        }
        val u = width / 18f
        var ox = 0f
        var oy = 3f * u
        var legPhase = 0
        if (walking) {
            oy += sin(now / 90.0).toFloat() * 0.2f * u
            legPhase = ((now / 140L) % 2L).toInt() + 1
        }
        if (isDragging) {
            oy -= 0.5f * u
            legPhase = ((now / 90L) % 2L).toInt() + 1
        }
        if (expr == SHY) ox += sin(now / 45.0).toFloat() * 0.18f * u
        val loveT = (now - exprStart).toFloat()
        if (expr == LOVE) oy -= abs(sin(loveT / 170.0)).toFloat() * 1.6f * u

        paint.style = Paint.Style.FILL
        paint.isAntiAlias = false

        // 地面阴影
        paint.color = 0x33000000
        canvas.drawOval(3f * u, 13.6f * u, 15f * u, 14.7f * u, paint)

        // 身体、双钳、四条腿
        paint.color = BODY
        rect(canvas, u, ox, oy, 2f, 1f, 16f, 8f)
        rect(canvas, u, ox, oy, 0f, 4f, 2f, 6f)
        rect(canvas, u, ox, oy, 16f, 4f, 18f, 6f)
        val legs = floatArrayOf(3f, 6f, 10f, 13f)
        for (i in legs.indices) {
            var bottom = 11f
            if (legPhase == 1 && i % 2 == 0) bottom = 9.6f
            if (legPhase == 2 && i % 2 == 1) bottom = 9.6f
            rect(canvas, u, ox, oy, legs[i], 8f, legs[i] + 2f, bottom)
        }
        paint.color = BODY_DARK
        rect(canvas, u, ox, oy, 2f, 7f, 16f, 8f)

        // 眼睛与表情
        val blink = now < blinkUntil
        when {
            isDragging -> {
                paint.color = EYE
                rect(canvas, u, ox, oy, 5.3f, 2.6f, 6.7f, 5.4f)
                rect(canvas, u, ox, oy, 11.3f, 2.6f, 12.7f, 5.4f)
            }
            expr == LOVE -> {
                paint.color = PINK
                heart(canvas, ox + 6f * u, oy + 4.1f * u, 0.3f * u)
                heart(canvas, ox + 12f * u, oy + 4.1f * u, 0.3f * u)
            }
            expr == SHY -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.7f * u
                paint.color = EYE
                path.reset()
                path.moveTo(ox + 5.0f * u, oy + 4.9f * u)
                path.lineTo(ox + 6.0f * u, oy + 3.7f * u)
                path.lineTo(ox + 7.0f * u, oy + 4.9f * u)
                path.moveTo(ox + 11.0f * u, oy + 4.9f * u)
                path.lineTo(ox + 12.0f * u, oy + 3.7f * u)
                path.lineTo(ox + 13.0f * u, oy + 4.9f * u)
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.FILL
                paint.color = BLUSH
                rect(canvas, u, ox, oy, 3.2f, 5.4f, 5.2f, 6.4f)
                rect(canvas, u, ox, oy, 12.8f, 5.4f, 14.8f, 6.4f)
            }
            blink -> {
                paint.color = EYE
                rect(canvas, u, ox, oy, 5.3f, 4.2f, 6.7f, 4.8f)
                rect(canvas, u, ox, oy, 11.3f, 4.2f, 12.7f, 4.8f)
            }
            else -> {
                paint.color = EYE
                rect(canvas, u, ox, oy, 5.3f, 3.0f, 6.7f, 5.2f)
                rect(canvas, u, ox, oy, 11.3f, 3.0f, 12.7f, 5.2f)
            }
        }

        // 开心时飘起的爱心
        if (expr == LOVE) {
            paint.color = PINK
            for (k in 0 until 3) {
                val t = ((loveT / 1400f) + k / 3f) % 1f
                val hx = (5f + k * 4f) * u + sin(t * 6.28f + k).toFloat() * 0.5f * u
                val hy = 2.6f * u - t * 2.6f * u
                paint.alpha = ((1f - t) * 255f).toInt().coerceIn(0, 255)
                heart(canvas, hx, hy, 0.2f * u)
            }
            paint.alpha = 255
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = e.rawX; downY = e.rawY
                lastX = e.rawX; lastY = e.rawY
                isDragging = false; longFired = false; cancelled = false
                removeCallbacks(longRun)
                postDelayed(longRun, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                val far = abs(e.rawX - downX) > slop || abs(e.rawY - downY) > slop
                if (!isDragging && !longFired && !cancelled && far) {
                    removeCallbacks(longRun)
                    if (draggable) {
                        isDragging = true
                        expr = NORMAL
                        onDragState?.invoke(true)
                    } else {
                        cancelled = true
                    }
                }
                if (isDragging) onDrag?.invoke(e.rawX - lastX, e.rawY - lastY)
                lastX = e.rawX; lastY = e.rawY
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longRun)
                if (isDragging) {
                    isDragging = false
                    onDragState?.invoke(false)
                } else if (!longFired && !cancelled) {
                    react(SHY)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longRun)
                if (isDragging) {
                    isDragging = false
                    onDragState?.invoke(false)
                }
                cancelled = true
            }
        }
        return true
    }
}

/** 桌宠悬浮窗控制器：随机移动（方向任意、时长在设定范围内随机）、停止、被拖动。 */
class PetController(private val ctx: Context, private val wm: WindowManager) {

    private val h = Handler(Looper.getMainLooper())
    private val rnd = Random()
    private val d = ctx.resources.displayMetrics.density

    private var cfg = PetCfg(false, 72, 60, 1, 4, 2, 8)
    private var view: PetView? = null
    private var lp: WindowManager.LayoutParams? = null

    private var x = 0f
    private var y = 0f
    private var vx = 0f
    private var vy = 0f
    private var moving = false
    private var phaseEnd = 0L
    private var dragging = false
    private var lastTick = 0L
    private var maxX = 0f
    private var maxY = 0f

    private val tick = object : Runnable {
        override fun run() {
            if (view == null) return
            step()
            h.postDelayed(this, 33)
        }
    }

    private fun viewW(): Int = ctx.dp(cfg.sizeDp)
    private fun viewH(): Int = viewW() * 15 / 18

    @Suppress("DEPRECATION")
    private fun updateBounds() {
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        maxX = (dm.widthPixels - viewW()).coerceAtLeast(0).toFloat()
        maxY = (dm.heightPixels - viewH()).coerceAtLeast(0).toFloat()
    }

    private fun clampPos() {
        x = x.coerceIn(0f, maxX)
        y = y.coerceIn(0f, maxY)
    }

    fun apply(c: PetCfg) {
        cfg = c
        if (!c.enabled) {
            remove()
            return
        }
        if (view == null) create() else resize()
    }

    private fun create() {
        val v = PetView(ctx)
        v.draggable = true
        val l = WindowManager.LayoutParams(
            viewW(), viewH(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SPLIT_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        updateBounds()
        val saved = Cfg.petPos(ctx)
        if (saved != null) {
            x = saved[0] / 1000f * maxX
            y = saved[1] / 1000f * maxY
        } else {
            x = maxX * 0.8f
            y = maxY * 0.6f
        }
        clampPos()
        l.x = x.toInt()
        l.y = y.toInt()

        v.onDrag = { dx, dy ->
            x += dx
            y += dy
            clampPos()
            pushPos()
        }
        v.onDragState = { on ->
            dragging = on
            if (!on) {
                savePos()
                beginPause()
            } else {
                moving = false
                v.walking = false
            }
        }
        v.onReact = { ms -> pauseFor(ms) }

        try {
            wm.addView(v, l)
        } catch (t: Throwable) {
            return
        }
        view = v
        lp = l
        lastTick = SystemClock.uptimeMillis()
        beginPause()
        h.removeCallbacks(tick)
        h.post(tick)
    }

    private fun resize() {
        val l = lp ?: return
        val v = view ?: return
        if (l.width == viewW()) return
        val fx = if (maxX > 0f) x / maxX else 0f
        val fy = if (maxY > 0f) y / maxY else 0f
        l.width = viewW()
        l.height = viewH()
        updateBounds()
        x = fx * maxX
        y = fy * maxY
        clampPos()
        l.x = x.toInt()
        l.y = y.toInt()
        try { wm.updateViewLayout(v, l) } catch (_: Throwable) { }
    }

    /** 屏幕方向变化：按比例换算位置，确保桌宠仍在屏幕内。 */
    fun onScreenChanged() {
        if (view == null) return
        val fx = if (maxX > 0f) x / maxX else 0f
        val fy = if (maxY > 0f) y / maxY else 0f
        updateBounds()
        x = fx * maxX
        y = fy * maxY
        clampPos()
        pushPos()
    }

    fun contains(px: Int, py: Int): Boolean {
        val l = lp ?: return false
        if (view == null) return false
        return px >= l.x && px <= l.x + l.width && py >= l.y && py <= l.y + l.height
    }

    fun remove() {
        val v = view
        if (v != null) {
            savePos()
            try { wm.removeView(v) } catch (_: Throwable) { }
        }
        h.removeCallbacks(tick)
        view = null
        lp = null
        dragging = false
        moving = false
    }

    private fun savePos() {
        val fx = if (maxX > 0f) (x / maxX * 1000f).toInt() else 0
        val fy = if (maxY > 0f) (y / maxY * 1000f).toInt() else 0
        Cfg.putPetPos(ctx, fx.coerceIn(0, 1000), fy.coerceIn(0, 1000))
    }

    private fun pushPos() {
        val l = lp ?: return
        val v = view ?: return
        l.x = x.toInt()
        l.y = y.toInt()
        try { wm.updateViewLayout(v, l) } catch (_: Throwable) { }
    }

    private fun randMs(a: Int, b: Int): Long {
        val lo = minOf(a, b).coerceAtLeast(1)
        val hi = maxOf(a, b).coerceAtLeast(lo)
        return ((lo + rnd.nextFloat() * (hi - lo)) * 1000f).toLong()
    }

    private fun beginPause() {
        moving = false
        vx = 0f
        vy = 0f
        phaseEnd = SystemClock.uptimeMillis() + randMs(cfg.pauseMin, cfg.pauseMax)
        view?.walking = false
    }

    private fun beginMove() {
        moving = true
        val ang = rnd.nextFloat() * 2f * Math.PI.toFloat()
        val sp = cfg.speedDp * d * (0.7f + 0.6f * rnd.nextFloat())
        vx = cos(ang) * sp
        vy = sin(ang) * sp
        phaseEnd = SystemClock.uptimeMillis() + randMs(cfg.moveMin, cfg.moveMax)
    }

    private fun pauseFor(ms: Long) {
        moving = false
        vx = 0f
        vy = 0f
        view?.walking = false
        phaseEnd = maxOf(phaseEnd, SystemClock.uptimeMillis() + ms)
    }

    private fun step() {
        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastTick) / 1000f).coerceIn(0f, 0.1f)
        lastTick = now
        val v = view ?: return
        if (dragging) return
        if (now >= phaseEnd) {
            if (moving) beginPause() else beginMove()
        }
        if (moving) {
            x += vx * dt
            y += vy * dt
            if (x < 0f) { x = 0f; vx = abs(vx) } else if (x > maxX) { x = maxX; vx = -abs(vx) }
            if (y < 0f) { y = 0f; vy = abs(vy) } else if (y > maxY) { y = maxY; vy = -abs(vy) }
            pushPos()
        }
        v.walking = moving
    }
}
