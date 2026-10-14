package com.ploybot.promptshield.injection.eval;

import com.ploybot.promptshield.injection.ContentSource;

/**
 * One labelled text of the evaluation corpus.
 *
 * @param id        unique id; mutated samples append the mutation to their seed's id
 * @param attack    whether the text contains an injection attempt
 * @param source    where the text would come from in an application
 * @param dataset   the group it is reported under ({@code dev}, {@code dev-mutated}, {@code bipia}...)
 * @param category  the attack family or benign topic
 * @param technique how the text was produced or disguised ({@code plain}, {@code zero-width}...)
 * @param lang      ISO language code
 * @param text      the text itself
 */
public record Sample(String id, boolean attack, ContentSource source, String dataset, String category,
                     String technique, String lang, String text) {

    public Sample withText(String newId, ContentSource newSource, String newTechnique, String newDataset, String newText) {
        return new Sample(newId, attack, newSource, newDataset, category, newTechnique, lang, newText);
    }
}
