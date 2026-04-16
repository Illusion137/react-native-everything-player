// PreloadManager.swift
// Preloads upcoming AudioItem assets so playback can begin instantly.

import Foundation
import AVFoundation

/// Manages preloading of upcoming audio items to reduce playback latency.
class PreloadManager {

    public static let shared = PreloadManager()

    private var preloadedAssets: [String: AVURLAsset] = [:]
    private var preloadedKeys = ["duration", "tracks", "playable"]
    private let lock = NSLock()

    private init() {}

    /// Preloads the asset for the given AudioItem so it is ready when needed.
    /// - Parameter item: The AudioItem to preload.
    public func preload(item: AudioItem) {
        let urlString = item.getSourceUrl()
        lock.lock()
        let alreadyLoaded = preloadedAssets[urlString] != nil
        lock.unlock()
        guard !alreadyLoaded else { return }

        guard let url = resolveURL(from: urlString) else { return }
        let asset = AVURLAsset(url: url)
        lock.lock()
        preloadedAssets[urlString] = asset
        lock.unlock()

        asset.loadValuesAsynchronously(forKeys: preloadedKeys) { [weak self] in
            guard let self else { return }
            for key in self.preloadedKeys {
                var error: NSError?
                let status = asset.statusOfValue(forKey: key, error: &error)
                if status == .failed {
                    self.lock.lock()
                    self.preloadedAssets.removeValue(forKey: urlString)
                    self.lock.unlock()
                    return
                }
            }
        }
    }

    /// Returns a preloaded AVURLAsset for the given URL string, if available.
    public func asset(for urlString: String) -> AVURLAsset? {
        lock.lock()
        defer { lock.unlock() }
        return preloadedAssets[urlString]
    }

    /// Removes the cached asset for the given URL string.
    public func evict(urlString: String) {
        lock.lock()
        preloadedAssets.removeValue(forKey: urlString)
        lock.unlock()
    }

    /// Clears all preloaded assets.
    public func evictAll() {
        lock.lock()
        preloadedAssets.removeAll()
        lock.unlock()
    }

    private func resolveURL(from string: String) -> URL? {
        if string.hasPrefix("http://") || string.hasPrefix("https://") {
            return URL(string: string)
        }
        return URL(fileURLWithPath: string)
    }
}
