package io.alexv525.dart.inlay

import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import io.alexv525.dart.inlay.psi.PsiParameterNameHintCalculator
import javax.swing.JPanel

class DartParameterNameInlayHintsProvider : InlayHintsProvider<NoSettings> {

  companion object {
    const val PROVIDER_ID = "io.alexv525.dart.inlay.parameter.names"
  }

  override val key = com.intellij.codeInsight.hints.SettingsKey<NoSettings>("dart.parameter.names")
  override val name = "Parameter Names"
  override val previewText: String? = null

  override fun getCollectorFor(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink): InlayHintsCollector {
    return object : InlayHintsCollector {
      override fun collect(element: com.intellij.psi.PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        // TODO: Implement when Dart plugin is available
        // val hints = PsiParameterNameHintCalculator.calculate(file)
        // for ((offset, text) in hints) {
        //   if (offset >= element.textRange.startOffset && offset < element.textRange.endOffset) {
        //     sink.addInlineElement(offset, false, text, false)
        //   }
        // }
        return true
      }
    }
  }

  override fun createSettings() = NoSettings()

  override fun createConfigurable(settings: NoSettings) = object : com.intellij.codeInsight.hints.ImmediateConfigurable {
    override fun createComponent(listener: com.intellij.codeInsight.hints.ChangeListener) = JPanel()
  }

  override fun isLanguageSupported(language: Language): Boolean = language.id == "Dart"
}