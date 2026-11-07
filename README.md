# DualDrop

**Get photos and videos off your DJI Osmo Action camera, without losing your home Wi-Fi.**

DualDrop is a free Android app. It connects to your camera, shows everything on it, and saves the photos and videos you pick to your phone's gallery. On many phones it can also keep your home Wi-Fi and internet working while the camera is connected, which the official app can't do.

[**⬇ Download the latest version**](https://github.com/UmutAlisoglu/DualDrop/releases/latest)

> DualDrop is an independent, unofficial app. It is not made by DJI and not affiliated with DJI. See [Disclaimer](#disclaimer).

> **Status: experiment.** DualDrop is my Android side project. It is native because the phone talks straight to the camera over Bluetooth and Wi-Fi. The "keep home Wi-Fi" feature is confirmed on **one** phone and camera pair so far (Galaxy S25 Ultra + Osmo Action 4) and needs a one-time Wireless debugging setup. If you try it, please [send a compatibility report](https://github.com/UmutAlisoglu/DualDrop/issues/new?template=compatibility-report.yml), whether it worked or not.

---

## Contents

- [What DualDrop does](#what-dualdrop-does)
- [What you need](#what-you-need)
- [Install DualDrop](#install-dualdrop)
- [Connect your camera](#connect-your-camera)
- [Download photos and videos](#download-photos-and-videos)
- [Keep your home Wi-Fi (optional)](#keep-your-home-wi-fi-optional)
- [Compatibility](#compatibility)
- [Questions and answers](#questions-and-answers)
- [Something isn't working](#something-isnt-working)
- [Privacy](#privacy)
- [Disclaimer](#disclaimer)
- [Credits and licence](#credits-and-licence)
- [For developers](#for-developers)

---

## What DualDrop does

- **Replaces DJI Mimo for downloading.** No account, no login, no activation, no ads.
- **Browse the camera.** See all photos and videos as a grid, sorted by day.
- **Pick many at once.** Long-press one item, then tap more, or tap a date to select the whole day. One tap downloads them all into your gallery, with a progress bar.
- **Preview before you download.** Play a video straight from the camera, trim it, or save a single frame as a photo.
- **Keeps your home Wi-Fi.** Normally your phone leaves your home Wi-Fi when it joins the camera's Wi-Fi. DualDrop can keep both connected at once, so your internet, messages and music keep working. It should work on phones whose Wi-Fi chip can hold two connections. So far it is confirmed on a Samsung Galaxy S25 Ultra; see [Compatibility](#compatibility). DualDrop checks your phone and tells you if it can't.
- **Set it up once.** The Wi-Fi feature is set up inside the app in about two minutes. No computer, no root, no extra apps. After a restart it switches itself back on automatically.

## What you need

| | |
|---|---|
| **Phone** | Android 10 or newer. The "keep home Wi-Fi" feature needs Android 11 or newer and a phone whose Wi-Fi chip supports two connections. |
| **Camera** | Tested with the **DJI Osmo Action 4**. The camera part comes from [Osmosis](https://github.com/KonradIT/Osmosis), which also supports Osmo Action 5 Pro, Action 6, Osmo Nano and Osmo Pocket 3 / 4 ([full list](docs/OSMOSIS_README.md#supported-cameras)). |
| **On the phone** | Bluetooth and Wi-Fi switched on. |

## Install DualDrop

DualDrop is not in the Google Play Store. You install it directly from this page. It only takes a minute.

1. On your phone, open the [latest release](https://github.com/UmutAlisoglu/DualDrop/releases/latest).
2. Under **Assets**, tap the file that ends in **.apk** (for example `DualDrop-0.2.1.apk`) to download it.
3. Open the downloaded file (from the notification or your *Downloads* folder).
4. If your phone asks, allow your browser or file app to **install unknown apps**, then go back and tap **Install**.
5. If Google Play Protect shows a warning, tap **More details › Install anyway**. This appears for apps that don't come from the Play Store.

To update later, download the newer .apk and install it the same way. Your settings and cameras are kept.

## Connect your camera

1. Switch on the camera and wait a few seconds.
2. Open DualDrop and allow the permissions it asks for (nearby devices / Bluetooth).
3. Tap your camera in the list. A new camera shows the label **NEW**.
4. **Look at the camera screen** and confirm the pairing request (it says **OSMO**).
5. When Android asks to join the camera's Wi-Fi, tap **Connect**.

You only have to pair once. Next time, just open DualDrop and tap the camera.

> Close DJI Mimo before you connect. The camera can only talk to one app at a time.

## Download photos and videos

- **One item:** tap it to open the preview, tap **Add to Queue**, go back and tap **Download**.
- **Several items:** long-press any photo or video to start selecting. Tap more items, or tap a **date** to select the whole day. Then tap **Download**.
- When it's done, DualDrop shows how many files are in your gallery. Tap **Open gallery** to see them.

Files are saved in full quality to your phone's normal gallery (Photos / Gallery app).

## Keep your home Wi-Fi (optional)

Without this, your phone drops your home Wi-Fi while it's connected to the camera. With it, both stay connected. You set it up once:

1. In DualDrop, tap the **Keep your home Wi-Fi** banner on the main screen.
2. Follow the three steps on screen:
   - **Turn on developer options.** Go to *Settings › About phone › Software information* and tap **Build number** seven times.
   - **Connect to Wi-Fi**, for example your home Wi-Fi.
   - **Pair DualDrop with your phone.** Tap **Start**, switch on **Wireless debugging**, open *Pair device with pairing code*, then type the 6-digit code into the DualDrop notification.
3. DualDrop finishes the rest by itself and shows **On** when it works.

After that you don't need to do anything. After every restart, DualDrop switches it back on within about a minute once your phone is on Wi-Fi.

<details>
<summary>How does this work? (technical)</summary>

Many phones have a Wi-Fi chip that can hold a second, local-only connection, but Android ships the setting that allows it switched off. DualDrop pairs with the phone's own *Wireless debugging* (a local connection to itself, nothing leaves the phone), grants itself the permission it needs, and runs:

```
cmd wifi force-overlay-config-value bool config_wifiMultiStaLocalOnlyConcurrencyEnabled enabled true
```

This is a runtime setting. It does not change any firmware and it resets on every restart, which is why DualDrop re-applies it after boot. To check whether a phone supports it, DualDrop asks Android whether a second Wi-Fi interface "can be created without destroying" the first.

Tested on a Samsung Galaxy S25 Ultra (Android 17 / One UI 9) with an Osmo Action 4: home Wi-Fi and internet stayed up while downloading from the camera at about 17 MB/s.

</details>

## Compatibility

DualDrop has been tested on very few devices so far. Every report helps, especially "it didn't work".

| Phone | Android | Camera | Browse + download | Keep home Wi-Fi |
|---|---|---|---|---|
| Samsung Galaxy S25 Ultra | 17 (One UI 9) | Osmo Action 4 | ✅ Works (about 17 MB/s) | ✅ Works |

Other cameras supported by [Osmosis](docs/OSMOSIS_README.md#supported-cameras) (Osmo Action 5 Pro, Action 6, Osmo Nano, Osmo Pocket 3 / 4) are expected to work for browsing and downloading, but haven't been tested with DualDrop yet.

**Tried it?** [Send a compatibility report](https://github.com/UmutAlisoglu/DualDrop/issues/new?template=compatibility-report.yml). It takes about a minute: phone model, Android version, camera, and what worked. Confirmed reports are added to the table above.

## Questions and answers

**Is DualDrop free?**
Yes. It's free and open source. There are no ads, accounts or in-app purchases.

**Is it safe to install an app from GitHub?**
The full source code is public on this page, so anyone can check what it does. DualDrop only talks to your camera. It has no analytics and sends nothing to the internet.

**Do I still need DJI Mimo?**
Not for browsing and downloading. You may still want Mimo for firmware updates or camera features DualDrop doesn't have, such as editing templates.

**Does it work without internet, for example in airplane mode?**
Yes. Downloading from the camera only uses the camera's own Wi-Fi and Bluetooth. Switch Bluetooth and Wi-Fi back on after enabling airplane mode.

**Does it work with my phone?**
The camera part works on most Android 10+ phones. For the "keep home Wi-Fi" feature, just try the setup: DualDrop checks your phone and tells you clearly if it isn't supported. Tested so far on a Samsung Galaxy S25 Ultra. See [Compatibility](#compatibility), and please add your phone with a [compatibility report](https://github.com/UmutAlisoglu/DualDrop/issues/new?template=compatibility-report.yml).

**Does it work on iPhone?**
No, DualDrop is Android only.

**Can it change my camera's settings or show a live view?**
Not at the moment. DualDrop is for getting your photos and videos onto your phone.

**Is "keep home Wi-Fi" risky for my phone?**
It only flips a setting that Android already has, and the setting resets on every restart. Nothing is installed or changed in the system. You can switch it off in the app at any time.

**Why do I need developer options and Wireless debugging?**
That's the only way for an app to change this Wi-Fi setting without a computer or root. DualDrop switches Wireless debugging off again after it's done.

**Where are my downloaded files?**
In your phone's normal gallery app, like photos you take with the phone.

## Something isn't working

**The camera doesn't show up in the list**
- Make sure Bluetooth is on and the camera is switched on and awake.
- Close DJI Mimo completely (swipe it away from recent apps).
- Tap **Rescan** to search again.

**The camera never asks to pair ("OSMO")**
- Tap the camera in DualDrop again and watch the camera screen; the request only shows for a short time.
- If it was paired with another phone before, restart the camera and try again.

**"Join Wi-Fi" fails or the gallery stays empty**
- Tap **Connect** when Android asks to join the camera's Wi-Fi.
- Switch Wi-Fi off and on again, then reconnect in DualDrop.

**Downloads stop or are slow**
- Keep the phone close to the camera and keep the screen on.
- Make sure the camera battery isn't almost empty.

**Pairing with Wireless debugging doesn't work**
- On Samsung phones, after expanding the DualDrop notification, tap **Enter code** once more if the text field doesn't appear.
- The 6-digit code expires quickly. If it says "Code wrong or expired", open *Pair device with pairing code* again and enter the new code.
- If Android says Wireless debugging isn't allowed on this network, switch it on, tap **Allow** and tick *Always allow on this network*.

**Home Wi-Fi still disconnects**
- Open the **Keep your home Wi-Fi** screen in DualDrop. If it says **Off right now**, tap **Switch on now**.
- If it says **Not supported**, your phone's Wi-Fi chip can't hold two connections. DualDrop still works, but home Wi-Fi drops while the camera is connected.

**Still stuck?** [Open an issue](https://github.com/UmutAlisoglu/DualDrop/issues) and tell us your phone model, Android version, camera model and what happened.

## Privacy

DualDrop talks only to your camera. There are no analytics, no accounts, no ads and no cloud uploads. Your photos and videos go straight from the camera to your phone and nowhere else.

## Disclaimer

DualDrop is an independent, unofficial project. It is **not affiliated with, endorsed by or sponsored by DJI**. "DJI", "Osmo" and "Osmo Action" are trademarks of SZ DJI Technology Co., Ltd. and are used here only to say which cameras the app works with. DualDrop contains no DJI code, SDK or firmware.

The software is provided "as is", without warranty of any kind. Use it at your own risk. Deleting or downloading media is your responsibility. The Wi-Fi feature only changes a runtime setting that resets on restart; it does not modify firmware.

## Credits and licence

DualDrop is built on [**Osmosis**](https://github.com/KonradIT/Osmosis) by Konrad Iturbe, which does all of the camera work: Bluetooth pairing, browsing, previewing and downloading. A big thank you to Konrad and everyone credited in the [original Osmosis README](docs/OSMOSIS_README.md#credits). DualDrop adds the "keep home Wi-Fi" feature, easier multi-select downloads and a new design.

DualDrop is released under the MIT licence, like Osmosis. See [LICENSE.txt](LICENSE.txt) (Copyright Konrad Iturbe and contributors). Bundled third-party libraries and their licences are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## For developers

- **Build:** JDK 17, Android SDK 36. Run `./gradlew assembleDebug` (or `assembleRelease` with your own `keystore.properties`).
- **DualDrop's own code** lives in `app/src/main/java/de/druomi/osmolink/` and `res/values*/osmolink_strings.xml`. Small changes to Osmosis files are marked with `DualDrop:` comments.
- **Maintenance notes** (building, releasing, merging updates from Osmosis): [MAINTAINING.md](MAINTAINING.md)
- **Camera protocol documentation** from Osmosis: [MEDIA_PROTOCOL.md](MEDIA_PROTOCOL.md)
- **Original Osmosis README** (features, supported cameras, credits): [docs/OSMOSIS_README.md](docs/OSMOSIS_README.md)
