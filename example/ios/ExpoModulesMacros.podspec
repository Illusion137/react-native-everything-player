Pod::Spec.new do |s|
  s.name         = 'ExpoModulesMacros'
  s.version      = '1.0.0'
  s.summary      = 'Expo Swift macro compiler-plugin stub for CocoaPods'
  s.description  = 'Provides the ExpoModulesMacros Swift macro tool so that ExpoModulesCore can locate it at build time.'
  s.homepage     = 'https://expo.dev'
  s.license      = { :type => 'MIT' }
  s.author       = { 'Expo' => 'support@expo.io' }
  s.platform     = :ios, '16.4'
  # Source lives in the macros-plugin npm package; the podspec is in ios/ so
  # we reach it via a relative path.
  # Podspec lives in example/ios/ — source files are resolved relative to that directory.
  s.source       = { :path => '.' }
  # ExpoModulesOptimized.swift is a copy of the macro declaration from
  # @expo/expo-modules-macros-plugin. Pure Swift (no SwiftSyntax imports),
  # compiles fine on iOS, and produces the ExpoModulesMacros Swift module that
  # ExpoModulesCore needs for its @_exported import ExpoModulesMacros.
  s.source_files = 'ExpoModulesOptimized.swift'
  # The pre-built macOS compiler-plugin binary — needed by swiftc at macro-expansion
  # time. Keep it from being cleaned up during pod install.
  s.preserve_paths = 'ExpoModulesMacros-tool'
  # Pass the plugin binary to the Swift compiler for macro expansion.
  s.pod_target_xcconfig = {
    'OTHER_SWIFT_FLAGS' => '-load-plugin-executable $(PODS_TARGET_SRCROOT)/ExpoModulesMacros-tool#ExpoModulesMacros'
  }
end
