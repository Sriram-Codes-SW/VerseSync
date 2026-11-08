# Releasing VerseSync

The checklist for a release. The first release has extra steps, marked **first release**. Each step that needs the owner's accounts or secrets says so.

## Before the first release (once)

- [ ] **Play Console app.** Create the app for `dev.sriramcodessw.versesync` (ADR-16). Check that the old id `dev.sriramkaushal.versesync` has no Play listing.
- [ ] **Products.** Create `pro_subscription`, `small_tip` and `big_tip`, or the final products (issue #12).
- [ ] **Signing key.** Create an upload keystore and keep it, and its passwords, outside the repository. Back it up. Losing it blocks updates. Play App Signing holds the final key.
- [ ] **Privacy policy.** Review `docs/PRIVACY.md`, publish it at a stable address and enter that address in Play Console. Fill in the Data safety form to match it, including approximate location and the speech model downloads.
- [ ] **Keys tied to the package name.** Register again anything bound to the old id.
- [ ] **Branch protection.** Add the `build` check from the CI workflow as a required status check on `main`.

## Every release

1. **Version.** Raise `versionCode` by one in `app/build.gradle.kts` for every upload. Set `versionName`. The first upload uses `versionCode = 1` and `versionName = "1.0"`, which are the current values.
2. **Green main.** CI passes on `main`.
3. **Release build.** From a clean checkout of `main`, with the signing properties set (see below):

   ```bash
   ./gradlew bundleRelease
   ```

   The file is `app/build/outputs/bundle/release/app-release.aab`.
4. **Test the minified build on a real device.** R8 is on for release (ADR-07). Install an APK built from the same bundle and check: first-run language setup, a speech model download, recognition, lyric sync, the app lock, a purchase with a licensed test account, and Bluetooth sharing between two phones.
5. **Tag.** Create a signed or annotated tag named `v<versionName>` on the released commit and push it.

   ```bash
   git tag -a v1.0 -m "VerseSync 1.0"
   git push origin v1.0
   ```

6. **Source offer (GPL-3.0 section 6).** The project is licensed under GPL-3.0 (ADR-13). For every binary release, the matching source must be available. Publish a GitHub release for the tag, which provides the source archive, and link it from the Play Store listing. Check that `THIRD_PARTY_NOTICES.md` is current.
7. **Upload** the bundle to a Play Console testing track first, then promote it.

## Signing properties

The release build signs with an upload key only when these Gradle properties are set, for example in `~/.gradle/gradle.properties` on the release machine. Never commit them.

```properties
versesync.signing.storeFile=C:/keys/versesync-upload.jks
versesync.signing.storePassword=...
versesync.signing.keyAlias=...
versesync.signing.keyPassword=...
```

Without them the release build is unsigned, which is what CI and contributors get.

## Known gaps to decide before publishing

- Room and the biometric library are pre-release versions in the release build (issue #26). Decide before publishing.
- The README says the Play Integrity API and the Licensing library will be integrated. Under GPL-3.0 anyone may build a modified version, so these only protect the Play Store build. Decide whether they are still wanted.
- The README says everything happens 100 percent on the device. Language suggestion and model downloads use the network (see `docs/PRIVACY.md`), so the README wording should be softened before the listing is written.
