package com.fightthefascists.claims;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;

@Service
public class HandoffCodeService {
    private static final List<String> WORDS = List.of(
            "MANGO", "RIVER", "CLOUD", "STONE", "BREAD", "LOTUS", "EAGLE", "MAPLE",
            "OCEAN", "PEARL", "TIGER", "CORAL", "GRAPE", "HONEY", "LEMON", "OLIVE",
            "PINE", "ROSE", "SAGE", "TULIP", "WAVE", "BLOOM", "DAISY", "FLAME",
            "GREEN", "IVORY", "JADE", "KITE", "LILAC", "MOSS", "NOVA", "OPAL"
    );
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        return WORDS.get(random.nextInt(WORDS.size()));
    }
}
