package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.remote.SupabaseConfig
import com.example.model.SyncUiState
import com.example.ui.components.MuvantaWordmark
import com.example.ui.theme.ArchivalSurface
import com.example.ui.theme.ErrorRoseInk
import com.example.ui.theme.ErrorTerracotta
import com.example.ui.theme.ErrorTerracottaSoft
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.IvoryInk
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SecondaryInk
import com.example.ui.theme.WarmBrass
import com.example.ui.theme.WatchedMoss
import com.example.ui.theme.WatchedMossSoft

@Composable
fun AuthScreen(
    isSignUp: Boolean,
    syncState: SyncUiState,
    authBusy: Boolean,
    authError: String?,
    authNotice: String?,
    prefilledEmail: String = "",
    onSubmitSignIn: (String, String) -> Unit,
    onSubmitSignUp: (String, String) -> Unit,
    onSwitchMode: () -> Unit,
    onClearFeedback: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by remember(isSignUp) { mutableStateOf(if (!isSignUp) prefilledEmail else "") }
    var password by remember(isSignUp) { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(prefilledEmail, isSignUp) {
        if (!isSignUp && prefilledEmail.isNotBlank() && email.isBlank()) {
            email = prefilledEmail
        }
    }

    fun submit() {
        if (isSignUp) onSubmitSignUp(email, password) else onSubmitSignIn(email, password)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag(if (isSignUp) "sign_up_screen" else "sign_in_screen"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("auth_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = IvoryInk,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                MuvantaWordmark()
            }

            Spacer(modifier = Modifier.height(28.dp))

            Surface(
                color = ArchivalSurface,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isSignUp) "CREATE ACCOUNT · SUPABASE AUTH" else "SIGN IN · SUPABASE AUTH",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarmBrass,
                        )
                        Text(
                            text = if (isSignUp) "Join MUVANTA" else "Welcome Back",
                            style = MaterialTheme.typography.headlineMedium,
                            color = IvoryInk,
                        )
                        Text(
                            text = if (isSignUp) {
                                "Create your personal cinema archive account to sync your library, ratings, and notes across devices."
                            } else {
                                "Sign in with your email and password to sync your watchlist, ratings, and offline edits."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryInk,
                        )
                    }

                    val configSecurityWarning = SupabaseConfig.securityWarning
                    if (configSecurityWarning != null || !syncState.isSupabaseConfigured) {
                        Surface(
                            color = ErrorTerracottaSoft,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, ErrorTerracotta),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = configSecurityWarning
                                    ?: "Add SUPABASE_URL and SUPABASE_PUBLIC_KEY in AI Studio's Secrets panel to enable live authentication.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRoseInk,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }

                    if (!authNotice.isNullOrBlank()) {
                        Surface(
                            color = WatchedMossSoft,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, WatchedMoss),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_inline_notice"),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MarkEmailRead,
                                    contentDescription = null,
                                    tint = WatchedMoss,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = authNotice,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IvoryInk,
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it.take(120)
                            if (authError != null) onClearFeedback()
                        },
                        label = { Text("Email Address") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WarmBrass,
                            unfocusedBorderColor = HairlineBorder,
                            focusedTextColor = IvoryInk,
                            unfocusedTextColor = IvoryInk,
                            focusedLabelColor = WarmBrass,
                            unfocusedLabelColor = SecondaryInk,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_email_field"),
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it.take(128)
                            if (authError != null) onClearFeedback()
                        },
                        label = { Text(if (isSignUp) "Password (at least 8 characters)" else "Password") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password",
                                    tint = SecondaryInk,
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WarmBrass,
                            unfocusedBorderColor = HairlineBorder,
                            focusedTextColor = IvoryInk,
                            unfocusedTextColor = IvoryInk,
                            focusedLabelColor = WarmBrass,
                            unfocusedLabelColor = SecondaryInk,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_password_field"),
                    )

                    Button(
                        onClick = ::submit,
                        enabled = !authBusy && syncState.isOnline,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarmBrass,
                            contentColor = ObsidianBg,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("auth_submit_button"),
                    ) {
                        if (authBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = ObsidianBg,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = when {
                                !syncState.isOnline -> "Reconnect to Continue"
                                isSignUp -> "Create Account"
                                else -> "Sign In"
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    // Inline error message directly under the form
                    if (!authError.isNullOrBlank()) {
                        Surface(
                            color = ErrorTerracottaSoft,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, ErrorTerracotta),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_inline_error"),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = ErrorRoseInk,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = authError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ErrorRoseInk,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isSignUp) "Already have an account?" else "New to MUVANTA?",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryInk,
                        )
                        TextButton(
                            onClick = onSwitchMode,
                            modifier = Modifier.testTag("auth_switch_mode_button"),
                        ) {
                            Text(
                                text = if (isSignUp) "Sign In" else "Create Account",
                                style = MaterialTheme.typography.labelLarge,
                                color = WarmBrass,
                            )
                        }
                    }
                }
            }
        }
    }
}
