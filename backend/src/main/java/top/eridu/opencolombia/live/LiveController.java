package top.eridu.opencolombia.live;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LiveController {
    private final LiveService service;

    public LiveController(LiveService service) {
        this.service = service;
    }

    /** Dólar intradía y sismos recientes, desde caché (el refresco corre en segundo plano cada 15 minutos). */
    @GetMapping("/api/live")
    public LiveSnapshot live() {
        return service.get();
    }
}
