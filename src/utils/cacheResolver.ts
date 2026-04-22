import { NitroModules } from 'react-native-nitro-modules'
import type { DownloadManager as DownloadManagerType } from '../specs/DownloadManager.nitro'
import type { TrackItem } from '../types/PlayerQueue'

export type CacheResolution = {
  url: string
  fromCache: boolean
}

export const resolvePlaybackUrl = async (
  track: TrackItem
): Promise<CacheResolution> => {
  const downloadManager =
    NitroModules.createHybridObject<DownloadManagerType>('DownloadManager')

  const downloaded = await downloadManager.isTrackDownloaded(track.id)
  if (downloaded) {
    const local = await downloadManager.getLocalPath(track.id)
    if (local) {
      return { url: local, fromCache: true }
    }
  }

  return { url: track.url, fromCache: false }
}
