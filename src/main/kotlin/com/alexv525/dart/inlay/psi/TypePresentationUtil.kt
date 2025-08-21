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
     */
    private fun cleanupGenerics(typeName: String): String {
        // For MVP, keep generics simple - just preserve basic structure
        // Future enhancement could make this more sophisticated
        return typeName
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

            // Numeric literals - basic detection
            text.matches(Regex("^-?\\d+$")) -> "int"
            text.matches(Regex("^-?\\d+\\.\\d+$")) -> "double"
            text.matches(Regex("^-?\\d+\\.\\d+e[+-]?\\d+$", RegexOption.IGNORE_CASE)) -> "double"

            // List literals
            text.startsWith("[") && text.endsWith("]") -> "List"
            text.startsWith("const [") && text.endsWith("]") -> "List"

            // Map literals
            text.startsWith("{") && text.endsWith("}") && text.contains(":") -> "Map"
            text.startsWith("const {") && text.endsWith("}") && text.contains(":") -> "Map"

            // Set literals
            text.startsWith("{") && text.endsWith("}") && !text.contains(":") -> "Set"
            text.startsWith("const {") && text.endsWith("}") && !text.contains(":") -> "Set"

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
            
            // String for String.characters, String.split, etc.
            text.matches(Regex(""".+\.characters""")) -> "String"
            text.matches(Regex(""".+\.split\(.*\)""")) -> "String"
            
            // As cast: expr as Iterable<T>
            text.contains(" as ") && text.contains("Iterable<") -> {
                val match = Regex("""Iterable<(\w+)>""").find(text)
                match?.groupValues?.get(1)
            }
            
            else -> null
        }
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
            text.contains(".toRecord()") || text.contains("_record") -> {
                // Could be expanded with more sophisticated analysis
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
}
