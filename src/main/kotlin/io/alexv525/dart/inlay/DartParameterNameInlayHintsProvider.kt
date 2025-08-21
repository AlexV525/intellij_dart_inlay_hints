package io.alexv525.dart.inlay

import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.lang.Language
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiFile
import com.jetbrains.lang.dart.DartLanguage
import io.alexv525.dart.inlay.psi.PsiParameterNameHintCalculator

class DartParameterNameInlayHintsProvider : InlayHintsProvider {

  companion object {
    const val PROVIDER_ID = "io.alexv525.dart.inlay.parameter.names"
  }

  override fun isLanguageSupported(language: Language): Boolean =
    language.isKindOf(DartLanguage.INSTANCE)

  override fun createCollector(file: PsiFile, editor: com.intellij.openapi.editor.Editor?): InlayHintsCollector =
    object : InlayHintsCollector {
      override fun collectHintsForFile(file: PsiFile, sink: InlayTreeSink) {
        if (DumbService.isDumb(file.project)) return

        val hints = PsiParameterNameHintCalculator.calculate(file)
        for ((offset, label) in hints) {
          sink.addPresentation(
            position = InlineInlayPosition(offset, relatedToPrevious = false),
            hintFormat = HintFormat.default
          ) {
            text(label)
          }
        }
      }
    }
}