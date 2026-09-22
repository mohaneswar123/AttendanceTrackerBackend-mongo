package com.AttendanceRegister.sdc.security;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.AttendanceRegister.sdc.model.Admin;
import com.AttendanceRegister.sdc.model.User;

// Issues the bearer tokens returned by the login endpoints. The subject is the
// account id and the "role" claim is USER or ADMIN.
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final Duration userTokenLifetime;
    private final Duration adminTokenLifetime;

    public TokenService(JwtEncoder encoder,
                        @Value("${app.jwt.user-token-hours:720}") long userTokenHours,
                        @Value("${app.jwt.admin-token-hours:12}") long adminTokenHours) {
        this.encoder = encoder;
        this.userTokenLifetime = Duration.ofHours(userTokenHours);
        this.adminTokenLifetime = Duration.ofHours(adminTokenHours);
    }

    public String issueUserToken(User user) {
        return issue(user.getId(), AccessGuard.ROLE_USER, userTokenLifetime);
    }

    public String issueAdminToken(Admin admin) {
        return issue(admin.getId(), AccessGuard.ROLE_ADMIN, adminTokenLifetime);
    }

    private String issue(String subject, String role, Duration lifetime) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(lifetime))
                .claim(AccessGuard.ROLE_CLAIM, role)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
