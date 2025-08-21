package io.alexv525.dart.inlay.psi

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.lang.dart.ide.info.DartFunctionDescription
import com.jetbrains.lang.dart.psi.DartCallExpression
import com.jetbrains.lang.dart.psi.DartNamedArgument
import com.jetbrains.lang.dart.psi.DartReferenceExpression
import com.jetbrains.lang.dart.util.DartPsiImplUtil

/**
 * PSI-only calculator for parameter name inlay hints.
 * Strategy:
 * - For each DartCallExpression, map positional arguments to the resolved function's positional parameters.
 * - Show "name: " before each positional argument, with basic de-noising.
 */
object PsiParameterNameHintCalculator {

  private val PARAM_NAME_BLACKLIST = setOf("value", "index", "context", "it")

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

  fun calculateForCall(call: DartCallExpression): List<Pair<Int, String>> {
    val result = mutableListOf<Pair<Int, String>>()
    computeForCall(call, result)
    return result
  }

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
    val args = DartPsiImplUtil.getArguments(call) ?: return
    val argList = args.argumentList ?: return

    // Collect positional-only arguments: filter out expressions that are part of DartNamedArgument.
    val positionalArgs = argList.expressionList.filter { expr -> expr.parent !is DartNamedArgument }
    if (positionalArgs.isEmpty()) return

    val description = DartFunctionDescription.tryGetDescription(call) ?: return
    val params = description.parameters

    for ((posIndex, expr) in positionalArgs.withIndex()) {
      if (posIndex >= params.size) break
      val paramName = extractParamName(params[posIndex].toString()) ?: continue

      // De-noising rules
      if (paramName in PARAM_NAME_BLACKLIST) continue
      if (expr is DartReferenceExpression && expr.text == paramName) continue
      if (isSimpleLiteral(expr.text)) continue

      val offset = expr.textRange.startOffset
      out += offset to "$paramName: "
    }
  }

  /**
   * DartFunctionDescription parameter toString() typically looks like:
   *   "int count" or "T item" or "String? name"
   * Extract the last identifier as the parameter name.
   */
  private fun extractParamName(paramString: String): String? {
    val trimmed = paramString.trim()
    if (trimmed.isEmpty()) return null
    // Grab the last identifier-looking token
    val match = Regex("[A-Za-z_][A-Za-z0-9_]*$").find(trimmed) ?: return null
    return match.value
  }

  private fun isSimpleLiteral(text: String?): Boolean {
    if (text == null) return false
    val t = text.trim()
    if (t.isEmpty()) return false
    return when (t) {
      "true", "false", "null", "0", "1", "\"\"" -> true
      else -> false
    }
  }
}