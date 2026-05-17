///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//DEPS com.cc0mon:sdk:0.1.0
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.0

import com.cc0mon.sdk.Client;
import com.cc0mon.sdk.Errors;
import com.cc0mon.sdk.LoggingSetup;
import com.cc0mon.sdk.Models.SpeciesImage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

class GetRegistryImages {
    public static void main(String[] args) {
        Logger log = LoggingSetup.configure("get-registry-images");
        Integer limit = null;
        String nameContains = null;
        boolean hasImage = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--limit": if (i + 1 < args.length) try { limit = Integer.parseInt(args[++i]); } catch (NumberFormatException e) {} break;
                case "--name-contains": if (i + 1 < args.length) nameContains = args[++i]; break;
                case "--has-image": hasImage = true; break;
            }
        }
        if (limit != null && limit < 0) {
            System.err.println("validation error: --limit must be >= 0, got " + limit);
            System.exit(5);
        }
        log.info("script start limit=" + limit + " name_contains=" + nameContains + " has_image=" + hasImage);

        try (Client client = new Client()) {
            List<SpeciesImage> images = client.getRegistryImages();
            List<SpeciesImage> filtered = new ArrayList<>(images);
            if (hasImage) filtered.removeIf(i -> i.tokenId() == null);
            if (nameContains != null) {
                String needle = nameContains.toLowerCase(Locale.ROOT);
                filtered.removeIf(i -> {
                    String name = i.name() == null ? "" : i.name().toLowerCase(Locale.ROOT);
                    return !name.contains(needle);
                });
            }
            List<SpeciesImage> shown = limit == null ? filtered : filtered.subList(0, Math.min(limit, filtered.size()));
            System.out.println(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(shown));
            log.info("script exit code=0 total=" + images.size() + " matched=" + filtered.size() + " shown=" + shown.size());
        } catch (Errors.ValidationException e) { log.severe("validation: " + e.getMessage()); System.err.println("validation error: " + e.getMessage()); System.exit(5);
        } catch (Errors.NetworkException e)    { log.severe("network: " + e.getMessage());    System.err.println("network error: " + e.getMessage()); System.exit(2);
        } catch (Errors.RateLimitException e)  { log.severe("rate limited http " + e.getStatusCode()); System.err.println("rate limited HTTP " + e.getStatusCode() + ": " + e.getBody()); System.exit(3);
        } catch (Errors.ClientApiException e)  { log.severe("http " + e.getStatusCode());     System.err.println("client error HTTP " + e.getStatusCode() + ": " + e.getBody()); System.exit(3);
        } catch (Errors.ServerApiException e)  { log.severe("http " + e.getStatusCode());     System.err.println("server error HTTP " + e.getStatusCode() + ": " + e.getBody()); System.exit(4);
        } catch (Exception e) { log.severe("unexpected: " + e); System.err.println("unexpected: " + e); System.exit(1); }
    }
}
