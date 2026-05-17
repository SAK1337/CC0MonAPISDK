package com.cc0mon.sdk;

import com.cc0mon.sdk.Models.Collector;
import com.cc0mon.sdk.Models.CollectorItem;
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
    // Known holder of token #1; used for non-trivial collector assertions.
    private static final String KNOWN_HOLDER = "0xB07952A55bF9c45C268F37C3631823Df50ac721a";
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
        // The 260/259 counts track the live API; bump these if cc0mon adds/maps species.
        List<SpeciesImage> images = client.getRegistryImages();
        assertEquals(260, images.size());
        long mapped = images.stream().filter(i -> i.tokenId() != null).count();
        assertEquals(259, mapped);
    }

    @Test
    void getCollector() {
        // The 260 totals track the live API; bump if cc0mon adds species.
        Collector c = client.getCollector(KNOWN_HOLDER);
        assertNotNull(c.progress());
        assertTrue(c.progress().endsWith("%"));
        assertEquals(260, c.totalCC0mon());
        assertEquals(260, c.checklist().size());
        assertNotNull(c.byEnergy());
        assertTrue(c.collected() >= 1);
    }

    @Test
    void findSpeciesEmptyResultIsNotError() {
        // Combination unlikely to match; the call should succeed and return [].
        List<Species> none = client.findSpecies("Mythic", "Common", "zzz-not-a-real-name");
        assertNotNull(none);
        assertTrue(none.isEmpty());
    }

    @Test
    void findSpeciesFilterSubset() {
        List<Species> fireAll = client.findSpecies("Fire", null, null);
        List<Species> fireCommon = client.findSpecies("Fire", "Common", null);
        assertTrue(fireCommon.size() <= fireAll.size());
        assertTrue(fireCommon.stream().allMatch(s -> "Fire".equals(s.energy()) && "Common".equals(s.rarity())));
    }

    @Test
    void findCollectorItemsOwnedOnly() {
        Collector c = client.getCollector(KNOWN_HOLDER);
        List<CollectorItem> owned = client.findCollectorItems(KNOWN_HOLDER, true, null, null);
        assertEquals(c.collected(), owned.size());
        assertTrue(owned.stream().allMatch(CollectorItem::collected));
    }
}
