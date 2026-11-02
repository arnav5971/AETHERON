package com.aetheron.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONObject

private data class ResourceService(
    val id: String,
    val name: String,
    val url: String,
    val urlType: String,
    val description: String,
    val freeTierNotes: String,
    val parentName: String?
)

private data class ResourceCategory(
    val id: String,
    val name: String,
    val description: String,
    val sourceUrl: String,
    val services: List<ResourceService>
)

private fun loadDeveloperResourceCatalogue(context: android.content.Context): List<ResourceCategory> {
    val json = context.assets.open("free_for_dev_catalog.json").bufferedReader().use { it.readText() }
    val root = JSONObject(json)
    val categories = root.getJSONArray("categories")
    return buildList {
        for (i in 0 until categories.length()) {
            val category = categories.getJSONObject(i)
            val servicesJson = category.getJSONArray("services")
            val services = buildList {
                for (j in 0 until servicesJson.length()) {
                    val service = servicesJson.getJSONObject(j)
                    add(
                        ResourceService(
                            id = service.optString("id"),
                            name = service.optString("name"),
                            url = service.optString("url"),
                            urlType = service.optString("urlType", "website"),
                            description = service.optString("description"),
                            freeTierNotes = service.optString("freeTierNotes"),
                            parentName = service.optString("parentName").takeIf { it.isNotBlank() && it != "null" }
                        )
                    )
                }
            }
            add(
                ResourceCategory(
                    id = category.optString("id"),
                    name = category.optString("name"),
                    description = category.optString("description"),
                    sourceUrl = category.optString("sourceUrl"),
                    services = services
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperResourcesScreen(
    onBack: () -> Unit,
    onOpenService: (String) -> Unit
) {
    val context = LocalContext.current
    val catalogue = remember {
        runCatching { loadDeveloperResourceCatalogue(context) }.getOrElse { emptyList() }
    }
    var query by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var selectedService by remember { mutableStateOf<Pair<ResourceCategory, ResourceService>?>(null) }
    var selectedQuickFilter by remember { mutableStateOf("all") }
    var selectedTypeFilter by remember { mutableStateOf("all") }
    val selectedCategory = catalogue.firstOrNull { it.id == selectedCategoryId }
    val hasActiveFilters = selectedQuickFilter != "all" || selectedTypeFilter != "all"
    val allServices = remember(catalogue) {
        catalogue.flatMap { category -> category.services.map { category to it } }
    }
    val matchingServices = remember(query, allServices, selectedQuickFilter, selectedTypeFilter) {
        allServices.filter { (category, service) ->
            val textMatches = query.isBlank() ||
                service.name.contains(query, ignoreCase = true) ||
                service.description.contains(query, ignoreCase = true) ||
                service.freeTierNotes.contains(query, ignoreCase = true) ||
                category.name.contains(query, ignoreCase = true) ||
                (service.parentName?.contains(query, ignoreCase = true) == true)
            textMatches && ResourceFilterRules.matchesQuickFilter(
                selectedQuickFilter, category.name, service.name, service.description, service.freeTierNotes
            ) && ResourceFilterRules.matchesTypeFilter(
                selectedTypeFilter, category.name, service.name, service.description, service.url
            )
        }.take(250)
    }
    val filteredCategories = remember(query, catalogue, hasActiveFilters) {
        if (query.isBlank()) {
            if (hasActiveFilters) emptyList() else catalogue
        } else catalogue.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
        }
    }
    val ink = Color(0xFF182235)
    val muted = Color(0xFF758295)
    val blue = Color(0xFF3268D9)
    val card = Color(0xFFF5F8FD)

    BackHandler(enabled = selectedService != null || selectedCategory != null) {
        if (selectedService != null) selectedService = null else selectedCategoryId = null
        query = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            selectedService?.second?.name ?: selectedCategory?.name ?: "Free Developer Resources",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (selectedService != null) {
                            Text("Service details", style = MaterialTheme.typography.labelSmall, color = muted)
                        } else if (selectedCategory != null) {
                            Text(
                                "${selectedCategory.services.size} listed resources",
                                style = MaterialTheme.typography.labelSmall,
                                color = muted
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            selectedService != null -> selectedService = null
                            selectedCategory != null -> { selectedCategoryId = null; query = "" }
                            else -> onBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)
        ) {
            if (selectedService != null) {
                ResourceServiceDetail(selectedService!!.first, selectedService!!.second, ink, muted, blue, onOpenService)
            } else if (catalogue.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Code, null, tint = blue, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Resource catalogue couldn't be loaded", color = ink, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Please update AETHERON or check the bundled catalogue asset.",
                        color = muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                if (selectedCategory == null) {
                    Text(
                        "Explore ${catalogue.size} categories and ${allServices.size} developer services — all inside AETHERON.",
                        color = muted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 14.dp)
                    )
                } else {
                    Text(
                        selectedCategory.description,
                        color = muted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                    )
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            TextButton(onClick = { query = "" }) { Text("Clear") }
                        }
                    },
                    placeholder = {
                        Text(if (selectedCategory == null) "Search services or categories" else "Search in this category")
                    },
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "All terms", "no_card" to "No credit card", "no_signup" to "No signup", "always_free" to "Always free", "limits" to "Limit listed").forEach { (id, label) ->
                        FilterChip(selected = selectedQuickFilter == id, onClick = { selectedQuickFilter = id }, label = { Text(label, maxLines = 1) })
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "All types", "cloud" to "Cloud", "ai" to "AI / ML", "api" to "API", "hosting" to "Hosting").forEach { (id, label) ->
                        FilterChip(selected = selectedTypeFilter == id, onClick = { selectedTypeFilter = id }, label = { Text(label, maxLines = 1) })
                    }
                }
                Text("Filters match only details explicitly recorded in this catalogue. Always verify provider terms.", color = muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))

                when {
                    selectedCategory != null -> {
                        val services = remember(selectedCategory, query) {
                            selectedCategory.services.filter { service ->
                                val textMatches = query.isBlank() || service.name.contains(query, true) || service.description.contains(query, true) || service.freeTierNotes.contains(query, true) || (service.parentName?.contains(query, true) == true)
                                textMatches && ResourceFilterRules.matchesQuickFilter(selectedQuickFilter, selectedCategory.name, service.name, service.description, service.freeTierNotes) &&
                                    ResourceFilterRules.matchesTypeFilter(selectedTypeFilter, selectedCategory.name, service.name, service.description, service.url)
                            }
                        }
                        if (services.isEmpty()) {
                            EmptyResourcesMessage("No services match this search.", muted)
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(services, key = { it.id }) { service ->
                                    ResourceServiceCard(service, ink, muted, blue, card, onOpenService) { selectedService = selectedCategory to service }
                                }
                                item { CatalogueAttribution(muted) }
                            }
                        }
                    }
                    query.isNotBlank() || hasActiveFilters -> {
                        if (matchingServices.isEmpty() && filteredCategories.isEmpty()) {
                            EmptyResourcesMessage("No matching services or categories. Try another search.", muted)
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (matchingServices.isNotEmpty()) {
                                    item {
                                        Text(
                                            "Matching services (${matchingServices.size})",
                                            color = ink,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                    items(matchingServices, key = { "${it.first.id}-${it.second.id}" }) { (category, service) ->
                                        Column {
                                            Text(
                                                category.name,
                                                color = muted,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                            )
                                            ResourceServiceCard(service, ink, muted, blue, card, onOpenService) { selectedService = category to service }
                                        }
                                    }
                                }
                                if (filteredCategories.isNotEmpty()) {
                                    item {
                                        Text(
                                            "Categories (${filteredCategories.size})",
                                            color = ink,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                        )
                                    }
                                    items(filteredCategories, key = { it.id }) { category ->
                                        ResourceCategoryCard(category, ink, muted, blue, card) {
                                            selectedCategoryId = category.id
                                            query = ""
                                        }
                                    }
                                }
                                item { CatalogueAttribution(muted) }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(catalogue, key = { it.id }) { category ->
                                ResourceCategoryCard(category, ink, muted, blue, card) {
                                    selectedCategoryId = category.id
                                }
                            }
                            item { CatalogueAttribution(muted) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResourceCategoryCard(
    category: ResourceCategory,
    ink: Color,
    muted: Color,
    blue: Color,
    card: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = card),
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
                Spacer(Modifier.height(6.dp))
                Text(
                    "${category.services.size} services",
                    color = blue,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(Icons.Default.ArrowForward, contentDescription = "Open category", tint = blue)
        }
    }
}

@Composable
private fun ResourceServiceCard(
    service: ResourceService,
    ink: Color,
    muted: Color,
    blue: Color,
    card: Color,
    onOpenService: (String) -> Unit,
    onSelectService: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(17.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Text(service.name, color = ink, fontWeight = FontWeight.SemiBold)
            service.parentName?.let {
                Text("Listed under $it", color = muted, style = MaterialTheme.typography.labelSmall)
            }
            if (service.description.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(service.description, color = muted, style = MaterialTheme.typography.bodySmall)
            }
            if (service.freeTierNotes.isNotBlank()) {
                Spacer(Modifier.height(9.dp))
                Text("FREE-TIER NOTES", color = blue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(service.freeTierNotes, color = ink, style = MaterialTheme.typography.bodySmall)
            } else {
                Spacer(Modifier.height(7.dp))
                Text(
                    "No specific limit is recorded here. Check the provider's current free-tier terms.",
                    color = muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Language, null, tint = muted, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    service.url.removePrefix("https://").removePrefix("http://"),
                    color = muted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = onSelectService) { Text("Details", color = blue) }
                if (service.url.startsWith("https://") || service.url.startsWith("http://")) {
                    TextButton(onClick = { onOpenService(service.url) }) {
                        Text("Open site", color = blue)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.OpenInNew, null, tint = blue, modifier = Modifier.size(15.dp))
                    }
                } else {
                    Text("Endpoint", color = muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ResourceServiceDetail(
    category: ResourceCategory,
    service: ResourceService,
    ink: Color,
    muted: Color,
    blue: Color,
    onOpenService: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text(category.name, color = blue, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(service.name, color = ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        service.parentName?.let { Text("Listed under $it", color = muted, style = MaterialTheme.typography.labelMedium) }
        Spacer(Modifier.height(18.dp))
        Text("ABOUT", color = blue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(service.description, color = ink, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Text("FREE-TIER DETAILS", color = blue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(service.freeTierNotes.ifBlank { "No specific allowance is recorded in this catalogue entry. Check the provider's current pricing and free-tier terms." }, color = ink, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Text("CATEGORY", color = blue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(category.description, color = muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(20.dp))
        Text("OFFICIAL ADDRESS", color = blue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(service.url, color = muted, style = MaterialTheme.typography.bodySmall)
        if (service.url.startsWith("https://") || service.url.startsWith("http://")) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onOpenService(service.url) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open official website")
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text("This entry is a technical endpoint, not a website link.", color = muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(18.dp))
        CatalogueAttribution(muted)
    }
}

@Composable
private fun EmptyResourcesMessage(message: String, muted: Color) {
    Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
        Text(message, color = muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CatalogueAttribution(muted: Color) {
    Text(
        "Catalogue source: ripienaar/free-for-dev on GitHub. Free-tier terms, limits, eligibility and availability may change. Verify current details with each provider before signing up. Service descriptions in this directory are catalogue summaries.",
        color = muted,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 10.dp, bottom = 12.dp)
    )
}
