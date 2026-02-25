package com.examplatform.auth.security;

import com.examplatform.auth.client.UserServiceClient;
import com.examplatform.auth.dto.AuthDtos.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring Security's UserDetailsService — bridges to User Service.
 *
 * Wired into DaoAuthenticationProvider in SecurityConfig.
 * Note: In our main login flow, AuthService performs authentication manually
 * (more control over error messages + audit logging). This class is wired
 * for Spring Security's standard auth manager and future extensibility.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserServiceClient userServiceClient;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserDto user = userServiceClient.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        return User.builder()
                .username(user.email())
                .password(user.passwordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())))
                .accountLocked(user.locked())
                .disabled(!user.active())
                .build();
    }
}
