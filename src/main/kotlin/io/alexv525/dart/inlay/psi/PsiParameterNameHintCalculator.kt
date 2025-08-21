package io.alexv525.dart.inlay.psi

import com.jetbrains.lang.dart.ide.info.DartFunctionDescription
import com.jetbrains.lang.dart.psi.DartCallExpression
import com.jetbrains.lang.dart.psi.DartReferenceExpression
import com.jetbrains.lang.dart.util.DartPsiImplUtil

/**
 * PSI-only calculator for parameter name inlay hints.
 * Strategy:
 * - For each DartCallExpression, map positional arguments to the resolved function's positional parameters.
 * - Show "name: " before each positional argument, with basic de-noising.
 */
object PsiParameterNameHintCalculator {
  /**
   * Calculate hints for a specific call expression (more efficient)
   */
  fun calculateForCall(call: DartCallExpression): List<Pair<Int, String>> {
    val result = mutableListOf<Pair<Int, String>>()
    computeForCall(call, result)
    return result
  }

  /**
   * Get the function name for a call expression (used for hint info)
   */
  fun getFunctionName(call: DartCallExpression): String? {
    return try {
      // Try to get the function name from the call expression
      when (val expression = call.expression) {
        is DartReferenceExpression -> expression.text
        else -> expression?.text?.substringAfterLast('.')
      }
    } catch (_: Exception) {
      null
    }
  }

  private fun computeForCall(call: DartCallExpression, out: MutableList<Pair<Int, String>>) {
    try {
      // Additional validation: ensure we're in a .dart file
      val containingFile = call.containingFile
      if (containingFile?.virtualFile?.extension != "dart") {
        return
      }

      val args = DartPsiImplUtil.getArguments(call) ?: return
      val argList = args.argumentList ?: return
      val positionalArgs = argList.expressionList
      if (positionalArgs.isEmpty()) return

      val description = DartFunctionDescription.tryGetDescription(call) ?: return
      val params = description.parameters

      for ((index, expr) in positionalArgs.withIndex()) {
        if (index >= params.size) break

        val paramString = params[index].toString()
        val paramName = extractParamName(paramString) ?: continue

        // Validate the extracted parameter name
        if (paramName.isEmpty() || !isValidParameterName(paramName)) continue

        // Enhanced de-noising: skip when the argument name equals the parameter name
        if (shouldSkipHint(expr, paramName)) continue

        val offset = expr.textRange.startOffset
        out += offset to paramName
      }
    } catch (_: Exception) {
      // Silently handle any PSI-related exceptions
      // This ensures the plugin doesn't break if there are parsing issues
    }
  }

  /**
   * Validate that the extracted parameter name is reasonable
   */
  private fun isValidParameterName(name: String): Boolean {
    // Must be a valid identifier
    if (!name.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*"))) {
      return false
    }

    // Must not be a reserved word or common type
    val reserved = setOf("int", "String", "bool", "double", "num", "dynamic", "Object", "void",
                         "var", "final", "const", "required", "late", "this", "super")
    return name !in reserved
  }

  /**
   * Enhanced logic to determine if we should skip showing a hint
   */
  private fun shouldSkipHint(expr: com.intellij.psi.PsiElement, paramName: String): Boolean {
    // Skip if the argument is a reference with the same name as parameter
    if (expr is DartReferenceExpression && expr.text == paramName) return true

    // Skip if it's a simple literal that's self-explanatory
    val text = expr.text
    return text.length <= 3 && (text.matches(Regex("\\d+")) || text in setOf("true", "false", "null"))
  }

  /**
   * DartFunctionDescription parameter toString() typically looks like:
   *   "int count" or "T item" or "[int count]" for optional parameters
   * Extract the parameter name, handling complex cases.
   */
  private fun extractParamName(paramString: String): String? {
    val trimmed = paramString.trim()
    if (trimmed.isEmpty()) return null

    // Remove brackets for optional parameters like "[int count]"
    val withoutBrackets = trimmed.removeSurrounding("[", "]").trim()
    if (withoutBrackets.isEmpty()) return null

    // Handle different parameter formats:
    // - "int count" -> "count"
    // - "required String name" -> "name"
    // - "T item" -> "item"
    // - "this.field" -> "field"
    val parts = withoutBrackets.split(Regex("\\s+"))

    // Get the last part, which should be the parameter name
    val lastPart = parts.lastOrNull()?.trim() ?: return null

    // Handle "this.field" case
    if (lastPart.contains('.')) {
      return lastPart.substringAfterLast('.')
    }

    // Filter out common type keywords and modifiers
    val filteredParts = parts.filter { part ->
      val cleanPart = part.trim()
      cleanPart.isNotEmpty() &&
      !cleanPart.matches(Regex("(int|String|bool|double|num|dynamic|Object|void|var|final|const|required|late)")) &&
      !cleanPart.startsWith("@") &&  // Skip annotations
      !cleanPart.contains("<") &&    // Skip generic types
      !cleanPart.contains("(")       // Skip function types
    }

    // Return the last filtered part, or fallback to original last part
    return filteredParts.lastOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: lastPart
  }
}
