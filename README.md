# Dart Inlay Hints (Parameter Names)

A lightweight IntelliJ plugin that adds parameter name inlay hints for Dart code, powered by PSI and depending on the official Dart plugin.

## Requirements

- IntelliJ IDEA 2024.3
- JDK 17
- Dart plugin is automatically provisioned in the sandbox by the Gradle IntelliJ Plugin

## Run in a sandbox IDE

```bash
./gradlew runIde
```

This starts a sandbox IDE with the Dart plugin and this plugin enabled. Open a Dart project, then:

- Configure Dart SDK (Settings > Languages & Frameworks > Dart).
- Ensure Editor > Inlay Hints > Dart > "Parameter Name Hints" is enabled (enabled by default).

## Build a distributable

```bash
./gradlew buildPlugin
```

The ZIP will be in `build/distributions/`.

## Notes

- This plugin uses PSI to compute hints for positional arguments. It avoids deeper coupling with the Dart Analysis Server.
- Future work may add more hint categories and optional de-noising rules.
