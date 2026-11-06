package com.ghostmago.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ghostmago.app.data.AppState
import com.ghostmago.app.ui.screens.AboutScreen
import com.ghostmago.app.ui.screens.AdvancedScreen
import com.ghostmago.app.ui.screens.AudioStegoScreen
import com.ghostmago.app.ui.screens.CalculatorDecoyScreen
import com.ghostmago.app.ui.screens.CommanderDashboardScreen
import com.ghostmago.app.ui.screens.CryptoToolsScreen
import com.ghostmago.app.ui.screens.DefenseScreen
import com.ghostmago.app.ui.screens.FileStegoScreen
import com.ghostmago.app.ui.screens.HomeScreen
import com.ghostmago.app.ui.screens.ImageStegoScreen
import com.ghostmago.app.ui.screens.LoginScreen
import com.ghostmago.app.ui.screens.MeshScreen
import com.ghostmago.app.ui.screens.OnboardingScreen
import com.ghostmago.app.ui.screens.QrScanScreen
import com.ghostmago.app.ui.screens.QrScreen
import com.ghostmago.app.ui.screens.ReadStegoScreen
import com.ghostmago.app.ui.screens.SendStegoScreen
import com.ghostmago.app.ui.screens.SettingsScreen
import com.ghostmago.app.ui.screens.SmsStegoScreen
import com.ghostmago.app.ui.screens.WifiDirectScreen
import com.ghostmago.app.ui.screens.OrgTrustScreen
import com.ghostmago.app.ui.screens.DeviceGuardScreen
import com.ghostmago.app.ui.screens.ComplianceScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed class Screen(val route: String) {
    data object Calculator : Screen("calculator")
    data object Login : Screen("login")
    data object Onboarding : Screen("onboarding")
    data object Home : Screen("home")
    data object SendStego : Screen("send_stego")
    data object ReadStego : Screen("read_stego")
    data object SmsStego : Screen("sms_stego")
    data object Mesh : Screen("mesh")
    data object CryptoTools : Screen("crypto_tools")
    data object ImageStego : Screen("image_stego")
    data object AudioStego : Screen("audio_stego")
    data object Qr : Screen("qr")
    data object QrScan : Screen("qr_scan")
    data object WifiDirect : Screen("wifi_direct")
    data object Advanced : Screen("advanced")
    data object Settings : Screen("settings")
    data object About : Screen("about")
    data object CommanderDash : Screen("commander_dash")
    data object Defense : Screen("defense")
    data object FileStego : Screen("file_stego")
    data object OrgTrust : Screen("org_trust")
    data object DeviceGuard : Screen("device_guard")
    data object Compliance : Screen("compliance")
}

@Composable
fun NavGraph() {
    val context = LocalContext.current
    // Show UI immediately — never block on EncryptedSharedPreferences / MasterKey
    LaunchedEffect(Unit) {
        if (!AppState.isReady) {
            withContext(Dispatchers.IO) {
                try {
                    withTimeoutOrNull(4_000L) {
                        AppState.load(context.applicationContext)
                    }
                } catch (_: Exception) {
                } finally {
                    AppState.isReady = true
                }
            }
        }
    }

    val nav = rememberNavController()
    // Cold start always opens calculator decoy (fast path). After unlock, normal flow.
    val start = Screen.Calculator.route

    NavHost(navController = nav, startDestination = start) {
        composable(Screen.Calculator.route) {
            CalculatorDecoyScreen(onUnlocked = {
                AppState.calculatorUnlocked = true
                nav.navigate(Screen.Login.route) {
                    popUpTo(Screen.Calculator.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Login.route) {
            LoginScreen(onSuccess = {
                val dest = if (AppState.onboardingDone) Screen.Home.route else Screen.Onboarding.route
                nav.navigate(dest) { popUpTo(Screen.Login.route) { inclusive = true } }
            })
        }
        composable(Screen.Onboarding.route) {
            OnboardingScreen(onDone = {
                nav.navigate(Screen.Home.route) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Home.route) { HomeScreen(nav) }
        composable(Screen.SendStego.route) { SendStegoScreen(onBack = { nav.popBackStack() }) }
        composable(Screen.ReadStego.route) { ReadStegoScreen(onBack = { nav.popBackStack() }) }
        composable(Screen.SmsStego.route) { SmsStegoScreen(onBack = { nav.popBackStack() }) }
        composable(Screen.Mesh.route) { MeshScreen(onBack = { nav.popBackStack() }, nav = nav) }
        composable(Screen.CryptoTools.route) { CryptoToolsScreen(onBack = { nav.popBackStack() }) }
        composable(Screen.ImageStego.route) { ImageStegoScreen(onBack = { nav.popBackStack() }) }
        composable(Screen.AudioStego.route) { AudioStegoScreen(onBack = { nav.popBackStack() }) }
        composable(Screen.Qr.route) { QrScreen(onBack = { nav.popBackStack() }) }
        composable(Screen.QrScan.route) { QrScanScreen(nav = nav, onBack = { nav.popBackStack() }) }
        composable(Screen.WifiDirect.route) { WifiDirectScreen(onBack = { nav.popBackStack() }) }
        composable(Screen.Advanced.route) { AdvancedScreen(onBack = { nav.popBackStack() }) }
        composable(Screen.Settings.route) { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable(Screen.About.route) { AboutScreen(onBack = { nav.popBackStack() }) }
        composable(Screen.CommanderDash.route) {
            CommanderDashboardScreen(onBack = { nav.popBackStack() }, nav = nav)
        }
        composable(Screen.Defense.route) { DefenseScreen(onBack = { nav.popBackStack() }) }
        composable(Screen.FileStego.route) { FileStegoScreen(onBack = { nav.popBackStack() }) }
        composable(Screen.OrgTrust.route) { OrgTrustScreen(onBack = { nav.popBackStack() }) }
        composable(Screen.DeviceGuard.route) { DeviceGuardScreen(onBack = { nav.popBackStack() }) }
        composable(Screen.Compliance.route) { ComplianceScreen(onBack = { nav.popBackStack() }) }
    }
}
