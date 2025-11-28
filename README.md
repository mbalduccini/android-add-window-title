# Android Window Title Caption Utility

This repo contains a small utility (`captionlib`) that lets you set a custom title inside the Android desktop caption bar (freeform/DeX/desktop) and optionally listen to inset/rect info. It also includes demo apps that show how to use it.

## Modules
- `captionlib`: reusable library with the `CaptionBarUtils` helper.
- `debugdemo`: debug/demo app showing insets/rects; includes optional debug listener usage.
- `simpledemo`: minimal “Hello World” layout with a fixed title.
- `titledemo`: button that changes the caption title from “Original Title” to “Modified Title”.

## Add to your project
1. Copy the `captionlib` module into your project (or add this repo as a submodule).
2. In your `settings.gradle`:
   ```kotlin
   include(":captionlib")
   ```
3. In your app module `build.gradle` dependencies:
   ```groovy
   implementation project(":captionlib")
   implementation "androidx.core:core-ktx:1.13.1"
   implementation "androidx.appcompat:appcompat:1.6.1"
   ```
4. Sync/build with `compileSdk/targetSdk` 35 (minSdk 26 in the demos).

## Usage
In your `Activity`:
```kotlin
import net.asklab.caption.CaptionBarUtils

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val binding = CaptionBarUtils.setWindowTitle(
        window = window,
        titleText = "My Window Title",
        captionColor = getColor(android.R.color.white),   // background + box color
        titleTextColor = getColor(android.R.color.black), // text color
    )

    // Later, change the title dynamically:
    binding.setTitle("Updated Title")

    // (Optional) receive inset/rect info for debugging:
    // binding.addDebugListener { info -> /* use info.captionRects, etc. */ }
}
```

Notes:
- `setWindowTitle` automatically applies a transparent caption bar (Android 15+).
- `captionColor` is used for both the bar background and the title box; omit to use the default white.
- The library creates and overlays the caption header view for you; no extra layout wiring is needed.

## Demos
- `simpledemo`: centered circle + “Hello World!” with a caption title.
- `titledemo`: button that changes the caption title from “Original Title” to “Modified Title”.

Build all demos: `./gradlew assembleDebug` (APK outputs under each module’s `build/outputs/apk`).
