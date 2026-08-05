package com.automatedinterview.ai;

import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VertexAccessTokenProvider {
    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    private final GoogleCredentials applicationCredentials;
    private final String fallbackToken;

    public VertexAccessTokenProvider(@Value("${VERTEX_ACCESS_TOKEN:}") String fallbackToken) {
        this.fallbackToken = VertexCredentials.token(fallbackToken);
        GoogleCredentials detected = null;
        try {
            detected = GoogleCredentials.getApplicationDefault().createScoped(CLOUD_PLATFORM_SCOPE);
        } catch (IOException ignored) {
            // Local development can still use the explicit token fallback.
        }
        this.applicationCredentials = detected;
    }

    public boolean isAvailable() {
        return applicationCredentials != null || !fallbackToken.isBlank();
    }

    public String token() throws IOException {
        if (applicationCredentials != null) {
            applicationCredentials.refreshIfExpired();
            if (applicationCredentials.getAccessToken() != null) {
                return applicationCredentials.getAccessToken().getTokenValue();
            }
        }
        if (!fallbackToken.isBlank()) return fallbackToken;
        throw new IOException("No Google Application Default Credential or Vertex access token configured");
    }
}
