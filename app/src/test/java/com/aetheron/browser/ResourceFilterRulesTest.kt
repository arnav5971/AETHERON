package com.aetheron.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceFilterRulesTest {
    @Test
    fun noCreditCardRequiresExplicitEvidence() {
        assertTrue(ResourceFilterRules.matchesQuickFilter("no_card", "Hosting", "Example", "", "No credit card required"))
        assertTrue(ResourceFilterRules.matchesQuickFilter("no_card", "Hosting", "Example", "", "Credit card not required"))
        assertFalse(ResourceFilterRules.matchesQuickFilter("no_card", "Hosting", "Example", "Free plan available", "100 requests per month"))
    }

    @Test
    fun noSignupRequiresExplicitEvidence() {
        assertTrue(ResourceFilterRules.matchesQuickFilter("no_signup", "Tools", "Example", "", "No signup required"))
        assertTrue(ResourceFilterRules.matchesQuickFilter("no_signup", "Tools", "Example", "", "No registration"))
        assertFalse(ResourceFilterRules.matchesQuickFilter("no_signup", "Tools", "Example", "Free tier available", "1 GB storage"))
    }

    @Test
    fun alwaysFreeRequiresForeverLanguage() {
        assertTrue(ResourceFilterRules.matchesQuickFilter("always_free", "Cloud", "Example", "", "Always free"))
        assertTrue(ResourceFilterRules.matchesQuickFilter("always_free", "Cloud", "Example", "", "Free forever"))
        assertFalse(ResourceFilterRules.matchesQuickFilter("always_free", "Cloud", "Example", "", "Free tier, 5 GB per month"))
    }

    @Test
    fun limitFilterRequiresRecordedAllowance() {
        assertTrue(ResourceFilterRules.matchesQuickFilter("limits", "Cloud", "Example", "", "5 GB storage per month"))
        assertTrue(ResourceFilterRules.matchesQuickFilter("limits", "Cloud", "Example", "", "Usage limit applies"))
        assertFalse(ResourceFilterRules.matchesQuickFilter("limits", "Cloud", "Example", "", "Free access"))
    }

    @Test
    fun typeFiltersMatchExpectedCategories() {
        assertTrue(ResourceFilterRules.matchesTypeFilter("cloud", "Major cloud providers", "Compute", "", "https://example.com"))
        assertTrue(ResourceFilterRules.matchesTypeFilter("ai", "Generative AI", "Model", "", "https://example.com"))
        assertTrue(ResourceFilterRules.matchesTypeFilter("api", "APIs, data & ML", "Data", "", "https://example.com"))
        assertTrue(ResourceFilterRules.matchesTypeFilter("hosting", "Web hosting", "Static site", "", "https://example.com"))
        assertFalse(ResourceFilterRules.matchesTypeFilter("hosting", "Email", "Inbox", "", "https://example.com"))
    }
}
