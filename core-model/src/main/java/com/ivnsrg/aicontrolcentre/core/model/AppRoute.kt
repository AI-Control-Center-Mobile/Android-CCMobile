package com.ivnsrg.aicontrolcentre.core.model

object AppRoute {
    const val SETUP = "setup"
    const val PROJECTS = "projects"
    const val NEW_CHAT = "new-chat"
    const val SETTINGS = "settings"
    const val PROJECT = "project/{projectId}"
    const val THREAD = "thread/{threadId}"
    const val COMPARE = "compare/{threadId}"
    const val MODEL_PICKER = "model-picker/{mode}"

    fun project(projectId: Long) = "project/$projectId"
    fun thread(threadId: Long) = "thread/$threadId"
    fun compare(threadId: Long) = "compare/$threadId"
    fun modelPicker(mode: String) = "model-picker/$mode"
}
