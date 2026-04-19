import AVFoundation
import NitroModules
import UIKit

// MARK: - VideoUIView

/**
 * The actual `UIView` that renders video content.
 * Owned by `HybridVideoView` and returned as `view`.
 */
final class VideoUIView: UIView {

    // MARK: - Subviews

    private let thumbnailView: UIImageView = {
        let iv = UIImageView()
        iv.contentMode = .scaleAspectFit
        iv.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        iv.backgroundColor = .black
        return iv
    }()

    private var playerLayer: AVPlayerLayer?
    private var readyForDisplayObservation: NSKeyValueObservation?

    // MARK: - Init

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .black
        addSubview(thumbnailView)
        thumbnailView.frame = bounds
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError() }

    // MARK: - Layout

    override func layoutSubviews() {
        super.layoutSubviews()
        thumbnailView.frame = bounds
        playerLayer?.frame = bounds
    }

    // MARK: - Player attachment

    func setAVPlayer(_ player: AVPlayer?, gravity: AVLayerVideoGravity) {
        readyForDisplayObservation?.invalidate()
        readyForDisplayObservation = nil
        playerLayer?.removeFromSuperlayer()
        playerLayer = nil

        if let player = player {
            let layer = AVPlayerLayer(player: player)
            layer.frame = bounds
            layer.videoGravity = gravity
            layer.backgroundColor = UIColor.black.cgColor
            self.layer.insertSublayer(layer, above: thumbnailView.layer)
            playerLayer = layer

            // Keep thumbnail visible until the first video frame is ready.
            if layer.isReadyForDisplay {
                thumbnailView.isHidden = true
            } else {
                thumbnailView.isHidden = false
                readyForDisplayObservation = layer.observe(\.isReadyForDisplay, options: [.new]) { [weak self] observedLayer, _ in
                    guard observedLayer.isReadyForDisplay else { return }
                    DispatchQueue.main.async {
                        self?.thumbnailView.isHidden = true
                        self?.readyForDisplayObservation?.invalidate()
                        self?.readyForDisplayObservation = nil
                    }
                }
            }
        } else {
            thumbnailView.isHidden = false
        }
    }

    func setVideoGravity(_ gravity: AVLayerVideoGravity) {
        playerLayer?.videoGravity = gravity
    }

    func setThumbnail(_ image: UIImage?) {
        thumbnailView.image = image
    }

    func clearVideo() {
        readyForDisplayObservation?.invalidate()
        readyForDisplayObservation = nil
        playerLayer?.removeFromSuperlayer()
        playerLayer = nil
        thumbnailView.isHidden = false
    }
}

// MARK: - HybridVideoView

/**
 * Nitro `HybridView` implementation for `NativeVideoView`.
 *
 * Auto-connects to `HybridEverythingPlayer.shared` when `onAttach()` is called
 * (which happens via the `hybridRef` prop in `VideoView.tsx`).
 */
final class HybridVideoView: HybridNativeVideoViewSpec_base, HybridNativeVideoViewSpec_protocol {

    // MARK: - HybridView

    typealias ViewType = VideoUIView
    let view = VideoUIView()

    // MARK: - Props

    var resizeMode: String = "contain" {
        didSet {
            DispatchQueue.main.async { [weak self] in
                guard let self else { return }
                self.view.setVideoGravity(self.gravity(for: self.resizeMode))
            }
        }
    }

    // MARK: - State

    /// The SABR video player (separate from the audio SabrOpusPlayer).
    private var sabrVideoPlayer: AVPlayer?
    private var sabrResourceLoader: SabrVideoResourceLoader?

    // MARK: - Methods (called from JS via hybridRef)

    func onAttach() throws {
        HybridEverythingPlayer.shared?.videoViewDidAttach(self)
    }

    func onDetach() throws {
        HybridEverythingPlayer.shared?.videoViewDidDetach(self)
    }

    // MARK: - Called by HybridEverythingPlayer

    /// Connect a plain `AVPlayer` (non-SABR HLS/DASH/progressive) to the view.
    func connectAVPlayer(_ player: AVPlayer) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.sabrVideoPlayer = nil
            self.sabrResourceLoader?.cancel()
            self.sabrResourceLoader = nil
            self.view.setAVPlayer(player, gravity: self.gravity(for: self.resizeMode))
        }
    }

    /// Start rendering SABR video from the given async stream.
    func connectSabrVideoStream(_ videoStream: AsyncThrowingStream<Data, Error>, playWhenReady: Bool) {
        let resourceLoader = SabrVideoResourceLoader()
        resourceLoader.attach(videoStream: videoStream)

        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.sabrResourceLoader?.cancel()
            self.sabrResourceLoader = resourceLoader

            let asset = AVURLAsset(url: SabrVideoResourceLoader.makeURL())
            asset.resourceLoader.setDelegate(resourceLoader, queue: .main)

            let item = AVPlayerItem(asset: asset)
            let sabrPlayer = AVPlayer(playerItem: item)
            sabrPlayer.automaticallyWaitsToMinimizeStalling = true
            self.sabrVideoPlayer = sabrPlayer

            self.view.setAVPlayer(sabrPlayer, gravity: self.gravity(for: self.resizeMode))
            if playWhenReady {
                sabrPlayer.play()
            } else {
                sabrPlayer.pause()
            }
        }
    }

    func setSabrPlaybackState(playWhenReady: Bool) {
        DispatchQueue.main.async { [weak self] in
            guard let player = self?.sabrVideoPlayer else { return }
            if playWhenReady {
                player.play()
            } else {
                player.pause()
            }
        }
    }

    /// Show the track artwork thumbnail (used for audio-only tracks).
    func showThumbnail(image: UIImage?) {
        DispatchQueue.main.async { [weak self] in
            self?.view.setThumbnail(image)
        }
    }

    /// Remove any active video and show the thumbnail.
    func clearVideo() {
        sabrVideoPlayer?.pause()
        sabrVideoPlayer = nil
        sabrResourceLoader?.cancel()
        sabrResourceLoader = nil
        DispatchQueue.main.async { [weak self] in
            self?.view.clearVideo()
        }
    }

    // MARK: - HybridView lifecycle

    func onDropView() {
        clearVideo()
        HybridEverythingPlayer.shared?.videoViewDidDetach(self)
    }

    // MARK: - Helpers

    private func gravity(for mode: String) -> AVLayerVideoGravity {
        switch mode {
        case "cover": return .resizeAspectFill
        case "fill":  return .resize
        default:      return .resizeAspect
        }
    }
}
