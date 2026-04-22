export type SabrContainer = 'webm' | 'mp4' | 'm4a' | 'unknown'

export type SabrMediaKind = 'audio' | 'video'

export type SabrTokenCallbackReason =
  | 'expired'
  | 'missing'
  | 'server_rejected'
  | 'requires_reauth'
  | 'unknown'

export interface SabrClientInfo {
  clientName: string
  clientVersion: string
  osName?: string
  osVersion?: string
  deviceModel?: string
}

export interface SabrFormat {
  itag: number
  mimeType: string
  bitrate: number
  width?: number
  height?: number
  fps?: number
  audioSampleRate?: number
  audioChannels?: number
  container?: SabrContainer
  kind: SabrMediaKind
}

export interface SabrDownloadParams {
  sabrServerUrl: string
  sabrUstreamerConfig: string
  sabrFormats?: SabrFormat[]
  poToken?: string
  placeholderPoToken?: string
  clientInfo?: SabrClientInfo
  cookie?: string
  preferOpus?: boolean
}

export interface SabrReloadPlayerResponseResult {
  sabrServerUrl: string
  sabrUstreamerConfig: string
}

export interface ManagedSabrDownloadParams extends SabrDownloadParams {
  onRefreshPoToken?: (reason: SabrTokenCallbackReason) => Promise<string>
  onReloadPlayerResponse?: (
    contextToken?: string | null
  ) => Promise<SabrReloadPlayerResponseResult | null>
}

export interface SabrDownloadProgress {
  outputPath: string
  bytesDownloaded: number
  totalBytes: number
  progress: number
  mediaKind: SabrMediaKind
}

export interface SabrReloadPlayerResponseRequest {
  outputPath: string
  token?: string | null
}

export interface SabrRefreshPoTokenRequest {
  outputPath: string
  reason: SabrTokenCallbackReason
}
