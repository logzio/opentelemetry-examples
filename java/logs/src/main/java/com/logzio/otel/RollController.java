package com.logzio.otel;

import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RollController {

    private static final Logger logger = LoggerFactory.getLogger(RollController.class);

    private int min = 1;
    private int max = 6;

    // Endpoint for rolling the dice with an optional player name in the URL
    @GetMapping({"/rolldice", "/rolldice/{player}"})
    public String rollDice(@PathVariable(required = false) String player) {
        int result = this.getRandomNumber(min, max);

        if (player != null && !player.isEmpty()) {
            logger.info("{} is rolling the dice: {}", player, result);
        } else {
            logger.info("Anonymous player is rolling the dice: {}", result);
        }

        return Integer.toString(result);
    }

    private int getRandomNumber(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}