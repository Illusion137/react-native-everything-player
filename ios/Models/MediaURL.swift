import Foundation
import React

struct MediaURL {
    let value: URL
    let isLocal: Bool

    init?(object: Any?) {
        guard let object = object else { return nil }

        if let localObject = object as? [String: Any] {
            var urlString = localObject["uri"] as? String ?? localObject["url"] as! String
            if let bundleName = localObject["bundle"] as? String {
                urlString = String(format: "%@.bundle/%@", bundleName, urlString)
            }
            isLocal = urlString.lowercased().hasPrefix("http") ? false : true
            value = RCTConvert.nsurl(urlString)
        } else {
            let urlString = object as! String
            isLocal = urlString.lowercased().hasPrefix("file://")
            value = RCTConvert.nsurl(urlString)
        }
    }
}
