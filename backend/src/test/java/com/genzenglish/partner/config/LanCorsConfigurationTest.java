package com.genzenglish.partner.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

class LanCorsConfigurationTest {

    private static CorsConfiguration corsFor(boolean allowLan) {
        AppProperties.Ai.Provider mock = new AppProperties.Ai.Provider("mock");
        AppProperties props = new AppProperties(
                new AppProperties.Jwt("test-only-secret-test-only-secret-0123456789", "test", Duration.ofMinutes(15),
                        Duration.ofDays(30)),
                new AppProperties.Auth(new AppProperties.Auth.Google("mock", List.of())),
                new AppProperties.Cors(List.of("http://localhost:8081"), allowLan),
                new AppProperties.Ai(mock, mock, mock, mock),
                new AppProperties.Privacy(false));
        return new SecurityConfig().corsConfigurationSource(props)
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/speech/languages"));
    }

    @Test
    void aPhoneOnTheSameWifiOrHotspotGetsIn() {
        CorsConfiguration cors = corsFor(true);

        assertThat(cors.checkOrigin("http://172.27.32.1:8081")).isEqualTo("http://172.27.32.1:8081");
        assertThat(cors.checkOrigin("https://192.168.1.20:8443")).isEqualTo("https://192.168.1.20:8443");
        assertThat(cors.checkOrigin("http://10.0.0.5:8081")).isEqualTo("http://10.0.0.5:8081");
        assertThat(cors.checkOrigin("http://localhost:8081")).isEqualTo("http://localhost:8081");
    }

    @Test
    void publicAddressesAndLookalikeNamesStayOut() {
        CorsConfiguration cors = corsFor(true);

        assertThat(cors.checkOrigin("http://172.32.0.1:8081")).isNull();
        assertThat(cors.checkOrigin("http://8.8.8.8:8081")).isNull();
        assertThat(cors.checkOrigin("http://192.168.1.20.example.com:8081")).isNull();
        assertThat(cors.checkOrigin("http://10.example.com")).isNull();
        assertThat(cors.checkOrigin("http://192.168.1.300:8081")).isNull();
        assertThat(cors.checkOrigin("http://localhost:3000")).isNull();
        assertThat(cors.checkOrigin(null)).isNull();
    }

    @Test
    void withoutTheSwitchOnlyTheListedOriginsGetIn() {
        CorsConfiguration cors = corsFor(false);

        assertThat(cors.checkOrigin("http://172.27.32.1:8081")).isNull();
        assertThat(cors.checkOrigin("http://localhost:8081")).isEqualTo("http://localhost:8081");
    }
}
