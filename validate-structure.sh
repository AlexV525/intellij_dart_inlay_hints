#!/bin/bash
# Project structure validation script

set -e

echo "🔍 Validating Dart Inlay Hints Plugin Project Structure..."

# Check required files exist
required_files=(
    "build.gradle.kts"
    "settings.gradle.kts" 
    "gradle.properties"
    "src/main/kotlin/com/alexv525/dart/inlay/DartParameterNameInlayHintsProvider.kt"
    "src/main/kotlin/com/alexv525/dart/inlay/psi/PsiParameterNameHintCalculator.kt"
    "src/main/resources/META-INF/plugin.xml"
    "src/main/resources/messages/DartInlayBundle.properties"
    "README.md"
    ".gitignore"
    "gradlew"
    "gradlew.bat"
    "gradle/wrapper/gradle-wrapper.properties"
    "gradle/wrapper/gradle-wrapper.jar"
)

echo "📁 Checking required files..."
for file in "${required_files[@]}"; do
    if [[ -f "$file" ]]; then
        echo "  ✅ $file"
    else
        echo "  ❌ Missing: $file"
        exit 1
    fi
done

# Check Gradle wrapper is executable
if [[ -x "gradlew" ]]; then
    echo "  ✅ gradlew is executable"
else
    echo "  ❌ gradlew is not executable"
    exit 1
fi

# Validate plugin.xml structure
echo "🔧 Validating plugin.xml..."
if grep -q "com.alexv525.dart.inlay.hints" src/main/resources/META-INF/plugin.xml; then
    echo "  ✅ Plugin ID found"
else
    echo "  ❌ Plugin ID missing"
    exit 1
fi

if grep -q "codeInsight.declarativeInlayProvider" src/main/resources/META-INF/plugin.xml; then
    echo "  ✅ Declarative inlay provider extension found"
else
    echo "  ❌ Declarative inlay provider extension missing"
    exit 1
fi

# Check Kotlin package structure
echo "📦 Validating Kotlin package structure..."
if grep -q "package com.alexv525.dart.inlay" src/main/kotlin/com/alexv525/dart/inlay/DartParameterNameInlayHintsProvider.kt; then
    echo "  ✅ Main provider package correct"
else
    echo "  ❌ Main provider package incorrect"
    exit 1
fi

if grep -q "package com.alexv525.dart.inlay.psi" src/main/kotlin/com/alexv525/dart/inlay/psi/PsiParameterNameHintCalculator.kt; then
    echo "  ✅ PSI calculator package correct"
else
    echo "  ❌ PSI calculator package incorrect" 
    exit 1
fi

# Check Gradle configuration
echo "⚙️ Validating Gradle configuration..."
if grep -q "org.jetbrains.intellij" build.gradle.kts; then
    echo "  ✅ IntelliJ Plugin found in build.gradle.kts"
else
    echo "  ❌ IntelliJ Plugin missing from build.gradle.kts"
    exit 1
fi

if grep -q "plugins.set(listOf(\"Dart\"))" build.gradle.kts; then
    echo "  ✅ Dart plugin dependency configured"
else
    echo "  ❌ Dart plugin dependency missing"
    exit 1
fi

echo ""
echo "🎉 All validations passed! Project structure is correct."
echo ""
echo "📝 Next steps:"
echo "  1. Ensure network access to JetBrains repositories"
echo "  2. Run: ./gradlew runIde"
echo "  3. Test inlay hints in a Dart project"
echo "  4. Run: ./gradlew buildPlugin to create distribution"