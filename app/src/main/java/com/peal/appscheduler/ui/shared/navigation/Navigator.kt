package com.peal.appscheduler.ui.shared.navigation

import androidx.navigation3.runtime.NavKey

class Navigator(private val state: NavigationState) {

    fun navigate(route: NavKey) {
        state.backStack.add(route)
    }

    fun goBack() {
        if (state.backStack.size > 1) {
            state.backStack.removeLastOrNull()
        }
    }

    fun popToRoot() {
        while (state.backStack.size > 1) {
            state.backStack.removeLastOrNull()
        }
    }
}