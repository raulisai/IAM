@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.iam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp


/**
 * Activity que muestra la política de privacidad y explica por qué la app necesita
 * acceso a los datos de Health Connect.
 * 
 * Esta actividad se abre cuando el usuario hace clic en el enlace de política de privacidad
 * en la pantalla de permisos de Health Connect.
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PermissionsRationaleScreen(
                    onClose = { finish() }
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PermissionsRationaleScreen(onClose: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Política de Privacidad") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "¿Por qué necesitamos acceso a tus datos de salud?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Nuestra aplicación solicita acceso a los siguientes datos de Health Connect:",
                style = MaterialTheme.typography.bodyLarge
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val permissions = listOf(
                "Pasos diarios" to "Para monitorear tu actividad física diaria",
                "Frecuencia cardíaca" to "Para rastrear tu salud cardiovascular",
                "Sueño" to "Para analizar la calidad de tu descanso",
                "Distancia recorrida" to "Para calcular tu actividad física",
                "Calorías quemadas" to "Para estimar tu gasto energético",
                "Peso" to "Para seguimiento de tu estado físico"
            )
            
            permissions.forEach { (permission, reason) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "• $permission",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Cómo usamos tus datos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = """
                    • Tus datos de salud se envían de forma segura a nuestro backend para generar análisis personalizados.
                    • No compartimos tus datos con terceros sin tu consentimiento explícito.
                    • Puedes revocar el acceso en cualquier momento desde la configuración de Health Connect.
                    • Todos los datos se transmiten de forma encriptada.
                """.trimIndent(),
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Entendido")
            }
        }
    }
}
