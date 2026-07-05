package com.example.myapplication.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.ui.theme.KeepMomentsTheme
import com.example.myapplication.viewmodel.AuthUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(state: AuthUiState) {
        composeRule.setContent {
            KeepMomentsTheme {
                AuthScreen(onBackClick = {}, uiState = state, onSubmit = { _, _, _ -> })
            }
        }
    }

    @Test
    fun showsValidationErrorFromState() {
        setScreen(AuthUiState(errorMessage = "Введите email"))

        composeRule.onNodeWithText("Введите email").assertIsDisplayed()
    }

    @Test
    fun submitButtonIsEnabledWhenIdle() {
        setScreen(AuthUiState())

        composeRule.onNodeWithText("Войти").assertIsEnabled()
    }

    @Test
    fun submittingReplacesTheLabelWithALoader() {
        setScreen(AuthUiState(isSubmitting = true))

        composeRule.onNodeWithText("Войти").assertDoesNotExist()
    }

    @Test
    fun switchesFromLoginToRegister() {
        setScreen(AuthUiState())

        composeRule.onNodeWithText("Зарегистрироваться").performClick()

        composeRule.onNodeWithText("Создать аккаунт").assertIsDisplayed()
    }
}
