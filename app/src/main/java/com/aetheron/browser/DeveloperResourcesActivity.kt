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

private val developerResourceCategories = listOf(
    ResourceCategory("Major cloud providers", "Always-free limits from major cloud platforms", "major-cloud-providers"),
    ResourceCategory("Cloud management solutions", "Tools for managing and operating cloud infrastructure", "cloud-management-solutions"),
    ResourceCategory("Analytics, events & statistics", "Product analytics, event tracking and usage statistics", "analytics-events-and-statistics"),
    ResourceCategory("APIs, data & ML", "Free APIs, datasets, machine-learning and data services", "apis-data-and-ml"),
    ResourceCategory("Artifact repositories", "Package registries and build-artifact storage", "artifact-repos"),
    ResourceCategory("Backend as a Service (BaaS)", "Hosted backend, database and application services", "baas"),
    ResourceCategory("Low-code platforms", "Build applications and workflows with less code", "low-code-platform"),
    ResourceCategory("CDN & protection", "Content delivery, edge services and traffic protection", "cdn-and-protection"),
    ResourceCategory("CI & CD", "Continuous integration, testing and deployment services", "ci-and-cd"),
    ResourceCategory("Content management systems", "Hosted and developer-friendly content management", "cms"),
    ResourceCategory("Code generation", "Code generation tools and developer productivity services", "code-generation"),
    ResourceCategory("Code quality", "Static analysis, linting and code-quality platforms", "code-quality"),
    ResourceCategory("Code search & browsing", "Search, navigate and understand source code", "code-search-and-browsing"),
    ResourceCategory("Crash & exception handling", "Crash reporting, error tracking and exception monitoring", "crash-and-exception-handling"),
    ResourceCategory("Data visualization on maps", "Mapping, geocoding and location visualization", "data-visualization-on-maps"),
    ResourceCategory("Managed data services", "Managed SQL, NoSQL, cache and data platforms", "managed-data-services"),
    ResourceCategory("Design & UI", "Design systems, UI assets and interface tools", "design-and-ui"),
    ResourceCategory("Developer blogging sites", "Platforms for publishing technical articles", "dev-blogging-sites"),
    ResourceCategory("DNS", "DNS hosting, management and related services", "dns"),
    ResourceCategory("Docker related", "Container registries and Docker ecosystem tools", "docker-related"),
    ResourceCategory("Domains", "Domain-related services and developer offers", "domain"),
    ResourceCategory("Education & career development", "Learning, training and career resources for developers", "education-and-career-development"),
    ResourceCategory("Email", "Email delivery, testing and mailbox services", "email"),
    ResourceCategory("Feature toggle management", "Feature flags and controlled feature rollouts", "feature-toggles-management-platforms"),
    ResourceCategory("Fonts", "Developer-friendly fonts and typography resources", "font"),
    ResourceCategory("Forms", "Hosted forms and form-processing services", "forms"),
    ResourceCategory("Generative AI", "Generative AI platforms, models and developer APIs", "generative-ai"),
    ResourceCategory("Infrastructure as a Service (IaaS)", "On-demand compute, networking and infrastructure", "iaas"),
    ResourceCategory("IDE & code editing", "Online IDEs, editors and cloud development environments", "ide-and-code-editing"),
    ResourceCategory("Mobile number verification APIs", "Phone verification APIs and SDKs", "international-mobile-number-verification-api-and-sdk"),
    ResourceCategory("Issue tracking & project management", "Track issues, tasks, releases and team projects", "issue-tracking-and-project-management"),
    ResourceCategory("Log management", "Collect, search and analyze application logs", "log-management"),
    ResourceCategory("Mobile app distribution & feedback", "Distribute test builds and collect app feedback", "mobile-app-distribution-and-feedback"),
    ResourceCategory("Management systems", "Hosted management and administration systems", "management-system"),
    ResourceCategory("Messaging & streaming", "Messaging queues, event streams and real-time services", "messaging-and-streaming"),
    ResourceCategory("Miscellaneous", "Useful free developer services across other areas", "miscellaneous"),
    ResourceCategory("Monitoring", "Uptime, metrics, observability and monitoring tools", "monitoring"),
    ResourceCategory("Platform as a Service (PaaS)", "Managed application platforms and deployment services", "paas"),
    ResourceCategory("Package build systems", "Build, package and release automation services", "package-build-system"),
    ResourceCategory("Payment & billing integration", "Payment processing and billing developer tools", "payment-and-billing-integration"),
    ResourceCategory("Privacy management", "Privacy, consent and data-rights management tools", "privacy-management"),
    ResourceCategory("Screenshot APIs", "APIs for website screenshots and page capture", "screenshot-apis"),
    ResourceCategory("Flutter & iOS build services", "Flutter tooling and iOS build options without a Mac", "flutter-related-and-building-ios-apps-without-mac"),
    ResourceCategory("Search", "Hosted search engines and search APIs", "search"),
    ResourceCategory("Security & PKI", "Security tooling, certificates and public-key infrastructure", "security-and-pki"),
    ResourceCategory("Authentication & user management", "Authentication, authorization and identity services", "authentication-authorization-and-user-management"),
    ResourceCategory("Source code repositories", "Git hosting and source-code collaboration", "source-code-repos"),
    ResourceCategory("Storage & media processing", "Object storage, file delivery and media processing", "storage-and-media-processing"),
    ResourceCategory("Tunneling, WebRTC & routers", "Tunnels, WebRTC, socket servers and routing services", "tunneling-webrtc-web-socket-servers-and-other-routers"),
    ResourceCategory("Testing", "Automated testing, browser testing and QA services", "testing"),
    ResourceCategory("Team collaboration tools", "Collaboration, communication and shared workspaces", "tools-for-teams-and-collaboration"),
    ResourceCategory("Translation management", "Localization and translation workflow tools", "translation-management"),
    ResourceCategory("Visitor session recording", "Session replay and visitor behavior recording", "visitor-session-recording"),
    ResourceCategory("Web hosting", "Static-site, frontend and application hosting", "web-hosting"),
    ResourceCategory("Commenting platforms", "Hosted commenting systems and discussion widgets", "commenting-platforms"),
    ResourceCategory("Remote desktop tools", "Remote access and desktop-control services", "remote-desktop-tools"),
    ResourceCategory("Other free resources", "Additional free services and developer resources", "other-free-resources")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperResourcesScreen(
    onBack: () -> Unit,
    onOpenCategory: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        developerResourceCategories.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
        }
    }
    val ink = Color(0xFF182235)
    val muted = Color(0xFF758295)
    val blue = Color(0xFF3268D9)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Free Developer Resources", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
            Text(
                "Explore free developer services by category. Search across ${developerResourceCategories.size} categories.",
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
            if (filtered.isEmpty()) {
                Text(
                    "No categories found. Try another search.",
                    color = muted,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.anchor }) { category ->
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
                            "Source: ripienaar/free-for-dev on GitHub. This directory links to the original category entries; it does not guarantee that every listed service is still free. Free-tier limits, eligibility and availability can change, so verify details with each provider before signing up.",
                            color = muted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 10.dp, bottom = 12.dp)
                        )
                    }
                }
            }
        }
    }
}
