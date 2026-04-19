import AVFoundation
import Foundation
import UniformTypeIdentifiers

/// Custom URL scheme used to intercept AVPlayer requests for SABR video data.
private let kSabrVideoScheme = "sabrstream"

/**
 * `AVAssetResourceLoaderDelegate` that feeds SABR video data (fMP4) to AVPlayer.
 *
 * AVPlayer issues range-based load requests via the resource loader.  This class
 * buffers incoming chunks from the `AsyncThrowingStream<Data, Error>` produced
 * by `SabrStream.video_stream` and satisfies those requests as data arrives.
 */
class SabrVideoResourceLoader: NSObject, AVAssetResourceLoaderDelegate {

    // MARK: - Public

    /// A URL that uses the intercepted scheme.  Pass this to `AVURLAsset`.
    static func makeURL() -> URL {
        return URL(string: "\(kSabrVideoScheme)://sabr-video-stream")!
    }

    // MARK: - Private

    private var buffer = Data()
    private var pendingRequests: [AVAssetResourceLoadingRequest] = []
    private var streamTask: Task<Void, Never>?
    private var isStreamFinished = false
    private let lock = NSLock()

    // MARK: - API

    func attach(videoStream: AsyncThrowingStream<Data, Error>) {
        streamTask?.cancel()
        streamTask = Task(priority: .userInitiated) { [weak self] in
            do {
                for try await chunk in videoStream {
                    guard !Task.isCancelled else { return }
                    self?.lock.lock()
                    self?.buffer.append(chunk)
                    self?.lock.unlock()
                    self?.processPendingRequests()
                }
            } catch {
                NSLog("[SabrVideoResourceLoader] stream error: \(error)")
            }
            guard !Task.isCancelled else { return }
            self?.lock.lock()
            self?.isStreamFinished = true
            self?.lock.unlock()
            self?.processPendingRequests()
        }
    }

    func cancel() {
        streamTask?.cancel()
        streamTask = nil
        lock.lock()
        pendingRequests.forEach { $0.finishLoading() }
        pendingRequests.removeAll()
        lock.unlock()
    }

    // MARK: - AVAssetResourceLoaderDelegate

    func resourceLoader(
        _ resourceLoader: AVAssetResourceLoader,
        shouldWaitForLoadingOfRequestedResource loadingRequest: AVAssetResourceLoadingRequest
    ) -> Bool {
        // Only handle our custom scheme
        guard loadingRequest.request.url?.scheme == kSabrVideoScheme else { return false }

        lock.lock()
        pendingRequests.append(loadingRequest)
        lock.unlock()
        processPendingRequests()
        return true
    }

    func resourceLoader(
        _ resourceLoader: AVAssetResourceLoader,
        didCancel loadingRequest: AVAssetResourceLoadingRequest
    ) {
        lock.lock()
        pendingRequests.removeAll { $0 === loadingRequest }
        lock.unlock()
    }

    // MARK: - Private helpers

    private func processPendingRequests() {
        lock.lock()
        let snapshot = pendingRequests
        let currentBuffer = buffer
        let finished = isStreamFinished
        lock.unlock()

        var completed: [AVAssetResourceLoadingRequest] = []

        for request in snapshot {
            if fillLoadingRequest(request, from: currentBuffer, streamFinished: finished) {
                completed.append(request)
            }
        }

        if !completed.isEmpty {
            lock.lock()
            for r in completed {
                pendingRequests.removeAll { $0 === r }
            }
            lock.unlock()
        }
    }

    /// Returns `true` if the request was fully satisfied and should be removed.
    private func fillLoadingRequest(
        _ request: AVAssetResourceLoadingRequest,
        from buffer: Data,
        streamFinished: Bool
    ) -> Bool {
        // Fill content information (MIME type, content length) on first request
        if let info = request.contentInformationRequest {
            info.contentType = UTType.mpeg4Movie.identifier
            // Streaming fMP4: byte-range access is not supported (data arrives sequentially).
            // Keep content length updated as the buffer grows.
            info.isByteRangeAccessSupported = false
            info.contentLength = Int64(buffer.count)
        }

        guard let dataRequest = request.dataRequest else {
            request.finishLoading()
            return true
        }

        let requestedOffset = Int(dataRequest.currentOffset)
        let currentLength = buffer.count

        guard currentLength > requestedOffset else {
            // Not enough data yet
            if streamFinished {
                request.finishLoading(with: NSError(
                    domain: AVFoundationErrorDomain,
                    code: AVError.fileFailedToParse.rawValue,
                    userInfo: nil
                ))
                return true
            }
            return false
        }

        let responseEndOffset: Int
        if dataRequest.requestsAllDataToEndOfResource {
            // Apple documents requestedLength as 0 in this mode.
            // Serve everything currently available from currentOffset onward.
            responseEndOffset = currentLength
        } else {
            let requestEndOffset = Int(dataRequest.requestedOffset) + dataRequest.requestedLength
            responseEndOffset = min(requestEndOffset, currentLength)
        }

        if responseEndOffset > requestedOffset {
            let range = requestedOffset..<responseEndOffset
            dataRequest.respond(with: buffer[range])
        }

        let isSatisfied: Bool
        if dataRequest.requestsAllDataToEndOfResource {
            isSatisfied = streamFinished
        } else {
            let requestEndOffset = Int(dataRequest.requestedOffset) + dataRequest.requestedLength
            isSatisfied = currentLength >= requestEndOffset
        }

        if isSatisfied {
            request.finishLoading()
            return true
        }
        return false
    }
}
