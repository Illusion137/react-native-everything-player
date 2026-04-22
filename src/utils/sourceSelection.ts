import { Platform } from 'react-native'

export type StreamFormat =
  | 'mp4'
  | 'm4a'
  | 'aac'
  | 'webm'
  | 'opus'
  | 'hls'
  | 'dash'

export interface StreamCandidate {
  url: string
  format: StreamFormat
  isSabr?: boolean
  isLocal?: boolean
}

export const selectPreferredSource = (
  candidates: StreamCandidate[],
  preferOpus = true
): StreamCandidate | null => {
  if (candidates.length === 0) return null

  const ios = Platform.OS === 'ios'
  const order: StreamFormat[] = ios
    ? preferOpus
      ? ['webm', 'opus', 'hls', 'dash', 'mp4', 'aac', 'm4a']
      : ['hls', 'dash', 'mp4', 'aac', 'webm', 'opus', 'm4a']
    : ['dash', 'hls', 'mp4', 'webm', 'm4a', 'aac', 'opus']

  const sorted = [...candidates].sort((a, b) => {
    const ai = order.indexOf(a.format)
    const bi = order.indexOf(b.format)
    return ai - bi
  })

  if (ios) {
    const nonBrokenSabr = sorted.filter(
      (item) => !(item.isSabr && item.format === 'm4a')
    )
    if (nonBrokenSabr.length > 0) return nonBrokenSabr[0] ?? null
  }

  return sorted[0] ?? null
}
