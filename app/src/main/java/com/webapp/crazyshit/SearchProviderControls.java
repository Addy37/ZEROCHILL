package com.webapp.crazyshit;

import java.util.Collections;
import java.util.List;

/**
 * Extra search options a provider can actually honor.
 * Layout is handled separately. Album filters stay inside galleries.
 * No provider currently exposes a real sort, so none is invented here.
 */
final class SearchProviderControls {
    static final class Option {
        final String id;
        final String label;

        Option(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    private SearchProviderControls() {}

    static List<Option> supported(SearchPanel.Scope scope) {
        return Collections.emptyList();
    }
}
