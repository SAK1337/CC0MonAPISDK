package com.cc0mon.sdk;

import com.cc0mon.sdk.Models.Collector;
import com.cc0mon.sdk.Models.Contract;
import com.cc0mon.sdk.Models.Metadata;
import com.cc0mon.sdk.Models.OwnerInfo;
import com.cc0mon.sdk.Models.Species;
import com.cc0mon.sdk.Models.SpeciesImage;
import com.cc0mon.sdk.Models.Token;
import com.cc0mon.sdk.Models.Traits;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live API smoke tests. Gated by env var {@code CC0MON_RUN_INTEGRATION=1}.
 *
 * <p>Run with:
 * <pre>
 *   $env:CC0MON_RUN_INTEGRATION = "1"
 *   mvn verify
 * </pre>
 */
class ClientIT {

    private static final int SAMPLE_TOKEN_ID = 1;
    private static final String SAMPLE_ADDRESS = "0x0000000000000000000000000000000000000000";
    private static Client client;

    @BeforeAll
    static void setUp() {
        assumeTrue("1".equals(System.getenv("CC0MON_RUN_INTEGRATION")),
            "Set CC0MON_RUN_INTEGRATION=1 to run live API tests.");
        client = new Client();
    }

    @Test
    void getToken() {
        Token t = client.getToken(SAMPLE_TOKEN_ID);
        assertNotNull(t.raw());
    }

    @Test
    void getMetadata() {
        Metadata m = client.getMetadata(SAMPLE_TOKEN_ID);
        assertNotNull(m.raw());
        assertNotNull(m.attributes());
    }

    @Test
    void getTraits() {
        Traits t = client.getTraits(SAMPLE_TOKEN_ID);
        assertNotNull(t.attributes());
    }

    @Test
    void getImageSvg() {
        byte[] svg = client.getImageSvg(SAMPLE_TOKEN_ID);
        assertNotNull(svg);
        assertTrue(svg.length > 100);
        assertTrue(new String(svg, 0, Math.min(200, svg.length)).contains("<svg"));
    }

    @Test
    void getImagePng() {
        byte[] png = client.getImagePng(SAMPLE_TOKEN_ID);
        assertNotNull(png);
        assertTrue(png.length > 100);
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 'P', png[1]);
        assertEquals((byte) 'N', png[2]);
        assertEquals((byte) 'G', png[3]);
    }

    @Test
    void getOwner() {
        OwnerInfo owner = client.getOwner(SAMPLE_TOKEN_ID);
        assertNotNull(owner.owner());
        assertTrue(owner.owner().startsWith("0x"));
        assertEquals(42, owner.owner().length());
    }

    @Test
    void getContract() {
        Contract c = client.getContract();
        assertNotNull(c.address());
        assertTrue(c.address().toLowerCase().startsWith("0x"));
    }

    @Test
    void getRegistry() {
        List<Species> species = client.getRegistry();
        assertTrue(species.size() >= 1);
    }

    @Test
    void getRegistryImages() {
        List<SpeciesImage> images = client.getRegistryImages();
        assertTrue(images.size() >= 1);
    }

    @Test
    void getCollector() {
        Collector c = client.getCollector(SAMPLE_ADDRESS);
        assertNotNull(c.raw());
    }
}
