package com.peal.appscheduler.ui.screens.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject


/**
 * Created by Peal Mazumder on 23/2/25.
 */

@HiltViewModel
class HomeViewModel @Inject constructor(): ViewModel() {
    private val _homeState = MutableStateFlow(HomeScreenState())
    val homeState: StateFlow<HomeScreenState> = _homeState
}