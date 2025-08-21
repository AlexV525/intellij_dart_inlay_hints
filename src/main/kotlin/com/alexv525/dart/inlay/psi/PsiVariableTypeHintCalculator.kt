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
        val text = element.text?.trim() ?: return null

        // Look for simple variable declarations
        // Pattern: (var|final|late) identifier = expression;
        val varPattern = Regex("""(var|final|late)\s+(\w+)\s*=\s*([^;]+);?""")
        val match = varPattern.find(text)

        if (match != null) {
            val keyword = match.groupValues[1]
            val varName = match.groupValues[2]
            val expression = match.groupValues[3].trim()

            // Skip underscore variables
            if (varName in UNDERSCORE_NAMES) return null

            // Try to infer type from expression
            val inferredType = inferTypeFromExpressionText(expression)

            if (inferredType != null) {
                val formattedType = TypePresentationUtil.formatType(inferredType)
                if (formattedType != null && !TypePresentationUtil.isTrivialType(formattedType)) {
                    // Find position after the keyword but before the variable name
                    val keywordIndex = text.indexOf(keyword)
                    if (keywordIndex >= 0) {
                        // Find the end of whitespace after the keyword
                        val keywordEndIndex = keywordIndex + keyword.length
                        val afterKeyword = text.substring(keywordEndIndex)
                        val whitespaceMatch = Regex("^\\s*").find(afterKeyword)
                        val whitespaceLength = whitespaceMatch?.value?.length ?: 0
                        val offset = element.textRange.startOffset + keywordEndIndex + whitespaceLength
                        return offset to formattedType
                    }
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
