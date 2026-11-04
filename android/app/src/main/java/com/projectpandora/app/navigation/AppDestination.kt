package com.projectpandora.app.navigation

enum class MainTab(val route: String, val label: String) {
    HOME("home", "首页"),
    VIEWS("views", "视图"),
    LOGS("logs", "日志"),
    AI_MAP("ai-map", "AI地图"),
    PROFILE("profile", "我的"),
}

object AppRoute {
    const val TASK_CENTER = "task-center"
}
