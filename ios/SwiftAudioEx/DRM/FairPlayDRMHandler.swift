// FairPlayDRMHandler.swift
// Handles Apple FairPlay Streaming (FPS) DRM for protected content.

import Foundation
import AVFoundation

/// Delegate for receiving DRM-related errors and events.
protocol FairPlayDRMHandlerDelegate: AnyObject {
    func fairPlayDRMHandler(_ handler: FairPlayDRMHandler, didFailWithError error: Error)
}

/// Handles FairPlay Streaming DRM license acquisition and renewal.
class FairPlayDRMHandler: NSObject, AVContentKeySessionDelegate {

    // MARK: - Properties

    public weak var delegate: FairPlayDRMHandlerDelegate?

    private let licenseServerURL: String
    private let certificateURL: String
    private var contentKeySession: AVContentKeySession?
    private var certificateData: Data?

    // Optional extra HTTP headers to include with license requests.
    public var licenseRequestHeaders: [String: String] = [:]

    // MARK: - Init

    public init(licenseServerURL: String, certificateURL: String) {
        self.licenseServerURL = licenseServerURL
        self.certificateURL = certificateURL
        super.init()
    }

    // MARK: - Public API

    /// Attaches this DRM handler to an AVPlayer, intercepting content keys.
    public func attach(to player: AVPlayer) {
        let session = AVContentKeySession(keySystem: .fairPlayStreaming)
        session.setDelegate(self, queue: DispatchQueue.global(qos: .userInteractive))
        self.contentKeySession = session

        if let playerItem = player.currentItem {
            attachRecipient(for: playerItem, session: session)
        }
    }

    /// Attaches this DRM handler to a specific AVPlayerItem.
    public func attach(to playerItem: AVPlayerItem) {
        let session: AVContentKeySession
        if let existing = contentKeySession {
            session = existing
        } else {
            session = AVContentKeySession(keySystem: .fairPlayStreaming)
            session.setDelegate(self, queue: DispatchQueue.global(qos: .userInteractive))
            self.contentKeySession = session
        }
        attachRecipient(for: playerItem, session: session)
    }

    /// Detaches the DRM handler, releasing the content key session.
    public func detach() {
        contentKeySession?.expire()
        contentKeySession = nil
        certificateData = nil
    }

    // MARK: - Certificate Fetching

    private func fetchCertificate() throws -> Data {
        if let cached = certificateData { return cached }
        guard let url = URL(string: certificateURL) else {
            throw FairPlayDRMError.invalidCertificateURL
        }
        let data = try Data(contentsOf: url)
        certificateData = data
        return data
    }

    private func attachRecipient(for playerItem: AVPlayerItem, session: AVContentKeySession) {
        if let asset = playerItem.asset as? AVContentKeyRecipient {
            session.addContentKeyRecipient(asset)
        }
    }

    // MARK: - AVContentKeySessionDelegate

    public func contentKeySession(
        _ session: AVContentKeySession,
        didProvide keyRequest: AVContentKeyRequest
    ) {
        handleKeyRequest(keyRequest)
    }

    public func contentKeySession(
        _ session: AVContentKeySession,
        contentKeyRequest keyRequest: AVContentKeyRequest,
        didFailWithError err: Error
    ) {
        delegate?.fairPlayDRMHandler(self, didFailWithError: err)
    }

    // MARK: - Key Request Handling

    private func handleKeyRequest(_ keyRequest: AVContentKeyRequest) {
        do {
            let certData = try fetchCertificate()

            // For FairPlay, the identifier is the key URI from the HLS manifest.
            // Extract the content identifier bytes from the skd:// URI.
            let contentIdentifier: Data
            if let urlString = keyRequest.identifier as? String,
               let url = URL(string: urlString),
               let host = url.host,
               let data = host.data(using: .utf8) {
                contentIdentifier = data
            } else if let strIdentifier = keyRequest.identifier as? String,
                      let data = strIdentifier.data(using: .utf8) {
                contentIdentifier = data
            } else {
                throw FairPlayDRMError.invalidContentIdentifier
            }

            keyRequest.makeStreamingContentKeyRequestData(
                forApp: certData,
                contentIdentifier: contentIdentifier,
                options: nil
            ) { [weak self] requestData, error in
                guard let self else { return }
                if let error {
                    keyRequest.processContentKeyResponseError(error)
                    self.delegate?.fairPlayDRMHandler(self, didFailWithError: error)
                    return
                }
                guard let requestData else {
                    let err = FairPlayDRMError.emptyLicenseResponse
                    keyRequest.processContentKeyResponseError(err)
                    self.delegate?.fairPlayDRMHandler(self, didFailWithError: err)
                    return
                }
                self.sendLicenseRequest(requestData: requestData, keyRequest: keyRequest)
            }
        } catch {
            keyRequest.processContentKeyResponseError(error)
            delegate?.fairPlayDRMHandler(self, didFailWithError: error)
        }
    }

    // MARK: - License Request

    private func sendLicenseRequest(requestData: Data, keyRequest: AVContentKeyRequest) {
        guard let url = URL(string: licenseServerURL) else {
            keyRequest.processContentKeyResponseError(FairPlayDRMError.invalidLicenseServerURL)
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.httpBody = requestData
        request.setValue("application/octet-stream", forHTTPHeaderField: "Content-Type")
        for (key, value) in licenseRequestHeaders {
            request.setValue(value, forHTTPHeaderField: key)
        }

        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            guard let self else { return }
            if let error {
                keyRequest.processContentKeyResponseError(error)
                self.delegate?.fairPlayDRMHandler(self, didFailWithError: error)
                return
            }
            guard let data else {
                let err = FairPlayDRMError.emptyLicenseResponse
                keyRequest.processContentKeyResponseError(err)
                self.delegate?.fairPlayDRMHandler(self, didFailWithError: err)
                return
            }
            let keyResponse = AVContentKeyResponse(fairPlayStreamingKeyResponseData: data)
            keyRequest.processContentKeyResponse(keyResponse)
        }.resume()
    }
}

// MARK: - Errors

enum FairPlayDRMError: LocalizedError {
    case invalidCertificateURL
    case invalidLicenseServerURL
    case invalidContentIdentifier
    case emptyLicenseResponse

    public var errorDescription: String? {
        switch self {
        case .invalidCertificateURL: return "Invalid FairPlay certificate URL"
        case .invalidLicenseServerURL: return "Invalid FairPlay license server URL"
        case .invalidContentIdentifier: return "Could not extract FairPlay content identifier from key request"
        case .emptyLicenseResponse: return "FairPlay license server returned empty response"
        }
    }
}
