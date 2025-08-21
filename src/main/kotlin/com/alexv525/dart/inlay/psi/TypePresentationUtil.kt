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
     */
    fun isTrivialType(typeName: String?): Boolean {
        if (typeName == null) return true
        
        val formatted = formatType(typeName) ?: return true
        
        return when (formatted.lowercase()) {
            "dynamic", "object", "void" -> true
            else -> false
        }
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
}