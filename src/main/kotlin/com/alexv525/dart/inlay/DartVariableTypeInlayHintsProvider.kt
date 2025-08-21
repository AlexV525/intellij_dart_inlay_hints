/*
 * Copyright (c) 2025, Alex Li (AlexV525)
 */

@file:Suppress("UnstableApiUsage")

package com.alexv525.dart.inlay

import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.alexv525.dart.inlay.psi.PsiVariableTypeHintCalculator

/**
 * Inlay hints provider for Dart variable type hints using the modern IntelliJ API.
 * Shows type information for implicitly typed local variables.
 */
@Suppress("UnstableApiUsage")
class DartVariableTypeInlayHintsProvider : InlayHintsProvider<NoSettings> {
    
    override val key: SettingsKey<NoSettings> = SettingsKey("dart.variable.type.hints")
    
    override val name: String = "Variable Type Hints"
    
    override val previewText: String? = """
        var name = "John";    // : String
        final age = 25;       // : int
        late value = true;    // : bool
    """.trimIndent()
    
    override fun getCollectorFor(
        file: PsiFile, 
        editor: Editor, 
        settings: NoSettings, 
        sink: InlayHintsSink
    ): InlayHintsCollector? {
        // Only apply to .dart files
        if (file.virtualFile?.extension != "dart") {
            return null
        }
        
        return DartVariableTypeInlayHintsCollector(editor, sink)
    }
    
    override fun createSettings(): NoSettings = NoSettings()
    
    override fun createConfigurable(settings: NoSettings): com.intellij.codeInsight.hints.ImmediateConfigurable {
        return object : com.intellij.codeInsight.hints.ImmediateConfigurable {
            override fun createComponent(listener: com.intellij.codeInsight.hints.ChangeListener): javax.swing.JComponent {
                return javax.swing.JLabel("No configuration needed")
            }
        }
    }
    
    override val description: String = "Show inferred type information for implicitly typed variables"
    
    override val group: com.intellij.codeInsight.hints.InlayGroup
        get() = com.intellij.codeInsight.hints.InlayGroup.TYPES_GROUP
}

/**
 * Collector that computes and displays variable type hints.
 */
private class DartVariableTypeInlayHintsCollector(
    private val editor: Editor,
    private val sink: InlayHintsSink
) : FactoryInlayHintsCollector(editor) {
    
    override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        val file = element.containingFile ?: return true
        
        // Calculate hints for the entire file (more efficient than per-element)
        val hints = PsiVariableTypeHintCalculator.calculate(file)
        
        for ((offset, hintText) in hints) {
            // Add the hint at the specified offset
            sink.addInlineElement(
                offset = offset,
                relatesToPrecedingText = true,
                presentation = factory.text(hintText),
                placeAtTheEndOfLine = false
            )
        }
        
        return true
    }
}