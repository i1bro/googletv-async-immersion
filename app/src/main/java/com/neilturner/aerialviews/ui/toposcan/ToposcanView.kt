package com.neilturner.aerialviews.ui.toposcan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20.GL_CLAMP_TO_EDGE
import android.opengl.GLES20.GL_COLOR_ATTACHMENT0
import android.opengl.GLES20.GL_COLOR_BUFFER_BIT
import android.opengl.GLES20.GL_COMPILE_STATUS
import android.opengl.GLES20.GL_FLOAT
import android.opengl.GLES20.GL_FRAGMENT_SHADER
import android.opengl.GLES20.GL_FRAMEBUFFER
import android.opengl.GLES20.GL_FRAMEBUFFER_COMPLETE
import android.opengl.GLES20.GL_LINEAR
import android.opengl.GLES20.GL_LINK_STATUS
import android.opengl.GLES20.GL_MAX_RENDERBUFFER_SIZE
import android.opengl.GLES20.GL_MAX_TEXTURE_SIZE
import android.opengl.GLES20.GL_MAX_VIEWPORT_DIMS
import android.opengl.GLES20.GL_NO_ERROR
import android.opengl.GLES20.GL_RGBA
import android.opengl.GLES20.GL_SCISSOR_TEST
import android.opengl.GLES20.GL_TEXTURE0
import android.opengl.GLES20.GL_TEXTURE_2D
import android.opengl.GLES20.GL_TEXTURE_MAG_FILTER
import android.opengl.GLES20.GL_TEXTURE_MIN_FILTER
import android.opengl.GLES20.GL_TEXTURE_WRAP_S
import android.opengl.GLES20.GL_TEXTURE_WRAP_T
import android.opengl.GLES20.GL_TRIANGLE_STRIP
import android.opengl.GLES20.GL_UNSIGNED_BYTE
import android.opengl.GLES20.GL_VERTEX_SHADER
import android.opengl.GLES20.glActiveTexture
import android.opengl.GLES20.glAttachShader
import android.opengl.GLES20.glBindAttribLocation
import android.opengl.GLES20.glBindFramebuffer
import android.opengl.GLES20.glBindTexture
import android.opengl.GLES20.glCheckFramebufferStatus
import android.opengl.GLES20.glClear
import android.opengl.GLES20.glClearColor
import android.opengl.GLES20.glCompileShader
import android.opengl.GLES20.glCreateProgram
import android.opengl.GLES20.glCreateShader
import android.opengl.GLES20.glDeleteFramebuffers
import android.opengl.GLES20.glDeleteProgram
import android.opengl.GLES20.glDeleteShader
import android.opengl.GLES20.glDeleteTextures
import android.opengl.GLES20.glDisable
import android.opengl.GLES20.glDrawArrays
import android.opengl.GLES20.glEnable
import android.opengl.GLES20.glEnableVertexAttribArray
import android.opengl.GLES20.glFramebufferTexture2D
import android.opengl.GLES20.glGenFramebuffers
import android.opengl.GLES20.glGenTextures
import android.opengl.GLES20.glGetError
import android.opengl.GLES20.glGetIntegerv
import android.opengl.GLES20.glGetProgramInfoLog
import android.opengl.GLES20.glGetProgramiv
import android.opengl.GLES20.glGetShaderInfoLog
import android.opengl.GLES20.glGetShaderiv
import android.opengl.GLES20.glGetUniformLocation
import android.opengl.GLES20.glLinkProgram
import android.opengl.GLES20.glScissor
import android.opengl.GLES20.glShaderSource
import android.opengl.GLES20.glTexImage2D
import android.opengl.GLES20.glTexParameteri
import android.opengl.GLES20.glUniform1f
import android.opengl.GLES20.glUniform1i
import android.opengl.GLES20.glUniform2f
import android.opengl.GLES20.glUniformMatrix4fv
import android.opengl.GLES20.glUseProgram
import android.opengl.GLES20.glVertexAttribPointer
import android.opengl.GLES20.glViewport
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.view.Choreographer
import android.view.Surface
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.helpers.DeviceHelper
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Only the media layer is processed. Android renders clock/weather views above this surface. */
@androidx.annotation.OptIn(UnstableApi::class)
class ToposcanView(
    context: Context,
    val hdrMode: Boolean = false,
) : GLSurfaceView(context),
    Choreographer.FrameCallback {
    var onSurfaceReady: ((Surface) -> Unit)? = null
    var onFinished: (() -> Unit)? = null
    var onFailure: (() -> Unit)? = null
    var onUnsupportedContent: ((String) -> Unit)? = null
    var onColourModeRequired: ((Boolean) -> Unit)? = null
    var videoSurface: Surface? = null
        private set

    @Volatile
    var frameState = FrameState(ScanPhase.WAIT, 0.0, false, 1f)
        private set
    private var released = false
    private var bypassRequested = false
    private var modeChangeRequested = false
    private val hdrSupport = HdrSupport.inspect(context)
    private val hdrEgl = if (hdrMode) HdrEgl() else null
    private var maxTextureSize = 2048

    @Volatile
    var renderSize = RenderSize(1920, 1080)
        private set
    private val settings =
        ScanSettings(
            scan = GeneralPrefs.toposcanScan.toDoubleOrNull()?.coerceIn(8.0, 96.0) ?: 32.0,
            freezeDelay = GeneralPrefs.toposcanFreezeDelay.toDoubleOrNull()?.coerceIn(0.0, 16.0) ?: 4.0,
            hold = GeneralPrefs.toposcanHold.toDoubleOrNull()?.coerceIn(0.0, 30.0) ?: 5.0,
            field = GeneralPrefs.toposcanField.toDoubleOrNull()?.coerceIn(1.0, 20.0) ?: 8.0,
            bandHeight = GeneralPrefs.toposcanBandHeight.toFloatOrNull()?.coerceIn(1f, 12f) ?: 3f,
            width = GeneralPrefs.toposcanResolution.toIntOrNull()?.coerceIn(1280, 3840) ?: 3840,
            direction = GeneralPrefs.toposcanDirection,
        )
    private val renderer = ScanRenderer()

    init {
        if (hdrEgl != null) {
            holder.setFormat(PixelFormat.RGBA_1010102)
            setEGLConfigChooser(hdrEgl)
            setEGLContextFactory(hdrEgl)
            setEGLWindowSurfaceFactory(hdrEgl)
        } else {
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 0, 0, 0)
        }
        setZOrderMediaOverlay(true)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateBufferSize()
    }

    private fun updateBufferSize() {
        if (released || width <= 0 || height <= 0) return
        val mode = display?.mode?.takeIf { DeviceHelper.isTV(context) }
        val size = RenderSize.choose(mode?.physicalWidth ?: width, mode?.physicalHeight ?: height, settings.width, maxTextureSize)
        holder.setFixedSize(size.width, size.height)
        renderSize = size
    }

    fun acceptsVideo(format: Format): Boolean {
        if (VideoColourPolicy.requiresStandardPlayer(format)) {
            bypassUnsupportedContent("HLG, Dolby Vision or unsupported wide-colour format")
            return false
        }
        val needsHdr = VideoColourPolicy.isHdr10(format)
        if (needsHdr && (!GeneralPrefs.toposcanHdrEnabled || !hdrSupport.available)) {
            bypassUnsupportedContent(if (!GeneralPrefs.toposcanHdrEnabled) "HDR effect disabled in settings" else hdrSupport.reason)
            return false
        }
        if (needsHdr != hdrMode) {
            if (!modeChangeRequested) {
                modeChangeRequested = true
                setPaused(true)
                post { if (!released) onColourModeRequired?.invoke(needsHdr) }
            }
            return false
        }
        return !bypassRequested && !modeChangeRequested
    }

    fun bypassUnsupportedContent(reason: String = "HDR effect unavailable") {
        if (released || bypassRequested) return
        bypassRequested = true
        setPaused(true)
        post {
            if (!released) {
                GeneralPrefs.toposcanHdrStatus = reason
                onUnsupportedContent?.invoke(reason)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (released) return
        requestRender()
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun beginVideo(
        aspect: Float,
        fps: Float,
        colour: ColorInfo? = null,
    ) {
        if (!released) queueEvent { renderer.beginVideo(aspect, fps, colour) }
    }

    fun beginImage(bitmap: Bitmap) {
        if (released) {
            bitmap.recycle()
            return
        }
        queueEvent { renderer.beginImage(bitmap) }
    }

    fun setPaused(paused: Boolean) {
        if (!released) queueEvent { renderer.timeline.paused = paused }
    }

    fun setBlackout(enabled: Boolean) {
        if (!released) queueEvent { renderer.blackout = enabled }
    }

    fun release() {
        if (released) return
        released = true
        Choreographer.getInstance().removeFrameCallback(this)
        onSurfaceReady = null
        onFinished = null
        onFailure = null
        onUnsupportedContent = null
        onColourModeRequired = null
        queueEvent { renderer.release() }
        onPause()
        videoSurface = null
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    private inner class ScanRenderer : Renderer {
        val timeline = ScanTimeline(settings)
        var blackout = false
        private val pendingFrame = AtomicBoolean()
        private var surfaceTexture: SurfaceTexture? = null
        private var surface: Surface? = null
        private var oes = 0
        private var photo = 0
        private var live = 0
        private var history = 0
        private var previous = 0
        private var composite = 0
        private var hdrPipeline: HdrFramePipeline? = null
        private var framebuffer = 0
        private var scene = 0
        private var externalCopy = 0
        private var imageCopy = 0
        private var width = 1
        private var height = 1
        private var sourceAspect = 1f
        private var active = false
        private var firstVideoFrame = false
        private var hasPrevious = false
        private var previousDirection = 1f
        private var frozen = 0
        private var lastWall = 0L
        private var lastVideoTimestamp = Long.MIN_VALUE
        private var failed = false
        private var pendingImage: Bitmap? = null
        private val transform = FloatArray(16)
        private val vertices =
            ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
                position(0)
            }

        override fun onSurfaceCreated(
            gl: GL10?,
            config: EGLConfig?,
        ) {
            try {
                if (hdrMode) {
                    check(hdrEgl?.ready == true) { hdrEgl?.failure ?: "HDR EGL unavailable" }
                    check(GlUtil.isYuvTargetExtensionSupported()) { "GPU lacks GL_EXT_YUV_target for HDR decoder frames" }
                    hdrPipeline = HdrFramePipeline(context)
                }
                val limit = IntArray(2)
                glGetIntegerv(GL_MAX_TEXTURE_SIZE, limit, 0)
                var maximum = limit[0]
                glGetIntegerv(GL_MAX_RENDERBUFFER_SIZE, limit, 0)
                maximum = minOf(maximum, limit[0])
                glGetIntegerv(GL_MAX_VIEWPORT_DIMS, limit, 0)
                maximum = minOf(maximum, limit[0], limit[1])
                post {
                    maxTextureSize = maximum
                    updateBufferSize()
                }
                active = false
                firstVideoFrame = false
                hasPrevious = false
                pendingFrame.set(false)
                surface?.release()
                surfaceTexture?.release()
                scene = program(SCENE)
                externalCopy = program(EXTERNAL_COPY)
                imageCopy = program(IMAGE_COPY)
                oes = texture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
                photo = texture()
                live = texture()
                history = texture()
                previous = texture()
                if (hdrMode) composite = texture()
                val ids = IntArray(1)
                glGenFramebuffers(1, ids, 0)
                framebuffer = ids[0]
                surfaceTexture =
                    SurfaceTexture(oes).apply {
                        setOnFrameAvailableListener {
                            pendingFrame.set(true)
                            requestRender()
                        }
                    }
                surface = Surface(surfaceTexture)
                post {
                    if (!released) {
                        videoSurface = surface
                        surface?.let { onSurfaceReady?.invoke(it) }
                    }
                }
            } catch (e: Exception) {
                fail(e)
            }
        }

        override fun onSurfaceChanged(
            gl: GL10?,
            w: Int,
            h: Int,
        ) {
            width = w
            height = h
            if (failed) return
            try {
                for (id in if (hdrMode) intArrayOf(live, history, previous, composite) else intArrayOf(live, history, previous)) {
                    glBindTexture(GL_TEXTURE_2D, id)
                    glTexImage2D(
                        GL_TEXTURE_2D,
                        0,
                        if (hdrMode) GLES30.GL_RGBA16F else GL_RGBA,
                        w,
                        h,
                        0,
                        GL_RGBA,
                        if (hdrMode) GLES30.GL_HALF_FLOAT else GL_UNSIGNED_BYTE,
                        null,
                    )
                    target(id)
                    glClearColor(0f, 0f, 0f, 1f)
                    glClear(GL_COLOR_BUFFER_BIT)
                }
                check(glGetError() == GL_NO_ERROR) { "Toposcan texture allocation failed: ${w}x$h" }
                renderSize = RenderSize(w, h)
                val status = if (hdrMode) "HDR10 / BT.2020 PQ / RGB10_A2 output / FP16 processing" else "SDR / RGB8"
                Timber.i("Toposcan buffer: ${w}x$h, $status; working textures: ${w.toLong() * h * (if (hdrMode) 32 else 12) / 1048576} MiB")
                if (hdrMode) post { GeneralPrefs.toposcanHdrStatus = "${w}x$h $status" }
                glBindFramebuffer(GL_FRAMEBUFFER, 0)
                hasPrevious = false
                frozen = 0
                if (active && !timeline.video && pendingImage == null) copySource(false)
                pendingImage?.let {
                    pendingImage = null
                    beginImage(it)
                }
            } catch (e: Exception) {
                fail(e)
            }
        }

        fun beginVideo(
            aspect: Float,
            fps: Float,
            colour: ColorInfo?,
        ) {
            if (failed) return
            try {
                hdrPipeline?.configureInput(checkNotNull(colour), width, height)
                carry()
                sourceAspect = aspect.takeIf { it > 0f } ?: (width.toFloat() / height)
                timeline.begin(true, fps.toDouble())
                firstVideoFrame = true
                active = true
                lastWall = System.nanoTime()
            } catch (e: Exception) {
                fail(e)
            }
        }

        fun beginImage(bitmap: Bitmap) {
            if (scene == 0 || width <= 1 || height <= 1) {
                pendingImage?.recycle()
                pendingImage = bitmap
                return
            }
            try {
                if (failed) return
                carry()
                sourceAspect = bitmap.width.toFloat() / bitmap.height
                glBindTexture(GL_TEXTURE_2D, photo)
                GLUtils.texImage2D(GL_TEXTURE_2D, 0, bitmap, 0)
                timeline.begin(false, 30.0)
                copySource(false)
                active = true
                lastWall = System.nanoTime()
            } catch (e: Exception) {
                fail(e)
            } finally {
                bitmap.recycle()
            }
        }

        private fun carry() {
            if (active && timeline.phase != ScanPhase.FIELD) {
                target(previous)
                // A skipped item carries its current live/frozen image, never uninitialised columns.
                drawScene(ScanPhase.REVEAL, 1f, frozen.toFloat() / width, live)
                hasPrevious = true
                previousDirection = timeline.direction
            }
            frozen = 0
            lastVideoTimestamp = Long.MIN_VALUE
        }

        override fun onDrawFrame(gl: GL10?) {
            if (failed) return
            try {
                var timestamp: Long? = null
                if (pendingFrame.getAndSet(false)) {
                    surfaceTexture?.updateTexImage()
                    surfaceTexture?.getTransformMatrix(transform)
                    val latest = surfaceTexture?.timestamp
                    if (latest != lastVideoTimestamp) {
                        timestamp = latest
                        lastVideoTimestamp = latest ?: Long.MIN_VALUE
                    }
                }
                if (!active || blackout) {
                    glBindFramebuffer(GL_FRAMEBUFFER, 0)
                    glClearColor(0f, 0f, 0f, 1f)
                    glClear(GL_COLOR_BUFFER_BIT)
                    return
                }
                val now = System.nanoTime()
                val elapsed = (now - lastWall) / 1e9
                lastWall = now
                if (!timeline.paused && timeline.video &&
                    (timeline.phase == ScanPhase.FIELD || timeline.phase == ScanPhase.REVEAL) &&
                    (timestamp != null || firstVideoFrame)
                ) {
                    copySource(true)
                    firstVideoFrame = false
                }
                timeline.advance(elapsed, timestamp)
                if (timeline.phase == ScanPhase.REVEAL) capture(timeline.freeze)
                if (hdrMode) {
                    target(composite)
                } else {
                    glBindFramebuffer(GL_FRAMEBUFFER, 0)
                    glViewport(0, 0, width, height)
                }
                drawScene(
                    timeline.phase,
                    if (timeline.phase ==
                        ScanPhase.REVEAL
                    ) {
                        timeline.reveal
                    } else {
                        timeline.progress
                    },
                    frozen.toFloat() / width,
                )
                hdrPipeline?.let {
                    glBindFramebuffer(GL_FRAMEBUFFER, 0)
                    glViewport(0, 0, width, height)
                    it.present(composite)
                }
                val before = timeline.phase
                frameState = FrameState(before, timeline.time, timeline.video, timeline.direction)
                timeline.finishFrame()
                if (before != ScanPhase.WAIT && timeline.phase == ScanPhase.WAIT) {
                    post { if (!released) onFinished?.invoke() }
                }
                val error = glGetError()
                check(error == GL_NO_ERROR) { "Toposcan GL error: $error" }
            } catch (e: Exception) {
                fail(e)
            }
        }

        private fun copySource(video: Boolean) {
            target(live)
            if (video && hdrPipeline != null) {
                hdrPipeline?.copyVideo(oes, transform, width, height, sourceAspect)
                return
            }
            val p = if (video) externalCopy else imageCopy
            use(p)
            bind(p, "uSource", 0, if (video) oes else photo, if (video) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GL_TEXTURE_2D)
            glUniform2f(glGetUniformLocation(p, "uSize"), width.toFloat(), height.toFloat())
            glUniform1f(glGetUniformLocation(p, "uAspect"), sourceAspect)
            if (video) {
                glUniformMatrix4fv(glGetUniformLocation(p, "uTransform"), 1, false, transform, 0)
            } else {
                glUniform1f(glGetUniformLocation(p, "uFlip"), 1f)
            }
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        }

        private fun capture(front: Float) {
            val end = (front * width).toInt().coerceIn(frozen, width)
            if (end == frozen) return
            target(history)
            use(imageCopy)
            bind(imageCopy, "uSource", 0, live)
            glUniform2f(glGetUniformLocation(imageCopy, "uSize"), width.toFloat(), height.toFloat())
            glUniform1f(glGetUniformLocation(imageCopy, "uAspect"), width.toFloat() / height)
            glUniform1f(glGetUniformLocation(imageCopy, "uFlip"), 0f)
            glEnable(GL_SCISSOR_TEST)
            glScissor(if (timeline.direction > 0) frozen else width - end, 0, end - frozen, height)
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
            glDisable(GL_SCISSOR_TEST)
            frozen = end
        }

        private fun drawScene(
            phase: ScanPhase,
            progress: Float,
            freeze: Float,
            previousTexture: Int = previous,
        ) {
            use(scene)
            bind(scene, "uLive", 0, live)
            bind(scene, "uHistory", 1, history)
            bind(scene, "uPrevious", 2, previousTexture)
            glUniform1f(glGetUniformLocation(scene, "uPhase"), phase.ordinal.toFloat())
            glUniform1f(glGetUniformLocation(scene, "uLinearLight"), if (hdrMode) 1f else 0f)
            glUniform1f(glGetUniformLocation(scene, "uProgress"), progress)
            glUniform1f(glGetUniformLocation(scene, "uFreeze"), freeze)
            glUniform1f(glGetUniformLocation(scene, "uDirection"), timeline.direction)
            glUniform1f(glGetUniformLocation(scene, "uPreviousDirection"), previousDirection)
            glUniform1f(glGetUniformLocation(scene, "uHasPrevious"), if (hasPrevious) 1f else 0f)
            glUniform1f(glGetUniformLocation(scene, "uBand"), maxOf(1f, settings.bandHeight * height / 1080f) / height)
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        }

        private fun target(id: Int) {
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer)
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, id, 0)
            check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) { "Toposcan framebuffer is incomplete" }
            glViewport(0, 0, width, height)
        }

        private fun use(id: Int) {
            glUseProgram(id)
            vertices.position(0)
            glEnableVertexAttribArray(0)
            glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, vertices)
        }

        private fun bind(
            p: Int,
            name: String,
            unit: Int,
            id: Int,
            type: Int = GL_TEXTURE_2D,
        ) {
            glActiveTexture(GL_TEXTURE0 + unit)
            glBindTexture(type, id)
            glUniform1i(glGetUniformLocation(p, name), unit)
        }

        private fun texture(type: Int = GL_TEXTURE_2D): Int {
            val ids = IntArray(1)
            glGenTextures(1, ids, 0)
            glBindTexture(type, ids[0])
            glTexParameteri(type, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(type, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(type, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(type, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            return ids[0]
        }

        private fun program(fragment: String): Int {
            val p = glCreateProgram()
            for ((type, source) in listOf(GL_VERTEX_SHADER to VERTEX, GL_FRAGMENT_SHADER to fragment)) {
                val shader = glCreateShader(type)
                glShaderSource(shader, source)
                glCompileShader(shader)
                val ok = IntArray(1)
                glGetShaderiv(shader, GL_COMPILE_STATUS, ok, 0)
                check(ok[0] != 0) { glGetShaderInfoLog(shader) }
                glAttachShader(p, shader)
                glDeleteShader(shader)
            }
            glBindAttribLocation(p, 0, "aPosition")
            glLinkProgram(p)
            val ok = IntArray(1)
            glGetProgramiv(p, GL_LINK_STATUS, ok, 0)
            check(ok[0] != 0) { glGetProgramInfoLog(p) }
            return p
        }

        private fun fail(e: Exception) {
            failed = true
            Timber.e(e, "Toposcan renderer failed")
            if (hdrMode) {
                post { bypassUnsupportedContent(e.message ?: "HDR graphics initialization failed") }
            } else {
                post { if (!released) onFailure?.invoke() }
            }
        }

        fun release() {
            pendingImage?.recycle()
            pendingImage = null
            surfaceTexture?.setOnFrameAvailableListener(null)
            surface?.release()
            surfaceTexture?.release()
            hdrPipeline?.release()
            hdrPipeline = null
            glDeleteTextures(6, intArrayOf(oes, photo, live, history, previous, composite), 0)
            glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
            for (p in intArrayOf(scene, externalCopy, imageCopy)) glDeleteProgram(p)
        }
    }

    companion object {
        private const val VERTEX = """
            attribute vec2 aPosition;
            varying vec2 vUV;
            void main() { vUV = aPosition * 0.5 + 0.5; gl_Position = vec4(aPosition, 0.0, 1.0); }
        """
        private const val COVER = """
            precision highp float;
            varying vec2 vUV;
            uniform vec2 uSize;
            uniform float uAspect;
            vec2 cover(vec2 uv) {
                float aspect = uSize.x / uSize.y;
                return (uv - 0.5) * vec2(min(aspect / uAspect, 1.0), min(uAspect / aspect, 1.0)) + 0.5;
            }
        """
        private const val IMAGE_COPY =
            COVER + """
            uniform highp sampler2D uSource;
            uniform float uFlip;
            void main() {
                vec2 uv = cover(vUV);
                if (uFlip > 0.5) uv.y = 1.0 - uv.y;
                gl_FragColor = texture2D(uSource, uv);
            }
        """
        private const val EXTERNAL_COPY =
            "#extension GL_OES_EGL_image_external : require\n" + COVER + """
            uniform samplerExternalOES uSource;
            uniform mat4 uTransform;
            void main() { gl_FragColor = texture2D(uSource, (uTransform * vec4(cover(vUV), 0.0, 1.0)).xy); }
        """
        internal const val SCENE = """
            precision highp float;
            varying vec2 vUV;
            uniform highp sampler2D uLive, uHistory, uPrevious;
            uniform float uPhase, uProgress, uFreeze, uDirection, uPreviousDirection, uHasPrevious, uBand, uLinearLight;
            float directed(float x, float d) { return d > 0.0 ? x : 1.0 - x; }
            void main() {
                vec2 uv = vUV;
                float x = directed(uv.x, uDirection);
                float y = (floor(uv.y / uBand) + 0.5) * uBand;
                vec4 colour;
                if (uPhase < 0.5) {
                    vec4 b = texture2D(uLive, vec2(directed(0.0, uDirection), y));
                    vec4 a = uHasPrevious > 0.5 ? texture2D(uPrevious, vec2(directed(1.0, uPreviousDirection), y)) : b;
                    float p = smoothstep(0.0, 1.0, uProgress);
                    colour = uLinearLight > 0.5 ? mix(a, b, p) :
                        vec4(pow(mix(pow(a.rgb, vec3(2.2)), pow(b.rgb, vec3(2.2)), p), vec3(1.0 / 2.2)), 1.0);
                } else if (uPhase < 1.5) {
                    if (x > uProgress) colour = texture2D(uLive, vec2(directed(uProgress, uDirection), y));
                    else if (x <= uFreeze) colour = texture2D(uHistory, uv);
                    else colour = texture2D(uLive, uv);
                } else if (uPhase < 2.5) {
                    colour = texture2D(uHistory, uv);
                } else {
                    float p = uPhase > 3.5 ? 1.0 : uProgress;
                    colour = texture2D(uHistory, x < p ? vec2(directed(p, uDirection), y) : uv);
                }
                gl_FragColor = vec4(colour.rgb, 1.0);
            }
        """
    }

    data class FrameState(
        val phase: ScanPhase,
        val time: Double,
        val video: Boolean,
        val direction: Float,
    )
}
