# AAOS SystemUI sample RROs

Each sample demonstrates the effect of overriding selected AAOS SystemUI
resources.

## Build and install an RRO

```bash
# Build all sample RROs from an initialized AOSP build environment
mmma <path-to-carsystemui>/samples

# Install one of the generated RRO APKs
adb install <path-to-rro-apk>

# Enable an installed RRO for user 0
adb shell cmd overlay enable --user 0 com.android.systemui.rro.bottom
adb shell cmd overlay enable --user 0 com.android.systemui.rro.bottom.rounded
adb shell cmd overlay enable --user 0 com.android.systemui.rro.right
adb shell cmd overlay enable --user 0 com.android.systemui.rro.left
adb shell cmd overlay enable --user 0 com.android.car.systemui.systembar.transparency.navbar.translucent
adb shell cmd overlay enable --user 0 com.android.car.systemui.systembar.transparency.statusbar.translucent

# To make the system bar persistent, enable this RRO for the system and foreground users
adb shell cmd overlay enable --user 0 com.android.systemui.controls.systembar.insets.rro
adb shell cmd overlay enable --user 10 com.android.systemui.controls.systembar.insets.rro

# Verify the configuration
adb shell dumpsys window | grep mRemoteInsetsControllerControlsSystemBars

# Select the desired system-bar persistence policy
adb shell cmd overlay enable --user 0 com.android.car.systemui.systembar.persistency.immersive_with_nav
adb shell cmd overlay enable --user 0 com.android.car.systemui.systembar.persistency.non_immersive
adb shell cmd overlay enable --user 0 com.android.car.systemui.systembar.persistency.immersive

# Restart SystemUI if necessary
adb shell pkill -TERM -f com.android.systemui
```

Replace user `10` with the ID of the foreground user when it differs. Use
`adb shell am get-current-user` to find the current user ID.
