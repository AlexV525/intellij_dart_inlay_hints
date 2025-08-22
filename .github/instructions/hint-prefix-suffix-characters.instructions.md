Do not append prefix and suffix characters to hints. The platform SDK will handle this automatically.

<Example>
(Hints are wrapped with `[]`.)

GOOD:
```kotlin
offset to formattedType
```

BAD:
```kotlin
offset to ": $formattedType"
```
</Example>
