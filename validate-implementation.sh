#!/bin/bash

# Validation script to check implementation completeness

echo "=== Dart Inlay Hints Implementation Validation ==="
echo

# Check if key files exist
echo "✓ Checking core implementation files..."

files=(
    "src/main/kotlin/com/alexv525/dart/inlay/settings/DartInlaySettings.kt"
    "src/main/kotlin/com/alexv525/dart/inlay/settings/DartInlaySettingsConfigurable.kt"
    "src/main/kotlin/com/alexv525/dart/inlay/DartVariableTypeInlayHintsProvider.kt"
    "src/main/kotlin/com/alexv525/dart/inlay/psi/PsiVariableTypeHintCalculator.kt"
    "src/main/kotlin/com/alexv525/dart/inlay/psi/TypePresentationUtil.kt"
)

all_exist=true
for file in "${files[@]}"; do
    if [ -f "$file" ]; then
        echo "  ✓ $file"
    else
        echo "  ✗ $file (missing)"
        all_exist=false
    fi
done

if [ "$all_exist" = true ]; then
    echo "✓ All core implementation files present"
else
    echo "✗ Some core files are missing"
    exit 1
fi

echo

# Check for key implementation features using grep
echo "✓ Checking feature implementation..."

echo "  Settings System:"
if grep -q "enableVariableTypeHints" src/main/kotlin/com/alexv525/dart/inlay/settings/DartInlaySettings.kt; then
    echo "    ✓ Variable type hints toggle"
else
    echo "    ✗ Variable type hints toggle (missing)"
fi

if grep -q "suppressDynamic" src/main/kotlin/com/alexv525/dart/inlay/settings/DartInlaySettings.kt; then
    echo "    ✓ Dynamic type suppression"
else
    echo "    ✗ Dynamic type suppression (missing)"
fi

if grep -q "blacklist" src/main/kotlin/com/alexv525/dart/inlay/settings/DartInlaySettings.kt; then
    echo "    ✓ Variable name blacklist"
else
    echo "    ✗ Variable name blacklist (missing)"
fi

echo "  For-each Loop Support:"
if grep -q "calculateForEachLoopHint" src/main/kotlin/com/alexv525/dart/inlay/psi/PsiVariableTypeHintCalculator.kt; then
    echo "    ✓ For-each loop variable detection"
else
    echo "    ✗ For-each loop variable detection (missing)"
fi

if grep -q "inferIterableElementType" src/main/kotlin/com/alexv525/dart/inlay/psi/TypePresentationUtil.kt; then
    echo "    ✓ Iterable element type inference"
else
    echo "    ✗ Iterable element type inference (missing)"
fi

echo "  Pattern/Destructuring Support:"
if grep -q "calculateDestructuringHint" src/main/kotlin/com/alexv525/dart/inlay/psi/PsiVariableTypeHintCalculator.kt; then
    echo "    ✓ Destructuring pattern detection"
else
    echo "    ✗ Destructuring pattern detection (missing)"
fi

if grep -q "inferDestructuringTypes" src/main/kotlin/com/alexv525/dart/inlay/psi/TypePresentationUtil.kt; then
    echo "    ✓ Record component type inference"
else
    echo "    ✗ Record component type inference (missing)"
fi

echo "  Performance & Robustness:"
if grep -q "maxFileSize" src/main/kotlin/com/alexv525/dart/inlay/settings/DartInlaySettings.kt; then
    echo "    ✓ Large file size protection"
else
    echo "    ✗ Large file size protection (missing)"
fi

if grep -q "DumbService.isDumb" src/main/kotlin/com/alexv525/dart/inlay/DartVariableTypeInlayHintsProvider.kt; then
    echo "    ✓ Dumb mode handling"
else
    echo "    ✗ Dumb mode handling (missing)"
fi

if grep -q "hintCache" src/main/kotlin/com/alexv525/dart/inlay/DartVariableTypeInlayHintsProvider.kt; then
    echo "    ✓ Hint result caching"
else
    echo "    ✗ Hint result caching (missing)"
fi

echo "  Hint Placement:"
if grep -q ": \$formattedType" src/main/kotlin/com/alexv525/dart/inlay/psi/PsiVariableTypeHintCalculator.kt; then
    echo "    ✓ Postfix hint placement (name: Type)"
else
    echo "    ✗ Postfix hint placement (missing)"
fi

echo

# Check plugin registration
echo "✓ Checking plugin.xml registration..."
if grep -q "DartInlaySettingsConfigurable" src/main/resources/META-INF/plugin.xml; then
    echo "  ✓ Settings UI registered"
else
    echo "  ✗ Settings UI registration (missing)"
fi

if grep -q "DartInlaySettings" src/main/resources/META-INF/plugin.xml; then
    echo "  ✓ Settings service registered"
else
    echo "  ✗ Settings service registration (missing)"
fi

echo

echo "✓ Implementation validation complete!"
echo
echo "Note: This script checks for implementation presence, not correctness."
echo "Manual testing in IntelliJ IDEA is recommended to verify functionality."