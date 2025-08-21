/*
 * Copyright (c) 2025, Alex Li (AlexV525)
 */

package com.alexv525.dart.inlay.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import javax.swing.*
import javax.swing.border.TitledBorder

/**
 * Configurable for Dart inlay hints settings in the IDE Settings panel.
 */
class DartInlaySettingsConfigurable : SearchableConfigurable {

    private lateinit var mainPanel: JPanel
    private lateinit var enableVariableTypeHints: JBCheckBox
    private lateinit var suppressDynamic: JBCheckBox
    private lateinit var suppressTrivialBuiltins: JBCheckBox
    private lateinit var suppressObviousLiterals: JBCheckBox
    private lateinit var showUnknownType: JBCheckBox
    private lateinit var minComplexity: ComboBox<String>
    private lateinit var blacklistField: JBTextField
    private lateinit var maxFileSize: JBIntSpinner

    override fun getId(): String = "dart.inlay.settings"

    override fun getDisplayName(): String = "Dart Inlay Hints"

    override fun createComponent(): JComponent {
        val settings = DartInlaySettings.getInstance()

        // Create components
        enableVariableTypeHints = JBCheckBox("Show variable type hints", settings.enableVariableTypeHints)
        suppressDynamic = JBCheckBox("Suppress 'dynamic' types", settings.suppressDynamic)
        suppressTrivialBuiltins = JBCheckBox("Suppress trivial built-in types (int, String, bool, etc.)", settings.suppressTrivialBuiltins)
        suppressObviousLiterals = JBCheckBox("Suppress obvious literal types", settings.suppressObviousLiterals)
        showUnknownType = JBCheckBox("Show 'unknown' for unresolved types", settings.showUnknownType)
        
        minComplexity = ComboBox(arrayOf("All types", "Include generics", "Nested generics only", "Function types only"))
        minComplexity.selectedIndex = settings.minComplexity
        
        blacklistField = JBTextField(settings.blacklist.joinToString(", "))
        maxFileSize = JBIntSpinner(settings.maxFileSize, 1000, 1000000)

        // Create main panel with sections
        val variableSection = FormBuilder.createFormBuilder()
            .addComponent(enableVariableTypeHints)
            .addVerticalGap(5)
            .panel

        val deNoiseSection = FormBuilder.createFormBuilder()
            .addComponent(suppressDynamic)
            .addComponent(suppressTrivialBuiltins)
            .addComponent(suppressObviousLiterals)
            .addLabeledComponent("Minimum type complexity:", minComplexity)
            .addLabeledComponent("Blacklisted variable names:", blacklistField)
            .panel

        val advancedSection = FormBuilder.createFormBuilder()
            .addComponent(showUnknownType)
            .addLabeledComponent("Max file size to process (chars):", maxFileSize)
            .panel

        // Add borders to sections
        variableSection.border = TitledBorder("Variable Type Hints")
        deNoiseSection.border = TitledBorder("De-noising Controls")
        advancedSection.border = TitledBorder("Advanced Options")

        mainPanel = FormBuilder.createFormBuilder()
            .addComponent(variableSection)
            .addComponent(deNoiseSection)
            .addComponent(advancedSection)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        mainPanel.border = JBUI.Borders.empty(10)

        // Enable/disable controls based on main checkbox
        enableVariableTypeHints.addActionListener {
            updateControlsState()
        }
        updateControlsState()

        return mainPanel
    }

    private fun updateControlsState() {
        val enabled = enableVariableTypeHints.isSelected
        suppressDynamic.isEnabled = enabled
        suppressTrivialBuiltins.isEnabled = enabled
        suppressObviousLiterals.isEnabled = enabled
        showUnknownType.isEnabled = enabled
        minComplexity.isEnabled = enabled
        blacklistField.isEnabled = enabled
        maxFileSize.isEnabled = enabled
    }

    override fun isModified(): Boolean {
        val settings = DartInlaySettings.getInstance()
        return enableVariableTypeHints.isSelected != settings.enableVariableTypeHints ||
                suppressDynamic.isSelected != settings.suppressDynamic ||
                suppressTrivialBuiltins.isSelected != settings.suppressTrivialBuiltins ||
                suppressObviousLiterals.isSelected != settings.suppressObviousLiterals ||
                showUnknownType.isSelected != settings.showUnknownType ||
                minComplexity.selectedIndex != settings.minComplexity ||
                blacklistField.text != settings.blacklist.joinToString(", ") ||
                maxFileSize.number != settings.maxFileSize
    }

    override fun apply() {
        val settings = DartInlaySettings.getInstance()
        settings.enableVariableTypeHints = enableVariableTypeHints.isSelected
        settings.suppressDynamic = suppressDynamic.isSelected
        settings.suppressTrivialBuiltins = suppressTrivialBuiltins.isSelected
        settings.suppressObviousLiterals = suppressObviousLiterals.isSelected
        settings.showUnknownType = showUnknownType.isSelected
        settings.minComplexity = minComplexity.selectedIndex
        settings.blacklist = blacklistField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        settings.maxFileSize = maxFileSize.number
    }

    override fun reset() {
        val settings = DartInlaySettings.getInstance()
        enableVariableTypeHints.isSelected = settings.enableVariableTypeHints
        suppressDynamic.isSelected = settings.suppressDynamic
        suppressTrivialBuiltins.isSelected = settings.suppressTrivialBuiltins
        suppressObviousLiterals.isSelected = settings.suppressObviousLiterals
        showUnknownType.isSelected = settings.showUnknownType
        minComplexity.selectedIndex = settings.minComplexity
        blacklistField.text = settings.blacklist.joinToString(", ")
        maxFileSize.number = settings.maxFileSize
        updateControlsState()
    }
}