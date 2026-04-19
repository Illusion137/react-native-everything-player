const { getDefaultConfig } = require("expo/metro-config");
const path = require("path");

const libraryRoot = path.resolve(__dirname, "..");
const libOriginRoot = path.resolve(__dirname, "../../Illusi/lib-origin");
const config = getDefaultConfig(__dirname);

// Watch the library source so Metro resolves it from disk.
config.watchFolders = [libraryRoot, libOriginRoot];

// Force all react-native and nitro-modules imports to resolve to the
// example's top-level copies, preventing duplicate module instances.
config.resolver.extraNodeModules = {
    "react-native": path.resolve(__dirname, "node_modules/react-native"),
    "react": path.resolve(__dirname, "node_modules/react"),
    "react-native-nitro-modules": path.resolve(__dirname, "node_modules/react-native-nitro-modules"),
    "@origin": path.resolve(libOriginRoot, "origin/src"),
    "@native": path.resolve(libOriginRoot, "roze/native"),
    "@common": path.resolve(libOriginRoot, "common"),
};

// Block react-native from being resolved from the library root's node_modules
// or from any nested node_modules inside example/node_modules/*/node_modules.
// This prevents duplicate module instances when Metro watches the library root.
//
// The existing blockList is an array of RegExps — spread it flat, NOT wrapped
// in another array (wrapping breaks Metro's flags-equality check in combine()).
const existingBlockList = config.resolver.blockList ?? [];
const blockListBase = Array.isArray(existingBlockList) ? existingBlockList : [existingBlockList];

function escapeRegex(str) {
    return str.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

const libraryNodeModules = path.resolve(libraryRoot, "node_modules");
const exampleNodeModules = path.resolve(__dirname, "node_modules");

config.resolver.blockList = [
    ...blockListBase,
    // Block react-native inside the library root's own node_modules
    new RegExp(`${escapeRegex(libraryNodeModules)}\\/react-native\\/.*`),
    new RegExp(`${escapeRegex(libraryNodeModules)}\\/react-native-nitro-modules\\/.*`),
    // Block nested react-native inside any package within example/node_modules
    new RegExp(`${escapeRegex(exampleNodeModules)}\\/(?!react-native\\/).*\\/node_modules\\/react-native\\/.*`),
    new RegExp(`${escapeRegex(exampleNodeModules)}\\/(?!react-native-nitro-modules\\/).*\\/node_modules\\/react-native-nitro-modules\\/.*`),
];

module.exports = config;
