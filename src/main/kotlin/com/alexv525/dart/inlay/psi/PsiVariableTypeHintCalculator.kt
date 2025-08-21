/*
 * Copyright (c) 2025, Alex Li (AlexV525)
 */

package com.alexv525.dart.inlay.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

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
     * Calculate variable type hints for a file.
     * Returns list of (offset, hint_text) pairs.
     * @deprecated Use calculateForElement instead to avoid duplicate processing
     */
    fun calculate(file: PsiFile): List<Pair<Int, String>> {
        val result = mutableListOf<Pair<Int, String>>()
        
        // Use generic PSI traversal and text analysis for MVP
        PsiTreeUtil.processElements(file) { element ->
            processElement(element, result)
            true
        }
        
        return result
    }
    
    /**
     * Process a PSI element looking for variable declarations.
     * Uses text-based analysis to identify patterns like "var x = value;"
     */
    private fun processElement(element: PsiElement, out: MutableList<Pair<Int, String>>) {
        val text = element.text?.trim() ?: return
        
        // Look for simple variable declarations
        // Pattern: (var|final|late) identifier = expression;
        val varPattern = Regex("""(var|final|late)\s+(\w+)\s*=\s*([^;]+);?""")
        val match = varPattern.find(text)
        
        if (match != null) {
            val keyword = match.groupValues[1]
            val varName = match.groupValues[2]
            val expression = match.groupValues[3].trim()
            
            // Skip underscore variables
            if (varName in UNDERSCORE_NAMES) return
            
            // Try to infer type from expression
            val inferredType = inferTypeFromExpressionText(expression)
            
            if (inferredType != null) {
                val formattedType = TypePresentationUtil.formatType(inferredType)
                if (formattedType != null && !TypePresentationUtil.isTrivialType(formattedType)) {
                    // Find position after the keyword but before the variable name
                    val keywordIndex = text.indexOf(keyword)
                    if (keywordIndex >= 0) {
                        val keywordEndIndex = keywordIndex + keyword.length
                        val offset = element.textRange.startOffset + keywordEndIndex
                        out += offset to " $formattedType"
                    }
                }
            }
        }
        
        // Also look for for-in patterns
        // Pattern: for (var identifier in expression)
        val forInPattern = Regex("""for\s*\(\s*(var|final)\s+(\w+)\s+in\s+([^)]+)\)""")
        val forInMatch = forInPattern.find(text)
        
        if (forInMatch != null) {
            val varName = forInMatch.groupValues[2]
            
            if (varName !in UNDERSCORE_NAMES) {
                // For MVP, just show a generic type for for-in variables
                var keywordIndex = text.indexOf("var")
                if (keywordIndex < 0) keywordIndex = text.indexOf("final")
                if (keywordIndex >= 0) {
                    val keywordEndIndex = keywordIndex + if (text.indexOf("var") >= 0) 3 else 5 // "var" or "final"
                    val offset = element.textRange.startOffset + keywordEndIndex
                    // For now, we'll skip for-in to keep it simple
                    // out += offset to " dynamic"
                }
            }
        }
    }
    
    /**
     * Infer type from expression text using simple heuristics.
     */
    private fun inferTypeFromExpressionText(expression: String): String? {
        return TypePresentationUtil.getTypeFromLiteral(expression)
    }
}