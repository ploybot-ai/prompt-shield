package com.ploybot.promptshield.ner;

import java.util.List;

public interface EntityDetector {

    List<DetectedEntity> detect(String text);

    String getName();

    boolean isAvailable();
}
