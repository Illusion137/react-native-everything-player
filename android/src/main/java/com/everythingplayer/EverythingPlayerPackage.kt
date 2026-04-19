package com.everythingplayer

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager
import com.margelo.nitro.com.everythingplayer.EverythingPlayerOnLoad
import com.margelo.nitro.com.everythingplayer.views.HybridNativeVideoViewManager

class EverythingPlayerPackage : ReactPackage {
    init {
        // In bridgeless/new-arch startup, JS can request Nitro hybrid objects very early.
        // Load native library at package construction time to avoid registration races.
        EverythingPlayerOnLoad.initializeNative()
    }

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        // Keep explicit initialization here as a safe fallback (idempotent).
        EverythingPlayerOnLoad.initializeNative()
        return emptyList()
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        return listOf(HybridNativeVideoViewManager())
    }
}
