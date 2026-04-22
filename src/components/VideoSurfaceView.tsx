import React from 'react'
import {
  Image,
  UIManager,
  requireNativeComponent,
  StyleSheet,
  View,
  type StyleProp,
  type ViewStyle,
} from 'react-native'
import type { VideoContentMode } from '../types/VideoTypes'

type NativeVideoSurfaceProps = {
  contentMode?: VideoContentMode
  active?: boolean
  style?: StyleProp<ViewStyle>
}

type VideoSurfaceViewProps = NativeVideoSurfaceProps & {
  artworkUri?: string | null
}

const NATIVE_VIDEO_SURFACE_NAME = 'NitroPlayerVideoSurfaceView'
const hasNativeVideoSurface =
  !!UIManager.getViewManagerConfig(NATIVE_VIDEO_SURFACE_NAME)
const NativeVideoSurface = hasNativeVideoSurface
  ? requireNativeComponent<NativeVideoSurfaceProps>(NATIVE_VIDEO_SURFACE_NAME)
  : null

export const VideoSurfaceView = ({
  artworkUri,
  contentMode = 'contain',
  active = true,
  style,
}: VideoSurfaceViewProps) => {
  if (!active && artworkUri) {
    return (
      <View style={[styles.container, style]}>
        <Image source={{ uri: artworkUri }} style={styles.artwork} resizeMode="cover" />
      </View>
    )
  }

  if (NativeVideoSurface != null) {
    return (
      <NativeVideoSurface contentMode={contentMode} active={active} style={style} />
    )
  }

  return (
    <View style={[styles.container, style]}>
      {artworkUri ? (
        <Image source={{ uri: artworkUri }} style={styles.artwork} resizeMode="cover" />
      ) : null}
    </View>
  )
}

const styles = StyleSheet.create({
  container: {
    overflow: 'hidden',
  },
  artwork: {
    width: '100%',
    height: '100%',
  },
})
