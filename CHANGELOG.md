## 0.1.0

Initial release with parameter name hints and variable type hints.

### Features
- **Parameter Name Hints**: Shows parameter names for positional arguments in function calls
- **Variable Type Hints**: Shows inferred types for implicitly typed local variables (`var`, `final`, `late`)

### Variable Type Hints Support
- Detects implicit variable declarations (`var x = value`)
- Infers types from simple literals (strings, numbers, booleans, null)
- Infers types from collection literals (List, Map, Set)
- Infers types from constructor calls
- Skips trivial types (dynamic) to reduce clutter
- Skips underscore variables (`_`, `__`) commonly used for unused values

### Settings
- Both hint types can be toggled in Editor > Inlay Hints > Dart
- Parameter name hints enabled by default
- Variable type hints enabled by default
