package io.github.corgisolutions.ruray.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.corgisolutions.ruray.R
import io.github.corgisolutions.ruray.data.db.VlessHost
import io.github.corgisolutions.ruray.utils.CountryUtils
import io.github.corgisolutions.ruray.utils.getHostDisplay
import io.github.corgisolutions.ruray.utils.parseVlessLink

@Composable
fun HostItem(
    host: VlessHost, 
    isActive: Boolean, 
    isTesting: Boolean, 
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onClick: () -> Unit
) {
    val latencyText = if (isTesting) "..."
                      else if (host.latency > 0) "${host.latency}${stringResource(R.string.ms)}"
                      else if (host.failureCount > 0) stringResource(R.string.timeout)
                      else stringResource(R.string.na)

    val containerColor = when {
        isActive -> Color(0xFF1564BF)
        host.isWorking -> Color(0xFF263238)
        else -> MaterialTheme.colorScheme.surface
    }
    
    val textColor = if (!isActive && host.failureCount > 0) Color(0xFFEF5350) else Color.White
    val contentColor = Color.White
    val dimColor = contentColor.copy(alpha = 0.5f)
    val borderColor = if (isActive) Color(0xFF60AEEC) else Color.Transparent
    
    val display = remember(host.link) { getHostDisplay(host.link) }
    val details = remember(host.link) { parseVlessLink(host.link) }
    
    val flag = remember(host.countryCode) { CountryUtils.getFlagEmoji(host.countryCode) }
    
    Card(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = !isTesting && !isActive,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .border(
                width = if (isActive) 1.dp else 0.dp,
                color = borderColor,
                shape = CardDefaults.shape
            ),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            disabledContainerColor = containerColor, 
            contentColor = contentColor,
            disabledContentColor = contentColor      
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (flag != "🌐") {
                        Text(flag, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = display,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
                
                if (details != null) {
                    Text(
                        text = details.uuid,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = dimColor,
                        maxLines = 1,
                        fontSize = 10.sp
                    )
                    val sni = details.params["sni"] ?: ""
                    val pbk = details.params["pbk"]?.take(8) ?: ""
                    if (sni.isNotEmpty() || pbk.isNotEmpty()) {
                        Text(
                            text = "sni:$sni pbk:$pbk...",
                            style = MaterialTheme.typography.labelSmall,
                            color = dimColor,
                            maxLines = 1,
                            fontSize = 10.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(horizontalAlignment = Alignment.End) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = contentColor
                    )
                } else {
                    Text(
                        text = latencyText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (host.failureCount > 0) textColor else contentColor
                    )
                    if (isActive) {
                        Text(
                            text = stringResource(R.string.active),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64B5F6),
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}
