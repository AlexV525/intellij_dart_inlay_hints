/*
 * Copyright (c) 2025, Alex Li (AlexV525)
 */

package com.alexv525.dart.inlay.psi

import com.intellij.psi.PsiElement

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
        
        val text = element.text?.trim() ?: return null

        // Try different patterns in order of specificity
        // First check if this element represents a variable identifier
        return calculateSimpleVariableHint(element, text)
            ?: calculateForEachLoopHint(element, text) 
            ?: calculateDestructuringHint(element, text)
    }

    /**
     * Calculate hint for for-each loop variables: for (var x in iterable)
     * This function now looks for variable identifiers within for-loop contexts.
     */
    private fun calculateForEachLoopHint(element: PsiElement, text: String): Pair<Int, String>? {
        // Check if this element looks like a variable identifier
        if (!text.matches(Regex("^\\w+$"))) return null
        
        val settings = com.alexv525.dart.inlay.settings.DartInlaySettings.getInstance()
        if (settings.shouldSuppressVariableName(text)) return null
        
        // Look for for-each loop context in parent elements
        val forLoopContext = findForEachLoopContext(element, text) ?: return null
        val (keyword, varName, iterableExpr) = forLoopContext
        
        // Infer element type from iterable
        val elementType = TypePresentationUtil.inferIterableElementType(iterableExpr)
            ?: if (settings.showUnknownType) "unknown" else return null

        val formattedType = TypePresentationUtil.formatType(elementType) ?: return null
        
        // Check suppression and complexity requirements
        if (TypePresentationUtil.isTrivialType(formattedType)) return null
        if (!TypePresentationUtil.meetsComplexityRequirement(formattedType)) return null

        // Return hint for this variable element
        val offset = element.textRange.startOffset
        return offset to formattedType
    }

    /**
     * Calculate hint for pattern/destructuring assignments: var (a, b) = (1, 's')
     * This function now looks for variable identifiers within destructuring contexts.
     */
    private fun calculateDestructuringHint(element: PsiElement, text: String): Pair<Int, String>? {
        // Check if this element looks like a variable identifier
        if (!text.matches(Regex("^\\w+$"))) return null
        
        val settings = com.alexv525.dart.inlay.settings.DartInlaySettings.getInstance()
        if (settings.shouldSuppressVariableName(text)) return null
        
        // Look for destructuring context in parent elements
        val destructuringContext = findDestructuringContext(element, text) ?: return null
        val (keyword, varName, rhsExpression) = destructuringContext.first
        val position = destructuringContext.second
        
        // Infer types from RHS expression
        val inferredTypes = TypePresentationUtil.inferDestructuringTypes(rhsExpression)
        val inferredType = inferredTypes.getOrNull(position)
        val finalType = inferredType ?: if (settings.showUnknownType) "unknown" else return null
        
        val formattedType = TypePresentationUtil.formatType(finalType) ?: return null
        if (TypePresentationUtil.isTrivialType(formattedType)) return null
        if (!TypePresentationUtil.meetsComplexityRequirement(formattedType)) return null

        // Return hint for this variable element
        val offset = element.textRange.startOffset
        return offset to formattedType
    }

    /**
     * Calculate hint for simple variable declarations: var name = expression
     * This function now works with individual PSI elements that might just contain the variable name.
     */
    private fun calculateSimpleVariableHint(element: PsiElement, text: String): Pair<Int, String>? {
        // Check if this element looks like a variable identifier
        if (!text.matches(Regex("^\\w+$"))) return null
        
        val settings = com.alexv525.dart.inlay.settings.DartInlaySettings.getInstance()
        if (settings.shouldSuppressVariableName(text)) return null

        // Look at the parent context to find the variable declaration pattern
        val parent = element.parent
        val grandParent = parent?.parent
        val greatGrandParent = grandParent?.parent
        
        // Try to find the variable declaration context by looking at surrounding text
        val contextText = findVariableDeclarationContext(element) ?: return null
        
        // Extract the assignment expression from context
        val assignmentPattern = Regex("""(var|final|late)\s+${Regex.escape(text)}\s*=\s*([^;,\)]+)""")
        val match = assignmentPattern.find(contextText) ?: return null
        
        val keyword = match.groupValues[1]
        val expression = match.groupValues[2].trim()

        // Try to infer type from expression
        val inferredType = inferTypeFromExpressionText(expression)
            ?: if (settings.showUnknownType) "unknown" else return null

        val formattedType = TypePresentationUtil.formatType(inferredType) ?: return null
        
        // Check suppression and complexity requirements  
        if (TypePresentationUtil.isTrivialType(formattedType)) return null
        if (!TypePresentationUtil.meetsComplexityRequirement(formattedType)) return null

        // Find position before variable name for prefix placement
        val offset = element.textRange.startOffset
        return offset to formattedType
    }

    /**
     * Infer type from expression text using simple heuristics.
     */
    private fun inferTypeFromExpressionText(expression: String): String? {
        return TypePresentationUtil.getTypeFromLiteral(expression)
    }
    
    /**
     * Find the variable declaration context by looking at parent elements
     */
    private fun findVariableDeclarationContext(element: PsiElement): String? {
        // Try different levels of parent context
        var current = element
        for (i in 0..3) {
            val contextText = current.text
            if (contextText.contains("=") && (contextText.contains("var ") || contextText.contains("final ") || contextText.contains("late "))) {
                return contextText
            }
            current = current.parent ?: break
        }
        return null
    }
    
    /**
     * Find for-each loop context for a variable identifier
     * Returns triple of (keyword, varName, iterableExpr) or null if not in for-each context
     */
    private fun findForEachLoopContext(element: PsiElement, varName: String): Triple<String, String, String>? {
        // Look in parent contexts for for-each loop pattern
        var current = element
        for (i in 0..4) {
            val contextText = current.text
            if (contextText.contains("for") && contextText.contains("in")) {
                // Try to match for-each pattern in this context
                val forEachPattern = Regex("""for\s*\(\s*(var|final)\s+${Regex.escape(varName)}\s+in\s+([^)]+)\)""")
                val match = forEachPattern.find(contextText)
                if (match != null) {
                    val keyword = match.groupValues[1]
                    val iterableExpr = match.groupValues[2].trim()
                    return Triple(keyword, varName, iterableExpr)
                }
            }
            current = current.parent ?: break
        }
        return null
    }
    
    /**
     * Find destructuring context for a variable identifier
     * Returns quadruple of (keyword, varName, rhsExpression, position) or null if not in destructuring context
     */
    private fun findDestructuringContext(element: PsiElement, varName: String): Pair<Triple<String, String, String>, Int>? {
        // Look in parent contexts for destructuring pattern
        var current = element
        for (i in 0..4) {
            val contextText = current.text
            if (contextText.contains("(") && contextText.contains(")") && contextText.contains("=")) {
                // Try to match destructuring pattern in this context
                val destructurePattern = Regex("""(var|final)\s+\(([^)]+)\)\s*=\s*([^;]+)""")
                val match = destructurePattern.find(contextText)
                if (match != null) {
                    val keyword = match.groupValues[1]
                    val variables = match.groupValues[2]
                    val rhsExpression = match.groupValues[3].trim()
                    
                    // Parse variable names and find position of this variable
                    val varNames = variables.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val position = varNames.indexOf(varName)
                    
                    if (position >= 0) {
                        return Pair(Triple(keyword, varName, rhsExpression), position)
                    }
                }
            }
            current = current.parent ?: break
        }
        return null
    }
}
