# Dart Inlay Hints (Parameter Names + Variable Types)

<!-- Plugin description -->
A lightweight IntelliJ plugin that adds parameter name inlay hints and variable type inlay hints for Dart code, powered by PSI and depending on the official Dart plugin.
<!-- Plugin description end -->

## Requirements

- IntelliJ IDEA 2025.1.3+
- JDK 21
- Dart plugin is automatically provisioned in the sandbox by the Gradle IntelliJ Plugin

## Project Setup

First, validate the project structure:
```bash
./validate-structure.sh
```

## Run in a sandbox IDE

```bash
./gradlew runIde
```

This starts a sandbox IDE with the Dart plugin and this plugin enabled. Open a Dart project, then:

- Configure Dart SDK (Settings > Languages & Frameworks > Dart).
- Ensure Editor > Inlay Hints > Dart > "Parameter Name Hints" is enabled (enabled by default).
- Ensure Editor > Inlay Hints > Dart > "Variable Type Hints" is enabled for type information on implicitly typed variables.

## Build a distributable

```bash
./gradlew buildPlugin
```

The ZIP will be in `build/distributions/`.

## Troubleshooting

If you encounter network issues when building:
- Ensure access to JetBrains repositories (cache-redirector.jetbrains.com)
- Some corporate/restricted networks may block these domains
- Try using a VPN or different network environment

## Notes

- This plugin uses PSI to compute hints for positional arguments and implicitly typed variables. It avoids deeper coupling with the Dart Analysis Server.
- Parameter name hints are shown before positional arguments in function calls.
- Variable type hints are shown after variable names for `var`, `final`, and `late` declarations without explicit types.
- Future work may add more hint categories and optional de-noising rules.
