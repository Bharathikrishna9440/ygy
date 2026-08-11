package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.util.FocusTimerManager
import com.example.util.StableTime

object WidgetUpdater {

    fun getPendingIntentFlags(isMutable: Boolean = false): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (isMutable) PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    /**
     * Programmatically requests the Android Launcher to pin a widget to the Home Screen (Android 8.0+ / API 26+)
     */
    fun requestPinWidget(context: Context, providerClass: Class<*>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                val myProvider = ComponentName(context, providerClass)
                val successCallback = PendingIntent.getBroadcast(
                    context,
                    9000,
                    Intent(context, providerClass).apply { action = "com.example.widget.ACTION_WIDGET_PINNED" },
                    getPendingIntentFlags()
                )
                appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
            }
        }
    }

    /**
     * Updates the Friends Focus Widget ("Who is Focusing")
     * Displays logos/avatars of active focusing users without any text labels.
     */
    fun updateFriendsFocusWidget(context: Context, statusText: String? = null) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, FriendsFocusWidgetProvider::class.java)
        val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        if (allWidgetIds.isEmpty()) return

        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val glassStyle = prefs.getString("widget_glass_style", "black_glass") ?: "black_glass"
        val bgRes = if (glassStyle == "clear_glass") R.drawable.widget_background_clear_glass else R.drawable.widget_background_black_glass

        data class FocusingUserLogo(val name: String, val avatar: String)
        val focusingLogos = mutableListOf<FocusingUserLogo>()

        FocusTimerManager.init(context)
        val isMeFocusing = (FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value)
                && FocusTimerManager.isFocusPhase.value
                && !FocusTimerManager.isPaused.value
                && FocusTimerManager.pendingFocusReview.value == null

        val myEmail = prefs.getString("user_email", "") ?: ""
        val myUsername = prefs.getString("username", "") ?: ""

        if (isMeFocusing) {
            val myName = prefs.getString("username", "")?.ifEmpty { prefs.getString("nickname", "Me") } ?: "Me"
            val myEmoji = prefs.getString("user_emoji", "") ?: ""
            val googleAccount = try { com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context) } catch (e: Throwable) { null }
            val photoUrl = googleAccount?.photoUrl?.toString() ?: prefs.getString("user_photo_url", "") ?: ""
            val myAvatar = when {
                myEmoji.isNotEmpty() && myEmoji != "👤" -> myEmoji
                photoUrl.isNotEmpty() -> photoUrl
                else -> prefs.getString("user_avatar_base64", "") ?: ""
            }
            focusingLogos.add(FocusingUserLogo(myName, myAvatar))
        }

        val activePeers = com.example.api.PeerLiveSphereManager.peerLiveStates.value.filter { (key, peer) ->
            !com.example.api.DevicePresenceManager.isMeUser(
                key = key,
                userId = peer.userId,
                myEmail = myEmail,
                myUsername = myUsername
            ) && peer.status.equals("Focusing", ignoreCase = true)
        }.values

        activePeers.forEach { peer ->
            val peerAvatar = peer.customEmoji ?: ""
            if (focusingLogos.none { it.name.equals(peer.displayName, ignoreCase = true) }) {
                focusingLogos.add(FocusingUserLogo(peer.displayName, peerAvatar))
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SHOW_TIMER_PAGE", true)
        }
        val pendingIntent = PendingIntent.getActivity(context, 2001, intent, getPendingIntentFlags())

        val logoIds = arrayOf(
            R.id.focus_logo_1,
            R.id.focus_logo_2,
            R.id.focus_logo_3,
            R.id.focus_logo_4,
            R.id.focus_logo_5
        )

        for (widgetId in allWidgetIds) {
            val largeView = RemoteViews(context.packageName, R.layout.widget_friends_focus).apply {
                setOnClickPendingIntent(android.R.id.background, pendingIntent)
                setInt(android.R.id.background, "setBackgroundResource", bgRes)
                if (focusingLogos.isEmpty()) {
                    setViewVisibility(R.id.focus_logo_idle, android.view.View.VISIBLE)
                    val idleBmp = createAvatarLogoBitmap(context, "💤", "Idle", isIdle = true, sizeDp = 44)
                    setImageViewBitmap(R.id.focus_logo_idle, idleBmp)
                    for (id in logoIds) {
                        setViewVisibility(id, android.view.View.GONE)
                    }
                } else {
                    setViewVisibility(R.id.focus_logo_idle, android.view.View.GONE)
                    for (i in logoIds.indices) {
                        if (i < focusingLogos.size) {
                            val logo = focusingLogos[i]
                            val bmp = createAvatarLogoBitmap(context, logo.avatar, logo.name, isIdle = false, sizeDp = 44)
                            setImageViewBitmap(logoIds[i], bmp)
                            setViewVisibility(logoIds[i], android.view.View.VISIBLE)
                        } else {
                            setViewVisibility(logoIds[i], android.view.View.GONE)
                        }
                    }
                }
            }

            val finalViews = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val smallLogoIds = arrayOf(
                    R.id.focus_logo_1,
                    R.id.focus_logo_2,
                    R.id.focus_logo_3,
                    R.id.focus_logo_4
                )
                val smallView = RemoteViews(context.packageName, R.layout.widget_friends_focus_small).apply {
                    setOnClickPendingIntent(android.R.id.background, pendingIntent)
                    setInt(android.R.id.background, "setBackgroundResource", bgRes)
                    if (focusingLogos.isEmpty()) {
                        setViewVisibility(R.id.focus_logo_idle, android.view.View.VISIBLE)
                        val idleBmp = createAvatarLogoBitmap(context, "💤", "Idle", isIdle = true, sizeDp = 36)
                        setImageViewBitmap(R.id.focus_logo_idle, idleBmp)
                        for (id in smallLogoIds) {
                            setViewVisibility(id, android.view.View.GONE)
                        }
                    } else {
                        setViewVisibility(R.id.focus_logo_idle, android.view.View.GONE)
                        for (i in smallLogoIds.indices) {
                            if (i < focusingLogos.size) {
                                val logo = focusingLogos[i]
                                val bmp = createAvatarLogoBitmap(context, logo.avatar, logo.name, isIdle = false, sizeDp = 36)
                                setImageViewBitmap(smallLogoIds[i], bmp)
                                setViewVisibility(smallLogoIds[i], android.view.View.VISIBLE)
                            } else {
                                setViewVisibility(smallLogoIds[i], android.view.View.GONE)
                            }
                        }
                    }
                }
                val viewMap = mapOf(
                    SizeF(140f, 50f) to smallView,
                    SizeF(200f, 80f) to largeView
                )
                RemoteViews(viewMap)
            } else {
                largeView
            }

            appWidgetManager.updateAppWidget(widgetId, finalViews)
        }
    }

    private fun createAvatarLogoBitmap(
        context: Context,
        logoTextOrEmoji: String,
        displayName: String,
        isIdle: Boolean = false,
        sizeDp: Int = 44
    ): android.graphics.Bitmap {
        val density = context.resources.displayMetrics.density
        val px = (sizeDp * density).toInt().coerceAtLeast(32)

        if (!isIdle) {
            val decodedBmp = decodeAvatarBitmap(context, logoTextOrEmoji, px)
            if (decodedBmp != null) {
                return decodedBmp
            }
        }

        val bitmap = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val radius = px / 2f

        val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            color = if (isIdle) {
                android.graphics.Color.argb(50, 255, 255, 255)
            } else {
                val colors = intArrayOf(
                    android.graphics.Color.rgb(16, 185, 129),
                    android.graphics.Color.rgb(59, 130, 246),
                    android.graphics.Color.rgb(139, 92, 246),
                    android.graphics.Color.rgb(236, 72, 153),
                    android.graphics.Color.rgb(245, 158, 11)
                )
                val colorIndex = Math.abs(displayName.hashCode()) % colors.size
                colors[colorIndex]
            }
        }

        val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2f * density
            color = if (isIdle) android.graphics.Color.argb(80, 255, 255, 255) else android.graphics.Color.rgb(16, 185, 129)
        }

        canvas.drawCircle(radius, radius, radius - (1f * density), bgPaint)
        canvas.drawCircle(radius, radius, radius - (1f * density), borderPaint)

        val textToDraw = when {
            isIdle -> "💤"
            logoTextOrEmoji.isNotEmpty() && logoTextOrEmoji != "👤" && logoTextOrEmoji.length <= 8 -> logoTextOrEmoji
            displayName.isNotBlank() -> {
                val parts = displayName.trim().split(" ")
                if (parts.size >= 2) {
                    "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
                } else {
                    displayName.take(2).uppercase()
                }
            }
            else -> "👤"
        }

        val isEmoji = textToDraw.any { 
            Character.getType(it) == Character.SURROGATE.toInt() || 
            Character.getType(it) == Character.OTHER_SYMBOL.toInt() 
        } || textToDraw == "💤" || textToDraw == "🎯" || textToDraw == "👤"

        val textPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = if (isEmoji) radius * 1.0f else radius * 0.75f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }

        val fontMetrics = textPaint.fontMetrics
        val baseline = radius - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(textToDraw, radius, baseline, textPaint)

        return bitmap
    }

    private fun decodeAvatarBitmap(context: Context, avatarStr: String, targetPx: Int): android.graphics.Bitmap? {
        val trimmed = avatarStr.trim()
        if (trimmed.isEmpty() || trimmed == "👤" || trimmed == "🎯" || trimmed == "💤") return null

        try {
            // Case 1: Base64
            if (trimmed.startsWith("base64:") || trimmed.startsWith("data:image/") || (trimmed.length > 60 && !trimmed.contains(" ") && !trimmed.startsWith("http"))) {
                val rawData = when {
                    trimmed.startsWith("base64:") -> trimmed.substringAfter("base64:")
                    trimmed.contains("base64,") -> trimmed.substringAfter("base64,")
                    else -> trimmed
                }
                val bytes = android.util.Base64.decode(rawData, android.util.Base64.DEFAULT)
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) return getCircularBitmap(bmp, targetPx)
            }

            // Case 2: URL (http/https)
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                val cacheFile = java.io.File(context.cacheDir, "widget_avatar_${Math.abs(trimmed.hashCode())}.png")
                if (cacheFile.exists() && cacheFile.length() > 0) {
                    val bmp = android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath)
                    if (bmp != null) return getCircularBitmap(bmp, targetPx)
                }
                val url = java.net.URL(trimmed)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.doInput = true
                conn.connect()
                val inputStream = conn.inputStream
                val bytes = inputStream.readBytes()
                inputStream.close()
                conn.disconnect()
                cacheFile.writeBytes(bytes)
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) return getCircularBitmap(bmp, targetPx)
            }

            // Case 3: Local file path
            if (trimmed.startsWith("/") || trimmed.startsWith("file://")) {
                val path = trimmed.removePrefix("file://")
                val bmp = android.graphics.BitmapFactory.decodeFile(path)
                if (bmp != null) return getCircularBitmap(bmp, targetPx)
            }
        } catch (e: Exception) {
            Log.e("WidgetUpdater", "Failed to decode avatar bitmap: ${e.message}")
        }
        return null
    }

    private fun getCircularBitmap(src: android.graphics.Bitmap, sizePx: Int): android.graphics.Bitmap {
        val output = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val rect = android.graphics.Rect(0, 0, sizePx, sizePx)

        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)

        val minDim = Math.min(src.width, src.height)
        val srcRect = android.graphics.Rect(
            (src.width - minDim) / 2,
            (src.height - minDim) / 2,
            (src.width + minDim) / 2,
            (src.height + minDim) / 2
        )
        canvas.drawBitmap(src, srcRect, rect, paint)

        val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 3f
            color = android.graphics.Color.rgb(16, 185, 129)
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, (sizePx / 2f) - 1.5f, borderPaint)

        return output
    }

    private fun formatTime(seconds: Int): String {
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
        }
    }

    /**
     * Updates the Stopwatch Widget using Chronometer and responsive layouts (Android 12+ API 31+)
     */
    fun updateStopwatchWidget(context: Context, isPartialUpdate: Boolean = false) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, TimerStopwatchWidgetProvider::class.java)
        val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        if (allWidgetIds.isEmpty()) return

        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val glassStyle = prefs.getString("widget_glass_style", "black_glass") ?: "black_glass"
        val bgRes = if (glassStyle == "clear_glass") R.drawable.widget_background_clear_glass else R.drawable.widget_background_black_glass

        FocusTimerManager.init(context)
        val isStopwatchActive = FocusTimerManager.isStopwatchActive.value
        val isPaused = FocusTimerManager.isPaused.value
        val wasStartedFromStopwatch = FocusTimerManager.wasStartedFromStopwatch.value

        val isStopwatchMode = isStopwatchActive || (isPaused && wasStartedFromStopwatch)
        val isRunning = isStopwatchActive && !isPaused

        val lastResumeMs = FocusTimerManager.lastResumeTimeMs.value
        val currentChunkMs = if (lastResumeMs != null && isRunning) maxOf(0L, StableTime.currentTimeMillis() - lastResumeMs) else 0L
        val baseAccumulatedMs = if (isStopwatchMode) FocusTimerManager.accumulatedSessionTimeMs.value else 0L
        val totalElapsedMs = if (isStopwatchMode) baseAccumulatedMs + currentChunkMs else 0L
        val seconds = (totalElapsedMs / 1000).toInt()

        val runningBaseTime = android.os.SystemClock.elapsedRealtime() - totalElapsedMs
        val staticBaseTime = android.os.SystemClock.elapsedRealtime() - (seconds * 1000L)

        val startPauseIntent = Intent(context, TimerStopwatchWidgetProvider::class.java).apply {
            action = "com.example.widget.ACTION_STOPWATCH_START_PAUSE"
        }
        val startPausePending = PendingIntent.getBroadcast(context, 3001, startPauseIntent, getPendingIntentFlags())

        val breakIntent = Intent(context, TimerStopwatchWidgetProvider::class.java).apply {
            action = "com.example.widget.ACTION_STOPWATCH_BREAK"
        }
        val breakPending = PendingIntent.getBroadcast(context, 3004, breakIntent, getPendingIntentFlags())

        val resetIntent = Intent(context, TimerStopwatchWidgetProvider::class.java).apply {
            action = "com.example.widget.ACTION_STOPWATCH_RESET"
        }
        val resetPending = PendingIntent.getBroadcast(context, 3002, resetIntent, getPendingIntentFlags())

        val rootIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SHOW_TIMER_PAGE", true)
        }
        val rootPending = PendingIntent.getActivity(context, 3003, rootIntent, getPendingIntentFlags())

        val btnStartPauseText = if (isRunning) "⏸ PAUSE" else if (isPaused && wasStartedFromStopwatch) "▶ RESUME" else "▶ START"
        val btnResetText = if (isRunning || (isPaused && wasStartedFromStopwatch) || seconds > 0) "◼ END" else "◼ RESET"

        for (widgetId in allWidgetIds) {
            val largeView = RemoteViews(context.packageName, R.layout.widget_stopwatch).apply {
                setInt(android.R.id.background, "setBackgroundResource", bgRes)
                if (isRunning) {
                    setChronometer(R.id.stopwatch_time_display, runningBaseTime, null, true)
                } else {
                    val staticText = formatTime(seconds)
                    setChronometer(R.id.stopwatch_time_display, staticBaseTime, null, false)
                    setTextViewText(R.id.stopwatch_time_display, staticText)
                }

                setTextViewText(R.id.btn_stopwatch_start_pause, btnStartPauseText)
                setOnClickPendingIntent(R.id.btn_stopwatch_start_pause, startPausePending)

                setTextViewText(R.id.btn_stopwatch_reset, btnResetText)
                setOnClickPendingIntent(R.id.btn_stopwatch_reset, resetPending)
                if (isRunning || (isPaused && wasStartedFromStopwatch) || seconds > 0) {
                    setViewVisibility(R.id.btn_stopwatch_reset, android.view.View.VISIBLE)
                } else {
                    setViewVisibility(R.id.btn_stopwatch_reset, android.view.View.GONE)
                }

                if (isRunning) {
                    setViewVisibility(R.id.btn_stopwatch_break, android.view.View.VISIBLE)
                    setOnClickPendingIntent(R.id.btn_stopwatch_break, breakPending)
                } else {
                    setViewVisibility(R.id.btn_stopwatch_break, android.view.View.GONE)
                }

                setOnClickPendingIntent(R.id.stopwatch_title, rootPending)
                setOnClickPendingIntent(R.id.stopwatch_time_display, rootPending)
            }

            if (isPartialUpdate) {
                appWidgetManager.partiallyUpdateAppWidget(widgetId, largeView)
                continue
            }

            val finalViews = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val smallView = RemoteViews(context.packageName, R.layout.widget_stopwatch_small).apply {
                    setInt(android.R.id.background, "setBackgroundResource", bgRes)
                    if (isRunning) {
                        setChronometer(R.id.stopwatch_time_display, runningBaseTime, null, true)
                    } else {
                        val staticText = formatTime(seconds)
                        setChronometer(R.id.stopwatch_time_display, staticBaseTime, null, false)
                        setTextViewText(R.id.stopwatch_time_display, staticText)
                    }

                    setTextViewText(R.id.btn_stopwatch_start_pause, btnStartPauseText)
                    setOnClickPendingIntent(R.id.btn_stopwatch_start_pause, startPausePending)
                    setOnClickPendingIntent(R.id.stopwatch_time_display, rootPending)
                }
                val viewMap = mapOf(
                    SizeF(140f, 70f) to smallView,
                    SizeF(200f, 100f) to largeView
                )
                RemoteViews(viewMap)
            } else {
                largeView
            }

            appWidgetManager.updateAppWidget(widgetId, finalViews)
        }
    }

    /**
     * Updates the Pomodoro Widget using countdown Chronometer and responsive layouts (Android 12+ API 31+)
     */
    fun updatePomodoroWidget(context: Context, isPartialUpdate: Boolean = false) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, PomodoroWidgetProvider::class.java)
        val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        if (allWidgetIds.isEmpty()) return

        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val glassStyle = prefs.getString("widget_glass_style", "black_glass") ?: "black_glass"
        val bgRes = if (glassStyle == "clear_glass") R.drawable.widget_background_clear_glass else R.drawable.widget_background_black_glass

        FocusTimerManager.init(context)
        val isTimerRunning = FocusTimerManager.isTimerRunning.value
        val isPaused = FocusTimerManager.isPaused.value
        val wasStartedFromStopwatch = FocusTimerManager.wasStartedFromStopwatch.value
        val isFocus = FocusTimerManager.isFocusPhase.value

        val isPomodoroMode = isTimerRunning || (isPaused && !wasStartedFromStopwatch)
        val isRunning = isTimerRunning && !isPaused

        val totalDurationMs = if (isFocus) {
            FocusTimerManager.timerDurationMinutes.value * 60 * 1000L
        } else {
            val bMins = prefs.getInt("break_duration", 5)
            bMins * 60 * 1000L
        }

        val lastResumeMs = FocusTimerManager.lastResumeTimeMs.value
        val currentChunkMs = if (lastResumeMs != null && isRunning) maxOf(0L, StableTime.currentTimeMillis() - lastResumeMs) else 0L
        val baseAccumulatedMs = if (isPomodoroMode) FocusTimerManager.accumulatedSessionTimeMs.value else 0L
        val totalElapsedMs = if (isPomodoroMode) baseAccumulatedMs + currentChunkMs else 0L

        val remainingMs = maxOf(0L, totalDurationMs - totalElapsedMs)
        val displaySecs = (remainingMs / 1000).toInt()

        val runningBaseTime = android.os.SystemClock.elapsedRealtime() + remainingMs
        val staticBaseTime = android.os.SystemClock.elapsedRealtime() + (displaySecs * 1000L)

        val headerText = if (isFocus) "POMODORO FOCUS 🎯" else "REST BREAK ☕"
        val headerColor = if (isFocus) 0xFF30D158.toInt() else 0xFFFF9500.toInt()
        val btnStartPauseText = if (isRunning) "⏸ PAUSE" else if (isPaused && !wasStartedFromStopwatch) "▶ RESUME" else "▶ START"
        val btnBreakText = if (isFocus) "☕ BREAK" else "⏭ FOCUS"
        val btnResetText = if (isRunning || (isPaused && !wasStartedFromStopwatch)) "◼ END" else "◼ RESET"

        val startPauseIntent = Intent(context, PomodoroWidgetProvider::class.java).apply {
            action = "com.example.widget.ACTION_POMO_START_PAUSE"
        }
        val startPausePending = PendingIntent.getBroadcast(context, 4001, startPauseIntent, getPendingIntentFlags())

        val breakIntent = Intent(context, PomodoroWidgetProvider::class.java).apply {
            action = "com.example.widget.ACTION_POMO_BREAK"
        }
        val breakPending = PendingIntent.getBroadcast(context, 4004, breakIntent, getPendingIntentFlags())

        val resetIntent = Intent(context, PomodoroWidgetProvider::class.java).apply {
            action = "com.example.widget.ACTION_POMO_RESET"
        }
        val resetPending = PendingIntent.getBroadcast(context, 4002, resetIntent, getPendingIntentFlags())

        val rootIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SHOW_TIMER_PAGE", true)
        }
        val rootPending = PendingIntent.getActivity(context, 4003, rootIntent, getPendingIntentFlags())

        for (widgetId in allWidgetIds) {
            val largeView = RemoteViews(context.packageName, R.layout.widget_pomodoro).apply {
                setInt(android.R.id.background, "setBackgroundResource", bgRes)
                setTextViewText(R.id.pomo_title, headerText)
                setTextColor(R.id.pomo_title, headerColor)

                if (isRunning) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        setChronometerCountDown(R.id.pomo_time_display, true)
                    }
                    setChronometer(R.id.pomo_time_display, runningBaseTime, null, true)
                } else {
                    val staticText = formatTime(displaySecs)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        setChronometerCountDown(R.id.pomo_time_display, true)
                    }
                    setChronometer(R.id.pomo_time_display, staticBaseTime, null, false)
                    setTextViewText(R.id.pomo_time_display, staticText)
                }

                setTextViewText(R.id.btn_pomo_start_pause, btnStartPauseText)
                setOnClickPendingIntent(R.id.btn_pomo_start_pause, startPausePending)

                setTextViewText(R.id.btn_pomo_reset, btnResetText)
                setOnClickPendingIntent(R.id.btn_pomo_reset, resetPending)

                if (isRunning || !isFocus) {
                    setViewVisibility(R.id.btn_pomo_break, android.view.View.VISIBLE)
                    setTextViewText(R.id.btn_pomo_break, btnBreakText)
                    setOnClickPendingIntent(R.id.btn_pomo_break, breakPending)
                } else {
                    setViewVisibility(R.id.btn_pomo_break, android.view.View.GONE)
                }

                setOnClickPendingIntent(R.id.pomo_title, rootPending)
                setOnClickPendingIntent(R.id.pomo_time_display, rootPending)
            }

            if (isPartialUpdate) {
                appWidgetManager.partiallyUpdateAppWidget(widgetId, largeView)
                continue
            }

            val finalViews = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val smallView = RemoteViews(context.packageName, R.layout.widget_pomodoro_small).apply {
                    setInt(android.R.id.background, "setBackgroundResource", bgRes)
                    setTextViewText(R.id.pomo_title, headerText)
                    setTextColor(R.id.pomo_title, headerColor)

                    if (isRunning) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            setChronometerCountDown(R.id.pomo_time_display, true)
                        }
                        setChronometer(R.id.pomo_time_display, runningBaseTime, null, true)
                    } else {
                        val staticText = formatTime(displaySecs)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            setChronometerCountDown(R.id.pomo_time_display, true)
                        }
                        setChronometer(R.id.pomo_time_display, staticBaseTime, null, false)
                        setTextViewText(R.id.pomo_time_display, staticText)
                    }

                    setTextViewText(R.id.btn_pomo_start_pause, btnStartPauseText)
                    setOnClickPendingIntent(R.id.btn_pomo_start_pause, startPausePending)
                    setOnClickPendingIntent(R.id.pomo_time_display, rootPending)
                }
                val viewMap = mapOf(
                    SizeF(140f, 70f) to smallView,
                    SizeF(200f, 100f) to largeView
                )
                RemoteViews(viewMap)
            } else {
                largeView
            }

            appWidgetManager.updateAppWidget(widgetId, finalViews)
        }
    }

    /**
     * Forces full updates across all widgets
     */
    fun updateAllWidgets(context: Context) {
        try {
            updateFriendsFocusWidget(context)
            updateStopwatchWidget(context)
            updatePomodoroWidget(context)
            updateTotalFocusTimeWidget(context)
            updatePhotoShowerWidget(context)
        } catch (e: Exception) {
            Log.e("WidgetUpdater", "Error updating widgets: ${e.message}")
        }
    }

    fun calculateTodayTotalFocusSeconds(context: Context): Int {
        FocusTimerManager.init(context)
        val baseTodaySecs = FocusTimerManager.getTodayFocusSeconds()
        val pendingFocusReview = FocusTimerManager.pendingFocusReview.value
        val todayStr = com.example.util.SystemTimeService.getTodayString()
        val pendingSecs = pendingFocusReview?.let { FocusTimerManager.getOverlapSecondsForDate(it, todayStr) } ?: 0

        val isRunningOrPaused = FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value || FocusTimerManager.isPaused.value
        val activeSecs = if (FocusTimerManager.isFocusPhase.value && pendingFocusReview == null && isRunningOrPaused) {
            (FocusTimerManager.accumulatedSessionTimeMs.value / 1000).toInt()
        } else {
            0
        }
        return baseTodaySecs + pendingSecs + activeSecs
    }

    data class TimelineBlock(
        val startMs: Long,
        val endMs: Long,
        val color: Int
    )

    private fun getTodayFocusBlocks(context: Context): List<TimelineBlock> {
        val blocks = mutableListOf<TimelineBlock>()
        val todayStr = com.example.util.SystemTimeService.getTodayString()
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val startOfDayMs = cal.timeInMillis
        val endOfDayMs = startOfDayMs + (24 * 3600 * 1000L)

        val colors = intArrayOf(
            android.graphics.Color.parseColor("#FFCC00"), // Yellow
            android.graphics.Color.parseColor("#30B0C7"), // Cyan
            android.graphics.Color.parseColor("#FF3B30"), // Red
            android.graphics.Color.parseColor("#007AFF"), // Blue
            android.graphics.Color.parseColor("#AF52DE"), // Purple
            android.graphics.Color.parseColor("#34C759"), // Green
            android.graphics.Color.parseColor("#FF9500")  // Orange
        )

        try {
            val db = com.example.data.AppDatabase.getInstance(context)
            val records = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    db.localHistoryVaultDao().getAllHistoryDirect().filter { it.date_string == todayStr }
                } catch (e: Throwable) {
                    emptyList()
                }
            }

            records.forEachIndexed { index, record ->
                val startMs = record.start_time_ms
                val endMs = if (record.end_time_ms > record.start_time_ms) record.end_time_ms else startMs + record.total_focus_ms
                if (startMs < endOfDayMs && endMs > startOfDayMs) {
                    val color = colors[Math.abs((record.subject ?: "").hashCode()) % colors.size]
                    blocks.add(TimelineBlock(startMs, endMs, color))
                }
            }
        } catch (e: Throwable) {
            Log.e("WidgetUpdater", "Failed to fetch today history blocks", e)
        }

        // Active live session block if running right now
        val isRunningOrPaused = FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value || FocusTimerManager.isPaused.value
        if (FocusTimerManager.isFocusPhase.value && isRunningOrPaused) {
            val activeSecs = (FocusTimerManager.accumulatedSessionTimeMs.value / 1000).toInt()
            if (activeSecs > 0) {
                val now = System.currentTimeMillis()
                val startMs = maxOf(startOfDayMs, now - (activeSecs * 1000L))
                blocks.add(TimelineBlock(startMs, now, android.graphics.Color.parseColor("#FF3B30")))
            }
        }

        return blocks
    }

    private fun generateTimelineBitmap(context: Context, blocks: List<TimelineBlock>): android.graphics.Bitmap {
        val width = 720
        val height = 150
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val startOfDayMs = cal.timeInMillis
        val dayDurationMs = 24 * 3600 * 1000f

        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        // 1. Sun & Moon Icons / Emojis
        paint.textSize = 26f
        paint.textAlign = android.graphics.Paint.Align.CENTER

        val sunX = 20f + 0.5f * (width - 40f)
        canvas.drawText("☀️", sunX, 28f, paint)

        val moonX = 20f + (20f / 24f) * (width - 40f)
        canvas.drawText("🌙", moonX, 28f, paint)

        // 2. Timeline Bar Track
        val trackLeft = 20f
        val trackRight = width - 20f
        val trackTop = 42f
        val trackBottom = 82f
        val trackWidth = trackRight - trackLeft

        val trackBgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#2C2C2E")
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawRoundRect(
            android.graphics.RectF(trackLeft, trackTop, trackRight, trackBottom),
            10f, 10f, trackBgPaint
        )

        // 3. Render Session Blocks
        val blockPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
        }

        blocks.forEach { block ->
            val startFrac = ((block.startMs - startOfDayMs).toFloat() / dayDurationMs).coerceIn(0f, 1f)
            val endFrac = ((block.endMs - startOfDayMs).toFloat() / dayDurationMs).coerceIn(0f, 1f)

            var bLeft = trackLeft + startFrac * trackWidth
            var bRight = trackLeft + endFrac * trackWidth

            if (bRight - bLeft < 5f) {
                bRight = bLeft + 5f
            }

            blockPaint.color = block.color
            canvas.drawRoundRect(
                android.graphics.RectF(bLeft, trackTop, bRight, trackBottom),
                6f, 6f, blockPaint
            )
        }

        // 4. Time Axis Ticks & Labels
        val timeLabels = arrayOf("00:00", "04:00", "08:00", "12:00", "16:00", "20:00", "24:00")
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#A1A1AA")
            textSize = 19f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }

        val tickPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#48484A")
            strokeWidth = 2f
        }

        timeLabels.forEachIndexed { i, label ->
            val frac = i / 6f
            val tickX = trackLeft + frac * trackWidth

            textPaint.textAlign = when (i) {
                0 -> android.graphics.Paint.Align.LEFT
                6 -> android.graphics.Paint.Align.RIGHT
                else -> android.graphics.Paint.Align.CENTER
            }

            canvas.drawLine(tickX, trackBottom + 2f, tickX, trackBottom + 10f, tickPaint)
            canvas.drawText(label, tickX, trackBottom + 34f, textPaint)
        }

        return bitmap
    }

    private fun formatWidgetFocusTime(totalSeconds: Int): String {
        val hrs = totalSeconds / 3600
        val mins = (totalSeconds % 3600) / 60
        return when {
            hrs > 0 && mins > 0 -> "${hrs}h ${mins}m"
            hrs > 0 -> "${hrs}h"
            mins > 0 -> "${mins}m"
            else -> "0m"
        }
    }

    /**
     * Updates the Total Focus Time Native Home Screen Widget
     */
    fun updateTotalFocusTimeWidget(context: Context, isPartialUpdate: Boolean = false) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, TotalFocusTimeWidgetProvider::class.java)
        val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        if (allWidgetIds.isEmpty()) return

        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val glassStyle = prefs.getString("widget_glass_style", "black_glass") ?: "black_glass"
        val bgRes = if (glassStyle == "clear_glass") R.drawable.widget_background_clear_glass else R.drawable.widget_background_black_glass

        val totalSeconds = calculateTodayTotalFocusSeconds(context)
        val formattedTime = formatWidgetFocusTime(totalSeconds)

        val timelineBlocks = getTodayFocusBlocks(context)
        val timelineBitmap = generateTimelineBitmap(context, timelineBlocks)

        // Open App Intent
        val timerIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SHOW_TIMER_PAGE", true)
        }
        val timerPending = PendingIntent.getActivity(context, 4001, timerIntent, getPendingIntentFlags())

        for (widgetId in allWidgetIds) {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_total_focus_time).apply {
                setInt(android.R.id.background, "setBackgroundResource", bgRes)
                setTextViewText(R.id.focus_title, "Today")
                setTextViewText(R.id.focus_time_display, formattedTime)
                setImageViewBitmap(R.id.focus_timeline_canvas, timelineBitmap)

                setOnClickPendingIntent(android.R.id.background, timerPending)
            }
            appWidgetManager.updateAppWidget(widgetId, remoteViews)
        }
    }

    data class JournalPhotoItem(
        val photoUrl: String,
        val title: String,
        val text: String,
        val dateFormatted: String, // "DD/MM/YY" format e.g. "10/08/26"
        val entryId: Int
    )

    fun formatJournalDateToDdMmYy(dateStr: String, timestamp: Long): String {
        val sdfOutput = java.text.SimpleDateFormat("dd/MM/yy", java.util.Locale.US)
        if (dateStr.isNotBlank()) {
            try {
                val inputFormats = listOf(
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US),
                    java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.US),
                    java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US),
                    java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US)
                )
                for (fmt in inputFormats) {
                    try {
                        val parsed = fmt.parse(dateStr)
                        if (parsed != null) return sdfOutput.format(parsed)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
        return if (timestamp > 0L) sdfOutput.format(java.util.Date(timestamp)) else sdfOutput.format(java.util.Date())
    }

    private fun extractPhotosFromJournalEntries(entries: List<com.example.data.JournalEntry>): List<JournalPhotoItem> {
        val result = mutableListOf<JournalPhotoItem>()
        for (entry in entries) {
            val attachments = if (entry.attachmentsJson.isNotEmpty()) entry.attachmentsJson.split(";;") else emptyList()
            val formattedDate = formatJournalDateToDdMmYy(entry.dateString, entry.timestamp)
            val titleText = entry.title.ifBlank { "Journal Entry" }
            val bodyText = entry.text.ifBlank { "Journal note" }

            for (attach in attachments) {
                val trimmed = attach.trim()
                if (trimmed.isEmpty()) continue
                val lower = trimmed.lowercase()
                val isPhoto = lower.startsWith("photo:") ||
                        lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                        lower.endsWith(".png") || lower.endsWith(".webp") ||
                        lower.startsWith("file://") || lower.startsWith("content://") ||
                        (lower.startsWith("/") && (lower.contains("image") || lower.contains("photo") || lower.contains("dcim") || lower.contains("pictures")))

                if (isPhoto) {
                    val cleanUrl = if (trimmed.startsWith("photo:", ignoreCase = true)) trimmed.substring(6).trim() else trimmed
                    if (cleanUrl.isNotEmpty()) {
                        result.add(JournalPhotoItem(cleanUrl, titleText, bodyText, formattedDate, entry.id))
                    }
                }
            }
        }
        return result
    }

    private fun decodeJournalPhotoBitmap(context: Context, photoPath: String, targetWidthPx: Int, targetHeightPx: Int): android.graphics.Bitmap? {
        val trimmed = photoPath.trim()
        if (trimmed.isEmpty()) return null

        try {
            // Case 1: content:// URI
            if (trimmed.startsWith("content://")) {
                val uri = android.net.Uri.parse(trimmed)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bytes = inputStream.readBytes()
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

                    var sampleSize = 1
                    while (options.outWidth / (sampleSize * 2) >= targetWidthPx && options.outHeight / (sampleSize * 2) >= targetHeightPx) {
                        sampleSize *= 2
                    }
                    val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                    if (bmp != null) return getRoundedCornerBitmap(bmp, 36f)
                }
            }

            // Case 2: Local File Path or file:// URI
            if (trimmed.startsWith("/") || trimmed.startsWith("file://")) {
                val path = trimmed.removePrefix("file://")
                val file = java.io.File(path)
                if (file.exists() && file.length() > 0) {
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)

                    var sampleSize = 1
                    while (options.outWidth / (sampleSize * 2) >= targetWidthPx && options.outHeight / (sampleSize * 2) >= targetHeightPx) {
                        sampleSize *= 2
                    }
                    val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                    if (bmp != null) return getRoundedCornerBitmap(bmp, 36f)
                }
            }

            // Case 3: HTTP/HTTPS URL
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                val cacheFile = java.io.File(context.cacheDir, "journal_photo_widget_${Math.abs(trimmed.hashCode())}.png")
                if (cacheFile.exists() && cacheFile.length() > 0) {
                    val bmp = android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath)
                    if (bmp != null) return getRoundedCornerBitmap(bmp, 36f)
                }
                val url = java.net.URL(trimmed)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.doInput = true
                conn.connect()
                val inputStream = conn.inputStream
                val bytes = inputStream.readBytes()
                inputStream.close()
                conn.disconnect()
                cacheFile.writeBytes(bytes)
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) return getRoundedCornerBitmap(bmp, 36f)
            }

            // Case 4: Base64
            if (trimmed.startsWith("base64:") || trimmed.startsWith("data:image/") || (trimmed.length > 80 && !trimmed.contains(" "))) {
                val rawData = when {
                    trimmed.startsWith("base64:") -> trimmed.substringAfter("base64:")
                    trimmed.contains("base64,") -> trimmed.substringAfter("base64,")
                    else -> trimmed
                }
                val bytes = android.util.Base64.decode(rawData, android.util.Base64.DEFAULT)
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) return getRoundedCornerBitmap(bmp, 36f)
            }
        } catch (e: Exception) {
            Log.e("WidgetUpdater", "Failed to decode journal photo bitmap: ${e.message}")
        }
        return null
    }

    private fun getRoundedCornerBitmap(src: android.graphics.Bitmap, cornerRadiusPx: Float): android.graphics.Bitmap {
        val output = android.graphics.Bitmap.createBitmap(src.width, src.height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val rect = android.graphics.Rect(0, 0, src.width, src.height)
        val rectF = android.graphics.RectF(rect)

        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, rect, rect, paint)

        return output
    }

    /**
     * Updates the Journal Photo Shower Native Home Screen Widget
     */
    fun updatePhotoShowerWidget(context: Context, forceNext: Boolean = false) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisWidget = ComponentName(context, PhotoShowerWidgetProvider::class.java)
        val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        if (allWidgetIds.isEmpty()) return

        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val glassStyle = prefs.getString("widget_glass_style", "black_glass") ?: "black_glass"
        val bgRes = if (glassStyle == "clear_glass") R.drawable.widget_background_clear_glass else R.drawable.widget_background_black_glass

        val journalEntries = try {
            val database = com.example.data.AppDatabase.getInstance(context)
            kotlinx.coroutines.runBlocking { database.journalDao().getAllJournalEntriesDirect() }
        } catch (e: Exception) {
            emptyList<com.example.data.JournalEntry>()
        }

        val photoItems = extractPhotosFromJournalEntries(journalEntries)

        val nextIntent = Intent(context, PhotoShowerWidgetProvider::class.java).apply {
            action = "com.example.widget.ACTION_PHOTO_SHOWER_NEXT"
        }
        val nextPending = PendingIntent.getBroadcast(context, 5001, nextIntent, getPendingIntentFlags())

        for (widgetId in allWidgetIds) {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_photo_shower).apply {
                setInt(android.R.id.background, "setBackgroundResource", bgRes)
                setOnClickPendingIntent(R.id.btn_next_photo, nextPending)

                if (photoItems.isEmpty()) {
                    setViewVisibility(R.id.photo_shower_image, android.view.View.GONE)
                    setViewVisibility(R.id.photo_bottom_shadow, android.view.View.GONE)
                    setViewVisibility(R.id.photo_date_container, android.view.View.GONE)
                    setViewVisibility(R.id.photo_shower_caption_layout, android.view.View.GONE)
                    setViewVisibility(R.id.photo_shower_empty_layout, android.view.View.VISIBLE)

                    val openAppIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("SHOW_JOURNAL_PAGE", true)
                    }
                    val openAppPending = PendingIntent.getActivity(context, 5002, openAppIntent, getPendingIntentFlags())
                    setOnClickPendingIntent(android.R.id.background, openAppPending)
                } else {
                    setViewVisibility(R.id.photo_shower_empty_layout, android.view.View.GONE)
                    setViewVisibility(R.id.photo_shower_image, android.view.View.VISIBLE)
                    setViewVisibility(R.id.photo_bottom_shadow, android.view.View.VISIBLE)
                    setViewVisibility(R.id.photo_date_container, android.view.View.VISIBLE)
                    setViewVisibility(R.id.photo_shower_caption_layout, android.view.View.VISIBLE)

                    var currentIndex = prefs.getInt("journal_photo_widget_index_$widgetId", 0)
                    if (forceNext) {
                        currentIndex = (currentIndex + 1) % photoItems.size
                    } else {
                        currentIndex = currentIndex % photoItems.size
                    }
                    prefs.edit().putInt("journal_photo_widget_index_$widgetId", currentIndex).apply()

                    val currentPhoto = photoItems[currentIndex]

                    setTextViewText(R.id.photo_shower_date, currentPhoto.dateFormatted)
                    setTextViewText(R.id.photo_shower_title, currentPhoto.title)
                    setTextViewText(R.id.photo_shower_text, currentPhoto.text)

                    val bitmap = decodeJournalPhotoBitmap(context, currentPhoto.photoUrl, 600, 400)
                    if (bitmap != null) {
                        setImageViewBitmap(R.id.photo_shower_image, bitmap)
                    } else {
                        setImageViewResource(R.id.photo_shower_image, R.drawable.widget_background)
                    }

                    val openJournalIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("SHOW_JOURNAL_PAGE", true)
                        putExtra("JOURNAL_ENTRY_ID", currentPhoto.entryId)
                    }
                    val openJournalPending = PendingIntent.getActivity(context, 5000 + widgetId, openJournalIntent, getPendingIntentFlags())
                    setOnClickPendingIntent(R.id.photo_shower_image, openJournalPending)
                    setOnClickPendingIntent(R.id.photo_shower_caption_layout, openJournalPending)
                }
            }
            appWidgetManager.updateAppWidget(widgetId, remoteViews)
        }
    }
}
