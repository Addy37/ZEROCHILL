package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Applies creator blocks to the active ShitTok queue without redealing unrelated clips. */
final class ShitTokQueuePruner {
    private ShitTokQueuePruner() {
    }

    static Result removeBlockedCreators(
            List<NativeContentItem> items,
            Set<String> blockedCreatorKeys,
            int selectedPosition
    ) {
        if (items == null || items.isEmpty()) {
            return new Result(new ArrayList<>(), new ArrayList<>(), 0, false);
        }

        int safeSelected = Math.max(0, Math.min(selectedPosition, items.size() - 1));
        NativeContentItem selected = items.get(safeSelected);
        ArrayList<NativeContentItem> removed = new ArrayList<>();
        ArrayList<Integer> removedPositions = new ArrayList<>();

        for (int index = 0; index < items.size(); index++) {
            NativeContentItem item = items.get(index);
            if (ShitTokBlockedCreatorStore.isBlocked(blockedCreatorKeys, item)) {
                removed.add(item);
                removedPositions.add(index);
            }
        }

        if (removed.isEmpty()) {
            return new Result(removed, removedPositions, safeSelected, false);
        }

        boolean selectedRemoved = removed.contains(selected);
        NativeContentItem target = selectedRemoved
                ? nearestRemaining(items, blockedCreatorKeys, safeSelected)
                : selected;
        items.removeAll(removed);
        int targetPosition = target == null ? 0 : items.indexOf(target);
        if (targetPosition < 0) targetPosition = 0;
        return new Result(removed, removedPositions, targetPosition, selectedRemoved);
    }

    private static NativeContentItem nearestRemaining(
            List<NativeContentItem> items,
            Set<String> blockedCreatorKeys,
            int selectedPosition
    ) {
        for (int distance = 1; distance < items.size(); distance++) {
            int next = selectedPosition + distance;
            if (next < items.size()
                    && !ShitTokBlockedCreatorStore.isBlocked(blockedCreatorKeys, items.get(next))) {
                return items.get(next);
            }
            int previous = selectedPosition - distance;
            if (previous >= 0
                    && !ShitTokBlockedCreatorStore.isBlocked(
                            blockedCreatorKeys, items.get(previous))) {
                return items.get(previous);
            }
        }
        return null;
    }

    static final class Result {
        final List<NativeContentItem> removed;
        final List<Integer> removedPositions;
        final int targetPosition;
        final boolean selectedRemoved;

        Result(
                List<NativeContentItem> removed,
                List<Integer> removedPositions,
                int targetPosition,
                boolean selectedRemoved
        ) {
            this.removed = removed;
            this.removedPositions = removedPositions;
            this.targetPosition = targetPosition;
            this.selectedRemoved = selectedRemoved;
        }
    }
}
