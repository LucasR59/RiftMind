package com.riftmind.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.riftmind.backend.riot.RiotClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RiftMindApplicationTests {

    @Autowired
    private RiotClient riotClient;

    @Test
    void contextLoads() {
        // Le client Riot est bien créé avec le RestClient.Builder fourni par Spring Boot
        assertThat(riotClient).isNotNull();
    }
}
