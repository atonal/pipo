# Pipo <img src="pipo/res/drawable/pipo_black.png" height="48">

Android application to track working hours based on GPS

## Installation

To install the Android SDK, run:

```bash
./install-android-sdk.sh
```

## Building and deploying

Building is done using [Leiningen](http://leiningen.org/).

To build and deploy to connected device or emulator, do:

```bash
lein droid doall
```

For more instructions, see lein droid
[tutorial](https://github.com/clojure-android/lein-droid/wiki/Tutorial).

## Testing

Tests utilize [Robolectric](http://robolectric.org/).

To run the tests, do:

```bash
lein with-profile local-testing droid local-test
```
