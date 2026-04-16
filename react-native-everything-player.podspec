require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

load 'nitrogen/generated/ios/EverythingPlayer+autolinking.rb'

Pod::Spec.new do |s|
  s.name         = "react-native-everything-player"
  s.module_name  = "EverythingPlayer"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = "https://github.com/Illusion137/react-native-everything-player"
  s.license      = package["license"]
  s.authors      = "Sumi!"

  s.platforms    = { :ios => "13.4" }
  s.source       = { :git => "https://github.com/Illusion137/react-native-everything-player.git", :tag => "v#{s.version}" }

  s.source_files = [
    "ios/**/*.{h,m,mm,swift}",
    "ios/SwiftAudioEx/Sources/Copus/**/*.{c,h}",
  ]
  s.swift_version = "5.9"
  s.public_header_files = "ios/SwiftAudioEx/Sources/Copus/include/*.h"
  s.pod_target_xcconfig = {
    "HEADER_SEARCH_PATHS" => [
      "$(PODS_TARGET_SRCROOT)/ios/SwiftAudioEx/Sources/Copus",
      "$(PODS_TARGET_SRCROOT)/ios/SwiftAudioEx/Sources/Copus/celt",
      "$(PODS_TARGET_SRCROOT)/ios/SwiftAudioEx/Sources/Copus/silk",
      "$(PODS_TARGET_SRCROOT)/ios/SwiftAudioEx/Sources/Copus/silk/float",
    ].join(" "),
    "GCC_PREPROCESSOR_DEFINITIONS" => "$(inherited) OPUS_BUILD VAR_ARRAYS=1 FLOATING_POINT HAVE_LRINT=1 HAVE_LRINTF=1",
    "OTHER_CFLAGS" => "$(inherited) -w -Xanalyzer -analyzer-disable-checker"
  }

  # Required for SwiftAudioEx SABR (protocol buffers)
  s.dependency "SwiftProtobuf", "~> 1.27"

  # Add all nitrogen-generated Nitro Modules files (adds NitroModules dep automatically)
  add_nitrogen_files(s)

  current_pod_target_xcconfig = s.attributes_hash['pod_target_xcconfig'] || {}
  s.pod_target_xcconfig = current_pod_target_xcconfig.merge({
    "SWIFT_INSTALL_OBJC_HEADER" => "YES",
    "PRODUCT_MODULE_NAME" => "EverythingPlayer",
  })
end
