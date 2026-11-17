# Dokker 1.5 dCi (2014) Climate & OBD2 Analyzer (`DokkerAnalyse`)

A native Android application (**Kotlin + Jetpack Compose**) designed specifically for the **Dacia Dokker 1.5 dCi (K9K, 2014 / X67 platform)** to monitor the **A/C Evaporator Temperature Sensor (`Température évaporateur` / `PR003`)** and additional climate/engine parameters over a **Bluetooth Classic (SPP) ELM327 / OBDLink** adapter.

---

## Features

1. **Live A/C Evaporator Hero Card (`Dashboard` Tab)**
   - Large real-time readout in `°C` with session **MIN / MAX** tracking and raw hex display.
   - **Automatic Icing & Fault Diagnostics**:
     - `≤ 1.5 °C`: **ICING / FREEZING RISK** (Red alert — evaporator core is about to freeze solid and block airflow or cut off compressor).
     - `1.5 °C – 10.5 °C`: **OPTIMAL A/C COOLING** (Green — normal operating window).
     - `> 10.5 °C`: **WARM / COMPRESSOR OFF** (Amber — compressor clutch disengaged or low refrigerant).
     - `< -25 °C` or `> 75 °C`: **SENSOR FAULT / OPEN CIRCUIT** (Defective NTC thermistor or wiring break).
   - **Live 60-Point Sparkline Trend Graph** with a red reference line at the icing threshold.
   - **Demo Simulation Mode** so you can preview the dashboard and test custom formulas without sitting in the car.

2. **Pre-Configured Dacia Dokker 1.5 dCi (2014) Sensors (`Sensors / PIDs` Tab)**
   - **AC Evaporator Temp (Climate ECU)**: Header `744`/`764`, Init `10C0`, PID `220003` (`PR003`), Formula `A-40`
   - **AC Evaporator Temp (K9K Engine ECU)**: Header `7E0`/`7E8`, PID `224007`, Formula `A-40`
   - **AC Evaporator Temp (UCH / BCM)**: Header `745`/`765`, Init `10C0`, PID `222006`, Formula `A-40`
   - **AC Refrigerant Pressure**: Header `7E0`/`7E8`, PID `224006`, Formula `A*0.1` (`bar`)
   - **Engine Coolant Temp**: Header `7E0`/`7E8`, PID `0105`, Formula `A-40` (`°C`)
   - **Ambient Outside Air Temp**: Header `7E0`/`7E8`, PID `0146`, Formula `A-40` (`°C`)
   - **Engine Speed (RPM)**: Header `7E0`/`7E8`, PID `010C`, Formula `((A*256)+B)/4` (`rpm`)
   - **System Voltage (12V)**: Header `7E0`/`7E8`, PID `0142`, Formula `((A*256)+B)/1000` (`V`)
   - **K9K Fuel Rail Pressure**: Header `7E0`/`7E8`, PID `222206`, Formula `((A*256)+B)*0.1` (`bar`)
   - **K9K Engine Oil Temp**: Header `7E0`/`7E8`, PID `222230`, Formula `A-40` (`°C`)

3. **Add & Edit Any Custom Sensor Later On**
   - Tap **Add Custom Value (`+`)** on the **Sensors / PIDs** tab to add any standard OBD-II (`01xx`) or Renault/Dacia UDS (`22xxxx`) parameter.
   - Configure custom CAN Tx/Rx headers (`ATSH` / `ATCRA`), diagnostic session init commands (`10C0` / `1003`), byte formulas (`A`, `B`, `C`, `D`, `SIGNED(A)`, `{A:bit}`), and units.
   - Includes a **Live Formula Tester** right inside the Add/Edit dialog.

4. **Evaporator ECU Auto-Scanner & Raw ELM327 Terminal (`ECU Scanner` Tab)**
   - Because 2014 Dacia Dokker manual A/C variants route the evaporator sensor through either the Climate ECU (`744`), the K9K Engine ECU (`7E0`/`7E2`), or the UCH BCM (`745`), tap **"Scan All Candidate Evaporator PIDs"** to automatically probe all candidate addresses and apply the responding one with a single tap.

---

## Building & Installing in Android Studio

1. Open **Android Studio** and select **Open** -> choose `c:\Projekte\DokkerAnalyse`.
2. Let Android Studio sync Gradle and download the Android SDK / dependencies.
3. Connect your Android phone via USB (with USB Debugging enabled) and click **Run (`▶`)**, or build an APK via **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
4. Plug your Bluetooth ELM327 (v1.5 / PIC18F25K80 recommended for Dacia CAN) or OBDLink adapter into the Dokker's OBD2 port (inside the glovebox fuse compartment), pair it in Android Bluetooth settings, turn the car ignition ON, and tap **Connect OBD2** in the app.
