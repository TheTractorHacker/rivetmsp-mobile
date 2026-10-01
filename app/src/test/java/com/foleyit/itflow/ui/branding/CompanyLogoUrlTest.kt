package com.foleyit.itflow.ui.branding

import org.junit.Assert.*
import org.junit.Test

class CompanyLogoUrlTest {
    @Test fun acceptsOnlyTheSelectedServerUpload() {
        assertEquals("https://msp.example/uploads/settings/logo.png",
            companyLogoUrl("https://msp.example", "/uploads/settings/logo.png"))
        assertEquals("https://msp.example:8443/uploads/settings/logo.png",
            companyLogoUrl("https://msp.example:8443", "/uploads/settings/logo.png"))
    }
    @Test fun rejectsOtherTenantsDowngradesAndCredentials() {
        listOf("https://other.example/uploads/settings/logo.png",
            "//other.example/uploads/settings/logo.png",
            "http://msp.example/uploads/settings/logo.png",
            "https://msp.example:8443/uploads/settings/logo.png",
            "https://user:password@msp.example/uploads/settings/logo.png")
            .forEach { assertNull(it, companyLogoUrl("https://msp.example", it)) }
    }
    @Test fun rejectsTraversalAndNonUploadPaths() {
        listOf("/uploads/settings/../../config.php", "/api/v1/tickets", "", "/uploads/settings/%2e%2e/%2e%2e/config.php")
            .forEach { assertNull(it, companyLogoUrl("https://msp.example", it)) }
        assertNull(companyLogoUrl("https://msp.example", null))
        assertNull(companyLogoUrl("not a server", "/uploads/settings/logo.png"))
    }
}
