export type VideoContentMode = 'cover' | 'contain' | 'stretch'

export interface VideoSurfaceLayout {
  width: number
  height: number
}

export interface VideoSurfaceState {
  attachedTrackId?: string | null
  hasVideo: boolean
  isSabrVideoActive: boolean
}
