//
//  AudioPlayerError.swift
//  SwiftAudio
//
//  Created by Jørgen Henrichsen on 25/03/2018.
//

import Foundation


enum AudioPlayerError: Error {

    public enum PlaybackError: Error {
        case failedToLoadKeyValue
        case invalidSourceUrl(String)
        case notConnectedToInternet
        case playbackFailed
        case itemWasUnplayable
    }

    public enum QueueError: Error {
        case noCurrentItem
        case invalidIndex(index: Int, message: String)
        case empty
    }
}

extension AudioPlayerError.PlaybackError: LocalizedError {
    var errorDescription: String? {
        switch self {
        case .failedToLoadKeyValue:
            return "Failed to load the media resource metadata required for playback."
        case .invalidSourceUrl(let source):
            return "The track URL is invalid or unsupported: \(source)"
        case .notConnectedToInternet:
            return "A network source was requested, but no internet connection is available."
        case .playbackFailed:
            return "Playback failed while preparing or rendering the media item."
        case .itemWasUnplayable:
            return "The media item is not playable by AVPlayer."
        }
    }
}
