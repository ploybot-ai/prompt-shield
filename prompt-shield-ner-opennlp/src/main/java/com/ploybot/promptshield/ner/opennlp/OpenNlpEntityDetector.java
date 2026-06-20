package com.ploybot.promptshield.ner.opennlp;

import com.ploybot.promptshield.ner.DetectedEntity;
import com.ploybot.promptshield.ner.EntityDetector;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class OpenNlpEntityDetector implements EntityDetector {

    private static final Logger logger = LoggerFactory.getLogger(OpenNlpEntityDetector.class);

    private final SentenceModel sentenceModel;
    private final TokenizerModel tokenizerModel;
    private final TokenNameFinderModel personModel;
    private final TokenNameFinderModel locationModel;
    private final TokenNameFinderModel organizationModel;

    public OpenNlpEntityDetector() {
        this.sentenceModel = loadModel("/models/en-sent.bin");
        this.tokenizerModel = loadModel("/models/en-token.bin");
        this.personModel = loadModel("/models/en-ner-person.bin");
        this.locationModel = loadModel("/models/en-ner-location.bin");
        this.organizationModel = loadModel("/models/en-ner-organization.bin");
    }

    private <T> T loadModel(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                logger.warn("Model not found: {}", path);
                return null;
            }
            if (path.contains("sent")) {
                return (T) new SentenceModel(is);
            } else if (path.contains("token")) {
                return (T) new TokenizerModel(is);
            } else if (path.contains("person")) {
                return (T) new TokenNameFinderModel(is);
            } else if (path.contains("location")) {
                return (T) new TokenNameFinderModel(is);
            } else if (path.contains("organization")) {
                return (T) new TokenNameFinderModel(is);
            }
        } catch (IOException e) {
            logger.error("Failed to load model: {}", path, e);
        }
        return null;
    }

    @Override
    public List<DetectedEntity> detect(String text) {
        List<DetectedEntity> entities = new ArrayList<>();

        if (sentenceModel == null || tokenizerModel == null) {
            logger.warn("OpenNLP models not loaded, skipping detection");
            return entities;
        }

        // Split into sentences
        SentenceDetectorME sentenceDetector = new SentenceDetectorME(sentenceModel);
        String[] sentences = sentenceDetector.sentDetect(text);

        TokenizerME tokenizer = new TokenizerME(tokenizerModel);

        int offset = 0;
        for (String sentence : sentences) {
            String[] tokens = tokenizer.tokenize(sentence);

            // Detect persons
            if (personModel != null) {
                detectEntities(tokens, personModel, "PERSON", sentence, offset, entities);
            }

            // Detect locations
            if (locationModel != null) {
                detectEntities(tokens, locationModel, "LOCATION", sentence, offset, entities);
            }

            // Detect organizations
            if (organizationModel != null) {
                detectEntities(tokens, organizationModel, "ORGANIZATION", sentence, offset, entities);
            }

            offset += sentence.length() + 1;
        }

        return entities;
    }

    private void detectEntities(String[] tokens, TokenNameFinderModel model, String entityType,
                                String sentence, int offset, List<DetectedEntity> entities) {
        NameFinderME nameFinder = new NameFinderME(model);
        Span[] spans = nameFinder.find(tokens);

        for (Span span : spans) {
            int start = span.getStart();
            int end = span.getEnd();

            // Reconstruct the entity text
            StringBuilder entityText = new StringBuilder();
            for (int i = start; i < end; i++) {
                if (i > start) {
                    entityText.append(" ");
                }
                entityText.append(tokens[i]);
            }

            // Find position in original text
            String entityStr = entityText.toString();
            int entityStart = sentence.indexOf(entityStr, 0);
            if (entityStart >= 0) {
                entities.add(new DetectedEntity(
                        entityType,
                        entityStr,
                        offset + entityStart,
                        offset + entityStart + entityStr.length(),
                        span.getProb()
                ));
            }
        }
    }

    @Override
    public String getName() {
        return "OpenNLP";
    }

    @Override
    public boolean isAvailable() {
        return sentenceModel != null && tokenizerModel != null;
    }
}
