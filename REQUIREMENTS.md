react-native-everything-player should be a primarly audio based cross platform react native player similar to react-native-track-player or react-native-nitro-player.
git clone react-native-nitro-player and insert the files as a base.

# Requirements

## C++ Main bridge

Use as much C++ as possible to not have to constantly rewrite features into both platforms; this will mainly be for features like SABR though.

## YouTube's SABR support

To go about this, git clone https://github.com/LuanRT/googlevideo and translate the library into C++ then reference functions like createSABRStream to extract the seperate video and audio streams for playback. Note heavily the protobuf layer that you'll be required to use `protoc` to generate the valid proto header files for c++.

> SABR streams can come in all different types of containers and sizes; be careful to correctly fix stuff like opus on iOS. and webm video on iOS.

Alongside playback, there should also be downloading SABR audio and SABR video seperately. Don't worry about API to stitch them together.

Some example SABR required paramters could be things like:

```ts
export interface SabrDownloadParams {
  sabrServerUrl: string
  sabrUstreamerConfig: string
  sabrFormats?: YouTubeDL.SabrFormat[]
  poToken?: string
  placeholder_po_token?: string
  clientInfo?: YouTubeDL.SabrClientInfo
  cookie?: string
  on_refresh_po_token?: (reason: SabrTokenCallbackReason) => Promise<string>
  on_reload_player_response?: (
    context: any
  ) => Promise<{ sabrServerUrl: string; sabrUstreamerConfig: string } | null>
  preferOpus?: boolean
}
```

on iOS it should always prefer something like webm and opus over m4a. SABR m4a never works on iOS so ignore it.

This doesn't have to be exact; but some code like this should be able to download a SABR audio stream

```ts
export const mobile_sabr_downloader: SabrDownloader = {
  download_sabr: async (params, output_path, on_progress) => {
    let unsub: { remove: () => void } | undefined
    let unsub_reload: { remove: () => void } | undefined
    let unsub_refresh: { remove: () => void } | undefined
    if (on_progress) {
      // SabrDownloadProgress = 'sabr-download-progress' (RNTPvE extension)
      unsub = TrackPlayer.addEventListener(
        Event.SabrDownloadProgress,
        (event: { outputPath: string; progress: number }) => {
          if (event.outputPath === output_path) on_progress(event.progress)
        }
      )
    }
    if (params.on_reload_player_response) {
      unsub_reload = TrackPlayer.addEventListener(
        Event.SabrReloadPlayerResponse,
        async (event: { outputPath: string; token: string | null }) => {
          if (event.outputPath !== output_path) return
          try {
            const result = await params.on_reload_player_response!(event.token)
            if (result) {
              await TrackPlayer.updateSabrStream(
                output_path,
                result.sabrServerUrl,
                result.sabrUstreamerConfig
              )
            }
          } catch {
            // ignore errors in reload handler — download will time out naturally
          }
        }
      )
    }
    if (params.on_refresh_po_token) {
      let token_refresh_in_flight = false
      unsub_refresh = TrackPlayer.addEventListener(
        Event.SabrRefreshPoToken,
        async (event: {
          outputPath: string
          reason: SabrTokenCallbackReason
        }) => {
          if (event.outputPath !== output_path) return
          if (token_refresh_in_flight) return
          token_refresh_in_flight = true
          try {
            const token = await params.on_refresh_po_token!(event.reason)
            await TrackPlayer.updateSabrPoToken(output_path, token)
          } catch {
            // ignore — stream will fail naturally if token can't be refreshed
          } finally {
            token_refresh_in_flight = false
          }
        }
      )
    }
    try {
      await TrackPlayer.downloadSabr(
        params as Parameters<typeof TrackPlayer.downloadSabr>[0],
        output_path
      )
    } finally {
      unsub?.remove()
      unsub_reload?.remove()
      unsub_refresh?.remove()
    }
  },
}
```

## Default Formats

Like any normal player, it should support stuff like: mp4, m4a, aac, webm, opus, HLS, dash, etc...
All of these should support both local files and streaming.
Some of these file formats iOS doesn't natively support; add in the support

## DRM Support

Implement Widevine DRM support

## Crossfading and Equalizer Support

Equalizer support may be baked in; but ensure that crossfading works just as well

## Video support

Have a native video component, a singular instance that is linked to the current playing track
If the track is audioonly simply show the artwork on the component;
If the track is normal video show the video on the component and let the trackplayer handle the audio.
If the track is SABR, then begin requesting the SABR video stream; don't otherwise to preserve bandwidth
The component should be able to customize the size and other features.

## Caching

`react-native-nitro-player` may support caching but double check it works for all streams, including SABR.

# Additional Notes

DO NOT NOT NOT use oldarch as a reference; it fails badly

Ask any questions as needed.
