/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.pgstac;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * An ECQL-based visibility rule: {@link #getRoles()} lists the roles allowed to see the features matched by
 * {@link #getCqlFilter()}; for item rules, {@link #getCollection()} scopes the rule to a single collection.
 */
public class AccessRule implements Serializable {

    private String collection;
    private String cqlFilter;
    private Set<String> roles = new LinkedHashSet<>();

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String getCqlFilter() {
        return cqlFilter;
    }

    public void setCqlFilter(String cqlFilter) {
        this.cqlFilter = cqlFilter;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccessRule)) return false;
        AccessRule other = (AccessRule) o;
        return Objects.equals(collection, other.collection)
                && Objects.equals(cqlFilter, other.cqlFilter)
                && Objects.equals(roles, other.roles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(collection, cqlFilter, roles);
    }

    @Override
    public String toString() {
        return "AccessRule[collection=" + collection + ", cqlFilter=" + cqlFilter + ", roles=" + roles + "]";
    }
}
