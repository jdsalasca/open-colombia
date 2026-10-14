package top.eridu.opencolombia.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Rutas del front (React Router) que deben servir index.html al recargar o abrir un enlace directo. */
@Controller
public class SpaController {
    @GetMapping({"/tema/{id}", "/propuestas", "/metodologia", "/metodologia/{id}"})
    public String spa() {
        return "forward:/index.html";
    }
}
