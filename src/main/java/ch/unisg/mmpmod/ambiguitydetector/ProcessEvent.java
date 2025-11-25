package ch.unisg.mmpmod.ambiguitydetector;

import java.time.Instant;

public record ProcessEvent(String label, String payload, int qos, Instant receivedAt) {
}
