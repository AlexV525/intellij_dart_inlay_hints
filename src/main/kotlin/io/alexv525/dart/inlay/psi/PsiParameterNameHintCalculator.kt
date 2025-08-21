package io.alexv525.dart.inlay.psi

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
// TODO: Uncomment when Dart plugin is available in environment
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

  fun calculate(file: PsiFile): List<Pair<Int, String>> {
    val result = mutableListOf<Pair<Int, String>>()
    PsiTreeUtil.processElements(file) { element ->
      if (element is DartCallExpression) {
        computeForCall(element, result)
      }
      true
    }
    return result
  }
  
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
      val expression = call.expression
      when (expression) {
        is DartReferenceExpression -> expression.text
        else -> expression?.text?.substringAfterLast('.')
      }
    } catch (e: Exception) {
      null
    }
  }

  private fun computeForCall(call: DartCallExpression, out: MutableList<Pair<Int, String>>) {
    try {
      val args = DartPsiImplUtil.getArguments(call) ?: return
      val argList = args.argumentList ?: return
      val positionalArgs = argList.expressionList
      if (positionalArgs.isEmpty()) return

      val description = DartFunctionDescription.tryGetDescription(call) ?: return
      val params = description.parameters

      for ((index, expr) in positionalArgs.withIndex()) {
        if (index >= params.size) break
        val paramName = extractParamName(params[index].toString()) ?: continue

        // Enhanced de-noising: skip when the argument name equals the parameter name
        if (shouldSkipHint(expr, paramName)) continue

        val offset = expr.textRange.startOffset
        out += offset to "$paramName: "
      }
    } catch (e: Exception) {
      // Silently handle any PSI-related exceptions
      // This ensures the plugin doesn't break if there are parsing issues
    }
  }
  
  /**
   * Enhanced logic to determine if we should skip showing a hint
   */
  private fun shouldSkipHint(expr: com.intellij.psi.PsiElement, paramName: String): Boolean {
    // Skip if the argument is a reference with the same name as parameter
    if (expr is DartReferenceExpression && expr.text == paramName) return true
    
    // Skip if it's a simple literal that's self-explanatory
    val text = expr.text
    if (text.length <= 3 && (text.matches(Regex("\\d+")) || text in setOf("true", "false", "null"))) {
      return true
    }
    
    return false
  }

  /**
   * DartFunctionDescription parameter toString() typically looks like:
   *   "int count" or "T item"
   * Extract the last identifier as the parameter name.
   */
  private fun extractParamName(paramString: String): String? {
    val trimmed = paramString.trim()
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split(Regex("\\s+"))
    return parts.lastOrNull()
  }
}
