package io.alexv525.dart.inlay

// Temporarily using older inlay hints API for compilation test
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import javax.swing.JPanel

class DartParameterNameInlayHintsProvider : InlayHintsProvider<NoSettings> {

  companion object {
    const val PROVIDER_ID = "io.alexv525.dart.inlay.parameter.names"
  }

  override val key = com.intellij.codeInsight.hints.SettingsKey<NoSettings>("dart.parameter.names")
  override val name = "Parameter Names"
  override val previewText: String? = null

  override fun createCollector(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink): InlayHintsCollector {
    return object : InlayHintsCollector {
      override fun collect(element: com.intellij.psi.PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        // TODO: Implement when Dart plugin is available
        return true
      }
    }
  }

  override fun createSettings() = NoSettings()
  override fun getCollectorFor(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink) =
    createCollector(file, editor, settings, sink)

  override fun createConfigurable(settings: NoSettings) = object : com.intellij.codeInsight.hints.ImmediateConfigurable {
    override fun createComponent(listener: com.intellij.codeInsight.hints.ChangeListener) = JPanel()
  }

  override fun isLanguageSupported(language: Language): Boolean = language.id == "Dart"
}