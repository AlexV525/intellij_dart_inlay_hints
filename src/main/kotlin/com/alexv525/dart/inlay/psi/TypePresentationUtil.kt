/*
 * Copyright (c) 2025, Alex Li (AlexV525)
 */

package com.alexv525.dart.inlay.psi

import com.intellij.psi.PsiElement
import com.jetbrains.lang.dart.psi.*

/**
 * Utility for formatting type strings in inlay hints.
 * Provides concise, readable type representations while avoiding clutter.
 */
object TypePresentationUtil {

    /**
     * Format a type name for display in inlay hints.
     * - Strips package qualifiers to keep hints concise
     * - Preserves nullability and basic generics when present
     * - Returns a clean, readable type string
     */
    fun formatType(typeName: String?): String? {
        if (typeName.isNullOrBlank()) return null

        val trimmed = typeName.trim()
        if (trimmed.isEmpty()) return null

        // Strip package qualifiers (e.g., "dart:core.String" -> "String")
        val withoutPackage = trimmed.substringAfterLast('.')

        // Handle generic types - keep them concise
        return cleanupGenerics(withoutPackage)
    }

    /**
     * Clean up generic type representations for better readability.
     * Enhanced to handle compression and complex generics.
     */
    private fun cleanupGenerics(typeName: String): String {
        var result = typeName

        // Compress deeply nested generics for readability
        // e.g., List<Map<String, List<int>>> -> List<Map<String, List<int>>>
        if (result.count { it == '<' } > 2) {
            // For very complex types, show simplified version
            val baseType = result.substringBefore('<')
            if (baseType.isNotEmpty()) {
                result = "$baseType<...>"
            }
        }

        // Clean up common Dart library prefixes
        result = result.replace("dart:core.", "")
        result = result.replace("dart:collection.", "")

        return result
    }

    /**
     * Check if this is a "trivial" type that users might want to suppress.
     * These are types that are often obvious from context.
     * Now delegates to settings for more sophisticated control.
     */
    fun isTrivialType(typeName: String?): Boolean {
        return com.alexv525.dart.inlay.settings.DartInlaySettings.getInstance().shouldSuppressType(typeName)
    }

    /**
     * Check if type meets minimum complexity requirements
     */
    fun meetsComplexityRequirement(typeName: String): Boolean {
        val settings = com.alexv525.dart.inlay.settings.DartInlaySettings.getInstance()
        val typeComplexity = settings.getTypeComplexity(typeName)
        return typeComplexity >= settings.minComplexity
    }

    /**
     * Determine type from simple literal expressions.
     * Returns null if type cannot be confidently determined.
     */
    fun getTypeFromLiteral(literalText: String, psiContext: PsiElement? = null): String? {
        var text = literalText.trim().replace("\n", "")
        val awaits = text.startsWith("await ")
        text = text.removePrefix("await ")

        var type = when {
            // String literals
            text.startsWith('"') && text.endsWith('"') -> "String"
            text.startsWith("'") && text.endsWith("'") -> "String"
            text.startsWith("r'") || text.startsWith("r\"") -> "String"
            text.startsWith("'''") || text.startsWith("\"\"\"") -> "String"

            // Boolean literals
            text == "true" || text == "false" -> "bool"

            // Null literal
            text == "null" -> "Null"

            // Numeric literals - enhanced detection
            text.matches(Regex("^-?\\d+$")) -> "int"
            text.matches(Regex("^-?\\d+\\.\\d+$")) -> "double"
            text.matches(Regex("^-?\\d+\\.\\d+e[+-]?\\d+$", RegexOption.IGNORE_CASE)) -> "double"
            text.matches(Regex("^0x[0-9a-fA-F]+$")) -> "int"  // Hex literals
            text.matches(Regex("^\\d+\\.\\d*$")) -> "double"  // 1. or 1.0

            // Collection literals - enhanced detection
            text.matches(Regex("^(const )?(<\\w*>)?\\[.*$")) && text.endsWith("]") -> inferListType(text)

            // Map literals
            text.matches(Regex("^(const )?(<\\w*,\\s*\\w*>)?\\{.*$")) && text.endsWith("}") -> inferMapType(text)

            // Set literals
            text.matches(Regex("^(const )?(<\\w*>)?\\{.*$")) && text.endsWith("}") -> inferSetType(text)

            // Method calls with obvious return types
            text.endsWith(".toString()") -> "String"
            text.endsWith(".toInt()") -> "int"
            text.endsWith(".toDouble()") -> "double"
            text.contains(".length") && !text.contains("(") -> "int"

            // Property access patterns: obj.prop, this.field, etc.
            text.contains(".") && !text.contains("(") && !text.contains("[") -> inferPropertyType(text, psiContext)

            // Constructor or method calls
            text.matches(Regex("^\\w+(\\.\\w+)?\\(.*\\)$")) -> inferConstructorType(text, psiContext)

            else -> null
        }
        if (type == null) {
            type = inferTypeFromPsiElement(psiContext)
        }
        Regex("^Future(<(.*)>)?$").run {
            if (type?.matches(this) == true && awaits) {
                type = this.find(type)?.groups?.get(2)?.value
            }
        }
        return type
    }

    /**
     * Infer specific List type from literal content
     */
    private fun inferListType(listText: String): String {
        val content = listText.removePrefix("const ").removePrefix("[").removeSuffix("]").trim()
        if (content.isEmpty()) return "List"

        val generics = Regex("^(<\\w+>)\\[").find(content)?.groups?.get(1)?.value
        if (generics != null) return "List<${generics.removePrefix("<").removeSuffix(">").trim()}>"

        val elementType = inferHomogeneousListElementType("[$content]")
        return if (elementType != null) "List<$elementType>" else "List"
    }

    /**
     * Infer specific Map type from literal content
     */
    private fun inferMapType(mapText: String): String {
        val content = mapText.removePrefix("const ").removePrefix("{").removeSuffix("}").trim()
        if (content.isEmpty()) return "Map"

        val generics = Regex("^(<\\w+,\\s*\\w+>)\\{").find(content)?.groups?.get(1)?.value
        if (generics != null) return generics.removePrefix("<").removeSuffix(">").split(",").let {
            "Map<${it.first().trim()}, ${it.last().trim()}>"
        }

        // Simple heuristic - look for homogeneous key-value pairs
        val pairs = content.split(",").map { it.trim() }.filter { it.contains(":") }
        if (pairs.isNotEmpty()) {
            val firstPair = pairs.first()
            val key = firstPair.substringBefore(":").trim()
            val value = firstPair.substringAfter(":").trim()

            val keyType = getTypeFromLiteral(key)
            val valueType = getTypeFromLiteral(value)

            if (keyType != null && valueType != null) {
                return "Map<$keyType, $valueType>"
            }
        }

        return "Map"
    }

    /**
     * Infer specific Set type from literal content
     */
    private fun inferSetType(setText: String): String {
        val content = setText.removePrefix("const ").removePrefix("{").removeSuffix("}").trim()
        if (content.isEmpty()) return "Set"

        val generics = Regex("^(<\\w+>)\\[").find(content)?.groups?.get(1)?.value
        if (generics != null) return "Set<${generics.removePrefix("<").removeSuffix(">").trim()}>"

        val elements = content.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (elements.isNotEmpty()) {
            val elementType = getTypeFromLiteral(elements.first())
            if (elementType != null && elements.all { getTypeFromLiteral(it) == elementType }) {
                return "Set<$elementType>"
            }
        }

        return "Set"
    }

    /**
     * Infer type from constructor calls using PSI analysis
     */
    private fun inferConstructorType(constructorText: String, psiContext: PsiElement? = null): String? {
        if (psiContext == null) {
            // Fallback to text-based analysis if no PSI context
            return inferConstructorTypeFromText(constructorText)
        }

        // Find DartNewExpression or DartCallExpression in the context
        val constructorCall = findConstructorCallInContext(psiContext, constructorText)
        if (constructorCall != null) {
            return inferTypeFromConstructorPsi(constructorCall)
        }

        return inferConstructorTypeFromText(constructorText)
    }

    /**
     * Find constructor call PSI element matching the given text
     */
    private fun findConstructorCallInContext(context: PsiElement, constructorText: String): DartCallExpression? {
        val callExpressions = mutableListOf<DartCallExpression>()
        collectCallExpressions(context, callExpressions)

        return callExpressions.find { call ->
            call.text.trim() == constructorText.trim()
        }
    }

    /**
     * Collect call expressions manually to avoid using PsiTreeUtil.findChildrenOfType
     */
    private fun collectCallExpressions(element: PsiElement, result: MutableList<DartCallExpression>) {
        if (element is DartCallExpression) {
            result.add(element)
        }

        for (child in element.children) {
            collectCallExpressions(child, result)
        }
    }

    /**
     * Infer type from constructor PSI element
     */
    private fun inferTypeFromConstructorPsi(call: DartCallExpression): String? {
        return when (val expression = call.expression) {
            is DartReferenceExpression -> inferTypeFromReferenceExpression(expression)

            is DartCallExpression -> {
                // Chained call - get the root type
                inferTypeFromConstructorPsi(expression)
            }

            else -> {
                // Try to extract type from the text
                val text = expression?.text ?: return null
                if (text.contains(".")) {
                    // Named constructor: MyClass.namedConstructor() -> MyClass
                    text.substringBefore(".")
                } else {
                    text
                }
            }
        }
    }

    private fun inferTypeFromReferenceExpression(expression: DartReferenceExpression): String? {
        // Simple constructor: Foo() -> Foo
        if (expression.children.isEmpty()) {
            return expression.text
        }
        return when (val child = expression.lastChild) {
            is DartId -> inferTypeFromIdAndReferenceExpression(expression)
            is DartReferenceExpression -> inferTypeFromReferenceExpression(child)
            else -> expression.text
        }
    }

    private fun inferTypeFromIdAndReferenceExpression(expression: DartReferenceExpression): String? {
        return expression.resolve()?.parent?.let { inferTypeFromPsiElement(it) }
    }

    private fun inferTypeFromPsiElement(element: PsiElement?): String? {
        return when (element) {
            is DartClassDefinition -> element.componentName.text
            is DartFactoryConstructorDeclaration -> element.type?.text
                ?: element.componentNameList.firstOrNull()?.text

            is DartFunctionDeclarationWithBody -> element.returnType?.text
            is DartFunctionDeclarationWithBodyOrNative -> element.returnType?.text
            is DartGetterDeclaration -> element.returnType?.text
            is DartMethodDeclaration -> {
                var type = element.returnType?.text
                if (type != null) {
                    val typeParameters = element.typeParameters ?: element.returnType?.type?.typeArguments
                    if (typeParameters != null) {
                        type = type.replace(typeParameters.text, "")
                    }
                }
                type ?: element.componentName?.text
            }
            is DartNamedConstructorDeclaration -> element.componentName?.text
            is DartVarDeclarationList -> {
                val expression: PsiElement? = element.varInit?.expression
                if (expression is DartAwaitExpression) {
                    val newExpression = expression.expression as? DartNewExpression
                    val type = newExpression?.typeArguments?.typeList?.typeList?.firstOrNull()?.text
                    if (type != null) {
                        return type
                    }
                }
                if (expression is DartCallExpression) {
                    val type = resolveMethodReturnType(expression)
                    if (type != null) {
                        return type
                    }
                }
                if (expression is DartReferenceExpression) {
                    inferTypeFromReferenceExpression(expression)
                } else null
            }

            else -> null
        }
    }

    /**
     * Fallback text-based constructor inference
     */
    private fun inferConstructorTypeFromText(constructorText: String): String? {
        val constructorName = constructorText.substringBefore("(")

        return when {
            constructorName.matches(Regex("^\\w+$")) -> constructorName
            constructorName.contains(".") -> {
                // Named constructor: MyClass.namedConstructor() -> MyClass
                constructorName.substringBefore(".")
            }

            else -> null
        }
    }

    /**
     * Infer element type from iterable expressions for for-each loops
     */
    fun inferIterableElementType(iterableText: String, psiContext: PsiElement? = null): String? {
        val text = iterableText.trim()

        return when {
            // Typed list literals: <Type>[...] or List<Type>[...]
            text.matches(Regex("""<(\w+)>\s*\[.*]""")) -> {
                val typeMatch = Regex("""<(\w+)>""").find(text)
                typeMatch?.groupValues?.get(1)
            }

            // Generic collection constructors: List<int>.filled(3, 0)
            text.matches(Regex("""(\w+)<(\w+)>\..*""")) -> {
                val match = Regex("""(\w+)<(\w+)>""").find(text)
                match?.groupValues?.get(2)
            }

            // Simple list literals with homogeneous content
            text.startsWith("[") && text.endsWith("]") -> {
                inferHomogeneousListElementType(text)
            }

            // String methods that return iterables
            text.matches(Regex(""".+\.split\(.*\)""")) -> "String"
            text.matches(Regex(""".+\.runes""")) -> "int"

            // Common Dart iterables
            text.startsWith("Iterable.") -> extractIterableGeneric(text, psiContext)
            text.startsWith("List.") -> handleListGenerate(text, psiContext)
            text.startsWith("Stream<") -> extractStreamGeneric(text)

            // As cast: expr as Iterable<T>
            text.contains(" as ") -> {
                val afterAs = text.substringAfter(" as ").trim()
                extractGenericType(afterAs)
            }

            // Variable references (enhanced - look up in same scope using PSI)
            text.matches(Regex("""^\w+$""")) -> {
                // Try to find the variable definition using PSI analysis
                resolveVariableType(text, psiContext)
            }

            else -> null
        }
    }

    /**
     * Extract generic type from Iterable<T>, List<T>, etc.
     */
    private fun extractGenericType(typeText: String): String? {
        val match = Regex("""(?:Iterable|List|Set|Collection)<(\w+)>""").find(typeText)
        return match?.groupValues?.get(1)
    }

    /**
     * Extract generic type from Iterable.* methods using PSI analysis
     */
    private fun extractIterableGeneric(text: String, psiContext: PsiElement? = null): String? {
        return when {
            text.contains("Iterable.generate") -> {
                if (psiContext != null) {
                    inferIterableGenerateTypeFromPsi(text, psiContext)
                } else {
                    inferIterableGenerateTypeFromText(text)
                }
            }

            text.contains("Iterable.empty") -> null  // No useful type info
            text.contains("Iterable<") -> extractGenericType(text)
            else -> null
        }
    }

    /**
     * Infer Iterable.generate type using PSI analysis of generator function
     */
    private fun inferIterableGenerateTypeFromPsi(text: String, psiContext: PsiElement): String? {
        // Find the Iterable.generate call expression in PSI
        val generateCall = findIterableGenerateCall(psiContext, text)
        if (generateCall != null) {
            // Get the generator function (second argument)
            val arguments = getCallArguments(generateCall)
            if (arguments.size >= 2) {
                val generatorFunction = arguments[1]
                return inferTypeFromGeneratorFunction(generatorFunction)
            }
        }

        // Fallback to text-based analysis if PSI analysis fails
        return inferIterableGenerateTypeFromText(text)
    }

    /**
     * Fallback text-based analysis for Iterable.generate
     */
    private fun inferIterableGenerateTypeFromText(text: String): String? {
        // Iterable.generate(count, generator) - try to infer from generator
        val match = Regex("""Iterable\.generate\([^,]+,\s*\(.*\)\s*=>\s*([^)]+)\)""").find(text)
        if (match != null) {
            val generatorExpr = match.groupValues[1].trim()
            return getTypeFromLiteral(generatorExpr)
        }
        return null
    }

    /**
     * Handle List.generate and similar patterns using PSI analysis
     */
    private fun handleListGenerate(text: String, psiContext: PsiElement? = null): String? {
        return when {
            text.contains("List<") -> extractGenericType(text)
            text.contains("List.generate") -> {
                if (psiContext != null) {
                    inferListGenerateTypeFromPsi(text, psiContext)
                } else {
                    null
                }
            }

            else -> null
        }
    }

    /**
     * Infer List.generate type using PSI analysis of generator function
     */
    private fun inferListGenerateTypeFromPsi(text: String, psiContext: PsiElement): String? {
        // Find the List.generate call expression in PSI
        val generateCall = findListGenerateCall(psiContext, text)
        if (generateCall != null) {
            // Get the generator function (second argument)
            val arguments = getCallArguments(generateCall)
            if (arguments.size >= 2) {
                val generatorFunction = arguments[1]
                return inferTypeFromGeneratorFunction(generatorFunction)
            }
        }

        // Fallback to text-based analysis if PSI analysis fails
        return inferListGenerateTypeFromText(text)
    }

    /**
     * Fallback text-based analysis for List.generate
     */
    private fun inferListGenerateTypeFromText(text: String): String? {
        // List.generate(count, generator) - try to infer from generator
        val match = Regex("""List\.generate\([^,]+,\s*\([^)]*\)\s*=>\s*([^)]+)\)""").find(text)
        if (match != null) {
            val generatorExpr = match.groupValues[1].trim()
            return getTypeFromLiteral(generatorExpr)
        }
        return null
    }

    /**
     * Extract generic type from Stream<T>
     */
    private fun extractStreamGeneric(text: String): String? {
        val match = Regex("""Stream<(\w+)>""").find(text)
        return match?.groupValues?.get(1)
    }

    /**
     * Infer element type from homogeneous list literal content
     */
    private fun inferHomogeneousListElementType(listText: String): String? {
        val content = listText.substring(1, listText.length - 1).trim()
        if (content.isEmpty()) return null

        val elements = content.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (elements.isEmpty()) return null

        // Check if all elements have the same inferred type
        val firstType = getTypeFromLiteral(elements.first())
        if (firstType != null && elements.all { getTypeFromLiteral(it) == firstType }) {
            return firstType
        }

        return null
    }

    /**
     * Infer types from tuple/record destructuring patterns using PSI analysis
     */
    fun inferDestructuringTypes(rhsExpression: String, psiContext: PsiElement? = null): List<String?> {
        val text = rhsExpression.trim()

        return when {
            // Tuple/record literals: (expr1, expr2, ...)
            text.startsWith("(") && text.endsWith(")") -> {
                val content = text.substring(1, text.length - 1)
                parseRecordComponents(content, psiContext)
            }

            // Method calls that return records - use PSI analysis
            text.contains(".") && text.contains("(") -> {
                if (psiContext != null) {
                    inferDestructuringFromMethodCall(text, psiContext)
                } else {
                    emptyList()
                }
            }

            psiContext is DartPatternVariableDeclaration -> {
                val callExpression = psiContext.lastChild as? DartCallExpression
                val reference = callExpression?.expression as? DartReferenceExpression
                val declaration = reference?.resolve()?.parent as? DartFunctionDeclarationWithBodyOrNative
                val types = declaration?.returnType?.text?.removePrefix("(")?.removeSuffix(")")?.split(",")
                return types ?: emptyList()
            }

            else -> emptyList()
        }
    }

    /**
     * Infer destructuring types from method calls using PSI analysis
     */
    private fun inferDestructuringFromMethodCall(methodCallText: String, psiContext: PsiElement): List<String?> {
        // Find the method call in PSI
        val callExpressions = mutableListOf<DartCallExpression>()
        collectCallExpressions(psiContext, callExpressions)

        val methodCall = callExpressions.find { call ->
            call.text.trim() == methodCallText.trim()
        }

        if (methodCall != null) {
            // Try to resolve the method and get its return type
            val returnType = resolveMethodReturnType(methodCall)
            if (returnType != null) {
                return parseRecordTypeComponents(returnType)
            }
        }

        return emptyList()
    }

    /**
     * Resolve method return type from PSI using proper analysis
     */
    private fun resolveMethodReturnType(methodCall: DartCallExpression): String? {
        var type: String? = when (val expression = methodCall.expression) {
            is DartReferenceExpression -> {
                // Direct method call like toString()
                val methodName = expression.text
                getKnownMethodReturnType(methodName) ?: inferTypeFromReferenceExpression(expression)
            }

            is DartCallExpression -> {
                // Method chain like obj.method().otherMethod()
                resolveMethodReturnType(expression)
            }

            else -> {
                // Property access method calls like obj.prop.method()
                val methodText = methodCall.text

                // Analyze the method being called
                if (methodText.contains(".")) {
                    val parts = methodText.split(".")
                    if (parts.size >= 2) {
                        val lastPart = parts.last()
                        val methodName = lastPart.substringBefore("(")
                        getKnownMethodReturnType(methodName)
                    }
                }

                // Try to resolve factory constructors
                if (isFactoryConstructor(methodCall)) {
                    resolveFactoryConstructorType(methodCall)
                }

                null
            }
        }

        // Append typed arguments
        val typedArguments = methodCall.typeArgumentsList
        if (typedArguments.isNotEmpty() && type?.contains("<") == false) {
            type += "<${typedArguments.joinToString(", ") { it.text.removePrefix("<").removeSuffix(">") }}>"
        }

        return type
    }

    /**
     * Get return type for known method names
     */
    private fun getKnownMethodReturnType(methodName: String): String? {
        return when (methodName) {
            "toString" -> "String"
            "toInt" -> "int"
            "toDouble" -> "double"
            "length" -> "int"
            "isEmpty", "isNotEmpty" -> "bool"
            "split" -> "List<String>"
            "runes" -> "Runes"
            "codeUnits" -> "List<int>"
            else -> null
        }
    }

    /**
     * Check if a call expression is a factory constructor using PSI analysis
     */
    private fun isFactoryConstructor(callExpr: DartCallExpression): Boolean {
        // Get the reference being called
        when (val expression = callExpr.expression) {
            is DartReferenceExpression -> {
                // Simple constructor call like MyClass() or named like MyClass.create()
                val referenceName = expression.text

                // Find the constructor declaration
                val constructorDeclaration = findConstructorDeclaration(callExpr, referenceName)
                return constructorDeclaration != null && isFactoryDeclaration(constructorDeclaration)
            }

            else -> {
                // For complex expressions, analyze the text to extract the constructor pattern
                val text = callExpr.text
                return isConstructorPatternAndFactory(text, callExpr)
            }
        }
    }

    /**
     * Resolve factory constructor type using PSI analysis
     */
    private fun resolveFactoryConstructorType(callExpr: DartCallExpression): String? {
        when (val expression = callExpr.expression) {
            is DartReferenceExpression -> {
                val referenceName = expression.text
                return extractClassNameFromConstructor(referenceName)
            }

            else -> {
                // Fallback to text analysis if PSI structure is complex
                val text = expression?.text ?: return null
                return extractClassNameFromConstructor(text)
            }
        }
    }

    /**
     * Extract class name from constructor reference
     */
    private fun extractClassNameFromConstructor(constructorRef: String): String? {
        return when {
            constructorRef.contains(".") -> {
                // Named constructor: MyClass.namedConstructor -> MyClass
                constructorRef.substringBefore(".")
            }

            constructorRef.matches(Regex("""^\w+$""")) -> {
                // Simple constructor: MyClass -> MyClass
                constructorRef
            }

            else -> null
        }
    }

    /**
     * Find constructor declaration PSI element using manual traversal
     */
    private fun findConstructorDeclaration(context: PsiElement, constructorRef: String): PsiElement? {
        val className = extractClassNameFromConstructor(constructorRef) ?: return null

        // Find the class declaration first
        val classDeclaration = findClassDeclaration(context, className) ?: return null

        // Look for the constructor within the class
        return findConstructorInClass(classDeclaration, constructorRef)
    }

    /**
     * Find specific constructor in class definition using PSI analysis
     */
    private fun findConstructorInClass(classDeclaration: PsiElement, constructorRef: String): PsiElement? {
        val constructorName = if (constructorRef.contains(".")) {
            // Named constructor: MyClass.create -> create
            constructorRef.substringAfter(".")
        } else {
            // Default constructor - look for the class name or no name
            constructorRef
        }

        // Manual traversal to find constructor declarations
        val constructors = mutableListOf<PsiElement>()
        collectConstructorDeclarations(classDeclaration, constructors)

        return constructors.find { constructor ->
            isMatchingConstructor(constructor, constructorName)
        }
    }

    /**
     * Collect constructor declarations manually using PSI traversal
     */
    private fun collectConstructorDeclarations(element: PsiElement, result: MutableList<PsiElement>) {
        // Check if this element is a constructor declaration
        if (isConstructorDeclaration(element)) {
            result.add(element)
        }

        // Recursively traverse children
        for (child in element.children) {
            collectConstructorDeclarations(child, result)
        }
    }

    /**
     * Check if PSI element represents a constructor declaration
     */
    private fun isConstructorDeclaration(element: PsiElement): Boolean {
        val text = element.text ?: return false

        // Look for constructor patterns:
        // - factory MyClass()
        // - external factory MyClass()
        // - MyClass()
        // - factory MyClass.namedConstructor()
        // - external factory MyClass.namedConstructor()
        return text.trimStart().let { trimmed ->
            trimmed.startsWith("factory ") || trimmed.startsWith("external factory ") ||
                    // Regular constructors (class name followed by parentheses)
                    trimmed.matches(Regex("""^\w+\s*\([^)]*\)""")) ||
                    // Named constructors
                    trimmed.matches(Regex("""^\w+\.\w+\s*\([^)]*\)"""))
        }
    }

    /**
     * Check if a constructor declaration is a factory constructor
     */
    private fun isFactoryDeclaration(constructorElement: PsiElement): Boolean {
        val text = constructorElement.text ?: return false
        val trimmed = text.trimStart()

        return trimmed.startsWith("factory ") || trimmed.startsWith("external factory ")
    }

    /**
     * Check if constructor matches the given name
     */
    private fun isMatchingConstructor(constructor: PsiElement, constructorName: String): Boolean {
        val text = constructor.text ?: return false

        return when {
            constructorName.contains(".") -> {
                // Named constructor
                text.contains(constructorName)
            }

            else -> {
                // Default constructor - match class name or just check if it's the main constructor
                text.contains("$constructorName(") ||
                        // For factory constructors that might not include class name in the method
                        (text.contains("factory") && !text.contains("."))
            }
        }
    }

    /**
     * Analyze constructor pattern from text and check if it's a factory
     */
    private fun isConstructorPatternAndFactory(text: String, context: PsiElement): Boolean {
        // Extract the constructor reference pattern
        val constructorPattern = Regex("""^(\w+(?:\.\w+)?)\(.*\)$""")
        val match = constructorPattern.find(text.trim())

        if (match != null) {
            val constructorRef = match.groupValues[1]
            val constructorDeclaration = findConstructorDeclaration(context, constructorRef)
            return constructorDeclaration != null && isFactoryDeclaration(constructorDeclaration)
        }

        return false
    }

    /**
     * Parse record type string into component types
     */
    private fun parseRecordTypeComponents(recordType: String): List<String?> {
        if (!recordType.startsWith("(") || !recordType.endsWith(")")) {
            return emptyList()
        }

        val content = recordType.substring(1, recordType.length - 1)
        return content.split(",").map { it.trim().ifEmpty { null } }
    }

    /**
     * Parse record components handling both positional and named fields with PSI context
     */
    private fun parseRecordComponents(content: String, psiContext: PsiElement? = null): List<String?> {
        val components = mutableListOf<String?>()
        var depth = 0
        val current = StringBuilder()

        for (char in content) {
            when (char) {
                '(' -> {
                    depth++
                    current.append(char)
                }

                ')' -> {
                    depth--
                    current.append(char)
                }

                ',' -> {
                    if (depth == 0) {
                        val component = current.toString().trim()
                        components.add(inferComponentType(component, psiContext))
                        current.clear()
                    } else {
                        current.append(char)
                    }
                }

                else -> current.append(char)
            }
        }

        // Add the last component
        if (current.isNotEmpty()) {
            val component = current.toString().trim()
            components.add(inferComponentType(component, psiContext))
        }

        return components
    }

    /**
     * Infer type from a single record component (handles named fields) with PSI context
     */
    private fun inferComponentType(component: String, psiContext: PsiElement? = null): String? {
        val trimmed = component.trim()

        // Handle named fields: "name: value"
        return if (trimmed.contains(":")) {
            val value = trimmed.substringAfter(":").trim()
            getTypeFromLiteral(value, psiContext)
        } else {
            getTypeFromLiteral(trimmed, psiContext)
        }
    }

    /**
     * Try to resolve a variable type by using PSI analysis
     */
    private fun resolveVariableType(variableName: String, psiContext: PsiElement? = null): String? {
        if (psiContext == null) return null

        // Find variable declarations in the current context
        val variableDeclaration = findVariableDeclaration(psiContext, variableName)
        if (variableDeclaration != null) {
            return inferTypeFromVariableDeclaration(variableDeclaration)
        }

        return null
    }

    /**
     * Find variable declaration PSI element using manual traversal
     */
    private fun findVariableDeclaration(context: PsiElement, variableName: String): PsiElement? {
        // Manual traversal to avoid using PsiTreeUtil.findChildrenOfType
        var current: PsiElement? = context
        while (current != null) {
            if (containsVariableDeclaration(current, variableName)) {
                return current
            }
            current = current.parent
        }
        return null
    }

    /**
     * Check if element contains a variable declaration
     */
    private fun containsVariableDeclaration(element: PsiElement, variableName: String): Boolean {
        val text = element.text ?: return false
        val pattern = Regex("""(?:var|final|late)\s+${Regex.escape(variableName)}\s*=""")
        return pattern.containsMatchIn(text)
    }

    /**
     * Find Iterable.generate call in PSI tree using manual traversal
     */
    private fun findIterableGenerateCall(context: PsiElement, targetText: String): DartCallExpression? {
        val callExpressions = mutableListOf<DartCallExpression>()
        collectCallExpressions(context, callExpressions)

        return callExpressions.find { call ->
            val text = call.text.trim()
            text.contains("Iterable.generate") && text == targetText.trim()
        }
    }

    /**
     * Find List.generate call in PSI tree using manual traversal
     */
    private fun findListGenerateCall(context: PsiElement, targetText: String): DartCallExpression? {
        val callExpressions = mutableListOf<DartCallExpression>()
        collectCallExpressions(context, callExpressions)

        return callExpressions.find { call ->
            val text = call.text.trim()
            text.contains("List.generate") && text == targetText.trim()
        }
    }

    /**
     * Get arguments from a call expression using manual analysis
     */
    private fun getCallArguments(call: DartCallExpression): List<PsiElement> {
        val arguments = mutableListOf<PsiElement>()

        // Use manual traversal to find arguments
        collectCallArgumentElements(call, arguments)

        return arguments
    }

    /**
     * Collect argument elements manually
     */
    private fun collectCallArgumentElements(element: PsiElement, result: MutableList<PsiElement>) {
        for (child in element.children) {
            // Check if this child represents an argument
            if (isArgumentElement(child)) {
                result.add(child)
            }
            // Recursively search children
            collectCallArgumentElements(child, result)
        }
    }

    /**
     * Check if PSI element represents a function argument
     */
    private fun isArgumentElement(element: PsiElement): Boolean {
        // This is a simplified check - in a real implementation we'd need to
        // understand the specific Dart PSI structure for arguments
        val text = element.text?.trim() ?: return false

        // Look for lambda function patterns
        return text.contains("=>") || (text.startsWith("(") && text.contains(")") && text.contains("=>"))
    }

    /**
     * Infer type from generator function (lambda) PSI element
     */
    private fun inferTypeFromGeneratorFunction(generatorElement: PsiElement): String? {
        val text = generatorElement.text?.trim() ?: return null

        // Extract the expression after =>
        val arrowIndex = text.indexOf("=>")
        if (arrowIndex >= 0 && arrowIndex < text.length - 2) {
            val returnExpr = text.substring(arrowIndex + 2).trim()

            // Remove trailing parentheses if present
            val cleanExpr = if (returnExpr.endsWith(")")) {
                returnExpr.dropLast(1).trim()
            } else returnExpr

            // Analyze the return expression
            return inferTypeFromGeneratorExpression(cleanExpr)
        }

        return null
    }

    /**
     * Infer type from generator expression (the part after =>)
     */
    private fun inferTypeFromGeneratorExpression(expression: String): String? {
        val expr = expression.trim()

        return when {
            // Variable references (like "index", "i")
            expr.matches(Regex("""^\w+$""")) -> "int" // Generator variables are typically int

            // Arithmetic expressions
            expr.contains("+") || expr.contains("-") || expr.contains("*") || expr.contains("/") -> {
                if (expr.contains(".")) "double" else "int"
            }

            // Method calls on the parameter
            expr.matches(Regex("""^\w+\.\w+\(.*\)$""")) -> {
                val methodName = expr.substringAfter(".").substringBefore("(")
                getKnownMethodReturnType(methodName)
            }

            // Literals
            else -> getTypeFromLiteral(expr)
        }
    }

    /**
     * Infer type from variable declaration PSI element (simplified)
     */
    private fun inferTypeFromVariableDeclaration(declaration: PsiElement): String? {
        val text = declaration.text ?: return null

        // Extract the initialization expression using text analysis
        val pattern = Regex("""(?:var|final|late)\s+\w+\s*=\s*([^;]+)""")
        val match = pattern.find(text)
        if (match != null) {
            val expression = match.groupValues[1].trim()
            return getTypeFromLiteral(expression)
        }
        return null
    }

    /**
     * Infer type from list literal PSI element
     */
    private fun inferListTypePsi(listExpr: PsiElement): String {
        val text = listExpr.text
        return inferListType(text)
    }

    /**
     * Infer type from call expression PSI using enhanced analysis
     */
    private fun inferTypeFromReferenceExpression(callExpr: DartCallExpression): String? {
        val expression = callExpr.expression
        val methodText = callExpr.text

        return when {
            // Method calls with known return types
            methodText.endsWith(".toString()") -> "String"
            methodText.endsWith(".toInt()") -> "int"
            methodText.endsWith(".toDouble()") -> "double"
            methodText.endsWith(".length") -> "int"
            methodText.contains(".split(") -> "List<String>"
            methodText.contains(".runes") -> "Runes"
            methodText.contains(".codeUnits") -> "List<int>"

            // Generator functions
            methodText.contains("Iterable.generate") -> {
                val elementType = inferIterableGenerateTypeFromPsi(methodText, callExpr)
                if (elementType != null) "Iterable<$elementType>" else "Iterable"
            }

            methodText.contains("List.generate") -> {
                val elementType = inferListGenerateTypeFromPsi(methodText, callExpr)
                if (elementType != null) "List<$elementType>" else "List"
            }

            // Factory constructors
            isFactoryConstructor(callExpr) -> resolveFactoryConstructorType(callExpr)

            // Constructor calls - infer from the constructor name
            expression is DartReferenceExpression -> {
                val constructorName = expression.text
                if (constructorName.matches(Regex("""^\w+$"""))) {
                    constructorName  // Simple constructor like MyClass()
                } else if (constructorName.contains(".")) {
                    // Named constructor like MyClass.namedConstructor()
                    constructorName.substringBefore(".")
                } else {
                    null
                }
            }

            else -> null
        }
    }

    /**
     * Infer type from reference expression PSI using context analysis
     */
    private fun inferTypeFromReference(refExpr: DartReferenceExpression): String? {
        val referenceName = refExpr.text

        // Try to resolve the reference to its declaration
        return resolveVariableType(referenceName, refExpr)
    }

    /**
     * Enhanced version that can resolve variable types in context using PSI
     */
    fun inferIterableElementTypeWithContext(
        iterableText: String, contextText: String? = null, psiContext: PsiElement? = null
    ): String? {
        val text = iterableText.trim()

        // First try the enhanced inference with PSI context
        val standardResult = inferIterableElementType(text, psiContext)
        if (standardResult != null) return standardResult

        // If it's a simple variable reference, try to resolve it
        if (text.matches(Regex("""^\w+$"""))) {
            // Try PSI-based resolution first
            if (psiContext != null) {
                val psiResult = resolveVariableType(text, psiContext)
                if (psiResult != null) {
                    // If it's a List<T>, extract T as the element type
                    if (psiResult.startsWith("List<") && psiResult.endsWith(">")) {
                        return psiResult.substring(5, psiResult.length - 1)
                    }
                }
            }

            // Fallback to text-based resolution
            if (contextText != null) {
                return resolveVariableInContext(text, contextText)
            }
        }

        return null
    }

    /**
     * Simple context-based variable resolution for basic cases
     */
    private fun resolveVariableInContext(variableName: String, context: String): String? {
        // Look for patterns like: final list = [1, 2, 3];
        val varPattern = Regex("""(?:var|final|late)\s+${Regex.escape(variableName)}\s*=\s*([^;]+)""")
        val match = varPattern.find(context)

        if (match != null) {
            val expression = match.groupValues[1].trim()
            val inferredType = getTypeFromLiteral(expression)

            // If it's a List<T>, extract T as the element type
            if (inferredType != null && inferredType.startsWith("List<") && inferredType.endsWith(">")) {
                return inferredType.substring(5, inferredType.length - 1)
            }
        }

        return null
    }

    /**
     * Infer type from property access patterns using PSI analysis
     */
    private fun inferPropertyType(propertyText: String, psiContext: PsiElement? = null): String? {
        val text = propertyText.trim()

        // Handle common property access patterns
        return when {
            // Standard library properties we know about
            text.endsWith(".length") -> "int"
            text.endsWith(".isEmpty") -> "bool"
            text.endsWith(".isNotEmpty") -> "bool"
            text.endsWith(".first") -> null  // Type depends on collection
            text.endsWith(".last") -> null   // Type depends on collection

            // Object property access: foo.bar1, foo.bar2, etc.
            text.matches(Regex("^\\w+\\.\\w+$")) -> {
                if (psiContext != null) {
                    inferPropertyTypeFromPsi(text, psiContext)
                } else {
                    null
                }
            }

            else -> null
        }
    }

    /**
     * Infer property type using PSI analysis
     */
    private fun inferPropertyTypeFromPsi(propertyText: String, psiContext: PsiElement): String? {
        val objectName = propertyText.substringBefore(".")
        val propertyName = propertyText.substringAfter(".")

        // Find the object declaration
        val objectDeclaration = findVariableDeclaration(psiContext, objectName)
        if (objectDeclaration != null) {
            val objectType = inferTypeFromVariableDeclaration(objectDeclaration)
            if (objectType != null) {
                return inferFieldTypeFromClass(objectType, propertyName, psiContext)
            }
        }

        return null
    }

    /**
     * Infer field type from class definition using enhanced PSI approach
     */
    private fun inferFieldTypeFromClass(className: String, fieldName: String, context: PsiElement): String? {
        // First try to find the class definition using PSI traversal
        val classDeclaration = findClassDeclaration(context, className)
        if (classDeclaration != null) {
            return findFieldTypeInClass(classDeclaration, fieldName)
        }

        // Fallback to text-based analysis if PSI traversal fails
        val fileText = context.containingFile?.text ?: return null

        // Find class definition pattern
        val classPattern = Regex("""class\s+${Regex.escape(className)}[^{]*\{([^}]*)\}""", RegexOption.DOT_MATCHES_ALL)
        val classMatch = classPattern.find(fileText)

        if (classMatch != null) {
            val classBody = classMatch.groupValues[1]

            // Look for field declaration
            val fieldPattern = Regex("""(?:final|var|late)\s+(\w+\??)\s+${Regex.escape(fieldName)}""")
            val fieldMatch = fieldPattern.find(classBody)

            if (fieldMatch != null) {
                return fieldMatch.groupValues[1]
            }
        }

        return null
    }

    /**
     * Find class declaration PSI element using enhanced traversal
     */
    private fun findClassDeclaration(context: PsiElement, className: String): PsiElement? {
        // Try to find class definition using PSI traversal
        val classDefinitions = mutableListOf<PsiElement>()
        collectClassDefinitions(context.containingFile ?: context, classDefinitions)

        return classDefinitions.find { classElement ->
            val text = classElement.text
            text != null && text.contains("class $className")
        }
    }

    /**
     * Collect class definitions manually using PSI traversal
     */
    private fun collectClassDefinitions(element: PsiElement, result: MutableList<PsiElement>) {
        // Check if this element represents a class declaration
        if (isClassDeclaration(element)) {
            result.add(element)
        }

        // Recursively traverse children
        for (child in element.children) {
            collectClassDefinitions(child, result)
        }
    }

    /**
     * Check if PSI element represents a class declaration
     */
    private fun isClassDeclaration(element: PsiElement): Boolean {
        val text = element.text ?: return false

        // Look for class declaration patterns
        return text.trimStart().startsWith("class ") && text.contains("{") && text.contains("}")
    }

    /**
     * Find field type in class definition using enhanced analysis
     */
    private fun findFieldTypeInClass(classDefinition: PsiElement, fieldName: String): String? {
        val text = classDefinition.text ?: return null

        // Look for field declarations with proper type annotations
        val patterns = listOf(
            // Explicit type: final String name;
            Regex("""(?:final|var|late)\s+(\w+\??)\s+${Regex.escape(fieldName)}\s*[;=]"""),
            // Constructor parameter: this.name,
            Regex("""this\.${Regex.escape(fieldName)}\s*[,)]"""),
            // Field with initializer: final name =
            Regex("""(?:final|var|late)\s+${Regex.escape(fieldName)}\s*=""")
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val groups = match.groupValues
                if (groups.size > 1 && groups[1].isNotEmpty()) {
                    return groups[1]  // Return the type
                } else {
                    // For constructor parameters and initializers, try to infer from context
                    return inferFieldTypeFromContext(classDefinition, fieldName)
                }
            }
        }

        return null
    }

    /**
     * Infer field type from class context (constructor parameters, etc.)
     */
    private fun inferFieldTypeFromContext(classDefinition: PsiElement, fieldName: String): String? {
        val text = classDefinition.text ?: return null

        // Look for constructor parameter types
        val constructorPattern = Regex("""(?:\w+\(|:\s*)\s*[^)]*\b(\w+\??)\s+this\.${Regex.escape(fieldName)}""")
        val constructorMatch = constructorPattern.find(text)

        if (constructorMatch != null) {
            return constructorMatch.groupValues[1]
        }

        return null
    }
}
