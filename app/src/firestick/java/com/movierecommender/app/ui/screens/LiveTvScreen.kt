package com.movierecommender.app.ui.screens.firestick

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val M3U_PLAYLIST_URL = "https://tvpass.org/playlist/m3u"
private const val EPG_URL = "https://tvpass.org/epg.xml"

data class TvChannel(
    val name: String,
    val streamUrl: String,
    val group: String = "",
    val tvgId: String = ""
)

data class EpgProgram(
    val title: String,
    val subtitle: String = "",
    val description: String = "",
    val startTime: Long = 0L,
    val endTime: Long = 0L
)

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@OptIn(UnstableApi::class)
@Composable
fun LiveTvScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var channels by remember { mutableStateOf<List<TvChannel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var selectedCategory by remember { mutableStateOf("All") }
    var isPlaying by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(true) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    // EPG: map from channel tvgId -> list of programs
    var epgData by remember { mutableStateOf<Map<String, List<EpgProgram>>>(emptyMap()) }

    // Debounce job for channel surfing in fullscreen mode
    var channelSwitchJob by remember { mutableStateOf<Job?>(null) }

    val listState = rememberLazyListState()
    val categoryListState = rememberLazyListState()
    val selectedItemFocusRequester = remember { FocusRequester() }
    val playerOverlayFocusRequester = remember { FocusRequester() }
    val guideCategories = remember(channels) { buildGuideCategories(channels) }
    val visibleChannels = remember(channels, selectedCategory) {
        filterGuideChannels(channels, selectedCategory)
    }

    // Fetch and parse the M3U playlist + EPG
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) { fetchAndParseM3u(M3U_PLAYLIST_URL) }
                channels = parsed
                isLoading = false
            } catch (e: Exception) {
                errorMessage = "Failed to load channels: ${e.message}"
                isLoading = false
            }
            // Fetch EPG in background (non-blocking — guide works without it)
            try {
                val epg = withContext(Dispatchers.IO) { fetchAndParseEpg(EPG_URL) }
                epgData = epg
            } catch (e: Exception) {
                android.util.Log.w("LiveTV", "EPG fetch failed: ${e.message}")
            }
        }
    }

    // Initialize ExoPlayer — force highest quality regardless of bandwidth
    DisposableEffect(Unit) {
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setForceHighestSupportedBitrate(true)
            )
        }
        val player = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("LiveTV", "Playback error: ${error.message}", error)
                errorMessage = "Playback error: ${error.message}"
            }
        })
        exoPlayer = player
        onDispose {
            player.release()
            exoPlayer = null
        }
    }

    // Play channel immediately (used from guide OK press)
    fun playChannel(channel: TvChannel) {
        errorMessage = null
        exoPlayer?.let { player ->
            player.stop()
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0")
                .setAllowCrossProtocolRedirects(true)
            val mediaItem = MediaItem.Builder()
                .setUri(channel.streamUrl)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()
            val hlsSource = HlsMediaSource.Factory(dataSourceFactory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(mediaItem)
            player.setMediaSource(hlsSource)
            player.prepare()
            player.playWhenReady = true
            isPlaying = true
        }
    }

    // Debounced play — for rapid channel surfing in fullscreen mode
    fun playChannelDebounced(channel: TvChannel) {
        channelSwitchJob?.cancel()
        channelSwitchJob = scope.launch {
            delay(400)
            playChannel(channel)
        }
    }

    BackHandler {
        when {
            !showGuide && isPlaying -> {
                // Fullscreen playback → show the guide
                showGuide = true
            }
            showGuide && isPlaying -> {
                // Guide open over playing video → exit Live TV screen
                // to avoid trapping users in a fullscreen/guide back-loop.
                exoPlayer?.stop()
                isPlaying = false
                onBackClick()
            }
            else -> {
                // Guide open, nothing playing → exit Live TV
                exoPlayer?.stop()
                onBackClick()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video player (always fills the screen)
        exoPlayer?.let { player ->
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    PlayerView(it).apply {
                        this.player = player
                        useController = false
                        keepScreenOn = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                }
            )
        }

        // Loading state
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading channels...", color = Color.White, fontSize = 18.sp)
                }
            }
        }

        // Error state
        errorMessage?.let { msg ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(msg, color = Color.Red, fontSize = 18.sp)
            }
        }

        // Channel guide overlay
        if (showGuide && !isLoading && errorMessage == null && channels.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isPlaying) 0.85f else 1f))
            ) {
                // Channel list - left side
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.45f)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Live TV",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "${visibleChannels.size} channels${if (selectedCategory != "All") " · $selectedCategory" else ""}",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Left/Right changes filter",
                        color = Color(0xFF00BCD4),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyRow(
                        state = categoryListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        contentPadding = PaddingValues(end = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(guideCategories) { category ->
                            GuideCategoryChip(
                                label = category,
                                isSelected = category == selectedCategory
                            )
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .onPreviewKeyEvent { keyEvent ->
                                val code = keyEvent.key.nativeKeyCode
                                if (keyEvent.type == KeyEventType.KeyUp) {
                                    return@onPreviewKeyEvent when (code) {
                                        KeyEvent.KEYCODE_DPAD_UP,
                                        KeyEvent.KEYCODE_DPAD_DOWN,
                                        KeyEvent.KEYCODE_DPAD_LEFT,
                                        KeyEvent.KEYCODE_DPAD_RIGHT,
                                        KeyEvent.KEYCODE_DPAD_CENTER,
                                        KeyEvent.KEYCODE_ENTER -> true
                                        else -> false
                                    }
                                }
                                // Handle UP/DOWN manually so focus never escapes the
                                // LazyColumn (off-screen items are not composed, so
                                // normal focus traversal can't find them and exits).
                                when (code) {
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        if (keyEvent.type == KeyEventType.KeyDown &&
                                            selectedIndex > 0
                                        ) {
                                            selectedIndex--
                                            scope.launch {
                                                listState.scrollToItem(selectedIndex)
                                            }
                                        }
                                        true // always consume — never let UP escape
                                    }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        if (keyEvent.type == KeyEventType.KeyDown &&
                                            selectedIndex < visibleChannels.size - 1
                                        ) {
                                            selectedIndex++
                                            scope.launch {
                                                listState.scrollToItem(selectedIndex)
                                            }
                                        }
                                        true // always consume — never let DOWN escape
                                    }
                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                    KeyEvent.KEYCODE_ENTER -> {
                                        val channel = visibleChannels.getOrNull(selectedIndex)
                                        if (keyEvent.type == KeyEventType.KeyDown && channel != null) {
                                            playChannel(channel)
                                            showGuide = false
                                        }
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            val categoryIndex = guideCategories.indexOf(selectedCategory)
                                                .coerceAtLeast(0)
                                            if (categoryIndex > 0) {
                                                selectedCategory = guideCategories[categoryIndex - 1]
                                                selectedIndex = 0
                                            }
                                        }
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            val categoryIndex = guideCategories.indexOf(selectedCategory)
                                                .coerceAtLeast(0)
                                            if (categoryIndex < guideCategories.lastIndex) {
                                                selectedCategory = guideCategories[categoryIndex + 1]
                                                selectedIndex = 0
                                            }
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            }
                    ) {
                        itemsIndexed(visibleChannels) { index, channel ->
                            val nowPlaying = remember(epgData, channel.tvgId) {
                                getNowPlaying(epgData, channel.tvgId)
                            }
                            ChannelItem(
                                channel = channel,
                                isSelected = index == selectedIndex,
                                nowPlayingTitle = nowPlaying?.title,
                                focusRequester = if (index == selectedIndex) selectedItemFocusRequester else null,
                                onFocused = { selectedIndex = index }
                            )
                        }
                    }
                }

                // Preview info - right side
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.55f)
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (selectedIndex in visibleChannels.indices) {
                        val ch = visibleChannels[selectedIndex]
                        Text(
                            text = ch.name,
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (ch.group.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = ch.group,
                                color = Color(0xFF00BCD4),
                                fontSize = 18.sp
                            )
                        }

                        // EPG: Now Playing + Up Next
                        val programs = epgData[ch.tvgId]
                        if (!programs.isNullOrEmpty()) {
                            val now = System.currentTimeMillis()
                            val nowProg = programs.firstOrNull { now in it.startTime until it.endTime }
                            val upNext = programs.firstOrNull { it.startTime >= (nowProg?.endTime ?: now) }

                            if (nowProg != null) {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "NOW PLAYING",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = nowProg.title,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (nowProg.subtitle.isNotBlank()) {
                                    Text(
                                        text = nowProg.subtitle,
                                        color = Color.LightGray,
                                        fontSize = 15.sp
                                    )
                                }
                                Text(
                                    text = formatTimeRange(nowProg.startTime, nowProg.endTime),
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )
                                if (nowProg.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = nowProg.description,
                                        color = Color.Gray,
                                        fontSize = 14.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (upNext != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "UP NEXT",
                                    color = Color(0xFFFFA726),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = upNext.title,
                                    color = Color.White,
                                    fontSize = 17.sp
                                )
                                Text(
                                    text = formatTimeRange(upNext.startTime, upNext.endTime),
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Press OK to watch",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // Keep actual Compose focus aligned with the highlighted row.
            // Without selectedIndex in the key set, DPAD navigation updates the
            // highlight but center/enter still activates the previously focused row.
            LaunchedEffect(visibleChannels, showGuide, selectedIndex) {
                if (showGuide && visibleChannels.isNotEmpty()) {
                    val boundedIndex = selectedIndex.coerceIn(0, visibleChannels.size - 1)
                    if (boundedIndex != selectedIndex) {
                        selectedIndex = boundedIndex
                    }
                    listState.scrollToItem(boundedIndex)
                    selectedItemFocusRequester.requestFocus()
                }
            }

            LaunchedEffect(guideCategories, selectedCategory, showGuide) {
                if (showGuide && guideCategories.isNotEmpty()) {
                    val categoryIndex = guideCategories.indexOf(selectedCategory).coerceAtLeast(0)
                    categoryListState.animateScrollToItem(categoryIndex)
                }
            }
        }

        // Player overlay — captures DPAD when guide is hidden
        if (isPlaying && !showGuide) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(playerOverlayFocusRequester)
                    .onPreviewKeyEvent { keyEvent ->
                        val code = keyEvent.key.nativeKeyCode
                        // Consume both KeyDown and KeyUp for navigation keys
                        // to prevent events leaking to other composables
                        if (keyEvent.type == KeyEventType.KeyUp) {
                            return@onPreviewKeyEvent when (code) {
                                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                                KeyEvent.KEYCODE_BACK -> true
                                else -> false
                            }
                        }
                        if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (code) {
                            // Back in fullscreen → reopen channel guide
                            KeyEvent.KEYCODE_BACK -> {
                                showGuide = true
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                showGuide = true
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (selectedIndex > 0) {
                                    selectedIndex--
                                    visibleChannels.getOrNull(selectedIndex)?.let { playChannelDebounced(it) }
                                }
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (selectedIndex < visibleChannels.size - 1) {
                                    selectedIndex++
                                    visibleChannels.getOrNull(selectedIndex)?.let { playChannelDebounced(it) }
                                }
                                true
                            }
                            // Consume left/right to prevent focus escaping
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> true
                            else -> false
                        }
                    }
                    .focusable()
            )

            // Grab focus when guide closes
            LaunchedEffect(Unit) {
                playerOverlayFocusRequester.requestFocus()
            }
        }
    }
}

@Composable
private fun ChannelItem(
    channel: TvChannel,
    isSelected: Boolean,
    nowPlayingTitle: String? = null,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Update selectedIndex when this item gains focus (DPAD scroll)
    LaunchedEffect(isFocused) {
        if (isFocused) onFocused()
    }

    val bgColor = when {
        isFocused -> Color(0xFF00BCD4)
        isSelected -> Color(0xFF1E3A5F)
        else -> Color.Transparent
    }
    val borderColor = if (isFocused) Color.White else Color.Transparent
    val textColor = if (isFocused) Color.Black else Color.White

    val modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 2.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(bgColor)
        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .focusable(interactionSource = interactionSource)

    val finalModifier = if (focusRequester != null) {
        modifier.focusRequester(focusRequester)
    } else {
        modifier
    }

    Row(
        modifier = finalModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                color = textColor,
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!nowPlayingTitle.isNullOrBlank()) {
                Text(
                    text = nowPlayingTitle,
                    color = if (isFocused) Color.Black.copy(alpha = 0.7f) else Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Fetches and parses an M3U playlist into a list of TvChannel objects.
 * Must be called on a background thread.
 */
private fun fetchAndParseM3u(url: String): List<TvChannel> {
    val channels = mutableListOf<TvChannel>()
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 15000
    connection.readTimeout = 15000

    try {
        val reader = BufferedReader(InputStreamReader(connection.inputStream))
        var line: String?
        var currentName = ""
        var currentGroup = ""
        var currentTvgId = ""

        while (reader.readLine().also { line = it } != null) {
            val l = line ?: continue

            if (l.startsWith("#EXTINF:")) {
                // Parse channel info from #EXTINF line
                // Format: #EXTINF:-1 tvg-id="..." tvg-name="..." group-title="...",Display Name
                currentTvgId = extractAttribute(l, "tvg-id")
                currentGroup = extractAttribute(l, "group-title")

                // Display name is after the last comma
                val commaIdx = l.lastIndexOf(',')
                currentName = if (commaIdx != -1) l.substring(commaIdx + 1).trim() else ""
            } else if (l.isNotBlank() && !l.startsWith("#")) {
                // This is a stream URL
                if (currentName.isNotBlank()) {
                    channels.add(
                        TvChannel(
                            name = currentName,
                            streamUrl = l.trim(),
                            group = currentGroup,
                            tvgId = currentTvgId
                        )
                    )
                }
                currentName = ""
                currentGroup = ""
                currentTvgId = ""
            }
        }
        reader.close()
    } finally {
        connection.disconnect()
    }

    return channels
}

private fun extractAttribute(line: String, attr: String): String {
    val pattern = """$attr="([^"]*)"""".toRegex()
    return pattern.find(line)?.groupValues?.getOrNull(1) ?: ""
}
/**
 * Fetches and parses XMLTV EPG data.
 * Returns a map: channelId -> list of EpgProgram sorted by start time.
 */
private fun fetchAndParseEpg(url: String): Map<String, List<EpgProgram>> {
    val programs = mutableMapOf<String, MutableList<EpgProgram>>()
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 20000
    connection.readTimeout = 30000
    connection.setRequestProperty("User-Agent", "Mozilla/5.0")

    try {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(connection.inputStream, "UTF-8")

        var eventType = parser.eventType
        var inProgramme = false
        var channelId = ""
        var startTime = 0L
        var endTime = 0L
        var title = ""
        var subtitle = ""
        var description = ""
        var currentTag = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "programme" -> {
                            inProgramme = true
                            channelId = parser.getAttributeValue(null, "channel") ?: ""
                            startTime = parseEpgTime(parser.getAttributeValue(null, "start") ?: "")
                            endTime = parseEpgTime(parser.getAttributeValue(null, "stop") ?: "")
                            title = ""
                            subtitle = ""
                            description = ""
                        }
                        "title", "sub-title", "desc" -> {
                            if (inProgramme) currentTag = parser.name
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inProgramme) {
                        when (currentTag) {
                            "title" -> title = parser.text?.trim() ?: ""
                            "sub-title" -> subtitle = parser.text?.trim() ?: ""
                            "desc" -> description = parser.text?.trim() ?: ""
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "programme" && inProgramme) {
                        if (channelId.isNotBlank() && title.isNotBlank()) {
                            programs.getOrPut(channelId) { mutableListOf() }.add(
                                EpgProgram(
                                    title = title,
                                    subtitle = subtitle,
                                    description = description,
                                    startTime = startTime,
                                    endTime = endTime
                                )
                            )
                        }
                        inProgramme = false
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }
    } finally {
        connection.disconnect()
    }

    // Sort each channel's programs by start time
    return programs.mapValues { (_, progs) -> progs.sortedBy { it.startTime } }
}

private fun parseEpgTime(timeStr: String): Long {
    if (timeStr.isBlank()) return 0L
    return try {
        val sdf = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        sdf.parse(timeStr)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
}

private fun getNowPlaying(epgData: Map<String, List<EpgProgram>>, tvgId: String): EpgProgram? {
    if (tvgId.isBlank()) return null
    val programs = epgData[tvgId] ?: return null
    val now = System.currentTimeMillis()
    return programs.firstOrNull { now in it.startTime until it.endTime }
}

private fun buildGuideCategories(channels: List<TvChannel>): List<String> {
    val preferred = listOf(
        "All",
        "Sports",
        "NFL",
        "NBA",
        "MLB",
        "NHL",
        "NCAA",
        "Soccer",
        "PPV",
        "News",
        "Movies",
        "Kids"
    )
    val dynamicGroups = channels
        .map { it.group.trim() }
        .filter { it.isNotBlank() && !it.equals("Live", ignoreCase = true) }
        .distinct()
        .sorted()

    return buildList {
        add("All")
        preferred.drop(1)
            .filter { category -> channels.any { matchesGuideCategory(it, category) } }
            .forEach(::add)
        dynamicGroups
            .filterNot { group -> any { it.equals(group, ignoreCase = true) } }
            .forEach(::add)
    }
}

private fun filterGuideChannels(channels: List<TvChannel>, category: String): List<TvChannel> {
    if (category.equals("All", ignoreCase = true)) return channels
    return channels.filter { matchesGuideCategory(it, category) }
}

private fun matchesGuideCategory(channel: TvChannel, category: String): Boolean {
    if (category.equals("All", ignoreCase = true)) return true
    if (channel.group.equals(category, ignoreCase = true)) return true

    val haystack = buildString {
        append(channel.name.uppercase(Locale.US))
        append(' ')
        append(channel.group.uppercase(Locale.US))
    }

    val keywords = when (category.uppercase(Locale.US)) {
        "SPORTS" -> listOf(
            "ESPN", "FOX SPORTS", "FS1", "FS2", "CBS SPORTS", "NBC SPORTS",
            "NFL", "NBA", "MLB", "NHL", "ACC NETWORK", "SEC NETWORK",
            "BIG TEN NETWORK", "PAC-12", "GOLF", "TENNIS", "BEIN", "TUDN",
            "UNIVERSO", "MOTORTREND", "SPORTS"
        )
        "NFL" -> listOf("NFL", "REDZONE")
        "NBA" -> listOf("NBA TV", "NBA")
        "MLB" -> listOf("MLB")
        "NHL" -> listOf("NHL")
        "NCAA" -> listOf("ACC NETWORK", "SEC NETWORK", "BIG TEN NETWORK", "PAC-12", "ESPNU")
        "SOCCER" -> listOf("BEIN", "GOLTV", "TUDN", "UNIVERSO", "SOCCER")
        "PPV" -> listOf("PPV")
        "NEWS" -> listOf(
            "CNN", "FOX NEWS", "MSNBC", "CNBC", "C-SPAN", "NEWSMAX",
            "NEWSNATION", "BBC NEWS", "BLOOMBERG", "HLN"
        )
        "MOVIES" -> listOf(
            "HBO", "SHOWTIME", "STARZ", "CINEMAX", "TCM", "FXM",
            "HALLMARK MOVIES", "LMN"
        )
        "KIDS" -> listOf(
            "DISNEY", "NICKELODEON", "NICK JR", "CARTOON NETWORK",
            "BOOMERANG", "UNIVERSAL KIDS", "DISCOVERY FAMILY"
        )
        else -> return false
    }

    return keywords.any(haystack::contains)
}

@Composable
private fun GuideCategoryChip(
    label: String,
    isSelected: Boolean
) {
    Surface(
        color = if (isSelected) Color(0xFF00BCD4) else Color(0xFF1B1B1B),
        contentColor = if (isSelected) Color.Black else Color.White,
        shape = RoundedCornerShape(999.dp),
        tonalElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

private fun formatTimeRange(startMs: Long, endMs: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return "${sdf.format(Date(startMs))} - ${sdf.format(Date(endMs))}"
}