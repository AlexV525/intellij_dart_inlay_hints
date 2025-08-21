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
        return calculateForEachLoopHint(element, text) 
            ?: calculateDestructuringHint(element, text)
            ?: calculateSimpleVariableHint(element, text)
    }

    /**
     * Calculate hint for for-each loop variables: for (var x in iterable)
     */
    private fun calculateForEachLoopHint(element: PsiElement, text: String): Pair<Int, String>? {
        // Pattern: for (var identifier in expression)
        val forEachPattern = Regex("""for\s*\(\s*(var|final)\s+(\w+)\s+in\s+([^)]+)\)""")
        val match = forEachPattern.find(text)

        if (match != null) {
            val keyword = match.groupValues[1]
            val varName = match.groupValues[2]
            val iterableExpr = match.groupValues[3].trim()

            // Apply settings-based filtering
            val settings = com.alexv525.dart.inlay.settings.DartInlaySettings.getInstance()
            if (settings.shouldSuppressVariableName(varName)) return null

            // Infer element type from iterable
            val elementType = TypePresentationUtil.inferIterableElementType(iterableExpr)
                ?: if (settings.showUnknownType) "unknown" else return null

            val formattedType = TypePresentationUtil.formatType(elementType) ?: return null
            
            // Check suppression and complexity requirements
            if (TypePresentationUtil.isTrivialType(formattedType)) return null
            if (!TypePresentationUtil.meetsComplexityRequirement(formattedType)) return null

            // Find position after variable name for postfix placement
            val varNameIndex = text.indexOf(varName)
            if (varNameIndex >= 0) {
                val offset = element.textRange.startOffset + varNameIndex + varName.length
                return offset to ": $formattedType"
            }
        }

        return null
    }

    /**
     * Calculate hint for pattern/destructuring assignments: var (a, b) = (1, 's')
     */
    private fun calculateDestructuringHint(element: PsiElement, text: String): Pair<Int, String>? {
        // Pattern: (var|final) (name1, name2, ...) = expression
        val destructurePattern = Regex("""(var|final)\s+\(([^)]+)\)\s*=\s*([^;]+);?""")
        val match = destructurePattern.find(text)

        if (match != null) {
            val keyword = match.groupValues[1]
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

                // Find position after this variable name
                val varIndex = text.indexOf(varName, text.indexOf("("))
                if (varIndex >= 0) {
                    val offset = element.textRange.startOffset + varIndex + varName.length
                    hints.add(offset to ": $formattedType")
                }
            }
            
            // Return the first valid hint (IntelliJ will call this for each element)
            return hints.firstOrNull()
        }

        return null
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
            val keyword = match.groupValues[1]
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

            // Find position after variable name for postfix placement
            val varNameIndex = text.indexOf(varName)
            if (varNameIndex >= 0) {
                val offset = element.textRange.startOffset + varNameIndex + varName.length
                return offset to ": $formattedType"
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
