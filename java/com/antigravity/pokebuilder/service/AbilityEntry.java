package com.antigravity.pokebuilder.service;

import com.cobblemon.mod.common.api.abilities.AbilityTemplate;

/**
 * Pairs an {@link AbilityTemplate} with whether it occupies a hidden-ability
 * slot on the species form. Used by the ability selector GUI to correctly
 * price and gate hidden abilities regardless of list position.
 */
public record AbilityEntry(AbilityTemplate template, boolean hidden) {}
