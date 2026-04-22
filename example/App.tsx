import '@origin/youtube_dl/ytdl_polyfill'

import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native'
import {
  PlayerQueue,
  TrackPlayer,
  VideoSurfaceView,
  useOnChangeTrack,
  useOnPlaybackProgressChange,
  useOnPlaybackStateChange,
} from 'react-native-everything-player'
import type { TrackItem } from 'react-native-everything-player'
import { YouTubeDL } from '@origin/youtube_dl/index'
import { load_native_fs } from '@native/fs/fs'
import { load_native_potoken } from '@native/potoken/potoken'
import nodejs from 'nodejs-mobile-react-native'

const DEMO_PLAYLIST_NAME = 'Demo Playlist'
const EXAMPLE_YOUTUBE_VIDEO_ID = 'wf4kRfGzflo'
const DEMO_TRACKS: TrackItem[] = [
  // {
  //   id: 'bbb-video',
  //   title: 'Big Buck Bunny',
  //   artist: 'Sample',
  //   album: 'Demo',
  //   duration: 596,
  //   url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
  //   artwork: 'https://i.ytimg.com/vi/jIhNe1ox1ls/maxresdefault.jpg',
  // },
  {
    id: 'aaa-video',
    title: 'Sample AVudio',
    artist: 'Sample',
    album: 'Demo',
    duration: 2333,
    url: 'https://us.mirror.ionos.com/projects/media.ccc.de/congress/2019/h264-hd/36c3-10592-eng-deu-pol-Fairtronics_hd.mp4',
    artwork: 'https://i.ytimg.com/vi/jIhNe1ox1ls/maxresdefault.jpg',
  },
  {
    id: 'bbb-audio',
    title: 'Sample Audio',
    artist: 'Sample',
    album: 'Demo',
    duration: 180,
    url: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3',
    artwork: 'https://i.ytimg.com/vi/jIhNe1ox1ls/maxresdefault.jpg',
  },
]

const formatTime = (value: number) => {
  const seconds = Math.max(0, Math.floor(value))
  const minutes = Math.floor(seconds / 60)
  return `${minutes}:${(seconds % 60).toString().padStart(2, '0')}`
}

export default function App() {
  const [ready, setReady] = useState(false)
  const [widevineSupported, setWidevineSupported] = useState(false)
  const [crossfadeSeconds, setCrossfadeSeconds] = useState(0)
  const [statusMessage, setStatusMessage] = useState('Initializing...')

  const { track: activeTrack } = useOnChangeTrack()
  const { state } = useOnPlaybackStateChange()
  const { position, totalDuration } = useOnPlaybackProgressChange()

  useEffect(() => {
    const bootstrap = async () => {
      if (Platform.OS !== 'web') {
        try {
          nodejs?.start?.('main.js')
          nodejs?.channel?.addListener?.('message', (msg: unknown) => {
            console.log(`[nodejs] ${String(msg)}`)
          })
        } catch (e) {
          console.warn('Failed to start nodejs-mobile:', e)
        }
      }
      setStatusMessage('Configuring player...')
      await TrackPlayer.configure({
        showInNotification: true,
        lookaheadCount: 3,
      })

      const existing = PlayerQueue.getAllPlaylists().find(
        (playlist) => playlist.name === DEMO_PLAYLIST_NAME
      )

      const playlistId =
        existing?.id ??
        (await PlayerQueue.createPlaylist(
          DEMO_PLAYLIST_NAME,
          'Library Example'
        ))

      let sabrTrack: TrackItem | null = null
      if (Platform.OS === 'android') {
        try {
          await load_native_fs()
          await load_native_potoken()
          const sabrParams =
            YouTubeDL?.resolve_sabr_url != null
              ? await YouTubeDL.resolve_sabr_url(EXAMPLE_YOUTUBE_VIDEO_ID)
              : null
          if (
            sabrParams != null &&
            !('error' in sabrParams) &&
            typeof sabrParams.url === 'string'
          ) {
            sabrTrack = {
              ...DEMO_TRACKS[0],
              id: 'youtube-sabr',
              title: 'YouTube SABR (Example)',
              artist: 'YouTube',
              url: sabrParams.url,
              extraPayload: {
                isSabr: true,
                sabrServerUrl: sabrParams.sabrServerUrl,
                sabrUstreamerConfig: sabrParams.sabrUstreamerConfig,
                sabrFormats: sabrParams.sabrFormats,
                poToken: sabrParams.poToken,
                clientInfo: sabrParams.clientInfo,
                cookie: sabrParams.cookie,
              } as any,
            }
            setStatusMessage('Loaded SABR URL and added SABR track.')
          } else {
            setStatusMessage('SABR resolve unavailable, using fallback tracks.')
          }
        } catch (e) {
          setStatusMessage(
            `SABR init failed; using fallback track. ${String(e)}`
          )
        }
      }

      const bootstrapTracks = sabrTrack
        ? [sabrTrack, DEMO_TRACKS[1]]
        : DEMO_TRACKS
      const existingPlaylist = PlayerQueue.getPlaylist(playlistId)
      const existingTrackIds = new Set(
        existingPlaylist?.tracks.map((track) => track.id) ?? []
      )
      const missingTracks = bootstrapTracks.filter(
        (track) => !existingTrackIds.has(track.id)
      )
      if (missingTracks.length > 0) {
        await PlayerQueue.addTracksToPlaylist(playlistId, missingTracks)
      }
      await PlayerQueue.loadPlaylist(playlistId)
      await TrackPlayer.setCrossfadeDuration(2)

      setCrossfadeSeconds(await TrackPlayer.getCrossfadeDuration())
      setWidevineSupported(TrackPlayer.isWidevineSupported())
      setReady(true)
    }

    void bootstrap()
  }, [])

  const playbackStatusText = useMemo(() => {
    if (!ready) return 'Loading...'
    return `${statusMessage}  |  State: ${state ?? 'unknown'}`
  }, [ready, state, statusMessage])

  return (
    <View style={styles.safeArea}>
      <View style={styles.videoContainer}>
        <VideoSurfaceView
          style={styles.videoSurface}
          active
          contentMode="contain"
          artworkUri={activeTrack?.artwork}
        />
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.heading}>
          react-native-everything-player example
        </Text>
        <Text style={styles.subText}>{playbackStatusText}</Text>
        <Text style={styles.subText}>
          {formatTime(position)} / {formatTime(totalDuration)}
        </Text>
        <Text style={styles.subText}>
          Widevine supported: {widevineSupported ? 'yes (Android)' : 'no (iOS)'}
        </Text>
        <Text style={styles.subText}>Crossfade: {crossfadeSeconds}s</Text>

        <View style={styles.controlsRow}>
          <Button title="Play" onPress={() => void TrackPlayer.play()} />
          <Button title="Pause" onPress={() => void TrackPlayer.pause()} />
        </View>

        <View style={styles.controlsRow}>
          <Button
            title="Prev"
            onPress={() => void TrackPlayer.skipToPrevious()}
          />
          <Button title="Next" onPress={() => void TrackPlayer.skipToNext()} />
          <Button
            title="+15s"
            onPress={() => void TrackPlayer.seek(position + 15)}
          />
        </View>
      </ScrollView>
    </View>
  )
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#0f172a',
  },
  videoContainer: {
    height: 240,
    backgroundColor: '#000',
  },
  videoSurface: {
    flex: 1,
  },
  content: {
    padding: 16,
    gap: 8,
  },
  heading: {
    color: '#e2e8f0',
    fontWeight: '700',
    fontSize: 18,
  },
  subText: {
    color: '#cbd5e1',
    fontSize: 14,
  },
  controlsRow: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 8,
  },
})
