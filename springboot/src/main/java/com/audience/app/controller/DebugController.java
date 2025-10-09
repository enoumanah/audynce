package com.audience.app.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class DebugController {
    private final ClientRegistrationRepository clients;

    @GetMapping("/debug/clients")
    public List<String> listClients() {
        List<String> names = new ArrayList<>();
        if (clients instanceof Iterable) {
            ((Iterable<?>) clients).forEach(c -> names.add(((ClientRegistration)c).getRegistrationId()));
        }
        return names;
    }
}
