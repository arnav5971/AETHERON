package com.aetheron.browser

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperResourcesScreen(
    onBack: () -> Unit,
    onOpenCategory: (String) -> Unit
) {
    val categories = listOf(
        ResourceCategory("Major cloud providers", "Always-free cloud infrastructure and compute offers", "major-cloud-providers"),
        ResourceCategory("Cloud management", "Tools for managing cloud infrastructure", "cloud-management-solutions"),
        ResourceCategory("Analytics & statistics", "Analytics, events and usage statistics", "analytics-events-and-statistics"),
        ResourceCategory("APIs, data & ML", "Free API tiers, datasets and machine learning services", "apis-data-and-ml"),
        ResourceCategory("Artifact repositories", "Package and build artifact repositories", "artifact-repos"),
        ResourceCategory("Backend as a Service", "Hosted backend, authentication and app services", "baas"),
        ResourceCategory("Generative AI", "AI platforms, models and developer APIs", "generative-ai"),
        ResourceCategory("CI & CD", "Build, test and deploy projects", "ci-and-cd"),
        ResourceCategory("Managed databases", "Managed SQL, NoSQL and data storage services", "managed-data-services"),
        ResourceCategory("DNS & domains", "Domain registration and DNS tools", "dns"),
        ResourceCategory("Monitoring", "Logs, uptime, metrics and product analytics", "monitoring"),
        ResourceCategory("Security & PKI", "Certificates and security tools", "security-and-pki"),
        ResourceCategory("Source code repositories", "Code hosting and repository services", "source-code-repos"),
        ResourceCategory("Web hosting", "Static and dynamic website hosting", "web-hosting"),
        ResourceCategory("Other free resources", "More free services and developer resources", "other-free-resources")
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
