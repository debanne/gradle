/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.artifacts.dsl;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.artifacts.ComponentModuleMetadataDetails;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;
import org.gradle.api.internal.artifacts.ModuleMetadataHandler;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyModuleDescriptor;
import org.gradle.api.internal.artifacts.repositories.resolver.ComponentMetadataDetailsAdapter;
import org.gradle.api.specs.Specs;
import org.gradle.internal.component.external.model.IvyModuleResolveMetaData;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetaData;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetaData;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.rules.DefaultRuleActionAdapter;
import org.gradle.internal.rules.DefaultRuleActionValidator;
import org.gradle.internal.rules.RuleAction;
import org.gradle.internal.rules.RuleActionAdapter;
import org.gradle.internal.rules.RuleActionValidator;
import org.gradle.internal.rules.SpecRuleAction;

import java.util.List;
import java.util.Set;

public class DefaultComponentMetadataHandler implements ComponentMetadataHandler, ModuleMetadataHandler {
    private final Instantiator instantiator;

    private static final String USER_CODE_ERROR = "Could not apply component metadata rule.";
    private final Set<SpecRuleAction<? super ComponentMetadataDetails>> rules = Sets.newLinkedHashSet();
    private final RuleActionAdapter<ComponentMetadataDetails> ruleActionAdapter;

    private final ComponentModuleMetadataContainer moduleMetadataContainer = new ComponentModuleMetadataContainer();

    public DefaultComponentMetadataHandler(Instantiator instantiator, RuleActionAdapter<ComponentMetadataDetails> ruleActionAdapter) {
        this.instantiator = instantiator;
        this.ruleActionAdapter = ruleActionAdapter;
    }

    public DefaultComponentMetadataHandler(Instantiator instantiator) {
        this(instantiator, createAdapter());
    }

    private static RuleActionAdapter<ComponentMetadataDetails> createAdapter() {
        List<Class<?>> parameterTypes = Lists.newArrayList();
        parameterTypes.add(IvyModuleDescriptor.class);
        RuleActionValidator<ComponentMetadataDetails> ruleActionValidator = new DefaultRuleActionValidator<ComponentMetadataDetails>(parameterTypes);
        return new DefaultRuleActionAdapter<ComponentMetadataDetails>(ruleActionValidator, ComponentMetadataHandler.class.getSimpleName());
    }

    private ComponentMetadataHandler addRule(SpecRuleAction<? super ComponentMetadataDetails> ruleAction) {
        rules.add(ruleAction);
        return this;
    }

    private SpecRuleAction<? super ComponentMetadataDetails> createAllSpecRuleAction(RuleAction<? super ComponentMetadataDetails> ruleAction) {
        return new SpecRuleAction<ComponentMetadataDetails>(ruleAction, Specs.<ComponentMetadataDetails>satisfyAll());
    }

    public ComponentMetadataHandler all(Action<? super ComponentMetadataDetails> rule) {
        return addRule(createAllSpecRuleAction(ruleActionAdapter.createFromAction(rule)));
    }

    public ComponentMetadataHandler all(Closure<?> rule) {
        return addRule(createAllSpecRuleAction(ruleActionAdapter.createFromClosure(ComponentMetadataDetails.class, rule)));
    }

    public void processMetadata(MutableModuleComponentResolveMetaData metadata) {
        ComponentMetadataDetails details = instantiator.newInstance(ComponentMetadataDetailsAdapter.class, metadata);
        processAllRules(metadata, details);
        if (!metadata.getStatusScheme().contains(metadata.getStatus())) {
            throw new ModuleVersionResolveException(metadata.getId(), "Unexpected status '" + metadata.getStatus() + "' specified for %s. Expected one of: " +  metadata.getStatusScheme());
        }
    }

    private void processAllRules(ModuleComponentResolveMetaData metadata, ComponentMetadataDetails details) {
        for (SpecRuleAction<? super ComponentMetadataDetails> rule : rules) {
            processRule(rule, metadata, details);
        }
    }

    private void processRule(SpecRuleAction<? super ComponentMetadataDetails> specRuleAction, ModuleComponentResolveMetaData metadata, ComponentMetadataDetails details) {
        if (!specRuleAction.getSpec().isSatisfiedBy(details)) {
            return;
        }

        List<Object> inputs = Lists.newArrayList();
        for (Class<?> inputType : specRuleAction.getAction().getInputTypes()) {
            if (inputType == IvyModuleDescriptor.class) {
                // Ignore the rule if it expects Ivy metadata and this isn't an Ivy module
                if (!(metadata instanceof IvyModuleResolveMetaData)) {
                    return;
                }

                IvyModuleResolveMetaData ivyMetadata = (IvyModuleResolveMetaData) metadata;
                inputs.add(new DefaultIvyModuleDescriptor(ivyMetadata.getExtraInfo(), ivyMetadata.getBranch(), ivyMetadata.getStatus()));
                continue;
            }

            // We've already validated the inputs: should never get here.
            throw new IllegalStateException();
        }

        try {
            specRuleAction.getAction().execute(details, inputs);
        } catch (Exception e) {
            throw new InvalidUserCodeException(USER_CODE_ERROR, e);
        }
    }

    public ComponentModuleMetadataDetails module(Object moduleNotation) {
        return moduleMetadataContainer.module(moduleNotation);
    }

    public ModuleReplacementsData getModuleReplacements() {
        return moduleMetadataContainer;
    }
}
