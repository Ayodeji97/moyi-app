fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## iOS

### ios beta

```sh
[bundle exec] fastlane ios beta
```

Archive the Release build (production API) and upload it to TestFlight, internal testing only

### ios check_ipa

```sh
[bundle exec] fastlane ios check_ipa
```

Check an .ipa is a production build, as beta does before uploading; uploads nothing. fastlane ios check_ipa ipa:<path> build:<n> [version:<v>, default Config.xcconfig's]

----


## Android

### android beta

```sh
[bundle exec] fastlane android beta
```

Build the signed release bundle (production API) and upload it to the Play internal track. Options: release_status:draft (needed until Play has reviewed the app once), any_branch:true

### android build_aab

```sh
[bundle exec] fastlane android build_aab
```

Build the signed release bundle with a given versionCode and check it, as beta does; asks Play nothing and uploads nothing. fastlane android build_aab version_code:<n> [any_branch:true]

### android check_aab

```sh
[bundle exec] fastlane android check_aab
```

Check an .aab is a production build, as beta does before uploading; uploads nothing. fastlane android check_aab aab:<path> version_code:<n> [version_name:<v>, default build.gradle.kts's]

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
