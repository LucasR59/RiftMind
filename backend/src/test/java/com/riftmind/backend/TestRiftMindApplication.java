package com.riftmind.backend;

import org.springframework.boot.SpringApplication;

/** Lance l'application en local avec une base Testcontainers, sans avoir besoin de docker compose. */
public class TestRiftMindApplication {

    public static void main(String[] args) {
        SpringApplication.from(RiftMindApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
