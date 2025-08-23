Do not use suffix hints for variable types, always use prefix hints to follow what natural Dart code looks like.

<Example>
(Hints are wrapped with `[]`.)

GOOD:
```dart
final [String] name = 'Alice';
final [int] age = 30;
```

BAD:
```dart
final name [:String] = 'Alice';
final age [:int] = 30;
```
</Example>
