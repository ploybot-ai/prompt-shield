package com.ploybot.promptshield.injection;

/**
 * One piece of evidence found by a detector.
 *
 * @param ruleId   stable identifier of the rule that fired
 * @param category what the rule looks for
 * @param weight   contribution to the score, between 0 and 1
 * @param layer    the view of the text it was found in: {@code raw}, {@code normalized},
 *                 {@code skeleton} (lookalikes, leetspeak and spacing folded),
 *                 {@code hidden} (invisible characters), {@code base64}, {@code hex},
 *                 {@code url}, {@code html-entities}, {@code rot13}, {@code reversed}
 * @param evidence a short excerpt of the matched text (at most 80 characters)
 */
public record InjectionSignal(String ruleId, InjectionCategory category, double weight, String layer, String evidence) {
}
