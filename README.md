# Pipo

Android application to track working hours based on GPS

## Building and deploying

Building is done using [Leiningen](http://leiningen.org/).

To build and deploy to connected device or emulator, do:

```bash
lein droid doall
```

## Testing

Tests utilize [Robolectric](http://robolectric.org/)

To run the tests, do:

```bash
lein with-profile local-testing droid local-test
```
