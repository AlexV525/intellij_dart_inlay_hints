package io.alexv525.dart.inlay.psi

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
// TODO: Uncomment when Dart plugin is available in environment
// import com.jetbrains.lang.dart.ide.info.DartFunctionDescription
// import com.jetbrains.lang.dart.psi.DartCallExpression
// import com.jetbrains.lang.dart.psi.DartReferenceExpression
// import com.jetbrains.lang.dart.util.DartPsiImplUtil

/**
 * PSI-only calculator for parameter name inlay hints.
 * Strategy:
 * - For each DartCallExpression, map positional arguments to the resolved function's positional parameters.
 * - Show "name: " before each positional argument, with basic de-noising.
 */
object PsiParameterNameHintCalculator {

  fun calculate(file: PsiFile): List<Pair<Int, String>> {
    // TODO: Implement when Dart plugin is available
    // For now, return empty list to allow compilation
    return emptyList()
    
    // Original implementation (commented out for compilation):
    /*
    val result = mutableListOf<Pair<Int, String>>()
    PsiTreeUtil.processElements(file) { element ->
      if (element is DartCallExpression) {
        computeForCall(element, result)
      }
      true
    }
    return result
    */
  }

  /* TODO: Uncomment when Dart plugin is available
  private fun computeForCall(call: DartCallExpression, out: MutableList<Pair<Int, String>>) {
    val args = DartPsiImplUtil.getArguments(call) ?: return
    val argList = args.argumentList ?: return
    val positionalArgs = argList.expressionList
    if (positionalArgs.isEmpty()) return

    val description = DartFunctionDescription.tryGetDescription(call) ?: return
    val params = description.parameters

    for ((index, expr) in positionalArgs.withIndex()) {
      if (index >= params.size) break
      val paramName = extractParamName(params[index].toString()) ?: continue

      // Basic de-noising: skip when the argument name equals the parameter name (e.g., foo(count))
      if (expr is DartReferenceExpression && expr.text == paramName) continue

      val offset = expr.textRange.startOffset
      out += offset to "$paramName: "
    }
  }
  */

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