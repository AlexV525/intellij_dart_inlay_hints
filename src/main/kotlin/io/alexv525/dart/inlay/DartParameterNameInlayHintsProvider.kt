package io.alexv525.dart.inlay

import com.intellij.codeInsight.hints.HintInfo
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.psi.PsiElement
import com.jetbrains.lang.dart.psi.DartCallExpression
import io.alexv525.dart.inlay.psi.PsiParameterNameHintCalculator

class DartParameterNameInlayHintsProvider : InlayParameterHintsProvider {
    override fun getParameterHints(element: PsiElement): List<InlayInfo> {
        if (element !is DartCallExpression) {
            return emptyList()
        }
        val hints = PsiParameterNameHintCalculator.calculate(element.containingFile)
        return hints
            .filter { element.textRange.contains(it.first) }
            .map { InlayInfo(it.second, it.first) }
    }

    override fun getHintInfo(element: PsiElement): HintInfo? {
        // TODO: Implement this method to provide more information about the hint.
        return null
    }

    override fun getDefaultBlackList(): Set<String> {
        return emptySet()
    }
}
