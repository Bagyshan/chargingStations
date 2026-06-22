package charg.ing.stations.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private String scope;
    private String idToken; // Для OpenID Connect

    public static TokenResponse fromAuthResponse(AuthResponse authResponse) {
        return TokenResponse.builder()
                .accessToken(authResponse.getAccessToken())
                .refreshToken(authResponse.getRefreshToken())
                .tokenType(authResponse.getTokenType())
                .expiresIn(authResponse.getExpiresIn())
                .scope(authResponse.getScope())
                .build();
    }
}