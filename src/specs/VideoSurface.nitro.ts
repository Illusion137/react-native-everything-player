import type { HybridObject } from 'react-native-nitro-modules'
import type {
  VideoContentMode,
  VideoSurfaceLayout,
  VideoSurfaceState,
} from '../types/VideoTypes'

export interface VideoSurface extends HybridObject<{
  android: 'kotlin'
  ios: 'swift'
}> {
  setContentMode(mode: VideoContentMode): void
  setLayout(layout: VideoSurfaceLayout): void
  setArtworkUri(uri?: string | null): void
  setVisible(isVisible: boolean): void
  getState(): VideoSurfaceState
}
