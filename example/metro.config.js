const { getDefaultConfig } = require('expo/metro-config')
const path = require('path')

const projectRoot = __dirname
const libraryRoot = path.resolve(__dirname, '..')
const config = getDefaultConfig(projectRoot)
const appNodeModules = path.resolve(projectRoot, 'node_modules')
const libraryNodeModules = path.resolve(libraryRoot, 'node_modules')

// Watch the symlinked library root so Metro sees changes to the package source
config.watchFolders = [...(config.watchFolders ?? []), libraryRoot]

// Prevent the library's own copies of React/RN/nitro from being double-bundled
const escapeRegex = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
const existingBlockList = config.resolver.blockList ?? []
const blockListBase = Array.isArray(existingBlockList)
  ? existingBlockList
  : [existingBlockList]

config.resolver.blockList = [
  ...blockListBase,
  /(\/lib-origin\/roze\/native\/.+?\/.+?\.node\.ts)$/,
  new RegExp(`${escapeRegex(libraryNodeModules)}/react/.*`),
  new RegExp(`${escapeRegex(libraryNodeModules)}/react-native/.*`),
  new RegExp(`${escapeRegex(libraryNodeModules)}/react-native-nitro-modules/.*`),
]

// Prefer the app's copies of these shared packages to avoid duplicate instances
config.resolver.extraNodeModules = {
  ...config.resolver.extraNodeModules,
  react: path.resolve(appNodeModules, 'react'),
  'react-native': path.resolve(appNodeModules, 'react-native'),
  'react-native-nitro-modules': path.resolve(appNodeModules, 'react-native-nitro-modules'),
}

module.exports = config
