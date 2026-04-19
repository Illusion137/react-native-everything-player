import Foundation
import MediaPlayer
import UIKit
import NitroModules

public class HybridEverythingPlayer: HybridNativeEverythingPlayerSpec {

    // MARK: - Singleton (accessed by HybridVideoView)

    /// Weak reference to the active player instance. Set on `setupPlayer`, cleared on `reset`.
    static weak var shared: HybridEverythingPlayer? = nil

    // MARK: - Attributes

    private var hasInitialized = false
    private let player = QueuedAudioPlayer()
    private let audioSessionController = AudioSessionController.shared
    private var shouldEmitProgressEvent: Bool = false
    private var shouldResumePlaybackAfterInterruptionEnds: Bool = false
    private var forwardJumpInterval: NSNumber? = nil
    private var backwardJumpInterval: NSNumber? = nil
    private var sessionCategory: AVAudioSession.Category = .playback
    private var sessionCategoryMode: AVAudioSession.Mode = .default
    private var sessionCategoryPolicy: AVAudioSession.RouteSharingPolicy = .default
    private var sessionCategoryOptions: AVAudioSession.CategoryOptions = []

    // Active DRM handler (retained while a DRM-protected item is playing)
    private var drmHandler: FairPlayDRMHandler?

    // MARK: - Video

    /// The currently attached video view (weak — the view may be unmounted at any time).
    private weak var attachedVideoView: HybridVideoView? = nil

    // MARK: - Nitro Callback Properties

    public var onPlaybackStateChanged: Variant_NullType____event__AnyMap_____Void = .first(NullType.null)
    public var onPlaybackError: Variant_NullType____event__AnyMap_____Void = .first(NullType.null)
    public var onPlaybackQueueEnded: Variant_NullType____event__AnyMap_____Void = .first(NullType.null)
    public var onActiveTrackChanged: Variant_NullType____event__AnyMap_____Void = .first(NullType.null)
    public var onPlayWhenReadyChanged: Variant_NullType____event__AnyMap_____Void = .first(NullType.null)
    public var onProgressUpdated: Variant_NullType____event__AnyMap_____Void = .first(NullType.null)
    public var onPlaybackMetadata: Variant_NullType____event__AnyMap_____Void = .first(NullType.null)
    public var onRemotePlay: Variant_NullType_______Void = .first(NullType.null)
    public var onRemotePause: Variant_NullType_______Void = .first(NullType.null)
    public var onRemoteStop: Variant_NullType_______Void = .first(NullType.null)
    public var onRemoteNext: Variant_NullType_______Void = .first(NullType.null)
    public var onRemotePrevious: Variant_NullType_______Void = .first(NullType.null)
    public var onRemoteJumpForward: Variant_NullType____event__AnyMap_____Void = .first(NullType.null)
    public var onRemoteJumpBackward: Variant_NullType____event__AnyMap_____Void = .first(NullType.null)
    public var onRemoteSeek: Variant_NullType____event__AnyMap_____Void = .first(NullType.null)
    public var onRemoteSetRating: Variant_NullType____event__AnyMap_____Void = .first(NullType.null)
    public var onRemoteDuck: Variant_NullType____event__AnyMap_____Void = .first(NullType.null)
    public var onRemoteLike: Variant_NullType_______Void = .first(NullType.null)
    public var onRemoteDislike: Variant_NullType_______Void = .first(NullType.null)
    public var onRemoteBookmark: Variant_NullType_______Void = .first(NullType.null)
    public var onChapterMetadataReceived: Variant_NullType____event__AnyMap_____Void = .first(NullType.null)
    public var onTimedMetadataReceived: Variant_NullType____event__AnyMap_____Void = .first(NullType.null)
    public var onCommonMetadataReceived: Variant_NullType____event__AnyMap_____Void = .first(NullType.null)
    public var onSabrDownloadProgress: Variant_NullType____event__AnyMap_____Void = .first(NullType.null)
    public var onSabrReloadPlayerResponse: Variant_NullType____event__AnyMap_____Void = .first(NullType.null)
    public var onSabrRefreshPoToken: Variant_NullType____event__AnyMap_____Void = .first(NullType.null)
    public var onAndroidControllerConnected: Variant_NullType____event__AnyMap_____Void = .first(NullType.null)
    public var onAndroidControllerDisconnected: Variant_NullType____event__AnyMap_____Void = .first(NullType.null)
    public var onPlaybackResume: Variant_NullType____event__AnyMap_____Void = .first(NullType.null)

    // MARK: - Lifecycle

    public override init() {
        super.init()
        audioSessionController.delegate = self
        player.playWhenReady = false
        player.event.receiveChapterMetadata.addListener(self, handleAudioPlayerChapterMetadataReceived)
        player.event.receiveTimedMetadata.addListener(self, handleAudioPlayerTimedMetadataReceived)
        player.event.receiveCommonMetadata.addListener(self, handleAudioPlayerCommonMetadataReceived)
        player.event.stateChange.addListener(self, handleAudioPlayerStateChange)
        player.event.fail.addListener(self, handleAudioPlayerFailed)
        player.event.currentItem.addListener(self, handleAudioPlayerCurrentItemChange)
        player.event.secondElapse.addListener(self, handleAudioPlayerSecondElapse)
        player.event.playWhenReadyChange.addListener(self, handlePlayWhenReadyChange)
    }

    deinit {
        player.stop()
        player.clear()
    }

    // MARK: - Event Emission

    private func emit(event: EventType, body: [String: Any]? = nil) {
        let map = AnyMap()
        if let body = body {
            for (key, value) in body {
                switch value {
                case let v as String:
                    map.setString(key: key, value: v)
                case let v as Double:
                    map.setDouble(key: key, value: v)
                case let v as Float:
                    map.setDouble(key: key, value: Double(v))
                case let v as Int:
                    map.setDouble(key: key, value: Double(v))
                case let v as Bool:
                    map.setBoolean(key: key, value: v)
                case let v as [String: Any]:
                    try? map.setAny(key: key, value: v)
                default:
                    break
                }
            }
        }
        switch event {
        case .PlaybackState:
            if case .second(let fn) = onPlaybackStateChanged { fn(map) }
        case .PlaybackError:
            if case .second(let fn) = onPlaybackError { fn(map) }
        case .PlaybackQueueEnded:
            if case .second(let fn) = onPlaybackQueueEnded { fn(map) }
        case .PlaybackActiveTrackChanged:
            if case .second(let fn) = onActiveTrackChanged { fn(map) }
        case .PlaybackPlayWhenReadyChanged:
            if case .second(let fn) = onPlayWhenReadyChanged { fn(map) }
        case .PlaybackProgressUpdated:
            if case .second(let fn) = onProgressUpdated { fn(map) }
        case .RemotePlay:
            if case .second(let fn) = onRemotePlay { fn() }
        case .RemotePause:
            if case .second(let fn) = onRemotePause { fn() }
        case .RemoteStop:
            if case .second(let fn) = onRemoteStop { fn() }
        case .RemoteNext:
            if case .second(let fn) = onRemoteNext { fn() }
        case .RemotePrevious:
            if case .second(let fn) = onRemotePrevious { fn() }
        case .RemoteJumpForward:
            if case .second(let fn) = onRemoteJumpForward { fn(map) }
        case .RemoteJumpBackward:
            if case .second(let fn) = onRemoteJumpBackward { fn(map) }
        case .RemoteSeek:
            if case .second(let fn) = onRemoteSeek { fn(map) }
        case .RemoteSetRating:
            if case .second(let fn) = onRemoteSetRating { fn(map) }
        case .RemoteDuck:
            if case .second(let fn) = onRemoteDuck { fn(map) }
        case .RemoteLike:
            if case .second(let fn) = onRemoteLike { fn() }
        case .RemoteDislike:
            if case .second(let fn) = onRemoteDislike { fn() }
        case .RemoteBookmark:
            if case .second(let fn) = onRemoteBookmark { fn() }
        case .MetadataChapterReceived:
            if case .second(let fn) = onChapterMetadataReceived { fn(map) }
        case .MetadataTimedReceived:
            if case .second(let fn) = onTimedMetadataReceived { fn(map) }
        case .MetadataCommonReceived:
            if case .second(let fn) = onCommonMetadataReceived { fn(map) }
        case .SabrDownloadProgress:
            if case .second(let fn) = onSabrDownloadProgress { fn(map) }
        case .SabrReloadPlayerResponse:
            if case .second(let fn) = onSabrReloadPlayerResponse { fn(map) }
        case .SabrRefreshPoToken:
            if case .second(let fn) = onSabrRefreshPoToken { fn(map) }
        }
    }

    private func buildAnyMap(from dict: [String: Any]) -> AnyMap {
        let map = AnyMap()
        for (key, value) in dict {
            switch value {
            case let v as String:
                map.setString(key: key, value: v)
            case let v as Double:
                map.setDouble(key: key, value: v)
            case let v as Float:
                map.setDouble(key: key, value: Double(v))
            case let v as Int:
                map.setDouble(key: key, value: Double(v))
            case let v as Bool:
                map.setBoolean(key: key, value: v)
            case let v as [String: Any]:
                try? map.setAny(key: key, value: v)
            default:
                break
            }
        }
        return map
    }

    private func anyMapToDictionary(_ map: AnyMap) -> [String: Any] {
        map.toDictionary().reduce(into: [String: Any]()) { result, entry in
            if let value = entry.value {
                result[entry.key] = value
            }
        }
    }

    // MARK: - AudioSessionControllerDelegate

    func handleInterruption(type: InterruptionType) {
        switch type {
        case .began:
            emit(event: .RemoteDuck, body: ["paused": true])
        case let .ended(shouldResume):
            if shouldResume {
                if shouldResumePlaybackAfterInterruptionEnds {
                    player.play()
                }
                emit(event: .RemoteDuck, body: ["paused": false])
            } else {
                emit(event: .RemoteDuck, body: ["paused": true, "permanent": true])
            }
        }
    }

    // MARK: - Validation Helpers

    private func throwWhenNotInitialized() throws {
        guard hasInitialized else {
            throw NSError(domain: "EverythingPlayer", code: 0, userInfo: [NSLocalizedDescriptionKey: "The player is not initialized. Call setupPlayer first."])
        }
    }

    private func throwWhenTrackIndexOutOfBounds(index: Int, min: Int = 0, max: Int? = nil) throws {
        let maxIdx = max ?? (player.items.count - 1)
        if index < min || index > maxIdx {
            throw NSError(domain: "EverythingPlayer", code: 1, userInfo: [NSLocalizedDescriptionKey: "The track index is out of bounds"])
        }
    }

    private func wrapAsync<T>(_ run: @escaping () async throws -> T) -> Promise<Promise<T>> {
        return Promise<Promise<T>>.resolved(withResult: Promise<T>.async { try await run() })
    }

    // MARK: - Setup

    public lazy var setupPlayer: (_ options: AnyMap) -> Promise<Promise<Void>> = { options in
        self.wrapAsync {
            // Idempotent: if already initialized (e.g. hot reload or StrictMode double-invocation),
            // just re-run configuration so options are applied, then return.
            guard !self.hasInitialized else { return }
            let config = self.anyMapToDictionary(options)

            if let bufferDuration = config["minBuffer"] as? TimeInterval {
                self.player.bufferDuration = bufferDuration
            }
            if let autoHandleInterruptions = config["autoHandleInterruptions"] as? Bool {
                self.shouldResumePlaybackAfterInterruptionEnds = autoHandleInterruptions
            }
            self.player.automaticallyUpdateNowPlayingInfo = config["autoUpdateMetadata"] as? Bool ?? true

            if let sessionCategoryStr = config["iosCategory"] as? String,
               let mappedCategory = SessionCategory(rawValue: sessionCategoryStr) {
                self.sessionCategory = mappedCategory.mapConfigToAVAudioSessionCategory()
            }
            if let sessionCategoryModeStr = config["iosCategoryMode"] as? String,
               let mappedCategoryMode = SessionCategoryMode(rawValue: sessionCategoryModeStr) {
                self.sessionCategoryMode = mappedCategoryMode.mapConfigToAVAudioSessionCategoryMode()
            }
            if let sessionCategoryPolicyStr = config["iosCategoryPolicy"] as? String,
               let mappedCategoryPolicy = SessionCategoryPolicy(rawValue: sessionCategoryPolicyStr) {
                self.sessionCategoryPolicy = mappedCategoryPolicy.mapConfigToAVAudioSessionCategoryPolicy()
            }

            let sessionCategoryOptsStr = config["iosCategoryOptions"] as? [String]
            let mappedCategoryOpts = sessionCategoryOptsStr?.compactMap {
                SessionCategoryOptions(rawValue: $0)?.mapConfigToAVAudioSessionCategoryOptions()
            } ?? []
            self.sessionCategoryOptions = AVAudioSession.CategoryOptions(mappedCategoryOpts)

            self.configureAudioSession()

            // Remote command handlers
            self.player.remoteCommandController.handleChangePlaybackPositionCommand = { [weak self] event in
                if let event = event as? MPChangePlaybackPositionCommandEvent {
                    self?.emit(event: .RemoteSeek, body: ["position": event.positionTime])
                    return .success
                }
                return .commandFailed
            }
            self.player.remoteCommandController.handleNextTrackCommand = { [weak self] _ in
                self?.emit(event: .RemoteNext)
                return .success
            }
            self.player.remoteCommandController.handlePauseCommand = { [weak self] _ in
                self?.emit(event: .RemotePause)
                return .success
            }
            self.player.remoteCommandController.handlePlayCommand = { [weak self] _ in
                self?.emit(event: .RemotePlay)
                return .success
            }
            self.player.remoteCommandController.handlePreviousTrackCommand = { [weak self] _ in
                self?.emit(event: .RemotePrevious)
                return .success
            }
            self.player.remoteCommandController.handleSkipBackwardCommand = { [weak self] event in
                if let command = event.command as? MPSkipIntervalCommand,
                   let interval = command.preferredIntervals.first {
                    self?.emit(event: .RemoteJumpBackward, body: ["interval": interval])
                    return .success
                }
                return .commandFailed
            }
            self.player.remoteCommandController.handleSkipForwardCommand = { [weak self] event in
                if let command = event.command as? MPSkipIntervalCommand,
                   let interval = command.preferredIntervals.first {
                    self?.emit(event: .RemoteJumpForward, body: ["interval": interval])
                    return .success
                }
                return .commandFailed
            }
            self.player.remoteCommandController.handleStopCommand = { [weak self] _ in
                self?.emit(event: .RemoteStop)
                return .success
            }
            self.player.remoteCommandController.handleTogglePlayPauseCommand = { [weak self] _ in
                self?.emit(event: self?.player.playerState == .paused ? .RemotePlay : .RemotePause)
                return .success
            }
            self.player.remoteCommandController.handleLikeCommand = { [weak self] _ in
                self?.emit(event: .RemoteLike)
                return .success
            }
            self.player.remoteCommandController.handleDislikeCommand = { [weak self] _ in
                self?.emit(event: .RemoteDislike)
                return .success
            }
            self.player.remoteCommandController.handleBookmarkCommand = { [weak self] _ in
                self?.emit(event: .RemoteBookmark)
                return .success
            }

            self.player.onSabrRefreshPoToken = { [weak self] reason in
                self?.emit(event: .SabrRefreshPoToken, body: ["reason": reason])
            }
            self.player.onSabrReloadPlayerResponse = { [weak self] token in
                self?.emit(event: .SabrReloadPlayerResponse, body: ["token": token as Any])
            }

            self.hasInitialized = true
            HybridEverythingPlayer.shared = self
        }
    }

    private func configureAudioSession() {
        if player.currentItem == nil {
            try? audioSessionController.deactivateSession()
            return
        }
        if player.playWhenReady {
            try? audioSessionController.activateSession()
            if #available(iOS 11.0, *) {
                try? AVAudioSession.sharedInstance().setCategory(sessionCategory, mode: sessionCategoryMode, policy: sessionCategoryPolicy, options: sessionCategoryOptions)
            } else {
                try? AVAudioSession.sharedInstance().setCategory(sessionCategory, mode: sessionCategoryMode, options: sessionCategoryOptions)
            }
        }
    }

    // MARK: - Options

    public lazy var updateOptions: (_ options: AnyMap) -> Promise<Promise<Void>> = { options in
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            let opts = self.anyMapToDictionary(options)

            var capabilitiesStr = opts["capabilities"] as? [String] ?? []
            if capabilitiesStr.contains("play") && capabilitiesStr.contains("pause") {
                capabilitiesStr.append("togglePlayPause")
            }
            self.forwardJumpInterval = opts["forwardJumpInterval"] as? NSNumber ?? self.forwardJumpInterval
            self.backwardJumpInterval = opts["backwardJumpInterval"] as? NSNumber ?? self.backwardJumpInterval

            self.player.remoteCommands = capabilitiesStr
                .compactMap { Capability(rawValue: $0) }
                .map { capability in
                    capability.mapToPlayerCommand(
                        forwardJumpInterval: self.forwardJumpInterval,
                        backwardJumpInterval: self.backwardJumpInterval,
                        likeOptions: opts["likeOptions"] as? [String: Any],
                        dislikeOptions: opts["dislikeOptions"] as? [String: Any],
                        bookmarkOptions: opts["bookmarkOptions"] as? [String: Any]
                    )
                }

            self.configureProgressUpdateEvent(
                interval: ((opts["progressUpdateEventInterval"] as? NSNumber) ?? 0).doubleValue
            )
        }
    }

    private func configureProgressUpdateEvent(interval: Double) {
        shouldEmitProgressEvent = interval > 0
        player.timeEventFrequency = shouldEmitProgressEvent
            ? .custom(time: CMTime(seconds: interval, preferredTimescale: 1000))
            : .everySecond
    }

    // MARK: - Queue Management

    public lazy var add: (_ tracks: [AnyMap], _ insertBeforeIndex: Variant_NullType_Double?) -> Promise<Promise<Variant_NullType_Double>> = { tracks, insertBeforeIndex in
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            let rawIndex: Double
            switch insertBeforeIndex {
            case .second(let v): rawIndex = v
            default: rawIndex = -1
            }
            let index = rawIndex == -1 ? self.player.items.count : Int(rawIndex)
            try self.throwWhenTrackIndexOutOfBounds(index: index, max: self.player.items.count)

            var trackObjects = [Track]()
            for trackMap in tracks {
                let dict = self.anyMapToDictionary(trackMap)
                guard let track = Track(dictionary: dict) else {
                    throw NSError(domain: "EverythingPlayer", code: 3, userInfo: [NSLocalizedDescriptionKey: "Track is missing a required key"])
                }
                trackObjects.append(track)
            }
            try? self.player.add(items: trackObjects, at: index)
            return .second(Double(index))
        }
    }

    public lazy var load: (_ track: AnyMap) -> Promise<Promise<Variant_NullType_Double>> = { track in
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            let dict = self.anyMapToDictionary(track)
            guard let t = Track(dictionary: dict) else {
                throw NSError(domain: "EverythingPlayer", code: 3, userInfo: [NSLocalizedDescriptionKey: "Track is missing a required key"])
            }
            self.player.load(item: t)
            let idx = self.player.currentIndex
            if idx < 0 { return .first(NullType.null) }
            return .second(Double(idx))
        }
    }

    public lazy var remove: (_ indexes: [Double]) -> Promise<Promise<Void>> = { indexes in
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            let intIndexes = indexes.map { Int($0) }
            for index in intIndexes {
                try self.throwWhenTrackIndexOutOfBounds(index: index)
            }
            for index in intIndexes.sorted().reversed() {
                try? self.player.removeItem(at: index)
            }
        }
    }

    public lazy var move: (_ fromIndex: Double, _ toIndex: Double) -> Promise<Promise<Void>> = { fromIndex, toIndex in
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            try self.throwWhenTrackIndexOutOfBounds(index: Int(fromIndex))
            try? self.player.moveItem(fromIndex: Int(fromIndex), toIndex: Int(toIndex))
        }
    }

    public lazy var removeUpcomingTracks: () -> Promise<Promise<Void>> = {
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            self.player.removeUpcomingItems()
        }
    }

    public lazy var setQueue: (_ tracks: [AnyMap]) -> Promise<Promise<Void>> = { tracks in
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            var trackObjects = [Track]()
            for trackMap in tracks {
                let dict = self.anyMapToDictionary(trackMap)
                guard let track = Track(dictionary: dict) else {
                    throw NSError(domain: "EverythingPlayer", code: 3, userInfo: [NSLocalizedDescriptionKey: "Track is missing a required key"])
                }
                trackObjects.append(track)
            }
            self.player.clear()
            try? self.player.add(items: trackObjects)
        }
    }

    // MARK: - Playback Control

    public lazy var play: () -> Promise<Promise<Void>> = {
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            self.player.play()
        }
    }

    public lazy var pause: () -> Promise<Promise<Void>> = {
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            self.player.pause()
        }
    }

    public lazy var stop: () -> Promise<Promise<Void>> = {
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            self.player.stop()
        }
    }

    public lazy var reset: () -> Promise<Promise<Void>> = {
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            self.player.stop()
            self.player.clear()
        }
    }

    public lazy var retry: () -> Promise<Promise<Void>> = {
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            self.player.reload(startFromCurrentTime: true)
        }
    }

    public lazy var setPlayWhenReady: (_ playWhenReady: Bool) -> Promise<Promise<Void>> = { playWhenReady in
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            self.player.playWhenReady = playWhenReady
        }
    }

    public lazy var getPlayWhenReady: () -> Promise<Promise<Bool>> = {
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            return self.player.playWhenReady
        }
    }

    // MARK: - Navigation

    public lazy var skip: (_ index: Double, _ initialPosition: Variant_NullType_Double?) -> Promise<Promise<Void>> = { index, initialPosition in
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            let trackIndex = Int(index)
            try self.throwWhenTrackIndexOutOfBounds(index: trackIndex)
            try? self.player.jumpToItem(atIndex: trackIndex, playWhenReady: self.player.playWhenReady)
            if case .second(let pos) = initialPosition, pos >= 0 {
                self.player.seek(to: pos)
            }
        }
    }

    public lazy var skipToNext: (_ initialPosition: Variant_NullType_Double?) -> Promise<Promise<Void>> = { initialPosition in
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            self.player.next()
            if case .second(let pos) = initialPosition, pos >= 0 {
                self.player.seek(to: pos)
            }
        }
    }

    public lazy var skipToPrevious: (_ initialPosition: Variant_NullType_Double?) -> Promise<Promise<Void>> = { initialPosition in
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            self.player.previous()
            if case .second(let pos) = initialPosition, pos >= 0 {
                self.player.seek(to: pos)
            }
        }
    }

    public lazy var seekTo: (_ position: Double) -> Promise<Promise<Void>> = { position in
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            self.player.seek(to: position)
        }
    }

    public lazy var seekBy: (_ offset: Double) -> Promise<Promise<Void>> = { offset in
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            self.player.seek(by: offset)
        }
    }

    // MARK: - Playback Properties

    public lazy var setRate: (_ rate: Double) -> Promise<Promise<Void>> = { rate in
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            self.player.rate = Float(rate)
        }
    }

    public lazy var getRate: () -> Promise<Promise<Double>> = {
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            return Double(self.player.rate)
        }
    }

    public lazy var setVolume: (_ level: Double) -> Promise<Promise<Void>> = { level in
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            self.player.volume = Float(level)
        }
    }

    public lazy var getVolume: () -> Promise<Promise<Double>> = {
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            return Double(self.player.volume)
        }
    }

    public lazy var setRepeatMode: (_ mode: Double) -> Promise<Promise<Void>> = { mode in
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            self.player.repeatMode = RepeatMode(rawValue: Int(mode)) ?? .off
        }
    }

    public lazy var getRepeatMode: () -> Promise<Promise<Double>> = {
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            return Double(self.player.repeatMode.rawValue)
        }
    }

    // MARK: - Track / Queue Getters

    public lazy var getTrack: (_ index: Double) -> Promise<Promise<Variant_NullType_AnyMap>> = { index in
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            let indexInt = Int(index)
            if indexInt >= 0 && indexInt < self.player.items.count,
               let track = self.player.items[indexInt] as? Track {
                return .second(self.buildAnyMap(from: track.toObject()))
            }
            return .first(NullType.null)
        }
    }

    public lazy var getQueue: () -> Promise<Promise<[AnyMap]>> = {
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            return self.player.items.compactMap { ($0 as? Track)?.toObject() }.map { self.buildAnyMap(from: $0) }
        }
    }

    public lazy var getActiveTrack: () -> Promise<Promise<Variant_NullType_AnyMap>> = {
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            let index = self.player.currentIndex
            if index >= 0 && index < self.player.items.count,
               let track = self.player.items[index] as? Track {
                return .second(self.buildAnyMap(from: track.toObject()))
            }
            return .first(NullType.null)
        }
    }

    public lazy var getActiveTrackIndex: () -> Promise<Promise<Variant_NullType_Double>> = {
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            let index = self.player.currentIndex
            if index < 0 || index >= self.player.items.count {
                return .first(NullType.null)
            }
            return .second(Double(index))
        }
    }

    public lazy var getProgress: () -> Promise<Promise<AnyMap>> = {
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            let map = AnyMap()
            map.setDouble(key: "position", value: self.player.currentTime)
            map.setDouble(key: "duration", value: self.player.duration)
            map.setDouble(key: "buffered", value: self.player.bufferedPosition)
            return map
        }
    }

    public lazy var getPlaybackState: () -> Promise<Promise<AnyMap>> = {
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            return self.buildAnyMap(from: self.getPlaybackStateBodyKeyValues(state: self.player.playerState))
        }
    }

    // MARK: - Metadata

    public lazy var updateMetadataForTrack: (_ trackIndex: Double, _ metadata: AnyMap) -> Promise<Promise<Void>> = { trackIndex, metadata in
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            let idx = Int(trackIndex)
            try self.throwWhenTrackIndexOutOfBounds(index: idx)
            let dict = self.anyMapToDictionary(metadata)
            let track = self.player.items[idx] as! Track
            track.updateMetadata(dictionary: dict)
            if self.player.currentIndex == idx {
                Metadata.update(for: self.player, with: dict)
            }
        }
    }

    public lazy var updateNowPlayingMetadata: (_ metadata: AnyMap) -> Promise<Promise<Void>> = { metadata in
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            let dict = self.anyMapToDictionary(metadata)
            Metadata.update(for: self.player, with: dict)
        }
    }

    // MARK: - Crossfade

    public lazy var setCrossFade: (_ seconds: Double) -> Promise<Promise<Void>> = { seconds in
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            self.player.crossfadeDuration = seconds
        }
    }

    // MARK: - Equalizer

    public lazy var setEqualizer: (_ bands: [AnyMap]) -> Promise<Promise<Void>> = { bands in
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            let gains = bands.map { band -> Float in
                let gain = band.getDouble(key: "gain")
                return Float(gain)
            }
            self.player.setEqualizerBands(gains)
        }
    }

    public lazy var getEqualizer: () -> Promise<Promise<[AnyMap]>> = {
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            return self.player.getEqualizerBands().enumerated().map { (i, gain) in
                let map = AnyMap()
                map.setDouble(key: "gain", value: Double(gain))
                map.setDouble(key: "frequency", value: Double(i))
                return map
            }
        }
    }

    public lazy var removeEqualizer: () -> Promise<Promise<Void>> = {
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            self.player.removeEqualizer()
        }
    }

    // MARK: - SABR Download

    private var sabrDownloaders: [String: SabrDownloader] = [:]

    public lazy var downloadSabrStream: (_ params: AnyMap, _ outputPath: String) -> Promise<Promise<String>> = { params, outputPath in
        self.wrapAsync {
            let paramsDict = self.anyMapToDictionary(params)
            guard let serverUrl = paramsDict["sabrServerUrl"] as? String,
                  let ustreamerConfig = paramsDict["sabrUstreamerConfig"] as? String else {
                throw NSError(domain: "EverythingPlayer", code: 4, userInfo: [NSLocalizedDescriptionKey: "Missing required SABR params (sabrServerUrl, sabrUstreamerConfig)"])
            }

            let formatsData = paramsDict["sabrFormats"] as? [[String: Any]] ?? []
            let formats = formatsData.compactMap { SabrFormat(dictionary: $0) }
            let poToken = paramsDict["poToken"] as? String
            let cookie = paramsDict["cookie"] as? String
            let clientInfoVal = paramsDict["clientInfo"] as? [String: Any]
            let clientName: Int32? = (clientInfoVal?["clientName"] as? NSNumber).map { Int32($0.intValue) }
            let clientVersion: String? = clientInfoVal?["clientVersion"] as? String
            let durationMs: Double? = (paramsDict["duration"] as? Double).map { $0 * 1000 }
            let preferOpus = paramsDict["preferOpus"] as? Bool ?? false

            let config = SabrStreamConfig(
                server_abr_streaming_url: serverUrl,
                video_playback_ustreamer_config: ustreamerConfig,
                po_token: poToken,
                duration_ms: durationMs,
                formats: formats,
                client_name: clientName,
                client_version: clientVersion,
                cookie: cookie
            )

            let outputURL: URL = outputPath.hasPrefix("file://")
                ? URL(string: outputPath)!
                : URL(fileURLWithPath: outputPath)

            let downloader = SabrDownloader(config: config)
            await MainActor.run { self.sabrDownloaders[outputPath] = downloader }

            downloader.onReloadPlayerResponse = { [weak self] token in
                self?.emit(event: .SabrReloadPlayerResponse, body: ["outputPath": outputPath, "token": token as Any])
            }
            downloader.onRefreshPoToken = { [weak self] reason in
                self?.emit(event: .SabrRefreshPoToken, body: ["outputPath": outputPath, "reason": reason])
            }

            _ = try await downloader.download(to: outputURL, preferOpus: preferOpus) { [weak self] fraction in
                self?.emit(event: .SabrDownloadProgress, body: ["outputPath": outputPath, "progress": fraction])
            }
            _ = await MainActor.run { self.sabrDownloaders.removeValue(forKey: outputPath) }
            return outputPath
        }
    }

    public lazy var updateSabrDownloadStream: (_ outputPath: String, _ serverUrl: String, _ ustreamerConfig: String) -> Promise<Promise<Void>> = { outputPath, serverUrl, ustreamerConfig in
        self.wrapAsync {
            guard let downloader = self.sabrDownloaders[outputPath] else {
                throw NSError(domain: "EverythingPlayer", code: 5, userInfo: [NSLocalizedDescriptionKey: "No active SABR download for outputPath: \(outputPath)"])
            }
            downloader.updateStream(serverUrl: serverUrl, ustreamerConfig: ustreamerConfig)
        }
    }

    public lazy var updateSabrDownloadPoToken: (_ outputPath: String, _ poToken: String) -> Promise<Promise<Void>> = { outputPath, poToken in
        self.wrapAsync {
            guard let downloader = self.sabrDownloaders[outputPath] else {
                throw NSError(domain: "EverythingPlayer", code: 5, userInfo: [NSLocalizedDescriptionKey: "No active SABR download for outputPath: \(outputPath)"])
            }
            downloader.updatePoToken(poToken: poToken)
        }
    }

    public lazy var updateSabrPlaybackPoToken: (_ poToken: String) -> Promise<Promise<Void>> = { poToken in
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            self.player.updateSabrStreamPoToken(poToken)
        }
    }

    public lazy var updateSabrPlaybackStream: (_ serverUrl: String, _ ustreamerConfig: String) -> Promise<Promise<Void>> = { serverUrl, ustreamerConfig in
        self.wrapAsync {
            try self.throwWhenNotInitialized()
            self.player.updateSabrPlaybackStream(serverUrl: serverUrl, ustreamerConfig: ustreamerConfig)
        }
    }

    // MARK: - Android-only Stubs

    public lazy var acquireWakeLock: () -> Promise<Promise<Void>> = {
        self.wrapAsync { }
    }

    public lazy var abandonWakeLock: () -> Promise<Promise<Void>> = {
        self.wrapAsync { }
    }

    public lazy var validateOnStartCommandIntent: () -> Promise<Promise<Bool>> = {
        self.wrapAsync { true }
    }

    // MARK: - DRM

    private func setupDRM(for track: Track) {
        drmHandler?.detach()
        drmHandler = nil

        guard track.drmType == "fairplay",
              let licenseServer = track.drmLicenseServer,
              let certUrl = track.drmCertificateUrl else { return }

        let handler = FairPlayDRMHandler(licenseServerURL: licenseServer, certificateURL: certUrl)
        if let headers = track.drmHeaders {
            handler.licenseRequestHeaders = headers
        }
        player.attachFairPlayDRMHandler(handler)
        drmHandler = handler
    }

    // MARK: - Playback State Helpers

    private func getPlaybackStateErrorKeyValues() -> [String: Any] {
        switch player.playbackError {
        case .failedToLoadKeyValue:
            return ["message": "Failed to load resource", "code": "ios_failed_to_load_resource"]
        case .invalidSourceUrl(let sourceUrl):
            return ["message": "The source URL was invalid: \(sourceUrl)", "code": "ios_invalid_source_url"]
        case .notConnectedToInternet:
            return ["message": "A network resource was requested, but an internet connection has not been established.", "code": "ios_not_connected_to_internet"]
        case .playbackFailed:
            if let detail = player.playbackErrorDescription, !detail.isEmpty {
                return ["message": "Playback of the track failed (\(detail))", "code": "ios_playback_failed"]
            }
            return ["message": "Playback of the track failed", "code": "ios_playback_failed"]
        case .itemWasUnplayable:
            return ["message": "The track could not be played", "code": "ios_track_unplayable"]
        default:
            return ["message": "A playback error occurred", "code": "ios_playback_error"]
        }
    }

    private func getPlaybackStateBodyKeyValues(state: AudioPlayerState) -> [String: Any] {
        var body: [String: Any] = ["state": State.fromPlayerState(state: state).rawValue]
        if state == .failed {
            body["error"] = getPlaybackStateErrorKeyValues()
        }
        return body
    }

    // MARK: - QueuedAudioPlayer Event Handlers

    func handleAudioPlayerStateChange(state: AVPlayerWrapperState) {
        emit(event: .PlaybackState, body: getPlaybackStateBodyKeyValues(state: state))
        if state == .ended {
            emit(event: .PlaybackQueueEnded, body: [
                "track": player.currentIndex,
                "position": player.currentTime
            ] as [String: Any])
        }
    }

    func handleAudioPlayerCommonMetadataReceived(metadata: [AVMetadataItem]) {
        let commonMetadata = MetadataAdapter.convertToCommonMetadata(metadata: metadata, skipRaw: true)
        emit(event: .MetadataCommonReceived, body: ["metadata": commonMetadata])
    }

    func handleAudioPlayerChapterMetadataReceived(metadata: [AVTimedMetadataGroup]) {
        let metadataItems = MetadataAdapter.convertToGroupedMetadata(metadataGroups: metadata)
        emit(event: .MetadataChapterReceived, body: ["metadata": metadataItems])
    }

    func handleAudioPlayerTimedMetadataReceived(metadata: [AVTimedMetadataGroup]) {
        let metadataItems = MetadataAdapter.convertToGroupedMetadata(metadataGroups: metadata)
        emit(event: .MetadataTimedReceived, body: ["metadata": metadataItems])
    }

    func handleAudioPlayerFailed(error: Error?) {
        var body: [String: Any] = [:]
        let details = getPlaybackStateErrorKeyValues()
        body["code"] = details["code"]
        body["message"] = details["message"]
        body["error"] = details["message"]
        if let localized = error?.localizedDescription {
            body["nativeErrorDescription"] = localized
        }
        if let nsError = error as NSError? {
            body["nativeCode"] = nsError.code
            body["nativeDomain"] = nsError.domain
            if let underlying = nsError.userInfo[NSUnderlyingErrorKey] as? NSError {
                body["underlyingCode"] = underlying.code
                body["underlyingDomain"] = underlying.domain
                if let underlyingMessage = underlying.userInfo[NSLocalizedDescriptionKey] as? String {
                    body["underlyingMessage"] = underlyingMessage
                }
            }
        }
        emit(event: .PlaybackError, body: body)
    }

    func handleAudioPlayerCurrentItemChange(
        item: AudioItem?,
        index: Int?,
        lastItem: AudioItem?,
        lastIndex: Int?,
        lastPosition: Double?
    ) {
        if let item = item {
            DispatchQueue.main.async {
                UIApplication.shared.beginReceivingRemoteControlEvents()
            }
            if player.automaticallyUpdateNowPlayingInfo {
                let isTrackLiveStream = (item as? Track)?.isLiveStream ?? false
                player.nowPlayingInfoController.set(keyValue: NowPlayingInfoProperty.isLiveStream(isTrackLiveStream))
            }
            if let track = item as? Track {
                setupDRM(for: track)
            }
        } else {
            DispatchQueue.main.async {
                UIApplication.shared.endReceivingRemoteControlEvents()
            }
            drmHandler?.detach()
            drmHandler = nil
        }

        if (item != nil && lastItem == nil) || item == nil {
            configureAudioSession()
        }

        var body: [String: Any] = ["lastPosition": lastPosition ?? 0]
        if let lastIndex = lastIndex { body["lastIndex"] = lastIndex }
        if let lastTrack = (lastItem as? Track)?.toObject() { body["lastTrack"] = lastTrack }
        if let index = index { body["index"] = index }
        if let track = (item as? Track)?.toObject() { body["track"] = track }
        emit(event: .PlaybackActiveTrackChanged, body: body)

        // Update attached video view when the track changes.
        if let videoView = attachedVideoView {
            let isSabrTrack = (item as? Track)?.isSabr == true
            if isSabrTrack {
                player.avPlayerWrapper.ensureSabrVideoStreamAttachedForCurrentItem()
            } else {
                videoView.connectAVPlayer(player.avPlayerWrapper.avPlayer)
            }
            item?.getArtwork { image in
                DispatchQueue.main.async {
                    videoView.showThumbnail(image: image)
                }
            }
        }

        // Force-emit playing state after crossfade so JS layer reflects correct state
        if item != nil && player.playerState == .playing {
            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }
                self.emit(event: .PlaybackState, body: self.getPlaybackStateBodyKeyValues(state: self.player.playerState))
            }
        }
    }

    func handleAudioPlayerSecondElapse(seconds: Double) {
        if !shouldEmitProgressEvent || player.currentItem == nil { return }
        emit(event: .PlaybackProgressUpdated, body: [
            "position": player.currentTime,
            "duration": player.duration,
            "buffered": player.bufferedPosition,
            "track": player.currentIndex
        ])
    }

    func handlePlayWhenReadyChange(playWhenReady: Bool) {
        configureAudioSession()
        attachedVideoView?.setSabrPlaybackState(playWhenReady: playWhenReady)
        emit(event: .PlaybackPlayWhenReadyChanged, body: ["playWhenReady": playWhenReady])
    }
}

// MARK: - Video View

extension HybridEverythingPlayer {

    /// Called by `HybridVideoView.onAttach()` when the view is mounted in React.
    func videoViewDidAttach(_ videoView: HybridVideoView) {
        attachedVideoView = videoView
        player.avPlayerWrapper.videoEnabled = true

        // Wire SABR video stream callback so that when a SABR track starts,
        // the video stream is handed off to the view automatically.
        player.avPlayerWrapper.onSabrVideoStreamReady = { [weak self] videoStream in
            self?.attachedVideoView?.connectSabrVideoStream(
                videoStream,
                playWhenReady: self?.player.playWhenReady ?? true
            )
        }
        player.avPlayerWrapper.ensureSabrVideoStreamAttachedForCurrentItem()

        // Connect the existing AVPlayer so non-SABR tracks render immediately.
        videoView.connectAVPlayer(player.avPlayerWrapper.avPlayer)

        // Show the current track's artwork while video loads or for audio-only tracks.
        player.currentItem?.getArtwork { image in
            DispatchQueue.main.async {
                videoView.showThumbnail(image: image)
            }
        }
    }

    /// Called by `HybridVideoView.onDetach()` or `onDropView()` when the view unmounts.
    func videoViewDidDetach(_ videoView: HybridVideoView) {
        guard attachedVideoView === videoView else { return }
        attachedVideoView = nil
        player.avPlayerWrapper.videoEnabled = false
        player.avPlayerWrapper.onSabrVideoStreamReady = nil
        videoView.clearVideo()
    }
}

// MARK: - AudioSessionControllerDelegate

extension HybridEverythingPlayer: AudioSessionControllerDelegate {}
