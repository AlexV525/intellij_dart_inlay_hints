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
 * - Use text-based heuristics for type inference
 * - Emit hints as "Type " before variable identifier (prefix format)
 * - Handle all patterns: simple vars, destructuring, for-each loops
 */
object PsiVariableTypeHintCalculator {

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
        
        // Skip comments entirely to prevent parsing issues
        if (element is PsiComment || isInsideComment(element)) {
            return emptyList()
        }
        
        val text = element.text?.trim() ?: return emptyList()
        
        // Skip elements that are too small to contain meaningful variable declarations
        if (text.length < 10) return emptyList()
        
        // Skip if text doesn't contain variable declaration keywords
        if (!text.contains("var ") && !text.contains("final ") && !text.contains("late ") && !text.contains("for (")) {
            return emptyList()
        }

        val hints = mutableListOf<Pair<Int, String>>()
        
        // Focus on patterns that actually work reliably
        
        // 1. Simple variable declarations: var/final/late name = expression
        calculateSimpleVariableHint(element, text)?.let { hints.add(it) }
        
        // 2. Pattern/destructuring assignments: var (a, b) = (x, y)
        hints.addAll(calculateDestructuringHints(element, text))
        
        // 3. For-each loops: for (var x in iterable) - improved version
        hints.addAll(calculateForEachLoopHintsImproved(element, text))
        
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
     * Calculate hints for for-each loop variables: for (var x in iterable)
     */
    private fun calculateForEachLoopHints(element: PsiElement, text: String): List<Pair<Int, String>> {
        val hints = mutableListOf<Pair<Int, String>>()
        val settings = com.alexv525.dart.inlay.settings.DartInlaySettings.getInstance()
        
        // Pattern: for (var identifier in expression)
        val forEachPattern = Regex("""for\s*\(\s*(var|final)\s+(\w+)\s+in\s+([^)]+)\)""")
        val matches = forEachPattern.findAll(text)

        for (match in matches) {
            val varName = match.groupValues[2]
            val iterableExpr = match.groupValues[3].trim()

            // Apply settings-based filtering
            if (settings.shouldSuppressVariableName(varName)) continue

            // Get broader context for variable resolution
            val contextElement = getContextElement(element, 3)
            val contextText = contextElement?.text

            // Infer element type from iterable
            val elementType = TypePresentationUtil.inferIterableElementTypeWithContext(iterableExpr, contextText ?: text)
                ?: if (settings.showUnknownType) "unknown" else continue

            val formattedType = TypePresentationUtil.formatType(elementType) ?: continue
            
            // Check suppression and complexity requirements
            if (TypePresentationUtil.isTrivialType(formattedType)) continue
            if (!TypePresentationUtil.meetsComplexityRequirement(formattedType)) continue

            // Find position before variable name for prefix placement
            val varNameIndex = match.range.start + match.value.indexOf(varName)
            val offset = element.textRange.startOffset + varNameIndex
            hints.add(offset to formattedType)
        }

        return hints
    }

    /**
     * Calculate hints for pattern/destructuring assignments: var (a, b) = (1, 's')
     */
    private fun calculateDestructuringHints(element: PsiElement, text: String): List<Pair<Int, String>> {
        val hints = mutableListOf<Pair<Int, String>>()
        val settings = com.alexv525.dart.inlay.settings.DartInlaySettings.getInstance()
        
        // Pattern: (var|final) (name1, name2, ...) = expression
        val destructurePattern = Regex("""(var|final)\s+\(([^)]+)\)\s*=\s*([^;]+);?""")
        val matches = destructurePattern.findAll(text)

        for (match in matches) {
            val variables = match.groupValues[2]
            val rhsExpression = match.groupValues[3].trim()

            // Parse variable names
            val varNames = variables.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            
            // Infer types from RHS expression
            val inferredTypes = TypePresentationUtil.inferDestructuringTypes(rhsExpression)
            
            // Create hints for each variable
            for (i in varNames.indices) {
                val varName = varNames[i]
                if (settings.shouldSuppressVariableName(varName)) continue
                
                val inferredType = inferredTypes.getOrNull(i)
                val finalType = inferredType ?: if (settings.showUnknownType) "unknown" else continue
                
                val formattedType = TypePresentationUtil.formatType(finalType) ?: continue
                if (TypePresentationUtil.isTrivialType(formattedType)) continue
                if (!TypePresentationUtil.meetsComplexityRequirement(formattedType)) continue

                // Find position before this variable name for prefix placement
                val varIndex = match.range.start + match.value.indexOf(varName, match.value.indexOf("("))
                val offset = element.textRange.startOffset + varIndex
                hints.add(offset to formattedType)
            }
        }

        return hints
    }

    /**
     * Calculate hint for simple variable declarations: var name = expression
     */
    private fun calculateSimpleVariableHint(element: PsiElement, text: String): Pair<Int, String>? {
        val settings = com.alexv525.dart.inlay.settings.DartInlaySettings.getInstance()
        
        // Look for simple variable declarations
        // Pattern: (var|final|late) identifier = expression;
        val varPattern = Regex("""(var|final|late)\s+(\w+)\s*=\s*([^;]+);?""")
        val match = varPattern.find(text) ?: return null

        val varName = match.groupValues[2]
        val expression = match.groupValues[3].trim()

        // Apply settings-based filtering
        if (settings.shouldSuppressVariableName(varName)) return null

        // Try to infer type from expression
        val inferredType = inferTypeFromExpressionText(expression)
            ?: if (settings.showUnknownType) "unknown" else return null

        val formattedType = TypePresentationUtil.formatType(inferredType) ?: return null
        
        // Check suppression and complexity requirements  
        if (TypePresentationUtil.isTrivialType(formattedType)) return null
        if (!TypePresentationUtil.meetsComplexityRequirement(formattedType)) return null

        // Find position before variable name for prefix placement
        val varNameIndex = match.range.start + match.value.indexOf(varName)
        val offset = element.textRange.startOffset + varNameIndex
        return offset to formattedType
    }

    /**
     * Infer type from expression text using simple heuristics.
     */
    private fun inferTypeFromExpressionText(expression: String): String? {
        return TypePresentationUtil.getTypeFromLiteral(expression)
    }
    
    /**
     * Improved for-each loop hints calculation with better element handling.
     * Handles cases where for-loops might be split across PSI elements.
     */
    private fun calculateForEachLoopHintsImproved(element: PsiElement, text: String): List<Pair<Int, String>> {
        val hints = mutableListOf<Pair<Int, String>>()
        val settings = com.alexv525.dart.inlay.settings.DartInlaySettings.getInstance()
        
        // More flexible pattern matching for for-each loops
        // Handle both complete and partial matches
        
        // Pattern 1: Complete for-each loop in one element
        val completePattern = Regex("""for\s*\(\s*(var|final)\s+(\w+)\s+in\s+([^)]+)\)""")
        val completeMatches = completePattern.findAll(text)
        
        for (match in completeMatches) {
            val varName = match.groupValues[2]
            val iterableExpr = match.groupValues[3].trim()
            
            if (settings.shouldSuppressVariableName(varName)) continue
            
            val elementType = inferForEachElementType(iterableExpr)
                ?: if (settings.showUnknownType) "unknown" else continue
                
            val formattedType = TypePresentationUtil.formatType(elementType) ?: continue
            
            if (TypePresentationUtil.isTrivialType(formattedType)) continue
            if (!TypePresentationUtil.meetsComplexityRequirement(formattedType)) continue
            
            val varNameIndex = match.range.start + match.value.indexOf(varName)
            val offset = element.textRange.startOffset + varNameIndex
            hints.add(offset to formattedType)
        }
        
        // Pattern 2: Partial for-each - look for just the variable declaration part
        // This handles cases where PSI splits "for (var x" and "in iterable)" into different elements
        if (hints.isEmpty()) {
            val partialPattern = Regex("""for\s*\(\s*(var|final)\s+(\w+)\s*(?:in)?""")
            val partialMatch = partialPattern.find(text)
            
            if (partialMatch != null) {
                val varName = partialMatch.groupValues[2]
                if (!settings.shouldSuppressVariableName(varName)) {
                    // Try to find the iterable in broader context
                    val contextElement = getContextElement(element, 2)
                    val contextText = contextElement?.text
                    
                    if (contextText != null) {
                        val fullPattern = Regex("""for\s*\(\s*(?:var|final)\s+${Regex.escape(varName)}\s+in\s+([^)]+)\)""")
                        val fullMatch = fullPattern.find(contextText)
                        
                        if (fullMatch != null) {
                            val iterableExpr = fullMatch.groupValues[1].trim()
                            val elementType = inferForEachElementType(iterableExpr)
                            
                            if (elementType != null) {
                                val formattedType = TypePresentationUtil.formatType(elementType)
                                if (formattedType != null && 
                                    !TypePresentationUtil.isTrivialType(formattedType) &&
                                    TypePresentationUtil.meetsComplexityRequirement(formattedType)) {
                                    
                                    val varNameIndex = partialMatch.range.start + partialMatch.value.indexOf(varName)
                                    val offset = element.textRange.startOffset + varNameIndex
                                    hints.add(offset to formattedType)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return hints
    }
    
    /**
     * Dedicated method for inferring element types in for-each loops
     */
    private fun inferForEachElementType(iterableExpr: String): String? {
        // Use the existing type inference logic but with specific for-each handling
        return TypePresentationUtil.inferIterableElementType(iterableExpr)
    }

    /**
     * Get context element by traversing up the PSI tree
     */
    private fun getContextElement(element: PsiElement, levels: Int): PsiElement? {
        var current = element
        repeat(levels) {
            current = current.parent ?: return current
        }
        return current
    }
}