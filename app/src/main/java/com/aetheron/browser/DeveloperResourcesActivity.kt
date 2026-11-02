package com.aetheron.browser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class ResourceCategory(
    val name: String,
    val description: String,
    val anchor: String
)

class DeveloperResourcesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                DeveloperResourcesScreen(onBack = { finish() }, onOpenCategory = { anchor ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ripienaar/free-for-dev#$anchor")))
                })
            }
        }
    }
}

@Composable
private fun DeveloperResourcesScreen(
    onBack: () -> Unit,
    onOpenCategory: (String) -> Unit
) {
    val categories = listOf(
        ResourceCategory("Cloud providers", "Always-free cloud infrastructure and compute offers", "major-cloud-providers"),
        ResourceCategory("APIs, data & machine learning", "Free API tiers, datasets and ML services", "apis-data-and-ml"),
        ResourceCategory("Generative AI", "AI platforms, model access and developer APIs", "generative-ai"),
        ResourceCategory("Backend as a Service", "Hosted backend, authentication and app services", "baas"),
        ResourceCategory("Databases", "Managed SQL, NoSQL and data storage services", "managed-data-services"),
        ResourceCategory("CI & CD", "Build, test and deploy projects", "ci-and-cd"),
        ResourceCategory("Hosting & PaaS", "Deploy websites, apps and backend services", "paas"),
        ResourceCategory("Web hosting", "Static and dynamic website hosting", "web-hosting"),
        ResourceCategory("Domains & DNS", "Domain, DNS and network tools", "domain"),
        ResourceCategory("Security & identity", "Authentication, certificates and security tools", "security-and-pki"),
        ResourceCategory("Monitoring & analytics", "Logs, uptime, metrics and product analytics", "monitoring"),
        ResourceCategory("Developer tools", "Code editors, repositories and team utilities", "ide-and-code-editing")
    )
    var query by remember { mutableStateOf("") }
    val filtered = categories.filter {
        it.name.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true)
    }
    val ink = Color(0xFF182235)
    val muted = Color(0xFF758295)
    val blue = Color(0xFF3268D9)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Free Developer Resources", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
            Text(
                "Discover free tiers for tools, hosting, APIs and infrastructure.",
                color = muted,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search categories") },
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                contentPadding = PaddingValues(bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.name }) { category ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenCategory(category.anchor) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F8FD)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = blue)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(category.name, color = ink, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text(category.description, color = muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Open  →", color = blue, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                item {
                    Text(
                        "Source: ripienaar/free-for-dev on GitHub. Free-tier limits and availability can change; verify details with each provider before signing up.",
                        color = muted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 10.dp, bottom = 12.dp)
                    )
                }
            }
        }
    }
}
