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

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
