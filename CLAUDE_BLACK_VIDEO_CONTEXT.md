# Black Video Debug Handoff (for Claude)

## Problem statement
In `react-native-everything-player`, the example app still shows a black video surface on iOS/Android, especially in SABR mode.  
Hermes/devtools setup has also been inconsistent, but the main fix target is black video rendering.

## Runtime/setup context
- Example app stack:
  - Expo `~56.0.0-canary-20260414-e3dbafd`
  - React Native `0.85.1`
- Hermes is configured in:
  - `example/app.json` (`expo.jsEngine = "hermes"`)
  - `example/android/gradle.properties` (`hermesEnabled=true`)
  - `example/ios/Podfile.properties.json` (`"expo.jsEngine": "hermes"`)
- Start command:
  - `example/package.json`:
    - `EXPO_UNSTABLE_HEADLESS=1 expo start --dev-client --localhost --clear`
- Example UI now shows engine label:
  - `example/App.tsx` (`Engine: Hermes` vs `JSC/Other`)

## JS example flow (SABR + fallback)
### `example/App.tsx`
- Resolves SABR via `YouTubeDL.resolve_sabr_url(EXAMPLE_YOUTUBE_VIDEO_ID)`.
- If compatible SABR video formats are found, creates SABR track:
  - `isSabr`, `sabrServerUrl`, `sabrUstreamerConfig`, `sabrFormats`, `poToken`, `clientInfo`, `cookie`, `duration`.
- If not, falls back to progressive MP4:
  - `https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4`
- Compatibility filter currently accepts video mime containing `mp4|avc|h264`.

## Android native pipeline
### 1) Track → separate SABR audio/video sessions
#### `android/src/main/java/com/everythingplayer/model/Track.kt`
- `toAudioItem()` builds separate IDs:
  - `sabrSessionId` (audio)
  - `sabrVideoSessionId` (video)
- Also builds separate URLs:
  - `audioUrl` (`sabr://...`)
  - `videoUrl` (`sabr://...`)
- `resolvePreferredSabrVideoMimeType()` prefers:
  - `avc/h264` > `mp4` > `webm`

### 2) Media source composition (2-stream merge)
#### `android/.../MediaFactory.kt` (`createSabrSource`)
- Creates audio SABR session/source first.
- If video metadata exists, creates separate video SABR session/source.
- Returns:
  - `MergingMediaSource(audioSource, videoSource)`

### 3) Surface + view toggling
#### `android/.../HybridEverythingPlayer.kt`
- `videoViewDidAttach(...)`:
  - sets ExoPlayer surface
  - toggles `showVideoSurface()`/`clearVideo()`
  - sets thumbnail
  - calls `enableCurrentSabrVideoPlayback()`
- On `PLAYBACK_ACTIVE_TRACK_CHANGED`, visibility is toggled by `trackPayloadHasVideo(...)`.

#### `android/.../HybridVideoView.kt`
- Uses `SurfaceView` + thumbnail `ImageView`.
- `showVideoSurface()` hides thumbnail, shows surface.
- `clearVideo()` shows thumbnail, hides surface.

## iOS native pipeline
### 1) Attach/detach and SABR wiring
#### `ios/EverythingPlayer.swift` (Video View extension)
- `videoViewDidAttach(...)`:
  - `player.avPlayerWrapper.videoEnabled = true`
  - wires `onSabrVideoStreamReady` -> `connectSabrVideoStream(...)`
  - calls `ensureSabrVideoStreamAttachedForCurrentItem()`
  - connects current AVPlayer for non-SABR path
  - applies artwork thumbnail
- `videoViewDidDetach(...)` disables video + clears view.

### 2) Rendering layer
#### `ios/HybridVideoView.swift`
- `VideoUIView` manages `AVPlayerLayer` + thumbnail.
- `connectSabrVideoStream(...)` creates `AVPlayerItem` from custom SABR resource loader, then sets `AVPlayerLayer`.

### 3) SABR selection behavior
#### `ios/SwiftAudioEx/AVPlayerWrapper/AVPlayerWrapper.swift`
- When `videoEnabled == true`:
  - `enabled_track_types = video_and_audio`
  - `prefer_mp4 = true`
- `onVideoStreamReady` is emitted only when video-enabled path is active.
- `ensureSabrVideoStreamAttachedForCurrentItem()` can restart SABR when video is attached late.

#### `ios/SwiftAudioEx/SABR/SabrStream.swift`
- `select_formats(...)` requires valid audio+video in `video_and_audio` mode.
- Throws `no_suitable_formats` if pair cannot be selected.

---

## googlevideo reference: explicit two-stream model

From upstream `LuanRT/googlevideo` examples:

### `examples/downloader/main.ts`
Uses separate streams and sinks in parallel:

```ts
const { videoStream, audioStream, selectedFormats } = streamResults;

await Promise.all([
  videoStream.pipeTo(createStreamSink(selectedFormats.videoFormat, videoOutputStream.stream, videoBar)),
  audioStream.pipeTo(createStreamSink(selectedFormats.audioFormat, audioOutputStream.stream, audioBar))
]);
```

### `examples/downloader/utils/sabr-stream-factory.ts`
`createSabrStream(...)` returns:
- `videoStream`
- `audioStream`
- `selectedFormats.videoFormat`
- `selectedFormats.audioFormat`

This confirms the intended model: **separate audio/video SABR streams selected independently and then synchronized by the player**.

### `examples/sabr-shaka-example/src/main.ts`
- Uses `SabrStreamingAdapter`
- calls `setServerAbrFormats(videoInfo.streaming_data.adaptive_formats.map(buildSabrFormat))`
- loads DASH manifest and lets adapter handle SABR stream wiring.

---

## Most likely black-video failure points
1. **Codec/container mismatch** between selected SABR video format and platform decoder.
2. **View/surface timing race** (surface/layer ready vs first decodable frame availability).
3. **Audio-only first, video enabled later** path not consistently reattaching stream/session.
4. **Metadata loss** in bridge conversion (`sabrFormats` details not always preserved correctly).
5. **No first-frame gating** for UI: thumbnail may hide before confirmed frame render.

## Suggested fix direction
Implement a deterministic render-readiness flow:
1. Keep thumbnail visible on attach.
2. Attach player/stream.
3. Hide thumbnail only after first video frame callback.
4. If timeout/no frame, keep thumbnail and emit a structured diagnostic reason.
5. Tighten per-platform SABR video format allowlist + fallback order.
