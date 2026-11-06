# Connection selection for SDK 1.1.0

Treat `APPtoAPP`, `App-to-App`, `APP_TO_APP` and `On-Device` as names for the same-device integration. Use `ConnectionConfig.createOnDeviceMode()` for new SDK 1.1.0 wiring; preserve a deliberately configured `AppToAppMode`. This request already determines the device arrangement, so proceed with project inspection and connection setup.

## Modes and exact factories

```kotlin
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.enums.CableProtocol
import com.sunmi.tapro.taplink.sdk.enums.CrossDeviceStrategy

val onDevice = ConnectionConfig.createOnDeviceMode()
val lan = ConnectionConfig.createCrossDeviceMode(
    CrossDeviceStrategy.LAN, host = terminalHost, port = terminalPort
)
val cable = ConnectionConfig.createCrossDeviceMode(
    CrossDeviceStrategy.CABLE, cableProtocol = CableProtocol.USB_VSP
)
val subScreen = ConnectionConfig.createSubScreenMode()
```

`terminalHost` and `terminalPort` come from the chosen terminal's configuration/discovery. Pass one config to `TaplinkSDK.connect(config, listener)`.

| Deployment | Select | Terminal prerequisites |
|---|---|---|
| POS and Tapro on the same Android device | On-Device (App-to-App underneath) | Tapro installed, configured and initialized |
| Separate POS and terminal on reachable LAN | Cross-Device + LAN | Tapro LAN service enabled, reachable address/advertised port |
| Separate POS and terminal over cable | Cross-Device + explicit protocol | Compatible host/accessory roles, port, cable and Tapro service mode |
| Supported customer-facing display deployment | Sub-Screen | Tapro 1.0.5 branch supports screen control; compatible Sub-Screen hardware/build and VSP service required |

Cable choices are `USB_VSP`, `RS232`, `USB_AOA`. Select the verified hardware protocol. `CableProtocol.AUTO`, `CrossDeviceStrategy.AUTO`, `CrossDeviceStrategy.CLOUD` and `ConnectionMode.CLOUD` are absent from 1.1.0. Cloud payments use a separate backend integration. Cable describes the transport; online authorization still depends on terminal/backend connectivity.

Legacy `createAppMode`, `createLanMode` and `createCableMode(protocol)` remain deprecated compatibility APIs. `createCableMode()` without a protocol does not compile. `createDefault()`, `createHighReliabilityConfig()` and `createFastConnectionConfig()` alone leave the mode unspecified; set the mode before connecting.

## LAN discovery and selection

The SDK offers one-shot discovery and QR helpers:

| Method on TaplinkSDK | Result |
|---|---|
| `discoverLanServices(DiscoveryListener)` | Returns discovered services; app selects and connects |
| `scanLanQrCode(DiscoveryListener)` | Returns the scanned endpoint; app connects |
| `autoDiscoverAndConnect(ConnectionListener)` | Discovers and connects |
| `scanAndConnect(ConnectionListener)` | Scans and connects |

When multiple terminals are present, present the discovery results for deliberate selection; avoid taking `services.first()` as a payment-routing policy.

```kotlin
import com.sunmi.tapro.taplink.sdk.TaplinkSDK
import com.sunmi.tapro.taplink.sdk.callback.DiscoveryListener
import com.sunmi.tapro.taplink.sdk.error.ConnectionError
import com.sunmi.tapro.taplink.sdk.model.DiscoveredService

TaplinkSDK.discoverLanServices(object : DiscoveryListener {
    override fun onDiscovered(services: List<DiscoveredService>) {
        // Switch to main thread to show selection. On selection:
        // ConnectionConfig.createCrossDeviceMode(CrossDeviceStrategy.LAN, chosen.host, chosen.port)
        // Persist the chosen endpoint and verified terminal identity with pending payments.
    }
    override fun onError(error: ConnectionError) {
        // Switch to main thread to show the actual discovery error.
    }
})
```

mDNS requires a network allowing discovery traffic. Manual/scanned endpoint connection remains useful where multicast is filtered. The QR content is `lan://host/port`. Use the actual advertised port, including after network/service changes. Never silently route recovery to another discovered terminal.

Bind identity before the first payment: after connection, call `getTerminalInfo()` and compare `info.terminal.sn` with the terminal selected/provisioned by the merchant. For first enrollment, have the operator verify the physical terminal serial and persist the accepted SN, merchant context and endpoint. For a pending attempt, reset payment readiness on disconnect; after reconnect, read metadata and compare with that attempt's saved SN/merchant before query or any new payment. Missing metadata or a mismatch keeps the attempt pending and requires correcting the connection/identity. `getTerminalInfo()` is shown in [the protocol reference](../taplink-protocol-reference.md); its success establishes metadata, while transaction permission is validated separately.

QR scanner dependencies in the consuming app:

```kotlin
dependencies {
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.zxing:core:3.5.3")
}
```

The SDK declares CAMERA and opens its scanner Activity, including permission handling. Preserve distinct errors: `E504` cancellation, `E505` permission denied, `E506` no usable camera. Verify the scanner theme and dependencies in the actual app.

## Reconnection and shared terminals

`autoReconnect` defaults to false. Set policy on the selected config:

```kotlin
val connection = ConnectionConfig.createCrossDeviceMode(
    CrossDeviceStrategy.LAN, host = terminalHost, port = terminalPort
).setAutoReconnect(true).setRetryPolicy(maxRetries = 3, delayMs = 3000L)
```

With false, 1.1.0 avoids implicit direct-LAN fallback to mDNS, background LAN monitoring and passive reconnection. Explicit discovery helpers still work. With true, SDK reconnection can follow a discovered endpoint change. Restore the transport, then reconcile pending transactions; never resend a payment from `onConnected`.

The SDK singleton represents the current connection. SDK 1.1.0 provides no per-`terminalSn` connection pool, reservation API or connected-POS ownership query. `getTerminalInfo()` returns metadata for the connected terminal. For several POS apps sharing one terminal, coordinate turns at the POS/application layer and resolve the previous payment before handing off; handle Host busy/rejection responses. A successful connection or metadata read is not an exclusive reservation.

## USB and Sub-Screen

Android USB access is device/accessory permission managed through `UsbManager.hasPermission`/`requestPermission`; it is relevant across supported Android versions. SDK transport code performs those checks. There is no standard runtime permission named `android.permission.USB_HOST` to request. Inspect the merged USB feature declarations and allow the system's device permission prompt. Do not add a competing permission/transport implementation solely for this SDK. [Android USB host documentation](https://developer.android.com/develop/connectivity/usb/host).

Check data-capable cable, OTG/host role, selected protocol, terminal service settings and permission denial before diagnosing a transaction problem. Unattended permission persistence depends on device/firmware provisioning and requires testing.

Sub-Screen reports connected only after VSP and the screen player are ready. Error `351` means screen opening failed; inspect terminal edition/build and VSP settings. `autoOpenUsbScreenPlayer` is retained but ignored for normal modes in this SDK. Use Sub-Screen for automatic opening, or explicitly call `openUsbScreenPlayer(callback)` after connection when the deployment supports it.
