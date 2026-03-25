package org.geoserver.rest.catalog;

import org.geoserver.rest.RequestInfo;
import org.geoserver.rest.ResourceNotFoundException;
import org.geoserver.rest.RestBaseController;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.crs.ProjectedCRS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.proj.PROJFormatter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@ControllerAdvice
@RequestMapping(path = RestBaseController.ROOT_PATH + "/crs")
public class CRSController extends RestBaseController{

    private static class CRSLink {
        private final String id;
        private final String href;

        public CRSLink(String id, String href) {
            this.id = id;
            this.href = href;
        }

        public String getId() { return id; }

        public String getHref() { return href; }
    }

    private static class PageInfo{
        private final int offset;
        private final int limit;
        private final int returned;

        private final int total;

        private PageInfo(int offset, int limit, int returned, int total) {
            this.offset = offset;
            this.limit = limit;
            this.returned = returned;
            this.total = total;
        }

        public int getTotal() { return total; }

        public int getReturned() { return returned; }

        public int getLimit() { return limit; }

        public int getOffset() { return offset; }

    }

    private static class ListResponse {

        private final List<CRSLink> crs;
        private final PageInfo page;

        public ListResponse(List<CRSLink> crs, PageInfo page) {
            this.crs = crs;
            this.page = page;
        }

        public List<CRSLink> getCrs() {
            return crs;
        }

        public PageInfo getPage() {
            return page;
        }
    }

    private static class DefinitionResponse {

        private final String id;
        private final String format;
        private final String definition;

        public DefinitionResponse(String id, String format, String definition) {
            this.id = id;
            this.format = format;
            this.definition = definition;
        }

        public String getId() {
            return id;
        }

        public String getFormat() {
            return format;
        }

        public String getDefinition() {
            return definition;
        }
    }


    private final Set<String> authorityExclusions =
            Set.of(
                    "http://www.opengis.net/gml",
                    "http://www.opengis.net/def",
                    "AUTO", // only suitable in WMS service
                    "AUTO2", // only suitable in WMS service
                    "urn:ogc:def",
                    "urn:x-ogc:def");


    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ListResponse list(
            @RequestParam(name = "authority", required = false) String authority,
            @RequestParam(name = "offset", defaultValue = "0") int offset,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {

        validatePaging(offset, limit);
        List<String> codes = new ArrayList<>(getCodes(authority));
        int total = codes.size();
        int from = Math.min(offset, total);
        int to = Math.min(offset + limit, total);

        String crslink = RequestInfo.get().servletURI("crs/");
        List<CRSLink> items = codes.subList(from, to).stream()
                .map(id -> new CRSLink(id, crslink + id + ".wkt"))
                .toList();

        return new ListResponse(items, new PageInfo(offset, limit, items.size(), total));
    }

    @GetMapping(path = "/{identifier:.+}.wkt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getWkt(@PathVariable String identifier) {
        return ResponseEntity.ok(decodeWkt(identifier));
    }

    @GetMapping(path = "/{identifier:.+}.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public DefinitionResponse getJson(@PathVariable String identifier) {
        return new DefinitionResponse(identifier, "wkt", decodeWkt(identifier));
    }

    private String decodeWkt(String identifier) {
        validateIdentifier(identifier);

        try {
            CoordinateReferenceSystem crs = CRS.decode(identifier, true);
            return crs.toWKT();
        } catch (FactoryException e) {
             throw new ResourceNotFoundException("CRS not found: " + identifier);
        }
    }

    private void validatePaging(int offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        if (limit > 1000) {
            throw new IllegalArgumentException("limit must be <= 200");
        }
    }

    private void validateIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("CRS identifier is required");
        }
        if (!identifier.contains(":")) {
            throw new IllegalArgumentException("Invalid CRS identifier, expected AUTHORITY:CODE");
        }
    }

    private Set<String> getCodes(String requestedAuthority) {
        List<String> authorities = CRS.getSupportedAuthorities(true).stream()
                .filter(a -> !authorityExclusions.contains(a))
                .filter(a -> requestedAuthority == null
                        || requestedAuthority.isBlank()
                        || a.equalsIgnoreCase(requestedAuthority))
                .sorted()
                .toList();

        return authorities.stream()
                .flatMap(authority -> CRS.getSupportedCodes(authority).stream()
                        .sorted(this::compareCodes)
                        .map(code -> mapCode(authority, code)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String mapCode(String authority, String code) {
        return authority + ":" + code;
    }

    private int compareCodes(String a, String b) {
        try {
            return Integer.valueOf(a).compareTo(Integer.valueOf(b));
        } catch (NumberFormatException e) {
            return a.compareTo(b);
        }
    }
}
