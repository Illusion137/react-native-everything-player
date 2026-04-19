import Foundation
import MediaPlayer
import AVFoundation

class Track: AudioItem, TimePitching, AssetOptionsProviding, Trimmable {
    let url: MediaURL

    @objc var title: String?
    @objc var artist: String?

    var date: String?
    var desc: String?
    var genre: String?
    var duration: Double?
    var artworkURL: MediaURL?
    let headers: [String: Any]?
    var userAgent: String?
    let pitchAlgorithm: String?
    var isLiveStream: Bool?
    var album: String?
    var artwork: MPMediaItemArtwork?

    // Track trimming
    var startTime: Double?
    var endTime: Double?

    // DRM
    var drmType: String?
    var drmLicenseServer: String?
    var drmCertificateUrl: String?
    var drmHeaders: [String: String]?

    // SABR streaming params (YouTube server-adaptive bitrate)
    var isOpus: Bool?
    var isSabr: Bool?
    var sabrServerUrl: String?
    var sabrUstreamerConfig: String?
    var sabrFormats: [[String: Any]]?
    var poToken: String?
    var clientInfo: [String: Any]?
    var cookie: String?

    private var originalObject: [String: Any] = [:]

    init?(dictionary: [String: Any]) {
        guard let url = MediaURL(object: dictionary["url"]) else { return nil }
        self.url = url
        self.headers = dictionary["headers"] as? [String: Any]
        self.userAgent = dictionary["userAgent"] as? String
        self.pitchAlgorithm = dictionary["pitchAlgorithm"] as? String
        self.startTime = dictionary["startTime"] as? Double
        self.endTime = dictionary["endTime"] as? Double
        self.drmType = dictionary["drmType"] as? String
        self.drmLicenseServer = dictionary["drmLicenseServer"] as? String
        self.drmCertificateUrl = dictionary["drmCertificateUrl"] as? String
        self.drmHeaders = dictionary["drmHeaders"] as? [String: String]
        self.isOpus = dictionary["isOpus"] as? Bool
        self.isSabr = dictionary["isSabr"] as? Bool
        self.sabrServerUrl = dictionary["sabrServerUrl"] as? String
        self.sabrUstreamerConfig = dictionary["sabrUstreamerConfig"] as? String
        self.sabrFormats = dictionary["sabrFormats"] as? [[String: Any]]
        self.poToken = dictionary["poToken"] as? String
        self.clientInfo = dictionary["clientInfo"] as? [String: Any]
        self.cookie = dictionary["cookie"] as? String
        updateMetadata(dictionary: dictionary)
    }

    // MARK: - Public Interface

    func toObject() -> [String: Any] {
        return originalObject
    }

    func updateMetadata(dictionary: [String: Any]) {
        self.title = (dictionary["title"] as? String) ?? self.title
        self.artist = (dictionary["artist"] as? String) ?? self.artist
        self.date = dictionary["date"] as? String
        self.album = dictionary["album"] as? String
        self.genre = dictionary["genre"] as? String
        self.desc = dictionary["description"] as? String
        self.duration = dictionary["duration"] as? Double
        self.artworkURL = MediaURL(object: dictionary["artwork"])
        self.isLiveStream = dictionary["isLiveStream"] as? Bool
        self.originalObject = self.originalObject.merging(dictionary) { (_, new) in new }
    }

    // MARK: - AudioItem Protocol

    func getSourceUrl() -> String {
        return url.isLocal ? url.value.path : url.value.absoluteString
    }

    func getArtist() -> String? { return artist }
    func getTitle() -> String? { return title }
    func getAlbumTitle() -> String? { return album }

    func getSourceType() -> SourceType {
        return url.isLocal ? .file : .stream
    }

    func getArtwork(_ handler: @escaping (UIImage?) -> Void) {
        if let artworkURL = artworkURL?.value {
            if self.artworkURL?.isLocal ?? false {
                let image = UIImage(contentsOfFile: artworkURL.path)
                handler(image)
            } else {
                URLSession.shared.dataTask(with: artworkURL) { (data, _, error) in
                    if let data = data, let artwork = UIImage(data: data), error == nil {
                        handler(artwork)
                    } else {
                        handler(nil)
                    }
                }.resume()
            }
        } else {
            handler(nil)
        }
    }

    func getDuration() -> Double? { return duration }

    // MARK: - TimePitching Protocol

    func getPitchAlgorithmType() -> AVAudioTimePitchAlgorithm {
        if let pitchAlgorithm = pitchAlgorithm {
            switch pitchAlgorithm {
            case PitchAlgorithm.linear.rawValue: return .varispeed
            case PitchAlgorithm.music.rawValue: return .spectral
            default: return .timeDomain
            }
        }
        return .timeDomain
    }

    // MARK: - AssetOptionsProviding Protocol

    func getAssetOptions() -> [String: Any] {
        var options: [String: Any] = [:]
        if let headers = headers {
            options["AVURLAssetHTTPHeaderFieldsKey"] = headers
        }
        if #available(iOS 16, *) {
            if let userAgent = userAgent {
                options[AVURLAssetHTTPUserAgentKey] = userAgent
            }
        }
        if isOpus == true {
            options["isOpus"] = true
        }
        if isSabr == true, let sabrServerUrl = sabrServerUrl {
            options["isSabr"] = true
            options["sabrServerUrl"] = sabrServerUrl
            options["sabrUstreamerConfig"] = sabrUstreamerConfig ?? ""
            options["sabrFormats"] = sabrFormats ?? []
            options["poToken"] = poToken ?? ""
            if let clientInfo = clientInfo {
                options["clientInfo"] = clientInfo
            }
            if let cookie = cookie {
                options["cookie"] = cookie
            }
        }
        return options
    }

    // MARK: - Trimmable Protocol

    func getStartTime() -> TimeInterval? { return startTime }
    func getEndTime() -> TimeInterval? { return endTime }
}
