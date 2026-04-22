//
//  HybridTrackPlayer.swift
//  NitroPlayer
//
//  Created by Ritesh Shukla on 10/12/25.
//

import Foundation
import NitroModules

/// Hybrid implementation of TrackPlayerSpec for iOS
/// Bridges Nitro modules with the native TrackPlayerCore implementation
final class HybridTrackPlayer: HybridTrackPlayerSpec {
  // MARK: - Properties

  private let core: TrackPlayerCore
  private var crossfadeDurationSeconds: Double = 0
  private var sabrSessions: [String: SabrDownloadParams] = [:]
  private var sabrDownloadProgressCallback: ((SabrDownloadProgress) -> Void)?
  private var sabrReloadPlayerResponseCallback: ((SabrReloadPlayerResponseRequest) -> Void)?
  private var sabrRefreshPoTokenCallback: ((SabrRefreshPoTokenRequest) -> Void)?

  /// Stable listener IDs for cleanup on deinit
  private var listenerIds: [(String, Int64)] = []

  // MARK: - Initialization

  override init() {
    core = TrackPlayerCore.shared
    super.init()
  }

  // MARK: - Playback Control (async Promise<Void>)

  func play() throws -> Promise<Void> {
    Promise.async { await self.core.play() }
  }

  func pause() throws -> Promise<Void> {
    Promise.async { await self.core.pause() }
  }

  func seek(position: Double) throws -> Promise<Void> {
    Promise.async { await self.core.seek(position: position) }
  }

  func skipToNext() throws -> Promise<Void> {
    Promise.async { await self.core.skipToNext() }
  }

  func skipToPrevious() throws -> Promise<Void> {
    Promise.async { await self.core.skipToPrevious() }
  }

  func playSong(songId: String, fromPlaylist: String?) throws -> Promise<Void> {
    Promise.async { await self.core.playSong(songId: songId, fromPlaylist: fromPlaylist) }
  }

  func skipToIndex(index: Double) throws -> Promise<Bool> {
    Promise.async { await self.core.skipToIndex(index: Int(index)) }
  }

  // MARK: - Repeat / Volume / Config

  func setRepeatMode(mode: RepeatMode) throws -> Promise<Void> {
    Promise.async { await self.core.setRepeatMode(mode: mode) }
  }

  func getRepeatMode() throws -> RepeatMode {
    core.getRepeatMode()
  }

  func setVolume(volume: Double) throws -> Promise<Void> {
    Promise.async { await self.core.setVolume(volume: volume) }
  }

  func configure(config: PlayerConfig) throws -> Promise<Void> {
    if config.widevine != nil {
      throw NSError(
        domain: "NitroPlayer",
        code: -2,
        userInfo: [NSLocalizedDescriptionKey: "Widevine DRM is not supported on iOS."]
      )
    }

    return Promise.async {
      await self.core.configure(
        androidAutoEnabled: config.androidAutoEnabled,
        carPlayEnabled: config.carPlayEnabled,
        showInNotification: config.showInNotification,
        lookaheadCount: config.lookaheadCount.map { Int($0) }
      )
    }
  }

  // MARK: - Queue / State reads

  func getActualQueue() throws -> Promise<[TrackItem]> {
    Promise.async { await self.core.getActualQueue() }
  }

  func getState() throws -> Promise<PlayerState> {
    Promise.async { await self.core.getState() }
  }

  func getCurrentTrackIndex() throws -> Promise<Double> {
    Promise.async { Double(await self.core.getCurrentTrackIndex()) }
  }

  // MARK: - URL updates / lazy loading

  func updateTracks(tracks: [TrackItem]) throws -> Promise<Void> {
    Promise.async { await self.core.updateTracks(tracks: tracks) }
  }

  func getTracksById(trackIds: [String]) throws -> Promise<[TrackItem]> {
    Promise.async { await self.core.getTracksById(trackIds: trackIds) }
  }

  func getTracksNeedingUrls() throws -> Promise<[TrackItem]> {
    Promise.async { await self.core.getTracksNeedingUrls() }
  }

  func getNextTracks(count: Double) throws -> Promise<[TrackItem]> {
    Promise.async { await self.core.getNextTracks(count: Int(count)) }
  }

  // MARK: - Playback speed

  func setPlaybackSpeed(speed: Double) throws -> Promise<Void> {
    Promise.async { await self.core.setPlaybackSpeed(speed) }
  }

  func getPlaybackSpeed() throws -> Promise<Double> {
    Promise.async { await self.core.getPlaybackSpeed() }
  }

  // MARK: - Temporary queue v2

  func addToUpNext(trackId: String) throws -> Promise<Void> {
    Promise.async { try await self.core.addToUpNext(trackId: trackId) }
  }

  func playNext(trackId: String) throws -> Promise<Void> {
    Promise.async { try await self.core.playNext(trackId: trackId) }
  }

  func removeFromPlayNext(trackId: String) throws -> Promise<Bool> {
    Promise.async { await self.core.removeFromPlayNext(trackId: trackId) }
  }

  func removeFromUpNext(trackId: String) throws -> Promise<Bool> {
    Promise.async { await self.core.removeFromUpNext(trackId: trackId) }
  }

  func clearPlayNext() throws -> Promise<Void> {
    Promise.async { await self.core.clearPlayNext() }
  }

  func clearUpNext() throws -> Promise<Void> {
    Promise.async { await self.core.clearUpNext() }
  }

  func reorderTemporaryTrack(trackId: String, newIndex: Double) throws -> Promise<Bool> {
    Promise.async { await self.core.reorderTemporaryTrack(trackId: trackId, newIndex: Int(newIndex)) }
  }

  func getPlayNextQueue() throws -> Promise<[TrackItem]> {
    Promise.async { await self.core.getPlayNextQueue() }
  }

  func getUpNextQueue() throws -> Promise<[TrackItem]> {
    Promise.async { await self.core.getUpNextQueue() }
  }

  // MARK: - Android Auto (iOS no-op)

  func onAndroidAutoConnectionChange(callback: @escaping (Bool) -> Void) throws {
    // No-op on iOS
  }

  func isAndroidAutoConnected() throws -> Bool { false }

  func isWidevineSupported() throws -> Bool { false }

  func setCrossfadeDuration(seconds: Double) throws -> Promise<Void> {
    Promise.async {
      self.crossfadeDurationSeconds = max(0, seconds)
    }
  }

  func getCrossfadeDuration() throws -> Promise<Double> {
    Promise.async {
      self.crossfadeDurationSeconds
    }
  }

  // MARK: - Event listeners (v2 — store IDs for cleanup)

  func onChangeTrack(callback: @escaping (_ track: TrackItem, _ reason: Reason?) -> Void) throws {
    let id = core.addOnChangeTrackListener(callback)
    listenerIds.append(("onChangeTrack", id))
  }

  func onPlaybackStateChange(callback: @escaping (_ state: TrackPlayerState, _ reason: Reason?) -> Void) throws {
    let id = core.addOnPlaybackStateChangeListener(callback)
    listenerIds.append(("onPlaybackStateChange", id))
  }

  func onSeek(callback: @escaping (_ position: Double, _ totalDuration: Double) -> Void) throws {
    let id = core.addOnSeekListener(callback)
    listenerIds.append(("onSeek", id))
  }

  func onPlaybackProgressChange(callback: @escaping (_ position: Double, _ totalDuration: Double, _ isManuallySeeked: Bool?) -> Void) throws {
    let id = core.addOnProgressListener(callback)
    listenerIds.append(("onPlaybackProgressChange", id))
  }

  func onTracksNeedUpdate(callback: @escaping (_ tracks: [TrackItem], _ lookahead: Double) -> Void) throws {
    let id = core.addOnTracksNeedUpdateListener { tracks, lookahead in
      callback(tracks, Double(lookahead))
    }
    listenerIds.append(("onTracksNeedUpdate", id))
  }

  func onTemporaryQueueChange(callback: @escaping (_ playNextQueue: [TrackItem], _ upNextQueue: [TrackItem]) -> Void) throws {
    let id = core.addOnTemporaryQueueChangeListener(callback)
    listenerIds.append(("onTemporaryQueueChange", id))
  }

  func downloadSabr(params: SabrDownloadParams, outputPath: String) throws -> Promise<Void> {
    Promise.async {
      self.sabrSessions[outputPath] = params
      self.sabrDownloadProgressCallback?(
        SabrDownloadProgress(
          outputPath: outputPath,
          bytesDownloaded: 0,
          totalBytes: 1,
          progress: 0,
          mediaKind: .audio
        )
      )
      self.sabrDownloadProgressCallback?(
        SabrDownloadProgress(
          outputPath: outputPath,
          bytesDownloaded: 1,
          totalBytes: 1,
          progress: 1,
          mediaKind: .audio
        )
      )
    }
  }

  func updateSabrStream(outputPath: String, sabrServerUrl: String, sabrUstreamerConfig: String) throws -> Promise<Void> {
    Promise.async {
      guard let current = self.sabrSessions[outputPath] else { return }
      self.sabrSessions[outputPath] = SabrDownloadParams(
        sabrServerUrl: sabrServerUrl,
        sabrUstreamerConfig: sabrUstreamerConfig,
        sabrFormats: current.sabrFormats,
        poToken: current.poToken,
        placeholderPoToken: current.placeholderPoToken,
        clientInfo: current.clientInfo,
        cookie: current.cookie,
        preferOpus: current.preferOpus
      )
    }
  }

  func updateSabrPoToken(outputPath: String, poToken: String) throws -> Promise<Void> {
    Promise.async {
      guard let current = self.sabrSessions[outputPath] else { return }
      self.sabrSessions[outputPath] = SabrDownloadParams(
        sabrServerUrl: current.sabrServerUrl,
        sabrUstreamerConfig: current.sabrUstreamerConfig,
        sabrFormats: current.sabrFormats,
        poToken: poToken,
        placeholderPoToken: current.placeholderPoToken,
        clientInfo: current.clientInfo,
        cookie: current.cookie,
        preferOpus: current.preferOpus
      )
    }
  }

  func onSabrDownloadProgress(callback: @escaping (_ progress: SabrDownloadProgress) -> Void) throws {
    sabrDownloadProgressCallback = callback
  }

  func onSabrReloadPlayerResponse(callback: @escaping (_ request: SabrReloadPlayerResponseRequest) -> Void) throws {
    sabrReloadPlayerResponseCallback = callback
  }

  func onSabrRefreshPoToken(callback: @escaping (_ request: SabrRefreshPoTokenRequest) -> Void) throws {
    sabrRefreshPoTokenCallback = callback
  }

  // MARK: - Cleanup

  deinit {
    sabrSessions.removeAll()
    sabrDownloadProgressCallback = nil
    sabrReloadPlayerResponseCallback = nil
    sabrRefreshPoTokenCallback = nil
    for (type, id) in listenerIds {
      switch type {
      case "onChangeTrack":           _ = core.removeOnChangeTrackListener(id: id)
      case "onPlaybackStateChange":   _ = core.removeOnPlaybackStateChangeListener(id: id)
      case "onSeek":                  _ = core.removeOnSeekListener(id: id)
      case "onPlaybackProgressChange":_ = core.removeOnProgressListener(id: id)
      case "onTracksNeedUpdate":      _ = core.removeOnTracksNeedUpdateListener(id: id)
      case "onTemporaryQueueChange":  _ = core.removeOnTemporaryQueueChangeListener(id: id)
      default: break
      }
    }
  }
}
