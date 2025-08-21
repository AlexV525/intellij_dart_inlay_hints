/*
 * Copyright (c) 2025, Alex Li (AlexV525)
 */

package com.alexv525.dart.inlay.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Settings for Dart inlay hints with persistent storage.
 * Manages variable type hints configuration including de-noising controls.
 */
@Service(Service.Level.APP)
@State(
    name = "DartInlaySettings",
    storages = [Storage("DartInlayHints.xml")]
)
class DartInlaySettings : PersistentStateComponent<DartInlaySettings> {

    // Variable type hints settings
    var enableVariableTypeHints: Boolean = true
    var suppressDynamic: Boolean = true
    var suppressTrivialBuiltins: Boolean = false  // Conservative default
    var suppressObviousLiterals: Boolean = true
    var showUnknownType: Boolean = false
    var minComplexity: Int = 0  // 0=simple, 1=generics, 2=nested generics, 3=function types
    var blacklist: Set<String> = setOf("_", "__", "___", "temp", "tmp")
    var maxFileSize: Int = 100000  // Max file size to process (chars)

    companion object {
        fun getInstance(): DartInlaySettings {
            return ApplicationManager.getApplication().getService(DartInlaySettings::class.java)
        }
    }

    override fun getState(): DartInlaySettings = this

    override fun loadState(state: DartInlaySettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    /**
     * Check if a variable name should be suppressed based on blacklist
     */
    fun shouldSuppressVariableName(name: String): Boolean {
        return name in blacklist || name.startsWith("_")
    }

    /**
     * Check if a type should be suppressed based on de-noising settings
     */
    fun shouldSuppressType(typeName: String?): Boolean {
        if (typeName == null) return true

        val formatted = typeName.lowercase().trim()
        
        // Always suppress dynamic if enabled
        if (suppressDynamic && formatted == "dynamic") return true
        
        // Suppress trivial built-ins if enabled
        if (suppressTrivialBuiltins) {
            when (formatted) {
                "object", "void", "int", "double", "string", "bool" -> return true
            }
        }
        
        return false
    }

    /**
     * Calculate type complexity heuristic for filtering
     */
    fun getTypeComplexity(typeName: String): Int {
        val name = typeName.trim()
        return when {
            name.contains("Function") || name.contains("=>") -> 3
            name.count { it == '<' } >= 2 -> 2  // Nested generics
            name.contains("<") && name.contains(">") -> 1  // Simple generics
            else -> 0  // Simple type
        }
    }
}