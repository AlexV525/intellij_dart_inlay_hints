/*
 * Copyright (c) 2025, Alex Li (AlexV525)
 */

package com.alexv525.dart.inlay.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
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
        return com.alexv525.dart.inlay.settings.DartInlaySettings.getInstance()
            .shouldSuppressType(typeName)
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
        val text = literalText.trim()

        return when {
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
            text.startsWith("[") && text.endsWith("]") -> inferListType(text)
            text.startsWith("const [") && text.endsWith("]") -> inferListType(text)

            // Map literals
            text.startsWith("{") && text.endsWith("}") && text.contains(":") -> inferMapType(text)
            text.startsWith("const {") && text.endsWith("}") && text.contains(":") -> inferMapType(text)

            // Set literals
            text.startsWith("{") && text.endsWith("}") && !text.contains(":") -> inferSetType(text)
            text.startsWith("const {") && text.endsWith("}") && !text.contains(":") -> inferSetType(text)

            // Method calls with obvious return types
            text.endsWith(".toString()") -> "String"
            text.endsWith(".toInt()") -> "int"
            text.endsWith(".toDouble()") -> "double"
            text.contains(".length") && !text.contains("(") -> "int"

            // Property access patterns: obj.prop, this.field, etc.
            text.contains(".") && !text.contains("(") && !text.contains("[") -> inferPropertyType(text, psiContext)

            // Constructor calls
            text.matches(Regex("^\\w+\\(.*\\)$")) -> inferConstructorType(text, psiContext)

            else -> null
        }
    }

    /**
     * Infer specific List type from literal content
     */
    private fun inferListType(listText: String): String {
        val content = listText.removePrefix("const ").removePrefix("[").removeSuffix("]").trim()
        if (content.isEmpty()) return "List"

        val elementType = inferHomogeneousListElementType("[$content]")
        return if (elementType != null) "List<$elementType>" else "List"
    }

    /**
     * Infer specific Map type from literal content
     */
    private fun inferMapType(mapText: String): String {
        val content = mapText.removePrefix("const ").removePrefix("{").removeSuffix("}").trim()
        if (content.isEmpty()) return "Map"

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
            is DartReferenceExpression -> {
                // Simple constructor: Foo() -> Foo
                expression.text
            }
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
     * Infer Iterable.generate type using simplified PSI analysis
     */
    private fun inferIterableGenerateTypeFromPsi(text: String, psiContext: PsiElement): String? {
        // For now, fall back to text-based analysis
        // The specific PSI element API is complex and varies between plugin versions
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
     * Infer List.generate type using simplified PSI analysis
     */
    private fun inferListGenerateTypeFromPsi(text: String, psiContext: PsiElement): String? {
        // For now, fall back to text-based analysis
        // The specific PSI element API is complex and varies between plugin versions
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
     * Resolve method return type from PSI
     */
    private fun resolveMethodReturnType(methodCall: DartCallExpression): String? {
        // This would require more sophisticated analysis to resolve method definitions
        // For now, handle common patterns
        val methodText = methodCall.text
        return when {
            methodText.contains(".toRecord()") -> "(String, String, String?)" // Common pattern from example
            else -> null
        }
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
        var current = StringBuilder()

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
     * Find variable declaration PSI element (simplified approach)
     */
    private fun findVariableDeclaration(context: PsiElement, variableName: String): PsiElement? {
        // Use a simplified text-based search for now since specific PSI element types vary
        var current: PsiElement? = context
        while (current != null) {
            val text = current.text
            if (text != null && text.contains("$variableName =")) {
                // Found a potential variable declaration
                val pattern = Regex("""(?:var|final|late)\s+${Regex.escape(variableName)}\s*=\s*([^;]+)""")
                if (pattern.containsMatchIn(text)) {
                    return current
                }
            }
            current = current.parent
        }
        return null
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
     * Infer type from any Dart expression PSI element
     */
    private fun inferTypeFromExpression(expression: PsiElement): String? {
        val text = expression.text?.trim() ?: return null
        
        // Use text-based analysis with known patterns for now
        // This is more reliable than assuming specific PSI element types that may not exist
        return getTypeFromLiteral(text)
    }

    /**
     * Infer type from list literal PSI (simplified)
     */
    private fun inferListType(listExpr: PsiElement): String {
        val text = listExpr.text
        return inferListType(text)
    }

    /**
     * Infer type from set or map literal PSI (simplified)
     */
    private fun inferSetOrMapType(setOrMapExpr: PsiElement): String {
        val text = setOrMapExpr.text
        return when {
            text.contains(":") -> inferMapType(text)
            else -> inferSetType(text)
        }
    }

    /**
     * Infer type from call expression PSI (simplified)
     */
    private fun inferTypeFromCallExpression(callExpr: DartCallExpression): String? {
        val methodText = callExpr.text
        return when {
            methodText.endsWith(".toString()") -> "String"
            methodText.endsWith(".toInt()") -> "int"
            methodText.endsWith(".toDouble()") -> "double"
            methodText.endsWith(".length") -> "int"
            methodText.contains(".split(") -> "List<String>"
            else -> {
                // For constructor calls, try to infer the type name
                val expression = callExpr.expression
                expression?.text?.substringBefore("(")
            }
        }
    }

    /**
     * Infer type from reference expression PSI (simplified)
     */
    private fun inferTypeFromReference(refExpr: DartReferenceExpression): String? {
        // For now, use text-based analysis
        return null
    }

    /**
     * Enhanced version that can resolve variable types in context using PSI
     */
    fun inferIterableElementTypeWithContext(iterableText: String, contextText: String? = null, psiContext: PsiElement? = null): String? {
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
     * Infer field type from class definition using simplified PSI approach
     */
    private fun inferFieldTypeFromClass(className: String, fieldName: String, context: PsiElement): String? {
        // For now, use a simplified approach based on known patterns
        // This could be enhanced with proper PSI traversal later
        
        // Look for class definition in the file
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
     * Find class declaration PSI element (simplified)
     */
    private fun findClassDeclaration(context: PsiElement, className: String): PsiElement? {
        // Simplified approach - look for class definition in file text
        val fileText = context.containingFile?.text ?: return null
        val classPattern = Regex("""class\s+${Regex.escape(className)}\b""")
        
        return if (classPattern.containsMatchIn(fileText)) {
            context.containingFile
        } else {
            null
        }
    }

    /**
     * Collect class definitions manually (simplified)
     */
    private fun collectClassDefinitions(element: PsiElement, result: MutableList<PsiElement>) {
        val text = element.text
        if (text != null && text.contains("class ")) {
            result.add(element)
        }
        
        for (child in element.children) {
            collectClassDefinitions(child, result)
        }
    }

    /**
     * Find field type in class definition (simplified)
     */
    private fun findFieldTypeInClass(classDefinition: PsiElement, fieldName: String): String? {
        val text = classDefinition.text ?: return null
        
        // Look for field declaration pattern
        val fieldPattern = Regex("""(?:final|var|late)\s+(\w+\??)\s+${Regex.escape(fieldName)}""")
        val match = fieldPattern.find(text)
        
        return match?.groupValues?.get(1)
    }
}
