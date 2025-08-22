/*
 * Copyright (c) 2025, Alex Li (AlexV525)
 */

package com.alexv525.dart.inlay.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiComment

/**
 * PSI-only calculator for variable type inlay hints.
 * Strategy:
 * - Find variable declarations using generic PSI traversal
 * - Use text-based heuristics for type inference (MVP approach)
 * - Emit hints as " Type" before variable identifier
 */
object PsiVariableTypeHintCalculator {

    private val UNDERSCORE_NAMES = setOf("_", "__", "___")

    /**
     * Calculate variable type hint for a specific element.
     * Returns (offset, hint_text) pair or null if no hint should be shown.
     */
    fun calculateForElement(element: PsiElement): Pair<Int, String>? {
        val settings = com.alexv525.dart.inlay.settings.DartInlaySettings.getInstance()
        
        // Skip if variable type hints are disabled
        if (!settings.enableVariableTypeHints) return null
        
        // CRITICAL FIX: Skip comments entirely to prevent parsing issues
        if (element is PsiComment || isInsideComment(element)) {
            return null
        }
        
        val text = element.text?.trim() ?: return null
        
        // CRITICAL FIX: Only process elements with complete variable declaration patterns
        // Don't process individual identifiers or fragments
        if (!containsVariableDeclarationPattern(text)) {
            return null
        }

        // Try to find variable declarations in this element's text
        return findVariableDeclarations(element, text)
    }

    
    /**
     * Check if the element is inside a comment
     */
    private fun isInsideComment(element: PsiElement): Boolean {
        var current = element.parent
        while (current != null) {
            if (current is PsiComment) {
                return true
            }
            current = current.parent
        }
        return false
    }
    
    /**
     * Check if the text contains any variable declaration patterns
     */
    private fun containsVariableDeclarationPattern(text: String): Boolean {
        // Check for basic patterns that indicate this might contain a variable declaration
        return text.contains("var ") || text.contains("final ") || text.contains("late ") || 
               (text.contains("for") && text.contains("in")) ||
               (text.contains("(") && text.contains(")") && text.contains("="))
    }
    
    /**
     * Find all variable declarations in the given text and return the first valid one
     */
    private fun findVariableDeclarations(element: PsiElement, text: String): Pair<Int, String>? {
        val settings = com.alexv525.dart.inlay.settings.DartInlaySettings.getInstance()
        
        // Try different patterns in order
        
        // 1. For-each loop pattern: for (var item in list) {
        val forEachPattern = Regex("""for\s*\(\s*(var|final)\s+(\w+)\s+in\s+([^)]+)\)""")
        val forMatch = forEachPattern.find(text)
        if (forMatch != null) {
            val varName = forMatch.groupValues[2]
            val iterableExpr = forMatch.groupValues[3].trim()
            
            if (!settings.shouldSuppressVariableName(varName)) {
                // Try to get broader context for variable resolution
                var contextElement = element
                for (i in 0..2) {
                    contextElement = contextElement.parent ?: break
                }
                val contextText = contextElement?.text
                
                val elementType = TypePresentationUtil.inferIterableElementTypeWithContext(iterableExpr, contextText ?: text)
                    ?: if (settings.showUnknownType) "unknown" else return null
                    
                val formattedType = TypePresentationUtil.formatType(elementType) ?: return null
                
                if (!TypePresentationUtil.isTrivialType(formattedType) && 
                    TypePresentationUtil.meetsComplexityRequirement(formattedType)) {
                    
                    // Find the variable name position and place hint before it
                    val varNameIndex = forMatch.range.first + forMatch.value.indexOf(varName)
                    val offset = element.textRange.startOffset + varNameIndex
                    return offset to formattedType
                }
            }
        }
        
        // 2. Destructuring pattern: var (a, b) = (1, 'hello')
        val destructurePattern = Regex("""(var|final)\s+\(([^)]+)\)\s*=\s*([^;]+)""")
        val destMatch = destructurePattern.find(text)
        if (destMatch != null) {
            val variables = destMatch.groupValues[2]
            val rhsExpression = destMatch.groupValues[3].trim()
            val varNames = variables.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val inferredTypes = TypePresentationUtil.inferDestructuringTypes(rhsExpression)
            
            // Find hints for each variable (return first valid one)
            for (i in varNames.indices) {
                val varName = varNames[i]
                if (settings.shouldSuppressVariableName(varName)) continue
                
                val inferredType = inferredTypes.getOrNull(i)
                val finalType = inferredType ?: if (settings.showUnknownType) "unknown" else continue
                val formattedType = TypePresentationUtil.formatType(finalType) ?: continue
                
                if (!TypePresentationUtil.isTrivialType(formattedType) && 
                    TypePresentationUtil.meetsComplexityRequirement(formattedType)) {
                    
                    // Find the variable position within the parentheses
                    val parenStartIndex = destMatch.range.first + destMatch.value.indexOf("(")
                    val varIndex = text.indexOf(varName, parenStartIndex)
                    if (varIndex >= 0) {
                        val offset = element.textRange.startOffset + varIndex
                        return offset to formattedType
                    }
                }
            }
        }
        
        // 3. Simple variable pattern: var name = expression
        val varPattern = Regex("""(var|final|late)\s+(\w+)\s*=\s*([^;,\)]+)""")
        val varMatch = varPattern.find(text)
        if (varMatch != null) {
            val varName = varMatch.groupValues[2]
            val expression = varMatch.groupValues[3].trim()
            
            if (!settings.shouldSuppressVariableName(varName)) {
                val inferredType = inferTypeFromExpressionText(expression)
                    ?: if (settings.showUnknownType) "unknown" else return null
                    
                val formattedType = TypePresentationUtil.formatType(inferredType) ?: return null
                
                if (!TypePresentationUtil.isTrivialType(formattedType) && 
                    TypePresentationUtil.meetsComplexityRequirement(formattedType)) {
                    
                    // Find the variable name position and place hint before it
                    val varNameIndex = varMatch.range.first + varMatch.value.indexOf(varName)
                    val offset = element.textRange.startOffset + varNameIndex
                    return offset to formattedType
                }
            }
        }
        
        return null
    }

    /**
     * Infer type from expression text using simple heuristics.
     */
    private fun inferTypeFromExpressionText(expression: String): String? {
        return TypePresentationUtil.getTypeFromLiteral(expression)
    }
}
