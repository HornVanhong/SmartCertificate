package com.smartcertificate.common.security;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AuthenticatedPrincipal implements UserDetails {

    private final UUID userId;
    private final UUID organizationId;
    private final String email;
    private final String fullName;
    private final String roleName;
    private final Set<String> permissions;
    private final Collection<? extends GrantedAuthority> authorities;

    public AuthenticatedPrincipal(
            UUID userId, UUID organizationId, String email, String fullName, String roleName, Set<String> permissions) {
        this.userId = userId;
        this.organizationId = organizationId;
        this.email = email;
        this.fullName = fullName;
        this.roleName = roleName;
        this.permissions = Set.copyOf(permissions);
        this.authorities = permissions.stream().map(SimpleGrantedAuthority::new).toList();
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public String getRoleName() {
        return roleName;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return email;
    }
}
