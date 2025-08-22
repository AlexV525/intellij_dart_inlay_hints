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
        val hints = calculateAllHintsForElement(element)
        return hints.firstOrNull()
    }

    /**
     * Calculate all variable type hints for a specific element.
     * Returns list of (offset, hint_text) pairs.
     */
    fun calculateAllHintsForElement(element: PsiElement): List<Pair<Int, String>> {
        val settings = com.alexv525.dart.inlay.settings.DartInlaySettings.getInstance()
        
        // Skip if variable type hints are disabled
        if (!settings.enableVariableTypeHints) return emptyList()
        
        // CRITICAL FIX: Skip comments entirely to prevent parsing issues
        if (element is PsiComment || isInsideComment(element)) {
            return emptyList()
        }
        
        val text = element.text?.trim() ?: return emptyList()

        // Try different patterns in order of specificity
        val hints = mutableListOf<Pair<Int, String>>()
        
        calculateForEachLoopHint(element, text)?.let { hints.add(it) }
        hints.addAll(calculateAllDestructuringHints(element, text))
        calculateSimpleVariableHint(element, text)?.let { hints.add(it) }
        
        return hints
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
     * Calculate hint for for-each loop variables: for (var x in iterable)
     */
    private fun calculateForEachLoopHint(element: PsiElement, text: String): Pair<Int, String>? {
        // Pattern: for (var identifier in expression)
        val forEachPattern = Regex("""for\s*\(\s*(var|final)\s+(\w+)\s+in\s+([^)]+)\)""")
        val match = forEachPattern.find(text)

        if (match != null) {
            val varName = match.groupValues[2]
            val iterableExpr = match.groupValues[3].trim()

            // Apply settings-based filtering
            val settings = com.alexv525.dart.inlay.settings.DartInlaySettings.getInstance()
            if (settings.shouldSuppressVariableName(varName)) return null

            // Try to get broader context for variable resolution
            var contextElement = element
            for (i in 0..2) {
                contextElement = contextElement.parent ?: break
            }
            val contextText = contextElement?.text

            // Infer element type from iterable
            val elementType = TypePresentationUtil.inferIterableElementTypeWithContext(iterableExpr, contextText ?: text)
                ?: if (settings.showUnknownType) "unknown" else return null

            val formattedType = TypePresentationUtil.formatType(elementType) ?: return null
            
            // Check suppression and complexity requirements
            if (TypePresentationUtil.isTrivialType(formattedType)) return null
            if (!TypePresentationUtil.meetsComplexityRequirement(formattedType)) return null

            // Find position before variable name for prefix placement
            val varNameIndex = text.indexOf(varName)
            if (varNameIndex >= 0) {
                val offset = element.textRange.startOffset + varNameIndex
                return offset to formattedType
            }
        }

        return null
    }

    /**
     * Calculate hint for pattern/destructuring assignments: var (a, b) = (1, 's')
     */
    private fun calculateDestructuringHint(element: PsiElement, text: String): Pair<Int, String>? {
        val hints = calculateAllDestructuringHints(element, text)
        return hints.firstOrNull()
    }

    /**
     * Calculate all hints for pattern/destructuring assignments: var (a, b) = (1, 's')
     */
    private fun calculateAllDestructuringHints(element: PsiElement, text: String): List<Pair<Int, String>> {
        // Pattern: (var|final) (name1, name2, ...) = expression
        val destructurePattern = Regex("""(var|final)\s+\(([^)]+)\)\s*=\s*([^;]+);?""")
        val match = destructurePattern.find(text)

        if (match != null) {
            val variables = match.groupValues[2]
            val rhsExpression = match.groupValues[3].trim()

            // Parse variable names
            val varNames = variables.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            
            // Infer types from RHS expression
            val inferredTypes = TypePresentationUtil.inferDestructuringTypes(rhsExpression)
            
            // Create hints for each variable
            val hints = mutableListOf<Pair<Int, String>>()
            val settings = com.alexv525.dart.inlay.settings.DartInlaySettings.getInstance()
            
            for (i in varNames.indices) {
                val varName = varNames[i]
                if (settings.shouldSuppressVariableName(varName)) continue
                
                val inferredType = inferredTypes.getOrNull(i)
                val finalType = inferredType ?: if (settings.showUnknownType) "unknown" else continue
                
                val formattedType = TypePresentationUtil.formatType(finalType) ?: continue
                if (TypePresentationUtil.isTrivialType(formattedType)) continue
                if (!TypePresentationUtil.meetsComplexityRequirement(formattedType)) continue

                // Find position before this variable name for prefix placement
                val varIndex = text.indexOf(varName, text.indexOf("("))
                if (varIndex >= 0) {
                    val offset = element.textRange.startOffset + varIndex
                    hints.add(offset to formattedType)
                }
            }
            
            return hints
        }

        return emptyList()
    }

    /**
     * Calculate hint for simple variable declarations: var name = expression
     */
    private fun calculateSimpleVariableHint(element: PsiElement, text: String): Pair<Int, String>? {
        // Look for simple variable declarations
        // Pattern: (var|final|late) identifier = expression;
        val varPattern = Regex("""(var|final|late)\s+(\w+)\s*=\s*([^;]+);?""")
        val match = varPattern.find(text)

        if (match != null) {
            val varName = match.groupValues[2]
            val expression = match.groupValues[3].trim()

            // Apply settings-based filtering
            val settings = com.alexv525.dart.inlay.settings.DartInlaySettings.getInstance()
            if (settings.shouldSuppressVariableName(varName)) return null

            // Try to infer type from expression
            val inferredType = inferTypeFromExpressionText(expression)
                ?: if (settings.showUnknownType) "unknown" else return null

            val formattedType = TypePresentationUtil.formatType(inferredType) ?: return null
            
            // Check suppression and complexity requirements  
            if (TypePresentationUtil.isTrivialType(formattedType)) return null
            if (!TypePresentationUtil.meetsComplexityRequirement(formattedType)) return null

            // Find position before variable name for prefix placement
            val varNameIndex = text.indexOf(varName)
            if (varNameIndex >= 0) {
                val offset = element.textRange.startOffset + varNameIndex
                return offset to formattedType
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
