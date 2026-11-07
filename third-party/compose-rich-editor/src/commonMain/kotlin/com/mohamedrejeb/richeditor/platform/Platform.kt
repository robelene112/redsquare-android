// Modified for Red Square Android (#94): bind the unchanged platform implementation to this Android-only module.
package com.mohamedrejeb.richeditor.platform

internal enum class Platform {
    Android, IOS, Desktop, WebJs, WebWasm;

    val isAndroid: Boolean
        get() = this == Android

    val isIOS: Boolean
        get() = this == IOS

    val isDesktop: Boolean
        get() = this == Desktop

    val isWebJs: Boolean
        get() = this == WebJs

    val isWebWasm: Boolean
        get() = this == WebWasm
}

