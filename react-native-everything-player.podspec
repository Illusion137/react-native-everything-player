require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

load 'nitrogen/generated/ios/EverythingPlayer+autolinking.rb'

Pod::Spec.new do |s|
  s.name         = "react-native-everything-player"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = "https://github.com/Illusion137/react-native-everything-player"
  s.license      = package["license"]
  s.authors      = "Sumi!"

  s.platforms    = { :ios => "13.4" }
  s.source       = { :git => "https://github.com/Illusion137/react-native-everything-player.git", :tag => "v#{s.version}" }

  s.source_files = [
    "ios/**/*.{h,m,mm,swift}",
  ]
  s.swift_version = "5.9"

  # Required for SwiftAudioEx SABR (protocol buffers)
  s.dependency "SwiftProtobuf", "~> 1.27"

  # Add all nitrogen-generated Nitro Modules files (adds NitroModules dep automatically)
  add_nitrogen_files(s)
end
