package com.webapp.crazyshit;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class SearchStateTest {
    @Test public void onlyFapSearchUsesCreatorResultCards() {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), SearchActivity.class)
                .putExtra("scope", "bunkr");
        ActivityController<SearchActivity> controller =
                Robolectric.buildActivity(SearchActivity.class, intent).create().start().resume();

        RecyclerView recycler = ReflectionHelpers.getField(controller.get().panel, "recycler");
        assertTrue(recycler.getAdapter() instanceof OnlyFapCreatorSearchAdapter);

        controller.pause().stop().destroy();
    }

    @Test public void showsScopeKeepsItsOwnFailuresAndCleanNoResults() {
        ActivityController<SearchActivity> controller =
                Robolectric.buildActivity(SearchActivity.class).create().start().resume();
        try {
            SearchPanel activity = controller.get().panel;
            ReflectionHelpers.setField(activity, "scope", SearchPanel.Scope.SHOWS);
            ReflectionHelpers.setField(activity, "activeQuery", "fbdijdnd");
            ReflectionHelpers.setField(activity, "pendingSources", 0);
            @SuppressWarnings("unchecked")
            Map<Integer, String> errors = ReflectionHelpers.getField(activity, "errors");
            errors.put(0, "CrazyShit");
            errors.put(1, "EFukt");

            ReflectionHelpers.callInstanceMethod(activity, "renderResults");

            TextView searchState = ReflectionHelpers.getField(activity, "searchState");
            TextView status = ReflectionHelpers.getField(activity, "status");
            assertEquals("Unavailable: CrazyShit, EFukt · Tap to retry", searchState.getText().toString());
            assertTrue(status.getText().toString().startsWith("Some sources could not be reached."));

            errors.clear();
            ReflectionHelpers.callInstanceMethod(activity, "renderResults");
            assertEquals("Shows search complete", searchState.getText().toString());
            assertTrue(status.getText().toString().startsWith("No matches for “fbdijdnd”"));
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test public void restoringFapzoneSearchKeepsTextWithoutOpeningAnAlbum() {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), SearchActivity.class).putExtra("scope", "bunkr");
        ActivityController<SearchActivity> original = Robolectric.buildActivity(SearchActivity.class, intent).create().start().resume();
        EditText input = ReflectionHelpers.getField(original.get().panel, "input");
        input.setText("ann");
        Bundle state = new Bundle();
        original.pause().saveInstanceState(state).stop().destroy();
        ActivityController<SearchActivity> restored = Robolectric.buildActivity(SearchActivity.class, intent).create(state).start().resume();
        assertEquals("ann", ((EditText) ReflectionHelpers.getField(restored.get().panel, "input")).getText().toString());
        assertNull(shadowOf(restored.get()).getNextStartedActivity());
        restored.pause().stop().destroy();
    }
}
