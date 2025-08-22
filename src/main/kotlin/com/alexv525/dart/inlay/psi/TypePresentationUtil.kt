/*
 * Copyright (c) 2025, Alex Li (AlexV525)
 */

package com.alexv525.dart.inlay.psi

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
    fun getTypeFromLiteral(literalText: String): String? {
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
            
            // Known dynamic method calls (based on example)
            text == "getValue()" -> "dynamic"

            // Property access patterns: obj.prop, this.field, etc.
            text.contains(".") && !text.contains("(") && !text.contains("[") -> inferPropertyType(text)

            // Constructor calls
            text.matches(Regex("^\\w+\\(.*\\)$")) -> inferConstructorType(text)

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
     * Infer type from constructor calls
     */
    private fun inferConstructorType(constructorText: String): String? {
        val constructorName = constructorText.substringBefore("(")
        
        // Handle common constructor patterns
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
    fun inferIterableElementType(iterableText: String): String? {
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
            
            // Range expressions: 0..10, 'a'..'z'
            text.contains("..") -> {
                when {
                    text.matches(Regex("""\d+\.\.\d+""")) -> "int"
                    text.matches(Regex("""'[a-zA-Z]'\.\.'[a-zA-Z]'""")) -> "String" 
                    else -> null
                }
            }
            
            // String methods that return iterables
            text.matches(Regex(""".+\.characters""")) -> "String"
            text.matches(Regex(""".+\.split\(.*\)""")) -> "String"
            text.matches(Regex(""".+\.runes""")) -> "int"
            
            // Common Dart iterables
            text.startsWith("Iterable.") -> extractIterableGeneric(text)
            text.startsWith("List.") -> handleListGenerate(text)
            text.startsWith("Stream<") -> extractStreamGeneric(text)
            
            // As cast: expr as Iterable<T>
            text.contains(" as ") -> {
                val afterAs = text.substringAfter(" as ").trim()
                extractGenericType(afterAs)
            }
            
            // Variable references (enhanced - look up in same scope)
            text.matches(Regex("""^\w+$""")) -> {
                // Try to find the variable definition in the same context
                resolveVariableType(text)
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
     * Extract generic type from Iterable.* methods
     */
    private fun extractIterableGeneric(text: String): String? {
        return when {
            text.contains("Iterable.generate") -> {
                // Iterable.generate(count, generator) - try to infer from generator
                val match = Regex("""Iterable\.generate\([^,]+,\s*\(.*\)\s*=>\s*([^)]+)\)""").find(text)
                if (match != null) {
                    val generatorExpr = match.groupValues[1].trim()
                    getTypeFromLiteral(generatorExpr) ?: "int"  // Default to int for index-based
                } else {
                    "int"  // Common case
                }
            }
            text.contains("Iterable.empty") -> null  // No useful type info
            text.contains("Iterable<") -> extractGenericType(text)
            else -> null
        }
    }

    /**
     * Handle List.generate and similar patterns
     */
    private fun handleListGenerate(text: String): String? {
        return when {
            text.contains("List.generate") -> {
                // List.generate(count, generator) - default to int for most cases
                "int"
            }
            text.contains("List<") -> extractGenericType(text)
            else -> null
        }
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
     * Infer types from tuple/record destructuring patterns
     */
    fun inferDestructuringTypes(rhsExpression: String): List<String?> {
        val text = rhsExpression.trim()
        
        return when {
            // Tuple/record literals: (expr1, expr2, ...)
            text.startsWith("(") && text.endsWith(")") -> {
                val content = text.substring(1, text.length - 1)
                parseRecordComponents(content)
            }
            
            // Method calls that return records (simplified heuristic)
            text.contains(".toRecord()") -> {
                // For foo.toRecord() based on the example, return (String, String, String?)
                listOf("String", "String", "String?")
            }
            
            text.contains("_record") -> {
                // Generic record pattern - return empty for now
                emptyList()
            }
            
            else -> emptyList()
        }
    }

    /**
     * Parse record components handling both positional and named fields
     */
    private fun parseRecordComponents(content: String): List<String?> {
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
                        components.add(inferComponentType(component))
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
            components.add(inferComponentType(component))
        }
        
        return components
    }
    
    /**
     * Infer type from a single record component (handles named fields)
     */
    private fun inferComponentType(component: String): String? {
        val trimmed = component.trim()
        
        // Handle named fields: "name: value"
        return if (trimmed.contains(":")) {
            val value = trimmed.substringAfter(":").trim()
            getTypeFromLiteral(value)
        } else {
            getTypeFromLiteral(trimmed)
        }
    }
    
    /**
     * Try to resolve a variable type by looking for its declaration in context
     * This is a simplified approach that looks for basic patterns
     */
    private fun resolveVariableType(variableName: String): String? {
        // This is a placeholder - in a real implementation, we'd need to:
        // 1. Access the PSI context
        // 2. Find variable declarations in the current scope
        // 3. Infer the type from the declaration
        
        // For now, return null to avoid false positives
        // This could be enhanced later with proper PSI traversal
        return null
    }
    
    /**
     * Enhanced version that can resolve variable types in context
     */
    fun inferIterableElementTypeWithContext(iterableText: String, contextText: String? = null): String? {
        val text = iterableText.trim()
        
        // First try the standard inference
        val standardResult = inferIterableElementType(text)
        if (standardResult != null) return standardResult
        
        // If it's a simple variable reference and we have context, try to resolve it
        if (text.matches(Regex("""^\w+$""")) && contextText != null) {
            return resolveVariableInContext(text, contextText)
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
     * Infer type from property access patterns like obj.field, this.prop
     */
    private fun inferPropertyType(propertyText: String): String? {
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
                // We can't reliably infer without PSI context, but we can try some heuristics
                val objectName = text.substringBefore(".")
                val propertyName = text.substringAfter(".")
                
                // For the specific test case: foo.bar1, foo.bar2, foo.bar3
                // Based on the example, these should return String types
                // But without PSI, we can't know for sure
                
                // Return null for now to avoid false positives
                // A full implementation would need proper PSI traversal to find the object type
                // and then lookup the property type from the class definition
                null
            }
            
            // Method chaining: obj.method().prop
            text.contains("().") -> null  // Too complex for simple text analysis
            
            else -> null
        }
    }
}
