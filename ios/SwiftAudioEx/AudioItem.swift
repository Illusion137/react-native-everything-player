//
//  AudioItem.swift
//  SwiftAudio
//
//  Created by Jørgen Henrichsen on 18/03/2018.
//

import Foundation
import AVFoundation

#if os(iOS)
import UIKit
typealias AudioItemImage = UIImage
#elseif os(macOS)
import AppKit
typealias AudioItemImage = NSImage
#endif

enum SourceType {
    case stream
    case file
}

protocol AudioItem {
    func getSourceUrl() -> String
    func getArtist() -> String?
    func getTitle() -> String?
    func getAlbumTitle() -> String?
    func getSourceType() -> SourceType
    func getArtwork(_ handler: @escaping (AudioItemImage?) -> Void)
    func getDuration() -> Double?
}

extension AudioItem {
    public func getDuration() -> Double? { nil }
}

/// Make your `AudioItem`-subclass conform to this protocol to control which AVAudioTimePitchAlgorithm is used for each item.
protocol TimePitching {
    func getPitchAlgorithmType() -> AVAudioTimePitchAlgorithm
    
}

/// Make your `AudioItem`-subclass conform to this protocol to control enable the ability to start an item at a specific time of playback.
protocol InitialTiming {
    func getInitialTime() -> TimeInterval
}

/// Make your `AudioItem`-subclass conform to this protocol to set initialization options for the asset. Available keys available at [Apple Developer Documentation](https://developer.apple.com/documentation/avfoundation/avurlasset/initialization_options).
protocol AssetOptionsProviding {
    func getAssetOptions() -> [String: Any]
}

/// Make your `AudioItem`-subclass conform to this protocol to enable track trimming.
/// When `getStartTime()` returns a non-nil value, playback will begin at that offset.
/// When `getEndTime()` returns a non-nil value, the track will end there and crossfade
/// timing is calculated relative to that value rather than the track's full duration.
protocol Trimmable {
    /// The trim start offset in seconds. Playback begins here. Returns nil to use the natural beginning.
    func getStartTime() -> TimeInterval?
    /// The trim end cutoff in seconds. Playback ends here. Returns nil to use the natural track duration.
    func getEndTime() -> TimeInterval?
}

class DefaultAudioItem: AudioItem, Identifiable {

    public var audioUrl: String
    
    public var artist: String?
    
    public var title: String?
    
    public var albumTitle: String?
    
    public var sourceType: SourceType
    
    public var artwork: AudioItemImage?
    
    public init(audioUrl: String, artist: String? = nil, title: String? = nil, albumTitle: String? = nil, sourceType: SourceType, artwork: AudioItemImage? = nil) {
        self.audioUrl = audioUrl
        self.artist = artist
        self.title = title
        self.albumTitle = albumTitle
        self.sourceType = sourceType
        self.artwork = artwork
    }
    
    public func getSourceUrl() -> String {
        audioUrl
    }
    
    public func getArtist() -> String? {
        artist
    }
    
    public func getTitle() -> String? {
        title
    }
    
    public func getAlbumTitle() -> String? {
        albumTitle
    }
    
    public func getSourceType() -> SourceType {
        sourceType
    }

    public func getArtwork(_ handler: @escaping (AudioItemImage?) -> Void) {
        handler(artwork)
    }
    
}

/// An AudioItem that also conforms to the `TimePitching`-protocol
class DefaultAudioItemTimePitching: DefaultAudioItem, TimePitching {
    
    public var pitchAlgorithmType: AVAudioTimePitchAlgorithm
    
    public override init(audioUrl: String, artist: String?, title: String?, albumTitle: String?, sourceType: SourceType, artwork: AudioItemImage?) {
        pitchAlgorithmType = AVAudioTimePitchAlgorithm.timeDomain
        super.init(audioUrl: audioUrl, artist: artist, title: title, albumTitle: albumTitle, sourceType: sourceType, artwork: artwork)
    }
    
    public init(audioUrl: String, artist: String?, title: String?, albumTitle: String?, sourceType: SourceType, artwork: AudioItemImage?, audioTimePitchAlgorithm: AVAudioTimePitchAlgorithm) {
        pitchAlgorithmType = audioTimePitchAlgorithm
        super.init(audioUrl: audioUrl, artist: artist, title: title, albumTitle: albumTitle, sourceType: sourceType, artwork: artwork)
    }
    
    public func getPitchAlgorithmType() -> AVAudioTimePitchAlgorithm {
        pitchAlgorithmType
    }
}

/// An AudioItem that also conforms to the `InitialTiming`-protocol
class DefaultAudioItemInitialTime: DefaultAudioItem, InitialTiming {
    
    public var initialTime: TimeInterval
    
    public override init(audioUrl: String, artist: String?, title: String?, albumTitle: String?, sourceType: SourceType, artwork: AudioItemImage?) {
        initialTime = 0.0
        super.init(audioUrl: audioUrl, artist: artist, title: title, albumTitle: albumTitle, sourceType: sourceType, artwork: artwork)
    }
    
    public init(audioUrl: String, artist: String?, title: String?, albumTitle: String?, sourceType: SourceType, artwork: AudioItemImage?, initialTime: TimeInterval) {
        self.initialTime = initialTime
        super.init(audioUrl: audioUrl, artist: artist, title: title, albumTitle: albumTitle, sourceType: sourceType, artwork: artwork)
    }
    
    public func getInitialTime() -> TimeInterval {
        initialTime
    }
    
}

/// An AudioItem that also conforms to the `AssetOptionsProviding`-protocol
class DefaultAudioItemAssetOptionsProviding: DefaultAudioItem, AssetOptionsProviding {
    
    public var options: [String: Any]
    
    public override init(audioUrl: String, artist: String?, title: String?, albumTitle: String?, sourceType: SourceType, artwork: AudioItemImage?) {
        options = [:]
        super.init(audioUrl: audioUrl, artist: artist, title: title, albumTitle: albumTitle, sourceType: sourceType, artwork: artwork)
    }
    
    public init(audioUrl: String, artist: String?, title: String?, albumTitle: String?, sourceType: SourceType, artwork: AudioItemImage?, options: [String: Any]) {
        self.options = options
        super.init(audioUrl: audioUrl, artist: artist, title: title, albumTitle: albumTitle, sourceType: sourceType, artwork: artwork)
    }
    
    public func getAssetOptions() -> [String: Any] {
        options
    }
}