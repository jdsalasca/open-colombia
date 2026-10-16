package top.eridu.opencolombia.source;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class HttpConfig {
    @Bean
    RestClient sourcesRestClient() {
        var jdk = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        var factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(Duration.ofSeconds(60)); // IDEAM: un bloque de 30 días tarda ~13 s solo y más con carga
        return RestClient.builder().requestFactory(factory)
                .defaultHeader("User-Agent", "open-colombia/1.0 (+https://open-colombia.eridu.top)")
                .build();
    }

    /** «Hoy» es el día calendario de Colombia (UTC-5): los datos diarios se consideran completos al terminar ese día. */
    @Bean
    Clock clock() {
        return Clock.system(java.time.ZoneId.of("America/Bogota"));
    }
}
