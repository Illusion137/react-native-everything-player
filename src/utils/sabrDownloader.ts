import { NitroModules } from 'react-native-nitro-modules'
import type { TrackPlayer as TrackPlayerType } from '../specs/TrackPlayer.nitro'
import type {
  SabrDownloadProgress,
  ManagedSabrDownloadParams,
  SabrTokenCallbackReason,
} from '../types/SabrTypes'

type ProgressHandler = (progress: SabrDownloadProgress) => void

const progressHandlers = new Map<string, ProgressHandler>()
const reloadHandlers = new Map<
  string,
  (token?: string | null) => Promise<{ sabrServerUrl: string; sabrUstreamerConfig: string } | null>
>()
const refreshHandlers = new Map<
  string,
  (reason: SabrTokenCallbackReason) => Promise<string>
>()
const refreshInFlight = new Set<string>()

let listenersInitialized = false
const trackPlayer = NitroModules.createHybridObject<TrackPlayerType>('TrackPlayer')

const ensureListeners = () => {
  if (listenersInitialized) return
  listenersInitialized = true

  trackPlayer.onSabrDownloadProgress((event) => {
    progressHandlers.get(event.outputPath)?.(event)
  })

  trackPlayer.onSabrReloadPlayerResponse(async (event) => {
    const handler = reloadHandlers.get(event.outputPath)
    if (!handler) return
    const next = await handler(event.token)
    if (!next) return
    await trackPlayer.updateSabrStream(
      event.outputPath,
      next.sabrServerUrl,
      next.sabrUstreamerConfig
    )
  })

  trackPlayer.onSabrRefreshPoToken(async (event) => {
    const handler = refreshHandlers.get(event.outputPath)
    if (!handler) return
    if (refreshInFlight.has(event.outputPath)) return

    refreshInFlight.add(event.outputPath)
    try {
      const token = await handler(event.reason)
      await trackPlayer.updateSabrPoToken(event.outputPath, token)
    } finally {
      refreshInFlight.delete(event.outputPath)
    }
  })
}

export const downloadSabrManaged = async (
  params: ManagedSabrDownloadParams,
  outputPath: string,
  onProgress?: ProgressHandler
) => {
  ensureListeners()

  if (onProgress) progressHandlers.set(outputPath, onProgress)
  if (params.onReloadPlayerResponse) {
    reloadHandlers.set(outputPath, params.onReloadPlayerResponse)
  }
  if (params.onRefreshPoToken) {
    refreshHandlers.set(outputPath, params.onRefreshPoToken)
  }

  try {
    await trackPlayer.downloadSabr(params, outputPath)
  } finally {
    progressHandlers.delete(outputPath)
    reloadHandlers.delete(outputPath)
    refreshHandlers.delete(outputPath)
    refreshInFlight.delete(outputPath)
  }
}
