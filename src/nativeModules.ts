import { NitroModules } from 'react-native-nitro-modules'
import type { HybridObject } from 'react-native-nitro-modules'
import { Platform } from 'react-native'
import type {
  PlayerQueue as PlayerQueueType,
  TrackPlayer as TrackPlayerType,
} from './specs/TrackPlayer.nitro'
import type { AndroidAutoMediaLibrary as AndroidAutoMediaLibraryType } from './specs/AndroidAutoMediaLibrary.nitro'
import type { AudioDevices as AudioDevicesType } from './specs/AudioDevices.nitro'
import type { AudioRoutePicker as AudioRoutePickerType } from './specs/AudioRoutePicker.nitro'
import type { DownloadManager as DownloadManagerType } from './specs/DownloadManager.nitro'
import type { Equalizer as EqualizerType } from './specs/Equalizer.nitro'
import type { VideoSurface as VideoSurfaceType } from './specs/VideoSurface.nitro'

const createHybridObjectSafely = <T extends HybridObject<{}>,>(
  name: string
): T | null => {
  try {
    return NitroModules.createHybridObject<T>(name)
  } catch {
    return null
  }
}

export const PlayerQueue =
  NitroModules.createHybridObject<PlayerQueueType>('PlayerQueue')
export const TrackPlayer =
  NitroModules.createHybridObject<TrackPlayerType>('TrackPlayer')

export const AndroidAutoMediaLibrary =
  Platform.OS === 'android'
    ? NitroModules.createHybridObject<AndroidAutoMediaLibraryType>(
        'AndroidAutoMediaLibrary'
      )
    : null

export const AudioDevices =
  Platform.OS === 'android'
    ? NitroModules.createHybridObject<AudioDevicesType>('AudioDevices')
    : null

export const AudioRoutePicker =
  Platform.OS === 'ios'
    ? NitroModules.createHybridObject<AudioRoutePickerType>('AudioRoutePicker')
    : null

export const DownloadManager =
  NitroModules.createHybridObject<DownloadManagerType>('DownloadManager')

export const Equalizer =
  NitroModules.createHybridObject<EqualizerType>('Equalizer')

export const VideoSurface =
  createHybridObjectSafely<VideoSurfaceType>('VideoSurface')

