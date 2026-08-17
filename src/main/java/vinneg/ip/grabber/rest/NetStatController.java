package vinneg.ip.grabber.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vinneg.ip.grabber.service.NetStatService;

import java.util.Set;

@RestController
@RequestMapping("/api/netstat")
@RequiredArgsConstructor
public class NetStatController {

    private final NetStatService netstatService;

    @GetMapping("/ips")
    public Set<String> getIps() {
        return netstatService.getIps();
    }

}
