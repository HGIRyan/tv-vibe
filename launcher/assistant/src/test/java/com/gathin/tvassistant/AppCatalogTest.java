package com.gathin.tvassistant;

import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class AppCatalogTest {
    @Test public void labelsAliasesAndPackageIdsRoundTrip() throws Exception {
        AppCatalog original = new AppCatalog(List.of(new AppCatalog.App("OPEN_MAX", "Max", "com.wbd.stream", List.of("HBO Max"))));
        AppCatalog copy = AppCatalog.fromJson(new JSONObject().put("apps", original.toJson()));
        assertEquals("com.wbd.stream", copy.byId("OPEN_MAX").packageName);
        assertTrue(copy.names().contains("HBO Max"));
        assertTrue(copy.byPackage("com.wbd.stream").description().contains("HBO Max"));
        assertNull(copy.byId("OPEN_NOT_INSTALLED"));
    }
    @Test(expected = IllegalArgumentException.class) public void duplicateIdsAreRejected() {
        new AppCatalog(List.of(new AppCatalog.App("OPEN_X", "One", "com.example.one", List.of()),
            new AppCatalog.App("OPEN_X", "Two", "com.example.two", List.of())));
    }
    @Test(expected = IllegalArgumentException.class) public void packageCannotContainShellCommands() {
        new AppCatalog.App("OPEN_X", "Bad", "com.example.app; reboot", List.of());
    }
}
