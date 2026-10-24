package com.helpdesk.security;

import com.helpdesk.entity.User;
import com.helpdesk.enums.Role;
import com.helpdesk.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@AllArgsConstructor
public class CustomUserDetails implements UserDetails {

    private Long id;
    private String email;
    private String password;
    private String fullName;
    private Role role;
    private UserStatus status;
    private Long departmentId;
    private String departmentName;
    private Collection<? extends GrantedAuthority> authorities;

    public static CustomUserDetails build(User user) {
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );

        String deptName = (user.getDepartment() != null) ? user.getDepartment().getName() : null;
        Long deptId = (user.getDepartment() != null) ? user.getDepartment().getId() : null;

        return new CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getFullName(),
                user.getRole(),
                user.getStatus(),
                deptId,
                deptName,
                authorities
        );
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
