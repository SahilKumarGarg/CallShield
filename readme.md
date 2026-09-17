# Call Shield 🛡️
### 100% Offline, Privacy-First Call Screener & Prefix Blocker for Android

Call Shield is a lightweight, zero-telemetry Android application and screening engine that intercepts incoming phone calls at the cellular radio level before the ringer sounds. Powered by Android's native `CallScreeningService`, it gives you complete control over which numbers ring your phone without ever exposing your contacts, phone records, or call metadata to external servers.

---

## 🌟 Key Features

- **🔒 100% Offline & Zero Telemetry**:
  - Requires **ZERO** internet permissions (`android.permission.INTERNET` is intentionally absent).
  - No ads, no third-party trackers, no cloud analytics, and no remote servers.
  - All rules, settings, and screening logs are stored locally on your device in an encrypted Room SQLite database.

- **⚡ Sub-5ms Cellular Radio Interception**:
  - Intercepts incoming calls within **3–5 milliseconds** directly through Android Telecom framework.
  - Terminates unwanted spam calls before the first ringtone vibrates or plays sound.

- **🎯 Flexible Number Screening Modes**:
  - **Prefix Matching**: Block whole series of scam or marketing numbers (e.g. `+1800`, `+44870`, `140`).
  - **Country & Satellite Dialing Codes**: Block entire countries or high-risk satellite scam codes (e.g. `+881`, `+882` Wangiri scams).
  - **Wildcard Matching**: Target suspicious localized spoofing blocks using `*` (any digits) and `?` (single digit), such as `+1415998????`.
  - **Exact Number Match**: Block specific persistent callers with smart telephony normalization (handles country codes, leading zeroes, and local formatting).

- **🔕 Dual Action Controls**:
  - **Reject / Drop**: Immediately terminates the call connection without ringing.
  - **Silence Ringer**: Mutes the audio ringer silently without disconnecting, allowing genuine callers to leave a voicemail.
  - **Call Log & Notification Control**: Choose whether blocked calls are saved to your Android dialer call history or silenced from notification banners.

- **👥 Tap-to-Toggle Saved Contacts Screening**:
  - **The Android Telecom Default**: By default, Android's OS automatically allows calls from numbers in your phonebook to bypass call screeners.
  - **Screen Saved Contacts Toggle**: With a single tap (`✓ Screen Saved Contacts: ON / OFF`), enable or disable screening for contacts. When ON, your rules apply to both unknown numbers and saved contacts. When OFF, family and friends always ring through unobstructed.
  - **100% Private**: Your contacts never leave your device.

- **🔋 Tap-to-Toggle Battery Optimization Exemption**:
  - Prevents Android's aggressive Doze mode and OEM battery savers (MIUI, ColorOS, EMUI) from sleeping the background screening service.
  - Single-tap toggle to exempt Call Shield or manage exemption status anytime.

- **⏸️ Quick Pause Protection**:
  - Temporarily suspend all screening with a single tap (e.g. for 15 minutes, 1 hour, or until manually resumed).
  - Essential when expecting immediate courier deliveries, one-time taxi calls, or bank OTP confirmations.

- **📋 Real-Time On-Device Activity Log**:
  - Inspect every call evaluation in real-time.
  - View execution duration in milliseconds, matched rule name, timestamp, and blocking reason.
  - Search and filter call history; clear or export audit logs at any time.

- **💾 1-Click Backup & Restore**:
  - Export all custom rules and configuration as a clean JSON backup file.
  - Easily transfer your rules when switching devices.

---

## 📱 End-User Usage Instructions

### 1. Initial Setup on Android

To screen calls, Android requires Call Shield to be registered as your **Default Caller ID & Spam App**:

1. Install and launch **Call Shield** on your Android device.
2. Tap the blue **"Set as Default Call Screener"** button on the main screen.
3. In the system dialog that appears, select **Call Shield** and tap **Set as default**.
   *(If prompted later, you can also navigate to: **Android Settings > Apps > Default Apps > Caller ID & Spam app > Call Shield**).*

### 2. Enable Battery Optimization Exemption

To guarantee the screening service responds in under 5 milliseconds even when your screen is locked:
- Tap the **"⚡ Battery Whitelist"** button.
- Tap **Allow** on the Android system prompt to exempt Call Shield from aggressive battery saving.
- You can disable this exemption anytime with a single tap directly from the dashboard.

### 3. Screening Calls from Saved Contacts (Optional)

- **Default State (OFF)**: Any number saved in your phone's address book will ring through normally, completely bypassing blocking rules.
- **To Block a Saved Contact**:
  1. Tap **"👥 Screen Saved Contacts"** on the dashboard.
  2. Grant the read-only Contacts permission when prompted by Android.
  3. The status will update to **"✓ Screen Saved Contacts: ON"**.
  4. Now, any rule matching a saved contact will be blocked or silenced.
  5. Tap the button again at any time to instantly switch it back to **OFF**.

### 4. Creating a Blocking Rule

1. Tap the **"+ Add Rule"** button.
2. Enter a descriptive name (e.g., *Toll-Free Telemarketers* or *Unknown Country Calls*).
3. Choose the **Match Type**:
   - **Prefix**: Enter the starting digits (e.g., `+1800` or `140`).
   - **Country Code**: Select or enter international dial codes (e.g., `+881`).
   - **Wildcard**: Use `*` or `?` for flexible range blocking (e.g., `+1415998????`).
   - **Exact Number**: Enter a full 10-digit or international telephone number.
4. Select the **Action**:
   - **Reject / Drop Call**: Call is dropped immediately.
   - **Silence Ringer**: Phone does not ring or vibrate; caller may leave voicemail.
5. Choose your preferences for **"Hide from System Call Log"** and **"Silence Notification"**.
6. Tap **"Save Rule"**. The rule takes effect immediately.

### 5. Pausing Protection Temporarily

Expecting an urgent delivery or verification call?
- Tap the **"Pause Protection"** button at the top of the dashboard.
- Select **15 minutes**, **1 hour**, or **Indefinitely**.
- All calls will ring through without screening until the pause timer expires or you tap **"Resume Protection"**.

### 6. Inspecting the Activity Log

- Open the **Activity Log** tab to see incoming call events.
- Each event displays:
  - Caller number and location.
  - Action taken (**BLOCKED**, **SILENCED**, or **ALLOWED**).
  - Exact rule matched and decision reason.
  - Execution time (typically 2–4ms).
- Tap **Clear History** to purge old local logs.

---

## 💻 Web Dashboard & Live Call Simulator

Call Shield includes an interactive web dashboard and call simulator to test rule behavior:

1. **Pre-Loaded Scenarios**:
   - Test predefined spam profiles like *Toll-Free Telemarketers*, *Wangiri Satellite Scam*, *Local Spoofed Exchange*, and *Saved Phone Contact*.
2. **Interactive Phone Tester**:
   - Type any custom number, toggle whether it is saved in your contacts, and click **"Simulate Incoming Call"**.
   - Watch the animated incoming call visualizer display real-time rule matching, execution latency, and carrier-grade action.
3. **Backup / Restore**:
   - Access **Settings > Backup & Restore** to download a portable JSON file of your rules.

---

## 🔒 Security & Privacy Architecture

Call Shield is designed according to **OWASP Mobile Application Security (MASVS)** and Google Play Developer Policies:

| Security Vector | Risk in Generic Call Blockers | Call Shield Hardening |
|---|---|---|
| **Data Privacy** | Contacts & logs uploaded to third-party servers | **ZERO Internet permission**. 100% on-device SQLite storage. |
| **Service Hijacking (CWE-280)** | Unauthorized apps invoking internal receiver | Enforced `android.permission.BIND_SCREENING_SERVICE`. Only Android OS (UID 1000) can bind. |
| **Binder ANR & Freezes (CWE-1333)** | Dangerous regex patterns causing catastrophic backtracking | Linear O(N) prefix searches and bounded 32-character pattern evaluation. Immune to ReDoS. |
| **OEM Battery Throttling** | Xiaomi / Samsung / Oppo killing background process | Built-in Doze whitelist helper with fallback intent routing. |

---

## 🛠️ Developer & Build Guide

### Prerequisites
- **Android Studio** (Hedgehog 2023.1.1 or newer)
- **JDK**: 17
- **Target SDK**: Android 14 (API level 34)
- **Minimum SDK**: Android 10 (API level 29)

### Building Signed APK / AAB
1. Open Android Studio.
2. Select **Open** and point to the `android/` directory.
3. Sync project with Gradle files.
4. Go to **Build > Generate Signed Bundle / APK...**
5. Select **Android App Bundle (.aab)** for Google Play, or **APK** for direct sideloading.
6. Select your signing keystore, set build variant to **Release**, and build.

---

## 📄 License & Attribution

- **License**: Free & Open Source Project
- **Created with ❤️ & security in mind by**: **Sahil Kumar**
- **LinkedIn**: [linkedin.com/in/sahilkumargarg](https://www.linkedin.com/in/sahilkumargarg/)
- **GitHub**: [github.com/SahilKumarGarg](https://github.com/SahilKumarGarg)
- **Support / Donations**: [PayPal](https://paypal.me/sahilkumargarg) | UPI: `sahilgarg50@oksbi`
