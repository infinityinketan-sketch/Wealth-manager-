package com.example.ledger.ui

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class SafetyPage(
    val icon: ImageVector,
    val colors: List<Color>,
    val title: String,
    val lead: String,
    val points: List<Pair<ImageVector, String>>
)

private val pages = listOf(
    SafetyPage(
        Icons.Filled.PhoneAndroid,
        listOf(Color(0xFF5B3FE4), Color(0xFF8E7CFF)),
        "Your money stays on your phone",
        "Your messages, transactions and balances are kept only on this phone.",
        listOf(
            Icons.Filled.WifiOff to "No SMS, amount, balance or bank detail is ever sent to any server.",
            Icons.Filled.CheckCircle to "You sign in with Google only for your account and free trial: we keep your name, email and basic app details.",
            Icons.Filled.CheckCircle to "No ads, and we never sell or share your details."
        )
    ),
    SafetyPage(
        Icons.Filled.Sms,
        listOf(Color(0xFF00A88E), Color(0xFF2BC0E4)),
        "Only bank messages are read",
        "That is how the app tracks your spending automatically.",
        listOf(
            Icons.Filled.CheckCircle to "Only messages from banks, cards and billers are used.",
            Icons.Filled.Block to "Personal chats, OTPs and offers are ignored.",
            Icons.Filled.CheckCircle to "Reading happens on this phone; messages are never copied anywhere."
        )
    ),
    SafetyPage(
        Icons.Filled.Shield,
        listOf(Color(0xFFFF8A3D), Color(0xFFFF5C7A)),
        "It can never move your money",
        "Wealth Manager only reads. It cannot pay, transfer or log in to your bank.",
        listOf(
            Icons.Filled.Block to "It never asks for your bank password, PIN, CVV or OTP. Never share these with any app.",
            Icons.Filled.Block to "It has no access to your bank or UPI apps.",
            Icons.Filled.CheckCircle to "Nothing you see here can change a rupee in your accounts."
        )
    ),
    SafetyPage(
        Icons.Filled.VerifiedUser,
        listOf(Color(0xFF2B6CF6), Color(0xFF00C2FF)),
        "You stay in control",
        "Your data, your choices.",
        listOf(
            Icons.Filled.Fingerprint to "Lock the app with your fingerprint, face or screen lock (More › Settings).",
            Icons.Filled.CheckCircle to "Backups are locked with your own password and saved only where you choose.",
            Icons.Filled.CheckCircle to "Uninstalling the app removes all its data from your phone."
        )
    )
)

/** The safety walkthrough shown before anything else on first launch (and from More). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.size - 1
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Wealth Manager", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (!last) TextButton(onClick = onFinish) { Text("Skip") }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { i ->
                val p = pages[i]
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier.size(120.dp).clip(CircleShape).background(Brush.linearGradient(p.colors)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(p.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(60.dp))
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(p.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        p.lead,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        p.points.forEach { (icon, text) ->
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(icon, contentDescription = null, tint = p.colors.first(), modifier = Modifier.size(22.dp))
                                Text(text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pages.size) { i ->
                    val on = i == pager.currentPage
                    Box(
                        modifier = Modifier.padding(horizontal = 4.dp).height(8.dp).width(if (on) 24.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (on) pages[pager.currentPage].colors.first() else MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
            Button(
                onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = pages[pager.currentPage].colors.first())
            ) {
                Text(if (last) "Get started" else "Next", fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}
