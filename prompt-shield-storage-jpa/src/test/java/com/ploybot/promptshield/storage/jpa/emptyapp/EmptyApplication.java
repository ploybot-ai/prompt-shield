package com.ploybot.promptshield.storage.jpa.emptyapp;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Configuration;

/** A host application without entities of its own. */
@Configuration(proxyBeanMethods = false)
@AutoConfigurationPackage
public class EmptyApplication {
}
